"""Otomatik (canlı) bağlayıcılar: Tarım Kredi, Hakmar, Migroskop dijital, A101 afişleri, flipbook katalogları.

Hepsi `PoliteClient` ile çalışır: robots.txt'ye uyar, istekler arası bekler, 403/429'da durur (aşmaya çalışmaz).
Sayfa yapısı değişip veri bulunamazsa bağlayıcı HATA verir; uydurma/varsayılan veri üretmez ve mevcut doğrulanmış
veri veritabanında korunur.

Not: Bu bağlayıcılar resmî sayfaların 13–14.10.2026 tarihli içeriğine (adres kalıpları, alan adları) göre yazıldı.
Geliştirme ortamı mağaza sitelerine bağlanamadığı için ilk canlı doğrulama sunucudaki günlük çalıştırmada yapılır;
sonuç durum dosyasına (status.json) mağaza bazında yazılır.
"""
from __future__ import annotations

import json
import re
from datetime import date, datetime
from urllib.parse import urljoin, urlparse

from ..normalize import clean, parse_price, parse_tr_range
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, OfferData, PoliteClient
from .htmlutil import img_src, parse, price_tokens, text_of

IMG_EXT = re.compile(r"\.(?:jpe?g|png|webp)(?:\?|$)", re.I)
SKIP_IMG = re.compile(r"(?i)logo|icon|favicon|sprite|banner-app|app-store|google-play|placeholder|yok\.png|avatar")


# ------------------------------------------------------------------ genel sayfa bilgisi
def page_meta(html: str, base_url: str) -> dict:
    """og:title/og:image/JSON-LD Product/h1/fiyat. Ürün ve katalog sayfaları için ortak okuma."""
    tree = parse(html)
    out: dict = {"title": None, "image": None, "name": None, "price_text": None, "description": None}

    def meta(prop):
        n = tree.css_first(f'meta[property="{prop}"]') or tree.css_first(f'meta[name="{prop}"]')
        v = (n.attributes.get("content") or "").strip() if n else ""
        return v or None

    out["title"] = meta("og:title") or (text_of(tree.css_first("title")) or None)
    img = meta("og:image") or meta("twitter:image")
    out["image"] = urljoin(base_url, img) if img else None
    out["description"] = meta("og:description") or meta("description")
    for s in tree.css('script[type="application/ld+json"]'):
        try:
            data = json.loads(s.text() or "")
        except ValueError:
            continue
        for obj in (data if isinstance(data, list) else data.get("@graph", [data]) if isinstance(data, dict) else []):
            if not isinstance(obj, dict) or "Product" not in str(obj.get("@type", "")):
                continue
            out["name"] = out["name"] or clean(obj.get("name"))
            im = obj.get("image")
            if isinstance(im, list):
                im = im[0] if im else None
            if isinstance(im, dict):
                im = im.get("url")
            if isinstance(im, str) and im:
                out["image"] = urljoin(base_url, im)
            offers = obj.get("offers")
            if isinstance(offers, list):
                offers = offers[0] if offers else None
            if isinstance(offers, dict) and offers.get("price") not in (None, ""):
                out["price_text"] = f"{offers.get('price')} TL"
    h1 = tree.css_first("h1")
    out["name"] = out["name"] or (text_of(h1) or None) or out["title"]
    if out["price_text"] is None:
        body = tree.css_first("main") or tree.body
        toks = price_tokens(text_of(body)) if body else []
        out["price_text"] = toks[0] if toks else None
    return out


def _host(u: str) -> str:
    return urlparse(u).netloc.lower().removeprefix("www.")


# ------------------------------------------------------------------ Tarım Kredi
TK_BASE = "https://www.tkkoop.com.tr"
TK_INDEX = TK_BASE + "/haftalik-kampanyalar"
TK_DEFAULT = TK_INDEX + "/kat-kat-firsat"


def tk_title(name: str) -> str:
    """BÜYÜK HARF ürün adlarını okunur hâle getirir (Türkçe i/ı doğru)."""
    from .snapshot import _tk_title
    return _tk_title(name)


