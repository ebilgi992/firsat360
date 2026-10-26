"""Ürün görsellerinin otomatik doğrulanması: her ürünün KENDİ resmî ürün sayfası açılır, sayfadaki ana görsel ve
ürün adı/ölçüsü/fiyatı okunur, imagematch.verify ile listedeki ürünle karşılaştırılır.

- Yalnızca görseli henüz doğrulanmamış veya son kontrolü RECHECK_DAYS günden eski ürünler açılır (günlük yük düşük).
- Mağaza başına çalıştırma limiti vardır; kalanlar sonraki günlerde tamamlanır.
- Görsel adresi mağazanın kendi alan adında/CDN'inde değilse kabul edilmez. Görsel dosyası indirilmez/kopyalanmaz.
- robots.txt bir sayfaya izin vermiyorsa o mağaza için durulur.
"""
from __future__ import annotations

import logging
import re
from datetime import datetime, timedelta, timezone
from urllib.parse import urljoin, urlparse

from sqlalchemy import or_, select
from sqlalchemy.orm import sessionmaker

from .connectors.base import ConnectorError, PoliteClient, RobotsDisallowed
from .connectors.extra import SKIP_IMG, page_meta
from .connectors.htmlutil import all_image_urls, parse, text_of
from .db import Offer, Store
from .imagematch import PageImage, apply_to_db

log = logging.getLogger("firsat360.productpages")

# Mağaza → ürün sayfası alan adı ve kabul edilen görsel alan adları
STORE_HOSTS = {
    "bim": ("bim.com.tr", ("cdn1.bim.com.tr", "bim.com.tr")),
    "a101": ("a101.com.tr", ("a101.com.tr",)),
    "sok": ("sokmarket.com.tr", ("images.ceptesok.com", "sokmarket.com.tr")),
    "tkkoop": ("tkkoop.com.tr", ("tkkoop.com.tr",)),
}
# Ürün sayfasındaki ANA ürün görselinin dosya kalıbı. og:image/JSON-LD yoksa (BİM, ŞOK ve TK sayfalarında yok)
# sayfadaki görseller bu kalıpla taranır. Kalıba uymayan görsel (logo, afiş, taksit bandı) ürün görseli sayılmaz.
MAIN_IMAGE = {
    "bim": re.compile(r"/uploads/aktuel-urunler/\d+_buyuk_", re.I),
    "sok": re.compile(r"product-assets/", re.I),
    "tkkoop": re.compile(r"/assets/images/urun/", re.I),
    "a101": re.compile(r"a101\.com\.tr/.*/(?:product|urun)", re.I),
}
RECHECK_DAYS = 7
_IMG_FILE = re.compile(r"\.(?:jpe?g|png|webp|avif)(?:[?#]|$)|/cdn-cgi/image/|[?&](?:w|width)=\d", re.I)
_SIZE_IN_CDN = re.compile(r"width=(\d+)")


def _host_ok(url: str | None, hosts: tuple[str, ...]) -> bool:
    if not url:
        return False
    h = urlparse(url).netloc.lower()
    return any(h == x or h.endswith("." + x) for x in hosts)


def main_image(store: str, html: str, url: str) -> str | None:
    """Sayfadaki ana ürün görseli: kalıba uyan görsellerden en sık geçen (ana görsel genelde birden çok kez yer alır:
    büyük görsel + yakınlaştırma), eşitlikte ilk görülen; CDN boyut parametresi en büyük olan tercih edilir."""
    pat = MAIN_IMAGE.get(store)
    if pat is None:
        return None
    tree = parse(html)
    root = tree.css_first("main") or tree.body
    if root is None:
        return None
    # Benzer ürünler / öneri bölmeleri ana görsel adayı değildir.
    for n in root.css("[class*=similar], [class*=related], [class*=recommend], [class*=benzer], footer, header, nav"):
        n.decompose()
    urls = [urljoin(url, u) for u in all_image_urls(root)]
    urls = [u for u in urls if pat.search(u) and not SKIP_IMG.search(u)]
    if not urls:
        return None
    # Aynı dosyanın farklı boyutları tek görsel sayılır (ŞOK: cdn-cgi/image/width=…/product-assets/…).
    def base(u: str) -> str:
        return re.sub(r"/cdn-cgi/image/[^/]+/", "/", u)
    counts: dict[str, int] = {}
    best_variant: dict[str, str] = {}
    for u in urls:
        b = base(u)
        counts[b] = counts.get(b, 0) + 1
        w = int((_SIZE_IN_CDN.search(u) or [0, 0])[1] or 0)
        cur = best_variant.get(b)
        if cur is None or w > int((_SIZE_IN_CDN.search(cur) or [0, 0])[1] or 0):
            best_variant[b] = u
    order = list(dict.fromkeys(base(u) for u in urls))
    top = max(order, key=lambda b: (counts[b], -order.index(b)))
    return best_variant[top]


