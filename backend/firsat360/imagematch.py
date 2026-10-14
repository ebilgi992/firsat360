"""Ürün görseli ↔ ürün eşleştirme ve OTOMATİK doğrulama.

Kural: bir görsel bir ürüne yalnızca şu üç koşul birlikte sağlanırsa eklenir:
  1. Görsel, ürünün KENDİ resmî detay sayfasından okunmuştur (aynı mağaza + aynı detay adresi).
  2. Sayfanın yazdığı ürün adı, bizdeki ürün adıyla yeterince örtüşür (ortak sözcük oranı).
  3. Her iki tarafta da ambalaj ölçüsü yazıyorsa ölçüler aynıdır (1 L = 1000 ml gibi birim eşdeğerliği dahil).
Aynı detay sayfasında birden çok çeşit varsa (ör. BİM'de iki farklı koku), açıklaması en iyi örtüşen satır seçilir.
Koşullar sağlanmazsa görsel EKLENMEZ ve nedeni ``image_check`` alanına yazılır (rastgele görsel konmaz).

Fiyat: sayfadaki fiyat listedeki fiyattan farklıysa fiyat DEĞİŞTİRİLMEZ; ``price_conflict`` olarak işaretlenir.
(Mağaza beyanı önceki fiyata eşitse çelişki sayılmaz: kampanya fiyatı sepette uygulanıyordur.)
"""
from __future__ import annotations

import re
from dataclasses import dataclass
from decimal import Decimal

from .normalize import fold, parse_price

_STOP = {"ve", "ile", "li", "lu", "lı", "lü", "adet", "cesit", "cesitleri", "farkli", "renklerde", "cesitlerde",
         "x", "the", "of", "&", "+", "-", "/", "tk"}
_SIZE_RE = re.compile(r"(?<![0-9])(\d+(?:[.,]\d+)?)\s*(?:x\s*(\d+(?:[.,]\d+)?)\s*)?(kg|gr|g|ml|lt|l|cl|cc)(?![a-z])")
_TO_BASE = {"kg": ("g", 1000), "gr": ("g", 1), "g": ("g", 1), "l": ("ml", 1000), "lt": ("ml", 1000),
            "ml": ("ml", 1), "cl": ("ml", 10), "cc": ("ml", 1)}
NAME_THRESHOLD = 0.5


def sizes(*texts: str | None) -> set[tuple[str, float]]:
    """Metindeki tüm ambalaj ölçüleri, temel birime çevrilmiş: '2,5 KG' → {('g', 2500.0)}; '3x140 g' → {('g', 420.0)}."""
    out = set()
    for t in texts:
        for m in _SIZE_RE.finditer(fold(t or "")):
            a = float(m.group(1).replace(",", "."))
            b = float(m.group(2).replace(",", ".")) if m.group(2) else 1.0
            unit, k = _TO_BASE[m.group(3)]
            out.add((unit, round(a * b * k, 3)))
            if m.group(2):
                out.add((unit, round(b * k, 3)))  # '3x140 g' tek paket ölçüsü olarak da kabul
    return out


def tokens(*texts: str | None) -> set[str]:
    t = " ".join(fold(x or "") for x in texts)
    t = re.sub(r"(\d)[.,](\d)", r"\1\2", t)
    words = re.split(r"[^0-9a-z]+", t)
    return {w for w in words if len(w) >= 2 and w not in _STOP and not w.isdigit()}


def name_score(page_name: str, offer_text: str) -> float:
    """Sayfa adındaki anlamlı sözcüklerin bizdeki ürün metninde bulunma oranı (0–1)."""
    p, o = tokens(page_name), tokens(offer_text)
    if not p or not o:
        return 0.0
    hit = 0
    for w in p:
        if w in o or any((len(w) >= 5 and (x.startswith(w[:5]) or w.startswith(x[:5]))) for x in o if len(x) >= 5):
            hit += 1
    return hit / len(p)


@dataclass
class PageImage:
    store: str
    detail_url: str
    page_name: str | None
    page_variant: str | None
    page_price_text: str | None
    image_url: str | None

    @property
    def page_price(self) -> Decimal | None:
        return parse_price(self.page_price_text)


@dataclass
class Match:
    image_url: str | None
    status: str          # verified | no_image_on_page | name_mismatch | size_mismatch | no_page
    score: float
    page_name: str | None
    page_price: Decimal | None
    price_conflict: Decimal | None
    page_size_text: str | None = None


def norm_url(u: str | None) -> str:
    return (u or "").strip().rstrip("/").split("#")[0].lower()


def index_pages(rows: list[PageImage]) -> dict[tuple[str, str], list[PageImage]]:
    idx: dict[tuple[str, str], list[PageImage]] = {}
    for r in rows:
        idx.setdefault((r.store, norm_url(r.detail_url)), []).append(r)
    return idx


