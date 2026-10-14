"""Veri hattı: bağlayıcı sonucu → doğrulama → veritabanı (upsert, fiyat geçmişi, kaynak sağlığı).

Her mağaza ayrı işlemde (transaction) çalışır: bir kaynağın hatası diğerlerini durdurmaz.
"""
from __future__ import annotations

import logging
import traceback
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from .config import STORES, Settings
from .connectors.base import ConnectorResult, PoliteClient
from .db import Catalog, Offer, PriceObservation, SourceRun, Store
from .normalize import categorize, clean, fold, format_try, offer_id, offer_id_full, size_token

log = logging.getLogger("firsat360.pipeline")
MAX_PLAUSIBLE_PRICE = Decimal("500000")


def utcnow() -> datetime:
    return datetime.now(timezone.utc)


def ensure_stores(session: Session, settings: Settings | None = None) -> None:
    settings = settings or Settings()
    for sid, meta in STORES.items():
        store = session.get(Store, sid)
        if store is None:
            store = Store(id=sid, name=meta["name"], official_url=meta["official_url"],
                          catalog_url=meta["catalog_url"], legal_note=meta["legal_note"])
            session.add(store)
        else:
            store.legal_note = meta["legal_note"]
        store.image_policy = settings.image_policy(sid)
    session.commit()


def validate_offer(o) -> str | None:
    """Yayına engel bir sorun varsa açıklamasını döner."""
    if not o.name or len(o.name) < 2:
        return "ad yok"
    if o.price is None:
        return "fiyat ayrıştırılamadı"
    if o.price <= 0 or o.price > MAX_PLAUSIBLE_PRICE:
        return f"makul olmayan fiyat: {o.price}"
    if o.store_declared_old_price is not None and o.store_declared_old_price <= o.price:
        o.store_declared_old_price = None  # indirim göstermez; sessizce bırakılır
    if o.valid_from and o.valid_to and o.valid_to < o.valid_from:
        return "bitiş tarihi başlangıçtan önce"
    return None


