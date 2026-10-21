"""Günlük senkronizasyon: kaynakları kontrol et → yeni katalogları bul → bir kez duyur → (isteğe bağlı) push → feed.

Kurallar:
- Bir katalog yalnızca BİR kez duyurulur (announced_catalogs). Aynı gün tekrar çalıştırmak yeni bildirim üretmez.
- "Yeni" = kaynakta ilk görülmesi son ANNOUNCE_MAX_AGE_DAYS gün içinde, süresi bitmemiş, içeriği (ürün/sayfa/kapak)
  olan katalog. Eski katalog yeniymiş gibi duyurulmaz.
- İlk kurulumda (hiç duyuru kaydı yokken) mevcut kataloglar sessizce "duyurulmuş" sayılır (bildirim bombardımanı yok).
- Kaynak erişilemezse o mağazanın mevcut doğrulanmış verisi korunur; hata mağaza durumuna yazılır (pipeline.run_store).
- Push gönderimi başarısız olursa en fazla 3 kez (sonraki çalıştırmalarda) yeniden denenir.
"""
from __future__ import annotations

import hashlib
import json
import logging
from datetime import date, datetime, timedelta, timezone
from pathlib import Path
from zoneinfo import ZoneInfo

import httpx
from sqlalchemy import delete, func, select
from sqlalchemy.orm import Session, sessionmaker

from .config import ROOT, Settings
from .db import (AnnouncedCatalog, Announcement, Catalog, Offer, PriceObservation, SourceRun, Store,
                 SyncRun)

log = logging.getLogger("firsat360.sync")
TR = ZoneInfo("Europe/Istanbul")
TITLE = "🛒 Günün katalogları yayınlandı!"
BODY = "Yeni aktüel ürünleri, fiyatları ve güncel broşürleri keşfetmek için dokun."
MAX_PUSH_ATTEMPTS = 3


def _aware(dt: datetime | None) -> datetime | None:
    if dt is None:
        return None
    return dt if dt.tzinfo else dt.replace(tzinfo=timezone.utc)


def has_content(c: Catalog, offer_counts: dict[str, int]) -> bool:
    return c.kind != "link_only" and (offer_counts.get(c.id, 0) > 0 or bool(c.pages_json) or bool(c.cover_url))


def offer_counts(session: Session) -> dict[str, int]:
    rows = session.execute(select(Offer.catalog_id, func.count()).where(Offer.published.is_(True))
                           .group_by(Offer.catalog_id)).all()
    return {cid: n for cid, n in rows if cid}


def detect_new_catalogs(session: Session, settings: Settings, now: datetime) -> tuple[list[Catalog], int, bool]:
    """(yeni kataloglar, sessizce işaretlenen eski katalog sayısı, ilk kurulum mu)."""
    today = now.astimezone(TR).date()
    announced = set(session.scalars(select(AnnouncedCatalog.catalog_id)))
    bootstrap = not announced and session.scalar(select(func.count()).select_from(Announcement)) == 0
    counts = offer_counts(session)
    fresh: list[Catalog] = []
    silent = 0
    for c in session.scalars(select(Catalog).where(Catalog.data_mode == "real").order_by(Catalog.store_id, Catalog.id)):
        if c.id in announced:
            continue
        if c.valid_to is not None and c.valid_to < today:
            continue  # süresi bitmiş: duyurulmaz (işaretlenmez de; zaten görünmez)
        if not has_content(c, counts):
            continue  # içerik gelince duyurulabilir
        first = _aware(c.first_seen_at) or _aware(c.observed_at)
        too_old = first is not None and now - first > timedelta(days=settings.announce_max_age_days)
        if bootstrap or too_old:
            session.add(AnnouncedCatalog(catalog_id=c.id, announcement_id=None, at=now))
            silent += 1
            continue
        fresh.append(c)
    return fresh, silent, bootstrap


def deliver_time(settings: Settings, now: datetime) -> datetime:
    """Bugünün bildirim saati (TR) henüz gelmediyse o saat; geçtiyse hemen."""
    h, m = settings.notify_hm()
    local = now.astimezone(TR)
    target = local.replace(hour=h, minute=m, second=0, microsecond=0)
    return target.astimezone(timezone.utc) if target > local else now


