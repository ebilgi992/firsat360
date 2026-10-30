"""OCR sayfa dizini: normalleştirme, dizin oluşturma, köprü mağazalarının dışlanması, hata dayanıklılığı.
Gerçek OCR modeli yüklenmez: motor sahte (stub)."""
import json
from datetime import datetime, timezone

import httpx

from firsat360 import ocr_index as oi
from firsat360.ocr_index import build_index, normalize_words, page_targets, run_after_feed

NOW = datetime(2026, 10, 30, 9, 0, tzinfo=timezone.utc)
TODAY = "2026-10-30"
JPEG = b"\xff\xd8\xff\xe0" + b"\x00" * 64


def _feed():
    # "yenikopru": statik listede yok, yalnızca feed'in client_bridge alanında → yine dışlanmalı
    return {
        "client_bridge": {"gratis": {"url": "https://www.gratis.com/gratis-katalog"},
                          "yenikopru": {"url": "https://x.example.com"}},
        "catalogs": [
            {"id": "bim-1", "store_id": "bim", "first_seen_at": "2026-10-29",
             "pages": [{"url": "https://cdn.example.com/bim/1.jpg", "page": 1},
                       {"url": "https://cdn.example.com/bim/2.jpg", "page": 2}]},
            {"id": "hakmar-1", "store_id": "hakmar", "first_seen_at": "2026-10-28",
             "pages": [{"url": "https://cdn.example.com/hakmar/1.jpg"}]},
            # İstemci köprüsündeki mağazalar: asla indirilmez/OCR'lanmaz
            *[{"id": f"{s}-1", "store_id": s, "pages": [{"url": f"https://cdn.example.com/{s}/1.jpg"}]}
              for s in ("a101", "sok", "migros", "gratis", "carrefoursa", "rossmann")],
            {"id": "yeni-kopru-1", "store_id": "yenikopru", "pages": [{"url": "https://cdn.example.com/yk/1.jpg"}]},
        ],
    }


TEXTS = {
    "https://cdn.example.com/bim/1.jpg": [("TAM YAĞLI SÜT 1 L", 0.98), ("49,90 TL", 0.99), ("Kaşar Peyniri", 0.9)],
    "https://cdn.example.com/bim/2.jpg": [("ŞAMPUAN 400 ml", 0.95), ("İNDİRİM", 0.97), ("bulanık", 0.2)],
    "https://cdn.example.com/hakmar/1.jpg": [("Çay Bardağı 6'lı", 0.9), ("Süt", 0.9)],
}


def stub_engine_for(url_of_data):
    calls = []

    def engine(data: bytes):
        calls.append(data)
        return TEXTS.get(url_of_data[data], [])
    engine.calls = calls
    return engine


def _fetch_and_engine(fail: set | None = None):
    """fetch(url) → url'ye özgü baytlar; motor baytlardan url'yi bulup sahte metni döndürür."""
    fail = fail or set()
    data_of = {u: JPEG + u.encode() for u in TEXTS}
    url_of = {v: k for k, v in data_of.items()}
    fetched = []

    def fetch(url):
        fetched.append(url)
        if url in fail:
            raise httpx.ConnectError("bağlantı yok")
        return data_of.get(url, JPEG + url.encode())
    return fetch, stub_engine_for(url_of), fetched


# ---------------------------------------------------------------- normalleştirme

def test_normalize_turkish_fold_and_filters():
    w = normalize_words(["TAM YAĞLI SÜT 1 L", "49,90 TL", "İNDİRİM ışık", "Çay Bardağı 6'lı", "Süt", "120TL 99tl",
                         "500gr Café", "ab 12345 x"])
    assert w == ["tam", "yagli", "sut", "indirim", "isik", "cay", "bardagi", "500gr", "cafe"]


def test_normalize_turkish_dotted_capital_i():
    # Türkçe: I → ı → i, İ → i (her ikisi de katlanmış 'i')
    assert normalize_words(["IŞIK", "İZMİR"]) == ["isik", "izmir"]


def test_normalize_drops_pure_numbers_and_prices():
    assert normalize_words(["1.299,00", "₺ 45", "100tl", "999,95TL", "2026"]) == []


# ---------------------------------------------------------------- hedef sayfalar

def test_bridged_stores_are_excluded():
    targets = page_targets(_feed())
    stores = {cid.split("-")[0] for cid, _, _ in targets}
    assert stores == {"bim", "hakmar"}
    assert all("a101" not in u and "/sok/" not in u and "migros" not in u and "gratis" not in u
               and "carrefoursa" not in u and "rossmann" not in u and "/yk/" not in u for _, _, u in targets)


