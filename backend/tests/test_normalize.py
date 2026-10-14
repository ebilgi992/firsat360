from datetime import date
from decimal import Decimal

import pytest

from firsat360.normalize import (categorize, fold, format_try, kurus_to_decimal, offer_id, parse_price,
                                 parse_tr_date, parse_tr_range, tr_lower)

OBS = date(2026, 10, 12)


@pytest.mark.parametrize("text,expected", [
    ("445, 00 ₺", "445.00"), ("1.500, 00 ₺", "1500.00"), ("34.990, 00 ₺", "34990.00"),
    ("₺349,00", "349.00"), ("139.00 TL", "139.00"), ("249,00₺", "249.00"), ("19, 75 ₺", "19.75"),
    ("₺1.299,90", "1299.90"), ("1.500 TL", "1500.00"),
])
def test_parse_price_formats(text, expected):
    assert parse_price(text) == Decimal(expected)


@pytest.mark.parametrize("text", [None, "", "-", "Yakında", "Fiyat için tıklayın", "0,00 ₺"])
def test_parse_price_never_guesses(text):
    assert parse_price(text) is None


def test_kurus():
    assert kurus_to_decimal(49995) == Decimal("499.95")
    assert kurus_to_decimal(None) is None
    assert kurus_to_decimal(0) is None


def test_format_try():
    assert format_try(Decimal("34990")) == "34.990,00 ₺"
    assert format_try(Decimal("9.5")) == "9,50 ₺"


def test_turkish_lower_and_fold():
    assert tr_lower("IŞIK İĞNE") == "ışık iğne"
    assert fold("ŞAMPUAN Çeşitleri") == "sampuan cesitleri"
    assert fold("ÇİKOLATALI GOFRET MAXİ") == "cikolatali gofret maxi"
    assert fold("Yoğurt") == fold("YOĞURT") == "yogurt"
    assert fold("Işık") == "isik"


def test_dates():
    assert parse_tr_date("13 Ekim Salı", OBS) == date(2026, 10, 13)
    assert parse_tr_date("Kampanya 04 Mart tarihinden itibaren geçerlidir.", OBS) == date(2026, 3, 4)
    assert parse_tr_date("Kampanya 28 Ağustos tarihinden itibaren", OBS) == date(2026, 8, 28)
    assert parse_tr_date("Yakında", OBS) is None
    assert parse_tr_range("10-16 Ekim", OBS) == (date(2026, 10, 10), date(2026, 10, 16))
    assert parse_tr_range("1 ve 31 Ekim 2026 tarihleri arasında", OBS) == (date(2026, 10, 1), date(2026, 10, 31))
    assert parse_tr_range("14 Ekim Çarşamba", OBS) == (date(2026, 10, 14), None)


def test_year_rollover():
    assert parse_tr_date("05 Ocak", date(2026, 12, 20)) == date(2027, 1, 5)


@pytest.mark.parametrize("name,brand,url,expected", [
    ("Piranha 9946M Bluetooth Kulaklık", None, "https://www.a101.com.tr/elektronik/x_p-1", "elektronik"),
    ("Hindi Salam", "Pınar", None, "et-tavuk"),
    ("Bulgur Çeşitleri", "Saban", None, "temel-gida"),
    ("Elektrikli Oda Kokusu Kit", "Airwick", None, "temizlik"),
    ("STAR SC300 ELİPTİK BİSİKLET", "DYNAMIC", None, "spor"),
    ("Kahve Dünyası Tambol Bademli Sütlü Çikolata 77 G", None, None, "atistirmalik"),
    ("Hindistan Cevizli Mini Kek", "Eti Popkek", None, "atistirmalik"),
    ("Tamamen Bilinmeyen Nesne", None, None, "diger"),
])
def test_categorize(name, brand, url, expected):
    assert categorize(name, brand, None, url)[0] == expected


def test_offer_id_stable_and_case_insensitive():
    a = offer_id("bim", "bim-1681", "Pınar", "Hindi Salam", "50 g")
    b = offer_id("bim", "bim-1681", "PINAR", "HİNDİ SALAM", "50 g")
    assert a == b and a.startswith("bim-")
    assert a != offer_id("bim", "bim-1682", "Pınar", "Hindi Salam", "50 g")


def test_size_token_and_stable_identity():
    from firsat360.normalize import offer_id_full, size_token
    assert size_token("3x140 g") == "3x140g" and size_token("1,5 Kg") == "1.5kg"
    assert size_token("Kınık Maden Suyu 6 x 200 Ml") == "6x200ml" and size_token("45 Kapsül") == ""
    # Açıklama metni farklı, ölçü aynı → aynı kimlik (fiyat geçmişi kopmaz)
    a = offer_id("bim", "bim-1690", "Pakmaya", "Puding Çeşitleri", "396 g; 2 Kakaolu Puding, 1 Çikolatalı Puding")
    b = offer_id("bim", "bim-1690", "Pakmaya", "Puding Çeşitleri", "396 g")
    assert a == b
    # Ölçü farklı → farklı ürün
    assert offer_id("bim", "c", "Saban", "Nohut", "2,5 kg") != offer_id("bim", "c", "Saban", "Nohut", "1 kg")
    assert offer_id_full("bim", "c", "Airwick", "Yedek", "Lavanta") != offer_id_full("bim", "c", "Airwick", "Yedek", "Bahar")


def test_new_categories():
    assert categorize("55 İNÇ QNED MİNİLED 4K TV")[0] == "elektronik"
    assert categorize("Leopar Desenli Gondol Terlik")[0] == "giyim"
    assert categorize("BAY AYAKKABI KUTUSU")[0] == "ev-yasam"
    assert categorize("HOT WHEELS XLARGE 1:43")[0] == "oyuncak"
    assert categorize("Wee Baby Kutulu Çatal Kaşık Seti Mavi")[0] != "elektronik"  # 'kutulu' içinde 'utu' tuzağı
    assert categorize("Bebelac 3 Devam Sütü 800 G")[0] == "bebek"
