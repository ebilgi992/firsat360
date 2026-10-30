"""Günlük senkronizasyon, duyuru tekilleştirme, push, koşullu indirme, budama, log sınırları ve Günün İndirimleri.

Bu testlerdeki mağaza sayfaları SENTETİK fikstürlerdir (ağ erişimi yok). Push testi gerçek bir yerel HTTP sunucusuna
gerçek HTTP isteği gönderir (ntfy.sh'e değil).
"""
from __future__ import annotations

import json
import os
import threading
import time
from datetime import date, datetime, timedelta, timezone
from decimal import Decimal
from http.server import BaseHTTPRequestHandler, HTTPServer

import httpx
import pytest
from sqlalchemy import select

from firsat360.connectors.base import CatalogData, ConnectorResult, OfferData, PoliteClient
from firsat360.connectors.extra import (parse_a101_afis, parse_flipbook_index, parse_hakmar, parse_migroskop,
                                        parse_publication, parse_tk_campaign)
from firsat360.db import AnnouncedCatalog, Announcement, Offer, PriceObservation, Store, SyncRun
from firsat360.deals import daily_deals
from firsat360.httpcache import ConditionalCache
from firsat360.logs import cleanup, redact
from firsat360.pipeline import ensure_stores, ingest
from firsat360.productpages import page_row, verify_product_pages
from firsat360.sync import detect_new_catalogs, prune, push_ntfy, run_daily

from .conftest import make_transport

pytestmark = pytest.mark.fixture_data
UTC = timezone.utc
# 14.10.2026 06:00 TR = 03:00 UTC (bildirim saati 09:00'dan önce)
NOW = datetime(2026, 10, 14, 3, 0, tzinfo=UTC)


def _result(store="bim", cats=(("bim-c1", "14 Ekim Çarşamba Aktüel"),), obs=NOW, offers=True, valid_to=None):
    res = ConnectorResult(store_id=store, method="live_html", observed_at=obs)
    for cid, title in cats:
        res.catalogs.append(CatalogData(id=cid, title=title, kind="aktuel", official_url="https://x.test/" + cid,
                                        valid_from=date(2026, 10, 14), valid_to=valid_to))
        if offers:
            res.offers.append(OfferData(name=f"Ürün {cid}", price=Decimal("100"), price_text="100,00 ₺",
                                        source_url="https://x.test", catalog_id=cid,
                                        store_declared_old_price=Decimal("150"), image_url="https://cdn1.bim.com.tr/a.jpg",
                                        image_status="listing", product_url=f"https://www.bim.com.tr/{cid}"))
    return res


def _seed(sf, *results):
    with sf() as s:
        ensure_stores(s)
        for r in results:
            ingest(s, r, "schedule")
        s.commit()


def _daily(sf, settings, now=NOW, **kw):
    return run_daily(sf, {}, settings, live=False, snapshot_root=settings_root_none(), now=now, sleep=lambda s: None,
                     verify_pages=False, **kw)


def settings_root_none():
    from pathlib import Path
    return Path("/nonexistent-firsat360-snapshots")


# ------------------------------------------------------------------ duyuru
def test_first_run_is_silent_then_new_catalog_announced_once(sf, settings):
    _seed(sf, _result(cats=(("bim-old", "13 Ekim Salı Aktüel"),), obs=NOW - timedelta(days=1)))
    r1 = _daily(sf, settings)
    assert r1["bootstrap"] is True and r1["announcement"] is None and r1["silently_marked"] == 1
    # Ertesi gün yeni katalog yayımlandı
    later = NOW + timedelta(hours=2)
    _seed(sf, _result(cats=(("bim-new", "16 Ekim Cuma Aktüel"),), obs=later))
    r2 = _daily(sf, settings, now=later)
    assert r2["new_catalogs"] == ["bim-new"] and r2["announcement"]
    feed = json.loads(open(settings.feed_export_path, encoding="utf-8").read())
    ann = feed["announcements"][0]
    assert ann["title"] == "🛒 Günün katalogları yayınlandı!"
    assert ann["body"].startswith("Yeni aktüel ürünleri, fiyatları ve güncel broşürleri keşfetmek için dokun.")
    assert ann["catalog_ids"] == ["bim-new"] and ann["store_ids"] == ["bim"]
    # 05:00 UTC = 08:00 TR → teslim 09:00 TR (06:00 UTC)
    assert ann["deliver_at"].startswith("2026-10-14T06:00")
    # Aynı gün tekrar: yeni duyuru yok
    r3 = _daily(sf, settings, now=later + timedelta(hours=1))
    assert r3["new_catalogs"] == [] and r3["announcement"] is None
    with sf() as s:
        assert len(s.scalars(select(Announcement)).all()) == 1
        assert s.get(AnnouncedCatalog, "bim-new").announcement_id == r2["announcement"]
        assert s.get(AnnouncedCatalog, "bim-old").announcement_id is None  # sessiz


