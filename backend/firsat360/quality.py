"""Kalite ajanları ve orkestratör.

Her günlük çalıştırmada, tarama ve görsel doğrulamadan SONRA, feed üretilmeden ÖNCE çalışır:

    ajanlar denetler → sorunları orkestratöre bildirir → orkestratör otomatik düzeltmeleri uygular
    → ajanlar yeniden denetler (düzeltme gerçekten işe yaradı mı?) → rapor (status.json, Actions özeti, GitHub issue)

Ajanlar (her birinin girdisi veritabanı, çıktısı ``Issue`` listesidir; ``fix`` varsa orkestratör uygular):
- SourceAgent        mağaza taraması başarısız → aynı çalıştırmada bir kez yeniden dener (erişim engelini aşmaz)
- CatalogAgent       süresi dolmuş katalogların yayındaki ürünleri → yayından alır
- ProductImageAgent  görselsiz ürünler → resmî ürün sayfasından yeniden doğrulatır; mağaza bazlı görsel oranını ölçer;
                     ürüne bağlanmış reklam afişi gibi kalıp dışı görselleri kaldırır
- DataQualityAgent   şablon metni ad ('product-thumb'), mükerrer kayıt, tutarsız önceki fiyat → düzeltir/gizler
- DealsAgent         Günün İndirimleri dünküyle büyük ölçüde aynıysa veya çok azsa bildirir
- UserReportAgent    uygulamadan gelen "Hatalı bilgi" bildirimleri (GitHub issue) → ürünü yeniden doğrulatır,
                     sonucu issue'ya yazar

Kural: hiçbir düzeltme veri UYDURMAZ. Düzeltme ya yeniden okur/doğrular ya da hatalı veriyi gizler.
Bir düzeltme denenip sorun sürerse "kalan sorun" olarak raporlanır; aynı çalıştırmada tekrar denenmez.
"""
from __future__ import annotations

import json
import logging
import os
import re
from dataclasses import dataclass, field
from datetime import date, datetime, timedelta, timezone
from typing import Callable

from sqlalchemy import select
from sqlalchemy.orm import Session, sessionmaker

from .db import Catalog, Offer, Store
from .imagematch import is_junk_name
from .normalize import fold

log = logging.getLogger("firsat360.quality")

# Mağaza → kabul edilen ürün görseli kalıbı (feed'deki görsel bu kalıba uymuyorsa ürüne bağlanmış afiş/logo olabilir).
IMAGE_PATTERNS = {
    "bim": re.compile(r"cdn1\.bim\.com\.tr/uploads/aktuel-urunler/\d+_(?:kucuk|buyuk)_", re.I),
    "sok": re.compile(r"images\.ceptesok\.com/.*product-assets/", re.I),
    "tkkoop": re.compile(r"tkkoop\.com\.tr/.*assets/images/urun/[^/?#]+\.(?:jpe?g|png|webp)", re.I),
}
MIN_DEALS = 10
REPORT_TITLE_PREFIX = "[Hatalı veri]"


@dataclass
class Issue:
    agent: str
    code: str
    severity: str            # info | warning | error
    message: str
    store: str | None = None
    count: int = 1
    fix: Callable[[], str] | None = None   # orkestratörün çağıracağı düzeltme; kısa sonuç metni döner
    fix_result: str | None = None
    resolved: bool | None = None           # yeniden denetim sonucu (fix varsa)

    def as_dict(self) -> dict:
        return {"agent": self.agent, "code": self.code, "severity": self.severity, "store": self.store,
                "count": self.count, "message": self.message, "fix": self.fix_result, "resolved": self.resolved}


@dataclass
class Context:
    sf: sessionmaker
    today: date
    now: datetime
    retry_store: Callable[[str], dict] | None = None        # mağazayı yeniden tara
    reverify: Callable[[tuple[str, ...]], dict] | None = None  # ürün sayfalarını yeniden doğrula
    github: "GitHubReports | None" = None
    notes: list[str] = field(default_factory=list)


def _aware(d):
    return None if d is None else (d if d.tzinfo else d.replace(tzinfo=timezone.utc))


