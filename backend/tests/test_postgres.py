"""PostgreSQL entegrasyon testi. FIRSAT_TEST_PG_URL ayarlı değilse atlanır (atlandı olarak raporlanır)."""
import os
from datetime import date

import pytest
from sqlalchemy import text

from firsat360.connectors.snapshot import SnapshotSource
from firsat360.db import Base, make_engine, make_session_factory
from firsat360.feed import build_feed
from firsat360.pipeline import import_snapshot

from .conftest import SNAPSHOT

PG = os.getenv("FIRSAT_TEST_PG_URL")
pytestmark = [pytest.mark.postgres, pytest.mark.real_data,
              pytest.mark.skipif(not PG, reason="FIRSAT_TEST_PG_URL ayarlı değil")]


def test_postgres_roundtrip():
    eng = make_engine(PG)
    Base.metadata.drop_all(eng)
    sf = make_session_factory(PG)
    out = import_snapshot(sf, SnapshotSource(SNAPSHOT).all())
    assert sum(r["offers"] for r in out) == 269
    # İkinci içe aktarma mükerrer kayıt üretmemeli
    import_snapshot(sf, SnapshotSource(SNAPSHOT).all())
    with sf() as s:
        assert s.execute(text("select count(*) from offers")).scalar() == 269
        assert s.execute(text("select server_version_num from (select current_setting('server_version_num') "
                              "as server_version_num) x")).scalar()
        feed = build_feed(s, today=date(2026, 10, 12))
        assert len(feed["offers"]) == 269
        # Türkçe karakterler veritabanında bozulmadan saklanıyor
        assert s.execute(text("select name from offers where name = 'ÇİKOLATALI GOFRET MAXİ'")).scalar()


def test_postgres_two_day_history():
    """12.10 + 13.10 gerçek anlık görüntüleri PostgreSQL'de: yayından kaldırma, fiyat geçmişi, saat dilimi."""
    from .conftest import SNAPSHOT_13
    eng = make_engine(PG)
    Base.metadata.drop_all(eng)
    sf = make_session_factory(PG)
    import_snapshot(sf, SnapshotSource(SNAPSHOT).all())
    import_snapshot(sf, SnapshotSource(SNAPSHOT_13).all())
    import_snapshot(sf, SnapshotSource(SNAPSHOT_13).all())  # tekrar: mükerrer gözlem/kayıt üretmemeli
    with sf() as s:
        q = lambda sql: s.execute(text(sql)).scalar()  # noqa: E731
        assert q("select count(*) from offers where published") == 39 + 347 + 40 + 10 + 30 + 65  # + Tarım Kredi
        assert q("select count(*) from offers where store_id='a101' and not published") == 2
        assert q("select count(*) from price_observations po join offers o on o.id=po.offer_id "
                 "where o.store_id='migros'") == 60
        assert q("select data_type from information_schema.columns where table_name='offers' "
                 "and column_name='last_seen_at'") == "timestamp with time zone"
        feed = build_feed(s, today=date(2026, 10, 13))
        assert len(feed["offers"]) == 466 + 65
    # Ürün sayfası görsellerinin doğrulanıp uygulanması PostgreSQL'de de aynı sonucu vermeli
    from firsat360.pipeline import apply_product_images
    stats = apply_product_images(sf, [SNAPSHOT, SNAPSHOT_13])
    assert stats["bim"] == {"verified": 347} and stats["tkkoop"]["verified"] == 61
    with sf() as s:
        assert s.execute(text("select count(*) from offers where published and image_status='verified'")).scalar() == 495