def test_multiple_new_catalogs_are_combined_into_one(sf, settings):
    _seed(sf, _result(cats=(("bim-x", "13 Ekim"),), obs=NOW - timedelta(days=1)))
    _daily(sf, settings)  # ilk kurulum
    t = NOW + timedelta(hours=1)
    _seed(sf, _result("bim", (("bim-a", "16 Ekim Cuma"),), obs=t), _result("sok", (("sok-a", "Haftanın Fırsatları"),), obs=t),
          _result("a101", (("a101-a", "Aldın Aldın"),), obs=t))
    r = _daily(sf, settings, now=t)
    assert sorted(r["new_catalogs"]) == ["a101-a", "bim-a", "sok-a"]
    with sf() as s:
        anns = s.scalars(select(Announcement)).all()
        assert len(anns) == 1 and json.loads(anns[0].store_ids) == ["a101", "bim", "sok"]
        assert "A101, BİM, ŞOK · 3 yeni katalog" in anns[0].body


def test_old_expired_and_empty_catalogs_not_announced(sf, settings):
    _seed(sf, _result(cats=(("bim-x", "x"),), obs=NOW - timedelta(days=1)))
    _daily(sf, settings)
    t = NOW + timedelta(hours=1)
    _seed(sf,
          _result("bim", (("bim-stale", "eski"),), obs=t - timedelta(days=5)),          # 5 gün önce ilk görüldü
          _result("sok", (("sok-exp", "bitti"),), obs=t, valid_to=date(2026, 10, 13)),  # süresi bitmiş
          _result("a101", (("a101-empty", "boş"),), obs=t, offers=False))               # içerik yok
    r = _daily(sf, settings, now=t)
    assert r["new_catalogs"] == [] and r["announcement"] is None
    with sf() as s:
        assert s.get(AnnouncedCatalog, "bim-stale").announcement_id is None
        assert s.get(AnnouncedCatalog, "sok-exp") is None and s.get(AnnouncedCatalog, "a101-empty") is None


def test_first_seen_is_kept_when_catalog_is_read_again(sf, settings):
    _seed(sf, _result(cats=(("bim-c", "t"),), obs=NOW))
    _seed(sf, _result(cats=(("bim-c", "t"),), obs=NOW + timedelta(days=1)))
    with sf() as s:
        from firsat360.db import Catalog
        c = s.get(Catalog, "bim-c")
        assert c.first_seen_at.replace(tzinfo=UTC) == NOW
        assert c.last_checked_at.replace(tzinfo=UTC) == NOW + timedelta(days=1)


