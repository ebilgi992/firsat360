"""Alternatif katalog toplama motoru: resmî sayfada broşür bulunamayan mağazalar için sıralı kaynak keşfi.

Her mağaza için kaynaklar sırayla denenir; biri sonuç verirse sonrakine geçilmez, hata verirse sıradaki denenir:

  1. Statik HTML: resmî sayfadaki PDF bağlantıları, flipbook bağlantıları (Publitas, FlipHTML5, Issuu…) ve
     broşür sayfası niteliğindeki büyük görseller (dosya adında insert/broşür/katalog/aktüel geçen).
  2. Tarayıcıyla açma (Playwright/Chromium, yalnızca GitHub Actions'ta kuruluysa): JavaScript ile yüklenen sayfalarda
     ağ trafiğindeki PDF'ler ve broşür görselleri yakalanır. robots.txt önceden kontrol edilir; giriş, CAPTCHA
     veya bot koruması aşılmaz (403/429 → durur, raporlar).
  3. Hiçbiri sonuç vermezse: mağaza hatalı veriyle DOLDURULMAZ; denenen kaynaklar ve neden kayıt altına alınır.

Kabul kuralları (uydurma/yanlış broşür olmasın):
- Görsel ya da PDF yalnızca mağazanın kendi alan adlarından veya bilinen flipbook servislerinden alınır.
- Broşür görseli: dosya adı/yolu broşür anahtar sözcüğü içermeli VE (tarayıcı modunda) en az 500 px olmalı.
- Aynı içerik farklı adreslerden gelirse (boyut parametresi farkı) tek sayfa sayılır.
"""
from __future__ import annotations

import hashlib
import json
import logging
import re
from dataclasses import dataclass, field
from datetime import date, datetime
from urllib.parse import urljoin, urlparse

from ..normalize import clean, parse_tr_range
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, PoliteClient, RobotsDisallowed
from .htmlutil import all_image_urls, parse, text_of

log = logging.getLogger("firsat360.discovery")

FLIPBOOK_HOSTS = ("publitas.com", "heyzine.com", "aflip.in", "fliphtml5.com", "issuu.com", "yumpu.com",
                  "flipsnack.com", "calameo.com")
# Yalnızca broşürü açıkça adlandıran sözcükler ("kampanya", "indirim" gibi genel afiş sözcükleri değil).
BROCHURE_WORD = re.compile(r"(?i)insert|bro[sş]ur|katalog|catalog|aktuel|aktüel|flyer|leaflet|afis|afiş|poster")
# Ürün küçük resimleri broşür sayfası değildir: Magento "/media/catalog/product/" yolundaki "catalog" sözcüğü
# broşür sanılıyordu (Rossmann'da 24 ürün fotoğrafı "katalog sayfası" olarak alınmıştı).
SKIP = re.compile(r"(?i)logo|icon|favicon|sprite|avatar|placeholder|app-?store|google-?play|banner-app|social|"
                  r"/catalog/product/|/product[s]?/|/urun(?:ler)?/|/thumbnail")
PDF_RE = re.compile(r"\.pdf(?:[?#]|$)", re.I)
# Broşür olmayan resmî PDF'ler (KVKK, aydınlatma metni, sözleşme, politika, bilgi toplumu hizmetleri…)
PDF_SKIP = re.compile(r"(?i)kvkk|ayd[ıi]nlatma|politika|s[öo]zle[şs]me|ko[şs]ul|gizlilik|[çc]erez|cookie|bilgi[-_ ]toplumu|"
                      r"veri[-_ ]saklama|imha|ba[şs]vuru[-_ ]form|personal|privacy")


