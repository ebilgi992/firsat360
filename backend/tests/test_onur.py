"""Onur Market insertleri: kampanya sayfası, PDF + kapak eşleme, PDF doğrulama, süresi dolanlar."""
from datetime import date, datetime, timezone

import httpx

from firsat360.connectors.base import PoliteClient
from firsat360.connectors.onur import OnurConnector, campaign_urls, parse_campaign

NOW = datetime(2026, 10, 21, 6, tzinfo=timezone.utc)
REGION = """<a href="/tr/kampanyalar/insertlerimiz?region=istanbul&amp;campaign=istanbul-genel-insert">Genel</a>"""
CAMPAIGN = """<html><body>
<div class="insert"><div class="txt">Onur'da Çok Al Az Öde 13-26 Ekim 2026</div>
  <img src="/pics/inserts/944_onurda-cok-al-az-ode.jpg" alt="Onur'da Çok Al Az Öde">
  <a href="/download/944_onurda-cok-al-az-ode..pdf">İndir</a></div>
<div class="insert"><div class="txt">Kaçırılmaz Fırsatlar 1-12 Ekim 2026</div>
  <img src="/pics/inserts/927_kacirilmaz-firsatlar-onurda.jpg" alt="Kaçırılmaz Fırsatlar">
  <a href="/download/927_kacirilmaz-firsatlar-onurda..pdf">İndir</a></div>
</body></html>"""


def test_campaign_urls_from_region_page_and_default():
    assert campaign_urls(REGION, "istanbul") == [
        "https://kurumsal.onurmarket.com/tr/kampanyalar/insertlerimiz?region=istanbul&campaign=istanbul-genel-insert"]
    assert campaign_urls("<html></html>", "bursa")[0].endswith("campaign=bursa-genel-insert")


def test_parse_campaign_pairs_pdf_cover_and_drops_expired():
    cats = parse_campaign(CAMPAIGN, "u", "istanbul", date(2026, 10, 21))
    assert len(cats) == 1  # 1-12 Ekim insert'i süresi dolmuş
    c = cats[0]
    assert c.pdf_url == "https://kurumsal.onurmarket.com/download/944_onurda-cok-al-az-ode..pdf"
    assert c.cover_url.endswith("/pics/inserts/944_onurda-cok-al-az-ode.jpg")
    assert c.valid_from == date(2026, 10, 13) and c.valid_to == date(2026, 10, 26) and c.kind == "pdf"


def test_connector_verifies_pdf_and_dedupes_regions():
    def handler(req):
        u = str(req.url)
        if req.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\\nDisallow:")
        if req.method == "HEAD":
            return httpx.Response(200, headers={"content-type": "application/pdf"})
        if "campaign=" in u:
            return httpx.Response(200, text=CAMPAIGN)
        return httpx.Response(200, text=REGION)
    client = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(handler), sleep=lambda s: None)
    res = OnurConnector().fetch(client, NOW)
    assert len([c for c in res.catalogs if c.pdf_url]) == 1  # 3 bölgede aynı PDF → tek katalog

    def html_pdf(req):
        if req.url.path == "/robots.txt":
            return httpx.Response(200, text="")
        if req.method == "HEAD":
            return httpx.Response(200, headers={"content-type": "text/html"})  # PDF yerine hata sayfası
        return httpx.Response(200, text=CAMPAIGN if "campaign=" in str(req.url) else REGION)
    client2 = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(html_pdf), sleep=lambda s: None)
    res2 = OnurConnector().fetch(client2, NOW)
    assert all(c.pdf_url is None and c.pages for c in res2.catalogs)  # PDF sayılmadı, yalnızca kapak görseli