def test_targets_first_pages_first():
    t = page_targets(_feed())
    assert [(c, n) for c, n, _ in t] == [("bim-1", 1), ("hakmar-1", 1), ("bim-1", 2)]


# ---------------------------------------------------------------- dizin

def test_build_index_and_cache_reuse():
    fetch, engine, fetched = _fetch_and_engine()
    cache = {"by_url": {}}
    feed = _feed()
    pages, stats = build_index(feed, cache, fetch=fetch, engine=engine, today=TODAY)
    assert pages == {
        "bim-1": [[1, "tam yagli sut kasar peyniri"], [2, "sampuan indirim"]],
        "hakmar-1": [[1, "cay bardagi sut"]],
    }
    assert stats["new"] == 3 and stats["failed"] == 0
    # Fiyat yok: dizinde rakam/TL parçası bulunmaz
    assert not any(ch.isdigit() for v in pages.values() for _, words in v for ch in words)
    # İkinci çalıştırma: hiçbir şey indirilmez/OCR'lanmaz
    fetched.clear()
    n_calls = len(engine.calls)
    pages2, stats2 = build_index(feed, cache, fetch=fetch, engine=engine, today=TODAY)
    assert pages2 == pages and fetched == [] and len(engine.calls) == n_calls and stats2["cached"] == 3


def test_same_content_other_url_uses_hash_cache():
    fetch, engine, _ = _fetch_and_engine()
    cache = {"by_url": {}}
    feed = {"catalogs": [{"id": "hakmar-1", "store_id": "hakmar", "pages": [{"url": "https://cdn.example.com/bim/1.jpg"}]}]}
    build_index(feed, cache, fetch=fetch, engine=engine, today=TODAY)
    # Aynı bayt içeriği başka adreste: önbellekteki özet eşleşir, motor yeniden çağrılmaz
    feed2 = {"catalogs": [{"id": "hakmar-2", "store_id": "hakmar", "pages": [{"url": "https://cdn2.example.com/x.jpg"}]}]}
    same = lambda url: JPEG + b"https://cdn.example.com/bim/1.jpg"  # noqa: E731
    pages, stats = build_index(feed2, cache, fetch=same, engine=engine, today=TODAY)
    assert stats["dedup_hash"] == 1 and len(engine.calls) == 1
    assert pages["hakmar-2"][0][1].startswith("tam yagli")


def test_budget_caps_new_pages():
    fetch, engine, fetched = _fetch_and_engine()
    pages, stats = build_index(_feed(), {"by_url": {}}, fetch=fetch, engine=engine, today=TODAY, max_new=1)
    assert len(fetched) == 1 and stats["skipped_budget"] == 2


def test_failures_never_raise_and_are_retried_later():
    bad = "https://cdn.example.com/bim/1.jpg"
    fetch, engine, _ = _fetch_and_engine(fail={bad})

    def boom(data):
        if b"hakmar" in data:
            raise ValueError("bozuk görsel")
        return engine(data)
    cache = {"by_url": {}}
    pages, stats = build_index(_feed(), cache, fetch=fetch, engine=boom, today=TODAY)
    assert stats["failed"] == 2 and list(pages) == ["bim-1"]
    assert cache["by_url"][bad]["err"].startswith("ConnectError")
    # Aynı gün yeniden denenmez; FAIL_RETRY_DAYS sonra denenir
    fetch2, engine2, fetched2 = _fetch_and_engine()
    build_index(_feed(), cache, fetch=fetch2, engine=engine2, today=TODAY)
    assert bad not in fetched2
    build_index(_feed(), cache, fetch=fetch2, engine=engine2, today="2026-11-05")
    assert bad in fetched2


def test_engine_unavailable_does_not_poison_cache():
    def missing(data):
        raise oi.EngineUnavailable("paket yok")
    fetch, _, _ = _fetch_and_engine()
    cache = {"by_url": {}}
    pages, stats = build_index(_feed(), cache, fetch=fetch, engine=missing, today=TODAY)
    assert pages == {} and cache["by_url"] == {} and "engine_error" in stats


# ---------------------------------------------------------------- günlük adım (dosyalar, ağ katmanı)

class _S:
    user_agent = "FIRSAT360Bot/test"
    request_timeout_s = 5.0
    min_request_interval_s = 0.0


def _transport(fail_all=False, seen=None):
    seen = seen if seen is not None else []

    def handler(req: httpx.Request):
        url = str(req.url)
        seen.append(url)
        if url.endswith("/robots.txt"):
            return httpx.Response(200, text="User-agent: *\nDisallow:\n")
        if fail_all or "hakmar" in url:
            return httpx.Response(200, text="<html>hata sayfası</html>", headers={"content-type": "image/jpeg"})
        return httpx.Response(200, content=JPEG + url.encode(), headers={"content-type": "image/jpeg"})
    return httpx.MockTransport(handler)


