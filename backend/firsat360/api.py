"""İsteğe bağlı salt okunur JSON API (FastAPI). Varsayılan yayın yolu değildir: uygulama ücretsiz GitHub Pages'teki
feed.json'u okur. Kendi sunucunuzda (VPS) canlı sorgu isterseniz: ``python -m firsat360 serve``.

Uçlar (hepsi GET, kimlik doğrulama gerektirmez, yazma yok):
  /health                    → {"ok": true, "stores": n}
  /catalogs?store=bim&active=true&limit=50
                             → güncel/yaklaşan kataloglar: mağaza, başlık, geçerlilik, sayfa görselleri, PDF, kaynak
  /catalogs/{id}             → tek katalog
"""
from __future__ import annotations

import json
from datetime import date, datetime, timezone

from fastapi import FastAPI, HTTPException, Query
from fastapi.middleware.gzip import GZipMiddleware
from sqlalchemy import or_, select
from sqlalchemy.orm import sessionmaker

from .db import Catalog, Store


def _today() -> date:
    from .sync import TR
    return datetime.now(timezone.utc).astimezone(TR).date()


def catalog_json(c: Catalog, store_name: str | None) -> dict:
    pages = json.loads(c.pages_json) if c.pages_json else []
    return {"id": c.id, "store_id": c.store_id, "store": store_name, "title": c.title, "kind": c.kind,
            "valid_from": c.valid_from.isoformat() if c.valid_from else None,
            "valid_to": c.valid_to.isoformat() if c.valid_to else None,
            "cover_url": c.cover_url, "pdf_url": c.pdf_url, "official_url": c.official_url,
            "pages": [{"page": p.get("page"), "url": p.get("url"), "thumb_url": p.get("thumb_url")} for p in pages],
            "last_checked_at": c.last_checked_at.isoformat() if c.last_checked_at else None}


def create_app(sf: sessionmaker) -> FastAPI:
    app = FastAPI(title="FIRSAT360 katalog API", version="0.8", docs_url="/docs", redoc_url=None)
    app.add_middleware(GZipMiddleware, minimum_size=1024)

    @app.get("/health")
    def health():
        with sf() as s:
            return {"ok": True, "stores": len(s.scalars(select(Store.id)).all())}

    @app.get("/catalogs")
    def catalogs(store: str | None = Query(None, max_length=32), active: bool = True,
                 limit: int = Query(50, ge=1, le=200)):
        today = _today()
        with sf() as s:
            q = select(Catalog).where(Catalog.kind != "link_only")
            if store:
                q = q.where(Catalog.store_id == store)
            if active:  # süresi dolmamış (bugün geçerli veya yaklaşan)
                q = q.where(or_(Catalog.valid_to.is_(None), Catalog.valid_to >= today))
            q = q.order_by(Catalog.valid_from.desc().nullslast()).limit(limit)
            names = {st.id: st.name for st in s.scalars(select(Store))}
            return {"date": today.isoformat(), "catalogs": [catalog_json(c, names.get(c.store_id)) for c in s.scalars(q)]}

    @app.get("/catalogs/{cid}")
    def one(cid: str):
        with sf() as s:
            c = s.get(Catalog, cid)
            if c is None:
                raise HTTPException(404, "katalog bulunamadı")
            st = s.get(Store, c.store_id)
            return catalog_json(c, st.name if st else None)

    return app
