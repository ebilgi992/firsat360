"""Katalog düzeltmeleri (30.10.2026): Bizim Toptan Heyzine, Hakmar h2 bölümleri, Publitas spreads.json, grup
birleştirme, ŞOK başlık/görsel boyu, bitişsiz kataloglar, mağaza adresleri, Tarım Kredi başlığı.
Tüm sayfalar SENTETİK küçük parçalardır (ağ erişimi yok)."""
from __future__ import annotations

import json
from datetime import date, datetime, timezone

import httpx
import pytest
from sqlalchemy import select

from firsat360.connectors.base import CatalogData, ConnectorResult, PoliteClient
from firsat360.db import Catalog, Store

pytestmark = pytest.mark.fixture_data
NOW = datetime(2026, 10, 30, 6, 0, tzinfo=timezone.utc)
D = NOW.date()
JPEG = b"\xff\xd8\xff\xe0" + b"0" * 32
PDF = b"%PDF-1.7\n" + b"0" * 32


def _client(handler, robots=None):
    def h(request):
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text=(robots or {}).get(request.url.host, "User-agent: *\nDisallow:\n"))
        return handler(request)
    return PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(h), sleep=lambda s: None)


# ------------------------------------------------------------------ 1. Bizim Toptan (aflip.in → Heyzine)
BT_INDEX = """<html><body>
<a href="https://aflip.in/bt-gida"><img src="https://www.bizimtoptan.com.tr/k1.jpg" alt="bt"></a>
<a href="https://aflip.in/bt-temizlik"><img src="https://www.bizimtoptan.com.tr/k2.jpg" alt="bt"></a>
<a href="https://aflip.in/bt-kozmetik"><img src="https://www.bizimtoptan.com.tr/k3.jpg" alt="bt"></a>
</body></html>"""


def _book(title, cover, pdf=None):
    script = f'<script>var cfg={{"pdf":"{pdf.replace("/", chr(92) + "/")}"}};</script>' if pdf else ""
    return (f'<html><head><meta property="og:title" content="{title} - Heyzine Flipbooks">'
            f'<meta property="og:image" content="{cover}"></head><body>{script}</body></html>')


def test_bizimtoptan_heyzine_books_get_title_cover_and_verified_pdf():
    from firsat360.connectors.extra import FlipbookIndexConnector
    from firsat360.feed import catalog_group
    probed = []

    def handler(req):
        u = str(req.url)
        if req.url.host == "www.bizimtoptan.com.tr":
            return httpx.Response(200, text=BT_INDEX)
        if u == "https://aflip.in/bt-gida":
            return httpx.Response(200, text=_book("Gıda Kataloğu 30 Ekim - 12 Kasım", "https://cdnc.heyzine.com/a/thumb.jpg",
                                                  "https://cdnm.heyzine.com/flip-book/pdf/abc123.pdf"))
        if u == "https://aflip.in/bt-temizlik":  # PDF bağlantısı sayfada yok → yalnızca başlık/kapak
            return httpx.Response(200, text=_book("Temizlik Kataloğu", "https://cdnc.heyzine.com/b/thumb.jpg"))
        if u == "https://aflip.in/bt-kozmetik":  # PDF adresi HTML hata sayfası döndürüyor → PDF alınmaz
            return httpx.Response(200, text=_book("Kozmetik Kataloğu", "https://cdnc.heyzine.com/c/thumb.jpg",
                                                  "https://cdnm.heyzine.com/flip-book/pdf/bad999.pdf"))
        if req.url.host == "cdnm.heyzine.com":
            probed.append(u)
            return httpx.Response(200, content=PDF if "abc123" in u else b"<html>hata</html>")
        return httpx.Response(404)

    res = FlipbookIndexConnector("bizimtoptan", "https://www.bizimtoptan.com.tr/pages/katalog").fetch(_client(handler), NOW)
    by = {c.official_url.rsplit("/", 1)[-1]: c for c in res.catalogs}
    g, t, k = by["bt-gida"], by["bt-temizlik"], by["bt-kozmetik"]
    assert g.title == "Gıda Kataloğu 30 Ekim - 12 Kasım" and g.cover_url == "https://cdnc.heyzine.com/a/thumb.jpg"
    assert g.pdf_url == "https://cdnm.heyzine.com/flip-book/pdf/abc123.pdf" and g.kind == "pdf"
    assert g.valid_to == date(2026, 11, 12)
    assert t.title == "Temizlik Kataloğu" and t.pdf_url is None and t.kind == "flipbook"
    assert t.cover_url.endswith("/b/thumb.jpg")
    assert k.pdf_url is None and k.kind == "flipbook"  # sihirli bayt %PDF değil
    assert len(probed) == 2
    rows = [Catalog(id=c.id, store_id="bizimtoptan", title=c.title, kind=c.kind, official_url=c.official_url,
                    source_method="x", observed_at=NOW, data_mode="real") for c in res.catalogs]
    assert len({catalog_group(r) for r in rows}) == 3  # üç kitap tek "bt" grubuna düşmez


