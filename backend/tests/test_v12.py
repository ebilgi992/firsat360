"""1.2: kontrollü geri çekilme, bölgesel katalog grubu, afiş türü."""
from datetime import date, datetime, timedelta, timezone

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker

from firsat360.db import Base, Catalog, SourceRun, Store
from firsat360.feed import catalog_group
from firsat360.pipeline import blocked_backoff

NOW = datetime(2026, 10, 21, 18, tzinfo=timezone.utc)


def _sf(tmp_path):
    e = create_engine(f"sqlite:///{tmp_path}/v.db")
    Base.metadata.create_all(e)
    return sessionmaker(e)


def test_backoff_after_three_blocked_runs(tmp_path):
    sf = _sf(tmp_path)
    with sf() as s:
        s.add(Store(id="a101", name="A101", official_url="u", catalog_url="u"))
        for h in (30, 18, 6):
            s.add(SourceRun(store_id="a101", method="live_html", trigger="schedule", started_at=NOW - timedelta(hours=h),
                            ok=False, error="ConnectorError: 403 yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı"))
        s.commit()
    assert blocked_backoff(sf, "a101", NOW)                       # 6 saat önce denendi → atla
    assert blocked_backoff(sf, "a101", NOW + timedelta(hours=15)) is None  # 21 saat geçti → dene
    with sf() as s:
        s.add(SourceRun(store_id="a101", method="live_html", trigger="schedule", started_at=NOW, ok=False,
                        error="Zaman aşımı"))
        s.commit()
    assert blocked_backoff(sf, "a101", NOW) is None  # son hata engel değil (geçici) → normal deneme


def test_regional_copies_share_group_but_different_inserts_do_not():
    def cat(i, title, region, f, t):
        return Catalog(id=i, store_id="onur", title=title, kind="pdf", official_url="u", source_method="x",
                       observed_at=NOW, data_mode="real", region=region, valid_from=f, valid_to=t)
    a = cat("onur-istanbul-944", "Onur'da Çok Al Az Öde · İstanbul", "istanbul", date(2026, 10, 13), date(2026, 10, 26))
    b = cat("onur-bursa-945", "Onur'da Çok Al Az Öde · Bursa", "bursa", date(2026, 10, 13), date(2026, 10, 26))
    c = cat("onur-istanbul-959", "Taze Ürünlerde İndirimler · İstanbul", "istanbul", date(2026, 10, 15), date(2026, 10, 21))
    assert catalog_group(a) == catalog_group(b) != catalog_group(c)


def test_file_posters_are_not_brochures():
    from firsat360.connectors.discovery import SOURCES, Found, to_catalogs
    f = Found(images=["https://www.file.com.tr/uploads/file/banner/x_AFIS_web.jpg"])
    cats = to_catalogs("file", "https://www.file.com.tr/", f, date(2026, 10, 21), 0, SOURCES["file"].title,
                       SOURCES["file"].page_kind)
    assert cats[0].kind == "poster" and cats[0].title == "File Market ürün afişleri"