def make_announcement(session: Session, catalogs: list[Catalog], settings: Settings, now: datetime) -> Announcement:
    ids = sorted(c.id for c in catalogs)
    store_ids = sorted({c.store_id for c in catalogs})
    names = []
    for sid in store_ids:
        s = session.get(Store, sid)
        names.append(s.name if s else sid)
    aid = "a" + hashlib.sha1("|".join(ids).encode("utf-8"), usedforsecurity=False).hexdigest()[:12]
    body = BODY + "\n" + ", ".join(names) + f" · {len(ids)} yeni katalog"
    ann = session.get(Announcement, aid)
    if ann is None:
        ann = Announcement(id=aid, created_at=now, title=TITLE, body=body, catalog_ids=json.dumps(ids),
                           store_ids=json.dumps(store_ids), push_status="none", push_attempts=0,
                           deliver_at=deliver_time(settings, now))
        session.add(ann)
    for cid in ids:
        if session.get(AnnouncedCatalog, cid) is None:
            session.add(AnnouncedCatalog(catalog_id=cid, announcement_id=aid, at=now))
    return ann


def deep_link(ann: Announcement) -> str:
    cats = json.loads(ann.catalog_ids)
    stores = json.loads(ann.store_ids)
    if len(cats) == 1:
        return f"firsat360://catalog/{cats[0]}"
    if len(stores) == 1:
        return f"firsat360://store/{stores[0]}"
    return f"firsat360://catalogs/new?a={ann.id}"


def push_ntfy(settings: Settings, ann: Announcement, now: datetime, transport=None, sleep=None) -> tuple[str, str]:
    """ntfy sunucusuna yayın. Genel konu + her mağaza için '<konu>-<mağaza>' konusu. (durum, ayrıntı) döner.

    Erişim anahtarı yalnızca ortam değişkeninden okunur ve hiçbir yere yazılmaz.
    """
    import time as _time
    sleep = sleep or _time.sleep
    if not settings.push_enabled:
        return "none", "push ayarlı değil (NTFY_URL/NTFY_TOPIC)"
    headers = {"Content-Type": "application/json"}
    if settings.ntfy_token:
        headers["Authorization"] = f"Bearer {settings.ntfy_token}"
    deliver = _aware(ann.deliver_at) or now
    topics = [settings.ntfy_topic] + [f"{settings.ntfy_topic}-{sid}" for sid in json.loads(ann.store_ids)]
    sent, errors = 0, []
    with httpx.Client(timeout=15, transport=transport) as client:
        for topic in topics:
            payload = {"topic": topic, "title": ann.title, "message": ann.body, "click": deep_link(ann),
                       "priority": 3}
            if deliver > now + timedelta(minutes=1):
                payload["delay"] = str(int(deliver.timestamp()))  # ntfy zamanlanmış teslim (Unix zaman damgası)
            ok = False
            for attempt in range(3):
                try:
                    r = client.post(settings.ntfy_url, json=payload, headers=headers)
                    if r.status_code < 300:
                        ok = True
                        break
                    if 400 <= r.status_code < 500 and r.status_code != 429:
                        errors.append(f"{topic}: {r.status_code}")
                        break
                    errors.append(f"{topic}: {r.status_code}")
                except httpx.HTTPError as exc:
                    errors.append(f"{topic}: {exc.__class__.__name__}")
                sleep(2.0 ** (attempt + 1))
            sent += ok
    if sent == len(topics):
        return "sent", f"{sent} konuya gönderildi" + (f" (teslim: {deliver.astimezone(TR):%H:%M})" if deliver > now else "")
    return ("partial" if sent else "failed"), f"{sent}/{len(topics)} gönderildi; " + "; ".join(errors[-5:])


