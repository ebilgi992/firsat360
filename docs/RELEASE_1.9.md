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

## Kullanıcının belirttiği A101AktuelRobot entegrasyonu

[keyiflerolsun/A101AktuelRobot](https://github.com/keyiflerolsun/A101AktuelRobot) için yayımlı `A101.json`
şemasını okuyan bağımsız adaptör günlük veri hattına eklendi. Sonuç `status.json/community_a101_robot` alanındadır.
Repo 29 Eylül 2023'ten beri arşivli; yayımlı JSON Eylül 2023 CMS görselleri içeriyor. Gerçek JSON adaptörde denendi:
arşiv/güncellik kontrolünden geçmedi. Bugün commit edilmiş gibi denendiğinde de eski CMS görsel tarihleri reddedildi.
[Makine tarafından doğrulanan kontrol özeti](A101_ROBOT_CHECK_2026-10-26.json).

Kaynağın robot kodu GPL-3.0, Telegram ve `aiocfscrape.CloudflareScraper` kullanıyor. Bot kodu/koruma çözme bağımlılığı
uygulamaya kopyalanmadı veya çalıştırılmadı; mesaj gönderilmedi. Uyumluluk adaptörü kendi kodumuzdur; yalnız yayımlı
JSON ve depo/commit metadatasını denetler. Kaynak tekrar aktif ve taze olursa broşür adayları tanınır; resmî kampanya
tarihi doğrulanmadan ürün/fiyat veya güncel katalog üretilmez. Eski 2023 görselleri uygulamada bugünkü broşür sayılmaz.

## current-products-online incelemesi ve uyarlama

[AbdulazizFMMS/current-products-online](https://github.com/AbdulazizFMMS/current-products-online) bir Android istemcisidir;
son depo güncellemesi 20 Nisan 2025. `Post` modeli Blogger alanlarını (`title`, `content`, `published`, `labels`, `url`)
ve istemci hem Blogger JSON hem uzak uygulama yapılandırmasını kullanır. Hazır güncel broşür veri kümesi veya
herkese açık canlı Blogger besleme adresi depoda sunulmuyor. Yapılandırma, uygulama kimliğine bağlı erişim anahtarıyla
alınıyor. Başka uygulamanın anahtarı çözülmedi, kullanılmadı ve projeye aktarılmadı. Açık lisans dosyası bulunmadığından
Java kodu kopyalanmadı.

Bağımsız `backend/firsat360/blogger.py` adaptörü Blogger v2 (`feed.entry`) ve v3 (`items`) JSON alanlarını okur;
yayın tarihi, kampanya tarihi ve gerçek market CDN görsellerini denetler. Küçük resim yerine gönderinin büyük görsel
bağlantısını seçer. Tarihsiz/eski içerik ve doğrulanmamış üçüncü taraf görsel aynaları güncel resmî katalog sayılmaz.
`config/public_brochure_feeds.json` içinde store/url eşleştirmesiyle herkese açık, anahtarsız HTTPS besleme tanımlanabilir.
Varsayılan liste boştur; günlük `status.json/community_blogger` durumu `not_configured` olur. Canlı veri aktarımı
başlamış gibi gösterilmez. Veriyi gerçekten bağlamak için kullanıma açık Blogger/JSON besleme adresi gereklidir.

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

Kotlin çekirdek testleri: 216 geçti. Python ve tam APK kontrolleri CI'da çalıştırılır: kaynak ayrıştırma,
izinli alan/kuruş/miktar sınırları, okuyucu açılış önceliği, tam Android derleme, minSdk 26 başvuruları ve v2/v3 imza.
Gerçek telefonda her marketin WebView sürümü, uzak sayfanın o andaki durumu ve bildirim teslimi test edilmedi.
Dış sitelerin erişim ve içerik değişiklikleri için hatasız çalışma garantisi verilemez; mevcut veri ve resmî bağlantı
alternatifleri korunur. Standart `com.firsat360.app` ve alternatif `com.firsat360.beta` paketleri aynı beta anahtarıyla sunulur.
