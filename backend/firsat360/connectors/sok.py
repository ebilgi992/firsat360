"""ŞOK resmî "İndirimli Ürünler" listesi ve kampanya sayfaları bağlayıcısı.

1) ``/indirimli-urunler-cms-pl1`` (menüde "Haftanın Fırsatları") sayfaları ``?page=N`` ile okunur. Her ürün kartında
   iki fiyat bulunur. Üstü çizili (del/s/line-through/old sınıfı) işaretli fiyat varsa o "mağaza beyanı önceki fiyat"
   olur. İşaret yoksa ve kartta tam iki farklı fiyat varsa, sayfa açıkça indirimli ürün listesi olduğundan düşük fiyat
   güncel, yüksek fiyat önceki fiyat kabul edilir. Üç ve daha fazla farklı fiyatlı kart belirsizdir → reddedilir.
2) /kampanyalar sayfasındaki ``-prm-`` bağlantıları kampanya olarak alınır; ilk N kampanya sayfasındaki ürünler
(``-p-<id>``) fiyat ve kampanya koşuluyla birlikte okunur. Kampanya fiyatı başlıkta açıkça yazıyorsa ("... alışverişte
139.00 TL") o fiyat kullanılır; ürün kartındaki diğer fiyat "mağaza beyanı liste fiyatı" olarak saklanır.
"""
from __future__ import annotations

import re
from datetime import datetime
from urllib.parse import urljoin

from ..normalize import clean, format_try, parse_price, parse_tr_date, parse_tr_range
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, OfferData, PoliteClient
from .htmlutil import ancestor_with, find_image, img_src, is_struck, parse, price_tokens, text_of

BASE = "https://www.sokmarket.com.tr"
LIST_URL = BASE + "/kampanyalar"
DISCOUNT_URL = BASE + "/indirimli-urunler-cms-pl1"
DISCOUNT_CATALOG_ID = "sok-indirimli"
CAMPAIGN_RE = re.compile(r"-prm-[0-9a-f-]{8,}")
PRODUCT_RE = re.compile(r"-p-\d+$")
TITLE_PRICE_RE = re.compile(r"(\d+[.,]\d{2})\s*TL", re.I)
JUNK_NAMES = {"product-thumb", "product thumb", "product-image", "image", "img", "thumbnail", "urun", "ürün"}
SOK_IMG_RE = re.compile(r"product-assets/", re.I)


def card_name(card, a) -> str | None:
    """Ürün adı: görselin alt metni (anlamlıysa) → başlık → bağlantı metni → bağlantının title özniteliği.
    'product-thumb' gibi şablon metinleri ad sayılmaz."""
    cands = []
    for img in card.css("img[alt]"):
        cands.append(img.attributes.get("alt"))
    cands += [text_of(card.css_first("h1, h2, h3, h4, h5")), a.attributes.get("title"), text_of(a)]
    for c in cands:
        c = clean(c)
        if c and c.lower() not in JUNK_NAMES and not price_tokens(c) and len(c) >= 3:
            return c
    return None


def card_image(card) -> str | None:
    u = find_image(card, "product-assets", pattern=SOK_IMG_RE)
    return urljoin(BASE, u) if u else None


VALID_FROM_RE = re.compile(r"Kampanya\s+(\d{1,2}\s+\S+)\s+tarihinden itibaren", re.I)


