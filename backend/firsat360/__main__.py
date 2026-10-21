"""Komut satırı: python -m firsat360 <komut>

  run [mağaza..]   Canlı taramayı şimdi çalıştırır (hepsi veya seçilenler)
  import-snapshot  data/snapshots/<tarih>/raw altındaki gerçek veri anlık görüntülerini (eskiden yeniye) içe aktarır
  export           Uygulamanın okuduğu feed.json dosyasını yazar
  schedule         Yapılandırılmış tarama saatlerini listeler
  daily            Günlük tam senkronizasyon: tüm mağazalar → ürün görselleri → yeni katalog duyurusu → push → feed
  serve            İsteğe bağlı salt okunur JSON API (requirements-api.txt; yalnızca kendi sunucunuzda)
"""
from __future__ import annotations

import argparse
import json
import logging
from pathlib import Path

from .config import ROOT, Settings
from .connectors import live_connectors
from .connectors.snapshot import snapshot_dirs
from .db import make_session_factory
from .feed import export_feed
from .pipeline import import_snapshots, run_all


def main(argv=None):
    p = argparse.ArgumentParser(prog="firsat360")
    sub = p.add_subparsers(dest="cmd", required=True)
    r = sub.add_parser("run")
    r.add_argument("stores", nargs="*")
    i = sub.add_parser("import-snapshot")
    i.add_argument("--dir", default=None, help="Tek bir anlık görüntü klasörü (varsayılan: hepsi, tarih sırasıyla)")
    e = sub.add_parser("export")
    e.add_argument("--out", default=None)
    sub.add_parser("schedule")
    d = sub.add_parser("daily")
    d.add_argument("--out", default=None, help="feed.json yolu (varsayılan FEED_EXPORT_PATH)")
    d.add_argument("--no-live", action="store_true", help="Canlı kaynakları atla (yalnızca duyuru/feed)")
    d.add_argument("--no-pages", action="store_true", help="Ürün sayfası görsel doğrulamasını atla")
    sv = sub.add_parser("serve")
    sv.add_argument("--host", default="127.0.0.1")
    sv.add_argument("--port", type=int, default=8000)
    a = p.parse_args(argv)

    settings = Settings()
    from .logs import setup as setup_logs
    setup_logs(settings.log_dir, settings.log_max_bytes, settings.log_keep_days)
    sf = make_session_factory(settings.database_url)
    conns = live_connectors(settings.max_pages_per_store)

    if a.cmd == "run":
        print(json.dumps(run_all(sf, conns, settings, trigger="manual", only=a.stores or None),
                         ensure_ascii=False, indent=1, default=str))
    elif a.cmd == "import-snapshot":
        dirs = [Path(a.dir)] if a.dir else snapshot_dirs(ROOT / "data" / "snapshots")
        for name, res in import_snapshots(sf, dirs):
            print(name, json.dumps(res, ensure_ascii=False))
    elif a.cmd == "export":
        with sf() as s:
            print(export_feed(s, a.out or settings.feed_export_path))
    elif a.cmd == "daily":
        from .sync import run_daily
        report = run_daily(sf, conns, settings, trigger="schedule", live=not a.no_live,
                           verify_pages=not a.no_pages, feed_path=a.out)
        print(json.dumps(report, ensure_ascii=False, indent=1, default=str))
        # Hiçbir mağaza güncellenemediyse iş akışı başarısız görünsün (izlenebilirlik); veri yine korunur.
        if report.get("ok_stores", 0) == 0:
            raise SystemExit(2)
    elif a.cmd == "serve":
        import uvicorn
        from .api import create_app
        uvicorn.run(create_app(sf), host=a.host, port=a.port, proxy_headers=True)
    elif a.cmd == "schedule":
        for sid in conns:
            print(sid, settings.schedule_for(sid), "etkin" if sid in settings.enabled_live_stores else "kapalı")


if __name__ == "__main__":
    main()
