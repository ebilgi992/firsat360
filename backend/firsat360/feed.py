"""Uygulamanın okuduğu JSON akışı (feed). API ve statik dışa aktarma aynı fonksiyonu kullanır."""
from __future__ import annotations

import json
from datetime import date, datetime, timezone
from decimal import Decimal
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from . import __version__
from .config import STORE_KIND, STORE_STYLE, STORES, CHAIN_RANK, BROCHURE_URLS
from .db import Catalog, Offer, PriceObservation, Store
from .normalize import CATEGORIES

SCHEMA_VERSION = 2

METHOD_LABELS = {
    "manual_snapshot": "Resmî sayfadan alınan anlık görüntü",
    "live_html": "Resmî sayfadan otomatik okuma",
    "live_embedded_json": "Resmî sayfaya gömülü veriden otomatik okuma",
    "live_site_json": "Sitenin kendi veri ucundan otomatik okuma",
    "live_html_afis": "Resmî afiş sayfasından otomatik okuma",
}


IMAGE_SOURCE = {"verified": "product_page_verified", "listing": "official_listing", "official_json": "official_json"}


def _iso(v):
    if v is None:
        return None
    if isinstance(v, datetime):
        if v.tzinfo is None:
            v = v.replace(tzinfo=timezone.utc)
        return v.isoformat()
    return v.isoformat()


def _num(v: Decimal | None):
    return None if v is None else float(v)


def _images_allowed(o_store: Store | None) -> bool:
    return o_store is not None and (o_store.image_policy or "official_cdn") == "official_cdn"


def catalog_pages(c: Catalog, allowed: bool) -> list[dict]:
    if not allowed or not c.pages_json:
        return []
    try:
        pages = json.loads(c.pages_json)
    except ValueError:
        return []
    return [p for p in pages if isinstance(p, dict) and p.get("url")]


def offer_dict(o: Offer, history: list[PriceObservation] | None = None, images: bool = True) -> dict:
    d = {
        "id": o.id, "store_id": o.store_id, "catalog_id": o.catalog_id,
        "name": o.name, "brand": o.brand, "variant": o.variant,
        "category": o.category, "category_source": o.category_source,
        "price": _num(o.price), "price_text": o.price_text, "currency": o.currency,
        "store_declared_old_price": _num(o.store_declared_old_price),
        "condition": o.condition, "channel": o.channel,
        "product_url": o.product_url,
        "image_url": o.image_url if images else None,
        "image_large_url": (o.image_large_url or o.image_url) if images else None,
        # Görsel kaynağı: ürünün kendi resmî sayfasından doğrulandı / resmî listede kartla birlikte / resmî JSON
        "image_source": IMAGE_SOURCE.get(o.image_status or "", "official_cdn") if (images and o.image_url) else None,
        "image_status": o.image_status,
        "image_page_url": o.image_page_url,
        "image_checked_at": _iso(o.image_checked_at),
        "page_price_conflict": _num(o.page_price_conflict),
        "page_size_text": o.page_size_text,
        "valid_from": _iso(o.valid_from), "valid_to": _iso(o.valid_to),
        "stock_status": o.stock_status, "scope": o.scope,
        "observed_at": _iso(o.last_seen_at), "first_seen_at": _iso(o.first_seen_at),
        "source_url": o.source_url, "source_method": o.source_method,
        "verification": o.verification, "data_mode": o.data_mode,
    }
    if history is not None:
        d["price_history"] = [{"price": _num(h.price), "observed_at": _iso(h.observed_at)} for h in history]
    return d