# ------------------------------------------------------------------ ajanlar
class SourceAgent:
    name = "kaynak"

    def check(self, ctx: Context) -> list[Issue]:
        out = []
        with ctx.sf() as s:
            for st in s.scalars(select(Store).where(Store.enabled.is_(True))):
                if st.status != "error":
                    continue
                err = (st.last_error or "")[:200]
                blocked = "403" in err or "robots" in err.lower() or "aşılmaya çalışılmadı" in err
                fix = None
                if ctx.retry_store and not blocked:
                    fix = (lambda sid=st.id: _retry(ctx, sid))
                out.append(Issue(self.name, "source_failed", "error",
                                 f"{st.name} taranamadı: {err or 'bilinmeyen hata'}"
                                 + (" (erişim engeli; aşılmaz, yalnızca bildirilir)" if blocked else ""),
                                 store=st.id, fix=fix))
        return out


def _retry(ctx: Context, sid: str) -> str:
    r = ctx.retry_store(sid)
    return "yeniden tarandı: " + ("başarılı" if r.get("ok") else f"yine başarısız ({str(r.get('error'))[:120]})")


class CatalogAgent:
    name = "katalog"

    def check(self, ctx: Context) -> list[Issue]:
        with ctx.sf() as s:
            expired = s.scalars(select(Offer).where(Offer.published.is_(True), Offer.valid_to.is_not(None),
                                                    Offer.valid_to < ctx.today - timedelta(days=1))).all()
            by: dict[str, int] = {}
            for o in expired:
                by[o.store_id] = by.get(o.store_id, 0) + 1
        return [Issue(self.name, "expired_published", "warning",
                      f"Süresi dolmuş {n} ürün hâlâ yayında", store=sid, count=n,
                      fix=(lambda sid=sid: self._unpublish(ctx, sid))) for sid, n in by.items()]

    def _unpublish(self, ctx: Context, sid: str) -> str:
        with ctx.sf() as s:
            rows = s.scalars(select(Offer).where(Offer.store_id == sid, Offer.published.is_(True),
                                                 Offer.valid_to.is_not(None),
                                                 Offer.valid_to < ctx.today - timedelta(days=1))).all()
            for o in rows:
                o.published = False
            s.commit()
        return f"{len(rows)} ürün yayından alındı"


class ProductImageAgent:
    name = "urun_gorseli"

    def metrics(self, ctx: Context) -> dict:
        out: dict[str, dict] = {}
        with ctx.sf() as s:
            for o in s.scalars(select(Offer).where(Offer.published.is_(True))):
                m = out.setdefault(o.store_id, {"products": 0, "with_image": 0, "verified": 0, "missing_reasons": {}})
                m["products"] += 1
                if o.image_url:
                    m["with_image"] += 1
                    if o.image_status in ("verified", "official_json"):
                        m["verified"] += 1
                else:
                    r = o.image_status or "none"
                    m["missing_reasons"][r] = m["missing_reasons"].get(r, 0) + 1
        for m in out.values():
            m["image_rate"] = round(m["with_image"] / m["products"], 3) if m["products"] else None
        return out

    def check(self, ctx: Context) -> list[Issue]:
        issues = []
        with ctx.sf() as s:
            bad: dict[str, list[str]] = {}
            for o in s.scalars(select(Offer).where(Offer.published.is_(True), Offer.image_url.is_not(None))):
                pat = IMAGE_PATTERNS.get(o.store_id)
                if pat is not None and o.image_status != "official_json" and not pat.search(o.image_url):
                    bad.setdefault(o.store_id, []).append(o.id)
        for sid, ids in bad.items():
            issues.append(Issue(self.name, "image_pattern", "error",
                                f"{len(ids)} üründe ürün görseli kalıbına uymayan görsel (afiş/logo olabilir)",
                                store=sid, count=len(ids), fix=(lambda ids=ids: self._drop(ctx, ids))))
        for sid, m in self.metrics(ctx).items():
            missing = m["products"] - m["with_image"]
            if not missing:
                continue
            sev = "error" if (m["image_rate"] or 0) < 0.5 else "warning" if (m["image_rate"] or 0) < 0.9 else "info"
            reasons = ", ".join(f"{k}: {v}" for k, v in sorted(m["missing_reasons"].items(), key=lambda t: -t[1]))
            fix = (lambda sid=sid: self._reverify(ctx, sid)) if ctx.reverify else None
            issues.append(Issue(self.name, "missing_images", sev,
                                f"{missing}/{m['products']} ürünün görseli yok ({reasons})",
                                store=sid, count=missing, fix=fix))
        return issues

    def _drop(self, ctx: Context, ids: list[str]) -> str:
        with ctx.sf() as s:
            for i in ids:
                o = s.get(Offer, i)
                o.image_url = o.image_large_url = None
                o.image_status, o.image_rights, o.image_checked_at = "rejected_pattern", "none", None
            s.commit()
        return f"{len(ids)} kalıp dışı görsel kaldırıldı, ürünler yeniden doğrulama kuyruğunda"

    def _reverify(self, ctx: Context, sid: str) -> str:
        """Bu çalıştırmada sayfası açılmamış (veya 1 günden eski kontrollü) görselsiz ürünler doğrulatılır.
        Aynı sayfa aynı çalıştırmada ikinci kez açılmaz (mağaza sitesine gereksiz yük bindirilmez)."""
        from .productpages import STORE_HOSTS
        if sid not in STORE_HOSTS:
            return "bu mağazada ürün sayfası doğrulaması yok (görsel yalnızca resmî liste/JSON'dan gelir)"
        cutoff = ctx.now - timedelta(days=1)
        with ctx.sf() as s:
            todo = [o for o in s.scalars(select(Offer).where(Offer.store_id == sid, Offer.published.is_(True),
                                                             Offer.image_url.is_(None)))
                    if o.image_checked_at is None or _aware(o.image_checked_at) < cutoff]
            for o in todo:
                o.image_checked_at = None
            s.commit()
        if not todo:
            return "hepsi bugün kontrol edildi; resmî sayfada doğrulanabilir görsel yok (yarın yeniden denenecek)"
        r = ctx.reverify((sid,)).get(sid, {}) if ctx.reverify else {}
        return f"{len(todo)} ürün yeniden doğrulamaya alındı: {r.get('pages', 0)} sayfa açıldı, {r.get('verified', 0)} doğrulandı"


