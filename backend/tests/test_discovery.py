"""Alternatif toplama motoru: statik tarama, tarayıcı (gerçek Chromium, yerel test sunucusu), sıralı yedekleme."""
import http.server
import threading
from datetime import datetime, timezone
from pathlib import Path

import httpx
import pytest

from firsat360.connectors.base import ConnectorError, PoliteClient
from firsat360.connectors.discovery import DiscoveryConnector, StoreSource, render_page, scan_html

NOW = datetime(2026, 10, 21, 6, tzinfo=timezone.utc)
SRC = StoreSource("hakmar", "Hakmar İnsert", ["https://hakmar.com.tr/tr/kampanyalar/insertlerimiz?region=bursa"],
                  hosts=("hakmar.com.tr",))


def _client(pages: dict[str, tuple[int, str]]):
    def handler(req):
        if req.url.path == "/robots.txt":
            return httpx.Response(200, text=pages.get("robots", (200, "User-agent: *\nDisallow:"))[1])
        if str(req.url).endswith(".jpg"):
            if "/bozuk" in str(req.url):  # sunucu image/jpeg diyor ama gövde HTML hata sayfası
                return httpx.Response(200, headers={"content-type": "image/jpeg"}, content=b"<!DOCTYPE html><html>")
            return httpx.Response(200, headers={"content-type": "image/jpeg"}, content=b"\xff\xd8\xff\xe0" + b"0" * 20000)
        code, body = pages.get(str(req.url), (404, ""))
        return httpx.Response(code, text=body)
    return PoliteClient("test", min_interval_s=0, transport=httpx.MockTransport(handler), sleep=lambda s: None)


def test_static_scan_finds_pdf_flipbook_and_brochure_images_only():
    html = """<h1>21-27 Ekim İnsert</h1>
      <a href="/files/insert-21-27-ekim.pdf">PDF</a><a href="https://evil.example.com/x.pdf">yabancı</a>
      <a href="https://online.fliphtml5.com/hakmar/abcd/">Katalog</a>
      <img src="/pics/insert/bursa_sayfa1.jpg"><img data-src="/pics/insert/bursa_sayfa2.jpg">
      <img src="/pics/logo.png"><img src="/pics/boxsliders/kampanya_banner.png">
      <img src="https://cdn.other.com/insert/x.jpg">"""
    f = scan_html(html, SRC.urls[0], SRC)
    assert f.pdfs == ["https://hakmar.com.tr/files/insert-21-27-ekim.pdf"]
    assert f.flipbooks[0][0] == "https://online.fliphtml5.com/hakmar/abcd/"
    assert f.images == ["https://hakmar.com.tr/pics/insert/bursa_sayfa1.jpg",
                        "https://hakmar.com.tr/pics/insert/bursa_sayfa2.jpg"]
    assert f.title == "21-27 Ekim İnsert"


def test_falls_back_to_renderer_when_static_empty():
    client = _client({SRC.urls[0]: (200, "<html><body><div id=app></div></body></html>")})
    calls = []

    def fake_render(url, ua):
        calls.append(url)
        return ("<html><h1>Bursa İnsert 21-27 Ekim</h1></html>",
                [{"url": "https://hakmar.com.tr/pics/insert/b1.jpg", "type": "image/jpeg", "width": 1200},
                 {"url": "https://hakmar.com.tr/pics/insert/thumb.jpg", "type": "image/jpeg", "width": 120},
                 {"url": "https://hakmar.com.tr/pics/icons/x.png", "type": "image/png", "width": 900},
                 {"url": "https://hakmar.com.tr/uploads/2026/b2.jpg", "type": "image/jpeg", "width": 1000,
                  "height": 1400},
                 {"url": "https://hakmar.com.tr/uploads/2026/header.jpg", "type": "image/jpeg",
                  "width": 1920, "height": 400}])
    res = DiscoveryConnector(SRC, renderer=fake_render).fetch(client, NOW)
    assert calls == SRC.urls
    cat = res.catalogs[0]
    assert [p["url"] for p in cat.pages] == ["https://hakmar.com.tr/pics/insert/b1.jpg",
                                             "https://hakmar.com.tr/uploads/2026/b2.jpg"]
    assert cat.valid_from and cat.valid_from.day == 21 and cat.valid_to.day == 27