def build_feed(session: Session, today: date | None = None, include_expired: bool = False) -> dict:
    today = today or datetime.now(timezone.utc).date()
    stores = session.scalars(select(Store).order_by(Store.id)).all()
    catalogs = session.scalars(select(Catalog).where(Catalog.data_mode == "real")).all()
    offers = session.scalars(select(Offer).where(Offer.published.is_(True), Offer.data_mode == "real")).all()
    if not include_expired:
        offers = [o for o in offers if o.valid_to is None or o.valid_to >= today]
        catalogs = [c for c in catalogs if c.valid_to is None or c.valid_to >= today]
    # Fiyat geçmişi (gözlemler) feed'e eklenir; uygulama detay ekranında grafik/özet gösterir.
    history: dict[str, list[PriceObservation]] = {}
    for h in session.scalars(select(PriceObservation).order_by(PriceObservation.observed_at)):
        history.setdefault(h.offer_id, []).append(h)
    store_by_id = {s.id: s for s in stores}
    counts: dict[str, int] = {}
    for o in offers:
        counts[o.catalog_id or ""] = counts.get(o.catalog_id or "", 0) + 1
    return {
        "schema_version": SCHEMA_VERSION,
        "backend_version": __version__,
        "generated_at": _iso(datetime.now(timezone.utc)),
        "data_mode": "real",
        "notice": ("Fiyatlar mağazaların resmî sayfalarından okunmuştur. Önceki fiyatlar mağaza beyanıdır ve "
                   "FIRSAT360 tarafından doğrulanmamıştır. Stok ve şube geçerliliği bilinmemektedir."),
        "method_labels": METHOD_LABELS,
        "categories": [{"id": k, "name": v} for k, v in CATEGORIES.items()],
        "stores": [{
            "id": s.id, "name": s.name, "official_url": s.official_url, "catalog_url": s.catalog_url,
            "enabled": s.enabled, "status": s.status, "status_message": s.status_message,
            "last_checked_at": _iso(s.last_checked_at), "last_success_at": _iso(s.last_success_at),
            "last_error": s.last_error, "legal_note": s.legal_note, "image_policy": s.image_policy or "official_cdn",
            "color": STORE_STYLE.get(s.id, ("#5B6472", s.name[:2]))[0],
            "short": STORE_STYLE.get(s.id, ("#5B6472", s.name[:2]))[1],
            "kind": STORE_KIND.get(s.id, "market"),
            "sort": list(STORES).index(s.id) if s.id in STORES else 99,
            "chain_rank": CHAIN_RANK.index(s.id) if s.id in CHAIN_RANK else 99,
            "brochure_url": BROCHURE_URLS.get(s.id),
        } for s in sorted(stores, key=lambda s: list(STORES).index(s.id) if s.id in STORES else 99)],
        "catalogs": [{
            "id": c.id, "store_id": c.store_id, "title": c.title, "kind": c.kind,
            "valid_from": _iso(c.valid_from), "valid_to": _iso(c.valid_to), "official_url": c.official_url,
            "source_method": c.source_method, "observed_at": _iso(c.observed_at),
            "offer_count": counts.get(c.id, 0),
            "first_seen_at": _iso(c.first_seen_at), "last_checked_at": _iso(c.last_checked_at),
            "pdf_url": c.pdf_url if _images_allowed(store_by_id.get(c.store_id)) else None,
            "cover_url": c.cover_url if _images_allowed(store_by_id.get(c.store_id)) else None,
            "pages": catalog_pages(c, _images_allowed(store_by_id.get(c.store_id))),
        } for c in sorted(catalogs, key=lambda c: (c.store_id, c.valid_from or date.min), reverse=False)],
        "offers": [offer_dict(o, history.get(o.id, []), _images_allowed(store_by_id.get(o.store_id))) for o in
                   sorted(offers, key=lambda o: (o.store_id, o.catalog_id or "", o.name))],
        # Günün İndirimleri (otomatik seçim) ve yeni katalog duyuruları (bildirimler bunlardan üretilir)
        "daily_deals": _deals(session, today),
        "announcements": _announcements(session),
        # Uzaktan yapılandırma (yönetim panelinden): reklam alanları vb. Gizli bilgi içermez.
        "app_config": _app_config(session),
        # Gelir altyapısı: yalnızca onaylı ve etkin olanlar "active" işaretlenir. Şu an hiçbiri onaylı değil.
        "monetization": {
            "ads": {"active": False, "note": "Reklam SDK'sı ve onaylı reklam hesabı yok"},
            "affiliate": {"active": False, "note": "Onaylı satış ortaklığı programı yok; bağlantılar doğrudan mağazaya gider"},
            "sponsored": [],
            "premium": {"active": False},
        },
    }


def _deals(session: Session, today: date) -> dict:
    from .deals import daily_deals
    return daily_deals(session, today)


DEFAULT_ADS = {"enabled": False, "home_banner": False, "list_banner": False, "native_every": 0, "admob_app_id": "",
               "banner_unit_id": "", "native_unit_id": "", "test_mode": True}


def _app_config(session: Session) -> dict:
    """Uzaktan yapılandırma: depodaki config/app_config.json (GitHub web arayüzünden düzenlenir; sunucu gerekmez)."""
    from .config import ROOT
    ads = dict(DEFAULT_ADS)
    try:
        data = json.loads((ROOT / "config" / "app_config.json").read_text(encoding="utf-8"))
        ads.update({k: v for k, v in (data.get("ads") or {}).items() if k in DEFAULT_ADS})
    except (OSError, ValueError):
        pass
    return {"ads": ads}


def _announcements(session: Session) -> list[dict]:
    from .sync import announcements_for_feed
    return announcements_for_feed(session, datetime.now(timezone.utc))


def export_feed(session: Session, path: str | Path, today: date | None = None) -> Path:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    data = build_feed(session, today=today)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(p)
    return p
