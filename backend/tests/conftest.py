import json
from datetime import datetime, timedelta, timezone
from pathlib import Path

import httpx
import pytest

from firsat360.config import Settings
from firsat360.db import make_session_factory

FIX = Path(__file__).parent / "fixtures"
SNAPSHOTS = Path(__file__).resolve().parents[2] / "data" / "snapshots"
SNAPSHOT = SNAPSHOTS / "2026-10-12" / "raw"
SNAPSHOT_13 = SNAPSHOTS / "2026-10-13" / "raw"
TR = timezone(timedelta(hours=3))
OBS = datetime(2026, 10, 12, 12, 0, tzinfo=TR)


def pytest_configure(config):
    config.addinivalue_line("markers", "fixture_data: SENTETİK fikstürlerle çalışan test (demo veri)")
    config.addinivalue_line("markers", "real_data: 12.10 / 13.10.2026 gerçek veri anlık görüntüleriyle çalışan test")
    config.addinivalue_line("markers", "postgres: gerçek PostgreSQL sunucusu gerektirir")


def fixture_text(name: str) -> str:
    return (FIX / name).read_text(encoding="utf-8")


ROUTES = {
    "https://www.bim.com.tr/categories/100/aktuel-urunler.aspx": "bim_list.html",
    "https://www.a101.com.tr/aldin-aldin": "a101_list.html",
    "https://www.sokmarket.com.tr/kampanyalar": "sok_list.html",
    "https://www.sokmarket.com.tr/50-tl-ve-uzeri-alisveriste-79-00-tl-prm-c1b11b9c-089e-4eba-9ee6-a769bee78923": "sok_campaign_79.html",
    "https://www.sokmarket.com.tr/1-alana-1-bedava-prm-df680613-3f29-45ab-9e60-2145a21f961a": "sok_campaign_bogo.html",
    "https://www.migros.com.tr/rest/search/screens/migroskop-urunleri-dt-3": "migros_search.json",
    "https://www.sokmarket.com.tr/indirimli-urunler-cms-pl1": "sok_discount.html",
    "https://www.bim.com.tr/Categories/680/afisler.aspx": "bim_afis.html",
}


def make_transport(overrides: dict | None = None, robots: dict | None = None):
    """URL → fikstür eşleyen sahte HTTP katmanı. overrides: url → callable(request) veya (status, body)."""
    overrides = overrides or {}
    robots = robots or {}

    def handler(request: httpx.Request):
        url = str(request.url)
        base = url.split("?")[0]
        if base.endswith("/robots.txt"):
            host = request.url.host
            return httpx.Response(200, text=robots.get(host, "User-agent: *\nDisallow: /arama\n"))
        for key in (url, base):
            if key in overrides:
                v = overrides[key]
                if callable(v):
                    return v(request)
                return httpx.Response(v[0], text=v[1])
        if base == ROUTES["https://www.bim.com.tr/categories/100/aktuel-urunler.aspx"] or base in ROUTES:
            name = ROUTES[base]
            ctype = "application/json" if name.endswith(".json") else "text/html; charset=utf-8"
            return httpx.Response(200, text=fixture_text(name), headers={"content-type": ctype})
        return httpx.Response(404, text="yok")

    return httpx.MockTransport(handler)


@pytest.fixture
def sf(tmp_path):
    return make_session_factory(f"sqlite:///{tmp_path/'t.db'}")


@pytest.fixture
def settings(tmp_path, monkeypatch):
    monkeypatch.setenv("MIN_REQUEST_INTERVAL_S", "0")
    monkeypatch.setenv("ADMIN_USER", "yonetici")
    monkeypatch.setenv("ADMIN_PASSWORD", "cok-gizli-parola-123")
    monkeypatch.setenv("FEED_EXPORT_PATH", str(tmp_path / "feed.json"))
    monkeypatch.setenv("HTTP_CACHE_DIR", str(tmp_path / "http-cache"))
    monkeypatch.setenv("LOG_DIR", str(tmp_path / "logs"))
    monkeypatch.setenv("NTFY_URL", "")
    monkeypatch.setenv("NTFY_TOPIC", "")
    return Settings()


@pytest.fixture
def no_sleep(monkeypatch):
    import firsat360.connectors.base as base
    monkeypatch.setattr(base.time, "sleep", lambda s: None)
