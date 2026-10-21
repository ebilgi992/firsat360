"""Onur Market resmî insertleri (kurumsal.onurmarket.com).

Kök neden (21.10.2026): bölge sayfası (?region=istanbul) insertleri GÖSTERMİYOR; insertler kampanya sayfasında
(?region=istanbul&campaign=istanbul-genel-insert) yayımlanıyor. Orada her insert için resmî kapak görseli
(/pics/inserts/<no>_<ad>.jpg) ve resmî PDF (/download/<no>_<ad>..pdf) var. Bu bağlayıcı:
  1. Her bölge sayfasındaki campaign= bağlantılarını bulur (bulamazsa "<bölge>-genel-insert" kalıbını dener),
  2. Kampanya sayfasında kapak + PDF çiftlerini insert numarasıyla eşler, tarihi insertin metninden okur,
  3. PDF'nin gerçekten PDF olduğunu (HEAD: 200 + application/pdf) doğrular; hata sayfası katalog sayılmaz,
  4. Süresi dolmuş insertleri almaz.
"""
from __future__ import annotations

import re
from datetime import datetime
from urllib.parse import urljoin

from ..normalize import clean, parse_tr_range
from .base import CatalogData, Connector, ConnectorError, ConnectorResult, PoliteClient
from .htmlutil import parse, text_of

BASE = "https://kurumsal.onurmarket.com"
INSERTS = BASE + "/tr/kampanyalar/insertlerimiz"
REGIONS = ("istanbul", "bursa", "trakya")
CAMPAIGN_RE = re.compile(r"campaign=([a-z0-9-]+)", re.I)
COVER_RE = re.compile(r"/pics/inserts/(\d+)_([^\"'\s<>?]+?)\.(?:jpe?g|png|webp)", re.I)
PDF_RE = re.compile(r"/download/(\d+)_([^\"'\s<>?]+?)\.?\.pdf", re.I)


def campaign_urls(html: str, region: str) -> list[str]:
    raw = html.replace("&amp;", "&")
    found = list(dict.fromkeys(m.group(1) for m in CAMPAIGN_RE.finditer(raw)))
    if not found:
        found = [f"{region}-genel-insert"]
    return [f"{INSERTS}?region={region}&campaign={c}" for c in found]


def parse_campaign(html: str, page_url: str, region: str, observed) -> list[CatalogData]:
    """Kampanya sayfasındaki insertler: numara → kapak, PDF, başlık, tarih."""
    raw = html.replace("&amp;", "&")
    covers = {m.group(1): urljoin(BASE, m.group(0)) for m in COVER_RE.finditer(raw)}
    pdfs = {m.group(1): urljoin(BASE, m.group(0)) for m in PDF_RE.finditer(raw)}
    tree = parse(html)
    out = []
    for no in sorted(set(covers) | set(pdfs), key=lambda x: -int(x)):
        # İnsertin kartı: kapak görselini içeren en yakın, metni olan üst öğe → başlık ve tarih
        title, text = None, ""
        for img in tree.css("img"):
            if f"/pics/inserts/{no}_" in (img.attributes.get("src") or "") + (img.attributes.get("data-src") or ""):
                node = img.parent
                for _ in range(5):
                    if node is None:
                        break
                    t = text_of(node)
                    if re.search(r"\d", t) and len(t) > 8:
                        text = t
                        break
                    node = node.parent
                title = clean(img.attributes.get("alt")) or None
                break
        slug = (COVER_RE.search(covers.get(no, "")) or PDF_RE.search(pdfs.get(no, "")))
        title = title or (slug.group(2).replace("-", " ").strip().title() if slug else f"Insert {no}")
        start, end = parse_tr_range(text or title, observed)
        if end is not None and end < observed:
            continue
        out.append(CatalogData(id=f"onur-{region}-{no}", title=f"{title} · {region.title()}", kind="pdf",
                               official_url=page_url, valid_from=start, valid_to=end,
                               pdf_url=pdfs.get(no), cover_url=covers.get(no),
                               pages=[{"url": covers[no], "thumb_url": covers[no], "page": 1, "verified": True}]
                               if covers.get(no) and not pdfs.get(no) else []))
    return out


class OnurConnector(Connector):
    store_id = "onur"
    method = "live_html"

    def fetch(self, client: PoliteClient, observed_at: datetime) -> ConnectorResult:
        res = ConnectorResult(store_id=self.store_id, method=self.method, observed_at=observed_at)
        seen_pdf: set[str] = set()
        for region in REGIONS:
            try:
                urls = campaign_urls(client.get(f"{INSERTS}?region={region}").text, region)
            except ConnectorError as exc:
                res.warnings.append(f"{region}: {exc}")
                res.partial = True
                continue
            for u in urls[:4]:
                try:
                    cats = parse_campaign(client.get(u).text, u, region, observed_at.date())
                except ConnectorError as exc:
                    res.warnings.append(f"{u}: {exc}")
                    res.partial = True
                    continue
                for c in cats:
                    if c.pdf_url:
                        if c.pdf_url in seen_pdf:
                            continue  # aynı insert birden çok bölgede: tek kayıt
                        r = client.head(c.pdf_url)
                        if r is None or r.status_code != 200 or "pdf" not in r.headers.get("content-type", "").lower():
                            res.warnings.append(f"PDF doğrulanamadı ({getattr(r, 'status_code', 'yok')}): {c.pdf_url}")
                            c.pdf_url = None
                            if not c.cover_url:
                                continue
                            c.kind = "aktuel"
                            c.pages = [{"url": c.cover_url, "thumb_url": c.cover_url, "page": 1, "verified": True}]
                        else:
                            seen_pdf.add(c.pdf_url)
                    res.catalogs.append(c)
        if not res.catalogs:
            raise ConnectorError("Onur Market: kampanya sayfalarında güncel insert bulunamadı")
        return res
