"""İstemci köprüsü yapılandırması: feed alanı, sunucu kazımasının kapalı olması, React Native paketinin güncelliği."""
import json
import subprocess
import sys
from pathlib import Path

from firsat360.connectors import CLIENT_BRIDGED
from firsat360.feed import client_bridge

ROOT = Path(__file__).resolve().parents[2]
MAPPED = {
    "a101": "https://www.a101.com.tr/afisler",
    "sok": "https://kurumsal.sokmarket.com.tr/tr/firsatlar",
    "migros": "https://migroskop.migros.com.tr/migroskop",
    "gratis": "https://www.gratis.com/gratis-katalog",
    "carrefoursa": "https://katalog.carrefoursa.com/books/mwis",
    "rossmann": "https://www.rossmann.com.tr/kataloglar",
}


def test_all_six_markets_client_rendered():
    cfg = json.loads((ROOT / "config/client_bridge.json").read_text(encoding="utf-8"))["markets"]
    assert {k: v["url"] for k, v in cfg.items()} == MAPPED
    assert all(v["client_rendered"] is True for v in cfg.values())
    assert set(CLIENT_BRIDGED) == set(MAPPED)


def test_feed_client_bridge_entries_are_safe():
    cb = client_bridge()
    assert set(cb) == set(MAPPED)
    for k, v in cb.items():
        assert v["url"] == MAPPED[k] and v["url"].startswith("https://")
        assert (ROOT / f"android-app/app/src/main/assets/bridge/{v['profile']}.js").exists()
        assert v["allowed_hosts"] and all("/" not in h for h in v["allowed_hosts"])


def test_react_native_bridge_scripts_in_sync():
    r = subprocess.run([sys.executable, str(ROOT / "scripts/gen_rn_bridge.py"), "--check"], capture_output=True, text=True)
    assert r.returncode == 0, r.stderr


def test_retired_store_onur_not_in_feed(tmp_path):
    """Onur Market kaldırıldı (22.10.2026): eski kayıtlar veritabanında kalsa da feed'e girmez, bağlayıcısı yok."""
    from datetime import date, datetime, timezone
    from firsat360.config import STORES
    from firsat360.connectors import live_connectors
    from firsat360.db import Catalog, Store, make_session_factory
    from firsat360.feed import build_feed
    assert "onur" not in STORES and "onur" not in live_connectors()
    sf = make_session_factory(f"sqlite:///{tmp_path}/t.db")
    now = datetime(2026, 10, 22, tzinfo=timezone.utc)
    with sf() as s:
        s.add(Store(id="onur", name="Onur Market", official_url="https://x", catalog_url="https://x", status="link_only"))
        s.add(Catalog(id="onur-1", store_id="onur", title="t", kind="pdf", official_url="u", source_method="x",
                      observed_at=now, data_mode="real", valid_to=date(2026, 10, 30)))
        s.commit()
        feed = build_feed(s, today=date(2026, 10, 22))
    assert not any(x["id"] == "onur" for x in feed["stores"]) and not any(c["store_id"] == "onur" for c in feed["catalogs"])


def test_retired_store_hidden_from_status(tmp_path):
    from firsat360.db import Store, make_session_factory
    from firsat360.sync import retire_stores, status_dict
    sf = make_session_factory(f"sqlite:///{tmp_path}/s.db")
    with sf() as s:
        s.add(Store(id="onur", name="Onur Market", official_url="https://x", catalog_url="https://x", status="link_only"))
        s.add(Store(id="bim", name="BİM", official_url="https://x", catalog_url="https://x", status="ok"))
        s.commit()
    retire_stores(sf)
    with sf() as s:
        assert s.get(Store, "onur").enabled is False
        ids = [x["id"] for x in status_dict(s)["stores"]]
    assert ids == ["bim"]


def test_variant_cleaning_and_duplicate_merge():
    from firsat360.feed import merge_variant_duplicates
    from firsat360.normalize import clean_variant
    assert clean_variant("• 2 L") == "2 L"
    assert clean_variant("•") is None and clean_variant(" • • ") is None
    assert clean_variant("• • Napoliten 360 g • Arrabbiata 360 g") == "Napoliten 360 g, Arrabbiata 360 g"
    base = {"store_id": "bim", "catalog_id": "bim-1", "name": "Set", "variant": None, "page_size_text": None}
    out = merge_variant_duplicates([dict(base, id="a", price=459.0), dict(base, id="b", price=249.0),
                                    dict(base, id="c", price=10.0, variant="200 ml")])
    assert [o["id"] for o in out] == ["b", "c"]
    assert [x["price"] for x in out[0]["options"]] == [249.0, 459.0] and out[0]["aliases"] == ["a"]
