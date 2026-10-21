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
