"""Veri hattı entegrasyon testleri: izolasyon, zaman aşımı, robots.txt, 403, mükerrer kayıt, fiyat geçmişi.
HTTP katmanı sahte (MockTransport) — SENTETİK fikstürler kullanılır."""
import json
from datetime import timedelta
from decimal import Decimal

import httpx
import pytest
from sqlalchemy import select

from firsat360.connectors import live_connectors
from firsat360.connectors.base import ConnectorResult, OfferData, PoliteClient, RobotsDisallowed
from firsat360.db import Offer, PriceObservation, SourceRun, Store
from firsat360.feed import build_feed, export_feed
from firsat360.pipeline import ingest, run_all

from .conftest import OBS, make_transport

pytestmark = pytest.mark.fixture_data


def test_full_run_all_four_with_fixtures(sf, settings, no_sleep):
    res = run_all(sf, live_connectors(), settings, transport=make_transport(),
                  only=["a101", "bim", "sok", "migros"])
    assert {r["store"]: r["ok"] for r in res} == {"a101": True, "bim": True, "sok": True, "migros": True}
    with sf() as s:
        assert {st.id: st.status for st in s.scalars(select(Store))}["bim"] in ("ok", "partial")
        assert s.scalars(select(Offer).where(Offer.store_id == "migros")).all()


def test_one_store_failing_does_not_stop_others(sf, settings, no_sleep):
    def boom(request):
        raise httpx.ConnectTimeout("zaman aşımı", request=request)
    t = make_transport({"https://www.bim.com.tr/categories/100/aktuel-urunler.aspx": boom})
    res = {r["store"]: r for r in run_all(sf, live_connectors(), settings, transport=t,
                                          only=["a101", "bim", "sok", "migros"])}
    assert res["bim"]["ok"] is False and "İstek başarısız" in res["bim"]["error"]
    assert res["a101"]["ok"] and res["sok"]["ok"] and res["migros"]["ok"]
    with sf() as s:
        bim = s.get(Store, "bim")
        assert bim.status == "error" and bim.last_error
        failed = s.scalars(select(SourceRun).where(SourceRun.store_id == "bim")).all()
        assert failed and not failed[-1].ok


def test_403_is_reported_not_bypassed(sf, settings, no_sleep):
    calls = []

    def forbidden(request):
        calls.append(str(request.url))
        return httpx.Response(403, text="cf-challenge")
    t = make_transport({"https://www.a101.com.tr/aldin-aldin": forbidden})
    res = {r["store"]: r for r in run_all(sf, live_connectors(), settings, transport=t, only=["a101"])}
    assert res["a101"]["ok"] is False and "403" in res["a101"]["error"]
    assert len(calls) == 1  # yeniden deneme / aşma girişimi yok


def test_robots_disallow_respected(no_sleep):
    t = make_transport(robots={"www.bim.com.tr": "User-agent: *\nDisallow: /categories/\n"})
    client = PoliteClient("FIRSAT360Bot/test", min_interval_s=0, transport=t)
    with pytest.raises(RobotsDisallowed):
        client.get("https://www.bim.com.tr/categories/100/aktuel-urunler.aspx")


def test_rate_limit_waits_between_requests():
    slept = []
    client = PoliteClient("ua", min_interval_s=5, transport=make_transport(), sleep=slept.append, check_robots=False)
    client.get("https://www.sokmarket.com.tr/kampanyalar")
    client.get("https://www.sokmarket.com.tr/kampanyalar")
    assert slept and slept[-1] > 4


def test_server_error_retries_then_fails(no_sleep):
    n = []

    def flaky(request):
        n.append(1)
        return httpx.Response(503, text="bakım")
    client = PoliteClient("ua", min_interval_s=0, retries=2, transport=make_transport(
        {"https://www.migros.com.tr/x": flaky}), check_robots=False)
    with pytest.raises(Exception):
        client.get("https://www.migros.com.tr/x")
    assert len(n) == 3


def _result(price, when=OBS):
    return ConnectorResult(store_id="bim", method="live_html", observed_at=when, offers=[
        OfferData(name="Hindi Salam", brand="Pınar", variant="50 g", price=Decimal(price), price_text=f"{price} ₺",
                  source_url="https://www.bim.com.tr/x", catalog_id=None),
        OfferData(name="HİNDİ SALAM", brand="PINAR", variant="50 g", price=Decimal(price), price_text="dup",
                  source_url="https://www.bim.com.tr/x", catalog_id=None),  # aynı ürün, farklı yazım
        OfferData(name="Fiyatsız", price=None, price_text=None, source_url="https://www.bim.com.tr/x"),
        OfferData(name="Uçuk fiyat", price=Decimal("9999999"), price_text="?", source_url="https://www.bim.com.tr/x"),
    ])


