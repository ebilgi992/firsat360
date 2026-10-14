"""GERÇEK veri testleri: 12.10 ve 13.10.2026 anlık görüntülerinin art arda içe aktarılması.

İki günlük gerçek okuma ile: kaynaktan kalkan ürünlerin yayından alınması, yeni ürünlerin eklenmesi, aynı ürünün
kimliğinin günler arasında korunması (fiyat geçmişi), yeni BİM sekmeleri ve ŞOK indirim listesi doğrulanır.
Canlı ağ isteği yapılmaz.
"""
from datetime import date
from decimal import Decimal

import pytest
from sqlalchemy import func, select

from firsat360.connectors.snapshot import SnapshotSource, snapshot_dirs
from firsat360.db import Catalog, Offer, PriceObservation, Store
from firsat360.feed import build_feed
from firsat360.pipeline import import_snapshot

from .conftest import SNAPSHOT, SNAPSHOT_13, SNAPSHOTS

pytestmark = pytest.mark.real_data


@pytest.fixture
def two_days(sf):
    first = {r["store"]: r for r in import_snapshot(sf, SnapshotSource(SNAPSHOT).all())}
    second = {r["store"]: r for r in import_snapshot(sf, SnapshotSource(SNAPSHOT_13).all())}
    return sf, first, second


def test_snapshot_dirs_sorted():
    dirs = snapshot_dirs(SNAPSHOTS)
    assert [d.parent.name for d in dirs][:2] == ["2026-10-12", "2026-10-13"]


def test_second_day_counts(two_days):
    sf, _, second = two_days
    assert {k: v["offers"] for k, v in second.items()} == {"a101": 39, "bim": 347, "sok": 40, "migros": 30,
                                                            "tkkoop": 65, "hakmar": 0, "watsons": 0, "metro": 0,
                                                            "bizimtoptan": 0, "gratis": 0}
    assert all(v["rejected"] == 0 for v in second.values())


def test_a101_real_change_between_days(two_days):
    """A101 Aldın Aldın 12.10→13.10: 2 ürün çıktı, 2 ürün girdi (gerçek değişiklik)."""
    sf, _, _ = two_days
    with sf() as s:
        a101 = s.scalars(select(Offer).where(Offer.store_id == "a101")).all()
        published = {o.name for o in a101 if o.published}
        removed = {o.name for o in a101 if not o.published}
        assert len(published) == 39
        assert removed == {"Superfresh Frulove Brownie Topları Dondurulmuş 170 G",
                           "Sleepy Bio Naturel Premium Plus Gece Ekstra Hijyenik Ped 8'li"}
        assert {"Tat Midyat Bulgur 1 Kg", "Uno Çikolata Kremalı Kruvasan 30 G"} <= published


def test_identity_kept_across_days_builds_price_history(two_days):
    sf, _, _ = two_days
    with sf() as s:
        # Migros: 30 ürün iki gün de aynı → her biri için 2 gözlem
        migros = s.scalars(select(Offer).where(Offer.store_id == "migros", Offer.published.is_(True))).all()
        assert len(migros) == 30
        for o in migros:
            n = s.scalar(select(func.count()).select_from(PriceObservation).where(PriceObservation.offer_id == o.id))
            assert n == 2
        # BİM: 13 Ekim Salı sekmesindeki ürünlerin büyük çoğunluğu aynı kimlikle eşleşmeli
        tue = s.scalars(select(Offer).where(Offer.catalog_id == "bim-1681")).all()
        matched = [o for o in tue if o.published and o.first_seen_at.day == 12]
        assert len(matched) >= 85, len(matched)
        # Açıklaması farklı, adı/ölçüsü aynı iki Airwick yedek ayrı ürün olarak kalır
        airwick = [o for o in tue if o.published and o.name == "Elektrikli Oda Kokusu Yedek"]
        assert len(airwick) == 2


def test_new_bim_tabs_and_titles(two_days):
    sf, _, _ = two_days
    with sf() as s:
        fri = s.get(Catalog, "bim-1683")
        sun = s.get(Catalog, "bim-1684")
        assert fri.valid_from == date(2026, 10, 16) and sun.valid_from == date(2026, 10, 18)
        assert s.get(Catalog, "bim-1690").title == "10-16 Ekim İndirimleri"
        tv = s.scalars(select(Offer).where(Offer.name == "55 İNÇ QNED MİNİLED 4K TV 55QNED7EB3C")).one()
        assert tv.price == Decimal("37500.00") and tv.category == "elektronik"


def test_sok_discount_list_real(two_days):
    sf, _, _ = two_days
    with sf() as s:
        disc = s.scalars(select(Offer).where(Offer.catalog_id == "sok-indirimli")).all()
        assert len(disc) == 40
        assert all(o.store_declared_old_price and o.store_declared_old_price > o.price for o in disc)
        signal = next(o for o in disc if o.name.startswith("Signal White Now Gold"))
        assert (signal.price, signal.store_declared_old_price) == (Decimal("99.00"), Decimal("229.00"))
        # 12.10'daki kampanya sayfası ürünleri 13.10'da yeniden okunmadı → yayında kalır, son görülme 12.10
        camp = s.scalars(select(Offer).where(Offer.catalog_id == "sok-kampanyalar")).all()
        assert camp and all(o.published and o.last_seen_at.day == 12 for o in camp)


def test_store_last_check_is_latest_snapshot(two_days):
    sf, _, _ = two_days
    with sf() as s:
        for st in s.scalars(select(Store)):
            assert st.last_success_at.astimezone().date() >= date(2026, 10, 13)


def test_feed_after_two_days(two_days):
    sf, _, _ = two_days
    with sf() as s:
        feed = build_feed(s, today=date(2026, 10, 13))
        n = len(feed["offers"])
        assert n == 39 + 347 + 40 + 10 + 30 + 65  # + Tarım Kredi (13-19 Ekim)
        diger = [o["name"] for o in feed["offers"] if o["category"] == "diger"]
        assert len(diger) <= n * 0.05, diger
        assert not any(o["store_id"] == "a101" and "Brownie" in o["name"] for o in feed["offers"])


def test_bim_identity_survives_missing_size_text(two_days):
    """12.10'da açıklamasıyla ('American Burger 230 g…'), 13.10'da açıklamasız okunan aynı BİM ürünleri tek kayıt kalır."""
    sf, _, second = two_days
    with sf() as s:
        assert s.scalar(select(func.count()).select_from(Offer).where(
            Offer.store_id == "bim", Offer.published.is_(False))) == 0
        heinz = s.scalars(select(Offer).where(Offer.catalog_id == "bim-1690", Offer.name == "Sos Çeşitleri")).all()
        assert len(heinz) == 1 and heinz[0].first_seen_at.day == 12 and heinz[0].last_seen_at.day == 13