def parse_tk_campaign(html: str, url: str, observed: date) -> tuple[CatalogData, list[OfferData], int]:
    tree = parse(html)
    heading = text_of(tree.css_first("h1")) or text_of(tree.css_first("title")) or "Haftalık Kampanya"
    start, end = parse_tr_range(heading, observed)
    slug = urlparse(url).path.rstrip("/").rsplit("/", 1)[-1] or "kampanya"
    cid = f"tkkoop-{slug}" + (f"-{start:%m%d}" if start else "")
    cat = CatalogData(id=cid, title=clean(heading) or "Haftalık Kampanya", kind="aktuel", official_url=url,
                      valid_from=start, valid_to=end)
    offers, rejected, seen = [], 0, set()
    for a in tree.css('a[href*="/urun/"]'):
        href = urljoin(url, a.attributes.get("href") or "")
        if href in seen:
            continue
        card, cur = None, a
        for _ in range(6):  # fiyatı içeren en küçük kapsayıcı
            cur = cur.parent
            if cur is None:
                break
            if price_tokens(text_of(cur)):
                card = cur
                break
        if card is None:
            continue
        toks = price_tokens(text_of(card))
        if len(toks) > 2:
            continue  # birden çok ürünü kapsayan kap
        name = text_of(a) or ""
        if not name or price_tokens(name):
            for sel in ("h2", "h3", "h4", ".name", ".title", "[class*=name]", "[class*=title]"):
                n = card.css_first(sel)
                if n and text_of(n) and not price_tokens(text_of(n)):
                    name = text_of(n)
                    break
        name = clean(re.sub(r"\s+", " ", name or ""))
        if not name or len(name) < 3:
            continue
        seen.add(href)
        price = parse_price(toks[-1])  # iki fiyat varsa sonuncusu genelde güncel; tek fiyat çoğunluk
        old = parse_price(toks[0]) if len(toks) == 2 else None
        if old is not None and price is not None and old < price:
            old, price = price, old
        if price is None:
            rejected += 1
            continue
        img = None
        for im in card.css("img"):
            u = img_src(im)
            if u and "/assets/images/urun/" in u and not SKIP_IMG.search(u):
                img = urljoin(url, u)
                break
        offers.append(OfferData(name=tk_title(name), price=price, price_text=toks[-1], source_url=url, catalog_id=cid,
                                store_declared_old_price=old if len(toks) == 2 else None, channel="magaza",
                                product_url=href, image_url=img, image_status="listing" if img else None,
                                valid_from=start, valid_to=end))
    return cat, offers, rejected


class TkkoopConnector(Connector):
    store_id = "tkkoop"
    method = "live_html"

    def __init__(self, max_campaigns: int = 4):
        self.max_campaigns = max_campaigns

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        urls: list[str] = []
        try:
            tree = parse(client.get(TK_INDEX).text)
            for a in tree.css('a[href*="/haftalik-kampanyalar/"]'):
                u = urljoin(TK_INDEX, a.attributes.get("href") or "").split("#")[0].rstrip("/")
                if u != TK_INDEX and u not in urls:
                    urls.append(u)
        except ConnectorError as exc:
            res.warnings.append(f"kampanya listesi okunamadı: {exc}")
        if not urls:
            urls = [TK_DEFAULT]
        for u in urls[: self.max_campaigns]:
            try:
                cat, offers, rej = parse_tk_campaign(client.get(u).text, u, observed_at.date())
            except ConnectorError as exc:
                res.warnings.append(str(exc))
                res.partial = True
                continue
            if offers:
                res.catalogs.append(cat)
                res.offers.extend(offers)
            if rej:
                res.warnings.append(f"{rej} ürün fiyatı ayrıştırılamadığı için reddedildi")
        if not res.offers:
            raise ConnectorError("Tarım Kredi: kampanya ürünü bulunamadı (sayfa yapısı değişmiş olabilir)")
        return res


# ------------------------------------------------------------------ Hakmar (broşür sayfa görselleri)
HAKMAR_URL = "https://hakmar.com.tr/indirim/"


def _stem_title(stem: str) -> str:
    s = re.sub(r"(?i)-(?:\d{1,2}|scaled|baski|\d+x\d+)(?=-|$)", "", stem)
    s = re.sub(r"(?i)^\d{1,2}-\d{1,2}-[a-zçğıöşü]+-\d{4}-", "", s)
    s = s.replace("-", " ").strip()
    return " ".join(w.capitalize() if not w.isupper() else w for w in s.split()) or "Broşür"


