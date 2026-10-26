"""Kalite ajanları + orkestratör: denetle → düzelt → yeniden denetle → raporla."""
import json
from datetime import date, datetime, timezone
from decimal import Decimal

import httpx
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from firsat360.db import Base, Offer, Store
from firsat360.quality import Context, GitHubReports, markdown_summary, orchestrate

NOW = datetime(2026, 10, 21, 6, tzinfo=timezone.utc)


def _db(tmp_path):
    e = create_engine(f"sqlite:///{tmp_path}/q.db")
    Base.metadata.create_all(e)
    return sessionmaker(e)


def _offer(i, **kw):
    d = dict(id=i, store_id="bim", name=f"Ürün {i}", search_key="x", category="diger", category_source="t",
             published=True, price=Decimal("10"), source_url="u", source_method="live_html", verification="",
             data_mode="real", first_seen_at=NOW, last_seen_at=NOW, catalog_id="bim-1",
             product_url=f"https://www.bim.com.tr/aktuel-urunler/{i}/aktuel.aspx")
    d.update(kw)
    return Offer(**d)


def test_orchestrator_fixes_and_reports(tmp_path):
    sf = _db(tmp_path)
    with sf() as s:
        s.add(Store(id="bim", name="BİM", official_url="u", catalog_url="u", status="error", last_error="Zaman aşımı"))
        s.add(Store(id="a101", name="A101", official_url="u", catalog_url="u", status="error",
                    last_error="403 yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı"))
        s.add(_offer("eski", valid_to=date(2026, 10, 10)))
        s.add(_offer("cop", name="product-thumb"))
        s.add(_offer("afis", image_url="https://cdn1.bim.com.tr/uploads/aktuel-urunler/986_aycicek-yagi-hc.jpg",
                     image_status="listing"))
        s.add(_offer("iyi", image_url="https://cdn1.bim.com.tr/uploads/aktuel-urunler/1692_kucuk_543X467_1.jpg",
                     image_status="listing"))
        s.add(_offer("eskifiyat", store_declared_old_price=Decimal("5")))
        s.commit()
    retried, reverified = [], []

    def retry(sid):
        retried.append(sid)
        with sf() as s:
            s.get(Store, sid).status = "ok"
            s.commit()
        return {"ok": True}

    rep = orchestrate(Context(sf=sf, today=date(2026, 10, 21), now=NOW, retry_store=retry,
                              reverify=lambda st: reverified.append(st) or {st[0]: {"pages": 1, "verified": 0}}))
    assert retried == ["bim"]  # erişim engeli (A101) yeniden denenmez, yalnızca raporlanır
    by = {(i["agent"], i["code"], i["store"]): i for i in rep["issues"]}
    assert by[("kaynak", "source_failed", "bim")]["resolved"] is True
    assert by[("kaynak", "source_failed", "a101")]["fix"] is None
    assert by[("katalog", "expired_published", "bim")]["resolved"] is True
    assert by[("veri_kalitesi", "junk_name", None)]["resolved"] is True
    assert by[("urun_gorseli", "image_pattern", "bim")]["resolved"] is True
    assert by[("veri_kalitesi", "bad_old_price", None)]["resolved"] is True
    with sf() as s:
        assert not s.get(Offer, "eski").published and not s.get(Offer, "cop").published
        assert s.get(Offer, "afis").image_url is None and s.get(Offer, "iyi").image_url
        assert s.get(Offer, "eskifiyat").store_declared_old_price is None
    assert any(e["store"] == "a101" for e in rep["remaining_errors"])
    assert "kalite raporu" in markdown_summary(rep)


def test_user_report_flow_via_github(tmp_path):
    sf = _db(tmp_path)
    with sf() as s:
        s.add(_offer("bim-abc123"))
        s.commit()
    calls = []

    def handler(req: httpx.Request):
        calls.append((req.method, req.url.path, req.content.decode() if req.content else ""))
        if req.method == "GET":
            return httpx.Response(200, json=[{"number": 7, "title": "[Hatalı veri] Ürün bim-abc123",
                                              "body": "Görsel yanlış\n\noffer:bim-abc123"}])
        return httpx.Response(201, json={})

    gh = GitHubReports("ebilgi992/firsat360", "t", transport=httpx.MockTransport(handler))
    rep = orchestrate(Context(sf=sf, today=date(2026, 10, 21), now=NOW, github=gh,
                              reverify=lambda st: {st[0]: {"pages": 1, "verified": 0}}))
    assert any(i["code"] == "user_report" for i in rep["issues"])
    posted = [c for c in calls if c[0] == "POST" and c[1].endswith("/issues/7/comments")]
    closed = [c for c in calls if c[0] == "PATCH" and c[1].endswith("/issues/7") and '"closed"' in c[2]]
    assert posted and closed
    assert "yeniden doğrulandı" in json.loads(posted[0][2])["body"]


def test_tk_pattern_accepts_subfolders_rejects_seo():
    from firsat360.quality import IMAGE_PATTERNS
    p = IMAGE_PATTERNS["tkkoop"]
    assert p.search("https://www.tkkoop.com.tr/assets/images/urun/base22/123029.jpg")
    assert p.search("https://www.tkkoop.com.tr/assets/images/urun/esk-govde-tavuk_1763707592.png")
    assert not p.search("https://www.tkkoop.com.tr/storage/assets/images/seo/")



def test_missing_images_have_product_evidence_and_keep_issue_open(tmp_path):
    sf = _db(tmp_path)
    with sf() as s:
        s.add(Store(id='bim', name='BİM', official_url='u', catalog_url='u', status='ok'))
        s.add(_offer('missing', name='Süt 1 L', image_status='name_mismatch'))
        s.commit()
    report = orchestrate(Context(sf=sf, today=NOW.date(), now=NOW))
    assert report['unresolved_images']
    product = report['image_metrics']['bim']['missing_products'][0]
    assert product['id'] == 'missing' and product['reason'] == 'name_mismatch'
    assert 'Görseli eksik ürün: 1' in markdown_summary(report)


def test_blocked_store_is_not_retried_for_image_verification(tmp_path):
    sf = _db(tmp_path)
    with sf() as s:
        s.add(Store(id='a101', name='A101', official_url='u', catalog_url='u', status='error', last_error='Cloudflare 403'))
        s.add(_offer('a101-missing', store_id='a101', image_status='page_empty'))
        s.commit()
    called = []
    report = orchestrate(Context(sf=sf, today=NOW.date(), now=NOW, reverify=lambda stores: called.append(stores)))
    assert not called
    assert 'erişim engelli' in next(i['fix'] for i in report['issues'] if i['code'] == 'missing_images')