def verify(store: str, product_url: str | None, name: str, brand: str | None, variant: str | None,
           price: Decimal | None, old_price: Decimal | None,
           idx: dict[tuple[str, str], list[PageImage]]) -> Match:
    cands = idx.get((store, norm_url(product_url)), [])
    if not cands:
        return Match(None, "no_page", 0.0, None, None, None)
    offer_text = " ".join(x for x in (brand, name, variant) if x)
    best, best_s = None, -1.0
    for c in cands:
        s = name_score(c.page_name or "", offer_text)
        if len(cands) > 1 and c.page_variant:
            # aynı sayfada birden çok çeşit: açıklama örtüşmesi ayırt edici
            s += 0.5 * name_score(c.page_variant, " ".join(x for x in (variant, name) if x))
        if s > best_s:
            best, best_s = c, s
    c = best
    page_sizes = sizes(c.page_name, c.page_variant)
    our_sizes = sizes(name, variant)
    pp = c.page_price
    conflict = None
    if pp is not None and price is not None and pp != price and pp != old_price:
        conflict = pp
    base = dict(score=round(min(best_s, 1.0), 3), page_name=c.page_name, page_price=pp, price_conflict=conflict)
    if page_sizes and our_sizes and not (page_sizes & our_sizes):
        return Match(None, "size_mismatch", **base)
    if best_s < NAME_THRESHOLD:
        return Match(None, "name_mismatch", **base)
    size_text = None
    if page_sizes and not our_sizes:
        m = _SIZE_RE.search(fold(c.page_name or ""))
        if m:
            size_text = _size_display(m)
    if not c.image_url:
        return Match(None, "no_image_on_page", page_size_text=size_text, **base)
    return Match(c.image_url, "verified", page_size_text=size_text, **base)


def _size_display(m: re.Match) -> str:
    unit = {"gr": "g", "lt": "L", "l": "L", "kg": "kg", "g": "g", "ml": "ml", "cl": "cl", "cc": "cc"}[m.group(3)]
    a = m.group(1)
    return f"{a}x{m.group(2)} {unit}" if m.group(2) else f"{a} {unit}"


# ---------------------------------------------------------------------------------------------------------------
# Anlık görüntü dosyası ve veritabanına uygulama
# ---------------------------------------------------------------------------------------------------------------

def load_rows(path) -> list[PageImage]:
    from .connectors.snapshot import _rows
    return [PageImage(*r) for r in _rows(path, 6)]


def latest_rows(snapshot_dirs) -> tuple[list[PageImage], "datetime | None"]:
    """Tüm anlık görüntülerdeki product_images.tsv; aynı sayfa satırı için en yenisi geçerlidir."""
    import json
    from datetime import datetime
    merged: dict[tuple, PageImage] = {}
    checked = None
    for d in snapshot_dirs:
        p = d / "product_images.tsv"
        if not p.exists():
            continue
        for r in load_rows(p):
            merged[(r.store, norm_url(r.detail_url), fold(r.page_name or ""), fold(r.page_variant or ""))] = r
        meta = json.loads((d / "meta.json").read_text(encoding="utf-8"))
        ts = meta.get("observed", {}).get("product_images")
        if ts:
            checked = datetime.fromisoformat(ts)
    return list(merged.values()), checked


def apply_to_db(session, rows: list[PageImage], checked_at=None) -> dict:
    """Yayındaki ürünlere doğrulanmış ürün sayfası görsellerini uygular. Dönüş: mağaza → durum sayıları."""
    from datetime import timezone

    from sqlalchemy import select

    from .db import Offer
    idx = index_pages(rows)
    stores = {r.store for r in rows}
    stats: dict[str, dict[str, int]] = {}
    when = checked_at.astimezone(timezone.utc) if checked_at else None
    for o in session.scalars(select(Offer).where(Offer.published.is_(True), Offer.store_id.in_(stores))):
        m = verify(o.store_id, o.product_url, o.name, o.brand, o.variant, o.price, o.store_declared_old_price, idx)
        if m.status == "name_mismatch" and not m.page_name:
            m.status = "page_empty"
        s = stats.setdefault(o.store_id, {})
        s[m.status] = s.get(m.status, 0) + 1
        if m.status == "no_page":
            continue  # bu ürün için sayfa okunmadı → mevcut (liste/JSON) görsel olduğu gibi kalır
        o.image_page_url = o.product_url
        o.image_checked_at = when
        o.image_match_score = Decimal(str(m.score))
        o.page_price_conflict = m.price_conflict
        o.page_size_text = m.page_size_text
        if m.status == "verified":
            o.image_url = o.image_large_url = m.image_url
            o.image_status = "verified"
            o.image_rights = "official_cdn_link"
        elif o.image_status not in ("official_json",):
            # Sayfada görsel yok ya da eşleşme doğrulanamadı → görsel GÖSTERİLMEZ (başka ürün görseli konmaz).
            o.image_url = o.image_large_url = None
            o.image_status = m.status
            o.image_rights = "none"
    return stats
