# FIRSAT360 1.9.0 Beta (21)

## Broşür açılışı

- Katalogda ürünler olsa bile sayfa görselleri önce yakınlaştırılabilir okuyucuda açılır. PDF ikinci önceliktir.
  Görseli/PDF'i olmayan ürün katalogları ürün listesini korur; olmayan broşür görseli uydurulmaz.
- Web kataloglarında mağaza/profil bilgisi görsel çıkarma motoruna aktarılır. Önceden bu bilgi eksik olduğu için
  bazı kartlar yalnız web sayfasını açıyor, broşür görsellerine geçemiyordu.
- Tek sayfalık afişler geçerli broşür kabul edilir. İki sayfa alt sınırı kaldırıldı.
- Belirli katalog URL'si açıldığında başka haftanın mağaza önbelleğine düşülmez.
- Okuyucuda ürün listesi kısayolu bulunur; sayfa değişirken eski yükleme geri dönüşleri yeni ekrana uygulanmaz.
- Görselleri kapatan veri tasarrufu tercihi korunur. Kaynak görsel sunmuyorsa resmî sayfa alternatifi gösterilir.

## Asistan

- `100 TL altı 1 litre süt`: bütçe ve toplam paket hacmi birlikte süzülür.
- `Litre fiyatına göre süt`: birim fiyat sıralaması; kartta paket fiyatı, yanıtta TL/L ayrı belirtilir.
- `Kilogram fiyatına göre yoğurt`: kilogram karşılaştırması. Ölçüsü bilinmeyen veya uyumsuz ürün sıralamaya girmez.
- Belirli ürün özelliği bulunamazsa tek geniş kelimeye düşülmez; ör. laktozsuz istenince normal süt önerilmez.
- Sayfa/liste fiyatı çelişen, fiyatı geçersiz ve süresi bitmiş ürünler önerilmez. Bütçe para hesabı önce kuruşa yuvarlanır.
- Önceki ürünün ardından `Peki Migros'ta?` gibi market soruları ürün bağlamını kullanır; açık market özeti sorusu ayrı kalır.
- İşleme arka planda yapılır; yapay bekleme kaldırıldı. Aynı anda gönderilen istekler sırayı bozmaz; ekran kapalıyken
  sonuç eski görünüme yazılmaz. Gün değişince asistanın tarih bağlamı yenilenir. Girdi 400 karakterle sınırlıdır.
- Asistan cihazda çalışır; ücretli bir bulut modeli veya sınırsız bilgi iddiası yoktur. Şube/stok garantisi verilmez.

## GitHub ve veri kaynakları

İncelenen güncel depolar:

| Depo | Kullanım / karar |
|---|---|
| [gokhancvs/market-fiyati-mcp](https://github.com/gokhancvs/market-fiyati-mcp) | Aktif MIT kodu. `src/money.ts` içindeki ondalık gösterimden kuruş normalizasyonu yaklaşımı Kotlin `PriceCents` olarak uyarlandı. Tam lisans eklendi. Şube fiyatları konumsuz ulusal aktüel fiyat gibi kopyalanmadı. |
| [onurcan-b/acik-sepet](https://github.com/onurcan-b/acik-sepet) | 26 Ekim tarihli veri/kalite raporu var. Endeks ve şube gözlem verisi, broşür değildir. NOTICE ham veriye kod lisansının uygulanmadığını belirtir; toplu fiyat aynası eklenmedi. |
| [emreozakyildiz/puppeteer-scraper](https://github.com/emreozakyildiz/puppeteer-scraper) | MIT, ancak koruma atlatmaya dönük stealth eklentisi kullanıyor; mevcut A101 erişim politikasına taşınmadı. |
| [codermert/A101_Afis](https://github.com/codermert/A101_Afis) | Önceki hata/eskilik/resmî CDN kontrolleri korunur. Hatalı çıktı güncel veri sayılmaz. |

A101 için resmî sayfada gömülü `Product` JSON-LD alternatifi eklendi. Yalnız açık `TRY` fiyat, aynı marketin ürün
adresi ve resmî CDN görseli kabul edilir. Belirsiz çoklu varyant fiyatı reddedilir. Bu ayrıştırıcı 403 engelini aşmaz;
engelli kaynağın çalıştığı iddia edilmez. TK Koop'ta kartın tam `title` alanı kullanılarak kesilmiş ürün/ambalaj adları düzeltilir.

## Instagram

Resmî A101 (`a101iletisim`), BİM (`bimturkiye`), ŞOK (`sokmarketler`) ve Migros (`migros_tr`) hesapları market ekranına
eklendi. Hesaplar resmî profil ve kurumsal bağlantılarla kontrol edildi. A101'in herkese açık güncel paylaşımına doğrudan
okuma denemesi erişime kapalı kaldı. Oturum, CAPTCHA, özel API veya geçici Instagram CDN adresleri üzerinden otomatik
broşür toplama eklenmedi. Paylaşım görseli gerçekten alınmış gibi sayfa/fiyat üretilmez; hesap normal tarayıcıda açılır.

Kaynak örnekleri:
- https://www.instagram.com/a101iletisim/
- https://www.instagram.com/bimturkiye/
- https://kurumsal.sokmarket.com.tr/uploads/pdf/2022090814173250142.pdf
- https://migrostv.migros.com.tr/eker-suzme-yogurt-askini-ariyor

## Doğrulama ve sınırlar

Kotlin çekirdek testleri: 215 geçti. Python ve tam APK kontrolleri CI'da çalıştırılır: kaynak ayrıştırma,
izinli alan/kuruş/miktar sınırları, okuyucu açılış önceliği, tam Android derleme, minSdk 26 başvuruları ve v2/v3 imza.
Gerçek telefonda her marketin WebView sürümü, uzak sayfanın o andaki durumu ve bildirim teslimi test edilmedi.
Dış sitelerin erişim ve içerik değişiklikleri için hatasız çalışma garantisi verilemez; mevcut veri ve resmî bağlantı
alternatifleri korunur. Standart `com.firsat360.app` ve alternatif `com.firsat360.beta` paketleri aynı beta anahtarıyla sunulur.
