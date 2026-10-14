"""GERÇEK veri testleri: 12.10.2026'da resmî sayfalardan alınan anlık görüntü.

Bu testler canlı ağ isteği yapmaz; resmî sayfalardan okunmuş veriyi doğrular (sayılar, bilinen fiyatlar,
kaynak/zaman alanları, uydurma değer olmaması).
"""
from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import select

from firsat360.connectors.snapshot import SnapshotSource
from firsat360.db import Catalog, Offer, Store
from firsat360.feed import build_feed
from firsat360.pipeline import import_snapshot

from .conftest import SNAPSHOT

pytestmark = pytest.mark.real_data


@pytest.fixture
def loaded(sf):
    out = import_snapshot(sf, SnapshotSource(SNAPSHOT).all())
    return sf, {r["store"]: r for r in out}


def test_counts_per_store(loaded):
    sf, res = loaded
    assert {k: v["offers"] for k, v in res.items()} == {"a101": 39, "bim": 190, "sok": 10, "migros": 30}
    assert all(v["rejected"] == 0 for v in res.values())


def test_known_real_prices(loaded):
    sf, _ = loaded
    with sf() as s:
        def get(store, name):
            return s.scalars(select(Offer).where(Offer.store_id == store, Offer.name == name)).one()
        assert get("bim", "Ayçiçek Yağı").price == Decimal("445.00")
        assert get("bim", "CREALITY OTTER LITE 3D TARAYICI").price == Decimal("34990.00")
        assert get("a101", "Sütaş Yarım Yağlı Süt 1 L").price == Decimal("49.50")
        assert get("a101", "Sütaş Yarım Yağlı Süt 1 L").store_declared_old_price == Decimal("65.00")
        assert get("migros", "Komili Ayçiçek Yağı 5 L").price == Decimal("499.95")
        assert get("sok", "Jacobs Monarch Gold 200g").price == Decimal("325.00")


def test_unknowns_are_not_invented(loaded):
    sf, _ = loaded
    with sf() as s:
        offers = s.scalars(select(Offer)).all()
        assert all(o.stock_status == "unknown" and o.scope == "unverified" for o in offers)
        assert all(o.image_url is None for o in offers)
        a101 = [o for o in offers if o.store_id == "a101"]
        assert all(o.valid_from is None and o.valid_to is None for o in a101)  # sayfada tarih yok
        piranha = next(o for o in a101 if o.name.startswith("Piranha 9946M"))
        assert piranha.store_declared_old_price is None  # sayfada eski fiyat yok
        banvit = s.scalars(select(Offer).where(Offer.name == "Banvit Piliç Pirzola Kg")).one()
        assert banvit.store_declared_old_price is None  # regularPrice == shownPrice
        bim_tue = s.get(Catalog, "bim-1681")
        assert bim_tue.valid_from == date(2026, 10, 13) and bim_tue.valid_to is None


def test_every_offer_has_source_and_time(loaded):
    sf, _ = loaded
    with sf() as s:
        for o in s.scalars(select(Offer)):
            assert o.source_url.startswith("https://www.")
            assert o.product_url and o.product_url.startswith("https://www.")
            assert o.source_method == "manual_snapshot" and o.verification == "tek_okuma_arac_aracili"
            assert o.last_seen_at.year == 2026
            assert o.data_mode == "real"


def test_store_health_after_snapshot(loaded):
    sf, _ = loaded
    with sf() as s:
        for st in s.scalars(select(Store)):
            if st.id in ("a101", "bim", "sok", "migros"):
                assert st.status == "ok" and st.last_success_at is not None
            else:  # 12.10 anlık görüntüsünde verisi olmayan (13.10'da eklenen) mağazalar dürüstçe "hiç çalışmadı"
                assert st.status == "never_run" and st.last_success_at is None


def test_feed_categories_and_catalogs(loaded):
    sf, _ = loaded
    with sf() as s:
        feed = build_feed(s, today=date(2026, 10, 12))
        assert len(feed["offers"]) == 269
        assert sum(1 for o in feed["offers"] if o["category"] == "diger") == 0
        kinds = {c["kind"] for c in feed["catalogs"]}
        assert {"aktuel", "campaign", "discount_list", "link_only"} <= kinds
        # 17 Ekim'de "10-16 Ekim" kataloğu ve ürünleri süresi dolduğu için gizlenir
        later = build_feed(s, today=date(2026, 10, 17))
        assert not any(c["id"] == "bim-1690" for c in later["catalogs"])
        assert len(later["offers"]) == 269 - 88
