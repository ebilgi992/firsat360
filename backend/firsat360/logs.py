"""Sunucu günlükleri: sınırlı boyut (varsayılan toplam 5 MB), sınırlı süre (7 gün), gizli bilgi maskeleme.

- Dosyalar LOG_DIR altında `firsat360.log`, `firsat360.log.1` … şeklinde döner (RotatingFileHandler).
- Toplam boyut sınırı aşılırsa veya dosya 7 günden eskiyse en eski dosyalar silinir.
- Erişim anahtarı / parola / Authorization başlığı gibi değerler günlüğe yazılmadan önce maskelenir.
- Temizleme hatası işi durdurmaz.
"""
from __future__ import annotations

import logging
import re
import time
from logging.handlers import RotatingFileHandler
from pathlib import Path

SECRET_PATTERNS = [
    re.compile(r"(?i)(authorization\s*[:=]\s*)(bearer\s+)?\S+"),
    re.compile(r"(?i)((?:token|password|parola|secret|api[_-]?key)\s*[:=]\s*)\S+"),
    re.compile(r"\btk_[A-Za-z0-9]{8,}\b"),          # ntfy erişim anahtarı biçimi
    re.compile(r"(?i)(postgres(?:ql)?(?:\+\w+)?://[^:/\s]+:)[^@\s]+(@)"),
]


def redact(text: str) -> str:
    for p in SECRET_PATTERNS:
        if p.groups >= 2 and "postgres" in p.pattern:
            text = p.sub(r"\1***\2", text)
        elif p.groups >= 1:
            text = p.sub(lambda m: m.group(1) + "***", text)
        else:
            text = p.sub("***", text)
    return text


class RedactFilter(logging.Filter):
    def filter(self, record: logging.LogRecord) -> bool:
        try:
            msg = record.getMessage()
        except Exception:  # noqa: BLE001
            return True
        clean = redact(msg)
        if clean != msg:
            record.msg, record.args = clean, None
        return True


def cleanup(directory: str | Path, max_bytes: int = 5 * 1024 * 1024, keep_days: int = 7,
            now: float | None = None, keep: set[str] | None = None) -> list[str]:
    """Eski ve sınırı aşan günlük dosyalarını siler. Silinenlerin adlarını döner. Hata fırlatmaz."""
    removed: list[str] = []
    try:
        d = Path(directory)
        if not d.exists():
            return removed
        now = now or time.time()
        keep = keep or set()
        files = [f for f in d.iterdir() if f.is_file()]
        for f in list(files):
            if f.name in keep:
                continue
            try:
                if now - f.stat().st_mtime > keep_days * 86400:
                    f.unlink()
                    removed.append(f.name)
                    files.remove(f)
            except OSError:
                pass
        files.sort(key=lambda f: f.stat().st_mtime)
        total = sum(f.stat().st_size for f in files)
        for f in files:
            if total <= max_bytes:
                break
            if f.name in keep:
                continue
            try:
                size = f.stat().st_size
                f.unlink()
                total -= size
                removed.append(f.name)
            except OSError:
                pass
    except Exception:  # noqa: BLE001 # nosec B110 — temizlik asla işi durdurmamalı
        return removed
    return removed


def setup(log_dir: str | Path | None, max_bytes: int = 5 * 1024 * 1024, keep_days: int = 7,
          level: int = logging.INFO) -> logging.Logger:
    root = logging.getLogger()
    root.setLevel(level)
    fmt = logging.Formatter("%(asctime)s %(levelname)s %(name)s: %(message)s")
    if not any(isinstance(h, logging.StreamHandler) and not isinstance(h, logging.FileHandler) for h in root.handlers):
        sh = logging.StreamHandler()
        sh.setFormatter(fmt)
        sh.addFilter(RedactFilter())
        root.addHandler(sh)
    if log_dir:
        d = Path(log_dir)
        try:
            d.mkdir(parents=True, exist_ok=True)
            cleanup(d, max_bytes, keep_days)
            # Dönen dosya başına sınırın 1/5'i; 4 yedek → toplam en fazla max_bytes
            fh = RotatingFileHandler(d / "firsat360.log", maxBytes=max(64 * 1024, max_bytes // 5),
                                     backupCount=4, encoding="utf-8")
            fh.setFormatter(fmt)
            fh.addFilter(RedactFilter())
            root.addHandler(fh)
        except OSError as exc:
            root.warning("Günlük klasörü kullanılamıyor (%s); yalnızca konsola yazılıyor", exc.__class__.__name__)
    for h in root.handlers:
        if not any(isinstance(f, RedactFilter) for f in h.filters):
            h.addFilter(RedactFilter())
    return root
