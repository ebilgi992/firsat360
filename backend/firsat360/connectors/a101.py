"""A101 "Aldın Aldın" resmî kampanya sayfası bağlayıcısı.

Önce sayfaya gömülü JSON (Next.js ``__NEXT_DATA__``) aranır; bulunamazsa HTML ürün bağlantıları (``_p-<id>``)
ayrıştırılır. Üstü çizili fiyat yalnızca HTML'de açıkça işaretliyse (del/s/line-through) "mağaza beyanı önceki fiyat"
olarak alınır; işaretsiz iki fiyat varsa ürün belirsiz sayılır ve reddedilir.
"""
from __future__ import annotations

import json
import re
from datetime import datetime
from urllib.parse import urljoin

from ..normalize import clean, parse_price
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, OfferData, PoliteClient
from .htmlutil import ancestor_with, find_image, is_struck, parse, price_tokens, text_of

BASE = "https://www.a101.com.tr"
LIST_URL = BASE + "/aldin-aldin"
PRODUCT_RE = re.compile(r"_p-\d+")
CATALOG_ID = "a101-aldin-aldin"


def _channel(url: str) -> str:
    return "online" if "/kapida/" in url else "bilinmiyor"


def parse_embedded_json(html: str) -> list[OfferData]:
    """__NEXT_DATA__ içinde {name, price(str)} + url içeren nesneleri arar.

    Sayısal (birimi belirsiz) fiyatlar kabul edilmez — kuruş mu TL mi olduğu doğrulanmadan yayına alınmaz.
    """
    tree = parse(html)
    node = tree.css_first("script#__NEXT_DATA__")
    if node is None:
        return []
    try:
        data = json.loads(node.text())
    except (ValueError, TypeError):
        return []
    found: list[OfferData] = []

    def walk(obj):
        if isinstance(obj, dict):
            name = obj.get("name") or obj.get("title")
            url = obj.get("url") or obj.get("slug") or obj.get("link")
            price_raw = obj.get("priceText") or obj.get("formattedPrice")
            if isinstance(name, str) and isinstance(url, str) and PRODUCT_RE.search(url) and isinstance(price_raw, str):
                price = parse_price(price_raw)
                if price is not None:
                    full = urljoin(BASE, url)
                    old_raw = obj.get("oldPriceText") or obj.get("formattedOldPrice")
                    found.append(OfferData(name=clean(name), price=price, price_text=price_raw, source_url=LIST_URL,
                                           catalog_id=CATALOG_ID, product_url=full, channel=_channel(full),
                                           store_declared_old_price=parse_price(old_raw) if isinstance(old_raw, str) else None,
                                           category_url_hint=full))
            for v in obj.values():
                walk(v)
        elif isinstance(obj, list):
            for v in obj:
                walk(v)

    walk(data)
    return found


def parse_html_products(html: str) -> tuple[list[OfferData], int]:
    tree = parse(html)
    offers: list[OfferData] = []
    seen_urls: set[str] = set()
    rejected = 0
    for a in tree.css("a[href]"):
        href = a.attributes.get("href") or ""
        if not PRODUCT_RE.search(href):
            continue
        full = urljoin(BASE, href)
        if full in seen_urls:
            continue
        card = ancestor_with(a, lambda n: bool(price_tokens(text_of(n))), max_up=5)
        if card is None:
            continue
        seen_urls.add(full)
        name = None
        img = card.css_first("img[alt]")
        if img is not None and (img.attributes.get("alt") or "").strip():
            name = img.attributes["alt"].strip()
        if not name:
            for sel in ("h3", "h2", "[title]"):
                n = card.css_first(sel)
                if n is not None:
                    name = n.attributes.get("title") or text_of(n)
                    if name:
                        break
        current, old, unmarked = [], [], []
        for el in card.css("*"):
            own = el.text(deep=False).strip()
            toks = price_tokens(own)
            if not toks:
                continue
            (old if is_struck(el) else unmarked).extend(toks)
        if not unmarked and not old:
            unmarked = price_tokens(text_of(card))
        if len(set(unmarked)) == 1:
            current = unmarked[:1]
        elif len(set(unmarked)) > 1:
            rejected += 1  # iki işaretsiz fiyat: hangisinin güncel olduğu kesin değil → yayına alma
            continue
        price = parse_price(current[0]) if current else None
        if not name or price is None:
            rejected += 1
            continue
        image = find_image(card, "/Image/get/", exclude=("aldin-aldin_", "yerliUretim"))
        offers.append(OfferData(name=clean(name), price=price, price_text=current[0], source_url=LIST_URL,
                                image_url=image, image_large_url=image, image_status="listing" if image else None,
                                catalog_id=CATALOG_ID, product_url=full, channel=_channel(full),
                                store_declared_old_price=parse_price(old[0]) if old else None,
                                category_url_hint=full))
    return offers, rejected


