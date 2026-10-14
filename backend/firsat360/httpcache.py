"""Koşullu HTTP isteği önbelleği (ETag / Last-Modified).

Kaynak sunucu doğrulayıcı (ETag veya Last-Modified) gönderiyorsa yanıt gövdesi sınırlı bir disk önbelleğine yazılır.
Sonraki çalıştırmada istek If-None-Match / If-Modified-Since ile yapılır; sunucu 304 dönerse içerik yeniden
indirilmez, önbellekteki gövde kullanılır. Doğrulayıcı göndermeyen kaynaklar için yalnızca içerik özeti tutulur.

Disk önbelleği toplam boyutu sınırlıdır (varsayılan 40 MB); sınır aşılınca en uzun süredir kullanılmayan dosyalar silinir.
"""
from __future__ import annotations

import hashlib
import logging
import os
from datetime import datetime, timezone
from pathlib import Path

from sqlalchemy.orm import sessionmaker

from .db import HttpCache

log = logging.getLogger("firsat360.httpcache")


class ConditionalCache:
    def __init__(self, sf: sessionmaker | None, directory: str | Path | None, max_bytes: int = 40 * 1024 * 1024):
        self.sf = sf
        self.dir = Path(directory) if directory else None
        self.max_bytes = max_bytes
        self.hits = 0      # 304 ile indirilmeyen yanıt sayısı
        self.stored = 0
        if self.dir:
            self.dir.mkdir(parents=True, exist_ok=True)
            self.cleanup_partial()

    @staticmethod
    def key_name(key: str) -> str:
        return hashlib.sha256(key.encode("utf-8")).hexdigest()[:40]

    def _path(self, key: str) -> Path | None:
        return self.dir / self.key_name(key) if self.dir else None

    def headers_for(self, key: str) -> dict[str, str]:
        """Önbellekte gövdesi olan kayıt için koşullu istek başlıkları."""
        if self.sf is None:
            return {}
        p = self._path(key)
        if p is None or not p.exists():
            return {}
        with self.sf() as s:
            row = s.get(HttpCache, key)
            if row is None:
                return {}
            h = {}
            if row.etag:
                h["If-None-Match"] = row.etag
            if row.last_modified:
                h["If-Modified-Since"] = row.last_modified
            return h

    def body_for(self, key: str) -> bytes | None:
        p = self._path(key)
        if p is None or not p.exists():
            return None
        try:
            os.utime(p)  # LRU: son kullanım
            return p.read_bytes()
        except OSError:
            return None

    def not_modified(self, key: str) -> None:
        self.hits += 1
        if self.sf is None:
            return
        with self.sf() as s:
            row = s.get(HttpCache, key)
            if row is not None:
                row.checked_at = datetime.now(timezone.utc)
                row.not_modified_count = (row.not_modified_count or 0) + 1
                s.commit()

    def store(self, key: str, content: bytes, etag: str | None, last_modified: str | None) -> None:
        if self.sf is None:
            return
        digest = hashlib.sha256(content).hexdigest()
        with self.sf() as s:
            row = s.get(HttpCache, key) or HttpCache(url=key)
            row.etag, row.last_modified = etag, last_modified
            row.content_sha256 = digest
            row.checked_at = datetime.now(timezone.utc)
            s.merge(row)
            s.commit()
        p = self._path(key)
        if p is None or not (etag or last_modified):
            return  # doğrulayıcı yok → gövdeyi saklamanın faydası yok
        tmp = p.with_suffix(".part")
        try:
            tmp.write_bytes(content)
            tmp.replace(p)
            self.stored += 1
        except OSError as exc:
            log.warning("Önbelleğe yazılamadı: %s", exc.__class__.__name__)
            tmp.unlink(missing_ok=True)
        self.trim()

    def trim(self) -> int:
        """Toplam boyut sınırını aşan en eski (en az kullanılan) dosyaları siler. Silinen dosya sayısını döner."""
        if not self.dir:
            return 0
        files = [f for f in self.dir.iterdir() if f.is_file() and not f.name.endswith(".part")]
        total = sum(f.stat().st_size for f in files)
        removed = 0
        for f in sorted(files, key=lambda f: f.stat().st_mtime):
            if total <= self.max_bytes:
                break
            total -= f.stat().st_size
            f.unlink(missing_ok=True)
            removed += 1
        return removed

    def cleanup_partial(self) -> int:
        """Yarım kalmış yazımları (.part) siler."""
        if not self.dir:
            return 0
        n = 0
        for f in self.dir.glob("*.part"):
            f.unlink(missing_ok=True)
            n += 1
        return n

    def size_bytes(self) -> int:
        return sum(f.stat().st_size for f in self.dir.iterdir() if f.is_file()) if self.dir else 0
