"""BİM resmî aktüel sayfası bağlayıcısı.

Sayfa sunucu tarafında üretilen HTML'dir. Tarih sekmeleri ``?Bim_AktuelTarihKey=<id>`` parametresiyle açılır.
Yalnızca olgusal veri alınır: marka, ürün adı, açıklama, fiyat, resmî ürün bağlantısı. Görseller kopyalanmaz.
"""
from __future__ import annotations

import re
from datetime import date, datetime, timedelta
from urllib.parse import urljoin

from ..normalize import clean, parse_price, parse_tr_range
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, OfferData, PoliteClient
from .htmlutil import ancestor_with, find_image, img_src, parse, price_tokens, text_of

BASE = "https://www.bim.com.tr"
LIST_URL = BASE + "/categories/100/aktuel-urunler.aspx"
AFIS_URL = BASE + "/Categories/680/afisler.aspx"
AFIS_KEY_RE = re.compile(r"Bim_AfisKey=(\d+)")
KEY_RE = re.compile(r"Bim_AktuelTarihKey=(\d+)")
DETAIL_RE = re.compile(r"/aktuel-urunler/[^/]+/aktuel\.aspx")
# Ürün görseli dosya adı kalıbı: <katalog anahtarı>_kucuk_… (liste) / _buyuk_… (detay). Aynı klasördeki reklam
# afişleri (ör. "986_aycicek-yagi-…-hc.jpg") bu kalıba uymaz → ürüne yanlış görsel bağlanmaz.
PRODUCT_IMG_RE = re.compile(r"/uploads/aktuel-urunler/\d+_(?:kucuk|buyuk)_", re.I)


# Tek günlük aktüel sekmesi ("30 Ekim Perşembe") bitiş tarihi yazmaz; BİM aktüel ürünleri stoklarla sınırlı olarak
# bir hafta satışta kalır → bitiş = başlangıç + 6 gün (bitişsiz eski sekme süresiz "güncel" görünmesin).
AKTUEL_DAYS = 6


def aktuel_range(label: str, observed: date) -> tuple[date | None, date | None]:
    start, end = parse_tr_range(label, observed)
    if start is not None and end is None:
        end = start + timedelta(days=AKTUEL_DAYS)
    return start, end


def parse_tabs(html: str, observed: date) -> list[CatalogData]:
    tree = parse(html)
    seen: dict[str, CatalogData] = {}
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        m = KEY_RE.search(href)
        label = text_of(a)
        if not m or not label or not re.search(r"\d", label):
            continue
        key = m.group(1)
        if key in seen:
            continue
        start, end = aktuel_range(label, observed)
        seen[key] = CatalogData(id=f"bim-{key}", title=f"{label} Aktüel", kind="aktuel",
                                official_url=f"{LIST_URL}?Bim_AktuelTarihKey={key}",
                                valid_from=start, valid_to=end)
    return list(seen.values())


def _variant_text(card) -> str | None:
    parts = []
    for sel in (".textArea", ".text", "ul li", ".description"):
        for n in card.css(sel):
            t = text_of(n)
            if t and not price_tokens(t) and len(t) < 200:
                parts.append(t)
        if parts:
            break
    from ..normalize import clean_variant
    return clean_variant("; ".join(dict.fromkeys(parts))) if parts else None


def parse_products(html: str, page_url: str, catalog_id: str | None) -> tuple[list[OfferData], int]:
    """(ürünler, reddedilen_sayısı) döner. Fiyatı ayrıştırılamayan ürün yayına alınmaz, reddedilir."""
    tree = parse(html)
    cards = tree.css("div.product")
    if not cards:  # Yapı değişmişse: ürün bağlantısından yukarı çıkarak kart bul
        cards, ids = [], set()
        for a in tree.css("a[href]"):
            if DETAIL_RE.search(a.attributes.get("href") or ""):
                card = ancestor_with(a, lambda n: bool(price_tokens(text_of(n))) and len(n.css("h2, h3")) >= 1)
                if card is not None and card.mem_id not in ids:
                    ids.add(card.mem_id)
                    cards.append(card)
    offers: list[OfferData] = []
    rejected = 0
    seen = set()
    for card in cards:
        brand = text_of(card.css_first(".subTitle")) or None
        name = text_of(card.css_first(".title")) or None
        if not name:
            heads = [text_of(h) for h in card.css("h2, h3") if text_of(h)]
            if len(heads) >= 2:
                brand, name = heads[0], heads[1]
            elif heads:
                name = heads[0]
        price_node = card.css_first(".quantify") or card.css_first(".priceArea")
        price_text = text_of(price_node) if price_node else None
        if not price_text or not price_tokens(price_text + " ₺"):
            toks = price_tokens(text_of(card))
            price_text = toks[0] if toks else None
        price = parse_price(price_text)
        if not name or price is None:
            rejected += 1
            continue
        link = None
        for a in card.css("a[href]"):
            href = a.attributes.get("href") or ""
            if DETAIL_RE.search(href):
                link = urljoin(BASE, href)
                break
        variant = _variant_text(card)
        image = find_image(card, "/uploads/aktuel-urunler/", pattern=PRODUCT_IMG_RE)
        image = urljoin(BASE, image) if image else None
        key = (brand, name, variant, price)
        if key in seen:
            continue
        seen.add(key)
        offers.append(OfferData(name=clean(name), brand=clean(brand) if brand and brand != "-" else None,
                                variant=variant, price=price, price_text=clean(price_text), source_url=page_url,
                                catalog_id=catalog_id, product_url=link or page_url, channel="magaza",
                                image_url=image, image_large_url=image, image_status="listing" if image else None))
    return offers, rejected


