"""Ayrıştırıcı testleri — SENTETİK fikstürlerle (demo veri). Canlı site doğrulaması değildir."""
import json
from datetime import date
from decimal import Decimal

import pytest

from firsat360.connectors.a101 import parse_embedded_json, parse_html_products
from firsat360.connectors.bim import parse_products, parse_tabs
from firsat360.connectors.migros import parse_search_json
from firsat360.connectors.sok import campaign_price_from_title, parse_campaign_list, parse_campaign_page

from firsat360.connectors.base import PoliteClient

from .conftest import OBS, fixture_text, make_transport

pytestmark = pytest.mark.fixture_data


def test_bim_tabs():
    tabs = parse_tabs(fixture_text("bim_list.html"), date(2026, 10, 12))
    ids = {t.id: t for t in tabs}
    assert set(ids) == {"bim-1681", "bim-1682", "bim-1690", "bim-1600"}  # mükerrer sekme tekilleşti
    assert ids["bim-1681"].valid_from == date(2026, 10, 13)
    assert ids["bim-1690"].valid_to == date(2026, 10, 16)


def test_bim_products_primary_layout():
    offers, rejected = parse_products(fixture_text("bim_list.html"), "https://x", "bim-1681")
    assert rejected == 1  # "Yakında" yazan ürün uydurma fiyatla yayına alınmaz
    by = {o.name: o for o in offers}
    assert by["ANANASLI MEYVELİ YOĞURT"].price == Decimal("57.50")
    assert by["ANANASLI MEYVELİ YOĞURT"].brand == "DOST"
    assert by["ANANASLI MEYVELİ YOĞURT"].variant == "500 g"
    assert by["STAR SC300 ELİPTİK BİSİKLET"].price == Decimal("7950.00")
    assert by["STAR SC300 ELİPTİK BİSİKLET"].product_url.endswith("/star-sc300-eliptik-bisiklet-5-10/aktuel.aspx")


def test_bim_products_fallback_when_classes_change():
    offers, rejected = parse_products(fixture_text("bim_changed_layout.html"), "https://x", "bim-1681")
    by = {o.name: o for o in offers}
    assert by["Hindi Salam"].brand == "Pınar" and by["Hindi Salam"].price == Decimal("15.00")
    assert by["ÇİFT KAPILI MİNİ BUZDOLABI"].price == Decimal("8900.00")


def test_a101_html_products():
    offers, rejected = parse_html_products(fixture_text("a101_list.html"))
    by = {o.name: o for o in offers}
    assert rejected == 1  # işaretsiz iki fiyatlı ürün belirsiz → reddedildi
    assert by["Piranha 9946M Bluetooth Kulaklık"].price == Decimal("349.00")
    assert by["Piranha 9946M Bluetooth Kulaklık"].store_declared_old_price is None
    sut = by["Sütaş Yarım Yağlı Süt 1 L"]
    assert sut.price == Decimal("49.50") and sut.store_declared_old_price == Decimal("65.00")
    assert sut.channel == "online"


def test_a101_embedded_json_rejects_unitless_numbers():
    offers = parse_embedded_json(fixture_text("a101_nextdata.html"))
    assert [o.name for o in offers] == ["Porsion İçli Köfte Dondurulmuş 320 G"]
    assert offers[0].price == Decimal("95.00") and offers[0].store_declared_old_price == Decimal("119.50")


def test_sok_campaign_list_and_pages():
    camps = parse_campaign_list(fixture_text("sok_list.html"))
    assert len(camps) == 3 and all("-prm-" in u for _, u in camps)
    assert campaign_price_from_title("50 TL ve üzeri alışverişte 79.00 TL!") == Decimal("79.00")
    cat, offers, rej = parse_campaign_page(fixture_text("sok_campaign_79.html"), camps[1][1], camps[1][0], OBS)
    assert cat.valid_from == date(2026, 10, 4)
    o = offers[0]
    assert o.price == Decimal("79.00") and o.store_declared_old_price == Decimal("120.00")
    assert o.condition == "50 TL ve üzeri alışverişte"
    cat2, offers2, _ = parse_campaign_page(fixture_text("sok_campaign_bogo.html"), camps[2][1], camps[2][0], OBS)
    assert offers2[0].price == Decimal("249.00") and offers2[0].store_declared_old_price is None
    assert "1 Alana 1 Bedava" in offers2[0].condition


def test_migros_json():
    offers, pages, rejected = parse_search_json(json.loads(fixture_text("migros_search.json")))
    assert pages == 1 and rejected == 1
    komili = offers[0]
    assert komili.price == Decimal("499.95") and komili.store_declared_old_price == Decimal("586.95")
    assert komili.product_url == "https://www.migros.com.tr/komili-aycicek-yagi-5-l-p-3eb6ee"
    assert offers[1].store_declared_old_price is None  # eşit fiyat indirim sayılmaz


# --- ŞOK "İndirimli Ürünler" listesi (SENTETİK fikstür) ---------------------------------------------------
def test_sok_discount_list_price_rules():
    from firsat360.connectors.sok import parse_discount_list
    offers, rejected = parse_discount_list(fixture_text("sok_discount.html"))
    by = {o.name: o for o in offers}
    assert set(by) == {"Elit Rattan Koltuk", "Milka Oreo 300 g", "Lezzcafe 2'si 1 Arada 12 g"}
    assert by["Elit Rattan Koltuk"].price == Decimal("699.00")
    assert by["Elit Rattan Koltuk"].store_declared_old_price == Decimal("999.00")  # üstü çizili işaretli
    assert by["Milka Oreo 300 g"].price == Decimal("199.00")                        # işaretsiz iki fiyat
    assert by["Milka Oreo 300 g"].store_declared_old_price == Decimal("399.00")
    assert by["Lezzcafe 2'si 1 Arada 12 g"].store_declared_old_price is None
    assert rejected == 1                                                             # üç fiyatlı belirsiz kart
    assert all(o.catalog_id == "sok-indirimli" and o.product_url.startswith("https://www.sokmarket.com.tr/")
               for o in offers)


def test_sok_connector_reads_discount_list_and_campaigns(no_sleep):
    from firsat360.connectors.sok import SokConnector
    client = PoliteClient("test", transport=make_transport(), sleep=lambda s: None)
    res = SokConnector(max_pages=2, max_list_pages=3).fetch(client, OBS)
    cats = {c.id for c in res.catalogs}
    assert "sok-indirimli" in cats and any(c.startswith("sok-") and c != "sok-indirimli" for c in cats)
    assert sum(1 for o in res.offers if o.catalog_id == "sok-indirimli") == 3
    assert not res.partial


def test_sok_discount_list_failure_keeps_campaigns(no_sleep):
    from firsat360.connectors.sok import DISCOUNT_URL, SokConnector
    client = PoliteClient("test", transport=make_transport({DISCOUNT_URL: (500, "hata")}), sleep=lambda s: None)
    res = SokConnector(max_pages=2).fetch(client, OBS)
    assert res.partial and any("İndirimli ürünler okunamadı" in w for w in res.warnings)
    assert res.offers and all(o.catalog_id != "sok-indirimli" for o in res.offers)
