"""Yapılandırma: tüm gizli bilgiler ortam değişkenlerinden okunur."""
from __future__ import annotations

import os
from dataclasses import dataclass, field
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

STORES = {
    "a101": {
        "name": "A101",
        "official_url": "https://www.a101.com.tr/",
        "catalog_url": "https://www.a101.com.tr/aldin-aldin",
        "legal_note": "robots.txt (12.10.2026): /arama? ve opf= parametreli yollar yasak; /aldin-aldin yasak değil. "
                      "Kullanım koşulları henüz okunmadı.",
    },
    "bim": {
        "name": "BİM",
        "official_url": "https://www.bim.com.tr/",
        "catalog_url": "https://www.bim.com.tr/categories/100/aktuel-urunler.aspx",
        "legal_note": "robots.txt 12.10.2026'da okunamadı (erişim hatası). Bağlayıcı her çalıştırmada robots.txt'yi "
                      "kontrol eder; izin yoksa çalışmaz. Kullanım koşulları henüz okunmadı.",
    },
    "sok": {
        "name": "ŞOK",
        "official_url": "https://www.sokmarket.com.tr/",
        "catalog_url": "https://www.sokmarket.com.tr/kampanyalar",
        "legal_note": "robots.txt (12.10.2026): yalnızca /arama yasak. Kullanım koşulları henüz okunmadı.",
    },
    "migros": {
        "name": "Migros",
        "official_url": "https://www.migros.com.tr/",
        "catalog_url": "https://www.migros.com.tr/migroskop-urunleri-dt-3",
        "legal_note": "robots.txt (12.10.2026): /arama ve espv yasak; /rest yasak değil. Kullanılan JSON uç noktası "
                      "sitenin kendi ön yüzünün kullandığı, belgelenmemiş bir uç noktadır; yazılı izin önerilir.",
    },
    "tkkoop": {
        "name": "Tarım Kredi",
        "official_url": "https://www.tkkoop.com.tr/",
        "catalog_url": "https://www.tkkoop.com.tr/haftalik-kampanyalar/kat-kat-firsat",
        "legal_note": "robots.txt (13.10.2026): 'User-agent: * / Disallow:' — kısıtlama yok. Ürün sayfası notu: "
                      "fiyat ve stok değişebilir, mağaza fiyatı esastır. Kullanım koşulları henüz okunmadı.",
    },
    "hakmar": {
        "name": "Hakmar",
        "official_url": "https://hakmar.com.tr/",
        "catalog_url": "https://hakmar.com.tr/indirim/",
        "legal_note": "robots.txt (13.10.2026): yalnızca /wp-admin/ yasak. Broşür sayfa görselleri resmî sayfadaki "
                      "adreslerinden gösterilir. Kullanım koşulları henüz okunmadı.",
    },
    "watsons": {
        "name": "Watsons",
        "official_url": "https://www.watsons.com.tr/",
        "catalog_url": "https://katalog.watsons.com.tr/ekim-katalog-2026/page/1",
        "legal_note": "www.watsons.com.tr robots.txt (13.10.2026): hesap, sepet, arama, filtre ve bazı kampanya yolları "
                      "yasak. Katalog ayrı alan adında (katalog.watsons.com.tr, Publitas); robots.txt okunamadı → "
                      "yalnızca kapak + resmî bağlantı, otomatik tarama yok.",
    },
    "metro": {
        "name": "Metro",
        "official_url": "https://www.metro-tr.com/",
        "catalog_url": "https://brosurler.metro-tr.com/kozmetik-deterjan/page/1",
        "legal_note": "www.metro-tr.com robots.txt (13.10.2026): /sonuclar, /sonuc-yok yasak. Broşür alan adı "
                      "(brosurler.metro-tr.com, Publitas) robots.txt okunamadı → yalnızca kapak + resmî bağlantı.",
    },
    "bizimtoptan": {
        "name": "Bizim Toptan",
        "official_url": "https://www.bizimtoptan.com.tr/",
        "catalog_url": "https://www.bizimtoptan.com.tr/pages/katalog",
        "legal_note": "robots.txt (13.10.2026): sepet/hesap/arama ve parametreli yollar yasak; /pages/katalog yasak "
                      "değil. Kataloglar üçüncü taraf flipbook servislerinde; yalnızca kapak + resmî bağlantı.",
    },
    "gratis": {
        "name": "Gratis",
        "official_url": "https://www.gratis.com/",
        "catalog_url": "https://www.gratis.com/gratis-katalog",
        "legal_note": "robots.txt (13.10.2026): /search, /cart, /checkout ve filtre parametreleri yasak; "
                      "/gratis-katalog yasak değil. Katalog fliphtml5 üzerinde; yalnızca kapak + resmî bağlantı.",
    },
}

# Uygulamadaki mağaza rengi ve kısa ad (logo kopyalanmaz; yalnızca renkli monogram).
STORE_STYLE = {
    "a101": ("#00A0E3", "A101"), "bim": ("#E30613", "BİM"), "sok": ("#F2B600", "ŞOK"), "migros": ("#F27A00", "M"),
    "tkkoop": ("#0B8A3E", "TK"), "hakmar": ("#D7141A", "H"), "watsons": ("#00A3AD", "W"), "metro": ("#00327D", "MT"),
    "bizimtoptan": ("#E2001A", "BZ"), "gratis": ("#E6007E", "G"),
}
STORE_KIND = {"tkkoop": "market", "hakmar": "market", "watsons": "kozmetik", "metro": "toptan",
              "bizimtoptan": "toptan", "gratis": "kozmetik"}