def parse_hakmar(html: str, observed: date) -> list[CatalogData]:
    tree = parse(html)
    groups: dict[str, dict] = {}
    for im in tree.css("img"):
        u = img_src(im)
        if not u or "/wp-content/uploads/" not in u or not IMG_EXT.search(u) or SKIP_IMG.search(u):
            continue
        u = urljoin(HAKMAR_URL, u)
        u = re.sub(r"-\d{2,4}x\d{2,4}(?=\.\w+$)", "", u)  # WordPress küçük boy → asıl dosya
        name = u.rsplit("/", 1)[-1].rsplit(".", 1)[0]
        # Sayfa numarasını at: "08-19-Ekim-2026-Genel-Insert-3-scaled" → "08-19-Ekim-2026-Genel-Insert"
        key = re.sub(r"(?i)(?:-scaled)?$", "", name)
        key = re.sub(r"-\d{1,2}(?:-\d)?$", "", key)
        g = groups.setdefault(key, {"pages": [], "alt": im.attributes.get("alt") or ""})
        if u not in [p["url"] for p in g["pages"]]:
            g["pages"].append({"url": u, "thumb_url": u, "page": len(g["pages"]) + 1, "verified": True})
    cats = []
    for key, g in groups.items():
        if len(g["pages"]) < 2:
            continue  # tek görsel: büyük olasılıkla tanıtım/afiş değil (logo, kampanya kutusu vb.)
        # "08-19-Ekim-2026-Genel-Insert" → "08-19 Ekim 2026 Genel Insert" (rakamlar arası tire korunur)
        start, end = parse_tr_range(re.sub(r"(?<=\D)-|-(?=\D)", " ", key), observed)
        title = _stem_title(key)
        if start and end:
            title = f"{title} · {start.day}{'' if start.month == end.month else ' ' + _month(start.month)}-{end.day} {_month(end.month)}"
        cats.append(CatalogData(id="hakmar-" + re.sub(r"[^a-z0-9]+", "-", key.lower()).strip("-")[:48],
                                title=title, kind="aktuel", official_url=HAKMAR_URL, valid_from=start, valid_to=end,
                                cover_url=g["pages"][0]["url"], pages=g["pages"]))
    return cats


_MONTHS = ["Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos", "Eylül", "Ekim", "Kasım", "Aralık"]


def _month(m: int) -> str:
    return _MONTHS[m - 1]


class HakmarConnector(Connector):
    store_id = "hakmar"
    method = "live_html"

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        res.catalogs = parse_hakmar(client.get(HAKMAR_URL).text, observed_at.date())
        if not res.catalogs:
            raise ConnectorError("Hakmar: broşür sayfası bulunamadı (sayfa yapısı değişmiş olabilir)")
        return res


# ------------------------------------------------------------------ Migroskop dijital (Money)
MIGROSKOP_URL = "https://www.money.com.tr/mc/kampanyalara-bak/migroskop-dijital/83"
ISSUE_RE = re.compile(r"(?i)say[ıi]\s*[:.]?\s*(\d{3,4})")


def parse_migroskop(html: str, observed: date) -> CatalogData | None:
    """Güncel Migroskop sayısı: sayı no, geçerlilik, PDF/katalog bağlantısı. PDF yalnızca sayfada yayımlanmışsa."""
    tree = parse(html)
    text = text_of(tree.body) if tree.body else ""
    m = ISSUE_RE.search(text)
    if not m:
        return None
    issue = m.group(1)
    after = text[m.end(): m.end() + 120]
    start, end = parse_tr_range(after, observed)
    pdf, viewer = None, None
    for a in tree.css("a[href]"):
        href = urljoin(MIGROSKOP_URL, a.attributes.get("href") or "")
        if href.lower().split("?")[0].endswith(".pdf") and f"/{issue}/" in href:
            pdf = href
        elif "KATALO" in text_of(a).upper() and "GÖR" in text_of(a).upper() and not viewer:
            viewer = href
    cover = None
    og = page_meta(html, MIGROSKOP_URL)["image"]
    if og and "migros" in og.lower():
        cover = og
    return CatalogData(id=f"migroskop-{issue}", title=f"Migroskop · Sayı {issue}", kind="pdf" if pdf else "flipbook",
                       official_url=viewer or MIGROSKOP_URL, valid_from=start, valid_to=end, cover_url=cover,
                       pages=[], pdf_url=pdf)


