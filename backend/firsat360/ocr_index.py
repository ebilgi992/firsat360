"""Broşür sayfalarında metin arama dizini (OCR) → page_index.json.

Ne yapar:
- feed.json'da yayımlanan katalogların sayfa GÖRSELLERİNİ (yalnızca sunucu tarafında okunan, istemci köprüsünde
  OLMAYAN mağazalar) kibar istemciyle indirir, sihirli baytlarla gerçekten görsel olduğunu doğrular, RapidOCR ile okur.
- Kelimeleri uygulamanın arama anahtarıyla aynı biçimde normalleştirir (Türkçe küçük harf + ç→c ğ→g ı→i ö→o ş→s ü→u),
  en az 3 harfli, tekil kelimeleri tutar; salt sayıları ve fiyat benzeri parçaları ATAR.
- Sonucu görsel adresi + içerik özeti (SHA-256) ile önbelleğe yazar: her sayfa bir kez OCR'lanır.
- feed.json'un yanına küçük bir page_index.json yazar:
  {"generated_at": ..., "pages": {"<katalog_id>": [[sayfa_no, "kelime kelime ..."], ...]}}

Ne YAPMAZ: OCR metninden fiyat, ürün veya kampanya bilgisi ÇIKARMAZ ve yayımlamaz. Bu yalnızca "bu kelime bu sayfada
geçiyor olabilir" diyen bir metin dizinidir; kullanıcı her zaman resmî broşür sayfasının kendisini görür.

İsteğe bağlıdır: OCR paketi kurulu değilse yeni sayfa okunmaz (önbellekteki sonuçlarla dizin yine yazılır); hiçbir hata
günlük çalıştırmayı durdurmaz. Ortam değişkenleri:
  OCR_INDEX=0             adımı tamamen kapatır
  OCR_CACHE_PATH          önbellek dosyası (varsayılan: HTTP_CACHE_DIR'in yanında ocr-cache.json → CI'da state/)
  OCR_MAX_PAGES=150       bir çalıştırmada en fazla bu kadar YENİ sayfa indirilir/okunur
  OCR_TIME_BUDGET_S=600   yeni sayfa okumaya ayrılan en uzun süre (saniye)
"""
from __future__ import annotations

import hashlib
import json
import logging
import os
import re
import time
import unicodedata
from datetime import datetime, timedelta, timezone
from pathlib import Path
from typing import Callable, Iterable

from .normalize import tr_lower

log = logging.getLogger("firsat360.ocr_index")

INDEX_NAME = "page_index.json"
CACHE_VERSION = 1
MIN_WORD_LEN = 3
MAX_WORD_LEN = 40
MAX_WORDS_PER_PAGE = 600
MIN_SCORE = 0.5                      # OCR güveni bunun altındaki satırlar atlanır
MAX_IMAGE_BYTES = 15 * 1024 * 1024   # sayfa görseli üst sınırı (PoliteClient'ın genel 30 MB sınırından sıkı)
MAX_PIXELS = 40_000_000              # piksel bombası koruması
MAX_SIDE = 2400                      # OCR öncesi uzun kenar küçültülür (süre sınırı)
IMAGE_KINDS = ("jpeg", "png", "webp")
FAIL_RETRY_DAYS = 3                  # indirilemeyen/okunamayan sayfa bu kadar gün sonra yeniden denenir
CACHE_KEEP_DAYS = 45                 # bu süre feed'de görülmeyen önbellek kayıtları silinir

# İstemci köprüsündeki mağazalar (connectors.CLIENT_BRIDGED ile aynı; içe aktarılamazsa bu liste kullanılır).
# Bu mağazaların broşürü sunucuda KAZINMAZ → OCR da yapılmaz.
_BRIDGED_FALLBACK = ("a101", "sok", "migros", "gratis", "carrefoursa", "rossmann")

# Uygulamanın (TurkishText.fold / SearchEngine.loose) ve normalize.fold'un katlama tablosu
_FOLD = str.maketrans({"ç": "c", "ğ": "g", "ı": "i", "ö": "o", "ş": "s", "ü": "u", "â": "a", "î": "i", "û": "u"})
_TOKEN = re.compile(r"[a-z0-9]+")
_PRICE_LIKE = re.compile(r"^\d+(tl|try|lira|kr|kurus|krs)$")

Engine = Callable[[bytes], list]  # görsel baytları → [(metin, güven), ...]


class EngineUnavailable(RuntimeError):
    """OCR modeli yüklenemedi: sayfa hatası sayılmaz (önbelleğe "hatalı" yazılmaz), yeni okuma durur."""


# ---------------------------------------------------------------- normalleştirme

