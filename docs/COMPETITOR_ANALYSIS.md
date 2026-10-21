# Rakip analizi (Google Play mağaza sayfaları, 21.10.2026)

Kaynak: yalnızca herkese açık Google Play listeleri (açıklama, sürüm notları, görünen yorumlar). APK'lar indirilmedi ve
incelenmedi: resmî indirme yolu yok, üçüncü taraf APK siteleri güvenilmez, ayrıca rakiplerin özel sunucularını/anahtarlarını
çıkarmak izinsiz erişim olurdu. REA ile statik APK analizi bu nedenle yalnızca kendi APK'mız için denendi (bkz. TEST_REPORT).

| Uygulama | İndirme | Puan | Son güncelleme | Resmî mi / kaynak | Öne çıkanlar | Görünen şikayetler |
|---|---|---|---|---|---|---|
| Aktüel Ürünler Kataloğu (Modern Uygulamalar) | 1 M+ | 4,5 | 25.07.2026 | Kaynak belirtilmiyor | yeni katalog bildirimi, yakınlaştırma, favoriler | çok fazla reklam ("10 dk reklam") |
| Market Aktüel Broşürleri (Ahmet Yüzlü) | 1 M+ | 4,1 | 17.08.2026 | "Broşürler markalar tarafından sağlanmamaktadır", kaldırma talebi kabul | ~231 marka, marka bazlı bildirim | broşür yüklenmiyor (Xiaomi), kapanmayan reklamlar |
| Aktüel Ürünler - A101 Bim Şok (Rise of Brains) | 1 M+ | 4,2 | 03.09.2026 | Kaynak belirtilmiyor | ~90 mağaza, kırpıp paylaşma, kategori filtresi | market seçimi çalışmıyor, yakınlaştırınca bulanık |
| Güncel Aktüel Ürünler (SuloSoft) | 100 B+ | 4,7 | 06.07.2026 | Kaynak belirtilmiyor | çift dokun yakınlaştır, kaydırarak sayfa | senkron/takılma endişesi |
| Aktüel Ürünler: İndirim Kuponu (MaruStudio) | 500 B+ | 4,6 | 04.09.2026 | "Resmî uygulama değildir", kaldırma talebi | haftalık önbellek temizliği | güncelleme sonrası açılmama |
| Aktüel Ürünler: Market Katalog (Raider Software) | 500 B+ | 4,8 | 31.10.2025 | "Resmî uygulaması değildir" | mağaza adıyla arama | yakınlaştırma sorunu, favori yok |

## Doğrulanabilen bulgular
- Hiçbiri resmî değil; hiçbiri kaynağını belirtmiyor. İkisi broşürlerin marketlerden gelmediğini ve "kaldırma talebi"
  usulüyle çalıştığını açıkça yazıyor. Cimri broşür sayfaları da görselleri kendi CDN'inde (cdn.cimri.io) barındırıyor,
  kaynak bağlantısı vermiyor. → Rakipler broşür görsellerini **kopyalayıp yeniden barındırıyor**; bu FIRSAT360'ın
  kurallarına (izinsiz üçüncü taraf içerik yok) aykırı olduğu için taklit edilmedi.
- Hepsinde reklam var; en sık şikayet reklam yoğunluğu, ikinci sık şikayet yüklenmeyen/bulanık broşür.

## FIRSAT360'ın ayrıştığı yerler (gerçek veriyle)
- Resmî kaynak + kaynak bağlantısı + son kontrol zamanı her katalogda; doğrulanmamış görsel gösterilmez.
- Ürün düzeyinde veri (fiyat, önceki fiyat beyanı, görsel) ve Türkçe akıllı arama; rakipler yalnızca sayfa görseli.
- Kaynak durumu ekranı, "Hatalı bilgi bildir" ve otomatik kalite ajanları.
- Reklamsız (şimdilik); yakınlaştırma tam çözünürlüklü resmî görselle.
- Eksik: A101/CarrefourSA broşürlerinin sunucuda toplanması (engel/robots) → cihazda resmî sayfadan okuma ile kapatılıyor.