class DataQualityAgent:
    name = "veri_kalitesi"

    def check(self, ctx: Context) -> list[Issue]:
        issues = []
        with ctx.sf() as s:
            offers = s.scalars(select(Offer).where(Offer.published.is_(True))).all()
            junk = [o.id for o in offers if is_junk_name(o.name)]
            groups: dict[tuple, list[Offer]] = {}
            for o in offers:
                groups.setdefault((o.store_id, o.catalog_id, fold(o.brand or ""), fold(o.name), fold(o.variant or ""),
                                   o.price), []).append(o)
            dups = [x.id for g in groups.values() if len(g) > 1
                    for x in sorted(g, key=lambda o: _aware(o.last_seen_at) or ctx.now, reverse=True)[1:]]
            bad_old = [o.id for o in offers if o.store_declared_old_price is not None and o.price is not None
                       and o.store_declared_old_price <= o.price]
        if junk:
            issues.append(Issue(self.name, "junk_name", "error", f"{len(junk)} ürünün adı şablon metni (ör. product-thumb)",
                                count=len(junk), fix=lambda: self._hide(ctx, junk, "şablon adlı")))
        if dups:
            issues.append(Issue(self.name, "duplicate", "warning", f"{len(dups)} mükerrer ürün kaydı",
                                count=len(dups), fix=lambda: self._hide(ctx, dups, "mükerrer")))
        if bad_old:
            issues.append(Issue(self.name, "bad_old_price", "warning",
                                f"{len(bad_old)} üründe önceki fiyat güncel fiyattan düşük/eşit",
                                count=len(bad_old), fix=lambda: self._clear_old(ctx, bad_old)))
        return issues

    def _hide(self, ctx: Context, ids: list[str], what: str) -> str:
        with ctx.sf() as s:
            for i in ids:
                s.get(Offer, i).published = False
            s.commit()
        return f"{len(ids)} {what} kayıt yayından alındı"

    def _clear_old(self, ctx: Context, ids: list[str]) -> str:
        with ctx.sf() as s:
            for i in ids:
                s.get(Offer, i).store_declared_old_price = None
            s.commit()
        return f"{len(ids)} tutarsız önceki fiyat kaldırıldı (indirim gösterilmez)"


class DealsAgent:
    name = "gunun_indirimleri"

    def check(self, ctx: Context) -> list[Issue]:
        from .deals import daily_deals
        with ctx.sf() as s:
            today = daily_deals(s, ctx.today, now=ctx.now)["offer_ids"]
            yday = daily_deals(s, ctx.today - timedelta(days=1), now=ctx.now)["offer_ids"]
        out = []
        if len(today) < MIN_DEALS:
            out.append(Issue(self.name, "few_deals", "warning",
                             f"Günün İndirimleri'nde yalnızca {len(today)} ürün var (görselli, indirimli/yeni aday az)",
                             count=len(today)))
        if today and len(set(today) & set(yday)) > len(today) // 2:
            out.append(Issue(self.name, "deals_static", "warning",
                             "Günün İndirimleri dünküyle büyük ölçüde aynı (aday havuzu küçük)",
                             count=len(set(today) & set(yday))))
        return out