# Europe/Istanbul saatine göre günde iki kontrol (sabah ve akşam). Dakikalar sunucu yükünü dağıtmak için kaydırıldı.
DEFAULT_SCHEDULE = {
    "a101": "07:40,18:40",
    "bim": "07:10,18:10",
    "sok": "07:25,18:25",
    "migros": "07:55,18:55",
}


@dataclass
class Settings:
    database_url: str = field(default_factory=lambda: os.getenv("DATABASE_URL", "sqlite:///./firsat360.db"))
    user_agent: str = field(default_factory=lambda: os.getenv(
        "SCRAPER_USER_AGENT", "FIRSAT360Bot/0.1 (+iletisim: ayarlanmadi)"))
    request_timeout_s: float = field(default_factory=lambda: float(os.getenv("REQUEST_TIMEOUT_S", "20")))
    min_request_interval_s: float = field(default_factory=lambda: float(os.getenv("MIN_REQUEST_INTERVAL_S", "4")))
    max_pages_per_store: int = field(default_factory=lambda: int(os.getenv("MAX_PAGES_PER_STORE", "8")))
    timezone: str = field(default_factory=lambda: os.getenv("TZ_NAME", "Europe/Istanbul"))
    feed_export_path: str = field(default_factory=lambda: os.getenv("FEED_EXPORT_PATH", str(ROOT / "dist" / "feed.json")))
    def image_policy(self, store_id: str) -> str:
        """IMAGE_POLICY_<MAĞAZA>=link_only ile görseller kapatılabilir (uygulama güncellemesi gerekmez)."""
        v = os.getenv(f"IMAGE_POLICY_{store_id.upper()}", os.getenv("IMAGE_POLICY", "official_cdn"))
        return v if v in ("official_cdn", "link_only") else "link_only"

    # Kaynak hatasında yöneticiye bildirim: JSON {"text": ...} POST edilir (Slack/Discord/Telegram köprüsü vb.).
    alert_webhook_url: str = field(default_factory=lambda: os.getenv("ALERT_WEBHOOK_URL", ""))
    enabled_live_stores: tuple[str, ...] = field(default_factory=lambda: tuple(
        s for s in os.getenv(
        "LIVE_STORES", "a101,bim,sok,migros,tkkoop,hakmar,watsons,metro,bizimtoptan,gratis").split(",") if s))

    # ---- günlük senkronizasyon, duyuru ve push
    http_cache_dir: str = field(default_factory=lambda: os.getenv("HTTP_CACHE_DIR", str(ROOT / "backend" / "data" / "http-cache")))
    log_dir: str = field(default_factory=lambda: os.getenv("LOG_DIR", str(ROOT / "backend" / "data" / "logs")))
    log_max_bytes: int = field(default_factory=lambda: int(os.getenv("LOG_MAX_BYTES", str(5 * 1024 * 1024))))
    log_keep_days: int = field(default_factory=lambda: int(os.getenv("LOG_KEEP_DAYS", "7")))
    # Bir katalog, kaynakta ilk görülmesinden en fazla bu kadar gün sonra "yeni" olarak duyurulabilir.
    announce_max_age_days: int = field(default_factory=lambda: int(os.getenv("ANNOUNCE_MAX_AGE_DAYS", "3")))
    # Varsayılan bildirim saati (Europe/Istanbul). Push bu saate zamanlanır; daha geç çalışılırsa hemen gönderilir.
    notify_time: str = field(default_factory=lambda: os.getenv("NOTIFY_TIME", "09:00"))
    # ntfy push (isteğe bağlı): sunucu adresi + konu öneki. Erişim anahtarı yalnızca sunucu ortamında tutulur.
    ntfy_url: str = field(default_factory=lambda: os.getenv("NTFY_URL", "").rstrip("/"))
    ntfy_topic: str = field(default_factory=lambda: os.getenv("NTFY_TOPIC", ""))
    ntfy_token: str = field(default_factory=lambda: os.getenv("NTFY_TOKEN", ""))
    # Geçmiş budama: fiyat gözlemleri ve çalıştırma kayıtları bu süreden eskiyse silinir.
    keep_price_history_days: int = field(default_factory=lambda: int(os.getenv("KEEP_PRICE_HISTORY_DAYS", "180")))
    keep_run_log_days: int = field(default_factory=lambda: int(os.getenv("KEEP_RUN_LOG_DAYS", "60")))

    product_pages_per_store: int = field(default_factory=lambda: int(os.getenv("PRODUCT_PAGES_PER_STORE", "150")))

    @property
    def push_enabled(self) -> bool:
        return bool(self.ntfy_url and self.ntfy_topic)

    def notify_hm(self) -> tuple[int, int]:
        try:
            h, m = self.notify_time.split(":")
            return max(0, min(23, int(h))), max(0, min(59, int(m)))
        except ValueError:
            return 9, 0

    def schedule_for(self, store_id: str) -> list[tuple[int, int]]:
        raw = os.getenv(f"SCHEDULE_{store_id.upper()}", DEFAULT_SCHEDULE.get(store_id, "07:00"))
        out = []
        for part in raw.split(","):
            h, m = part.strip().split(":")
            out.append((int(h), int(m)))
        return out