def ingest(session: Session, result: ConnectorResult, trigger: str = "schedule") -> SourceRun:
    run = SourceRun(store_id=result.store_id, method=result.method, trigger=trigger,
                    started_at=result.observed_at, ok=False)
    session.add(run)
    # Tüm zaman damgaları UTC saklanır (SQLite saat dilimi bilgisi tutmaz; PostgreSQL'de de tutarlı olur).
    obs = _aware(result.observed_at).astimezone(timezone.utc)
    run.started_at = obs
    for c in result.catalogs:
        cat = session.get(Catalog, c.id)
        if cat is None:
            cat = Catalog(id=c.id, store_id=result.store_id, title=c.title, kind=c.kind, official_url=c.official_url,
                          source_method=result.method, observed_at=obs, data_mode="real")
            session.add(cat)
        cat.title, cat.kind, cat.official_url = c.title, c.kind, c.official_url
        cat.valid_from, cat.valid_to = c.valid_from, c.valid_to
        cat.source_method, cat.observed_at = result.method, obs
        if c.pages or c.cover_url:
            import json as _json
            cat.cover_url = c.cover_url or (c.pages[0].get("thumb_url") or c.pages[0].get("url") if c.pages else None)
            cat.pages_json = _json.dumps(c.pages, ensure_ascii=False) if c.pages else None
        if c.pdf_url:
            cat.pdf_url = c.pdf_url
        # Yayın izi: ilk görülme bir kez yazılır (eski anlık görüntü sonradan içe aktarılırsa daha erken olanı kalır).
        if cat.first_seen_at is None or _aware(cat.first_seen_at) > obs:
            cat.first_seen_at = obs
        if cat.last_checked_at is None or _aware(cat.last_checked_at) < obs:
            cat.last_checked_at = obs
        cat.content_hash = catalog_hash(cat)
    session.flush()
    seen_ids: set[str] = set()
    seen_variant: dict[str, str] = {}
    duplicates = 0
    # Kimlik sürekliliği için aynı kataloglardaki mevcut ürünler (marka+ad → kayıtlar).
    cat_ids = {o.catalog_id for o in result.offers if o.catalog_id}
    by_name: dict[tuple[str, str, str], list[Offer]] = {}
    if cat_ids:
        for ex in session.scalars(select(Offer).where(Offer.store_id == result.store_id,
                                                      Offer.catalog_id.in_(cat_ids))):
            by_name.setdefault((ex.catalog_id, fold(ex.brand or ""), fold(ex.name)), []).append(ex)
    for o in result.offers:
        problem = validate_offer(o)
        if problem:
            run.rejected += 1
            log.warning("%s: '%s' reddedildi: %s", result.store_id, o.name, problem)
            continue
        oid = offer_id(result.store_id, o.catalog_id, o.brand, o.name, o.variant)
        if session.get(Offer, oid) is None:
            # Aynı katalogda aynı marka+ad tek bir kayıt varsa ve taraflardan birinde ölçü bilgisi yoksa aynı üründür
            # (kaynakta/aktarımda açıklama eksik yazılmış). Ölçüler farklıysa farklı ürün sayılır.
            cands = [c for c in by_name.get((o.catalog_id, fold(o.brand or ""), fold(o.name)), [])
                     if c.id not in seen_ids]
            if len(cands) == 1 and (not size_token(o.variant, o.name)
                                    or not size_token(cands[0].variant, cands[0].name)):
                oid = cands[0].id
        if oid in seen_ids and seen_variant.get(oid) != fold(o.variant or ""):
            # Aynı ad/ölçü, farklı açıklama (ör. iki farklı koku): ayrı ürün → açıklamayı da içeren kimlik.
            oid = offer_id_full(result.store_id, o.catalog_id, o.brand, o.name, o.variant)
        if oid in seen_ids:
            duplicates += 1
            continue  # aynı çalıştırmada mükerrer kayıt
        seen_ids.add(oid)
        seen_variant[oid] = fold(o.variant or "")
        cat_key, cat_src = categorize(o.name, o.brand, o.variant, o.category_url_hint)
        offer = session.get(Offer, oid)
        is_new = offer is None
        if is_new:
            offer = Offer(id=oid, store_id=result.store_id, first_seen_at=obs, last_seen_at=obs,
                          name=o.name, search_key="", category=cat_key, category_source=cat_src,
                          source_url=o.source_url, source_method=result.method, verification="", data_mode="real")
            session.add(offer)
            run.new_offers += 1
        elif offer.price is not None and offer.price != o.price:
            run.price_changes += 1
        offer.catalog_id = o.catalog_id
        offer.name, offer.brand, offer.variant = clean(o.name), clean(o.brand), clean(o.variant)
        offer.search_key = fold(" ".join(x for x in (o.brand, o.name, o.variant) if x))
        offer.category, offer.category_source = cat_key, cat_src
        offer.price = o.price
        offer.price_text = o.price_text or format_try(o.price)
        offer.store_declared_old_price = o.store_declared_old_price
        offer.condition, offer.channel = o.condition, o.channel
        offer.product_url = o.product_url
        # Görsel dosyası kopyalanmaz; yalnızca mağazanın resmî CDN adresi saklanır. Gösterim, mağaza bazlı
        # image_policy ile feed üretiminde açılıp kapatılır.
        if o.image_url or offer.image_status != "verified":
            # Doğrulanmış ürün sayfası görseli, kaynağı görselsiz bir sonraki okumada silinmez.
            offer.image_url, offer.image_large_url = o.image_url, o.image_large_url or o.image_url
            offer.image_status = o.image_status or ("listing" if o.image_url else "none")
        offer.image_rights = "official_cdn_link" if offer.image_url else "none"
        offer.valid_from, offer.valid_to = o.valid_from, o.valid_to
        offer.last_seen_at = obs
        offer.source_url, offer.source_method = o.source_url, result.method
        offer.verification = ("tek_okuma_arac_aracili" if result.method == "manual_snapshot"
                              else "otomatik_ayristirma")
        offer.published = True
        last = session.scalars(select(PriceObservation).where(PriceObservation.offer_id == oid)
                               .order_by(PriceObservation.observed_at.desc()).limit(1)).first()
        # Gözlem kaydı: fiyat değiştiyse veya o gün (İstanbul saatiyle) henüz gözlem yoksa → günlük doğrulama izi.
        if last is None or last.price != o.price or _tr_day(_aware(last.observed_at)) != _tr_day(obs):
            session.add(PriceObservation(offer_id=oid, price=o.price, observed_at=obs, source_method=result.method))
    # Kaynaktan kalkan ürünler: bu çalıştırmada ürünü okunan kataloglarda olup artık görünmeyenler yayından alınır.
    # Kısmi (eksik) okumada hiçbir ürün kaldırılmaz; eski veri "son görülme" zamanıyla kalır.
    delisted = 0
    if not result.partial:
        cats_with_offers = {o.catalog_id for o in result.offers if o.catalog_id}
        if cats_with_offers and seen_ids:
            stale = session.scalars(select(Offer).where(
                Offer.store_id == result.store_id, Offer.catalog_id.in_(cats_with_offers),
                Offer.published.is_(True), Offer.id.not_in(seen_ids))).all()
            for st in stale:
                st.published = False
                delisted += 1
    notes = [f"{delisted} ürün kaynakta artık yok → yayından kaldırıldı"] if delisted else []
    if duplicates:
        log.info("%s: %d mükerrer kayıt atlandı", result.store_id, duplicates)
    run.catalogs = len(result.catalogs)
    run.offers = len(seen_ids)
    run.finished_at = utcnow()
    run.ok = True
    run.error = "; ".join(result.warnings + notes) or None
    store = session.get(Store, result.store_id)
    # Kullanıcıya gösterilen "son kontrol" = verinin kaynaktan okunduğu an (anlık görüntüde de dürüst kalır).
    store.last_checked_at = obs
    store.last_success_at = obs
    store.last_error = None
    if not seen_ids:
        store.status = "link_only"
    elif result.partial or result.warnings:
        store.status = "partial"
    else:
        store.status = "ok"
    store.status_message = (f"{len(seen_ids)} ürün, {len(result.catalogs)} katalog · yöntem: {result.method}"
                            + (f" · uyarı: {'; '.join(result.warnings)}" if result.warnings else "")
                            + (f" · bilgi: {'; '.join(notes)}" if notes else ""))
    return run