@dataclass
class StoreSource:
    store_id: str
    title: str
    urls: list[str]                        # resmî sayfalar (bölge sayfaları dahil)
    hosts: tuple[str, ...]                 # kabul edilen içerik alan adları (mağazanın kendi alanları/CDN'i)
    render: bool = True                    # statik HTML'de bulunamazsa tarayıcıyla dene
    min_width: int = 500
    page_kind: str = "aktuel"              # görsel sayfalı yayının türü: aktuel (broşür) | poster (tek tek ürün afişi)
    # Broşür sayfası görselleri adında "insert/katalog" geçmeyen marketler için ek kabul deseni (yol üzerinde)
    image_re: str | None = None
    # PDF broşürün resmî kapak görseli (PDF bağlantısını saran görsel veya bu desene uyan görsel)
    cover_re: str | None = None
    # Sayfa başlığı (h1) genel bir sözcükse ("Kataloglar") kaynağın kendi başlığı kullanılır
    default_title_only: bool = False


@dataclass
class Found:
    pdfs: list[str] = field(default_factory=list)
    pdf_titles: dict = field(default_factory=dict)                    # PDF adresi → bağlantı metni
    pdf_covers: dict = field(default_factory=dict)                    # PDF adresi → resmî kapak görseli
    flipbooks: list[tuple[str, str]] = field(default_factory=list)   # (adres, başlık)
    images: list[str] = field(default_factory=list)
    title: str | None = None

    def empty(self) -> bool:
        return not (self.pdfs or self.flipbooks or self.images)


def _host(u: str) -> str:
    return urlparse(u).netloc.lower().removeprefix("www.")


def _own(u: str, hosts: tuple[str, ...]) -> bool:
    h = _host(u)
    return any(h == x or h.endswith("." + x) for x in hosts)


def _dedupe_key(u: str) -> str:
    p = urlparse(u)
    path = re.sub(r"/cdn-cgi/image/[^/]+/", "/", p.path)
    path = re.sub(r"[-_](?:\d{2,4}x\d{2,4}|thumb|small|k|kucuk)(?=\.\w+$)", "", path, flags=re.I)
    return _host(u) + path.lower()


def scan_html(html: str, base_url: str, src: StoreSource) -> Found:
    """1. kaynak: statik HTML taraması."""
    tree = parse(html)
    out = Found(title=clean(text_of(tree.css_first("h1"))) or None)
    seen: set[str] = set()
    for a in tree.css("a[href]"):
        href = urljoin(base_url, (a.attributes.get("href") or "").strip())
        if not href.startswith("http") or href in seen:
            continue
        seen.add(href)
        if PDF_RE.search(href) and (_own(href, src.hosts) or any(h in _host(href) for h in FLIPBOOK_HOSTS)):
            t = clean(text_of(a)) or clean(a.attributes.get("title"))
            if PDF_SKIP.search(href) or (t and PDF_SKIP.search(t)):
                continue
            out.pdfs.append(href)
            if t and len(t) > 3:
                out.pdf_titles[href] = t[:90]
            img = a.css_first("img")
            if img is not None:
                cu = urljoin(base_url, (img.attributes.get("src") or img.attributes.get("data-src") or "").strip())
                if cu.startswith("https://") and _own(cu, src.hosts) and not SKIP.search(cu):
                    out.pdf_covers[href] = cu
                alt = clean(img.attributes.get("alt"))
                if alt and href not in out.pdf_titles:
                    out.pdf_titles[href] = alt[:90]
        elif any(_host(href).endswith(h) for h in FLIPBOOK_HOSTS) and urlparse(href).path.strip("/"):
            out.flipbooks.append((href, clean(text_of(a)) or clean(a.attributes.get("title")) or src.title))
    keys: set[str] = set()
    for u in all_image_urls(tree.body or tree.root):
        u = urljoin(base_url, u)
        path = urlparse(u).path
        extra = bool(src.image_re and re.search(src.image_re, path, re.I))
        if not _own(u, src.hosts) or SKIP.search(u) or not (BROCHURE_WORD.search(path) or extra):
            continue
        if src.cover_re and re.search(src.cover_re, path, re.I):
            continue  # PDF kapağı sayfa görseli değildir
        k = _dedupe_key(u)
        if k not in keys:
            keys.add(k)
            out.images.append(u)
    return out