class UserReportAgent:
    """Uygulamadaki "Hatalı bilgi bildir" → GitHub issue ("[Hatalı veri] …", gövdede ``offer:<id>``)."""
    name = "kullanici_bildirimi"
    OFFER_RE = re.compile(r"offer:([a-z0-9_-]{3,64})", re.I)

    def check(self, ctx: Context) -> list[Issue]:
        if ctx.github is None:
            return []
        try:
            reports = ctx.github.open_reports()
        except Exception as exc:  # noqa: BLE001
            return [Issue(self.name, "github_unreachable", "info", f"bildirimler okunamadı: {exc}")]
        out = []
        for rep in reports:
            m = self.OFFER_RE.search(rep.get("body") or "")
            if not m:
                continue
            oid = m.group(1)
            out.append(Issue(self.name, "user_report", "warning", f"Kullanıcı bildirimi #{rep['number']}: {oid}",
                             fix=(lambda rep=rep, oid=oid: self._handle(ctx, rep, oid))))
        return out

    def _handle(self, ctx: Context, rep: dict, oid: str) -> str:
        with ctx.sf() as s:
            o = s.get(Offer, oid)
            if o is None or not o.published:
                msg = "Bu ürün artık yayında değil; işlem gerekmedi."
            else:
                o.image_checked_at = None  # görsel yeniden doğrulama kuyruğu
                store, url = o.store_id, o.product_url
                s.commit()
                msg = "Ürün yeniden doğrulama kuyruğuna alındı."
                if ctx.reverify:
                    ctx.reverify((store,))
                    with ctx.sf() as s2:
                        o2 = s2.get(Offer, oid)
                        msg = (f"Ürün resmî sayfasından yeniden doğrulandı: görsel durumu `{o2.image_status}`, "
                               f"görsel {'var' if o2.image_url else 'gösterilmiyor'}. Kaynak: {url}")
        ctx.github.answer(rep["number"], msg, close=True)
        return msg


AGENTS = (SourceAgent(), CatalogAgent(), DataQualityAgent(), ProductImageAgent(), DealsAgent(), UserReportAgent())


# ------------------------------------------------------------------ orkestratör
def orchestrate(ctx: Context, agents=AGENTS) -> dict:
    found: list[Issue] = []
    for a in agents:
        try:
            found += a.check(ctx)
        except Exception as exc:  # noqa: BLE001 — bir ajanın hatası diğerlerini durdurmaz
            found.append(Issue(getattr(a, "name", "?"), "agent_crashed", "error", f"{exc.__class__.__name__}: {exc}"))
    fixed_codes: set[tuple] = set()
    for i in found:
        if i.fix is None:
            continue
        try:
            i.fix_result = i.fix()
            fixed_codes.add((i.agent, i.code, i.store))
        except Exception as exc:  # noqa: BLE001
            i.fix_result = f"düzeltme başarısız: {exc.__class__.__name__}: {exc}"
        log.info("kalite: %s/%s %s → %s", i.agent, i.code, i.store or "", i.fix_result)
    # Yeniden denetim: düzeltilen sorun hâlâ var mı?
    after: list[Issue] = []
    for a in agents:
        if isinstance(a, UserReportAgent):
            continue  # işlenen bildirimler kapatıldı; tekrar okunmaz
        try:
            after += a.check(ctx)
        except Exception as exc:  # noqa: BLE001 — ilk turda zaten raporlandı; yeniden denetim hatası kayda geçer
            log.warning("kalite: %s yeniden denetimi başarısız: %s", getattr(a, "name", "?"), exc)
    still = {(i.agent, i.code, i.store): i for i in after}
    for i in found:
        if (i.agent, i.code, i.store) in fixed_codes:
            j = still.get((i.agent, i.code, i.store))
            i.resolved = j is None or j.count < i.count
            if j is not None and j.count < i.count:
                i.fix_result = f"{i.fix_result} · kalan: {j.count}"
    remaining = [i for i in after if i.severity == "error"]
    report = {
        "checked_at": ctx.now.isoformat(),
        "issues": [i.as_dict() for i in found],
        "fixed": sum(1 for i in found if i.resolved),
        "remaining_errors": [i.as_dict() for i in remaining],
        "image_metrics": ProductImageAgent().metrics(ctx),
    }
    for i in remaining:
        log.warning("kalite: KALAN SORUN %s/%s %s: %s", i.agent, i.code, i.store or "", i.message)
    if ctx.github is not None:
        try:
            ctx.github.upsert_quality_issue(report)
        except Exception as exc:  # noqa: BLE001
            log.warning("kalite raporu GitHub'a yazılamadı: %s", exc)
    return report