def test_bizimtoptan_robots_disallow_keeps_index_link_with_distinct_titles():
    from firsat360.connectors.extra import FlipbookIndexConnector

    def handler(req):
        if req.url.host == "www.bizimtoptan.com.tr":
            return httpx.Response(200, text=BT_INDEX)
        raise AssertionError(f"robots.txt yasakken istek yapıldı: {req.url}")

    client = _client(handler, robots={"aflip.in": "User-agent: *\nDisallow: /"})
    res = FlipbookIndexConnector("bizimtoptan", "https://www.bizimtoptan.com.tr/pages/katalog").fetch(client, NOW)
    titles = [c.title for c in res.catalogs]
    assert titles == ["Katalog · 1", "Katalog · 2", "Katalog · 3"]  # "bt" başlık sayılmaz; yine de ayrı kalırlar
    assert all(c.cover_url.startswith("https://www.bizimtoptan.com.tr/k") and c.kind == "flipbook" for c in res.catalogs)


# ------------------------------------------------------------------ 2. Hakmar: tarih <h2>'de, dosya adları "sayfaN-1"
def test_hakmar_groups_by_preceding_h2_section():
    from firsat360.connectors.extra import parse_hakmar
    base = "https://hakmar.com.tr/wp-content/uploads/2026/10/"
    html = ("<html><body><h2>İndirimler</h2><section><h2>29 Ekim-9 Kasım</h2><div>"
            + "".join(f'<img src="{base}sayfa{i}-1-300x424.jpg">' for i in (2, 1, 3, 4, 5, 6, 7, 8))
            + "</div></section><section><h2>5-11 Kasım</h2>"
            + "".join(f'<img src="{base}sayfa{i}-2.jpg">' for i in (1, 2))
            + f'</section><img src="{base}logo.png"></body></html>')
    cats = parse_hakmar(html, D)
    assert len(cats) == 2
    a, b = cats
    assert (a.valid_from, a.valid_to) == (date(2026, 10, 29), date(2026, 11, 9))
    assert len(a.pages) == 8 and [p["page"] for p in a.pages] == list(range(1, 9))
    assert a.pages[0]["url"] == base + "sayfa1-1.jpg"  # küçük boy → asıl dosya, dosya adındaki sayfa sırası
    assert a.cover_url == base + "sayfa1-1.jpg" and "29" in a.title and "Kasım" in a.title
    assert (b.valid_from, b.valid_to) == (date(2026, 11, 5), date(2026, 11, 11)) and len(b.pages) == 2
    assert a.id != b.id


# ------------------------------------------------------------------ 3. Publitas spreads.json
SPREADS = [
    {"pages": [{"number": 1, "images": {"at200": "/x/pages/p1-at200.jpg", "at600": "/x/pages/p1-at600.jpg",
                                         "at2000": "/x/pages/p1-at2000.jpg"}}]},
    {"pages": [{"number": 2, "images": {"at600": "/x/pages/p2-at600.jpg", "at2000": "/x/pages/p2-at2000.jpg"}},
               {"number": 3, "images": {"at600": "/x/pages/p3-at600.jpg", "at1200": "/x/pages/p3-at1200.jpg"}}]},
    {"pages": [{"number": 4, "images": {"at2000": "https://view.publitas.com/x/pages/p4-at2000.jpg"}}]},
]