def parse_afis(html: str) -> dict[str, dict]:
    """Resmî afiş sayfası → {anahtar: {"label", "column", "pages": [...]}}.

    Tarih sekmeleri ``Bim_AfisKey`` bağlantılarından okunur. Afiş görselleri (``/uploads/afisler/``) belge sırasıyla,
    kendilerinden önce gelen son tarih etiketine atanır. Tam boy adres: görselin bağlı olduğu indirme bağlantısı; yoksa
    küçük resim adından ``k_`` öneki kaldırılarak türetilir (``verified=False``; uygulama yüklenemezse küçük resme düşer).
    """
    tree = parse(html)
    labels: dict[str, tuple[str, str]] = {}
    for a in tree.css("a[href]"):
        m = AFIS_KEY_RE.search(a.attributes.get("href") or "")
        label = text_of(a)
        if m and label and re.search(r"\d", label) and label not in labels:
            column = "indirim" if re.search(r"\d+\s*(?:-|–)\s*\d+|\d+\s+\S+\s*-\s*\d+", label) else "aktuel"
            labels[label] = (m.group(1), column)
    groups: dict[str, dict] = {}
    current = None
    seen: set[str] = set()
    for node in tree.root.traverse(include_text=False) if tree.root is not None else []:
        own = (node.text(deep=False) or "").strip()
        if own in labels and node.tag not in ("a",):
            current = own
            continue
        if node.tag != "img":
            continue
        src = img_src(node)
        if not src or "/uploads/afisler/" not in src:
            continue
        thumb = urljoin(BASE, src)
        if thumb in seen:
            continue
        seen.add(thumb)
        parent_a = node.parent
        while parent_a is not None and parent_a.tag != "a":
            parent_a = parent_a.parent
        href = (parent_a.attributes.get("href") or "") if parent_a is not None else ""
        name = thumb.rsplit("/", 1)[-1]
        if "/uploads/afisler/" in href:
            full, verified = urljoin(BASE, href), True
        else:
            full, verified = thumb.rsplit("/", 1)[0] + "/" + (name[2:] if name.startswith("k_") else name), not name.startswith("k_")
        label = current or "Afişler"
        key, column = labels.get(label, ("0", "aktuel"))
        g = groups.setdefault(key, {"label": label, "column": column, "pages": []})
        n = len(g["pages"]) + 1
        g["pages"].append({"url": full, "thumb_url": thumb, "page": n, "verified": verified,
                           "caption": f"{label} · sayfa {n}", "link": f"{AFIS_URL}?top=1&Bim_AfisKey={key}"})
    return groups


def attach_afis(catalogs: list[CatalogData], groups: dict[str, dict], observed: date, id_prefix: str = "bim-afis-") -> None:
    """Afiş sayfalarını aynı tarihli ürün kataloğuna bağlar; eşi yoksa yalnızca afişten oluşan katalog ekler."""
    by_label = {c.title.replace(" İndirimleri", "").replace(" Aktüel", ""): c for c in catalogs}
    for key, g in groups.items():
        pages = sorted(g["pages"], key=lambda p: p["page"])
        if not pages:
            continue
        target = by_label.get(g["label"])
        if target is None:
            start, end = (parse_tr_range(g["label"], observed) if g["column"] == "indirim"
                          else aktuel_range(g["label"], observed))
            if g["column"] == "indirim":
                title, kind = f"{g['label']} İndirimleri", "discount_list"
            else:
                title, kind = f"{g['label']} Aktüel", "aktuel"
            target = CatalogData(id=f"{id_prefix}{key}", title=title, kind=kind, official_url=pages[0]["link"],
                                 valid_from=start, valid_to=end)
            catalogs.append(target)
        target.pages = pages
        target.cover_url = pages[0]["thumb_url"]


class BimConnector(Connector):
    store_id = "bim"
    method = "live_html"

    def __init__(self, max_pages: int = 8):
        self.max_pages = max_pages

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        result = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        first = client.get(LIST_URL).text
        tabs = parse_tabs(first, observed_at.date())
        if not tabs:
            raise ConnectorError("BİM: tarih sekmesi bulunamadı — sayfa yapısı değişmiş olabilir")
        today = observed_at.date()
        tabs = [t for t in tabs if t.valid_from is None or (t.valid_to or t.valid_from) >= today.replace(day=1)]
        tabs.sort(key=lambda t: (t.valid_from or today), reverse=True)
        total_rejected = 0
        for tab in tabs[: self.max_pages]:
            try:
                html = client.get(tab.official_url).text
            except ConnectorError as exc:
                result.warnings.append(f"{tab.title}: {exc}")
                result.partial = True
                continue
            offers, rejected = parse_products(html, tab.official_url, tab.id)
            total_rejected += rejected
            for o in offers:
                o.valid_from, o.valid_to = tab.valid_from, tab.valid_to
            if offers:
                result.catalogs.append(tab)
                result.offers.extend(offers)
        if total_rejected:
            result.warnings.append(f"{total_rejected} ürün fiyatı/adı ayrıştırılamadığı için reddedildi")
        # Afişler (katalog sayfa görselleri): ayrı sayfa; hata ürünleri etkilemez.
        try:
            groups = parse_afis(client.get(AFIS_URL).text)
            if groups:
                attach_afis(result.catalogs, groups, observed_at.date())
            else:
                result.warnings.append("Afiş sayfasında görsel bulunamadı")
        except ConnectorError as exc:
            result.warnings.append(f"Afişler okunamadı: {exc}")
        if not result.offers:
            raise ConnectorError("BİM: hiçbir sekmeden ürün ayrıştırılamadı")
        return result