# ------------------------------------------------------------------ A101 afişleri (sayfaya gömülü JSON)
A101_AFIS_URL = "https://www.a101.com.tr/afisler"


def parse_a101_afis(html: str, observed: date) -> list[CatalogData]:
    tree = parse(html)
    blobs = []
    for s in tree.css("script"):
        t = s.text() or ""
        if "a101" in t and ("http" in t) and (s.attributes.get("id") == "__NEXT_DATA__" or "self.__next_f" in t
                                             or s.attributes.get("type") == "application/json"):
            blobs.append(t)
    groups: dict[str, list[str]] = {}

    def walk(obj, title=None):
        if isinstance(obj, dict):
            t = next((obj[k] for k in ("title", "name", "label", "headline") if isinstance(obj.get(k), str)), title)
            for v in obj.values():
                walk(v, t)
        elif isinstance(obj, list):
            for v in obj:
                walk(v, title)
        elif isinstance(obj, str) and "a101.com.tr" in obj and IMG_EXT.search(obj) and not SKIP_IMG.search(obj):
            if re.search(r"(?i)afis|poster|katalog|brosur", obj + " " + (title or "")):
                groups.setdefault(clean(title) or "A101 Afişleri", [])
                if obj not in groups[clean(title) or "A101 Afişleri"]:
                    groups[clean(title) or "A101 Afişleri"].append(obj)

    for b in blobs:
        try:
            walk(json.loads(b))
        except ValueError:  # React akış (flight) biçimi: içindeki JSON parçalarını tara
            for frag in re.findall(r'\{[^{}]{0,2000}\}', b.replace('\\"', '"')):
                try:
                    walk(json.loads(frag))
                except ValueError:
                    continue
    cats = []
    for title, imgs in groups.items():
        start, end = parse_tr_range(title, observed)
        slug = re.sub(r"[^a-z0-9]+", "-", title.lower().translate(str.maketrans("çğıöşü", "cgiosu"))).strip("-")[:40]
        cats.append(CatalogData(id=f"a101-afis-{slug}" + (f"-{start:%m%d}" if start else ""), title=title,
                                kind="aktuel", official_url=A101_AFIS_URL, valid_from=start, valid_to=end,
                                cover_url=imgs[0], pages=[{"url": u, "thumb_url": u, "page": i + 1, "verified": True}
                                                          for i, u in enumerate(imgs)]))
    return cats


# ------------------------------------------------------------------ flipbook kapak + resmî bağlantı
FLIPBOOK_HOSTS = ("publitas.com", "heyzine.com", "aflip.in", "fliphtml5.com", "issuu.com", "yumpu.com",
                  "katalog.watsons.com.tr", "brosurler.metro-tr.com")


