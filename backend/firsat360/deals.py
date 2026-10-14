"""Günün İndirimleri: her gün birçok mağazadan otomatik seçilen ürünler.

Seçim kuralları (uydurma veri yok; yalnızca resmî kaynakta okunan fiyat ve mağaza beyanı):
- Bugün geçerli, yayında, fiyatı olan, son 3 gün içinde kaynakta görülmüş, görseli doğrulanmış/resmî olan ürünler.
- Sayfa fiyatı listeyle çelişen ürün alınmaz.
- Puan: mağazanın beyan ettiği indirim oranı (önceki fiyat → güncel fiyat) + yenilik (bugün/dün kaynağa giren aktüel).
  İndirim beyanı olmayan ürün yalnızca yeni aktüel ise aday olur.
- Çeşitlilik: mağazalar arasında sırayla; mağaza başına üst sınır. Aynı gün aynı seçim (deterministik).
"""
from __future__ import annotations

import hashlib
from datetime import date, datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.orm import Session

from .db import Catalog, Offer

NOTE = "Fiyatlar ve stok durumu mağazaya, şubeye ve saate göre değişebilir. Satın almadan önce mağazada kontrol edin."
GOOD_IMAGE = ("verified", "official_json", "listing")


def _aware(dt):
    if dt is None:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def score(o: Offer, kind: str | None, today: date, now: datetime) -> float | None:
    pct = 0.0
    if o.store_declared_old_price and o.price and o.store_declared_old_price > o.price:
        pct = float((o.store_declared_old_price - o.price) / o.store_declared_old_price * 100)
        if pct > 90:  # olağandışı beyan: güvenilmez
            return None
    first = _aware(o.first_seen_at)
    age_days = (now - first).total_seconds() / 86400 if first else 99
    fresh = 8.0 if age_days < 1 else 4.0 if age_days < 2 else 0.0
    if pct < 10 and not (kind == "aktuel" and fresh > 0):
        return None
    return pct + fresh


def daily_deals(session: Session, today: date, now: datetime | None = None, limit: int = 30,
                per_store: int = 6) -> dict:
    now = now or datetime.now(timezone.utc)
    kinds = {c.id: c.kind for c in session.scalars(select(Catalog))}
    cands: list[tuple[float, str, Offer]] = []
    for o in session.scalars(select(Offer).where(Offer.published.is_(True), Offer.price.is_not(None))):
        if o.valid_to and o.valid_to < today:
            continue
        if o.valid_from and o.valid_from > today:
            continue  # henüz satışta değil
        seen = _aware(o.last_seen_at)
        if seen is None or now - seen > timedelta(days=3):
            continue
        if not o.image_url or o.image_status not in GOOD_IMAGE or o.page_price_conflict is not None:
            continue
        s = score(o, kinds.get(o.catalog_id or ""), today, now)
        if s is None:
            continue
        tie = hashlib.sha1(f"{today}|{o.id}".encode(), usedforsecurity=False).hexdigest()
        cands.append((s, tie, o))
    cands.sort(key=lambda t: (-t[0], t[1]))
    by_store: dict[str, list[Offer]] = {}
    for _, _, o in cands:
        lst = by_store.setdefault(o.store_id, [])
        if len(lst) < per_store:
            lst.append(o)
    picked: list[str] = []
    queues = [list(v) for v in by_store.values()]
    queues.sort(key=lambda q: -score(q[0], kinds.get(q[0].catalog_id or ""), today, now))
    while len(picked) < limit and any(queues):
        for q in queues:
            if q and len(picked) < limit:
                picked.append(q.pop(0).id)
    return {"date": today.isoformat(), "title": "Günün İndirimleri", "note": NOTE, "offer_ids": picked,
            "stores": sorted(by_store)}