# ------------------------------------------------------------------ kaynak hatası: veri korunur
def test_source_failure_keeps_verified_data(sf, settings, no_sleep, monkeypatch):
    from firsat360 import pipeline
    from firsat360.connectors import live_connectors
    # Bağlayıcı gözlem zamanı (pipeline.utcnow) sabitlenir: fikstürdeki BİM sekmeleri (13-14 Ekim) artık bitiş
    # tarihi taşıdığından (tek gün → +6 gün) duvar saatine göre süresi dolmuş sayılmasın.
    monkeypatch.setattr(pipeline, "utcnow", lambda: NOW)
    conns = {"bim": live_connectors()["bim"]}
    settings.enabled_live_stores = ("bim",)
    r1 = run_daily(sf, conns, settings, transport=make_transport(), now=NOW, snapshot_root=settings_root_none(),
                   verify_pages=False)
    with sf() as s:
        before = len(s.scalars(select(Offer).where(Offer.store_id == "bim", Offer.published.is_(True))).all())
        ok_at = s.get(Store, "bim").last_success_at
    assert before > 0 and r1["ok_stores"] >= 1

    def down(request):
        raise httpx.ConnectError("bağlantı yok", request=request)
    t = make_transport({"https://www.bim.com.tr/categories/100/aktuel-urunler.aspx": down})
    r2 = run_daily(sf, conns, settings, transport=t, now=NOW + timedelta(days=1),
                   snapshot_root=settings_root_none(), verify_pages=False)
    with sf() as s:
        st = s.get(Store, "bim")
        after = len(s.scalars(select(Offer).where(Offer.store_id == "bim", Offer.published.is_(True))).all())
        assert st.status == "error" and st.last_error and st.last_success_at == ok_at
    assert after == before  # eski doğrulanmış veri korunur
    status = json.loads(open(r2["status"], encoding="utf-8").read())
    bim = next(x for x in status["stores"] if x["id"] == "bim")
    assert bim["status"] == "error" and bim["last_success_at"]
    assert len(status["runs"]) == 2  # her çalıştırma izlenebilir


# ------------------------------------------------------------------ push (gerçek yerel HTTP sunucusu)
class _Hook(BaseHTTPRequestHandler):
    received: list = []
    status = 200

    def do_POST(self):  # noqa: N802
        body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
        _Hook.received.append({"auth": self.headers.get("Authorization"), "json": json.loads(body)})
        self.send_response(_Hook.status)
        self.end_headers()
        self.wfile.write(b"{}")

    def log_message(self, *a):
        pass


@pytest.fixture
def push_server():
    _Hook.received = []
    _Hook.status = 200
    srv = HTTPServer(("127.0.0.1", 0), _Hook)
    th = threading.Thread(target=srv.serve_forever, daemon=True)
    th.start()
    yield f"http://127.0.0.1:{srv.server_address[1]}"
    srv.shutdown()


def test_push_is_sent_once_scheduled_for_0900(sf, settings, push_server, caplog):
    settings.ntfy_url, settings.ntfy_topic, settings.ntfy_token = push_server, "firsat360-test", "tk_supersecret123456"
    _seed(sf, _result(cats=(("bim-x", "x"),), obs=NOW - timedelta(days=1)))
    _daily(sf, settings)
    t = NOW + timedelta(hours=1)  # 07:00 TR
    _seed(sf, _result("bim", (("bim-new", "16 Ekim"),), obs=t))
    r = _daily(sf, settings, now=t)
    assert r["push"][0]["status"] == "sent"
    topics = sorted(x["json"]["topic"] for x in _Hook.received)
    assert topics == ["firsat360-test", "firsat360-test-bim"]
    msg = _Hook.received[0]["json"]
    assert msg["title"] == "🛒 Günün katalogları yayınlandı!"
    assert msg["click"] == "firsat360://catalog/bim-new"
    assert int(msg["delay"]) == int(datetime(2026, 10, 14, 6, 0, tzinfo=UTC).timestamp())  # 09:00 TR
    assert _Hook.received[0]["auth"] == "Bearer tk_supersecret123456"
    # Aynı katalog için ikinci gönderim yok
    _Hook.received = []
    _daily(sf, settings, now=t + timedelta(hours=3))
    assert _Hook.received == []
    assert "tk_supersecret123456" not in caplog.text


def test_push_failure_is_retried_at_most_three_times(sf, settings, push_server):
    settings.ntfy_url, settings.ntfy_topic = push_server, "f360"
    _Hook.status = 503
    _seed(sf, _result(cats=(("bim-x", "x"),), obs=NOW - timedelta(days=1)))
    _daily(sf, settings)
    t = NOW + timedelta(hours=1)
    _seed(sf, _result("bim", (("bim-new", "16 Ekim"),), obs=t))
    for i in range(5):
        _daily(sf, settings, now=t + timedelta(minutes=i))
    with sf() as s:
        a = s.scalars(select(Announcement)).one()
        assert a.push_status == "failed" and a.push_attempts == 3


