"""1.6.0 yeni market zincirleri (yalnızca market): Mopaş, Happy Center, Yunus Market — 23.10.2026'da resmî
sayfalarda gözlenen yapıların ayrıştırılması (çevrim dışı; canlı çalıştırma GitHub Actions'ta)."""
from datetime import date

from firsat360.config import STORES, STORE_STYLE
from firsat360.connectors import live_connectors
from firsat360.connectors.discovery import SOURCES, _numeric_range, scan_html, to_catalogs

D = date(2026, 10, 23)


def test_new_markets_configured_and_connected():
    for sid in ("mopas", "happycenter", "yunus"):
        assert sid in STORES and sid in STORE_STYLE and sid in live_connectors()


def test_mopas_pdf_with_cover_and_dates_from_filename():
    html = ('<html><body><h1>Kataloglar</h1><a href="https://cdnprod.mopas.com.tr/x/hf8/17-30.10.26.pdf">'
            '<img src="https://cdnprod.mopas.com.tr/x/h0d/HERO.png"></a><img src="https://cdnprod.mopas.com.tr/x/BG.jpg">'
            '</body></html>')
    f = scan_html(html, "https://mopas.com.tr/kataloglar", SOURCES["mopas"])
    cats = to_catalogs("mopas", "https://mopas.com.tr/kataloglar", f, D, 0, SOURCES["mopas"].title)
    assert len(cats) == 1 and cats[0].kind == "pdf" and cats[0].title == "Mopaş Kataloğu"
    assert (cats[0].valid_from, cats[0].valid_to) == (date(2026, 10, 17), date(2026, 10, 30))
    assert cats[0].cover_url.endswith("/HERO.png") and not f.images  # kapak sayfa görseli sayılmaz


def test_happycenter_bulletin_pages():
    html = ('<html><body><h1>İndirim Bülteni</h1>'
            '<img src="https://crm.happycenter.tr/admin/images/uploads/happy.jpg">'
            '<img src="https://crm.happycenter.tr/admin/images/uploads/happy2.jpg">'
            '<img src="https://www.happycenter.com.tr/image/catalog/logo.png"></body></html>')
    f = scan_html(html, "https://www.happycenter.com.tr/kurumsal/indirim-bulteni/", SOURCES["happycenter"])
    assert [u.rsplit("/", 1)[-1] for u in f.images] == ["happy.jpg", "happy2.jpg"]


def test_yunus_insert_pdfs_titles_dates_and_kvkk_skipped():
    html = ('<html><body><a href="/images/dosyalar/insert-brosur/a/54cde7c9-d860_23-30.pdf">23-30 Ekim Kampanyalı Ürünler Yayında!</a>'
            '<a href="/fileman/uploads/documents/bilgi-toplumu-hizmetleri/1_kisisel_verilerin_korunmasi.pdf">KVKK Politikası</a></body></html>')
    f = scan_html(html, "https://www.yunusmarket.com.tr/insert-brosur", SOURCES["yunus"])
    assert len(f.pdfs) == 1
    c = to_catalogs("yunus", "https://www.yunusmarket.com.tr/insert-brosur", f, D, 0, SOURCES["yunus"].title)[0]
    assert c.title.startswith("23-30 Ekim") and (c.valid_from, c.valid_to) == (date(2026, 10, 23), date(2026, 10, 30))


def test_numeric_range():
    assert _numeric_range("17-30.10.26.pdf", D) == (date(2026, 10, 17), date(2026, 10, 30))
    assert _numeric_range("katalog.pdf", D) == (None, None)
