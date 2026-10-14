"""Migros "Migroskop" ürünleri bağlayıcısı.

Migros ürün listesini istemci tarafında, kendi ``/rest/search/screens/<sayfa>`` JSON uç noktasından yükler.
Bu uç nokta herkese açıktır (kimlik doğrulama yok) ve robots.txt'de yasaklı değildir; ancak BELGELENMİŞ RESMİ BİR API
DEĞİLDİR. Bu nedenle bağlayıcı varsayılan olarak etkin olsa da yönetim panelinden kapatılabilir ve hukuki not taşır.
Fiyatlar kuruş cinsindendir. ``regularPrice`` mağaza beyanı önceki fiyattır; ``shownPrice`` koşulları (ör. Money
üyeliği) bu veriden anlaşılamadığı için koşul alanına not düşülür.
"""
from __future__ import annotations

from datetime import datetime

from ..normalize import clean, format_try, kurus_to_decimal
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, OfferData, PoliteClient

BASE = "https://www.migros.com.tr"
PAGE_SLUG = "migroskop-urunleri-dt-3"
API_URL = f"{BASE}/rest/search/screens/{PAGE_SLUG}"
CATALOG_URL = f"{BASE}/{PAGE_SLUG}"
CATALOG_ID = "migros-migroskop"
CONDITION = "Fiyat koşulları (ör. Money üyeliği) resmî ürün sayfasında kontrol edilmeli"


def parse_search_json(payload: dict) -> tuple[list[OfferData], int, int]:
    """(ürünler, sayfa_sayısı, reddedilen) döner."""
    if not isinstance(payload, dict) or not payload.get("successful", True):
        raise ConnectorError("Migros: yanıt başarısız işaretli")
    info = (payload.get("data") or {}).get("searchInfo") or {}
    items = info.get("storeProductInfos")
    if not isinstance(items, list):
        raise ConnectorError("Migros: storeProductInfos bulunamadı — yanıt yapısı değişmiş olabilir")
    offers, rejected = [], 0
    for it in items:
        name = clean(it.get("name"))
        shown = kurus_to_decimal(it.get("shownPrice"))
        regular = kurus_to_decimal(it.get("regularPrice"))
        pretty = it.get("prettyName")
        if not name or shown is None or not pretty:
            rejected += 1
            continue
        old = regular if regular is not None and regular > shown else None
        url = f"{BASE}/{pretty}"
        urls = {}
        imgs = it.get("images")
        if isinstance(imgs, list) and imgs and isinstance(imgs[0], dict):
            urls = imgs[0].get("urls") or {}
        offers.append(OfferData(name=name, price=shown, price_text=format_try(shown), source_url=API_URL,
                                catalog_id=CATALOG_ID, product_url=url, store_declared_old_price=old,
                                condition=CONDITION, channel="online",
                                image_url=urls.get("PRODUCT_LIST") or urls.get("PRODUCT_DETAIL"),
                                image_large_url=urls.get("PRODUCT_DETAIL") or urls.get("PRODUCT_HD"),
                                image_status="official_json" if urls else None))
    return offers, int(info.get("pageCount") or 1), rejected


class MigrosConnector(Connector):
    store_id = "migros"
    method = "live_site_json"

    def __init__(self, max_pages: int = 8):
        self.max_pages = max_pages

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        result = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        resp = client.get(API_URL, headers={"Accept": "application/json"})
        try:
            payload = resp.json()
        except ValueError as exc:
            raise ConnectorError("Migros: JSON beklenirken farklı içerik geldi") from exc
        offers, pages, rejected = parse_search_json(payload)
        seen = {o.product_url for o in offers}
        # Sayfalama parametresi ('sayfa') site URL'lerinden çıkarıldı; canlı olarak DOĞRULANMADI.
        for page in range(2, min(pages, self.max_pages) + 1):
            try:
                more, _, rej = parse_search_json(client.get(API_URL, params={"sayfa": page}).json())
            except (ConnectorError, ValueError) as exc:
                result.warnings.append(f"Sayfa {page}: {exc}")
                result.partial = True
                break
            new = [o for o in more if o.product_url not in seen]
            if not new:
                result.warnings.append("Sayfalama yeni ürün döndürmedi; durduruldu")
                break
            seen.update(o.product_url for o in new)
            offers.extend(new)
            rejected += rej
        if not offers:
            raise ConnectorError("Migros: ürün bulunamadı")
        if rejected:
            result.warnings.append(f"{rejected} ürün eksik alan nedeniyle reddedildi")
        result.catalogs.append(CatalogData(id=CATALOG_ID, title="Migroskop Ürünleri", kind="discount_list",
                                           official_url=CATALOG_URL))
        result.offers = offers
        return result
