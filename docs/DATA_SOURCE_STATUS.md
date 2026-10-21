# Veri kaynağı durumu

## 1.4.0 — İstemci Katalog Köprüsü

Bu 6 marketin **broşürleri sunucuda kazınmaz** (`config/client_bridge.json`, `"client_rendered": true`); sunucu
durumu `client_bridge` yazar (hata değil). Uygulama resmî sayfayı kullanıcı dokunduğunda cihazda açar ve broşürü
yerel okuyucuya aktarır. Ürün fiyatı toplama (ŞOK, Migros sanal market) sunucuda sürer.

| Market | Resmî sayfa | Profil | Uygulamada |
|---|---|---|---|
| A101 | https://www.a101.com.tr/afisler | a101 | afiş görselleri → okuyucu |
| ŞOK | https://kurumsal.sokmarket.com.tr/tr/firsatlar | sok | resmî PDF → PDF okuyucu |
| Migros | https://migroskop.migros.com.tr/migroskop | migroskop | Migroskop sayfaları → okuyucu (money.com.tr PDF arşivi sunucuda kalır) |
| Gratis | https://www.gratis.com/gratis-katalog | fliphtml5 | FlipHTML5 kitabı → okuyucu |
| CarrefourSA | https://katalog.carrefoursa.com/books/mwis | fliphtml5 | FlipHTML5 kitabı → okuyucu |
| Rossmann | https://www.rossmann.com.tr/kataloglar | generic | flipbook/PDF/büyük görseller → okuyucu |

## 1.1.0 (canlı veritabanı, 21.10.2026 17:15 UTC)

Güncel tablo her çalıştırmada üretilir: `status.json` (GitHub Pages) ve uygulamada Ayarlar → Kaynak durumu.
Kurtarma ayrıntıları: `CATALOG_RECOVERY_REPORT.md`. Zamanlama: 08:05 ve 00:05 (İstanbul). Şimdi dene: Actions → gunluk-veri → stores.

| Market | Durum | Son başarı (UTC) | Aktif katalog | Sayfalı | PDF | Ürün | Görselli | Not / hata |
|---|---|---|---:|---:|---:|---:|---:|---|
| A101 | error | 2026-10-13 05:12 | 2 | 0 | 0 | 39 | 38 | ConnectorError: 403 yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı: https://www.a101.com.tr/aldin-aldin [engell |
| BİM | partial | 2026-10-21 16:47 | 22 | 22 | 0 | 496 | 496 | 496 ürün, 12 katalog · yöntem: live_html · uyarı: 10 ürün fiyatı/adı ayrıştırılamadığı için reddedildi |
| Bizim Toptan | link_only | 2026-10-21 16:49 | 8 | 0 | 0 | 0 | 0 | 0 ürün, 4 katalog · yöntem: live_html |
| CarrefourSA | error | - | 0 | 0 | 0 | 0 | 0 | ConnectorError: CarrefourSA Katalog: hiçbir resmî kaynakta broşür bulunamadı · denemeler: robots.txt izin vermiyor: http |
| File Market | link_only | 2026-10-21 17:14 | 1 | 1 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: discovery |
| Gratis | link_only | 2026-10-21 16:50 | 2 | 0 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |
| Hakmar | link_only | 2026-10-21 16:49 | 3 | 3 | 0 | 0 | 0 | 0 ürün, 2 katalog · yöntem: live_html |
| Metro | link_only | 2026-10-21 16:49 | 4 | 3 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |
| Migros | ok | 2026-10-21 16:48 | 3 | 1 | 0 | 236 | 236 | 236 ürün, 2 katalog · yöntem: live_site_json · bilgi: 7 ürün kaynakta artık yok → yayından kaldırıldı |
| Onur Market | link_only | 2026-10-21 17:15 | 6 | 0 | 6 | 0 | 0 | 0 ürün, 6 katalog · yöntem: live_html |
| Rossmann | error | 2026-10-21 16:21 | 1 | 0 | 0 | 0 | 0 | ConnectorError: Rossmann Katalog: hiçbir resmî kaynakta broşür bulunamadı · denemeler: statik: https://www.rossmann.com. |
| ŞOK | ok | 2026-10-21 16:48 | 81 | 1 | 0 | 110 | 110 | 107 ürün, 59 katalog · yöntem: live_html · bilgi: 5 ürün kaynakta artık yok → yayından kaldırıldı |
| Tarım Kredi | ok | 2026-10-21 16:49 | 2 | 0 | 0 | 64 | 60 | 64 ürün, 1 katalog · yöntem: live_html |
| Watsons | link_only | 2026-10-21 16:49 | 1 | 1 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |

Durumlar: ok = tam · partial = kısmi · link_only = katalog/broşür var, ürün listesi yok · error = sunucuda okunamadı
(A101/CarrefourSA: Cloudflare bot sınaması; aşılmaz → uygulama cihazda resmî sayfadan okur).
