"""Katalog sayfaları (afiş/kampanya görselleri), ürün görselleri, görsel politikası, analitik ve sütun göçü testleri."""
import json
import os
from datetime import date

import pytest
from sqlalchemy import create_engine, inspect, select, text

from firsat360.connectors.base import CatalogData
from firsat360.connectors.bim import attach_afis, parse_afis, parse_products
from firsat360.connectors.migros import parse_search_json
from firsat360.connectors.snapshot import SnapshotSource
from firsat360.connectors.sok import parse_campaign_banners, parse_discount_list
from firsat360.db import Catalog, Offer, make_session_factory
from firsat360.feed import build_feed
from firsat360.pipeline import import_snapshot

from .conftest import SNAPSHOT, SNAPSHOT_13, fixture_text


# --- SENTETİK fikstürlerle ayrıştırıcı testleri -----------------------------------------------------------
@pytest.mark.fixture_data
def test_bim_afis_grouping_and_full_url():
    g = parse_afis(fixture_text("bim_afis.html"))
    assert set(g) == {"1406", "1409", "1412"}
    tue = g["1406"]["pages"]
    assert tue[0]["url"].endswith("/aaaa1111.jpg") and tue[0]["verified"] is True       # indirme bağlantısı
    assert tue[1]["url"].endswith("/bbbb2222.jpg") and tue[1]["verified"] is False      # k_ öneki kaldırıldı (türetilmiş)
    assert tue[1]["thumb_url"].endswith("/k_bbbb2222.jpg")                               # data-src okundu, data: atlandı
    assert g["1412"]["column"] == "indirim" and g["1406"]["column"] == "aktuel"
    assert all("bim-logo" not in p["url"] for grp in g.values() for p in grp["pages"])


@pytest.mark.fixture_data
def test_attach_afis_links_to_product_catalog_or_creates_poster_catalog():
    cats = [CatalogData(id="bim-1681", title="13 Ekim Salı Aktüel", kind="aktuel", official_url="x")]
    attach_afis(cats, parse_afis(fixture_text("bim_afis.html")), date(2026, 10, 13))
    by = {c.id: c for c in cats}
    assert len(by["bim-1681"].pages) == 2 and by["bim-1681"].cover_url.endswith("k_aaaa1111.jpg")
    assert by["bim-afis-1409"].valid_from == date(2026, 10, 16)
    assert by["bim-afis-1412"].kind == "discount_list" and by["bim-afis-1412"].valid_to == date(2026, 10, 16)


@pytest.mark.fixture_data
def test_product_images_from_listing_cards():
    bim_html = """<div class="product"><a href="/aktuel-urunler/x-1-1/aktuel.aspx"><img data-src="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1_x.jpg"></a>
      <h2 class="subTitle">Marka</h2><h2 class="title">Ürün</h2><div class="quantify">10,<span>00</span> ₺</div></div>"""
    offers, _ = parse_products(bim_html, "https://www.bim.com.tr/x", "bim-1")
    assert offers[0].image_url == "https://cdn1.bim.com.tr/uploads/aktuel-urunler/1_x.jpg"
    sok_html = """<div><a href="/milka-oreo-300-g-p-7196"><img alt="Milka Oreo 300 g"
      src="https://images.ceptesok.com/cdn-cgi/image/width=600/product-assets/sub-folder/abc.png"></a><span>199,00₺</span></div>"""
    offers, _ = parse_discount_list(sok_html)
    assert offers[0].image_url.endswith("/product-assets/sub-folder/abc.png")
    payload = {"successful": True, "data": {"searchInfo": {"pageCount": 1, "storeProductInfos": [
        {"name": "Erikli Su 5 L", "shownPrice": 4990, "regularPrice": 7495, "prettyName": "erikli-su-5-l-p-7b04fb",
         "images": [{"urls": {"PRODUCT_LIST": "https://images.migrosone.com/a-105x105.jpg",
                              "PRODUCT_DETAIL": "https://images.migrosone.com/a.jpg"}}]}]}}}
    offers, _, _ = parse_search_json(payload)
    assert (offers[0].image_url, offers[0].image_large_url) == ("https://images.migrosone.com/a-105x105.jpg",
                                                                 "https://images.migrosone.com/a.jpg")


