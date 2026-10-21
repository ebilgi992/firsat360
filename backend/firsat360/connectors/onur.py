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

BASE = "https://kurumsal.onurmarket.com"
INSERTS = BASE + "/tr/kampanyalar/insertlerimiz"
REGIONS = ("istanbul", "bursa", "trakya")
REGION_NAMES = {"istanbul": "İstanbul", "bursa": "Bursa", "trakya": "Trakya"}
CAMPAIGN_RE = re.compile(r"campaign=([a-z0-9-]+)", re.I)
# Sayfa <base href="https://kurumsal.onurmarket.com/"> kullanır; adresler göreli ("pics/inserts/…", "download/…").
COVER_RE = re.compile(r"pics/inserts/(\d+)_([^\"'\s<>?]+?)\.(?:jpe?g|png|webp)", re.I)
PDF_RE = re.compile(r"download/(\d+)_([^\"'\s<>?]+?)\.?\.pdf", re.I)
CURRENT_RE = re.compile(r"(?i)G[ÜU]NCEL\s+[İI]NSERT")
PAST_RE = re.compile(r"(?i)GE[ÇC]M[İI][ŞS]\s+[İI]NSERT")


def campaign_urls(html: str, region: str) -> list[str]:
    raw = html.replace("&amp;", "&")
    found = list(dict.fromkeys(m.group(1) for m in CAMPAIGN_RE.finditer(raw)))
    if not found:
        found = [f"{region}-genel-insert"]
    return [f"{INSERTS}?region={region}&campaign={c}" for c in found]


_SPAN = re.compile(r'<span[^>]*class="txt\s+([a-d])"[^>]*>(.*?)</span>', re.I | re.S)


def parse_campaign(html: str, page_url: str, region: str, observed) -> list[CatalogData]:
    """Kampanya sayfasındaki GÜNCEL insertler. Gerçek yapı (21.10.2026): kapak görseli, ardından bilgi kutusu
    <span class="txt a">GÜNCEL INSERT</span> <span class="txt b">İstanbul</span> <span class="txt c">13-26 Ekim 2026</span>
    <span class="txt d">Onur'da Çok Al Az Öde</span>, ardından indirme bağlantısı (download/<no>_….pdf).
    "Geçmiş İnsert" bölümündekiler alınmaz."""
    raw = html.replace("&amp;", "&")
    covers = {}
    for m in COVER_RE.finditer(raw):
        covers.setdefault(m.group(1), urljoin(BASE + "/", m.group(0)))
    marks = sorted([(m.start(), "cur") for m in CURRENT_RE.finditer(raw)] +
                   [(m.start(), "past") for m in PAST_RE.finditer(raw)])
    out, seen = [], set()
    for k, (pos, kind) in enumerate(marks):
        if kind != "cur":
            continue
        end = marks[k + 1][0] if k + 1 < len(marks) else len(raw)
        seg = raw[pos:min(end, pos + 4000)]
        m_pdf = PDF_RE.search(seg)
        if not m_pdf or m_pdf.group(1) in seen:
            continue
        no = m_pdf.group(1)
        seen.add(no)
        spans = {c: clean(re.sub(r"<[^>]+>", " ", t)) for c, t in _SPAN.findall(seg[:1500])}
        date_text = spans.get("c") or re.sub(r"<[^>]+>", " ", seg[:800])
        start, end_d = parse_tr_range(date_text, observed)
        if end_d is not None and end_d < observed:
            continue
        title = spans.get("d") or clean(m_pdf.group(2).replace("-", " ").capitalize())
        cover = covers.get(no)
        out.append(CatalogData(id=f"onur-{region}-{no}", title=f"{title} · {REGION_NAMES.get(region, region)}", kind="pdf",
                               official_url=page_url, valid_from=start, valid_to=end_d,
                               pdf_url=urljoin(BASE + "/", m_pdf.group(0)), cover_url=cover, region=region))
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
                        pr = client.probe(c.pdf_url)  # ilk baytlar "%PDF" olmalı; hata sayfası PDF sayılmaz
                        if not (pr.get("ok") and pr.get("kind") == "pdf"):
                            res.warnings.append(f"PDF doğrulanamadı ({pr.get('status')}, {pr.get('kind')}): {c.pdf_url}")
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
