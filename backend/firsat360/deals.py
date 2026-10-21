"""Günün İndirimleri: her gün birçok mağazadan otomatik seçilen ürünler.

Seçim kuralları (uydurma veri yok; yalnızca resmî kaynakta okunan fiyat ve mağaza beyanı):
- Bugün geçerli, yayında, fiyatı olan, son 3 gün içinde kaynakta görülmüş, görseli doğrulanmış/resmî olan ürünler.
- Sayfa fiyatı listeyle çelişen ürün alınmaz.
- Puan: mağazanın beyan ettiği indirim oranı (önceki fiyat → güncel fiyat) + yenilik (bugün/dün kaynağa giren aktüel).
  İndirim beyanı olmayan ürün yalnızca yeni aktüel ise aday olur.
- Çeşitlilik: mağazalar arasında sırayla; mağaza başına üst sınır.
- Günlük değişim: puan, güne bağlı bir çarpanla (0,6–1,4) karıştırılır ve son 2 günde gösterilen ürünler, yeterli
  aday varsa bugün tekrar gösterilmez. Dünün seçimi aynı kuralla yeniden hesaplanır; ek durum saklamak gerekmez.
  Aynı gün içinde seçim sabittir (sabah ve akşam çalıştırması aynı listeyi verir, aday kümesi değişmediyse).
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
    """Aday puanı. Mağaza beyanlı indirim (%10+) veya satıştaki aktüel ürün aday olur; diğerleri olmaz."""
    pct = 0.0
    if o.store_declared_old_price and o.price and o.store_declared_old_price > o.price:
        pct = float((o.store_declared_old_price - o.price) / o.store_declared_old_price * 100)
        if pct > 90:  # olağandışı beyan: güvenilmez
            return None
    if pct >= 10:
        return pct + 10.0
    if kind == "aktuel":
        start = o.valid_from or (_aware(o.first_seen_at).date() if o.first_seen_at else None)
        if start is None:
            return None
        days_on_sale = (today - start).days
        if 0 <= days_on_sale <= 7:  # satışa yeni çıkmış aktüel ürün: ilk günler öne çıkar
            return 6.0 + (8.0 if days_on_sale == 0 else 4.0 if days_on_sale == 1 else 0.0)
    return None


def daily_deals(session: Session, today: date, now: datetime | None = None, limit: int = 30,
                per_store: int = 6) -> dict:
    now = now or datetime.now(timezone.utc)
    kinds = {c.id: c.kind for c in session.scalars(select(Catalog))}
    cands: list[tuple[float, Offer]] = []
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
        cands.append((s, o))
    picked, stores = pick(cands, today, limit, per_store)
    return {"date": today.isoformat(), "title": "Günün İndirimleri", "note": NOTE, "offer_ids": picked,
            "stores": stores}


def _u(day: date, oid: str) -> float:
    """Gün + ürün için 0–1 arası sabit sözde-rastgele sayı."""
    h = hashlib.sha1(f"{day.isoformat()}|{oid}".encode(), usedforsecurity=False).hexdigest()
    return int(h[:8], 16) / 0xFFFFFFFF


def _rank(cands: list[tuple[float, Offer]], day: date, limit: int, per_store: int,
          exclude: set[str] = frozenset()) -> tuple[list[str], list[str]]:
    keyed = sorted(((s * (0.6 + 0.8 * _u(day, o.id)), o) for s, o in cands if o.id not in exclude),
                   key=lambda t: (-t[0], t[1].id))
    by_store: dict[str, list[tuple[float, Offer]]] = {}
    for k, o in keyed:
        lst = by_store.setdefault(o.store_id, [])
        if len(lst) < per_store:
            lst.append((k, o))
    queues = sorted(by_store.values(), key=lambda q: -q[0][0])
    # Mağaza sırası da güne göre döner: her gün aynı mağaza en başta olmasın.
    if queues:
        r = int(_u(day, "store-order") * len(queues))
        queues = queues[r:] + queues[:r]
    picked: list[str] = []
    queues = [list(q) for q in queues]
    while len(picked) < limit and any(queues):
        for q in queues:
            if q and len(picked) < limit:
                picked.append(q.pop(0)[1].id)
    return picked, sorted(by_store)


def pick(cands: list[tuple[float, Offer]], today: date, limit: int = 30, per_store: int = 6) -> tuple[list[str], list[str]]:
    """Bugünün seçimi. Aday havuzu, ürün kimliğinin özetine göre K gruba bölünür (K = havuz / limit, en çok 3);
    her gün sıradaki grup öne çıkar. Böylece ardışık günlerde farklı ürünler gösterilir ve ek durum saklamak gerekmez.
    Grup yetmezse diğer adaylarla tamamlanır."""
    stores_with = len({o.store_id for _, o in cands}) or 1
    cap = min(limit, per_store * stores_with)
    k = max(1, min(3, len(cands) // max(1, cap)))
    slot = today.toordinal() % k
    primary = [(sc, o) for sc, o in cands if int(_u(date(2000, 1, 1), o.id) * k) % k == slot]
    picked, _ = _rank(primary, today, limit, per_store)
    if len(picked) < limit:
        store_of = {o.id: o.store_id for _, o in cands}
        count: dict[str, int] = {}
        for i in picked:
            count[store_of[i]] = count.get(store_of[i], 0) + 1
        rest, _ = _rank(cands, today, limit * 3, limit, exclude=set(picked))
        for i in rest:
            if len(picked) >= limit:
                break
            if count.get(store_of[i], 0) < per_store:  # mağaza başına üst sınır korunur
                picked.append(i)
                count[store_of[i]] = count.get(store_of[i], 0) + 1
    stores = sorted({o.store_id for _, o in cands if o.id in set(picked)})
    return picked, stores
