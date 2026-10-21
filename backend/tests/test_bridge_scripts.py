"""İstemci katalog köprüsü betikleri (android-app/app/src/main/assets/bridge/*.js) — gerçek Chromium ile.
Test sayfaları, 21.10.2026'da resmî sayfalarda doğrulanan yapıları taklit eder:
ŞOK kurumsal (PDF bağlantıları + tarih), FlipHTML5 kitabı (fliphtml5_pages), Gratis mağaza sayfası (kitaba bağlantı),
Migroskop (migroskop-assets Cover/Ad), A101 (Next.js gömülü verisi), genel (büyük dikey görseller), doğrulama sayfası."""
import functools
import http.server
import importlib.util
import json
import threading
from pathlib import Path

import pytest

BRIDGE = Path(__file__).resolve().parents[2] / "android-app/app/src/main/assets/bridge"
pytestmark = pytest.mark.skipif(
    not Path("/opt/pw-browsers").exists() or importlib.util.find_spec("playwright") is None
    or importlib.util.find_spec("PIL") is None, reason="Chromium/Pillow yok")


ROOT = Path(__file__).resolve().parents[2]


def rn_script(profile: str) -> str:
    spec = importlib.util.spec_from_file_location("gen_rn_bridge", ROOT / "scripts/gen_rn_bridge.py")
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod.wrap((BRIDGE / "common.js").read_text(encoding="utf-8"), (BRIDGE / f"{profile}.js").read_text(encoding="utf-8"))


def script(profile: str) -> str:
    return (BRIDGE / "common.js").read_text(encoding="utf-8") + "\n" + (BRIDGE / f"{profile}.js").read_text(encoding="utf-8")


PAGES = {
    "sok.html": """<html><body><div class="card"><h3>Haftanın Fırsatları</h3><span>21 - 27 Ekim 2026</span>
      <a href="https://kurumsal.sokmarket.com.tr/uploads/pdf/mvi8bc55-21-27ekimhaftaninfirsatlari.pdf">Haftanın Fırsatları</a></div>
      <div class="card"><h3>Hafta Sonu</h3><span>24 - 27 Ekim 2026</span>
      <a href="https://kurumsal.sokmarket.com.tr/uploads/pdf/mvi6kke9-24-27haftasonufirsatlari.pdf">Hafta Sonu Fırsatları</a></div>
      <a href="https://evil.example.com/x.pdf">yabancı</a></body></html>""",
    "book/index.html": """<html><head><meta name="generator" content="fliphtml5.com"><title>16 Ekim - 1 Kasım</title>
      <script>var fliphtml5_pages=[{"n":["files/large/a1.jpg"]},{"n":["../files/large/b2.jpg"]},{"n":"c3.jpg"}];</script>
      </head><body><img src="files/shot.jpg"></body></html>""",
    "gratis.html": """<html><body><h2>Ürün Kataloğu</h2><a href="https://online.fliphtml5.com/gratiscom/vczv/">katalog</a></body></html>""",
    "migroskop.html": """<html><body>
      <img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/MIGROSKOP_72YIL_migroskop_kapak1/Cover/6f30_743x1024.jpg">
      <div style="background-image:url('https://migroskop-assets.migros.com.tr/migrosdigitalinsert/908 8-21 EKIM HODRI/Ad/11ea_716x1024.jpg?t=1')"></div>
      <img src="https://migroskop-assets.migros.com.tr/migrosdigitalinsert/12345678/Product/aa_200x200.png"></body></html>""",
    "a101.html": """<html><body><script id="__NEXT_DATA__" type="application/json">{"props":{"posters":[
      {"title":"Haftanın Yıldızları","image":"https:\\/\\/cdn2.a101.com.tr\\/dbmk89vnr\\/CALL\\/Image\\/get\\/afis_hy_1.jpg"},
      {"title":"Aldın Aldın","image":"https://cdn2.a101.com.tr/dbmk89vnr/CALL/Image/get/afis_aa_2.jpg"},
      {"logo":"https://cdn2.a101.com.tr/logo.png"}]}}</script></body></html>""",
    "challenge.html": "<html><head><title>Just a moment...</title></head><body><div class='cf-turnstile'></div></body></html>",
}