def _publitas_handler(spreads_status=200, image=JPEG):
    asked = []

    def handler(req):
        p = req.url.path
        asked.append(p)
        if p == "/":
            return httpx.Response(302, headers={"location": "https://katalog.example.test/ekim-katalog-2026/page/1"})
        if p.endswith("/spreads.json"):
            return httpx.Response(spreads_status, text=json.dumps(SPREADS))
        if "/x/pages/" in p:
            return httpx.Response(200, content=image)
        # /page/N: her sayfa ilk sayfayı tekrar ediyor (eski yolun 1 sayfada kalma nedeni)
        return httpx.Response(200, text='<html><head><meta property="og:title" content="Ekim Kataloğu | Watsons">'
                                        '<link rel="image_src" href="https://view.publitas.com/y/p1.jpg"></head></html>')
    return handler, asked


def test_publitas_reads_all_pages_from_spreads_json():
    from firsat360.connectors.extra import PublitasConnector
    handler, asked = _publitas_handler()
    c = PublitasConnector("watsons", "https://katalog.example.test/").fetch(_client(handler), NOW).catalogs[0]
    assert [p["url"] for p in c.pages] == ["https://katalog.example.test/x/pages/p1-at2000.jpg",
                                           "https://katalog.example.test/x/pages/p2-at2000.jpg",
                                           "https://katalog.example.test/x/pages/p3-at1200.jpg",
                                           "https://view.publitas.com/x/pages/p4-at2000.jpg"]
    assert c.pages[0]["thumb_url"].endswith("p1-at600.jpg") and c.pages[2]["link"].endswith("/ekim-katalog-2026/page/3")
    assert c.kind == "aktuel" and not any("/page/2" in a for a in asked)
    # 6. yalnızca ay adı ("Ekim Kataloğu") → ay sonu
    assert (c.valid_from, c.valid_to) == (date(2026, 10, 1), date(2026, 10, 31))


@pytest.mark.parametrize("status,image", [(404, JPEG), (200, b"<html>giris</html>")])
def test_publitas_falls_back_to_page_walk(status, image):
    from firsat360.connectors.extra import PublitasConnector
    handler, _ = _publitas_handler(status, image)
    c = PublitasConnector("watsons", "https://katalog.example.test/").fetch(_client(handler), NOW).catalogs[0]
    assert [p["url"] for p in c.pages] == ["https://view.publitas.com/y/p1.jpg"]  # eski yol (yedek)


# ------------------------------------------------------------------ 4. aynı yayının iki kaydı tek grup
def test_same_first_page_or_content_hash_share_group():
    from firsat360.feed import catalog_groups

    def cat(i, title, pages=None, h=None, store="bim"):
        return Catalog(id=i, store_id=store, title=title, kind="aktuel", official_url="u", source_method="x",
                       observed_at=NOW, data_mode="real", valid_from=date(2026, 10, 30),
                       pages_json=json.dumps([{"url": u} for u in pages]) if pages else None, content_hash=h)
    a = cat("bim-1701", "30 Ekim Perşembe Aktüel", ["https://cdn.bim/afis/1.jpg", "https://cdn.bim/afis/2.jpg"])
    b = cat("bim-afis-1432", "30 Ekim Afişleri Aktüel", ["https://cdn.bim/afis/1.jpg?v=2"])
    c = cat("bim-1702", "Başka Aktüel", h="same")
    d = cat("bim-afis-9", "Başka Afiş", h="same")
    e = cat("bim-1703", "Ayrı Aktüel", ["https://cdn.bim/afis/9.jpg"])
    f = cat("a101-x", "Ayrı", ["https://cdn.bim/afis/1.jpg"], store="a101")  # başka mağaza birleşmez
    g = catalog_groups([a, b, c, d, e, f])
    assert g["bim-1701"] == g["bim-afis-1432"] and g["bim-1702"] == g["bim-afis-9"]
    assert len({g["bim-1701"], g["bim-1702"], g["bim-1703"], g["a101-x"]}) == 4


