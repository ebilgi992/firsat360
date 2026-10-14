"""Resmî sayfalardan araçla alınmış GERÇEK veri anlık görüntülerini içe aktarır.

Bu, canlı tarayıcı çalıştırılamayan ortamlarda (ör. mağaza sitelerine ağ erişimi olmayan sunucu) gerçek veriyi
kaynağı ve okunma zamanıyla birlikte sisteme almak içindir. Kayıtların ``source_method`` alanı
``manual_snapshot`` olarak işaretlenir; canlı taramayla karıştırılmaz. Demo veri DEĞİLDİR.

Her anlık görüntü ``data/snapshots/<YYYY-MM-DD>/raw/`` klasörüdür:
  meta.json            okunma zamanları (mağaza başına) ve BİM sekme anahtarı → etiket eşlemesi
  bim.tsv              catalog_key, brand, name, variant, price_text, detail_path
  a101.tsv             name, price_text, old_price_text, detail_url
  migros.tsv           name, shownPrice(kuruş), regularPrice(kuruş), prettyName
  sok.tsv              (isteğe bağlı) kampanya sayfası ürünleri
  sok_indirimli.tsv    (isteğe bağlı) "İndirimli Ürünler" listesi: name, price_text, old_price_text, detail_url
  sok_campaigns.tsv    yalnızca resmî bağlantı olarak gösterilen kampanyalar
  tkkoop.tsv           Tarım Kredi haftalık kampanya: name, price_text, detail_url
  catalogs_more.tsv    yeni mağazaların katalogları: store, id, title, kind, valid_text, official_url, cover_url
  catalog_pages.tsv    katalog sayfa görselleri: catalog_id, page_no, image_url
  product_images.tsv   ürünlerin kendi resmî sayfalarından görseller (imagematch.py ile doğrulanarak uygulanır)
Bir mağazanın dosyası yoksa o mağaza bu anlık görüntüde atlanır (eski verisi olduğu gibi kalır).
"""
from __future__ import annotations

import csv
import hashlib
import json
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from urllib.parse import urljoin

from ..normalize import clean, kurus_to_decimal, parse_price, parse_tr_date, parse_tr_range
from .base import CatalogData, ConnectorError, ConnectorResult, OfferData

METHOD = "manual_snapshot"
BIM_LIST = "https://www.bim.com.tr/categories/100/aktuel-urunler.aspx"
SOK_DISCOUNT_URL = "https://www.sokmarket.com.tr/indirimli-urunler-cms-pl1"
TKKOOP_CAMPAIGN = "https://www.tkkoop.com.tr/haftalik-kampanyalar/kat-kat-firsat"


def _tk_title(name: str) -> str:
    """Tarım Kredi listesi BÜYÜK HARF: okunur başlık biçimine (Türkçe kurallarıyla); kısaltma ve birimler korunur."""
    import re

    from ..normalize import tr_lower
    keep = {"TK", "TKS", "MAP", "PET", "3D", "C", "HORECA"}
    units = {"G": "g", "GR": "g", "KG": "kg", "ML": "ml", "L": "L", "LT": "L", "LI": "'lı", "Lİ": "'li", "LÜ": "'lü",
             "LU": "'lu"}

    def up1(ch: str) -> str:
        return {"i": "İ", "ı": "I"}.get(ch, ch.upper())

    def fix(m: re.Match) -> str:
        w, i = m.group(0), m.start()
        prev = name[i - 1] if i > 0 else " "
        if w in keep:
            return w
        if prev.isdigit() and w in units:
            return units[w].lstrip("'") if units[w].startswith("'") is False else units[w]
        lw = tr_lower(w)
        return up1(lw[0]) + lw[1:]

    out = re.sub(r"[^\W\d_]+", fix, name)
    for k, v in FOREIGN_WORDS.items():  # Türkçe büyük I → ı kuralı yabancı marka adlarında uygulanmaz
        out = out.replace(k, v)
    out = re.sub(r"(\d)\s?[xX](\d)", r"\1x\2", out)
    out = re.sub(r"(\d) (G|Gr|Kg|Ml|Lt)\b", lambda m: m.group(1) + " " + units[m.group(2).upper()], out)
    return re.sub(r"(\d)'?\s?(lı|li|lu|lü|Lı|Li|Lu|Lü)\b", lambda m: m.group(1) + "'" + tr_lower(m.group(2)), out)


FOREIGN_WORDS = {"Molfıx": "Molfix", "Tıramısu": "Tiramisu"}