def parse_flipbook_index(html: str, base_url: str, store_id: str, observed: date) -> list[CatalogData]:
    """Mağazanın katalog sayfasındaki flipbook bağlantıları + yanlarındaki kapak görseli."""
    tree = parse(html)
    out, seen = [], set()
    for a in tree.css("a[href]"):
        href = urljoin(base_url, a.attributes.get("href") or "")
        if not any(h in _host(href) for h in FLIPBOOK_HOSTS) or href in seen:
            continue
        seen.add(href)
        img = None
        for im in a.css("img"):
            u = img_src(im)
            if u and not SKIP_IMG.search(u):
                img = urljoin(base_url, u)
                break
        title = clean(text_of(a)) or clean((a.css_first("img").attributes.get("alt") if a.css_first("img") else "")) \
            or clean(a.attributes.get("title")) or "Katalog"
        start, end = parse_tr_range(title, observed)
        slug = re.sub(r"[^a-z0-9]+", "-", href.lower().split("//", 1)[-1])[-40:].strip("-")
        out.append(CatalogData(id=f"{store_id}-{slug}", title=title, kind="flipbook", official_url=href,
                               valid_from=start, valid_to=end, cover_url=img))
    if not out:
        # Bağlantı <a> olarak değil, sayfanın gömülü verisinde (Next.js/Nuxt JSON, CMS bloğu) olabilir (Gratis):
        # ham HTML'de kaçışlı biçimler dahil flipbook adresleri aranır.
        raw = html.replace("\\u002F", "/").replace("\\/", "/").replace("&amp;", "&")
        page_title = clean(text_of(tree.css_first("h1"))) or None
        for m in re.finditer(r"https?://[A-Za-z0-9.-]+/[^\s\"'<>\\]+", raw):
            href = m.group(0).rstrip(".,;)")
            if not any(h in _host(href) for h in FLIPBOOK_HOSTS) or href in seen or IMG_EXT.search(href):
                continue
            if _host(href) in ("fliphtml5.com",) or href.rstrip("/").count("/") < 3:
                continue  # yalnızca alan adı (yayın değil)
            seen.add(href)
            start, end = parse_tr_range(page_title or "", observed)
            slug = re.sub(r"[^a-z0-9]+", "-", href.lower().split("//", 1)[-1])[-40:].strip("-")
            out.append(CatalogData(id=f"{store_id}-{slug}", title=page_title or "Katalog", kind="flipbook",
                                   official_url=href, valid_from=start, valid_to=end))
    return out


def parse_publication(html: str, url: str, store_id: str, observed: date) -> CatalogData | None:
    """Tek yayın sayfası (Publitas vb.): og:title + og:image."""
    m = page_meta(html, url)
    if not m["title"]:
        return None
    title = clean(re.sub(r"\s*[|–-]\s*(Publitas|Watsons|Metro).*$", "", m["title"])) or m["title"]
    start, end = parse_tr_range(title + " " + (m["description"] or ""), observed)
    slug = urlparse(url).path.strip("/").split("/")[0] or "katalog"
    return CatalogData(id=f"{store_id}-{slug}"[:64], title=title, kind="flipbook", official_url=url,
                       valid_from=start, valid_to=end, cover_url=m["image"])


# ------------------------------------------------------------------ Publitas (Watsons, Metro): sayfa görselleri
def publitas_page_image(html: str, base_url: str) -> str | None:
    """Publitas yayın sayfasının (…/page/N) resmî sayfa görseli: link/meta image_src, yoksa og:image (ekran görüntüsü değilse)."""
    tree = parse(html)
    for sel, attr in (('link[rel="image_src"]', "href"), ('meta[name="image_src"]', "content"),
                      ('meta[property="image_src"]', "content"), ('meta[property="og:image"]', "content")):
        n = tree.css_first(sel)
        v = (n.attributes.get(attr) or "").strip() if n else ""
        if v and "/screenshots" not in v:
            return urljoin(base_url, v)
    return None


def publication_base(url: str) -> str:
    """https://katalog.watsons.com.tr/ekim-katalog-2026/page/3 → https://katalog.watsons.com.tr/ekim-katalog-2026"""
    p = urlparse(url)
    parts = [x for x in p.path.split("/") if x]
    if "page" in parts:
        parts = parts[:parts.index("page")]
    return f"{p.scheme}://{p.netloc}/" + "/".join(parts[:1])


def publitas_pages(client: PoliteClient, pub: str, max_pages: int = 80) -> list[dict]:
    """/page/1, /page/2 … sırayla okunur; aynı görsel tekrar edince (son sayfa) veya sayfa yoksa durulur."""
    pages, seen = [], set()
    for n in range(1, max_pages + 1):
        try:
            r = client.get(f"{pub}/page/{n}")
        except ConnectorError:
            break
        img = publitas_page_image(r.text, str(r.url))
        if not img or img in seen:
            break  # son sayfadan sonra Publitas aynı/son sayfayı döndürür
        seen.add(img)
        pages.append({"url": img, "thumb_url": img, "page": len(pages) + 1, "verified": True,
                      "link": f"{pub}/page/{n}"})
    return pages


