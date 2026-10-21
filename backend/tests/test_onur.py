"""Onur Market insertleri: kampanya sayfası, PDF + kapak eşleme, PDF doğrulama, süresi dolanlar."""
from datetime import date, datetime, timezone

import httpx

from firsat360.connectors.base import PoliteClient
from firsat360.connectors.onur import OnurConnector, campaign_urls, parse_campaign

NOW = datetime(2026, 10, 21, 6, tzinfo=timezone.utc)
REGION = """<a href="/tr/kampanyalar/insertlerimiz?region=istanbul&amp;campaign=istanbul-genel-insert">Genel</a>"""
# 21.10.2026 resmî sayfadan alınan yapı (base href + göreli adresler, Güncel/Geçmiş bölümleri)
CAMPAIGN = """<html><head><base href="https://kurumsal.onurmarket.com/" /></head><body>
  <a href="download/944_onurda-cok-al-az-ode..pdf" target="_blank">
  <img src="pics/inserts/944_onurda-cok-al-az-ode.jpg" width="160" height="240" alt="" class="img" /></a>
  <div class="info"><span class="txt a">GÜNCEL INSERT</span>
  <span class="txt b">İstanbul</span>
  <span class="txt c">13-26 Ekim 2026</span>
  <span class="txt d">Onur'da Çok Al Az Öde </span></div>
  <div class="link"><a href="download/944_onurda-cok-al-az-ode..pdf" onclick="get('_countInsert.php?X')">İndir</a></div>
<h3>Geçmiş İnsert</h3>
  <a href="download/927_kacirilmaz-firsatlar-onurda..pdf"><img src="pics/inserts/927_kacirilmaz-firsatlar-onurda.jpg"></a>
</body></html>"""


def test_campaign_urls_from_region_page_and_default():
    assert campaign_urls(REGION, "istanbul") == [
        "https://kurumsal.onurmarket.com/tr/kampanyalar/insertlerimiz?region=istanbul&campaign=istanbul-genel-insert"]
    assert campaign_urls("<html></html>", "bursa")[0].endswith("campaign=bursa-genel-insert")


def test_parse_campaign_pairs_pdf_cover_and_drops_expired():
    cats = parse_campaign(CAMPAIGN, "u", "istanbul", date(2026, 10, 21))
    assert len(cats) == 1  # "Geçmiş İnsert" bölümündeki alınmaz
    c = cats[0]
    assert c.pdf_url == "https://kurumsal.onurmarket.com/download/944_onurda-cok-al-az-ode..pdf"
    assert c.cover_url.endswith("/pics/inserts/944_onurda-cok-al-az-ode.jpg")
    assert c.valid_from == date(2026, 10, 13) and c.valid_to == date(2026, 10, 26) and c.kind == "pdf"
    assert c.title.startswith("Onur'da Çok Al Az Öde")


def test_connector_verifies_pdf_and_dedupes_regions():
    def handler(req):
        u = str(req.url)
        if req.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\\nDisallow:")
        if u.endswith(".pdf"):
            return httpx.Response(200, headers={"content-type": "application/pdf"}, content=b"%PDF-1.7 ...")
        if "campaign=" in u:
            return httpx.Response(200, text=CAMPAIGN)
        return httpx.Response(200, text=REGION)
    client = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(handler), sleep=lambda s: None)
    res = OnurConnector().fetch(client, NOW)
    assert len([c for c in res.catalogs if c.pdf_url]) == 1  # 3 bölgede aynı PDF → tek katalog

    def html_pdf(req):
        if req.url.path == "/robots.txt":
            return httpx.Response(200, text="")
        if str(req.url).endswith(".pdf"):  # "PDF" adresi hata sayfası döndürüyor
            return httpx.Response(200, headers={"content-type": "application/pdf"}, content=b"<html>Hata</html>")
        return httpx.Response(200, text=CAMPAIGN if "campaign=" in str(req.url) else REGION)
    client2 = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(html_pdf), sleep=lambda s: None)
    res2 = OnurConnector().fetch(client2, NOW)
    assert all(c.pdf_url is None and c.pages for c in res2.catalogs)  # PDF sayılmadı, yalnızca kapak görseli


def test_sniff_detects_real_type():
    from firsat360.connectors.base import sniff
    assert sniff(b"\xff\xd8\xff\xe0abc") == "jpeg" and sniff(b"%PDF-1.4") == "pdf"
    assert sniff(b"  <!DOCTYPE html>") == "html" and sniff(b"RIFF1234WEBPVP8 ") == "webp"