def render_page(url: str, user_agent: str, timeout_ms: int = 45000) -> tuple[str, list[dict]]:
    """2. kaynak: sayfayı başsız Chromium'da açar; (son HTML, ağ yanıtları [{url, type, width?}]) döner.
    Playwright kurulu değilse ConnectorError. Giriş/CAPTCHA aşılmaz: sayfa ne gösteriyorsa o okunur."""
    try:
        from playwright.sync_api import sync_playwright
    except ImportError as exc:
        raise ConnectorError("tarayıcı motoru (Playwright) kurulu değil; yalnızca statik tarama yapıldı") from exc
    responses: list[dict] = []
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=True)
        try:
            page = browser.new_page(user_agent=user_agent, locale="tr-TR", viewport={"width": 1280, "height": 1600})

            def on_response(r):
                ct = (r.headers.get("content-type") or "").split(";")[0].strip().lower()
                if ct == "application/pdf" or ct.startswith("image/"):
                    responses.append({"url": r.url, "type": ct, "status": r.status})
                elif ct.endswith("json") and r.status == 200 and r.request.resource_type in ("xhr", "fetch"):
                    # Sayfanın kendi arka plan isteği (XHR/fetch): broşür görsel adresleri çoğu zaman burada,
                    # DOM'dan daha temiz ve yüksek çözünürlüklü. Gövde sınırlı okunur.
                    try:
                        body = r.body()
                        if len(body) <= MAX_JSON_BYTES:
                            responses.append({"url": r.url, "type": "json", "status": 200,
                                              "images": json_image_urls(json.loads(body))})
                    except Exception:  # noqa: BLE001 — bozuk/ikili yanıt atlanır
                        log.debug("JSON yanıtı okunamadı: %s", r.url)
            page.on("response", on_response)
            resp = page.goto(url, wait_until="networkidle", timeout=timeout_ms)
            if resp is not None and resp.status in (401, 403, 429):
                raise ConnectorError(f"{resp.status} yanıtı (erişim engeli); aşılmaya çalışılmadı: {url}")
            # Gecikmeli yüklenen görseller için sayfayı yavaşça kaydır (insan benzeri hız taklidi değil; yalnızca yükleme)
            for _ in range(6):
                page.mouse.wheel(0, 1400)
                page.wait_for_timeout(500)
            sizes = page.evaluate("""() => Array.from(document.images).map(i => [i.currentSrc || i.src, i.naturalWidth, i.naturalHeight])""")
            dims = {u: (w, h) for u, w, h in sizes if u}
            for r in responses:
                r["width"], r["height"] = dims.get(r["url"], (None, None))
            html = page.content()
        finally:
            browser.close()
    return html, responses


MAX_JSON_BYTES = 3 * 1024 * 1024
_IMG_URL = re.compile(r"^https?://[^\s\"'<>]+\.(?:jpe?g|png|webp|avif)(?:\?[^\s\"'<>]*)?$", re.I)


def json_image_urls(data, limit: int = 400) -> list[str]:
    """JSON ağacındaki görsel adresleri, belge sırasıyla (derinlik ve sayı sınırlı). Aynı öğede büyük/küçük sürüm
    varsa ("large", "original", "zoom", "detail" anahtarları) büyük olan önce gelir."""
    out: list[str] = []
    big = ("original", "large", "zoom", "full", "detail", "big", "hires", "highres")

    def walk(x, depth: int):
        if depth > 12 or len(out) >= limit:
            return
        if isinstance(x, dict):
            items = sorted(x.items(), key=lambda kv: 0 if any(b in str(kv[0]).lower() for b in big) else 1)
            for _, v in items:
                walk(v, depth + 1)
        elif isinstance(x, list):
            for v in x:
                walk(v, depth + 1)
        elif isinstance(x, str) and _IMG_URL.match(x.strip()) and x.strip() not in out:
            out.append(x.strip())
    walk(data, 0)
    return out