def test_feed_uses_merged_groups(sf):
    from firsat360.feed import build_feed
    from firsat360.pipeline import ensure_stores, ingest
    pages = [{"url": "https://cdn1.bim.com.tr/uploads/afisler/a1.jpg", "page": 1}]
    res = ConnectorResult(store_id="bim", method="live_html", observed_at=NOW)
    res.catalogs = [CatalogData(id="bim-1701", title="30 Ekim Perşembe Aktüel", kind="aktuel", official_url="https://x/1",
                                valid_from=D, valid_to=date(2026, 11, 5), pages=pages),
                    CatalogData(id="bim-afis-1432", title="30 Ekim Aktüel", kind="aktuel", official_url="https://x/2",
                                valid_from=D, valid_to=date(2026, 11, 5), pages=pages)]
    with sf() as s:
        ensure_stores(s)
        ingest(s, res)
        s.commit()
        feed = build_feed(s, today=D)
    groups = {c["id"]: c["group"] for c in feed["catalogs"] if c["store_id"] == "bim"}
    assert groups["bim-1701"] == groups["bim-afis-1432"]


# ------------------------------------------------------------------ 5. ŞOK başlık + galeri görsel boyu
SOK_CARD = """<div class="card"><img src="https://images.ceptesok.com/cdn-cgi/image/width=512,height=260,fit=pad,quality=80,format=webp/promotion-assets/sub-folder/a.jpg">
<div><a href="/haftanin-firsatlari-prm-0a1b2c3d-1111-2222-3333-444455556666"><div>Kampanya Detayı</div></a></div></div>
<div class="card2"><a href="/ucretsiz-teslimat-prm-c1f73e10-3cf9-4ef1-a954-32fa6dcdb901">Kampanya Detayı</a></div>"""


def test_sok_generic_link_text_uses_h1_or_slug():
    from firsat360.connectors.sok import parse_campaign_list, parse_campaign_page
    camps = parse_campaign_list(SOK_CARD)
    assert [t for t, _ in camps] == ["Haftanin Firsatlari", "Ucretsiz Teslimat"]  # "Kampanya Detayı" değil
    url = camps[0][1]
    cat, _, _ = parse_campaign_page("<html><body><h1>Haftanın Fırsatları</h1></body></html>", url, camps[0][0], NOW)
    assert cat.title == "Haftanın Fırsatları"
    cat2, _, _ = parse_campaign_page("<html><body></body></html>", url, "Kampanya Detayı", NOW)
    assert cat2.title == "Haftanin Firsatlari"
    cat3, _, _ = parse_campaign_page("<html><body><h1>Başka</h1></body></html>", url, "1 Alana 1 Bedava", NOW)
    assert cat3.title == "1 Alana 1 Bedava"  # anlamlı liste başlığı korunur


def test_sok_larger_thumb_same_cdn_url():
    from firsat360.connectors.sok import larger_thumb
    u = "https://images.ceptesok.com/cdn-cgi/image/width=512,height=260,fit=pad,quality=80,format=webp/promotion-assets/a.jpg"
    assert larger_thumb(u) == ("https://images.ceptesok.com/cdn-cgi/image/width=1600,fit=pad,quality=80,format=webp/"
                               "promotion-assets/a.jpg")
    assert larger_thumb("https://images.ceptesok.com/promotion-assets/a.jpg") == "https://images.ceptesok.com/promotion-assets/a.jpg"


