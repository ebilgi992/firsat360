"""0.7 kök neden düzeltmeleri: canlı veritabanında (21.10.2026) görülen görsel kayıplarının regresyon testleri.
HTML parçaları, resmî sayfalarda 21.10.2026'da gözlenen görsel adresi kalıplarıyla yazılmıştır."""
from datetime import datetime, timezone
from decimal import Decimal

from firsat360.connectors.bim import parse_products
from firsat360.connectors.htmlutil import all_image_urls, parse
from firsat360.connectors.sok import parse_campaign_page, parse_discount_list
from firsat360.imagematch import PageImage, index_pages, is_junk_name, verify
from firsat360.productpages import main_image, page_row

BIM_DETAIL = """<html><head><title>Anasayfa - Bim A.Ş</title></head><body><header><img src="https://cdn2.bim.com.tr/templates/images/header-logo_w-3x.png"></header>
<main><div class="detailArea"><img src="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1681_buyuk_543X467_9808_1_0024_vector-smart-object.jpg">
<h2 class="subTitle">Sütaş</h2><h2 class="title">ANANASLI MEYVELİ YOĞURT</h2><div class="quantify">57,<span>50</span> ₺</div>
<a class="zoom" href="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1681_buyuk_543X467_9808_1_0024_vector-smart-object.jpg"></a></div>
<div class="otherProducts"><img src="https://cdn2.bim.com.tr/templates/images/taksit6.jpg"></div></main></body></html>"""

SOK_DETAIL = """<html><body><header><img src="https://images.ceptesok.com/cdn-cgi/image/width=49,height=49,fit=pad,quality=80,format=webp/logos/service-types/market-logo.svg"></header>
<main><h1>Perwoll Hızlı Yıkama 2 Lt</h1>
<img alt="product-thumb" src="https://images.ceptesok.com/cdn-cgi/image/width=600,height=600,fit=pad,quality=80,format=webp/product-assets/sub-folder/113e8239.jpg">
<span>249,00₺</span></main></body></html>"""


def test_bim_detail_page_without_og_image():
    row = page_row("bim", "https://www.bim.com.tr/aktuel-urunler/ananasli-meyveli-yogurt-5-10/aktuel.aspx", BIM_DETAIL)
    assert row.image_url.endswith("1681_buyuk_543X467_9808_1_0024_vector-smart-object.jpg")
    idx = index_pages([row])
    m = verify("bim", row.detail_url, "ANANASLI MEYVELİ YOĞURT", "Sütaş", None, Decimal("57.50"), None, idx)
    assert m.status == "verified" and m.page_name


def test_sok_detail_page_image_and_name():
    row = page_row("sok", "https://www.sokmarket.com.tr/perwoll-hizli-yikama-2-lt-p-609093", SOK_DETAIL)
    assert "product-assets/sub-folder/113e8239.jpg" in row.image_url and "logo" not in row.image_url
    m = verify("sok", row.detail_url, "Perwoll Hızlı Yıkama 2 Lt", None, None, Decimal("249"), None, index_pages([row]))
    assert m.status == "verified"


def test_redirected_page_is_gone_not_matched():
    # Kaldırılan BİM ürün sayfası ana sayfaya yönlendirir; ana sayfadaki ürün adları eşleşse bile görsel alınmaz.
    row = page_row("bim", "https://www.bim.com.tr/aktuel-urunler/x-4-10/aktuel.aspx", BIM_DETAIL,
                   final_url="https://www.bim.com.tr/")
    assert row.gone and row.image_url is None
    m = verify("bim", row.detail_url, "ANANASLI MEYVELİ YOĞURT", None, None, None, None, index_pages([row]))
    assert m.status == "page_gone"


def test_bim_listing_rejects_banner_images_and_reads_lazy():
    html = """<div class="product"><a href="/aktuel-urunler/ayran-27/aktuel.aspx">
      <img src="https://cdn1.bim.com.tr/uploads/aktuel-urunler/923_aycicek-yagi-sole-1l-atmosfer-hc.jpg"></a>
      <h2 class="subTitle">Torku</h2><h2 class="title">Ayran</h2><div class="quantify">49,<span>50</span> ₺</div></div>
      <div class="product"><a href="/aktuel-urunler/yogurt-27/aktuel.aspx">
      <img src="data:image/gif;base64,R0lG" data-lazy="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1692_kucuk_543X467_3002_27_0115_v.jpg"></a>
      <h2 class="subTitle">Torku</h2><h2 class="title">%3 Yağlı Yoğurt</h2><div class="quantify">209,<span>00</span> ₺</div></div>"""
    offers, _ = parse_products(html, "https://www.bim.com.tr/x", "bim-1692")
    by = {o.name: o for o in offers}
    assert by["Ayran"].image_url is None  # reklam afişi ürüne bağlanmaz
    assert by["%3 Yağlı Yoğurt"].image_url.endswith("1692_kucuk_543X467_3002_27_0115_v.jpg")