def prune(session: Session, settings: Settings, now: datetime) -> dict:
    """Gereksiz geçmişi siler: eski fiyat gözlemleri (her ürünün son gözlemi korunur), eski çalıştırma kayıtları,
    süresi 60 günden önce bitmiş kataloglar ve ürünleri."""
    out = {}
    cutoff = now - timedelta(days=settings.keep_price_history_days)
    latest = select(func.max(PriceObservation.id)).group_by(PriceObservation.offer_id)
    res = session.execute(delete(PriceObservation).where(PriceObservation.observed_at < cutoff,
                                                         PriceObservation.id.not_in(latest)))
    out["price_observations"] = res.rowcount or 0
    run_cut = now - timedelta(days=settings.keep_run_log_days)
    out["source_runs"] = session.execute(delete(SourceRun).where(SourceRun.started_at < run_cut)).rowcount or 0
    out["sync_runs"] = session.execute(delete(SyncRun).where(SyncRun.started_at < run_cut)).rowcount or 0
    old_day = (now.astimezone(TR) - timedelta(days=60)).date()
    old_offers = session.scalars(select(Offer.id).where(Offer.valid_to.is_not(None), Offer.valid_to < old_day)).all()
    if old_offers:
        session.execute(delete(PriceObservation).where(PriceObservation.offer_id.in_(old_offers)))
        session.execute(delete(Offer).where(Offer.id.in_(old_offers)))
    out["expired_offers"] = len(old_offers)
    in_use = select(Offer.catalog_id).where(Offer.catalog_id.is_not(None))
    out["expired_catalogs"] = session.execute(delete(Catalog).where(
        Catalog.valid_to.is_not(None), Catalog.valid_to < old_day, Catalog.id.not_in(in_use))).rowcount or 0
    ann_cut = now - timedelta(days=90)
    out["announcements"] = session.execute(delete(Announcement).where(Announcement.created_at < ann_cut)).rowcount or 0
    return out


