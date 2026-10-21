"""Bağlayıcı arayüzü ve kibar (rate-limited, robots.txt'ye uyan) HTTP istemcisi."""
from __future__ import annotations

import time
import urllib.robotparser
from dataclasses import dataclass, field
from datetime import date, datetime
from decimal import Decimal
from urllib.parse import urlparse

import httpx


class ConnectorError(Exception):
    """Kaynaktan veri alınamadı veya yapı beklenenden farklı."""


class RobotsDisallowed(ConnectorError):
    pass


@dataclass
class CatalogData:
    id: str
    title: str
    kind: str
    official_url: str
    valid_from: date | None = None
    valid_to: date | None = None
    cover_url: str | None = None
    # [{"url": tam boy, "thumb_url": küçük, "caption": açıklama, "link": sayfaya özel resmî bağlantı, "verified": bool}]
    pages: list[dict] = field(default_factory=list)
    pdf_url: str | None = None  # mağazanın yayımladığı resmî PDF (uygulama istek üzerine açar, kopyalanmaz)
    region: str | None = None   # bölgesel yayın kimliği (yoksa tüm Türkiye)


@dataclass
class OfferData:
    name: str
    price: Decimal | None
    price_text: str | None
    source_url: str
    catalog_id: str | None = None
    brand: str | None = None
    variant: str | None = None
    store_declared_old_price: Decimal | None = None
    condition: str | None = None
    channel: str | None = None
    product_url: str | None = None
    image_url: str | None = None
    image_large_url: str | None = None
    image_status: str | None = None  # listing | official_json (bağlayıcının kendi kaynağından)
    category_url_hint: str | None = None
    valid_from: date | None = None
    valid_to: date | None = None


@dataclass
class ConnectorResult:
    store_id: str
    method: str
    observed_at: datetime
    catalogs: list[CatalogData] = field(default_factory=list)
    offers: list[OfferData] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    partial: bool = False


MAX_RESPONSE_BYTES = 30 * 1024 * 1024


def sniff(b: bytes) -> str:
    """İçerik türünü ilk baytlardan belirler (sunucunun Content-Type'ına güvenilmez)."""
    if b.startswith(b"\xff\xd8\xff"):
        return "jpeg"
    if b.startswith(b"\x89PNG"):
        return "png"
    if b[:4] == b"RIFF" and b[8:12] == b"WEBP":
        return "webp"
    if b[:4] in (b"GIF8",):
        return "gif"
    if b.startswith(b"%PDF"):
        return "pdf"
    if b.lstrip()[:1] == b"<":
        return "html"
    return "unknown"


def block_diagnosis(resp: httpx.Response) -> str:
    """403/429'un kaynağı: hangi sunucu/CDN, hangi içerik türü, yönlendirme zinciri. Gövde kaydedilmez."""
    h = resp.headers
    who = ("Cloudflare" if "cf-ray" in h or "cloudflare" in h.get("server", "").lower() else
           "Akamai" if any(k.lower().startswith("x-akamai") for k in h) or "akamai" in h.get("server", "").lower() else
           "Imperva" if "x-iinfo" in h or "incap" in h.get("set-cookie", "").lower() else
           "AWS CloudFront" if "x-amz-cf-id" in h else h.get("server", "bilinmiyor") or "bilinmiyor")
    chain = " → ".join(str(r.url) for r in resp.history) or "yok"
    return f"engelleyen: {who}; içerik: {h.get('content-type', '-')}; yönlendirme: {chain}"


def check_public_url(url: str) -> None:
    """SSRF koruması: yalnızca http/https ve herkese açık adresler. localhost, özel/yerel/bağlantı-yerel IP'ler ve
    çıplak (noktasız) ana bilgisayar adları reddedilir. Kaynak adresleri yapılandırma dosyasından gelse de uygulanır."""
    import ipaddress
    p = urlparse(url)
    host = (p.hostname or "").lower()
    if p.scheme not in ("http", "https") or not host:
        raise ConnectorError(f"İzin verilmeyen adres: {url}")
    if host == "localhost" or host.endswith(".localhost") or host.endswith(".local") or "." not in host:
        raise ConnectorError(f"Yerel adrese erişim engellendi: {host}")
    try:
        ip = ipaddress.ip_address(host.strip("[]"))
    except ValueError:
        return
    if ip.is_private or ip.is_loopback or ip.is_link_local or ip.is_reserved or ip.is_multicast or ip.is_unspecified:
        raise ConnectorError(f"Özel ağ adresine erişim engellendi: {host}")