def parse_discount_list(html: str) -> tuple[list[OfferData], int]:
    """İndirimli ürünler sayfasındaki ürün kartları → (ürünler, reddedilen)."""
    tree = parse(html)
    offers: list[OfferData] = []
    seen: set[str] = set()
    rejected = 0
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        if not PRODUCT_RE.search(href):
            continue
        full = urljoin(BASE, href)
        if full in seen:
            continue
        card = ancestor_with(a, lambda n: bool(price_tokens(text_of(n))), max_up=6)
        if card is None:
            continue
        seen.add(full)
        name = card_name(card, a)
        struck, plain = [], []
        for el in card.css("*"):
            toks = price_tokens(el.text(deep=False).strip())
            if toks:
                (struck if is_struck(el) else plain).extend(toks)
        if not struck and not plain:
            plain = price_tokens(text_of(card))
        values = sorted({v for v in (parse_price(t) for t in plain) if v is not None})
        old_values = sorted({v for v in (parse_price(t) for t in struck) if v is not None})
        price = old = None
        if old_values and len(values) == 1:
            price, old = values[0], old_values[-1]
        elif not old_values and len(values) == 1:
            price = values[0]
        elif not old_values and len(values) == 2:
            price, old = values[0], values[1]
        if not name or price is None:
            rejected += 1
            continue
        image = card_image(card)
        offers.append(OfferData(name=clean(name), price=price, price_text=format_try(price), source_url=DISCOUNT_URL,
                                image_url=image, image_large_url=image, image_status="listing" if image else None,
                                catalog_id=DISCOUNT_CATALOG_ID, product_url=full, channel="online",
                                store_declared_old_price=old if old is not None and old > price else None))
    return offers, rejected


def _original_promo(u: str) -> str:
    """Cloudflare boyutlandırma önekini kaldırır → resmî tam boy görsel (…/promotion-assets/…)."""
    return re.sub(r"/cdn-cgi/image/[^/]+/", "/", u)


GALLERY_WIDTH = 1600


def larger_thumb(u: str, width: int = GALLERY_WIDTH) -> str:
    """…/cdn-cgi/image/width=512,height=260,fit=pad,…/promotion-assets/x.jpg → aynı adres, width=1600 (yükseklik
    sınırı kaldırılır ki oran korunsun). Boyutlandırma öneki yoksa adres değişmez."""
    m = re.search(r"/cdn-cgi/image/([^/]+)/", u)
    if not m:
        return u
    opts = [o for o in m.group(1).split(",") if o and not o.startswith(("width=", "w=", "height=", "h="))]
    return u[:m.start(1)] + ",".join([f"width={width}"] + opts) + u[m.end(1):]


def parse_campaign_banners(html: str) -> list[dict]:
    """Kampanya listesindeki resmî kampanya afişleri → galeri sayfaları.

    21.10.2026 yapısı: kart (div) içinde ayrı ayrı <img src=…promotion-assets…>, başlık <p> ve "Kampanya Detayı"
    bağlantısı (<a href="…-prm-…">). Eski yapıda görsel bağlantının içindeydi; ikisi de desteklenir: bağlantıdan
    yukarı çıkılarak kampanya görselini içeren en yakın kart bulunur."""
    tree = parse(html)
    pages, seen = [], set()
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        if not CAMPAIGN_RE.search(href):
            continue
        url = urljoin(BASE, href)
        if url in seen:
            continue
        card = ancestor_with(a, lambda n: any("promotion-assets" in (img_src(i) or "") for i in n.css("img")), max_up=5)
        if card is None:
            continue
        img = next((img_src(i) for i in card.css("img") if "promotion-assets" in (img_src(i) or "")), None)
        if not img:
            continue
        seen.add(url)
        alt = next((i.attributes.get("alt") for i in card.css("img") if i.attributes.get("alt")), None)
        title = (useful_title(clean(text_of(card.css_first("p")))) or useful_title(clean(alt))
                 or useful_title(clean(text_of(a))) or slug_title(url))
        full = _original_promo(img)
        pages.append({"url": full, "thumb_url": img, "caption": title, "link": url, "page": len(pages) + 1,
                      "verified": True})
    return pages


def parse_campaign_list(html: str) -> list[tuple[str, str]]:
    tree = parse(html)
    out, seen = [], set()
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        if not CAMPAIGN_RE.search(href):
            continue
        url = urljoin(BASE, href)
        if url in seen:
            continue
        seen.add(url)
        title = useful_title(clean(text_of(a))) or useful_title(clean(a.attributes.get("title")))
        if not title:
            # Yeni kart yapısı: bağlantı metni yalnızca "Kampanya Detayı"; asıl başlık kartın <p>'sinde
            card = ancestor_with(a, lambda n: n.css_first("p") is not None and n.css_first("img") is not None, max_up=5)
            title = useful_title(clean(text_of(card.css_first("p")))) if card is not None else None
        out.append((title or slug_title(url), url))
    return out