def fold_text(text: str) -> str:
    """Türkçe küçük harf + aksan katlama (uygulamadaki arama anahtarıyla aynı). Diğer aksanlar (é, ñ) da atılır."""
    t = tr_lower(text or "").translate(_FOLD)
    t = unicodedata.normalize("NFKD", t)
    return "".join(ch for ch in t if not unicodedata.combining(ch))


def normalize_words(texts: Iterable[str]) -> list[str]:
    """OCR satırlarından aranabilir kelimeler: tekil, ≥3 karakter, en az bir harf; sayı/fiyat parçaları atılır.
    İlk görülme sırası korunur (sayfadaki okuma sırası)."""
    seen: set[str] = set()
    out: list[str] = []
    for text in texts:
        for tok in _TOKEN.findall(fold_text(str(text or ""))):
            if len(tok) < MIN_WORD_LEN or len(tok) > MAX_WORD_LEN:
                continue
            if tok.isdigit() or not any(c.isalpha() for c in tok) or _PRICE_LIKE.match(tok):
                continue
            if tok in seen:
                continue
            seen.add(tok)
            out.append(tok)
            if len(out) >= MAX_WORDS_PER_PAGE:
                return out
    return out


# ---------------------------------------------------------------- OCR motoru

def _prepare_image(data: bytes):
    """Baytları güvenle çöz → BGR numpy dizisi (büyükse küçültülür). Pillow/numpy OCR paketiyle birlikte gelir."""
    import io

    import numpy as np
    from PIL import Image

    with Image.open(io.BytesIO(data)) as im:
        w, h = im.size
        if w * h > MAX_PIXELS:
            raise ValueError(f"görsel çok büyük ({w}x{h})")
        im = im.convert("RGB")
        if max(w, h) > MAX_SIDE:
            s = MAX_SIDE / max(w, h)
            im = im.resize((max(1, int(w * s)), max(1, int(h * s))))
        arr = np.asarray(im)
    return np.ascontiguousarray(arr[:, :, ::-1])


def load_engine() -> tuple[Engine | None, str]:
    """(motor, ad). Tercih: `rapidocr` (PP-OCRv6, Türkçe tanıma 'tr'), yoksa `rapidocr_onnxruntime` (varsayılan
    Çince+Latin modeli: Türkçe harfleri çoğunlukla ç→c gibi düz harfle okur; katlama sonrası arama yine eşleşir).
    Paket yoksa (None, neden)."""
    for name in ("rapidocr", "rapidocr_onnxruntime"):
        try:
            if name == "rapidocr":
                from rapidocr import RapidOCR  # type: ignore
                logging.getLogger("RapidOCR").setLevel(logging.WARNING)
                eng = RapidOCR(params={"Rec.lang_type": "tr"})
                label = "rapidocr/PP-OCRv6 tr"

                def run(data: bytes, _e=eng):
                    r = _e(_prepare_image(data))
                    return list(zip(r.txts or (), r.scores or ()))
            else:
                from rapidocr_onnxruntime import RapidOCR  # type: ignore
                eng = RapidOCR()
                label = "rapidocr_onnxruntime/varsayilan"

                def run(data: bytes, _e=eng):
                    res, _ = _e(_prepare_image(data))
                    return [(x[1], float(x[2])) for x in (res or [])]
            return run, label
        except ImportError:
            continue
        except Exception as exc:  # noqa: BLE001 — ör. 'tr' modeli yok (eski sürüm): sıradaki motoru dene
            log.warning("OCR motoru %s yüklenemedi: %s", name, exc)
            continue
    return None, "OCR paketi kurulu değil (pip install -r requirements-ocr.txt)"


def ocr_words(engine: Engine, data: bytes) -> list[str]:
    lines = [t for t, s in engine(data) if t and (s is None or float(s) >= MIN_SCORE)]
    return normalize_words(lines)


# ---------------------------------------------------------------- önbellek

def default_cache_path() -> Path:
    if os.getenv("OCR_CACHE_PATH"):
        return Path(os.environ["OCR_CACHE_PATH"])
    from .config import Settings
    return Path(Settings().http_cache_dir).parent / "ocr-cache.json"


def load_cache(path: Path) -> dict:
    try:
        data = json.loads(Path(path).read_text(encoding="utf-8"))
        if isinstance(data, dict) and isinstance(data.get("by_url"), dict):
            return data
    except (OSError, ValueError):
        pass
    return {"version": CACHE_VERSION, "by_url": {}}