def name_candidates(html: str, meta_name: str | None) -> list[str]:
    """Sayfanın ürün adı olabilecek metinleri: JSON-LD/h1/og:title + ürün bloğundaki başlıklar.
    imagematch her adayı ayrı puanlar ve en iyisini alır (h1 olmayan sayfalar: BİM, TK)."""
    tree = parse(html)
    out: list[str] = []
    if meta_name:
        out.append(meta_name)
    root = tree.css_first("main") or tree.body
    if root is not None:
        for n in root.css("[class*=similar], [class*=related], [class*=recommend], footer, header, nav"):
            n.decompose()
        heads = [text_of(h) for h in root.css("h1, h2, h3, [class*=product-name], [class*=productName], "
                                             "[class*=urun-adi], [class*=title]")]
        out += [h for h in heads if h and 3 <= len(h) <= 160]
        # Marka + ad aynı blokta iki başlık olarak yazılıyorsa (BİM) birleşik hâli de aday
        out += [f"{a} {b}" for a, b in zip(heads, heads[1:]) if a and b and len(a) + len(b) < 160][:20]
    return list(dict.fromkeys(out))[:60]


def page_row(store: str, url: str, html: str, final_url: str | None = None) -> PageImage:
    if final_url and _path(final_url) != _path(url):
        # Ürün sayfası kaldırılmış, ana sayfaya/kategoriye yönlendirildi: bu sayfadaki görsel bu ürünün değildir.
        return PageImage(store=store, detail_url=url, page_name=None, page_variant=None, page_price_text=None,
                         image_url=None, gone=True)
    if store == "tkkoop":
        tree = parse(html)
        title = tree.css_first(".urun_bilgi_sag .u_bilgi_2")
        primary = tree.css_first(".urun_resim")
        if title is not None and primary is not None:
            # TK ana başlığı div içindedir; ürün kartları/slider başlıkları değildir.
            # yok.png yer tutucusunda önerilen ürünlerin görsellerine geri düşülmez.
            images = [urljoin(url, u) for u in all_image_urls(primary)]
            image = next((u for u in images if _host_ok(u, STORE_HOSTS[store][1])
                          and MAIN_IMAGE[store].search(u) and _IMG_FILE.search(u)
                          and not SKIP_IMG.search(u)), None)
            price = tree.css_first(".urun_bilgi_sag .ss_urun52a")
            return PageImage(store, url, text_of(title), None, text_of(price), image)
    m = page_meta(html, url)
    img = m["image"]
    pat = MAIN_IMAGE.get(store)
    # og:image bazı sitelerde ürün görseli değil, sitenin genel paylaşım görseli/klasör adresidir
    # (TK: ".../storage/assets/images/seo/"). Mağazanın ürün görseli kalıbına uymayan og:image kullanılmaz.
    if img and (SKIP_IMG.search(img) or not _host_ok(img, STORE_HOSTS[store][1]) or not _IMG_FILE.search(img)
                or (pat is not None and not pat.search(img))):
        img = None
    if not img:
        img = main_image(store, html, url)
        if img and not _host_ok(img, STORE_HOSTS[store][1]):
            img = None
    names = name_candidates(html, m["name"])
    return PageImage(store=store, detail_url=url, page_name="\n".join(names) or None, page_variant=None,
                     page_price_text=m["price_text"], image_url=img)


def _path(u: str) -> str:
    p = urlparse(u)
    return (p.netloc.lower().removeprefix("www.") + p.path.rstrip("/")).lower()


def verify_product_pages(sf: sessionmaker, client: PoliteClient, per_store: int = 400,
                         now: datetime | None = None, stores: tuple[str, ...] | None = None) -> dict:
    now = now or datetime.now(timezone.utc)
    out: dict = {}
    for store, (page_host, _) in STORE_HOSTS.items():
        if stores and store not in stores:
            continue
        with sf() as s:
            source = s.get(Store, store)
            if source and source.status == "error" and any(k in (source.last_error or "").lower() for k in ("403", "429", "robots", "cloudflare")):
                out[store] = {"pages": 0, "errors": 0, "blocked": True}
                continue
            todo = s.scalars(select(Offer).where(
                Offer.store_id == store, Offer.published.is_(True), Offer.product_url.is_not(None),
                or_(Offer.image_status.is_(None), Offer.image_status != "verified",
                    Offer.image_checked_at.is_(None), Offer.image_checked_at < now - timedelta(days=RECHECK_DAYS)))
                .order_by(Offer.image_url.is_not(None), Offer.image_checked_at.is_not(None), Offer.image_checked_at).limit(per_store)).all()
            urls = [o.product_url for o in todo if _host_ok(o.product_url, (page_host,))]
        rows, errors = [], 0
        for u in dict.fromkeys(urls):
            try:
                resp = client.get(u)
                rows.append(page_row(store, u, resp.text, str(resp.url)))
            except RobotsDisallowed:
                log.warning("%s: robots.txt ürün sayfalarına izin vermiyor; durduruldu", store)
                break
            except ConnectorError as exc:
                errors += 1
                if "403" in str(exc) or "429" in str(exc):
                    break  # erişim engeli/hız sınırı: zorlamadan dur
        stats = {}
        if rows:
            with sf() as s:
                stats = apply_to_db(s, rows, now).get(store, {})
                s.commit()
        out[store] = {"pages": len(rows), "errors": errors, **stats}
    return out