def test_run_after_feed_writes_compact_index(tmp_path, monkeypatch):
    monkeypatch.delenv("OCR_INDEX", raising=False)
    feed_path = tmp_path / "feed.json"
    feed_path.write_text(json.dumps(_feed()), encoding="utf-8")
    url_of = {JPEG + u.encode(): u for u in TEXTS}
    engine = stub_engine_for(url_of)
    seen = []
    rep = run_after_feed(_S(), feed_path, transport=_transport(seen=seen), sleep=lambda s: None, engine=engine,
                         now=NOW, cache_path=tmp_path / "state" / "ocr-cache.json")
    assert rep["state"] == "ok", rep
    assert sorted(u for u in seen if not u.endswith("robots.txt")) == [
        "https://cdn.example.com/bim/1.jpg", "https://cdn.example.com/bim/2.jpg", "https://cdn.example.com/hakmar/1.jpg"]
    doc = json.loads((tmp_path / "page_index.json").read_text(encoding="utf-8"))
    assert doc["generated_at"].startswith("2026-10-30")
    assert doc["pages"] == {"bim-1": [[1, "tam yagli sut kasar peyniri"], [2, "sampuan indirim"]]}
    assert rep["failed"] == 1  # hakmar: HTML hata sayfası (sihirli bayt) → görsel değil
    cache = json.loads((tmp_path / "state" / "ocr-cache.json").read_text(encoding="utf-8"))
    assert "görsel değil" in cache["by_url"]["https://cdn.example.com/hakmar/1.jpg"]["err"]
    # feed.json'a dokunulmaz
    assert json.loads(feed_path.read_text(encoding="utf-8")) == _feed()


def test_run_after_feed_never_raises(tmp_path, monkeypatch):
    monkeypatch.delenv("OCR_INDEX", raising=False)
    # feed yok
    assert run_after_feed(_S(), tmp_path / "yok.json")["state"] == "skipped"
    # bozuk feed
    bad = tmp_path / "feed.json"
    bad.write_text("{bozuk", encoding="utf-8")
    assert run_after_feed(_S(), bad, cache_path=tmp_path / "c.json")["state"] == "error"
    # motor her sayfada patlıyor + bozuk önbellek dosyası
    bad.write_text(json.dumps(_feed()), encoding="utf-8")
    (tmp_path / "c.json").write_text("çöp", encoding="utf-8")

    def boom(data):
        raise RuntimeError("onnx çöktü")
    rep = run_after_feed(_S(), bad, transport=_transport(), sleep=lambda s: None, engine=boom, now=NOW,
                         cache_path=tmp_path / "c.json")
    assert rep["state"] == "ok" and rep["pages"] == 0
    assert json.loads((tmp_path / "page_index.json").read_text(encoding="utf-8"))["pages"] == {}


def test_run_after_feed_disabled_and_offline(tmp_path, monkeypatch):
    feed_path = tmp_path / "feed.json"
    feed_path.write_text(json.dumps(_feed()), encoding="utf-8")
    monkeypatch.setenv("OCR_INDEX", "0")
    assert run_after_feed(_S(), feed_path)["state"] == "disabled"
    assert not (tmp_path / "page_index.json").exists()
    monkeypatch.delenv("OCR_INDEX")
    # live=False: ağ yok, yalnızca önbellek
    cache_path = tmp_path / "c.json"
    cache_path.write_text(json.dumps({"by_url": {"https://cdn.example.com/bim/2.jpg": {"h": "x", "w": "sut",
                                                                                         "seen": TODAY}}}))
    rep = run_after_feed(_S(), feed_path, live=False, now=NOW, cache_path=cache_path,
                         transport=_transport(fail_all=True))
    assert rep["state"] == "ok"
    assert json.loads((tmp_path / "page_index.json").read_text())["pages"] == {"bim-1": [[2, "sut"]]}


def test_missing_ocr_package_skips_gracefully(tmp_path, monkeypatch):
    monkeypatch.delenv("OCR_INDEX", raising=False)
    monkeypatch.setattr(oi, "_lazy_engine", lambda: None)
    feed_path = tmp_path / "feed.json"
    feed_path.write_text(json.dumps(_feed()), encoding="utf-8")
    rep = run_after_feed(_S(), feed_path, now=NOW, cache_path=tmp_path / "c.json",
                         transport=_transport(fail_all=True))
    assert rep["state"] == "ok" and rep["pages"] == 0 and rep["failed"] == 0