def test_dedupe_validation_and_price_history(sf):
    from firsat360.pipeline import ensure_stores
    with sf() as s:
        ensure_stores(s)
        run = ingest(s, _result("15.00"))
        s.commit()
        assert run.offers == 1 and run.rejected == 2
        run2 = ingest(s, _result("12.50", OBS + timedelta(days=1)))
        s.commit()
        assert run2.price_changes == 1 and run2.new_offers == 0
        offer = s.scalars(select(Offer)).one()
        assert offer.price == Decimal("12.50")
        hist = s.scalars(select(PriceObservation).order_by(PriceObservation.observed_at)).all()
        assert [h.price for h in hist] == [Decimal("15.00"), Decimal("12.50")]


def test_old_price_not_above_current_is_dropped(sf):
    from firsat360.pipeline import ensure_stores
    r = ConnectorResult(store_id="sok", method="live_html", observed_at=OBS, offers=[
        OfferData(name="Ürün X", price=Decimal("10"), price_text="10", source_url="u",
                  store_declared_old_price=Decimal("8"))])
    with sf() as s:
        ensure_stores(s)
        ingest(s, r)
        s.commit()
        assert s.scalars(select(Offer)).one().store_declared_old_price is None


def test_feed_has_no_demo_and_no_images(sf, settings, no_sleep, tmp_path):
    run_all(sf, live_connectors(), settings, transport=make_transport())
    with sf() as s:
        with sf() as s2:
            o = s2.scalars(select(Offer)).first()
            o.data_mode = "demo"
            s2.commit()
        feed = build_feed(s, today=OBS.date())
        assert feed["data_mode"] == "real"
        assert all(x["data_mode"] == "real" for x in feed["offers"])
        assert all(x["image_url"] is None for x in feed["offers"])
        assert all(x["source_url"] and x["observed_at"] for x in feed["offers"])
        p = export_feed(s, tmp_path / "feed.json", today=OBS.date())
        assert json.loads(p.read_text(encoding="utf-8"))["schema_version"] == 2


def test_expired_offers_hidden(sf):
    from firsat360.pipeline import ensure_stores
    from datetime import date
    r = ConnectorResult(store_id="bim", method="live_html", observed_at=OBS, offers=[
        OfferData(name="Eski", price=Decimal("5"), price_text="5", source_url="u", valid_to=date(2026, 10, 1)),
        OfferData(name="Güncel", price=Decimal("5"), price_text="5", source_url="u")])
    with sf() as s:
        ensure_stores(s)
        ingest(s, r)
        s.commit()
        names = [o["name"] for o in build_feed(s, today=OBS.date())["offers"]]
        assert names == ["Güncel"]


def test_disabled_store_is_skipped(sf, settings, no_sleep):
    from firsat360.pipeline import ensure_stores
    with sf() as s:
        ensure_stores(s)
        s.get(Store, "migros").enabled = False
        s.commit()
    res = {r["store"]: r for r in run_all(sf, live_connectors(), settings, transport=make_transport())}
    assert res["migros"].get("skipped") == "devre dışı"


def test_admin_alert_webhook_on_failure(sf, settings, no_sleep):
    """Kaynak hatasında yönetici webhook'una bildirim gider; bildirim hatası taramayı bozmaz."""
    sent = []

    def hook(request):
        sent.append(json.loads(request.content))
        return httpx.Response(200)
    settings.alert_webhook_url = "https://hooks.example.test/firsat360"
    t = make_transport({"https://www.migros.com.tr/rest/search/screens/migroskop-urunleri-dt-3": (403, "engel")})
    res = {r["store"]: r for r in run_all(sf, live_connectors(), settings, transport=t, only=["a101", "bim", "sok", "migros"],
                                          alert_transport=httpx.MockTransport(hook))}
    assert res["migros"]["ok"] is False and res["bim"]["ok"]
    assert len(sent) == 1 and "migros" in sent[0]["text"] and "403" in sent[0]["text"]


def test_alert_failure_does_not_break_run(sf, settings, no_sleep):
    def broken(request):
        raise httpx.ConnectError("webhook kapalı", request=request)
    settings.alert_webhook_url = "https://hooks.example.test/x"
    t = make_transport({"https://www.a101.com.tr/aldin-aldin": (500, "hata")})
    res = {r["store"]: r for r in run_all(sf, live_connectors(), settings, transport=t, only=["a101", "bim", "sok", "migros"],
                                          alert_transport=httpx.MockTransport(broken))}
    assert res["a101"]["ok"] is False and res["sok"]["ok"] and res["migros"]["ok"]