GENERIC_TITLES = {"kampanya detayi", "kampanya detayı", "detay", "detaylar", "incele", "kampanya", "tumu", "tümü",
                  "hemen incele", "kampanyayi incele", "kampanyayı incele"}


def useful_title(t: str | None) -> str | None:
    """"Kampanya Detayı" gibi düğme metinleri başlık değildir."""
    from ..normalize import tr_lower
    return None if not t or tr_lower(t).strip(" .:›>") in GENERIC_TITLES else t


def slug_title(url: str) -> str:
    """Kampanya adresinden başlık: "/1-alana-1-bedava-prm-…" → "1 Alana 1 Bedava"."""
    from urllib.parse import urlparse
    path = urlparse(url).path.strip("/").rsplit("/", 1)[-1]
    slug = CAMPAIGN_RE.split(path)[0].strip("-")
    words = [w for w in slug.split("-") if w]
    return " ".join(w if w.isdigit() else w[:1].upper() + w[1:] for w in words) or "Kampanya"


def campaign_price_from_title(title: str):
    m = TITLE_PRICE_RE.search(title or "")
    return parse_price(m.group(1) + " TL") if m else None


def parse_campaign_page(html: str, url: str, title: str, observed) -> tuple[CatalogData, list[OfferData], int]:
    tree = parse(html)
    # Liste sayfasındaki başlık genel ("Kampanya Detayı") ya da adresten türetilmişse kampanya sayfasının <h1>'i esas
    h1 = useful_title(clean(text_of(tree.css_first("h1"))))
    if h1 and (not useful_title(title) or title == slug_title(url)):
        title = h1
    title = useful_title(title) or slug_title(url)
    page_text = text_of(tree.body) if tree.body else ""
    vf_match = VALID_FROM_RE.search(page_text)
    valid_from = parse_tr_date(vf_match.group(1), observed.date()) if vf_match else None
    range_from, range_to = (None, None)
    if "tarihleri arasında" in page_text:
        range_from, range_to = parse_tr_range(page_text, observed.date())
    cid = "sok-" + CAMPAIGN_RE.search(url).group(0).replace("-prm-", "")[:12]
    catalog = CatalogData(id=cid, title=title, kind="campaign", official_url=url,
                          valid_from=range_from or valid_from, valid_to=range_to)
    title_price = campaign_price_from_title(title)
    is_bogo = "1 alana 1 bedava" in title.lower()
    condition = None
    if is_bogo:
        condition = "1 Alana 1 Bedava — sepete 2 adet eklenmeli"
    elif "50 tl" in title.lower():
        condition = "50 TL ve üzeri alışverişte"
    offers, rejected, seen = [], 0, set()
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        if not PRODUCT_RE.search(href):
            continue
        full = urljoin(BASE, href)
        if full in seen:
            continue
        card = ancestor_with(a, lambda n: bool(price_tokens(text_of(n))), max_up=5)
        if card is None:
            continue
        seen.add(full)
        name = card_name(card, a)
        image = card_image(card)
        toks = price_tokens(text_of(card))
        listed = parse_price(toks[0]) if toks else None
        price = title_price if title_price is not None else listed
        if not name or price is None:
            rejected += 1
            continue
        old = listed if (title_price is not None and listed is not None and listed > title_price) else None
        offers.append(OfferData(name=clean(name), price=price,
                                price_text=f"{title_price} TL" if title_price is not None else toks[0],
                                store_declared_old_price=old, condition=condition, source_url=url,
                                catalog_id=cid, product_url=full, channel="magaza",
                                image_url=image, image_large_url=image, image_status="listing" if image else None,
                                valid_from=catalog.valid_from, valid_to=catalog.valid_to))
    return catalog, offers, rejected