def catalog_hash(cat: Catalog) -> str:
    """Kataloğun görünen içeriğinin özeti: başlık, geçerlilik, kapak ve sayfa adresleri."""
    import hashlib
    parts = [cat.title or "", str(cat.valid_from or ""), str(cat.valid_to or ""), cat.cover_url or "",
             cat.pages_json or "", cat.pdf_url or ""]
    return hashlib.sha256("\x1f".join(parts).encode("utf-8")).hexdigest()


def _aware(dt: datetime) -> datetime:
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def _tr_day(dt: datetime) -> date:
    return (dt.astimezone(timezone.utc) + timedelta(hours=3)).date()  # Türkiye: sabit UTC+3


def make_alert(settings: Settings, transport=None):
    """Yönetici bildirimi. Webhook ayarlı değilse yalnızca günlüğe yazar. Bildirim hatası taramayı etkilemez."""
    import httpx

    def alert(text: str) -> bool:
        log.error("YÖNETİCİ UYARISI: %s", text)
        if not settings.alert_webhook_url:
            return False
        try:
            with httpx.Client(timeout=10, transport=transport) as c:
                r = c.post(settings.alert_webhook_url, json={"text": text, "content": text})
                return r.status_code < 300
        except Exception as exc:  # noqa: BLE001
            log.warning("Bildirim gönderilemedi: %s", exc)
            return False
    return alert


