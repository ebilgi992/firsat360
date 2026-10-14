"""Ürün görsellerinin otomatik doğrulanması: her ürünün KENDİ resmî ürün sayfası açılır, sayfadaki ana görsel ve
ürün adı/ölçüsü/fiyatı okunur, imagematch.verify ile listedeki ürünle karşılaştırılır.

- Yalnızca görseli henüz doğrulanmamış veya son kontrolü RECHECK_DAYS günden eski ürünler açılır (günlük yük düşük).
- Mağaza başına çalıştırma limiti vardır; kalanlar sonraki günlerde tamamlanır.
- Görsel adresi mağazanın kendi alan adında/CDN'inde değilse kabul edilmez. Görsel dosyası indirilmez/kopyalanmaz.
- robots.txt bir sayfaya izin vermiyorsa o mağaza için durulur.
"""
from __future__ import annotations

import logging
from datetime import datetime, timedelta, timezone
from urllib.parse import urlparse

from sqlalchemy import or_, select
from sqlalchemy.orm import sessionmaker

from .connectors.base import ConnectorError, PoliteClient, RobotsDisallowed
from .connectors.extra import SKIP_IMG, page_meta
from .db import Offer
from .imagematch import PageImage, apply_to_db

log = logging.getLogger("firsat360.productpages")

# Mağaza → ürün sayfası alan adı ve kabul edilen görsel alan adları
STORE_HOSTS = {
    "bim": ("bim.com.tr", ("cdn1.bim.com.tr", "bim.com.tr")),
    "a101": ("a101.com.tr", ("a101.com.tr",)),
    "sok": ("sokmarket.com.tr", ("images.ceptesok.com", "sokmarket.com.tr")),
    "tkkoop": ("tkkoop.com.tr", ("tkkoop.com.tr",)),
}
RECHECK_DAYS = 7


def _host_ok(url: str | None, hosts: tuple[str, ...]) -> bool:
    if not url:
        return False
    h = urlparse(url).netloc.lower()
    return any(h == x or h.endswith("." + x) for x in hosts)


def page_row(store: str, url: str, html: str) -> PageImage:
    m = page_meta(html, url)
    img = m["image"]
    if img and (SKIP_IMG.search(img) or not _host_ok(img, STORE_HOSTS[store][1])):
        img = None
    return PageImage(store=store, detail_url=url, page_name=m["name"], page_variant=None,
                     page_price_text=m["price_text"], image_url=img)


def verify_product_pages(sf: sessionmaker, client: PoliteClient, per_store: int = 150,
                         now: datetime | None = None, stores: tuple[str, ...] | None = None) -> dict:
    now = now or datetime.now(timezone.utc)
    out: dict = {}
    for store, (page_host, _) in STORE_HOSTS.items():
        if stores and store not in stores:
            continue
        with sf() as s:
            todo = s.scalars(select(Offer).where(
                Offer.store_id == store, Offer.published.is_(True), Offer.product_url.is_not(None),
                or_(Offer.image_status.is_(None), Offer.image_status != "verified",
                    Offer.image_checked_at.is_(None), Offer.image_checked_at < now - timedelta(days=RECHECK_DAYS)))
                .order_by(Offer.image_checked_at.is_not(None), Offer.image_checked_at).limit(per_store)).all()
            urls = [o.product_url for o in todo if _host_ok(o.product_url, (page_host,))]
        rows, errors = [], 0
        for u in dict.fromkeys(urls):
            try:
                rows.append(page_row(store, u, client.get(u).text))
            except RobotsDisallowed:
                log.warning("%s: robots.txt ürün sayfalarına izin vermiyor; durduruldu", store)
                break
            except ConnectorError as exc:
                errors += 1
                if "403" in str(exc) or "429" in str(exc):
                    break  # erişim engeli/hız sınırı: zorlamadan dur
        stats = {}
        if rows:
            with sf() as s:
                stats = apply_to_db(s, rows, now).get(store, {})
                s.commit()
        out[store] = {"pages": len(rows), "errors": errors, **stats}
    return out