def test_push_after_notify_time_is_immediate(sf, settings):
    settings.ntfy_url, settings.ntfy_topic = "https://push.example.test", "f360"
    sent = []

    def h(req):
        sent.append(json.loads(req.content))
        return httpx.Response(200)
    with sf() as s:
        ensure_stores(s)
        a = Announcement(id="a1", created_at=NOW, title="t", body="b", catalog_ids='["c1","c2"]',
                         store_ids='["bim","sok"]', deliver_at=NOW - timedelta(minutes=5))
        status, _ = push_ntfy(settings, a, NOW, transport=httpx.MockTransport(h), sleep=lambda s: None)
    assert status == "sent" and all("delay" not in m for m in sent)
    assert sent[0]["click"].startswith("firsat360://catalogs/new")


# ------------------------------------------------------------------ koşullu indirme (ETag / Last-Modified)
def test_conditional_get_does_not_redownload_unchanged(sf, tmp_path):
    calls = []

    def handler(request):
        if request.url.path == "/robots.txt":
            return httpx.Response(404)
        calls.append(dict(request.headers))
        if request.headers.get("if-none-match") == '"v1"':
            return httpx.Response(304, headers={"etag": '"v1"'})
        return httpx.Response(200, text="<html>içerik</html>", headers={"etag": '"v1"'})
    cache = ConditionalCache(sf, tmp_path / "c")
    client = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(handler), sleep=lambda s: None,
                          cache=cache)
    a = client.get("https://shop.test/katalog").text
    b = client.get("https://shop.test/katalog").text
    assert a == b == "<html>içerik</html>"
    assert "if-none-match" not in calls[0] and calls[1]["if-none-match"] == '"v1"'
    assert cache.hits == 1


def test_http_cache_size_limit_and_partial_cleanup(sf, tmp_path):
    d = tmp_path / "c"
    d.mkdir()
    (d / "yarim.part").write_bytes(b"x" * 100)
    cache = ConditionalCache(sf, d, max_bytes=3000)
    assert not (d / "yarim.part").exists()
    for i in range(10):
        cache.store(f"https://s.test/{i}", b"y" * 1000, f'"e{i}"', None)
        time.sleep(0.01)
    assert cache.size_bytes() <= 3000


# ------------------------------------------------------------------ budama
def test_prune_keeps_latest_observation(sf, settings):
    _seed(sf, _result(cats=(("bim-c", "t"),), obs=NOW - timedelta(days=400)))
    _seed(sf, _result(cats=(("bim-c", "t"),), obs=NOW - timedelta(days=300)))
    with sf() as s:
        o = s.scalars(select(Offer)).first()
        o.price = Decimal("90")
        s.add(PriceObservation(offer_id=o.id, price=Decimal("90"), observed_at=NOW - timedelta(days=200),
                               source_method="t"))
        s.add(SyncRun(started_at=NOW - timedelta(days=100)))
        s.commit()
        out = prune(s, settings, NOW)
        s.commit()
        left = s.scalars(select(PriceObservation)).all()
    assert len(left) == 1 and out["sync_runs"] == 1


# ------------------------------------------------------------------ günlük log sınırları
def test_log_cleanup_age_and_size(tmp_path):
    d = tmp_path / "logs"
    d.mkdir()
    old = d / "firsat360.log.4"
    old.write_text("eski")
    os.utime(old, (time.time() - 8 * 86400, time.time() - 8 * 86400))
    for i in range(3):
        f = d / f"firsat360.log.{i}"
        f.write_bytes(b"x" * 3_000_000)
        os.utime(f, (time.time() - (3 - i) * 60, time.time() - (3 - i) * 60))
    removed = cleanup(d, max_bytes=5 * 1024 * 1024, keep_days=7)
    assert "firsat360.log.4" in removed
    assert sum(f.stat().st_size for f in d.iterdir()) <= 5 * 1024 * 1024
    assert (d / "firsat360.log.2").exists()  # en yeni korunur