def merge_rendered(found: Found, html: str, responses: list[dict], base_url: str, src: StoreSource) -> Found:
    more = scan_html(html, base_url, src)
    keys = {_dedupe_key(u) for u in found.images}
    # 1) Ağ trafiğindeki JSON uç noktaları (en temiz kaynak): mağaza alan adındaki broşür adlı görseller
    for r in responses:
        if r.get("type") != "json":
            continue
        for u in r.get("images", []):
            if _own(u, src.hosts) and not SKIP.search(u) and BROCHURE_WORD.search(urlparse(u).path):
                k = _dedupe_key(u)
                if k not in keys:
                    keys.add(k)
                    found.images.append(u)
    for u in more.pdfs + [r["url"] for r in responses if r["type"] == "application/pdf" and r.get("status", 200) == 200]:
        if u not in found.pdfs and (_own(u, src.hosts) or any(h in _host(u) for h in FLIPBOOK_HOSTS)):
            found.pdfs.append(u)
    for fb in more.flipbooks:
        if fb[0] not in [x[0] for x in found.flipbooks]:
            found.flipbooks.append(fb)
    for r in responses:
        u = r["url"]
        if r.get("type") == "json" or not r["type"].startswith("image/") or r.get("status", 200) != 200 or not _own(u, src.hosts) or SKIP.search(u):
            continue
        w, h = r.get("width"), r.get("height")
        big = (w or 0) >= src.min_width
        named = bool(BROCHURE_WORD.search(urlparse(u).path))
        # Broşür sayfası (adres "insert/katalog" içeriyor) üzerindeki büyük, dikey/kareye yakın görseller de broşür
        # sayfasıdır; geniş afiş/başlık bantları (en/boy > 1,1) değildir.
        page_like = bool(BROCHURE_WORD.search(base_url)) and big and h and h >= w * 0.9
        if not ((named and (big or w is None)) or page_like):
            continue
        k = _dedupe_key(u)
        if k not in keys:
            keys.add(k)
            found.images.append(u)
    for u in more.images:
        k = _dedupe_key(u)
        if k not in keys:
            keys.add(k)
            found.images.append(u)
    found.title = found.title or more.title
    return found


def to_catalogs(store_id: str, page_url: str, found: Found, observed: date, idx: int,
                default_title: str = "Broşür", page_kind: str = "aktuel") -> list[CatalogData]:
    out: list[CatalogData] = []
    slug = re.sub(r"[^a-z0-9]+", "-", (urlparse(page_url).path + "-" + (urlparse(page_url).query or "")).lower()).strip("-")[-30:]
    title = found.title or default_title
    if title and re.fullmatch(r"(?i)\s*(kataloglar|katalog|insert\s*&?\s*bro[şs][üu]r|indirim\s+b[üu]lteni|bro[şs][üu]rler|aktüel|aktuel)\s*", title):
        title = default_title
    start, end = parse_tr_range(title, observed)
    if found.images:
        pages = [{"url": u, "thumb_url": u, "page": i + 1, "verified": True} for i, u in enumerate(found.images[:60])]
        out.append(CatalogData(id=f"{store_id}-{slug or idx}"[:64], title=title, kind=page_kind, official_url=page_url,
                               valid_from=start, valid_to=end, pages=pages, cover_url=pages[0]["url"]))
    for j, pdf in enumerate(found.pdfs[:5]):
        name = urlparse(pdf).path.rsplit("/", 1)[-1]
        link_title = found.pdf_titles.get(pdf)
        s2, e2 = parse_tr_range(link_title or "", observed)
        if s2 is None and e2 is None:
            s2, e2 = parse_tr_range(name.replace("-", " ").replace("_", " "), observed)
        if s2 is None and e2 is None:
            s2, e2 = _numeric_range(name, observed)
        out.append(CatalogData(id=f"{store_id}-pdf-{hashlib.sha1(pdf.encode(), usedforsecurity=False).hexdigest()[:10]}",
                               title=link_title or (title if j == 0 else name), kind="pdf", official_url=page_url,
                               pdf_url=pdf, valid_from=s2 or start, valid_to=e2 or end, cover_url=found.pdf_covers.get(pdf)))
    for fb, t in found.flipbooks[:5]:
        fslug = re.sub(r"[^a-z0-9]+", "-", fb.lower().split("//", 1)[-1])[-40:].strip("-")
        s3, e3 = parse_tr_range(t or "", observed)
        out.append(CatalogData(id=f"{store_id}-{fslug}"[:64], title=t or title, kind="flipbook", official_url=fb,
                               valid_from=s3, valid_to=e3))
    return out


