"""Uygulamanın WebView'e enjekte ettiği broşür okuma betiği (Kotlin EXTRACT_JS) — gerçek Chromium ile.
Betik doğrudan Kotlin kaynağından okunur; uygulamadaki ile test edilen aynıdır."""
import functools
import http.server
import importlib.util
import json
import re
import threading
import time
from pathlib import Path

import pytest

ROOT = Path(__file__).resolve().parents[2]
BRIDGE = ROOT / "android-app/app/src/main/assets/bridge"

pytestmark = pytest.mark.skipif(
    not Path("/opt/pw-browsers").exists() or importlib.util.find_spec("playwright") is None
    or importlib.util.find_spec("PIL") is None, reason="Chromium/Pillow yok")


def _js() -> str:  # uygulamanın çalıştırdığı genel profil (common.js + generic.js)
    return (BRIDGE / "common.js").read_text(encoding="utf-8") + "\n" + (BRIDGE / "generic.js").read_text(encoding="utf-8")


@pytest.fixture()
def site(tmp_path):
    from PIL import Image
    (tmp_path / "img").mkdir()
    for n, (w, h) in {"p1": (900, 1300), "p2": (900, 1300), "p3": (900, 1300), "banner": (1600, 400),
                      "logo": (120, 120)}.items():
        Image.new("RGB", (w, h), "white").save(tmp_path / "img" / f"{n}.jpg")
    (tmp_path / "index.html").write_text("""<html><head><title>Afişler</title></head><body>
      <img src="img/logo.jpg"><img src="img/banner.jpg"><div style="height:2500px"></div>
      <img loading="lazy" src="img/p1.jpg"><div style="height:2500px"></div>
      <img loading="lazy" srcset="img/p2.jpg?w=300 300w, img/p2.jpg 900w"><div style="height:2500px"></div>
      <img loading="lazy" src="img/p3.jpg"></body></html>""")
    (tmp_path / "challenge.html").write_text(
        "<html><head><title>Just a moment...</title></head><body><div id='challenge-form'></div></body></html>")

    class Q(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a):
            pass
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Q, directory=str(tmp_path)))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    yield f"http://127.0.0.1:{srv.server_port}"
    srv.shutdown()


def test_extracts_lazy_portrait_pages_in_order_after_reaching_end(site):
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 412, "height": 900})
        pg.goto(site + "/index.html")
        last, result = -1, None
        for _ in range(16):  # uygulamadaki MAX_POLLS ile aynı
            r = json.loads(pg.evaluate(_js()))
            n = len(r.get("urls", []))
            if n >= 2 and n == last and r["end"]:
                result = r["urls"]
                break
            last = n
            time.sleep(0.9)
        pg.goto(site + "/challenge.html")
        challenge = json.loads(pg.evaluate(_js()))
        b.close()
    assert [u.rsplit("/", 1)[-1] for u in result] == ["p1.jpg", "p2.jpg", "p3.jpg"]  # afiş bandı/logo yok, sıra doğru
    assert challenge == {"type": "challenge"}  # doğrulama sayfası tanınır → kullanıcıya gösterilir, çözülmez