def test_cleanup_never_raises(tmp_path):
    assert cleanup(tmp_path / "yok") == []


def test_redact_secrets():
    t = redact("Authorization: Bearer tk_abcdefgh12345678 token=xyz postgresql+psycopg://u:parola@h/db")
    assert "tk_abcdefgh12345678" not in t and "xyz" not in t and "parola" not in t


# ------------------------------------------------------------------ Günün İndirimleri
def test_daily_deals_rules(sf):
    with sf() as s:
        ensure_stores(s)
        res = ConnectorResult(store_id="bim", method="live_html", observed_at=NOW - timedelta(days=2))
        res.catalogs.append(CatalogData(id="bim-c", title="t", kind="discount_list", official_url="u"))
        for i in range(10):
            res.offers.append(OfferData(name=f"İndirimli {i}", price=Decimal("50"), price_text="50 ₺", source_url="u",
                                        catalog_id="bim-c", store_declared_old_price=Decimal("100"),
                                        image_url="https://cdn1.bim.com.tr/i.jpg", image_status="listing"))
        res.offers.append(OfferData(name="Az indirim", price=Decimal("95"), price_text="95", source_url="u",
                                    catalog_id="bim-c", store_declared_old_price=Decimal("100"),
                                    image_url="https://cdn1.bim.com.tr/i.jpg", image_status="listing"))
        res.offers.append(OfferData(name="Görselsiz", price=Decimal("10"), price_text="10", source_url="u",
                                    catalog_id="bim-c", store_declared_old_price=Decimal("100")))
        ingest(s, res)
        sok = ConnectorResult(store_id="sok", method="live_html", observed_at=NOW)
        sok.catalogs.append(CatalogData(id="sok-c", title="t", kind="aktuel", official_url="u"))
        sok.offers.append(OfferData(name="Yeni aktüel", price=Decimal("300"), price_text="300", source_url="u",
                                    catalog_id="sok-c", image_url="https://images.ceptesok.com/x.jpg",
                                    image_status="verified"))
        ingest(s, sok)
        s.commit()
        d = daily_deals(s, date(2026, 10, 14), now=NOW)
        names = [s.get(Offer, i).name for i in d["offer_ids"]]
    assert "Az indirim" not in names and "Görselsiz" not in names
    assert "Yeni aktüel" in names
    assert sum(1 for n in names if n.startswith("İndirimli")) == 6  # mağaza başına üst sınır
    assert d["note"].startswith("Fiyatlar ve stok durumu")
    # Mağazalar sırayla: ilk ikisi farklı mağaza
    assert names[0] != names[1]


# ------------------------------------------------------------------ yeni otomatik bağlayıcılar (SENTETİK sayfalar)
TK_HTML = """<html><h1>Kat Kat Fırsat 13- 19 Ekim</h1><div class="list">
<div class="card"><a href="/urun/kuzey-balik-somon-500-g"><img src="/assets/images/urun/somon_1.png"></a>
<a href="/urun/kuzey-balik-somon-500-g">KUZEY BALIK DONDURULMUŞ SOMON 500 G</a><span>279,00 TL</span></div>
<div class="card"><a href="/urun/bolca-milfoy-1000-g"><img src="/assets/images/urun/yok.png"></a>
<a href="/urun/bolca-milfoy-1000-g">BOLCA DONUK MİLFÖY HAMURU 1000 G</a><span>74,90 TL</span></div>
</div></html>"""


def test_tkkoop_parser():
    cat, offers, rej = parse_tk_campaign(TK_HTML, "https://www.tkkoop.com.tr/haftalik-kampanyalar/kat-kat-firsat",
                                         date(2026, 10, 13))
    assert cat.valid_from == date(2026, 10, 13) and cat.valid_to == date(2026, 10, 19)
    assert len(offers) == 2 and offers[0].price == Decimal("279.00")
    assert offers[0].image_url == "https://www.tkkoop.com.tr/assets/images/urun/somon_1.png"
    assert offers[1].image_url is None  # yer tutucu görsel alınmaz
    assert offers[0].product_url.endswith("/urun/kuzey-balik-somon-500-g")