# ------------------------------------------------------------------ 6. bitişsiz / eski kataloglar
def test_bim_single_day_tab_gets_week_end():
    from firsat360.connectors.bim import parse_tabs
    html = ('<a href="?Bim_AktuelTarihKey=1701">30 Ekim Perşembe</a>'
            '<a href="?Bim_AktuelTarihKey=1702">27 - 31 Ekim</a>')
    tabs = {t.id: t for t in parse_tabs(html, D)}
    assert (tabs["bim-1701"].valid_from, tabs["bim-1701"].valid_to) == (date(2026, 10, 30), date(2026, 11, 5))
    assert tabs["bim-1702"].valid_to == date(2026, 10, 31)  # yazılı bitiş korunur


def test_file_poster_month_in_filename_expires():
    from firsat360.connectors.discovery import SOURCES, Found, poster_month_end, to_catalogs
    old = "https://www.file.com.tr/uploads/file/banner/kampanya_072026_AFIS_web.jpg"
    cur = "https://www.file.com.tr/uploads/file/banner/kampanya_102026_AFIS_web.jpg"
    assert poster_month_end(old) == date(2026, 7, 31) and poster_month_end(cur) == date(2026, 10, 31)
    src = SOURCES["file"]
    c = to_catalogs("file", "https://www.file.com.tr/", Found(images=[old, cur]), D, 0, src.title, src.page_kind)[0]
    assert [p["url"] for p in c.pages] == [cur] and c.valid_to == date(2026, 10, 31)
    only_old = to_catalogs("file", "https://www.file.com.tr/", Found(images=[old]), D, 0, src.title, src.page_kind)[0]
    assert only_old.valid_to == date(2026, 7, 31)  # feed süresi geçmiş olarak gizler
    undated = to_catalogs("file", "https://www.file.com.tr/", Found(images=[old, "https://www.file.com.tr/u/x_AFIS.jpg"]),
                          D, 0, src.title, src.page_kind)[0]
    assert undated.valid_to is None and len(undated.pages) == 1


def test_month_only_title():
    from firsat360.normalize import parse_tr_month
    assert parse_tr_month("Watsons Ekim Kataloğu", D) == (date(2026, 10, 1), date(2026, 10, 31))
    assert parse_tr_month("aralik-katalog-2026", D) == (date(2026, 12, 1), date(2026, 12, 31))
    assert parse_tr_month("Kozmetik Deterjan", D) == (None, None)
    assert parse_tr_month("Ekim Kasım Fırsatları", D) == (None, None)  # iki ay: belirsiz


# ------------------------------------------------------------------ 7. mağaza adresleri yapılandırmadan güncellenir
def test_ensure_stores_updates_urls(sf):
    from firsat360.config import STORES
    from firsat360.pipeline import ensure_stores
    with sf() as s:
        ensure_stores(s)
        st = s.get(Store, "file")
        st.official_url = st.catalog_url = "https://www.filemarket.com.tr/"
        s.commit()
        ensure_stores(s)
        st = s.scalars(select(Store).where(Store.id == "file")).one()
        assert st.official_url == STORES["file"]["official_url"] == "https://www.file.com.tr/"
        assert st.catalog_url == STORES["file"]["catalog_url"]


# ------------------------------------------------------------------ 8. Tarım Kredi: broşür değil, kampanya ürünleri
def test_tkkoop_catalog_title_is_clear():
    from firsat360.connectors.extra import parse_tk_campaign
    html = ('<html><h1>Kat Kat Fırsat 30 Ekim - 5 Kasım</h1><div class="card"><a href="/urun/somon-500-g">SOMON 500 G</a>'
            '<span>279,00 TL</span></div></html>')
    cat, offers, _ = parse_tk_campaign(html, "https://www.tkkoop.com.tr/haftalik-kampanyalar/kat-kat-firsat", D)
    assert cat.title == "Kampanya ürünleri · Kat Kat Fırsat 30 Ekim - 5 Kasım"
    assert cat.cover_url is None and not cat.pages and len(offers) == 1
    cat2, _, _ = parse_tk_campaign("<html><h1>Haftalık Kampanya</h1></html>", "https://www.tkkoop.com.tr/x", D)
    assert cat2.title == "Kampanya ürünleri"