def run_store(sf: sessionmaker, store_id: str, connector, client: PoliteClient, trigger: str = "schedule",
              alert=None) -> dict:
    """Tek mağazayı çalıştırır. Hata yakalanır, kaydedilir ve yukarı FIRLATILMAZ (izolasyon)."""
    started = utcnow()
    with sf() as session:
        store = session.get(Store, store_id)
        if store is not None and not store.enabled:
            return {"store": store_id, "ok": False, "skipped": "devre dışı"}
    try:
        result = connector.fetch(client, started)
        with sf() as session:
            run = ingest(session, result, trigger)
            session.commit()
            return {"store": store_id, "ok": True, "offers": run.offers, "rejected": run.rejected,
                    "warnings": run.error}
    except Exception as exc:  # noqa: BLE001 — kaynak hatası diğerlerini durdurmamalı
        msg = f"{exc.__class__.__name__}: {exc}"
        log.error("%s başarısız: %s\n%s", store_id, msg, traceback.format_exc(limit=3))
        with sf() as session:
            session.add(SourceRun(store_id=store_id, method=getattr(connector, "method", "?"), trigger=trigger,
                                  started_at=started, finished_at=utcnow(), ok=False, error=msg[:2000]))
            store = session.get(Store, store_id)
            if store is not None:
                store.last_checked_at = utcnow()
                store.last_error = msg[:2000]
                # Daha önce başarılı veri varsa mağaza "hata" olur ama eski veri son kontrol zamanıyla gösterilmeye devam eder.
                store.status = "error"
                store.status_message = "Son kontrol başarısız; gösterilen veriler son başarılı kontrolden"
            session.commit()
        if alert is not None:
            alert(f"FIRSAT360: {store_id} taraması başarısız ({trigger}): {msg[:300]}")
        return {"store": store_id, "ok": False, "error": msg}


def run_all(sf: sessionmaker, connectors: dict, settings: Settings, trigger: str = "schedule",
            transport=None, only: list[str] | None = None, alert_transport=None, sleep=None) -> list[dict]:
    with sf() as session:
        ensure_stores(session, settings)
    from .httpcache import ConditionalCache
    kw = {"sleep": sleep} if sleep is not None else {}
    client = PoliteClient(settings.user_agent, settings.request_timeout_s, settings.min_request_interval_s,
                          transport=transport, cache=ConditionalCache(sf, settings.http_cache_dir), **kw)
    alert = make_alert(settings, alert_transport)
    out = []
    try:
        for sid, conn in connectors.items():
            if only and sid not in only:
                continue
            out.append(run_store(sf, sid, conn, client, trigger, alert=alert))
    finally:
        client.close()
    return out


def import_snapshot(sf: sessionmaker, results: list[ConnectorResult]) -> list[dict]:
    with sf() as session:
        ensure_stores(session)
    out = []
    for res in results:
        with sf() as session:
            run = ingest(session, res, trigger="snapshot")
            session.commit()
            out.append({"store": res.store_id, "offers": run.offers, "rejected": run.rejected,
                        "catalogs": run.catalogs})
    return out


def is_expired(o: Offer, today: date) -> bool:
    return o.valid_to is not None and o.valid_to < today


def apply_product_images(sf: sessionmaker, dirs) -> dict:
    """Anlık görüntülerdeki ürün sayfası görsellerini doğrulayarak yayındaki ürünlere uygular."""
    from .imagematch import apply_to_db, latest_rows
    rows, checked = latest_rows(dirs)
    if not rows:
        return {}
    with sf() as session:
        stats = apply_to_db(session, rows, checked)
        session.commit()
    return stats


def import_snapshots(sf: sessionmaker, dirs) -> list[tuple[str, list[dict]]]:
    """Anlık görüntüleri eskiden yeniye içe aktarır, ardından ürün görsellerini doğrulayıp uygular."""
    from .connectors.snapshot import SnapshotSource
    out = [(d.parent.name, import_snapshot(sf, SnapshotSource(d).all())) for d in dirs]
    out.append(("product_images", [apply_product_images(sf, dirs)]))
    return out