def test_hakmar_parser_groups_pages():
    base = "https://hakmar.com.tr/wp-content/uploads/2026/10/"
    html = "<html>" + "".join(f'<img src="{base}08-19-Ekim-2026-Genel-Insert-{i}-scaled.jpg">' for i in range(1, 5)) \
        + f'<img src="{base}logo.png"><img src="{base}tek-kampanya.jpg"></html>'
    cats = parse_hakmar(html, date(2026, 10, 13))
    assert len(cats) == 1
    c = cats[0]
    assert len(c.pages) == 4 and c.valid_from == date(2026, 10, 8) and c.valid_to == date(2026, 10, 19)
    assert "Genel Insert" in c.title


def test_migroskop_parser():
    html = """<html><body><h2>Migroskop Dijital</h2><p>Sayı 908</p><p>8 - 21 Ekim 2026</p>
    <a href="https://moneyclubkart.azureedge.net/mcfiles/insert/migros/908/insert.pdf?v=1">PDF</a>
    <a href="https://www.money.com.tr/katalog/908">KATALOĞU GÖR</a></body></html>"""
    c = parse_migroskop(html, date(2026, 10, 14))
    assert c.id == "migroskop-908" and c.valid_from == date(2026, 10, 8) and c.valid_to == date(2026, 10, 21)
    assert c.pdf_url.endswith("/908/insert.pdf?v=1") and c.kind == "pdf"


def test_migroskop_without_pdf_is_link_only():
    c = parse_migroskop("<html><body>Sayı 909 22 Ekim - 4 Kasım</body></html>", date(2026, 10, 20))
    assert c.pdf_url is None and c.kind == "flipbook" and c.valid_to == date(2026, 11, 4)


def test_a101_afis_parser():
    data = {"props": {"pageProps": {"posters": [
        {"title": "Haftanın Yıldızları 16-22 Ekim", "image": "https://cdn2.a101.com.tr/afis/hy-1.jpg"},
        {"title": "Haftanın Yıldızları 16-22 Ekim", "image": "https://cdn2.a101.com.tr/afis/hy-2.jpg"},
        {"title": "Logo", "image": "https://cdn2.a101.com.tr/logo.png"}]}}}
    html = f'<html><script id="__NEXT_DATA__" type="application/json">{json.dumps(data)}</script></html>'
    cats = parse_a101_afis(html, date(2026, 10, 14))
    assert len(cats) == 1 and len(cats[0].pages) == 2 and cats[0].valid_to == date(2026, 10, 22)


def test_flipbook_parsers():
    html = """<html><a href="https://heyzine.com/flip-book/f7d10044c6.html"><img src="https://img.test/k.png" alt="Haftalık Katalog"></a>
    <a href="/sepet">Sepet</a></html>"""
    cats = parse_flipbook_index(html, "https://www.bizimtoptan.com.tr/pages/katalog", "bizimtoptan", date(2026, 10, 14))
    assert len(cats) == 1 and cats[0].cover_url == "https://img.test/k.png" and cats[0].title == "Haftalık Katalog"
    pub = parse_publication('<html><head><meta property="og:title" content="Temizlik Broşürü 1 - 31 Ekim | Publitas">'
                            '<meta property="og:image" content="https://view.publitas.com/c.jpg"></head></html>',
                            "https://brosurler.metro-tr.com/kozmetik-deterjan/page/1", "metro", date(2026, 10, 14))
    assert pub.valid_to == date(2026, 10, 31) and pub.cover_url.endswith("c.jpg")


