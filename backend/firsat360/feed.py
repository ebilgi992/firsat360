"""Uygulamanın okuduğu JSON akışı (feed). API ve statik dışa aktarma aynı fonksiyonu kullanır."""
from __future__ import annotations

import hashlib
import re

import json
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from pathlib import Path

from sqlalchemy import select
from sqlalchemy.orm import Session

from . import __version__
from .normalize import clean_variant
from .config import STORE_KIND, STORE_STYLE, STORES, CHAIN_RANK, BROCHURE_URLS, RETIRED_STORES
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
        "name": o.name, "brand": o.brand, "variant": clean_variant(o.variant),
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


STALE_DAYS = 2
# Bitiş tarihi olmayan ürün/katalog, kaynakta bu kadar gün görülmezse yayından çıkar (ör. erişimi engellenen bir
# kaynağın haftalar önceki fiyatları "güncel" gibi gösterilmez).
STALE_UNDATED_DAYS = 5
# Mağaza ekranındaki "Geçmiş kataloglar": süresi bu kadar gün içinde bitmiş, sayfası/PDF'i/kapağı olan kataloglar.
ARCHIVE_DAYS = 90


def merge_variant_duplicates(offers: list[dict]) -> list[dict]:
    """Aynı katalogda aynı ad + aynı açıklamayla birden çok fiyat (ör. BİM'de aynı ürün sayfasının iki boyu, açıklaması
    okunamamış): tek kart, en düşük fiyat; diğer fiyatlar "options" listesinde. Açıklaması farklı olanlar ayrı kalır."""
    from .normalize import fold
    groups: dict[tuple, list[dict]] = {}
    order: list[tuple] = []
    for o in offers:
        k = (o["store_id"], o.get("catalog_id") or "", fold(o.get("name") or ""), fold(o.get("variant") or ""))
        if k not in groups:
            groups[k] = []
            order.append(k)
        groups[k].append(o)
    out = []
    for k in order:
        g = groups[k]
        if len(g) == 1:
            out.append(g[0])
            continue
        g = sorted(g, key=lambda x: (x["price"] is None, x["price"] or 0))
        head = dict(g[0])
        head["options"] = [{"id": x["id"], "price": x["price"], "variant": x.get("variant") or x.get("page_size_text")}
                           for x in g]
        head["aliases"] = [x["id"] for x in g[1:]]
        out.append(head)
    return out


def catalog_group(c: Catalog) -> str:
    """Aynı yayının farklı kopyaları (bölge sürümleri, farklı adresler) için ortak anahtar: mağaza + bölge eki
    çıkarılmış normalleştirilmiş başlık + geçerlilik tarihleri. Uygulama aynı gruptan tek kart gösterir."""
    from .normalize import fold
    title = re.sub(r"\s*·\s*[^·]+$", "", c.title) if c.region else c.title
    key = f"{c.store_id}|{fold(title)}|{c.valid_from or ''}|{c.valid_to or ''}"
    return hashlib.sha1(key.encode(), usedforsecurity=False).hexdigest()[:12]


def _first_page_url(c: Catalog) -> str | None:
    if not c.pages_json:
        return None
    try:
        pages = json.loads(c.pages_json)
    except ValueError:
        return None
    if isinstance(pages, list) and pages and isinstance(pages[0], dict):
        return (pages[0].get("url") or "").split("?")[0] or None
    return None