def save_cache(path: Path, cache: dict, today: str) -> None:
    keep_after = (datetime.fromisoformat(today) - timedelta(days=CACHE_KEEP_DAYS)).date().isoformat()
    cache["by_url"] = {u: e for u, e in cache["by_url"].items() if str(e.get("seen", today)) >= keep_after}
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(cache, ensure_ascii=False, separators=(",", ":"), sort_keys=True), encoding="utf-8")
    tmp.replace(p)


# ---------------------------------------------------------------- hangi sayfalar

def bridged_stores(feed: dict) -> set[str]:
    try:
        from .connectors import CLIENT_BRIDGED
        base = set(CLIENT_BRIDGED)
    except Exception:  # noqa: BLE001
        base = set()
    return base | set(_BRIDGED_FALLBACK) | set((feed.get("client_bridge") or {}).keys())


def page_targets(feed: dict) -> list[tuple[str, int, str]]:
    """(katalog_id, sayfa_no, görsel_adresi) — yalnızca yayındaki, köprüde OLMAYAN mağazaların sayfa görselleri.
    Sıra: önce her kataloğun ilk sayfaları (bütçe yetmezse daha çok katalog aranabilir olsun), yeni kataloglar önde."""
    excluded = bridged_stores(feed)
    cats = [c for c in (feed.get("catalogs") or []) if isinstance(c, dict) and c.get("id")
            and c.get("store_id") not in excluded and c.get("data_mode", "real") == "real"]
    cats.sort(key=lambda c: (c.get("first_seen_at") or c.get("valid_from") or ""), reverse=True)
    out: list[tuple[int, int, str, int, str]] = []
    for rank, c in enumerate(cats):
        for i, p in enumerate(c.get("pages") or []):
            url = p.get("url") if isinstance(p, dict) else None
            if not isinstance(url, str) or not url.startswith(("https://", "http://")):
                continue
            no = p.get("page") if isinstance(p.get("page"), int) and p.get("page") > 0 else i + 1
            out.append((i, rank, c["id"], no, url))
    out.sort(key=lambda t: (t[0], t[1]))
    return [(cid, no, url) for _, _, cid, no, url in out]


# ---------------------------------------------------------------- dizin

def build_index(feed: dict, cache: dict, *, fetch: Callable[[str], bytes] | None, engine: Engine | None,
                today: str, max_new: int = 150, time_budget_s: float = 600.0,
                clock: Callable[[], float] = time.monotonic) -> tuple[dict, dict]:
    """(sayfalar, istatistik). fetch(url) → doğrulanmış görsel baytları (hata fırlatabilir). fetch/engine None ise
    yalnızca önbellek kullanılır. Hiçbir sayfa hatası dışarı sızmaz."""
    by_url: dict = cache.setdefault("by_url", {})
    by_hash = {e["h"]: e["w"] for e in by_url.values() if e.get("h") and "w" in e}
    stats = {"targets": 0, "cached": 0, "new": 0, "failed": 0, "skipped_budget": 0, "dedup_hash": 0}
    pages: dict[str, list] = {}
    start = clock()
    attempted = 0
    retry_before = (datetime.fromisoformat(today) - timedelta(days=FAIL_RETRY_DAYS)).date().isoformat()
    for cid, no, url in page_targets(feed):
        stats["targets"] += 1
        entry = by_url.get(url)
        words: str | None = None
        if entry is not None and "w" in entry:
            words = entry["w"]
            entry["seen"] = today
            stats["cached"] += 1
        elif entry is not None and entry.get("err") and str(entry.get("at", "")) > retry_before:
            entry["seen"] = today
            stats["failed"] += 1
        elif fetch is None or engine is None:
            pass
        elif attempted >= max_new or clock() - start > time_budget_s:
            stats["skipped_budget"] += 1
        else:
            attempted += 1
            try:
                data = fetch(url)
                h = hashlib.sha256(data).hexdigest()[:24]
                if h in by_hash:
                    words = by_hash[h]
                    stats["dedup_hash"] += 1
                else:
                    words = " ".join(ocr_words(engine, data))
                    by_hash[h] = words
                    stats["new"] += 1
                by_url[url] = {"h": h, "w": words, "at": today, "seen": today}
            except EngineUnavailable as exc:
                log.warning("OCR motoru kullanılamıyor, yeni sayfa okunmayacak: %s", exc)
                stats["engine_error"] = str(exc)[:200]
                engine = None
            except Exception as exc:  # noqa: BLE001 — tek sayfa hatası dizini durdurmaz
                stats["failed"] += 1
                by_url[url] = {"err": f"{exc.__class__.__name__}: {str(exc)[:120]}", "at": today, "seen": today}
                log.info("OCR atlandı %s: %s", url, exc)
        if words:
            pages.setdefault(cid, []).append([no, words])
    for v in pages.values():
        v.sort(key=lambda x: x[0])
    stats["seconds"] = round(clock() - start, 1)
    return pages, stats