def markdown_summary(report: dict) -> str:
    lines = ["## FIRSAT360 kalite raporu", "", "| Mağaza | Ürün | Görselli | Oran |", "|---|---:|---:|---:|"]
    for sid, m in sorted(report.get("image_metrics", {}).items()):
        lines.append(f"| {sid} | {m['products']} | {m['with_image']} | %{round((m['image_rate'] or 0) * 100)} |")
    lines += ["", "| Ajan | Sorun | Mağaza | Düzeltme | Çözüldü |", "|---|---|---|---|---|"]
    for i in report.get("issues", []):
        res = "✅" if i["resolved"] else ("❌" if i["resolved"] is False else "—")
        lines.append(f"| {i['agent']} | {i['message']} | {i['store'] or ''} | {i['fix'] or ''} | {res} |")
    if report.get("remaining_errors"):
        lines += ["", f"**Kalan hata: {len(report['remaining_errors'])}**"]
    return "\n".join(lines)


# ------------------------------------------------------------------ GitHub (yalnızca Actions içinde, GITHUB_TOKEN ile)
class GitHubReports:
    QUALITY_TITLE = "🔧 Otomatik kalite raporu"

    def __init__(self, repo: str, token: str, transport=None):
        import httpx
        self.repo = repo
        self.http = httpx.Client(base_url="https://api.github.com", timeout=20, transport=transport,
                                 headers={"Authorization": f"Bearer {token}", "Accept": "application/vnd.github+json",
                                          "X-GitHub-Api-Version": "2022-11-28", "User-Agent": "firsat360-quality"})

    @classmethod
    def from_env(cls) -> "GitHubReports | None":
        repo, token = os.getenv("GITHUB_REPOSITORY"), os.getenv("GITHUB_TOKEN")
        return cls(repo, token) if repo and token else None

    def _issues(self) -> list[dict]:
        r = self.http.get(f"/repos/{self.repo}/issues", params={"state": "open", "per_page": 100})
        r.raise_for_status()
        return [i for i in r.json() if "pull_request" not in i]

    def open_reports(self) -> list[dict]:
        return [i for i in self._issues() if (i.get("title") or "").startswith(REPORT_TITLE_PREFIX)][:20]

    def answer(self, number: int, body: str, close: bool) -> None:
        self.http.post(f"/repos/{self.repo}/issues/{number}/comments", json={"body": body}).raise_for_status()
        if close:
            self.http.patch(f"/repos/{self.repo}/issues/{number}", json={"state": "closed"}).raise_for_status()

    def upsert_quality_issue(self, report: dict) -> None:
        """Kalan hata varsa tek bir "kalite raporu" issue'su açık tutulur/güncellenir; hata kalmayınca kapatılır."""
        existing = next((i for i in self._issues() if i.get("title") == self.QUALITY_TITLE), None)
        body = markdown_summary(report)
        if report.get("remaining_errors"):
            if existing:
                self.http.patch(f"/repos/{self.repo}/issues/{existing['number']}", json={"body": body}).raise_for_status()
            else:
                self.http.post(f"/repos/{self.repo}/issues", json={"title": self.QUALITY_TITLE, "body": body}).raise_for_status()
        elif existing:
            self.answer(existing["number"], "Tüm kalite hataları giderildi.\n\n" + body, close=True)


def write_step_summary(report: dict) -> None:
    p = os.getenv("GITHUB_STEP_SUMMARY")
    if p:
        with open(p, "a", encoding="utf-8") as f:
            f.write(markdown_summary(report) + "\n")


def compact(report: dict) -> dict:
    """status.json için kısa sürüm (uygulamadaki Kaynak durumu ekranı bunu okur)."""
    return {"checked_at": report["checked_at"], "fixed": report["fixed"],
            "remaining_errors": [{k: i[k] for k in ("agent", "store", "message")} for i in report["remaining_errors"]],
            "image_metrics": {k: {"products": v["products"], "with_image": v["with_image"], "image_rate": v["image_rate"]}
                              for k, v in report["image_metrics"].items()}}


def dumps(report: dict) -> str:
    return json.dumps(report, ensure_ascii=False, indent=1, default=str)
