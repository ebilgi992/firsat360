"""Ürün görseli doğrulama (imagematch) — gerçek 13.10.2026 anlık görüntüsü + sentetik ret senaryoları."""
import json
from decimal import Decimal

import pytest
from sqlalchemy import select

from firsat360.db import Catalog, Offer
from firsat360.feed import build_feed
from firsat360.imagematch import PageImage, index_pages, name_score, sizes, verify
from firsat360.pipeline import import_snapshots

from .conftest import SNAPSHOT, SNAPSHOT_13


def _idx(*rows):
    return index_pages([PageImage(*r) for r in rows])


U = "https://www.bim.com.tr/aktuel-urunler/x/aktuel.aspx"


@pytest.mark.fixture_data
def test_size_equivalence_and_tokens():
    assert ("ml", 1000.0) in sizes("1 L") and ("ml", 1000.0) in sizes("1000 ml")
    assert ("g", 2500.0) in sizes("TK PİLAVLIK BULGUR 2,5 KG")
    assert ("g", 420.0) in sizes("3x140 g") and ("g", 140.0) in sizes("3x140 g")
    assert name_score("Elektrikli Oda Kokusu Yedek", "Elektrikli Oda Kokusu Yedek Bahar Çiçekleri") == 1.0


@pytest.mark.fixture_data
def test_verify_rejects_wrong_name_and_wrong_size():
    idx = _idx(("bim", U, "Zeytinyağı", "1 L", "300,00 ₺", "https://cdn/x.jpg"))
    m = verify("bim", U, "Ayçiçek Yağı", "ABALI", "5 L", Decimal("445"), None, idx)
    assert m.image_url is None and m.status in ("size_mismatch", "name_mismatch")
    idx = _idx(("bim", U, "Ayçiçek Yağı", "1 L", "445,00 ₺", "https://cdn/x.jpg"))
    m = verify("bim", U, "Ayçiçek Yağı", "ABALI", "5 L", Decimal("445"), None, idx)
    assert m.image_url is None and m.status == "size_mismatch"
    m = verify("bim", U + "?baska", "Ayçiçek Yağı", None, "1 L", Decimal("445"), None, idx)
    assert m.status == "no_page"


@pytest.mark.fixture_data
def test_verify_picks_right_variant_and_flags_price_conflict():
    idx = _idx(("bim", U, "Oda Kokusu Yedek", "Temizliğin Esintisi", "159,00 ₺", "https://cdn/a.jpg"),
               ("bim", U, "Oda Kokusu Yedek", "Bahar Çiçekleri", "159,00 ₺", "https://cdn/b.jpg"))
    m = verify("bim", U, "Oda Kokusu Yedek", None, "Bahar Çiçekleri", Decimal("159"), None, idx)
    assert (m.status, m.image_url, m.price_conflict) == ("verified", "https://cdn/b.jpg", None)
    m = verify("bim", U, "Oda Kokusu Yedek", None, "Bahar Çiçekleri", Decimal("149"), None, idx)
    assert m.price_conflict == Decimal("159.00")
    # mağaza beyanı önceki fiyata eşit sayfa fiyatı çelişki değildir (kampanya fiyatı sepette)
    m = verify("bim", U, "Oda Kokusu Yedek", None, "Bahar Çiçekleri", Decimal("99"), Decimal("159"), idx)
    assert m.price_conflict is None


@pytest.fixture
def loaded_all(sf):
    import_snapshots(sf, [SNAPSHOT, SNAPSHOT_13])
    return sf