@pytest.mark.fixture_data
def test_sok_campaign_banners():
    html = """<a href="/1-alana-1-bedava-prm-df680613-3f29-45ab-9e60-2145a21f961a"><img
      src="https://images.ceptesok.com/cdn-cgi/image/width=512/promotion-assets/sub-folder/x.jpg">1 ALANA 1 BEDAVA</a>
      <a href="/kampanyalar/4">Tümü</a>"""
    pages = parse_campaign_banners(html)
    assert len(pages) == 1 and pages[0]["caption"] == "1 ALANA 1 BEDAVA" and "promotion-assets" in pages[0]["url"]


# --- GERÇEK anlık görüntü (13.10.2026) ------------------------------------------------------------------
@pytest.fixture
def loaded13(sf):
    import_snapshot(sf, SnapshotSource(SNAPSHOT).all())
    import_snapshot(sf, SnapshotSource(SNAPSHOT_13).all())
    return sf


@pytest.mark.real_data
def test_real_catalog_pages_and_images(loaded13):
    with loaded13() as s:
        feed = build_feed(s, today=date(2026, 10, 13))
    cats = {c["id"]: c for c in feed["catalogs"]}
    assert len(cats["bim-1681"]["pages"]) == 3 and cats["bim-1681"]["cover_url"].startswith("https://cdn1.bim.com.tr/uploads/afisler/k_")
    assert cats["bim-1681"]["pages"][0]["verified"] is True
    assert cats["bim-afis-1413"]["valid_from"] == "2026-10-20" and cats["bim-afis-1413"]["offer_count"] == 0
    assert cats["bim-afis-1198"]["valid_to"] == "2026-12-31"
    assert len(cats["sok-kampanya-gorselleri"]["pages"]) == 55
    assert cats["migros-migroskop-dijital-908"]["valid_to"] == "2026-10-21"
    migros = [o for o in feed["offers"] if o["store_id"] == "migros"]
    assert all(o["image_url"].startswith("https://images.migrosone.com/") and "-105x105" not in o["image_large_url"]
               for o in migros)
    # Görseli olmayan ürüne başka ürünün görseli verilmez
    assert all(o["image_url"] is None for o in feed["offers"] if o["store_id"] in ("a101", "bim", "sok"))
    assert feed["monetization"]["ads"]["active"] is False and feed["monetization"]["affiliate"]["active"] is False


@pytest.mark.real_data
def test_image_policy_link_only_hides_images(tmp_path, monkeypatch):
    monkeypatch.setenv("IMAGE_POLICY_MIGROS", "link_only")
    monkeypatch.setenv("IMAGE_POLICY_BIM", "link_only")
    sf = make_session_factory(f"sqlite:///{tmp_path}/p.db")
    import_snapshot(sf, SnapshotSource(SNAPSHOT_13).all())
    with sf() as s:
        feed = build_feed(s, today=date(2026, 10, 13))
    assert all(o["image_url"] is None for o in feed["offers"] if o["store_id"] == "migros")
    cats = {c["id"]: c for c in feed["catalogs"]}
    assert cats["bim-1681"]["pages"] == [] and cats["bim-1681"]["cover_url"] is None
    assert len(cats["sok-kampanya-gorselleri"]["pages"]) == 55   # ŞOK politikası değişmedi




def test_auto_migrate_adds_missing_columns(tmp_path):
    url = f"sqlite:///{tmp_path}/old.db"
    eng = create_engine(url)
    with eng.begin() as conn:  # 0.2.0 öncesi şema: kapak/sayfa sütunları yok
        conn.execute(text("CREATE TABLE catalogs (id VARCHAR(64) PRIMARY KEY, store_id VARCHAR(32), title VARCHAR(256), "
                          "kind VARCHAR(24), valid_from DATE, valid_to DATE, official_url VARCHAR(512), "
                          "source_method VARCHAR(48), observed_at DATETIME, data_mode VARCHAR(8))"))
    make_session_factory(url)
    cols = {c["name"] for c in inspect(create_engine(url)).get_columns("catalogs")}
    assert {"cover_url", "pages_json"} <= cols