class PublitasConnector(Connector):
    """Kataloğu Publitas'ta yayımlayan mağazalar: güncel yayın + tüm sayfa görselleri (uygulama içi okuyucu)."""
    method = "live_html"

    def __init__(self, store_id: str, root: str, max_pages: int = 80):
        self.store_id, self.root, self.max_pages = store_id, root, max_pages

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        r = client.get(self.root)  # kök adres güncel yayına yönlendirir veya yayınları listeler
        final = str(r.url)
        if "/page/" not in final and urlparse(final).path.strip("/") == "":
            # Liste sayfası: ilk yayın bağlantısını al
            tree = parse(r.text)
            link = next((urljoin(final, a.attributes.get("href") or "") for a in tree.css("a[href]")
                         if urlparse(urljoin(final, a.attributes.get("href") or "")).netloc == urlparse(final).netloc
                         and urlparse(urljoin(final, a.attributes.get("href") or "")).path.strip("/")), None)
            if link:
                r = client.get(link)
                final = str(r.url)
        cat = parse_publication(r.text, final, self.store_id, observed_at.date())
        if cat is None:
            raise ConnectorError(f"{self.store_id}: Publitas yayını okunamadı")
        pub = publication_base(final)
        cat.official_url = pub + "/page/1"
        cat.pages = publitas_pages(client, pub, self.max_pages)
        if cat.pages:
            cat.kind = "aktuel"
            cat.cover_url = cat.cover_url if cat.cover_url and "/screenshots" not in cat.cover_url else cat.pages[0]["url"]
        else:
            res.warnings.append("sayfa görselleri okunamadı; katalog uygulama içinde resmî sayfasıyla açılır")
        res.catalogs.append(cat)
        return res


# ------------------------------------------------------------------ FlipHTML5 (Gratis): sayfa görselleri
def fliphtml5_pages(client: PoliteClient, base: str, max_pages: int = 80) -> list[dict]:
    """FlipHTML5 yayınlarında sayfa görselleri files/large/<n>.jpg adresindedir; yalnızca VAR OLDUĞU doğrulananlar alınır."""
    base = base.rstrip("/") + "/"
    pages = []
    for n in range(1, max_pages + 1):
        url = f"{base}files/large/{n}.jpg"
        r = client.head(url)
        if r is None or r.status_code != 200 or "image" not in r.headers.get("content-type", ""):
            break
        pages.append({"url": url, "thumb_url": f"{base}files/thumb/{n}.jpg", "page": n, "verified": True})
    return pages


class FlipbookIndexConnector(Connector):
    """Mağazanın katalog sayfasındaki flipbook bağlantıları; FlipHTML5 ise sayfa görselleri de alınır."""
    method = "live_html"

    def __init__(self, store_id: str, index_url: str):
        self.store_id, self.index_url = store_id, index_url

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        cats = parse_flipbook_index(client.get(self.index_url).text, self.index_url, self.store_id, observed_at.date())
        if not cats:
            raise ConnectorError(f"{self.store_id}: katalog bağlantısı bulunamadı")
        for c in cats:
            if "fliphtml5.com" in c.official_url:
                try:
                    c.pages = fliphtml5_pages(client, c.official_url)
                except ConnectorError as exc:
                    res.warnings.append(str(exc))
                if c.pages:
                    c.kind = "aktuel"
                    c.cover_url = c.cover_url or c.pages[0]["thumb_url"]
        res.catalogs = cats
        return res


class FlipbookConnector(Connector):
    """Katalogu yalnızca üçüncü taraf flipbook servisinde yayımlayan mağazalar: kapak + resmî bağlantı."""
    method = "live_html"

    def __init__(self, store_id: str, index_url: str, publication_root: str | None = None):
        self.store_id = store_id
        self.index_url = index_url
        self.publication_root = publication_root

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        if self.publication_root:
            r = client.get(self.publication_root)  # kök adres güncel yayına yönlendirir
            final = str(r.url)
            c = parse_publication(r.text, final, self.store_id, observed_at.date())
            if c:
                res.catalogs.append(c)
        else:
            res.catalogs = parse_flipbook_index(client.get(self.index_url).text, self.index_url, self.store_id,
                                                observed_at.date())
        if not res.catalogs:
            raise ConnectorError(f"{self.store_id}: katalog bağlantısı bulunamadı")
        return res