def _numeric_range(name: str, observed: date) -> tuple[date | None, date | None]:
    """Dosya adındaki sayısal tarih aralığı: "17-30.10.26", "17-30.10.2026" → 17 Ekim – 30 Ekim."""
    m = re.search(r"(\d{1,2})\s*[-_]\s*(\d{1,2})[._](\d{1,2})[._](\d{2,4})", name)
    if not m:
        return None, None
    d1, d2, mo, y = (int(x) for x in m.groups())
    y = y + 2000 if y < 100 else y
    try:
        a, b = date(y, mo, d1), date(y, mo, d2)
    except ValueError:
        return None, None
    return (a, b) if a <= b and abs((b - observed).days) < 200 else (None, None)


MIN_PAGE_BYTES = 15_000  # broşür sayfası bundan küçük olamaz (simge/boş görsel elenir)


def validate_images(client: PoliteClient, urls: list[str], limit: int = 60) -> tuple[list[str], int]:
    """Her sayfa görselinin gerçekten görsel olduğu ilk baytlarından doğrulanır (JPEG/PNG/WEBP); HTML hata sayfası,
    boş dosya veya giriş sayfasına yönlendirme broşür sayfası olarak kaydedilmez. Bilinen boyut MIN_PAGE_BYTES altındaysa
    (simge) elenir."""
    ok, bad = [], 0
    for u in urls[:limit]:
        try:
            p = client.probe(u)
        except ConnectorError:
            p = {"ok": False}
        if p.get("ok") and p.get("kind") in ("jpeg", "png", "webp") and (not p.get("size") or p["size"] >= MIN_PAGE_BYTES):
            ok.append(u)
        else:
            bad += 1
    return ok, bad