def run_daily(sf: sessionmaker, connectors: dict, settings: Settings, trigger: str = "schedule", transport=None,
              push_transport=None, now: datetime | None = None, snapshot_root: Path | None = None,
              live: bool = True, sleep=None, feed_path: str | Path | None = None,
              verify_pages: bool = True, only: list[str] | None = None) -> dict:
    """Tek günlük çalıştırma. Tekrar çalıştırılabilir (idempotent): aynı veriyle ikinci çalıştırma bildirim üretmez."""
    from .connectors.snapshot import snapshot_dirs
    from .feed import export_feed
    from .pipeline import ensure_stores, import_snapshots, run_all

    now = now or datetime.now(timezone.utc)
    report: dict = {"started_at": now.isoformat(), "trigger": trigger, "stores": [], "errors": []}
    with sf() as s:
        ensure_stores(s, settings)
        run = SyncRun(started_at=now, trigger=trigger)
        s.add(run)
        s.commit()
        run_id = run.id
    # 1) İlk kurulum tohumu: depodaki doğrulanmış okumalar (yalnızca veritabanı boşsa). Mağazada daha yeni başarılı
    #    veri varsa atlanır: eski okuma, sonraki canlı okumanın fiyatlarını geri almaz.
    root = snapshot_root if snapshot_root is not None else ROOT / "data" / "snapshots"
    try:
        # Yalnızca boş veritabanında (ilk kurulum) başlangıç verisi olarak; sonrasında veri tamamen canlı kaynaklardan.
        with sf() as s:
            empty = s.scalar(select(func.count()).select_from(Offer)) == 0
        dirs = snapshot_dirs(root) if empty and Path(root).exists() else []
        report["snapshots"] = import_new_snapshots(sf, dirs) if dirs else {}
    except Exception as exc:  # noqa: BLE001
        report["errors"].append(f"anlık görüntü: {exc.__class__.__name__}: {exc}")
    # 2) Canlı kaynaklar (her mağaza kendi işleminde; biri hata verirse diğerleri sürer, eski veri korunur)
    if live and connectors:
        try:
            report["stores"] = run_all(sf, connectors, settings, trigger=trigger, transport=transport,
                                       only=[x for x in (only or settings.enabled_live_stores)
                                             if x in settings.enabled_live_stores], sleep=sleep)
        except Exception as exc:  # noqa: BLE001
            report["errors"].append(f"canlı tarama: {exc.__class__.__name__}: {exc}")
    # 2b) Ürün görselleri: her ürünün kendi resmî sayfasından otomatik doğrulama (yalnızca yeni/eskimiş olanlar)
    if live and verify_pages:
        from .connectors.base import PoliteClient
        from .httpcache import ConditionalCache
        from .productpages import verify_product_pages
        kw = {"sleep": sleep} if sleep is not None else {}
        client = PoliteClient(settings.user_agent, settings.request_timeout_s, settings.min_request_interval_s,
                              transport=transport, cache=ConditionalCache(sf, settings.http_cache_dir), **kw)
        try:
            report["product_pages"] = verify_product_pages(sf, client, settings.product_pages_per_store, now)
        except Exception as exc:  # noqa: BLE001
            report["errors"].append(f"ürün sayfaları: {exc.__class__.__name__}: {exc}")
        finally:
            client.close()
    # 2a') İstemci köprüsündeki broşür kaynakları: sunucuda bağlayıcısı yok → durum açıkça "client_bridge"
    mark_client_bridged(sf, connectors)
    # 2c) Kalite ajanları + orkestratör: denetle → düzelt → yeniden denetle → raporla
    report["quality"] = run_quality(sf, connectors if live else {}, settings, now, transport, sleep)
    # 3) Yeni katalog tespiti ve duyuru (tek işlem)
    with sf() as s:
        fresh, silent, bootstrap = detect_new_catalogs(s, settings, now)
        ann = make_announcement(s, fresh, settings, now) if fresh else None
        s.commit()
        report.update(new_catalogs=[c.id for c in fresh], silently_marked=silent, bootstrap=bootstrap,
                      announcement=ann.id if ann else None)
    # 4) Push: yeni duyuru + daha önce başarısız olanlar (en fazla 3 deneme, son 1 gün)
    with sf() as s:
        pending = s.scalars(select(Announcement).where(
            Announcement.push_status.in_(("none", "failed", "partial")),
            Announcement.push_attempts < MAX_PUSH_ATTEMPTS,
            Announcement.created_at >= now - timedelta(days=1))).all()
        pushes = []
        for a in pending:
            if not settings.push_enabled:
                a.push_detail = "push ayarlı değil"
                continue
            status, detail = push_ntfy(settings, a, now, transport=push_transport, sleep=sleep)
            a.push_status, a.push_detail = status, detail
            a.push_attempts = (a.push_attempts or 0) + 1
            pushes.append({"id": a.id, "status": status, "detail": detail})
        s.commit()
        report["push"] = pushes
    # 5) Budama ve feed
    with sf() as s:
        report["pruned"] = prune(s, settings, now)
        s.commit()
    with sf() as s:
        stores = s.scalars(select(Store)).all()
        ok = sum(1 for st in stores if st.status in ("ok", "partial", "link_only"))
        failed = sum(1 for st in stores if st.status == "error")
        run = s.get(SyncRun, run_id)
        run.finished_at = datetime.now(timezone.utc)
        run.ok_stores, run.failed_stores = ok, failed
        run.new_catalogs = len(report["new_catalogs"])
        run.announcement_id = report["announcement"]
        run.summary = json.dumps({k: report[k] for k in ("new_catalogs", "silently_marked", "bootstrap", "errors")},
                                 ensure_ascii=False)[:4000]
        s.commit()
        report["ok_stores"], report["failed_stores"] = ok, failed
    with sf() as s:
        path = export_feed(s, feed_path or settings.feed_export_path, today=now.astimezone(TR).date())
        report["feed"] = str(path)
        status_path = Path(path).with_name("status.json")
        st = status_dict(s)
        if report.get("quality"):
            from .quality import compact
            st["quality"] = compact(report["quality"])
        status_path.write_text(json.dumps(st, ensure_ascii=False, indent=1), encoding="utf-8")
        report["status"] = str(status_path)
    report["finished_at"] = datetime.now(timezone.utc).isoformat()
    return report


def mark_client_bridged(sf: sessionmaker, connectors: dict) -> None:
    from .connectors import CLIENT_BRIDGED
    with sf() as s:
        for sid in CLIENT_BRIDGED:
            if sid in connectors:
                continue  # ürün/fiyat bağlayıcısı olan mağazanın durumu kendi taramasından gelir
            st = s.get(Store, sid)
            if st is not None:
                st.status, st.last_error = "client_bridge", None
                st.status_message = "Broşür, uygulamada kullanıcı açtığında resmî sayfadan okunur (sunucu taraması yok)"
        s.commit()