def catalog_groups(catalogs: list[Catalog]) -> dict[str, str]:
    """catalog_group + içerik eşlemesi: aynı mağazanın ilk sayfa görseli aynı olan ya da içerik özeti (content_hash)
    aynı olan katalogları (ör. BİM ürün sekmesi bim-1701 ile aynı afişleri taşıyan bim-afis-1432) tek gruba bağlar.
    Dönüş: {katalog kimliği: grup}. Grup, birleşen kataloglardan en küçük temel grup anahtarıdır (kararlı)."""
    parent = {c.id: c.id for c in catalogs}

    def find(x):
        while parent[x] != x:
            parent[x] = parent[parent[x]]
            x = parent[x]
        return x

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[max(ra, rb)] = min(ra, rb)

    base = {c.id: catalog_group(c) for c in catalogs}
    by_key: dict[tuple, str] = {}
    for c in sorted(catalogs, key=lambda c: c.id):
        keys = [("g", c.store_id, base[c.id])]
        first = _first_page_url(c)
        if first:
            keys.append(("p", c.store_id, first))
        if c.content_hash:
            keys.append(("h", c.store_id, c.content_hash))
        for k in keys:
            if k in by_key:
                union(by_key[k], c.id)
            else:
                by_key[k] = c.id
    members: dict[str, list[str]] = {}
    for c in catalogs:
        members.setdefault(find(c.id), []).append(c.id)
    out = {}
    for ids in members.values():
        g = min(base[i] for i in ids)
        for i in ids:
            out[i] = g
    return out


def is_stale(c: Catalog, store: Store | None, catalogs_with_offers: set) -> bool:
    """Kaynakta artık listelenmeyen katalog: mağazanın son başarılı okumasından STALE_DAYS gün önce son görülmüş
    ve yayında ürünü yok. (Ör. eski anlık görüntüden kalan, yeni sayısıyla mükerrer görünen Migroskop kaydı.)"""
    if c.id in catalogs_with_offers or store is None or store.last_success_at is None or c.last_checked_at is None:
        return False
    seen, ok = _aware(c.last_checked_at), _aware(store.last_success_at)
    return (ok - seen).total_seconds() > STALE_DAYS * 86400


def _aware(d):
    return d if d is None or d.tzinfo else d.replace(tzinfo=timezone.utc)


def archive_catalogs(session: Session, today: date, store_by_id: dict) -> list[dict]:
    old = today - timedelta(days=ARCHIVE_DAYS)
    rows = session.scalars(select(Catalog).where(Catalog.data_mode == "real", Catalog.valid_to.is_not(None),
                                                 Catalog.valid_to < today, Catalog.valid_to >= old)).all()
    groups = catalog_groups(list(rows))
    out = []
    for c in sorted(rows, key=lambda c: (c.store_id, c.valid_to), reverse=True):
        if c.store_id not in store_by_id:
            continue
        img = _images_allowed(store_by_id.get(c.store_id))
        pages = catalog_pages(c, img)
        pdf = c.pdf_url if img else None
        cover = c.cover_url if img else None
        if not (pages or pdf or cover):
            continue
        out.append({"id": c.id, "store_id": c.store_id, "title": c.title, "kind": c.kind,
                    "valid_from": _iso(c.valid_from), "valid_to": _iso(c.valid_to), "official_url": c.official_url,
                    "source_method": c.source_method, "observed_at": _iso(c.observed_at), "offer_count": 0,
                    "pdf_url": pdf, "cover_url": cover or ((pages[0].get("thumb_url") or pages[0]["url"]) if pages else None),
                    "pages": pages, "group": groups[c.id], "archived": True})
    return out


def client_bridge() -> dict:
    """config/client_bridge.json → feed'deki "client_bridge" (statik eşleme; sunucu bu adresleri hiç açmaz)."""
    p = Path(__file__).resolve().parents[2] / "config" / "client_bridge.json"
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
    except (OSError, ValueError):
        return {}
    out = {}
    for sid, m in (data.get("markets") or {}).items():
        url = str(m.get("url") or "")
        if url.startswith("https://") and m.get("client_rendered") and re.fullmatch(r"[a-z0-9]+", str(m.get("profile", ""))):
            out[sid] = {"url": url, "profile": m["profile"], "allowed_hosts": [str(h) for h in m.get("allowed_hosts", [])],
                        "client_rendered": True}
    return out


