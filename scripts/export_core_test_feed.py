"""Eski anlık görüntüye dayalı JVM testlerini değişen canlı APK verisinden ayırır."""
import json
import sys
from datetime import date
from pathlib import Path
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from firsat360.connectors.snapshot import snapshot_dirs
from firsat360.db import Base
from firsat360.feed import build_feed
from firsat360.pipeline import import_snapshots

engine = create_engine('sqlite://')
Base.metadata.create_all(engine)
sf = sessionmaker(engine, expire_on_commit=False)
import_snapshots(sf, snapshot_dirs(Path('data/snapshots')))
with sf() as session:
    feed = build_feed(session, today=date(2026, 10, 13))
# Bu fixture 12–13 Ekim kaynaklarını kapsar; sonraki marketler canlı paket içinde kalır.
ids = ['a101','bim','sok','migros','tkkoop','hakmar','watsons','metro','bizimtoptan','gratis']
feed['stores'] = [next(s for s in feed['stores'] if s['id'] == i) for i in ids]
feed['generated_at'] = '2026-10-13T10:00:00+00:00'
Path(sys.argv[1]).write_text(json.dumps(feed, ensure_ascii=False), encoding='utf-8')
print('Historical JVM fixture:', len(feed['offers']), 'offers')
