# Veri kaynağı durumu (canlı veritabanı, 21.10.2026 16:18 UTC çalıştırması + kalite orkestratörü)

Canlı ve güncel tablo her çalıştırmada üretilir: `status.json` (GitHub Pages) ve uygulamada Ayarlar → Kaynak durumu.
Kaynak adresleri ve yasal notlar: `docs/KAYNAKLAR.md`. Zamanlama: 08:05 ve 00:05 (İstanbul), GitHub Actions.

| Market | Durum | Son başarı (UTC) | Aktif katalog | Sayfalı | Ürün | Görselli | Not / hata |
|---|---|---|---:|---:|---:|---:|---|
| A101 | error | 2026-10-13 05:12 | 2 | 0 | 39 | 38 | ConnectorError: 403 yanıtı (erişim engeli/hız sınırı); aşılmaya çalışılmadı: https://www.a101.com.tr/aldin-ald |
| BİM | partial | 2026-10-21 16:18 | 22 | 22 | 496 | 496 | 496 ürün, 12 katalog · yöntem: live_html · uyarı: 10 ürün fiyatı/adı ayrıştırılamadığı için reddedildi · bilgi |
| Bizim Toptan | link_only | 2026-10-21 16:21 | 8 | 0 | 0 | 0 | 0 ürün, 4 katalog · yöntem: live_html |
| CarrefourSA | error | - | 0 | 0 | 0 | 0 | ConnectorError: CarrefourSA Katalog: hiçbir resmî kaynakta broşür bulunamadı · denemeler: robots.txt izin verm |
| File Market | error | - | 0 | 0 | 0 | 0 | ConnectorError: File Market Broşür: hiçbir resmî kaynakta broşür bulunamadı · denemeler: statik başarısız: htt |
| Gratis | link_only | 2026-10-21 16:21 | 2 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |
| Hakmar | link_only | 2026-10-21 16:20 | 3 | 3 | 0 | 0 | 0 ürün, 2 katalog · yöntem: live_html |
| Metro | link_only | 2026-10-21 16:20 | 4 | 3 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |
| Migros | ok | 2026-10-21 16:19 | 3 | 1 | 239 | 239 | 239 ürün, 2 katalog · yöntem: live_site_json · bilgi: 1 ürün kaynakta artık yok → yayından kaldırıldı |
| Onur Market | error | - | 0 | 0 | 0 | 0 | ConnectorError: Onur Market İnsert: hiçbir resmî kaynakta broşür bulunamadı · denemeler: statik: https://kurum |
| Rossmann | link_only | 2026-10-21 16:21 | 1 | 0 | 0 | 0 | 0 ürün, 1 katalog · yöntem: discovery |
| ŞOK | ok | 2026-10-21 16:19 | 81 | 1 | 115 | 115 | 112 ürün, 59 katalog · yöntem: live_html · bilgi: 5 ürün kaynakta artık yok → yayından kaldırıldı |
| Tarım Kredi | ok | 2026-10-21 16:20 | 2 | 0 | 64 | 60 | 64 ürün, 1 katalog · yöntem: live_html |
| Watsons | link_only | 2026-10-21 16:20 | 1 | 1 | 0 | 0 | 0 ürün, 1 katalog · yöntem: live_html |

Durumlar: ok = tam okundu · partial = bir kısmı okundu · link_only = yalnızca katalog bağlantısı/kapak · error = okunamadı
(neden yanda; erişim engeli ve robots.txt aşılmaz, uygulama resmî sayfayı cihazda açar).