@pytest.mark.real_data
def test_real_product_images_verified_and_honest(loaded_all):
    with loaded_all() as s:
        offers = s.scalars(select(Offer).where(Offer.published.is_(True))).all()
        by = {}
        for o in offers:
            by.setdefault(o.store_id, []).append(o)
        for sid in ("bim", "a101", "sok", "tkkoop"):
            ver = [o for o in by[sid] if o.image_status == "verified"]
            assert ver, sid
            for o in ver:
                assert o.image_url and o.image_page_url == o.product_url  # görsel ürünün KENDİ sayfasından
                assert float(o.image_match_score) >= 0.5
        assert all(o.image_status == "official_json" and o.image_url for o in by["migros"])
        # sayfada fotoğraf olmayan ürünlere görsel konmadı
        no_img = [o for o in offers if o.image_status in ("no_image_on_page", "page_empty", "no_page")]
        assert all(o.image_url is None for o in no_img)
        assert {o.name for o in no_img if o.store_id == "tkkoop"} == {
            "Bolca Kalem Börek 400 g", "TK Kırmızı Acı Pul Biber 250 g", "Teksüt Misket Peynir 200 g",
            "Teksüt Çubuk Peynir 200 g"}
        total = len(offers)
        with_img = sum(1 for o in offers if o.image_url)
        assert with_img / total > 0.98, (with_img, total)
        # sayfa fiyatı farklı → fiyat değiştirilmez, işaretlenir
        bulgur = next(o for o in by["tkkoop"] if "Pilavlık Bulgur" in o.name)
        assert bulgur.price == Decimal("99.00") and bulgur.page_price_conflict == Decimal("107.50")
        assert bulgur.page_size_text == "2,5 kg"


@pytest.mark.real_data
def test_real_new_stores_in_feed(loaded_all):
    with loaded_all() as s:
        feed = build_feed(s, today=__import__("datetime").date(2026, 10, 13))
    ids = [x["id"] for x in feed["stores"]]
    assert ids[:4] == ["a101", "bim", "sok", "migros"]
    assert {"tkkoop", "hakmar", "watsons", "metro", "bizimtoptan", "gratis"} <= set(ids)
    assert all(x["color"].startswith("#") and x["short"] for x in feed["stores"])
    cats = {c["id"]: c for c in feed["catalogs"]}
    hak = cats["hakmar-genel-insert-0819-ekim"]
    assert len(hak["pages"]) == 8 and hak["valid_to"] == "2026-10-19"
    assert all(p["url"].startswith("https://hakmar.com.tr/wp-content/uploads/") for p in hak["pages"])
    assert cats["gratis-ekim-katalog"]["cover_url"].startswith("https://api.gratis.retter.io/")
    assert cats["tkkoop-kat-kat-firsat-1319-ekim"]["offer_count"] == 65
    tk = [o for o in feed["offers"] if o["store_id"] == "tkkoop"]
    assert len(tk) == 65 and all(o["valid_to"] == "2026-10-19" for o in tk)
    v = [o for o in feed["offers"] if o["image_source"] == "product_page_verified"]
    assert len(v) >= 490
    assert all(o["image_page_url"] for o in v)
    json.dumps(feed, ensure_ascii=False)


@pytest.mark.real_data
def test_verified_image_survives_reimport_without_image(loaded_all):
    """Kaynağı görselsiz bir sonraki okuma, doğrulanmış ürün sayfası görselini silmemeli."""
    from firsat360.connectors.snapshot import SnapshotSource
    from firsat360.pipeline import import_snapshot
    import_snapshot(loaded_all, SnapshotSource(SNAPSHOT_13).all())
    with loaded_all() as s:
        o = s.scalars(select(Offer).where(Offer.store_id == "bim", Offer.image_status == "verified")).first()
        assert o is not None and o.image_url.startswith("https://cdn1.bim.com.tr/uploads/aktuel-urunler/")
        assert s.get(Catalog, "hakmar-kirtasiye-2026").valid_from.isoformat() == "2026-08-21"



def test_tk_brands_and_pack_counts_do_not_cross_match():
    from firsat360.imagematch import PageImage, index_pages, verify
    url = 'https://www.tkkoop.com.tr/urun/banvit-pilic-nugget-1000-g'
    page = PageImage('tkkoop', url, 'Doyfarm Piliç Nugget 1 kg', None, None,
                     'https://www.tkkoop.com.tr/storage/assets/images/urun/doyfarm.jpg')
    result = verify('tkkoop', url, 'Banvit Piliç Nugget 1000 g', None, None, None, None, index_pages([page]))
    assert result.status == 'name_mismatch'
    page.page_name = 'Banvit Piliç Nugget 1 kg'
    assert verify('tkkoop', url, 'Banvit Piliç Nugget 1000 g', None, None, None, None, index_pages([page])).status == 'verified'
    page.page_name = "Renax Bulaşık Makinesi Kapsülü 30'lu"
    assert verify('tkkoop', url, "Renax Bulaşık Makinesi Kapsülü 60'lı", None, None, None, None, index_pages([page])).status == 'size_mismatch'