def _rows(path: Path, ncols: int) -> list[list[str | None]]:
    if not path.exists():
        raise ConnectorError(f"Anlık görüntü dosyası yok: {path}")
    out = []
    with path.open(encoding="utf-8") as fh:
        for raw in csv.reader((ln for ln in fh if ln.strip() and not ln.startswith("#")), delimiter="\t",
                              quoting=csv.QUOTE_NONE):
            if len(raw) != ncols:
                raise ConnectorError(f"{path.name}: beklenen {ncols} sütun, gelen {len(raw)}: {raw[:2]}")
            out.append([None if c.strip() in ("-", "") else c.strip() for c in raw])
    return out


def bim_catalog_title(label: str, column: str) -> tuple[str, str]:
    """(başlık, tür). BİM sayfasında 'Aktüel' (günlük) ve 'İndirim' (haftalık aralık) sütunları vardır."""
    if column == "indirim":
        return f"{label} İndirimleri", "discount_list"
    return f"{label} Aktüel", "aktuel"


@dataclass
class SnapshotSource:
    directory: Path

    def __post_init__(self):
        self.directory = Path(self.directory)
        meta_path = self.directory / "meta.json"
        if not meta_path.exists():
            raise ConnectorError(f"meta.json yok: {meta_path}")
        self.meta = json.loads(meta_path.read_text(encoding="utf-8"))
        self.observed = {k: datetime.fromisoformat(v) for k, v in self.meta["observed"].items()}

    @property
    def date(self) -> str:
        return self.meta.get("snapshot_date", self.directory.parent.name)

    def _has(self, name: str) -> bool:
        return (self.directory / name).exists()

    def bim(self) -> ConnectorResult:
        obs = self.observed["bim"]
        res = ConnectorResult(store_id="bim", method=METHOD, observed_at=obs)
        for key, tab in self.meta["bim_tabs"].items():
            label = tab["label"]
            start, end = parse_tr_range(label, obs.date())
            title, kind = bim_catalog_title(label, tab.get("column", "aktuel"))
            res.catalogs.append(CatalogData(id=f"bim-{key}", title=title, kind=kind,
                                            official_url=f"{BIM_LIST}?Bim_AktuelTarihKey={key}",
                                            valid_from=start, valid_to=end))
        cat_by_id = {c.id: c for c in res.catalogs}
        self._bim_afis(res, obs)
        for key, brand, name, variant, price_text, url in _rows(self.directory / "bim.tsv", 6):
            cat = cat_by_id.get(f"bim-{key}")
            if cat is None:
                raise ConnectorError(f"bim.tsv: meta.json'da tanımsız sekme anahtarı {key}")
            res.offers.append(OfferData(
                name=name, brand=brand, variant=variant, price=parse_price(price_text), price_text=price_text,
                source_url=cat.official_url, catalog_id=cat.id,
                product_url=urljoin("https://www.bim.com.tr", url) if url else cat.official_url,
                channel="magaza", valid_from=cat.valid_from, valid_to=cat.valid_to))
        return res

    def _bim_afis(self, res: ConnectorResult, obs) -> None:
        """BİM afişleri (resmî katalog sayfa görselleri) → aynı tarihli ürün kataloğuna sayfa olarak; yoksa afiş kataloğu."""
        if not self._has("bim_afis.tsv"):
            return
        from .bim import AFIS_URL, attach_afis
        groups: dict[str, dict] = {}
        for key, label, column, page_no, full, thumb, verified in _rows(self.directory / "bim_afis.tsv", 7):
            g = groups.setdefault(key, {"label": label, "column": column, "pages": []})
            g["pages"].append({"url": full, "thumb_url": thumb, "page": int(page_no), "verified": verified == "yes",
                               "caption": f"{label} · sayfa {page_no}", "link": f"{AFIS_URL}?top=1&Bim_AfisKey={key}"})
        attach_afis(res.catalogs, groups, obs.date())

    def a101(self) -> ConnectorResult:
        obs = self.observed["a101"]
        res = ConnectorResult(store_id="a101", method=METHOD, observed_at=obs)
        src = "https://www.a101.com.tr/aldin-aldin"
        res.catalogs.append(CatalogData(id="a101-aldin-aldin", title="Aldın Aldın", kind="aktuel", official_url=src))
        for name, price_text, old_text, url in _rows(self.directory / "a101.tsv", 4):
            res.offers.append(OfferData(
                name=name, price=parse_price(price_text), price_text=price_text, source_url=src,
                catalog_id="a101-aldin-aldin", product_url=url, category_url_hint=url,
                store_declared_old_price=parse_price(old_text),
                channel="online" if url and "/kapida/" in url else "bilinmiyor"))
        return res

    def migros(self) -> ConnectorResult:
        obs = self.observed["migros"]
        res = ConnectorResult(store_id="migros", method=METHOD, observed_at=obs)
        api = "https://www.migros.com.tr/rest/search/screens/migroskop-urunleri-dt-3"
        res.catalogs.append(CatalogData(id="migros-migroskop", title="Migroskop Ürünleri", kind="discount_list",
                                        official_url="https://www.migros.com.tr/migroskop-urunleri-dt-3"))
        images = {}
        if self._has("migros_images.tsv"):
            images = {pretty: url for pretty, url in _rows(self.directory / "migros_images.tsv", 2)}
        for name, shown, regular, pretty in _rows(self.directory / "migros.tsv", 4):
            p, r = kurus_to_decimal(shown), kurus_to_decimal(regular)
            thumb = images.get(pretty)
            res.offers.append(OfferData(
                image_url=thumb, image_large_url=migros_large_image(thumb),
                image_status="official_json" if thumb else None,
                name=name, price=p, price_text=None, source_url=api, catalog_id="migros-migroskop",
                product_url=f"https://www.migros.com.tr/{pretty}",
                store_declared_old_price=r if r is not None and p is not None and r > p else None,
                condition="Fiyat koşulları (ör. Money üyeliği) resmî ürün sayfasında kontrol edilmeli",
                channel="online"))
        return res

    def sok(self) -> ConnectorResult:
        obs = self.observed["sok"]
        res = ConnectorResult(store_id="sok", method=METHOD, observed_at=obs)
        if self._has("sok.tsv"):
            res.catalogs.append(CatalogData(id="sok-kampanyalar", title="Ürünlü Kampanyalar", kind="campaign",
                                            official_url="https://www.sokmarket.com.tr/kampanyalar"))
            for name, price_text, listed_text, cond, vf_text, url, camp_url in _rows(self.directory / "sok.tsv", 7):
                price, listed = parse_price(price_text), parse_price(listed_text)
                res.offers.append(OfferData(
                    name=name, price=price, price_text=price_text, source_url=camp_url, catalog_id="sok-kampanyalar",
                    product_url=url or camp_url, condition=cond, channel="magaza",
                    store_declared_old_price=listed if listed is not None and price is not None and listed > price else None,
                    valid_from=parse_tr_date(vf_text, obs.date())))
        if self._has("sok_indirimli.tsv"):
            res.catalogs.append(CatalogData(id="sok-indirimli", title="Haftanın Fırsatları · İndirimli Ürünler",
                                            kind="discount_list", official_url=SOK_DISCOUNT_URL))
            for name, price_text, old_text, url in _rows(self.directory / "sok_indirimli.tsv", 4):
                price, old = parse_price(price_text), parse_price(old_text)
                res.offers.append(OfferData(
                    name=name, price=price, price_text=price_text, source_url=SOK_DISCOUNT_URL,
                    catalog_id="sok-indirimli", product_url=url or SOK_DISCOUNT_URL, channel="online",
                    store_declared_old_price=old if old is not None and price is not None and old > price else None))
        if self._has("sok_banners.tsv"):
            pages = []
            for i, (title, valid_text, url, image) in enumerate(_rows(self.directory / "sok_banners.tsv", 4), 1):
                caption = clean(title) + (f" · {valid_text}" if valid_text else "")
                pages.append({"url": sok_large_banner(image), "thumb_url": image, "caption": caption, "link": url,
                              "page": i, "verified": False})
            res.catalogs.append(CatalogData(
                id="sok-kampanya-gorselleri", title="ŞOK Kampanyaları", kind="campaign_gallery",
                official_url="https://www.sokmarket.com.tr/kampanyalar", pages=pages, cover_url=pages[1]["thumb_url"]
                if len(pages) > 1 else (pages[0]["thumb_url"] if pages else None)))
        for title, valid_text, url in _rows(self.directory / "sok_campaigns.tsv", 3):
            start, end = parse_tr_range(valid_text, obs.date()) if valid_text else (None, None)
            cid = "sok-link-" + hashlib.sha1(url.encode("utf-8"), usedforsecurity=False).hexdigest()[:10]
            res.catalogs.append(CatalogData(id=cid, title=clean(title), kind="link_only",
                                            official_url=url, valid_from=start, valid_to=end))
        return res

    def tkkoop(self) -> ConnectorResult:
        """Tarım Kredi Kooperatif Market — haftalık kampanya sayfası (ürün + fiyat; tarih sayfa başlığında)."""
        obs = self.observed["tkkoop"]
        res = ConnectorResult(store_id="tkkoop", method=METHOD, observed_at=obs)
        start, end = parse_tr_range("13-19 Ekim", obs.date())
        res.catalogs.append(CatalogData(id="tkkoop-kat-kat-firsat-1319-ekim", title="Kat Kat Fırsat · 13-19 Ekim",
                                        kind="campaign", official_url=TKKOOP_CAMPAIGN,
                                        valid_from=start, valid_to=end))
        for name, price_text, url in _rows(self.directory / "tkkoop.tsv", 3):
            res.offers.append(OfferData(
                name=_tk_title(name), price=parse_price(price_text), price_text=price_text, source_url=TKKOOP_CAMPAIGN,
                catalog_id=res.catalogs[0].id, product_url=url, channel="magaza",
                condition="Mağaza fiyatı esastır (Tarım Kredi sayfa notu)", valid_from=start, valid_to=end))
        return res

    def _catalog_only(self, store: str, rows: list, pages: dict[str, list[dict]]) -> ConnectorResult:
        obs = self.observed[store]
        res = ConnectorResult(store_id=store, method=METHOD, observed_at=obs)
        for _store, cid, title, kind, valid_text, url, cover in rows:
            start, end = parse_tr_range(valid_text, obs.date()) if valid_text else (None, None)
            res.catalogs.append(CatalogData(id=cid, title=title, kind=kind, official_url=url, valid_from=start,
                                            valid_to=end, cover_url=cover, pages=pages.get(cid, [])))
        return res

    def all(self) -> list[ConnectorResult]:
        files = {"a101": "a101.tsv", "bim": "bim.tsv", "sok": "sok_campaigns.tsv", "migros": "migros.tsv",
                 "tkkoop": "tkkoop.tsv"}
        out = []
        for store, fname in files.items():
            if self._has(fname) and store in self.observed:
                out.append(getattr(self, store)())
        if self._has("catalogs_more.tsv"):
            pages: dict[str, list[dict]] = {}
            if self._has("catalog_pages.tsv"):
                for cid, no, img in _rows(self.directory / "catalog_pages.tsv", 3):
                    pages.setdefault(cid, []).append({"url": img, "thumb_url": img, "page": int(no), "verified": True,
                                                      "caption": f"Sayfa {no}", "link": None})
            rows = _rows(self.directory / "catalogs_more.tsv", 7)
            by_store = {r.store_id: r for r in out}
            for store in dict.fromkeys(r[0] for r in rows):
                mine = [r for r in rows if r[0] == store]
                if store in by_store:
                    by_store[store].catalogs.extend(self._catalog_only(store, mine, pages).catalogs)
                elif store in self.observed:
                    out.append(self._catalog_only(store, mine, pages))
        if self._has("catalogs_extra.tsv"):
            by_store = {r.store_id: r for r in out}
            for store, cid, title, kind, valid_text, url in _rows(self.directory / "catalogs_extra.tsv", 6):
                r = by_store.get(store)
                if r is None:
                    continue
                start, end = parse_tr_range(valid_text, r.observed_at.date()) if valid_text else (None, None)
                r.catalogs.append(CatalogData(id=cid, title=title, kind=kind, official_url=url,
                                              valid_from=start, valid_to=end))
        return out


def migros_large_image(list_url: str | None) -> str | None:
    """PRODUCT_LIST (…-105x105.jpg) → PRODUCT_DETAIL (boyut eki olmadan). Kural yanıtla doğrulandı (5/5 örnek)."""
    if not list_url:
        return None
    return list_url.replace("-105x105.", ".")


def sok_large_banner(url: str) -> str:
    """ŞOK görsel servisinde boyut parametresi: sayfadaki 512x260 → 1024x520 (türetilmiş; yüklenemezse küçük kullanılır)."""
    return url.replace("width=512,height=260", "width=1024,height=520")


def snapshot_dirs(root: Path) -> list[Path]:
    """data/snapshots altındaki tüm anlık görüntüler, tarih sırasıyla (eskiden yeniye)."""
    return sorted(p for p in Path(root).glob("*/raw") if (p / "meta.json").exists())