def make_fetcher(client) -> Callable[[str], bytes]:
    """PoliteClient ile indir (robots.txt, hız sınırı, SSRF koruması, yeniden deneme); sihirli bayt + boyut kontrolü."""
    from .connectors.base import ConnectorError, sniff

    def fetch(url: str) -> bytes:
        resp = client.get(url)
        data = resp.content
        if len(data) > MAX_IMAGE_BYTES:
            raise ConnectorError(f"görsel çok büyük ({len(data) // 1048576} MB)")
        kind = sniff(data[:16])
        if kind not in IMAGE_KINDS:
            raise ConnectorError(f"görsel değil ({kind})")
        return data
    return fetch


def write_index(path: Path, pages: dict, now: datetime) -> Path:
    p = Path(path)
    p.parent.mkdir(parents=True, exist_ok=True)
    doc = {"generated_at": now.isoformat(), "pages": pages}
    tmp = p.with_suffix(".tmp")
    tmp.write_text(json.dumps(doc, ensure_ascii=False, separators=(",", ":")), encoding="utf-8")
    tmp.replace(p)
    return p


def run_after_feed(settings, feed_path: str | Path | None, *, transport=None, sleep=None, live: bool = True,
                   engine: Engine | None = None, now: datetime | None = None,
                   cache_path: str | Path | None = None) -> dict:
    """Günlük işin son adımı (feed yazıldıktan sonra). Asla hata fırlatmaz; özet sözlüğü döner."""
    if os.getenv("OCR_INDEX", "1").strip().lower() in ("0", "false", "no", "off"):
        return {"state": "disabled"}
    client = None
    try:
        if not feed_path or not Path(feed_path).exists():
            return {"state": "skipped", "reason": "feed.json yok"}
        now = now or datetime.now(timezone.utc)
        today = now.date().isoformat()
        feed = json.loads(Path(feed_path).read_text(encoding="utf-8"))
        cpath = Path(cache_path) if cache_path else default_cache_path()
        cache = load_cache(cpath)
        fetch = None
        if live:
            if engine is None:
                engine = _lazy_engine()
            if engine is not None:
                from .connectors.base import PoliteClient
                kw = {"sleep": sleep} if sleep is not None else {}
                client = PoliteClient(settings.user_agent, settings.request_timeout_s, settings.min_request_interval_s,
                                      transport=transport, **kw)
                fetch = make_fetcher(client)
        pages, stats = build_index(feed, cache, fetch=fetch, engine=engine, today=today,
                                   max_new=int(os.getenv("OCR_MAX_PAGES", "150")),
                                   time_budget_s=float(os.getenv("OCR_TIME_BUDGET_S", "600")))
        save_cache(cpath, cache, today)
        engine_name = getattr(engine, "name", "stub") if engine is not None else "yok (yalnızca önbellek)"
        out = write_index(Path(feed_path).with_name(INDEX_NAME), pages, now)
        return {"state": "ok", "path": str(out), "engine": engine_name,
                "catalogs": len(pages), "pages": sum(len(v) for v in pages.values()), **stats}
    except Exception as exc:  # noqa: BLE001 — OCR dizini günlük yayını asla durdurmaz
        log.warning("OCR sayfa dizini başarısız: %s", exc)
        return {"state": "error", "error": f"{exc.__class__.__name__}: {str(exc)[:200]}"}
    finally:
        if client is not None:
            client.close()


class _LazyEngine:
    """Modeli yalnızca gerçekten okunacak yeni bir sayfa geldiğinde yükler (önbellekten dönen çalıştırmada maliyet yok)."""

    def __init__(self):
        self._engine: Engine | None = None
        self.name = "rapidocr"
        self.failed: str | None = None

    def __call__(self, data: bytes):
        if self._engine is None:
            if self.failed:
                raise EngineUnavailable(self.failed)
            self._engine, name = load_engine()
            if self._engine is None:
                self.failed = name
                raise EngineUnavailable(name)
            self.name = name
        return self._engine(data)


def _lazy_engine() -> "_LazyEngine | None":
    """Paket kurulu mu? (model yüklenmeden, yalnızca içe aktarma denetimi)."""
    import importlib.util
    if importlib.util.find_spec("rapidocr") is None and importlib.util.find_spec("rapidocr_onnxruntime") is None:
        log.info("OCR paketi kurulu değil; yalnızca önbellekteki sayfalar dizine girer")
        return None
    return _LazyEngine()
