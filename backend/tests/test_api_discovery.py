"""JSON uç noktası yakalama (ağ trafiği) ve salt okunur API."""
from datetime import date, datetime, timezone

import pytest

from firsat360.connectors.discovery import Found, StoreSource, json_image_urls, merge_rendered

NOW = datetime(2026, 10, 21, 6, tzinfo=timezone.utc)


def test_json_image_urls_prefers_large_variants():
    assert json_image_urls({"a": [{"thumb": "https://x.com/k.jpg", "large": "https://x.com/b.jpg"}]}) == \
        ["https://x.com/b.jpg", "https://x.com/k.jpg"]


def test_json_interception_prefers_clean_endpoint_images():
    src = StoreSource("a101", "A101", ["https://www.a101.com.tr/afisler"], hosts=("a101.com.tr",))
    resp = [{"type": "json", "url": "https://www.a101.com.tr/api/posters", "status": 200,
             "images": json_image_urls({"posters": [
                 {"thumb": "https://cdn2.a101.com.tr/afis/k_1.jpg", "original": "https://cdn2.a101.com.tr/afis/1.jpg"},
                 {"original": "https://cdn2.a101.com.tr/logo.png"}, {"original": "https://evil.com/afis/x.jpg"}]})}]
    f = merge_rendered(Found(), "<html></html>", resp, src.urls[0], src)
    assert f.images[0] == "https://cdn2.a101.com.tr/afis/1.jpg"
    assert not any("logo" in u or "evil" in u for u in f.images)


def test_readonly_api(tmp_path):
    pytest.importorskip("fastapi")
    from fastapi.testclient import TestClient
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker
    from firsat360.api import create_app
    from firsat360.db import Base, Catalog, Store
    e = create_engine(f"sqlite:///{tmp_path}/a.db")
    Base.metadata.create_all(e)
    sf = sessionmaker(e)
    with sf() as s:
        s.add(Store(id="bim", name="BİM", official_url="u", catalog_url="u"))
        s.add(Catalog(id="bim-1", store_id="bim", title="Aktüel", kind="aktuel", official_url="u",
                      source_method="live_html", observed_at=NOW, data_mode="real", valid_from=date(2026, 10, 21),
                      pages_json='[{"page": 1, "url": "https://cdn1.bim.com.tr/p1.jpg"}]'))
        s.add(Catalog(id="bim-old", store_id="bim", title="Eski", kind="aktuel", official_url="u",
                      source_method="live_html", observed_at=NOW, data_mode="real", valid_to=date(2020, 1, 1)))
        s.commit()
    c = TestClient(create_app(sf))
    assert c.get("/health").json()["ok"]
    body = c.get("/catalogs", params={"store": "bim"}).json()
    assert [x["id"] for x in body["catalogs"]] == ["bim-1"] and body["catalogs"][0]["pages"][0]["url"].endswith("p1.jpg")
    assert c.get("/catalogs/yok").status_code == 404
    assert c.post("/catalogs").status_code == 405  # yazma yok


def test_magento_product_thumbnails_are_not_brochure_pages():
    from firsat360.connectors.discovery import scan_html
    src = StoreSource("rossmann", "R", ["https://www.rossmann.com.tr/kataloglar"], hosts=("rossmann.com.tr",))
    html = """<img src="https://cdn.rossmann.com.tr/mnpadding/700/700/FFFFFF/media/catalog/product/6/6/660f.jpeg">
      <img src="https://cdn.rossmann.com.tr/media/wysiwyg/katalog/ekim-katalog-sayfa1.jpg">"""
    f = scan_html(html, src.urls[0], src)
    assert f.images == ["https://cdn.rossmann.com.tr/media/wysiwyg/katalog/ekim-katalog-sayfa1.jpg"]


def test_stale_catalog_hidden_from_feed():
    from datetime import timedelta
    from firsat360.db import Catalog, Store
    from firsat360.feed import is_stale
    st = Store(id="migros", name="M", official_url="u", catalog_url="u", last_success_at=NOW)
    old = Catalog(id="a", store_id="migros", title="t", kind="flipbook", official_url="u", source_method="x",
                  observed_at=NOW, data_mode="real", last_checked_at=NOW - timedelta(days=8))
    fresh = Catalog(id="b", store_id="migros", title="t", kind="aktuel", official_url="u", source_method="x",
                    observed_at=NOW, data_mode="real", last_checked_at=NOW)
    assert is_stale(old, st, set()) and not is_stale(fresh, st, set()) and not is_stale(old, st, {"a"})


@pytest.mark.parametrize("url", ["http://127.0.0.1/x", "http://localhost:8080/", "http://10.0.0.5/a", "file:///etc/passwd",
                                 "http://169.254.169.254/latest/meta-data", "http://[::1]/", "http://intranet/"])
def test_ssrf_guard_blocks_private_targets(url):
    from firsat360.connectors.base import ConnectorError, check_public_url
    with pytest.raises(ConnectorError):
        check_public_url(url)


def test_ssrf_guard_allows_public():
    from firsat360.connectors.base import check_public_url
    check_public_url("https://www.bim.com.tr/x")
    check_public_url("https://8.8.8.8/")