@pytest.fixture(scope="module")
def site(tmp_path_factory):
    from PIL import Image
    root = tmp_path_factory.mktemp("bridge")
    for name, html in PAGES.items():
        (root / name).parent.mkdir(parents=True, exist_ok=True)
        (root / name).write_text(html, encoding="utf-8")
    (root / "img").mkdir()
    for n, (w, h) in {"p1": (900, 1300), "p2": (900, 1300), "band": (1600, 400)}.items():
        Image.new("RGB", (w, h), "white").save(root / "img" / f"{n}.jpg")
    (root / "generic.html").write_text("""<html><body><img src="img/band.jpg"><img src="img/p1.jpg">
      <img src="img/p2.jpg"></body></html>""")

    class Q(http.server.SimpleHTTPRequestHandler):
        def log_message(self, *a):
            pass
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), functools.partial(Q, directory=str(root)))
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    from playwright.sync_api import sync_playwright
    with sync_playwright() as p:
        b = p.chromium.launch()
        pg = b.new_page(viewport={"width": 412, "height": 900})

        def run(path, profile):
            pg.goto(f"http://127.0.0.1:{srv.server_port}/{path}")
            pg.wait_for_load_state("load")
            return json.loads(pg.evaluate(script(profile)))

        def run_rn(path, profile):
            # React Native yolu: aynı betik, injectJavaScript sarmalayıcısıyla; sonuç ReactNativeWebView.postMessage'a gider
            pg.goto(f"http://127.0.0.1:{srv.server_port}/{path}")
            pg.wait_for_load_state("load")
            pg.evaluate("window.__posted=[]; window.ReactNativeWebView={postMessage:function(m){window.__posted.push(m)}}")
            assert pg.evaluate(rn_script(profile)) is True  # injectJavaScript betiği "true" ile biter
            posted = pg.evaluate("window.__posted")
            assert len(posted) == 1
            return json.loads(posted[0])
        run.rn = run_rn
        yield run, srv.server_port
        b.close()
    srv.shutdown()


def test_sok_official_pdfs_with_dates(site):
    run, _ = site
    r = run("sok.html", "sok")
    assert r["type"] == "pdf" and len(r["items"]) == 2  # yabancı alan adındaki PDF alınmaz
    assert r["items"][0]["url"].endswith("21-27ekimhaftaninfirsatlari.pdf") and r["items"][0]["dates"] == "21 - 27 Ekim 2026"


def test_fliphtml5_full_page_list_from_book_config(site):
    run, port = site
    r = run("book/index.html", "fliphtml5")
    assert r["type"] == "images" and r["end"] is True
    assert r["urls"] == [f"http://127.0.0.1:{port}/book/files/large/{n}" for n in ("a1.jpg", "b2.jpg", "c3.jpg")]


def test_gratis_store_page_navigates_to_book(site):
    run, _ = site
    r = run("gratis.html", "fliphtml5")
    assert r == {"type": "navigate", "url": "https://online.fliphtml5.com/gratiscom/vczv/"}


def test_migroskop_cover_and_ad_pages_only(site):
    run, _ = site
    r = run("migroskop.html", "migroskop")
    assert r["type"] == "images" and len(r["urls"]) == 2
    assert "/Cover/" in r["urls"][0] and "%20" in r["urls"][1] and not any("/Product/" in u for u in r["urls"])


def test_a101_posters_from_embedded_next_data(site):
    run, _ = site
    r = run("a101.html", "a101")
    assert r["type"] == "images"
    assert [u.rsplit("/", 1)[-1] for u in r["urls"]] == ["afis_hy_1.jpg", "afis_aa_2.jpg"]  # logo yok, kaçışlar çözüldü


def test_generic_portrait_pages(site):
    run, _ = site
    r = run("generic.html", "generic")
    assert r["type"] == "images" and [u.rsplit("/", 1)[-1] for u in r["urls"]] == ["p1.jpg", "p2.jpg"]


@pytest.mark.parametrize("profile", ["a101", "sok", "migroskop", "fliphtml5", "generic"])
def test_challenge_is_reported_not_solved(site, profile):
    run, _ = site
    assert run("challenge.html", profile) == {"type": "challenge"}


@pytest.mark.parametrize("path,profile", [("sok.html", "sok"), ("book/index.html", "fliphtml5"), ("gratis.html", "fliphtml5"),
                                          ("migroskop.html", "migroskop"), ("a101.html", "a101"), ("generic.html", "generic"),
                                          ("challenge.html", "a101")])
def test_react_native_wrapper_posts_same_result(site, path, profile):
    """React Native (postMessage) ve Android (evaluateJavascript dönüşü) aynı sonucu alır."""
    run, _ = site
    assert run.rn(path, profile) == run(path, profile)