MIGROSKOP_DIGITAL = "https://migroskop.migros.com.tr/migroskop"
_MIGROSKOP_IMG = re.compile(r"""https?://migroskop-assets\.migros\.com\.tr/migrosdigitalinsert/[^"'<>()]+?\.(?:jpe?g|png|webp)(?:\?[^"'<>()\s]*)?""", re.I)


def parse_migroskop_digital(html: str) -> list[dict]:
    """Migroskop dijital (resmî görüntüleyici) sayfa görselleri: Cover (kapak) + Ad (sayfa) görselleri, belge sırasıyla.
    Ürün küçük resimleri (/Product/) sayfa değildir. Adreslerdeki boşluklar kodlanır; aynı görsel bir kez alınır."""
    from urllib.parse import quote
    raw = html.replace("\\/", "/").replace("&amp;", "&")
    pages, seen = [], set()
    for m in _MIGROSKOP_IMG.finditer(raw):
        u = m.group(0)
        if "/Cover/" not in u and "/Ad/" not in u:
            continue
        u = quote(u, safe=":/?=&%._-~")
        key = u.split("?")[0]
        if key in seen:
            continue
        seen.add(key)
        pages.append({"url": u, "thumb_url": u, "page": len(pages) + 1, "verified": True})
    return pages


class MigroskopConnector(Connector):
    """Migroskop dijital: güncel sayı + (sayfada yoksa) aynı sayı numaralı resmî PDF'in varlığı HEAD ile doğrulanır."""
    store_id = "migros"
    method = "live_html"
    PDF = "https://moneyclubkart.azureedge.net/mcfiles/insert/migros/{issue}/insert.pdf"

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        c = parse_migroskop(client.get(MIGROSKOP_URL).text, observed_at.date())
        if c is None:
            raise ConnectorError("Migroskop: güncel sayı bulunamadı")
        # Resmî dijital görüntüleyicinin sayfa görselleri (rakip uygulamaların gösterdiği broşürün kendisi)
        try:
            pages = parse_migroskop_digital(client.get(MIGROSKOP_DIGITAL).text)
            if pages:
                c.pages, c.cover_url, c.kind = pages, pages[0]["url"], "aktuel"
                c.official_url = MIGROSKOP_DIGITAL
        except ConnectorError as exc:
            res.warnings.append(f"Migroskop dijital: {exc}")
        if not c.pdf_url:
            issue = c.id.rsplit("-", 1)[-1]
            try:
                r = client.head(self.PDF.format(issue=issue))
                if r is not None and r.status_code == 200 and "pdf" in r.headers.get("content-type", "").lower():
                    c.pdf_url = self.PDF.format(issue=issue)
                    c.kind = c.kind if c.pages else "pdf"
            except ConnectorError as exc:
                res.warnings.append(str(exc))
        res.catalogs.append(c)
        return res


class CompositeConnector(Connector):
    """Aynı mağazanın birden çok kaynağını tek sonuçta birleştirir. Bir parça başarısızsa sonuç 'kısmi' olur
    (ürünler yayından kaldırılmaz); hepsi başarısızsa hata."""

    def __init__(self, store_id: str, parts: list):
        self.store_id = store_id
        self.parts = parts
        self.method = parts[0].method

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        errors = []
        for p in self.parts:
            try:
                r = p.fetch(client, observed_at)
            except ConnectorError as exc:
                errors.append(str(exc))
                continue
            res.catalogs.extend(r.catalogs)
            res.offers.extend(r.offers)
            res.warnings.extend(r.warnings)
            res.partial = res.partial or r.partial
        if errors:
            if not res.catalogs and not res.offers:
                raise ConnectorError("; ".join(errors))
            res.warnings.extend(errors)
            res.partial = True
        return res


class FuncConnector(Connector):
    """Sayfa → katalog listesi ayrıştırıcısını bağlayıcıya dönüştürür."""

    def __init__(self, store_id: str, url: str, parser, method: str = "live_html"):
        self.store_id, self.url, self.parser, self.method = store_id, url, parser, method

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        out = self.parser(client.get(self.url).text, observed_at.date())
        res.catalogs = [c for c in (out if isinstance(out, list) else [out]) if c is not None]
        if not res.catalogs:
            raise ConnectorError(f"{self.store_id}: {self.url} sayfasında katalog bulunamadı")
        return res