def run_quality(sf: sessionmaker, connectors: dict, settings: Settings, now: datetime, transport=None,
                sleep=None, github=None) -> dict | None:
    from .connectors.base import PoliteClient
    from .httpcache import ConditionalCache
    from .pipeline import run_all
    from .productpages import verify_product_pages
    from .quality import Context, GitHubReports, orchestrate, write_step_summary
    kw = {"sleep": sleep} if sleep is not None else {}
    client = None

    def get_client():
        nonlocal client
        if client is None:
            client = PoliteClient(settings.user_agent, settings.request_timeout_s, settings.min_request_interval_s,
                                  transport=transport, cache=ConditionalCache(sf, settings.http_cache_dir), **kw)
        return client

    def retry_store(sid: str) -> dict:
        if sid not in connectors:
            return {"ok": False, "error": "bağlayıcı yok"}
        r = run_all(sf, {sid: connectors[sid]}, settings, trigger="retry", transport=transport, only=[sid], sleep=sleep)
        return r[0] if r else {"ok": False}

    def reverify(stores: tuple[str, ...]) -> dict:
        return verify_product_pages(sf, get_client(), settings.product_pages_per_store, now, stores=stores)

    ctx = Context(sf=sf, today=now.astimezone(TR).date(), now=now,
                  retry_store=retry_store if connectors else None, reverify=reverify if connectors else None,
                  github=github if github is not None else GitHubReports.from_env())
    try:
        rep = orchestrate(ctx)
        write_step_summary(rep)
        return rep
    except Exception as exc:  # noqa: BLE001 — kalite adımı günlük yayını asla durdurmaz
        log.warning("kalite adımı başarısız: %s", exc)
        return None
    finally:
        if client is not None:
            client.close()


def import_new_snapshots(sf: sessionmaker, dirs) -> dict:
    """Anlık görüntüleri eskiden yeniye içe aktarır; mağazada aynı/daha yeni başarılı veri varsa o okumayı atlar."""
    from .connectors.snapshot import SnapshotSource
    from .pipeline import apply_product_images, ensure_stores, ingest
    with sf() as s:
        ensure_stores(s)
        # Başlangıçtaki durum: aynı klasörde bir mağazanın birden çok dosyası olabilir; hepsi aynı ölçütle değerlendirilir.
        before = {st.id: _aware(st.last_success_at) for st in s.scalars(select(Store))}
    imported, skipped = [], []
    for d in dirs:
        for res in SnapshotSource(d).all():
            with sf() as s:
                prev = before.get(res.store_id)
                obs = _aware(res.observed_at)
                if prev is not None and prev >= obs:
                    skipped.append(f"{d.parent.name}/{res.store_id}")
                    continue
                ingest(s, res, trigger="snapshot")
                s.commit()
                imported.append(f"{d.parent.name}/{res.store_id}")
    images = apply_product_images(sf, dirs) if imported else {}
    return {"imported": imported, "skipped": len(skipped), "images": images}


def status_dict(session: Session) -> dict:
    """İzlenebilirlik: mağaza bazlı son başarılı güncelleme ve son çalıştırmalar (yönetim/durum ekranı için)."""
    def iso(d):
        d = _aware(d)
        return d.isoformat() if d else None
    runs = session.scalars(select(SyncRun).order_by(SyncRun.started_at.desc()).limit(14)).all()
    return {
        "stores": [{"id": s.id, "name": s.name, "status": s.status, "last_checked_at": iso(s.last_checked_at),
                    "last_success_at": iso(s.last_success_at), "last_error": (s.last_error or "")[:300] or None}
                   for s in session.scalars(select(Store).order_by(Store.id))],
        "runs": [{"started_at": iso(r.started_at), "finished_at": iso(r.finished_at), "trigger": r.trigger,
                  "ok_stores": r.ok_stores, "failed_stores": r.failed_stores, "new_catalogs": r.new_catalogs,
                  "announcement_id": r.announcement_id} for r in runs],
    }


def announcements_for_feed(session: Session, now: datetime, days: int = 14) -> list[dict]:
    rows = session.scalars(select(Announcement).where(Announcement.created_at >= now - timedelta(days=days))
                           .order_by(Announcement.created_at.desc())).all()
    return [{"id": a.id, "created_at": _aware(a.created_at).isoformat(),
             "deliver_at": _aware(a.deliver_at).isoformat() if a.deliver_at else None,
             "title": a.title, "body": a.body, "catalog_ids": json.loads(a.catalog_ids),
             "store_ids": json.loads(a.store_ids)} for a in rows]


def today_tr(now: datetime | None = None) -> date:
    return (now or datetime.now(timezone.utc)).astimezone(TR).date()