def build_feed(session: Session, today: date | None = None, include_expired: bool = False) -> dict:
    today = today or datetime.now(timezone.utc).date()
    stores = session.scalars(select(Store).order_by(Store.id)).all()
    catalogs = session.scalars(select(Catalog).where(Catalog.data_mode == "real")).all()
    offers = session.scalars(select(Offer).where(Offer.published.is_(True), Offer.data_mode == "real")).all()
    # Kaldırılan marketler (RETIRED_STORES): eski kayıtları veritabanında kalsa da yayına girmez.
    stores = [s for s in stores if s.id not in RETIRED_STORES]
    catalogs = [c for c in catalogs if c.store_id not in RETIRED_STORES]
    offers = [o for o in offers if o.store_id not in RETIRED_STORES]
    if not include_expired:
        offers = [o for o in offers if o.valid_to is None or o.valid_to >= today]
        catalogs = [c for c in catalogs if c.valid_to is None or c.valid_to >= today]
        undated_cut = datetime.combine(today - timedelta(days=STALE_UNDATED_DAYS), datetime.min.time(), tzinfo=timezone.utc)
        offers = [o for o in offers if not (o.valid_to is None and _aware(o.last_seen_at) is not None
                                            and _aware(o.last_seen_at) < undated_cut)]
        catalogs = [c for c in catalogs if not is_stale(c, {s.id: s for s in stores}.get(c.store_id),
                                                        {o.catalog_id for o in offers})]
        with_offers = {o.catalog_id for o in offers}
        catalogs = [c for c in catalogs if not (c.valid_to is None and c.id not in with_offers
                                                and _aware(c.last_checked_at) is not None
                                                and _aware(c.last_checked_at) < undated_cut)]
    # Fiyat geçmişi (gözlemler) feed'e eklenir; uygulama detay ekranında grafik/özet gösterir.
    history: dict[str, list[PriceObservation]] = {}
    for h in session.scalars(select(PriceObservation).order_by(PriceObservation.observed_at)):
        history.setdefault(h.offer_id, []).append(h)
    store_by_id = {s.id: s for s in stores}
    groups = catalog_groups(catalogs)
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
            "region": c.region,
            "group": groups[c.id],
            "cover_url": c.cover_url if _images_allowed(store_by_id.get(c.store_id)) else None,
            "pages": catalog_pages(c, _images_allowed(store_by_id.get(c.store_id))),
        } for c in sorted(catalogs, key=lambda c: (c.store_id, c.valid_from or date.min), reverse=False)],
        "offers": merge_variant_duplicates([offer_dict(o, history.get(o.id, []), _images_allowed(store_by_id.get(o.store_id)))
                                            for o in sorted(offers, key=lambda o: (o.store_id, o.catalog_id or "", o.name))]),
        # Geçmiş kataloglar (mağaza ekranında tarihe göre): yalnızca okunabilir olanlar (sayfa, PDF veya kapak)
        "archive": archive_catalogs(session, today, store_by_id),
        # Günün İndirimleri (otomatik seçim) ve yeni katalog duyuruları (bildirimler bunlardan üretilir)
        "daily_deals": _deals(session, today),
        "client_bridge": client_bridge(),
        "announcements": _announcements(session, today),
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


def _announcements(session: Session, today: date | None = None) -> list[dict]:
    """Yayındaki duyurular. Geçmiş/gelecek bir gün için üretilen feed o günün saatini kullanır (testler, yeniden üretim)."""
    from .sync import announcements_for_feed
    now = datetime.now(timezone.utc)
    if today is not None and today != now.date():
        now = datetime.combine(today, datetime.max.time(), tzinfo=timezone.utc)
    return announcements_for_feed(session, now)


def export_feed(session: Session, path: str | Path, today: date | None = None) -> Path:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    data = build_feed(session, today=today)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=1), encoding="utf-8")
    tmp.replace(p)
    return p