class PoliteClient:
    """robots.txt'ye uyan, istekler arası bekleyen, zaman aşımı ve sınırlı yeniden denemesi olan istemci.

    CAPTCHA veya bot korumasını aşmaya çalışmaz: 403/429 yanıtı hata olarak raporlanır.
    """

    def __init__(self, user_agent: str, timeout_s: float = 20.0, min_interval_s: float = 4.0,
                 retries: int = 2, transport: httpx.BaseTransport | None = None,
                 sleep=time.sleep, check_robots: bool = True, cache=None):
        self.cache = cache  # httpcache.ConditionalCache: ETag/Last-Modified ile değişmeyeni yeniden indirmez
        self.user_agent = user_agent
        self.min_interval_s = min_interval_s
        self.retries = retries
        self.sleep = sleep
        self.check_robots = check_robots
        self._last: dict[str, float] = {}
        self._robots: dict[str, urllib.robotparser.RobotFileParser | None] = {}
        self.client = httpx.Client(
            headers={"User-Agent": user_agent, "Accept-Language": "tr-TR,tr;q=0.9"},
            timeout=httpx.Timeout(timeout_s), follow_redirects=True, transport=transport, max_redirects=5)
        self.requests_made = 0

    def close(self):
        self.client.close()

    def _robots_for(self, url: str):
        host = urlparse(url).netloc
        if host not in self._robots:
            robots_url = f"{urlparse(url).scheme}://{host}/robots.txt"
            rp = urllib.robotparser.RobotFileParser()
            try:
                resp = self._raw_get(robots_url)
                if resp.status_code == 200:
                    rp.parse(resp.text.splitlines())
                elif resp.status_code in (401, 403):
                    rp.disallow_all = True
                else:  # 404 vb.: robots.txt yok → standart gereği her şey serbest
                    rp.parse([])
            except httpx.HTTPError as exc:
                raise ConnectorError(f"robots.txt okunamadı ({robots_url}): {exc.__class__.__name__}") from exc
            self._robots[host] = rp
        return self._robots[host]

    def _wait(self, host: str):
        last = self._last.get(host)
        if last is not None:
            delta = time.monotonic() - last
            if delta < self.min_interval_s:
                self.sleep(self.min_interval_s - delta)
        self._last[host] = time.monotonic()

    def _raw_get(self, url: str, **kw) -> httpx.Response:
        check_public_url(url)
        self._wait(urlparse(url).netloc)
        self.requests_made += 1
        resp = self.client.get(url, **kw)
        check_public_url(str(resp.url))  # yönlendirme sonrası hedef de doğrulanır
        if len(resp.content) > MAX_RESPONSE_BYTES:
            raise ConnectorError(f"Yanıt çok büyük ({len(resp.content) // 1048576} MB): {url}")
        return resp

    def ensure_allowed(self, url: str) -> None:
        """robots.txt izin vermiyorsa RobotsDisallowed (tarayıcıyla açılacak sayfalar için ön kontrol)."""
        if self.check_robots:
            rp = self._robots_for(url)
            if rp is not None and not rp.can_fetch(self.user_agent, url):
                raise RobotsDisallowed(f"robots.txt bu adrese izin vermiyor: {url}")

    def probe(self, url: str) -> dict:
        """Dosyanın GERÇEK türünü doğrular: GET ile yalnızca ilk baytlar okunur (sihirli sayı), bağlantı kapatılır.
        Bazı sunucular HEAD'e yanlış yanıt verir (File: sonsuz 302). Dönüş: {ok, kind, status, type, size}.
        kind: jpeg | png | webp | gif | pdf | html | unknown | robots | error.
        robots.txt izin vermiyorsa dosya İNDİRİLMEZ ve hata fırlatılmaz: {ok: False, kind: "robots"} döner
        (çağıran taraf kendi güvenli yedeğine geçer; bir doğrulama adımı tüm mağaza taramasını düşürmez)."""
        try:
            self.ensure_allowed(url)
            check_public_url(url)
        except RobotsDisallowed:
            return {"ok": False, "kind": "robots", "status": None, "type": "", "size": 0}
        except ConnectorError as exc:
            return {"ok": False, "kind": "error", "status": None, "type": "", "size": 0, "error": str(exc)[:120]}
        self._wait(urlparse(url).netloc)
        self.requests_made += 1
        try:
            with self.client.stream("GET", url) as r:
                check_public_url(str(r.url))
                head = b""
                for chunk in r.iter_bytes(64):
                    head += chunk
                    if len(head) >= 16:
                        break
                size = int(r.headers.get("content-length") or 0)
                kind = sniff(head)
                return {"ok": r.status_code == 200 and kind != "html" and kind != "unknown", "kind": kind,
                        "status": r.status_code, "type": r.headers.get("content-type", ""), "size": size}
        except (httpx.HTTPError, ConnectorError) as exc:
            return {"ok": False, "kind": "error", "status": None, "type": "", "size": 0, "error": exc.__class__.__name__}

    def head(self, url: str) -> httpx.Response | None:
        """Varlık kontrolü (ör. katalog sayfa görseli var mı). robots.txt'ye uyar; hata durumunda None döner."""
        if self.check_robots:
            rp = self._robots_for(url)
            if rp is not None and not rp.can_fetch(self.user_agent, url):
                raise RobotsDisallowed(f"robots.txt bu adrese izin vermiyor: {url}")
        check_public_url(url)
        try:
            self._wait(urlparse(url).netloc)
            self.requests_made += 1
            return self.client.head(url)
        except httpx.HTTPError:
            return None

    def get(self, url: str, **kw) -> httpx.Response:
        if self.check_robots:
            rp = self._robots_for(url)
            if rp is not None and not rp.can_fetch(self.user_agent, url):
                raise RobotsDisallowed(f"robots.txt bu adrese izin vermiyor: {url}")
        key = str(httpx.URL(url, params=kw.get("params"))) if self.cache is not None else ""
        cond = self.cache.headers_for(key) if self.cache is not None else {}
        if cond:
            kw = dict(kw)
            kw["headers"] = {**(kw.get("headers") or {}), **cond}
        last_exc: Exception | None = None
        for attempt in range(self.retries + 1):
            try:
                resp = self._raw_get(url, **kw)
            except (httpx.TimeoutException, httpx.TransportError) as exc:
                last_exc = exc
                # Artan bekleme (üstel): 2, 4, 8 … sn, en fazla 30 sn
                self.sleep(min(30.0, 2.0 ** (attempt + 1)))
                continue
            if resp.status_code == 304 and cond:
                body = self.cache.body_for(key)
                if body is not None:
                    self.cache.not_modified(key)
                    return httpx.Response(200, content=body, headers=resp.headers, request=resp.request)
                # Önbellek dosyası kaybolduysa koşulsuz yeniden iste
                kw["headers"] = {k: v for k, v in kw["headers"].items() if k not in cond}
                cond = {}
                continue
            if resp.status_code in (403, 429):
                raise ConnectorError(f"{resp.status_code} yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı: {url} "
                                     f"[{block_diagnosis(resp)}]")
            if 500 <= resp.status_code < 600:
                last_exc = ConnectorError(f"Sunucu hatası {resp.status_code}: {url}")
                self.sleep(min(30.0, 2.0 ** (attempt + 1)))
                continue
            if resp.status_code != 200:
                raise ConnectorError(f"Beklenmeyen durum kodu {resp.status_code}: {url}")
            if self.cache is not None:
                self.cache.store(key, resp.content, resp.headers.get("etag"), resp.headers.get("last-modified"))
            return resp
        raise ConnectorError(f"İstek başarısız ({self.retries + 1} deneme): {url}: {last_exc}")


class Connector:
    store_id: str = ""
    method: str = ""

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:  # pragma: no cover
        raise NotImplementedError