def test_sok_campaign_names_not_product_thumb_and_images():
    html = """<html><body><div><a href="/elseve-hydra-hyaluronic-maske-300-ml-p-458657">
      <img alt="product-thumb" src="/_next/image?url=https%3A%2F%2Fimages.ceptesok.com%2Fproduct-assets%2Fsub%2Fm.jpg&amp;w=640">
      <h3>Elseve Hydra Hyaluronic Maske 300 ml</h3></a><span>139,00 TL</span></div></body></html>"""
    _, offers, _ = parse_campaign_page(html, "https://www.sokmarket.com.tr/x-prm-ed047d9b-c5e0", "Kampanya",
                                       datetime(2026, 10, 21, tzinfo=timezone.utc))
    assert offers[0].name == "Elseve Hydra Hyaluronic Maske 300 ml"
    assert offers[0].image_url == "https://images.ceptesok.com/product-assets/sub/m.jpg"
    offers, _ = parse_discount_list(html)
    assert offers and offers[0].name != "product-thumb"


def test_scanner_finds_noscript_and_style_images():
    t = parse("""<div id=c><noscript><img src="https://x.com/a.webp"></noscript><i style="background-image:url('/b.png')"></i></div>""")
    assert all_image_urls(t.css_first("#c")) == ["https://x.com/a.webp", "/b.png"]


def test_junk_names():
    assert is_junk_name("product-thumb") and is_junk_name("") and not is_junk_name("Ayran")


def test_main_image_picks_largest_cdn_variant():
    html = """<main><img src="https://images.ceptesok.com/cdn-cgi/image/width=300/product-assets/a.jpg">
      <img src="https://images.ceptesok.com/cdn-cgi/image/width=1200/product-assets/a.jpg"></main>"""
    assert "width=1200" in main_image("sok", html, "https://www.sokmarket.com.tr/a-p-1")


def test_failed_check_keeps_listing_image(tmp_path):
    from sqlalchemy import create_engine
    from sqlalchemy.orm import Session
    from firsat360.db import Base, Offer
    from firsat360.imagematch import apply_to_db
    e = create_engine(f"sqlite:///{tmp_path}/t.db")
    Base.metadata.create_all(e)
    now = datetime(2026, 10, 21, tzinfo=timezone.utc)
    with Session(e) as s:
        s.add(Offer(id="o1", store_id="bim", name="Ayran", search_key="ayran", category="diger", category_source="test", published=True, price=Decimal("10"),
                    product_url="https://www.bim.com.tr/aktuel-urunler/ayran/aktuel.aspx", source_url="x",
                    source_method="live_html", verification="", data_mode="real", first_seen_at=now, last_seen_at=now,
                    image_url="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1_kucuk_a.jpg", image_status="listing"))
        s.commit()
        row = PageImage("bim", "https://www.bim.com.tr/aktuel-urunler/ayran/aktuel.aspx", None, None, None, None, gone=True)
        apply_to_db(s, [row], now)
        o = s.get(Offer, "o1")
        assert o.image_url and o.image_status == "listing"


def test_generic_seo_og_image_is_not_a_product_image():
    # Canlı veride 62 TK ürününe aynı genel paylaşım klasörü "doğrulanmış görsel" olarak bağlanmıştı.
    html = """<html><head><meta property="og:image" content="https://www.tkkoop.com.tr/storage/assets/images/seo/"></head>
      <body><main><h1>BOLCA KALEM BÖREK 400 G</h1><img src="/assets/images/urun/bolca_kalem.png"></main></body></html>"""
    row = page_row("tkkoop", "https://www.tkkoop.com.tr/urun/bolca-kalem-borek-400-g", html)
    assert row.image_url == "https://www.tkkoop.com.tr/assets/images/urun/bolca_kalem.png"
    html2 = html.replace('<img src="/assets/images/urun/bolca_kalem.png">', '<img src="/assets/image/yok.png">')
    assert page_row("tkkoop", "https://www.tkkoop.com.tr/urun/x", html2).image_url is None