def test_nothing_found_is_reported_not_faked():
    client = _client({SRC.urls[0]: (200, "<html><body>boş</body></html>")})
    with pytest.raises(ConnectorError) as e:
        DiscoveryConnector(SRC, renderer=lambda u, ua: ("<html></html>", [])).fetch(client, NOW)
    assert "bulunamadı" in str(e.value) and "statik" in str(e.value) and "tarayıcı" in str(e.value)


def test_robots_disallow_stops_both_paths():
    client = _client({"robots": (200, "User-agent: *\nDisallow: /"), SRC.urls[0]: (200, "<a href='/x.pdf'>x</a>")})
    rendered = []
    with pytest.raises(ConnectorError) as e:
        DiscoveryConnector(SRC, renderer=lambda u, ua: rendered.append(u) or ("", [])).fetch(client, NOW)
    assert not rendered and "robots" in str(e.value)


def test_blocked_403_is_not_forced_through_browser():
    client = _client({SRC.urls[0]: (403, "")})
    rendered = []
    with pytest.raises(ConnectorError):
        DiscoveryConnector(SRC, renderer=lambda u, ua: rendered.append(u) or ("", [])).fetch(client, NOW)
    assert not rendered


@pytest.mark.skipif(not Path("/opt/pw-browsers").exists() or __import__("importlib").util.find_spec("PIL") is None
                    or __import__("importlib").util.find_spec("playwright") is None, reason="Chromium/Pillow yok")
def test_real_chromium_render_captures_js_loaded_brochure(tmp_path):
    """Gerçek tarayıcı: görseller JavaScript ile eklenen sayfa (bölgesel insert sayfası gibi)."""
    from PIL import Image
    (tmp_path / "pics" / "insert").mkdir(parents=True)
    Image.new("RGB", (900, 1200), "white").save(tmp_path / "pics" / "insert" / "s1.jpg")
    Image.new("RGB", (64, 64), "white").save(tmp_path / "pics" / "insert" / "k.jpg")
    (tmp_path / "index.html").write_text("""<html><body><h1>İnsert 21-27 Ekim</h1><div id=r></div><script>
      setTimeout(()=>{for (const s of ['s1','k']) {const i=document.createElement('img'); i.src='/pics/insert/'+s+'.jpg';
      document.getElementById('r').appendChild(i);}}, 200);</script></body></html>""", encoding="utf-8")

    class H(http.server.SimpleHTTPRequestHandler):
        def __init__(self, *a, **k):
            super().__init__(*a, directory=str(tmp_path), **k)

        def log_message(self, *a):
            pass
    srv = http.server.ThreadingHTTPServer(("127.0.0.1", 0), H)
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    try:
        url = f"http://127.0.0.1:{srv.server_port}/index.html"
        html, responses = render_page(url, "test")
        src = StoreSource("t", "t", [url], hosts=("127.0.0.1",))
        from firsat360.connectors.discovery import Found, merge_rendered
        f = merge_rendered(Found(), html, [dict(r, url=r["url"].replace(f":{srv.server_port}", "")) for r in responses],
                           url, src)
        assert any(u.endswith("/pics/insert/s1.jpg") for u in f.images)
        assert not any(u.endswith("/pics/insert/k.jpg") for u in f.images)  # küçük görsel broşür sayfası değil
    finally:
        srv.shutdown()


def test_html_error_page_is_not_saved_as_brochure_page():
    client = _client({SRC.urls[0]: (200, "<h1>İnsert</h1><img src='/pics/insert/s1.jpg'><img src='/pics/insert/bozuk.jpg'>")})
    res = DiscoveryConnector(SRC, renderer=lambda u, ua: ("", [])).fetch(client, NOW)
    assert [p["url"] for p in res.catalogs[0].pages] == ["https://hakmar.com.tr/pics/insert/s1.jpg"]