# ------------------------------------------------------------------ ürün sayfası görsel doğrulaması
def test_product_page_image_rules():
    img = "https://cdn1.bim.com.tr/uploads/aktuel-urunler/1692_buyuk_543X467_7_x.jpg"
    ok = page_row("bim", "https://www.bim.com.tr/p", f'<meta property="og:image" content="{img}">'
                  '<h1>AYÇİÇEK YAĞI 5 L</h1><main>445,00 ₺</main>')
    assert ok.image_url == img and ok.page_name.split("\n")[0] == "AYÇİÇEK YAĞI 5 L"
    banner = page_row("bim", "https://www.bim.com.tr/p", '<meta property="og:image" content="https://cdn1.bim.com.tr/x.jpg">')
    assert banner.image_url is None  # ürün görseli kalıbına uymayan (afiş/genel) görsel kabul edilmez
    foreign = page_row("bim", "https://www.bim.com.tr/p", '<meta property="og:image" content="https://baska.site/x.jpg">')
    assert foreign.image_url is None  # mağazanın kendi alan adı değil → kabul edilmez


def test_verify_product_pages_applies_verified_image(sf, settings):
    _seed(sf, _result(cats=(("bim-c", "t"),), obs=NOW))
    with sf() as s:
        o = s.scalars(select(Offer)).one()
        o.name = "Ayçiçek Yağı"
        o.variant = "5 L"
        s.commit()

    def handler(request):
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nDisallow:\n")
        return httpx.Response(200, text='<meta property="og:image" content="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1692_buyuk_543X467_1_dogru.jpg">'
                                        '<h1>AYÇİÇEK YAĞI 5 L</h1><main>100,00 ₺</main>')
    client = PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(handler), sleep=lambda s: None)
    out = verify_product_pages(sf, client, now=NOW, stores=("bim",))
    with sf() as s:
        o = s.scalars(select(Offer)).one()
        assert o.image_status == "verified" and o.image_url == "https://cdn1.bim.com.tr/uploads/aktuel-urunler/1692_buyuk_543X467_1_dogru.jpg"
    assert out["bim"]["pages"] == 1


# ------------------------------------------------------------------ flipbook kataloglarının sayfaları (SENTETİK)
def _client(handler):
    def h(request):
        if request.url.path == "/robots.txt":
            return httpx.Response(200, text="User-agent: *\nDisallow:\n")
        return handler(request)
    return PoliteClient("t", min_interval_s=0, transport=httpx.MockTransport(h), sleep=lambda s: None)


def test_publitas_all_pages_extracted():
    from firsat360.connectors.extra import PublitasConnector
    total = 6

    def handler(req):
        p = req.url.path
        if p == "/":
            return httpx.Response(302, headers={"location": "https://katalog.example.test/ekim-katalog/page/1"})
        if p.endswith("/spreads.json"):
            return httpx.Response(404)  # spreads.json yok → eski /page/N yolu
        n = int(p.rsplit("/", 1)[-1])
        n = min(n, total)  # son sayfadan sonrası son sayfayı döndürür
        return httpx.Response(200, text=f'<html><head><meta property="og:title" content="Ekim Kataloğu 1 - 31 Ekim | Publitas">'
                              f'<link rel="image_src" href="https://view.publitas.com/x/pages/p{n}.jpg">'
                              f'<meta property="og:image" content="https://view.publitas.com/screenshots?x=1"></head></html>')
    res = PublitasConnector("watsons", "https://katalog.example.test/").fetch(_client(handler), NOW)
    c = res.catalogs[0]
    assert len(c.pages) == total and c.pages[0]["url"].endswith("p1.jpg") and c.pages[-1]["url"].endswith("p6.jpg")
    assert c.kind == "aktuel" and c.cover_url.endswith("p1.jpg")  # ekran görüntüsü kapak olarak kullanılmaz
    assert c.valid_to == date(2026, 10, 31) and c.official_url.endswith("/ekim-katalog/page/1")


def test_fliphtml5_pages_only_verified():
    from firsat360.connectors.extra import FlipbookIndexConnector

    def handler(req):
        if req.url.host == "www.gratis.example":
            return httpx.Response(200, text='<a href="https://online.fliphtml5.com/gratiscom/vczv/"><img src="https://img.test/k.png" '
                                            'alt="Gratis Ekim Kataloğu"></a>')
        n = int(req.url.path.rsplit("/", 1)[-1].split(".")[0])
        if req.method == "HEAD" and n <= 4:
            return httpx.Response(200, headers={"content-type": "image/jpeg"})
        return httpx.Response(404)
    res = FlipbookIndexConnector("gratis", "https://www.gratis.example/gratis-katalog").fetch(_client(handler), NOW)
    c = res.catalogs[0]
    assert [p["page"] for p in c.pages] == [1, 2, 3, 4] and c.pages[0]["url"].endswith("/files/large/1.jpg")
    assert c.kind == "aktuel"