def parse_jsonld_products(html: str) -> list[OfferData]:
    """Resmî sayfanın Product JSON-LD'si: birimi açık TRY fiyat, kendi ürün adresi ve CDN'i."""
    from urllib.parse import urlparse
    tree = parse(html)
    out = []
    def walk(node):
        if isinstance(node, list):
            for item in node: walk(item)
        elif isinstance(node, dict):
            types = node.get("@type", [])
            if types == "Product" or isinstance(types, list) and "Product" in types:
                name, url = node.get("name"), node.get("url")
                full = urljoin(BASE, url) if isinstance(url, str) else ""
                host = (urlparse(full).hostname or "").lower()
                offers = node.get("offers", [])
                if isinstance(offers, dict): offers = [offers]
                if isinstance(name, str) and (host == "a101.com.tr" or host.endswith(".a101.com.tr")) and full.startswith("https://") and PRODUCT_RE.search(full):
                    # Bir üründe birden çok belirsiz varyant fiyatı seçilmez.
                    values = [x for x in offers if isinstance(x, dict) and x.get("priceCurrency") == "TRY"] if isinstance(offers, list) else []
                    prices = {parse_price(str(x.get("price", ""))) for x in values}
                    prices.discard(None)
                    if len(prices) == 1:
                        price = next(iter(prices))
                        image = node.get("image")
                        if isinstance(image, list): image = image[0] if image else None
                        if isinstance(image, dict): image = image.get("url")
                        ih = (urlparse(image).hostname or "").lower() if isinstance(image, str) else ""
                        image = image if isinstance(image, str) and image.startswith("https://") and (ih == "a101.com.tr" or ih.endswith(".a101.com.tr")) else None
                        out.append(OfferData(name=clean(name), price=price, price_text=str(price) + " TL", source_url=LIST_URL,
                            catalog_id=CATALOG_ID, product_url=full, image_url=image, image_large_url=image,
                            image_status="listing" if image else None, channel=_channel(full)))
            for key, value in node.items():
                if isinstance(value, (dict, list)): walk(value)
    for script in tree.css('script[type="application/ld+json"]'):
        try: walk(json.loads(script.text()))
        except (ValueError, TypeError): continue
    grouped = {}
    for offer in out: grouped.setdefault(offer.product_url, []).append(offer)
    return [items[0] for items in grouped.values() if len({o.price for o in items}) == 1]


class A101Connector(Connector):
    store_id = "a101"
    method = "live_html"

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        result = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        html = client.get(LIST_URL).text
        offers = parse_embedded_json(html)
        if offers:
            result.method = "live_embedded_json"
        else:
            offers = parse_jsonld_products(html)
            if offers: result.method = "live_embedded_json"
        if not offers:
            offers, rejected = parse_html_products(html)
            if rejected:
                result.warnings.append(f"{rejected} ürün belirsiz fiyat nedeniyle reddedildi")
        if not offers:
            raise ConnectorError("A101: ürün ayrıştırılamadı — sayfa yapısı değişmiş veya içerik istemci tarafında yükleniyor")
        result.catalogs.append(CatalogData(id=CATALOG_ID, title="Aldın Aldın", kind="aktuel", official_url=LIST_URL))
        result.offers = offers
        return result

