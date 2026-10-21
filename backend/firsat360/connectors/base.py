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
            timeout=httpx.Timeout(timeout_s), follow_redirects=True, transport=transport)
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
        self._wait(urlparse(url).netloc)
        self.requests_made += 1
        return self.client.get(url, **kw)

    def ensure_allowed(self, url: str) -> None:
        """robots.txt izin vermiyorsa RobotsDisallowed (tarayıcıyla açılacak sayfalar için ön kontrol)."""
        if self.check_robots:
            rp = self._robots_for(url)
            if rp is not None and not rp.can_fetch(self.user_agent, url):
                raise RobotsDisallowed(f"robots.txt bu adrese izin vermiyor: {url}")

    def head(self, url: str) -> httpx.Response | None:
        """Varlık kontrolü (ör. katalog sayfa görseli var mı). robots.txt'ye uyar; hata durumunda None döner."""
        if self.check_robots:
            rp = self._robots_for(url)
            if rp is not None and not rp.can_fetch(self.user_agent, url):
                raise RobotsDisallowed(f"robots.txt bu adrese izin vermiyor: {url}")
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
                raise ConnectorError(f"{resp.status_code} yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı: {url}")
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