class SokConnector(Connector):
    store_id = "sok"
    method = "live_html"

    def __init__(self, max_pages: int = 8, max_list_pages: int = 5):
        self.max_pages = max_pages
        self.max_list_pages = max_list_pages

    def _discount_list(self, client: PoliteClient, result: ConnectorResult) -> None:
        offers: list[OfferData] = []
        seen: set[str] = set()
        rejected = 0
        for page in range(1, self.max_list_pages + 1):
            try:
                html = client.get(DISCOUNT_URL, params={"page": page} if page > 1 else None).text
            except ConnectorError as exc:
                if page == 1:
                    raise
                result.warnings.append(f"İndirimli ürünler sayfa {page}: {exc}")
                result.partial = True
                break
            more, rej = parse_discount_list(html)
            rejected += rej
            new = [o for o in more if o.product_url not in seen]
            if not new:
                break
            seen.update(o.product_url for o in new)
            offers.extend(new)
        if rejected:
            result.warnings.append(f"İndirimli ürünler: {rejected} ürün belirsiz fiyat nedeniyle reddedildi")
        if offers:
            result.catalogs.append(CatalogData(id=DISCOUNT_CATALOG_ID, title="Haftanın Fırsatları · İndirimli Ürünler",
                                               kind="discount_list", official_url=DISCOUNT_URL))
            result.offers.extend(offers)

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        result = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        try:
            self._discount_list(client, result)
        except ConnectorError as exc:
            # İndirim listesi okunamazsa kampanya sayfalarıyla devam edilir (kaynak içinde de izolasyon).
            result.warnings.append(f"İndirimli ürünler okunamadı: {exc}")
            result.partial = True
        list_html = client.get(LIST_URL).text
        campaigns = parse_campaign_list(list_html)
        banners = parse_campaign_banners(list_html)
        if banners:
            # Tam boy (boyutlandırmasız) adres gerçekten görsel mi? Değilse resmî küçük boyuta düşülür.
            pr = client.probe(banners[0]["url"])
            if not (pr.get("ok") and pr.get("kind") in ("jpeg", "png", "webp")):
                # Doğrulanamadı (ör. görsel sunucusu robots.txt ile kapalı): sitenin kullandığı aynı resmî CDN
                # boyutlandırma adresinin büyük boyu (width=512 → width=1600). Yeni sayfa/görsel indirilmez.
                for b in banners:
                    b["url"] = larger_thumb(b["thumb_url"])
            result.catalogs.append(CatalogData(id="sok-kampanya-gorselleri", title="ŞOK Kampanyaları",
                                               kind="campaign_gallery", official_url=LIST_URL, pages=banners,
                                               cover_url=banners[0]["thumb_url"]))
        if not campaigns:
            raise ConnectorError("ŞOK: kampanya bağlantısı bulunamadı — sayfa yapısı değişmiş olabilir")
        # Ürünsüz genel kampanyaları (ücretsiz teslimat, puan) sona at.
        campaigns.sort(key=lambda c: ("teslimat" in c[0].lower() or "puan" in c[0].lower()))
        rejected_total = 0
        for title, url in campaigns[: self.max_pages]:
            try:
                html = client.get(url).text
            except ConnectorError as exc:
                result.warnings.append(f"{title}: {exc}")
                result.partial = True
                continue
            catalog, offers, rejected = parse_campaign_page(html, url, title, observed_at)
            rejected_total += rejected
            result.catalogs.append(catalog)
            result.offers.extend(offers)
        for title, url in campaigns[self.max_pages:]:
            cid = "sok-" + CAMPAIGN_RE.search(url).group(0).replace("-prm-", "")[:12]
            result.catalogs.append(CatalogData(id=cid, title=title, kind="link_only", official_url=url))
        if rejected_total:
            result.warnings.append(f"{rejected_total} ürün reddedildi (ad/fiyat yok)")
        return result