def test_migroskop_pdf_probe_same_issue_only():
    from firsat360.connectors.extra import MigroskopConnector
    asked = []

    def handler(req):
        if "money.com.tr" in req.url.host:
            return httpx.Response(200, text="<html><body>Sayı 908 8 - 21 Ekim 2026</body></html>")
        asked.append(str(req.url))
        return httpx.Response(200, headers={"content-type": "application/pdf"})
    c = MigroskopConnector().fetch(_client(handler), NOW).catalogs[0]
    assert c.pdf_url.endswith("/migros/908/insert.pdf") and c.kind == "pdf"
    assert [u for u in asked if "moneyclubkart" in u] == ["https://moneyclubkart.azureedge.net/mcfiles/insert/migros/908/insert.pdf"]

    def no_pdf(req):
        if "money.com.tr" in req.url.host:
            return httpx.Response(200, text="<html><body>Sayı 909 22 Ekim - 4 Kasım</body></html>")
        return httpx.Response(404)
    c2 = MigroskopConnector().fetch(_client(no_pdf), NOW).catalogs[0]
    assert c2.pdf_url is None and c2.kind == "flipbook"  # uygulamada resmî sayfası açılır


def test_daily_deals_rotate_each_day(sf):
    """Günün İndirimleri her gün değişir: yeterli aday varken dün gösterilenler bugün tekrar edilmez."""
    with sf() as s:
        ensure_stores(s)
        for st in ("bim", "sok", "a101"):
            res = ConnectorResult(store_id=st, method="live_html", observed_at=NOW)
            res.catalogs.append(CatalogData(id=f"{st}-c", title="t", kind="discount_list", official_url="u"))
            for i in range(40):
                res.offers.append(OfferData(name=f"{st} ürün {i}", price=Decimal("50"), price_text="50 ₺",
                                            source_url="u", catalog_id=f"{st}-c",
                                            store_declared_old_price=Decimal(str(70 + i)),
                                            image_url=f"https://cdn1.bim.com.tr/{st}{i}.jpg", image_status="listing"))
            ingest(s, res)
        s.commit()
        days = [set(daily_deals(s, date(2026, 10, d), now=NOW, limit=12, per_store=4)["offer_ids"])
                for d in (14, 15, 16)]
    assert all(len(x) == 12 for x in days)
    assert not (days[0] & days[1]) and not (days[1] & days[2])  # ardışık günlerde ortak ürün yok
    # Aynı gün iki kez hesaplanınca aynı sonuç (sabah/akşam çalıştırması)
    with sf() as s:
        again = set(daily_deals(s, date(2026, 10, 14), now=NOW, limit=12, per_store=4)["offer_ids"])
    assert again == days[0]


def test_migroskop_digital_pages():
    from firsat360.connectors.extra import parse_migroskop_digital
    html = """<img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/MIGROSKOP_72YIL_migroskop_kapak1/Cover/6f30_743x1024.jpg">
      <div style="background:url('https://migroskop-assets.migros.com.tr/migrosdigitalinsert/908%208-21%20EKIM%20MIGROSKOPHODRI%20MEYDAN/Ad/11ea_716x1024.jpg?t=6392')"></div>
      <img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/908 8-21 EKIM MIGROSKOPHODRI MEYDAN5/Ad/d95f_716x1024.jpg">
      <img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/12345678/Product/aa_200x200.png">
      <img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/MIGROSKOP_72YIL_migroskop_kapak1/Cover/6f30_743x1024.jpg">"""
    pages = parse_migroskop_digital(html)
    assert len(pages) == 3 and "/Cover/" in pages[0]["url"]
    assert " " not in pages[2]["url"] and "%20MEYDAN5/Ad/d95f" in pages[2]["url"]
    assert not any("/Product/" in p["url"] for p in pages)