class DiscoveryConnector(Connector):
    method = "discovery"

    def __init__(self, src: StoreSource, renderer=render_page):
        self.src = src
        self.store_id = src.store_id
        self.renderer = renderer

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        attempts: list[str] = []
        for i, url in enumerate(self.src.urls):
            found = Found()
            try:
                html = client.get(url).text
                found = scan_html(html, url, self.src)
                attempts.append(f"statik: {url} → {len(found.images)} görsel, {len(found.pdfs)} PDF, "
                                f"{len(found.flipbooks)} flipbook")
            except RobotsDisallowed as exc:
                attempts.append(f"robots.txt izin vermiyor: {url}")
                res.warnings.append(str(exc))
                continue  # robots izin vermiyorsa tarayıcıyla da açılmaz
            except ConnectorError as exc:
                attempts.append(f"statik başarısız: {url}: {exc}")
                if "403" in str(exc) or "429" in str(exc):
                    continue  # erişim engeli: tarayıcıyla da zorlanmaz
            if found.empty() and self.src.render:
                try:
                    client.ensure_allowed(url)
                    html2, responses = self.renderer(url, client.user_agent)
                    found = merge_rendered(found, html2, responses, url, self.src)
                    attempts.append(f"tarayıcı: {url} → {len(found.images)} görsel, {len(found.pdfs)} PDF")
                except ConnectorError as exc:
                    attempts.append(f"tarayıcı başarısız: {url}: {exc}")
                except Exception as exc:  # noqa: BLE001 — tarayıcı hatası mağazayı/diğer mağazaları durdurmaz
                    attempts.append(f"tarayıcı hatası: {url}: {exc.__class__.__name__}")
            if found.images:
                ok, bad = validate_images(client, found.images)
                if bad:
                    attempts.append(f"{bad} görsel doğrulanamadı (HTML/hata/boş)")
                found.images = ok
            if found.pdfs:
                # PDF gerçekten PDF mi (ilk baytlar "%PDF"); hata sayfası katalog sayılmaz
                good = []
                for u in found.pdfs[:5]:
                    try:
                        p = client.probe(u)
                    except ConnectorError:
                        p = {"ok": False}
                    if p.get("ok") and p.get("kind") == "pdf":
                        good.append(u)
                    else:
                        attempts.append(f"PDF doğrulanamadı ({p.get('status')}, {p.get('kind')}): {u}")
                found.pdfs = good
            res.catalogs += to_catalogs(self.store_id, url, found, observed_at.date(), i, self.src.title,
                                        self.src.page_kind)
        if not res.catalogs:
            raise ConnectorError(f"{self.src.title}: hiçbir resmî kaynakta broşür bulunamadı · denemeler: "
                                 + " | ".join(attempts))
        res.warnings += [a for a in attempts if "başarısız" in a or "hatası" in a]
        if res.warnings:
            res.partial = True
        log.info("%s keşif: %s", self.store_id, " | ".join(attempts))
        return res


# Doğrulanmış resmî kaynaklar (21.10.2026, web üzerinden kontrol edildi; ayrıntı docs/KAYNAKLAR.md)
SOURCES = {
    "rossmann": StoreSource("rossmann", "Rossmann Katalog", ["https://www.rossmann.com.tr/kataloglar"],
                            hosts=("rossmann.com.tr",)),
    "carrefoursa": StoreSource("carrefoursa", "CarrefourSA Katalog", ["https://www.carrefoursa.com/"],
                               hosts=("carrefoursa.com",)),
    # Kök neden: resmî alan adı file.com.tr (filemarket.com.tr değil). Resmî sitede broşür sayfası yok; ana sayfadaki
    # "AFIS" adlı resmî ürün afişleri (uploads/file/banner/…AFIS…) gösterilir.
    "file": StoreSource("file", "File Market ürün afişleri", ["https://www.file.com.tr/"], hosts=("file.com.tr",),
                        page_kind="poster"),
    # 1.6.0 — 23.10.2026'da resmî sayfalarda doğrulandı (yalnızca market zincirleri)
    # Mopaş /kataloglar: resmî PDF (cdnprod.mopas.com.tr/…/17-30.10.26.pdf), PDF bağlantısını saran HERO.png kapak
    "mopas": StoreSource("mopas", "Mopaş Kataloğu", ["https://mopas.com.tr/kataloglar"], hosts=("mopas.com.tr",),
                         cover_re=r"/HERO\.(?:png|jpe?g|webp)$"),
    # Happy Center indirim bülteni: sayfalar crm.happycenter.tr/admin/images/uploads/happy*.jpg
    "happycenter": StoreSource("happycenter", "Happy Center İndirim Bülteni",
                               ["https://www.happycenter.com.tr/kurumsal/indirim-bulteni/"],
                               hosts=("happycenter.com.tr", "happycenter.tr"), image_re=r"/admin/images/uploads/happy\d*\.(?:jpe?g|png|webp)$"),
    # Yunus Market Insert & Broşür: resmî PDF insertler (/images/dosyalar/insert-brosur/…pdf)
    "yunus": StoreSource("yunus", "Yunus Market İnsert", ["https://www.yunusmarket.com.tr/insert-brosur"],
                         hosts=("yunusmarket.com.tr",)),
}
