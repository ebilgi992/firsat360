# Değişiklik günlüğü

## 0.6.1-beta — 14 Ekim 2026
- **Tamamen ücretsiz, sunucusuz:** yönetim paneli, HTTP API, sunucu zamanlayıcısı, Docker/Caddy yapılandırması
  kaldırıldı. Veri hattı yalnızca GitHub Actions'ta çalışır; uzaktan ayarlar `config/app_config.json` dosyasından.
- **Gösterilmeyen kataloglar artık uygulamada:** A101 afişleri, Migroskop dijital katalog ve tüm flipbook kataloglar
  (Watsons, Metro, Bizim Toptan ×3, Gratis) listelerde görünüyor ve uygulama içinde tam ekran açılıyor (önceden ya hiç
  listelenmiyor ya da dış bağlantıydı). Günlük çalıştırmada: Publitas kataloglarının (Watsons, Metro) **tüm sayfa
  görselleri** her sayfanın resmî görsel adresinden, FlipHTML5 (Gratis) sayfaları varlığı doğrulanarak çekilir ve
  uygulamanın yakınlaştırılabilir okuyucusunda gösterilir; Migroskop için aynı sayı numaralı resmî PDF doğrulanır.
- **Sade arayüz:** ana sayfa = arama + Günün İndirimleri + Kataloglar + Marketler. "Son baktıkların", hikâye halkaları,
  kategori/öne çıkan bölümleri, ızgara/liste seçici, arama sıralama/kategori satırları, ürün ekranındaki ayrıntı
  satırları ve gereksiz ayarlar kaldırıldı. Ayarlar: Bildirimler, Görseller ve depolama, Hakkında.

## 0.6.0-beta — 14 Ekim 2026
- **Arama motoru:** Türkçe kurallarına uygun iki aşamalı normalizasyon (I→ı / İ→i birebir anahtar + ş/ç/ğ/ı/ö/ü ve aksan
  katlamalı gevşek anahtar), boşluk temizleme, market adı algılama ("bim", "BİM market", "a 101", "şok", "Tarım Kredi" →
  o marketin tüm fırsatları; "bim süt" → BİM'de süt), ürün/marka/ölçü/kategori/market/katalog alanlarında arama, yalnızca
  tam sonuç yoksa devreye giren kontrollü yazım hatası toleransı ("sampuqn" → "şampuan" sonuçları, bilgilendirmeyle),
  anlık sonuç (kısa gecikmeli), tek dokunuşla temizleme, son 10 arama (ayarlardan kapatılır), anlaşılır boş durum ve
  "Filtreleri temizle", kaydırınca klavyenin kapanması, geri tuşuyla aramanın temizlenmesi, market ve katalog sonuçları.
- **Katalog kartları:** tek tasarım sistemi; aynı satırda eşit genişlik/yükseklik, sabit 3:4 kapak alanı (kırpmadan sığdırma),
  dar ekranda (320 dp) iki kart, 2 satırlık sabit başlık alanı, uzun market adlarında "…", kapak yok/yüklenemedi durumunda
  market renkli yer tutucu, "YENİ", sayfa/PDF/ürün rozeti, ekran okuyucu açıklaması.
- **Son baktıkların** (ana sayfa, yalnızca cihazda).
- **Güvenlik:** yönetim paneli baştan yazıldı (oturum, CSRF, kaba kuvvet kilidi, IP sınırı, roller, TOTP 2FA, denetim kaydı,
  güvenlik başlıkları, varsayılan hesap yok); analitik yazma ucu kaldırıldı; herkese açık API hız sınırı + önbellek + gzip;
  WebView ve harici bağlantı şemaları sıkılaştırıldı; JSON ayrıştırıcıya derinlik/sınır koruması. Ayrıntı: `docs/GUVENLIK_RAPORU.md`.
- **Yönetim paneli:** durum/sağlık, kaynak yönetimi ve elle tarama, tam senkronizasyon, bildirim hazırlama-önizleme-gönderme
  (mükerrer engelli), reklam alanları uzaktan yapılandırması, kullanıcılar, hesap/2FA, denetim kaydı. `docs/YONETIM_PANELI.md`.
  Sunucu modu: Docker Compose + Caddy (otomatik HTTPS), günlük senkronizasyon sunucunun zamanlayıcısında.
- **Yayın hazırlığı:** `RELEASE=1` ile imzasız/imzalı release APK + **AAB** (bundletool), `tools/sign-release.sh`, gizlilik
  politikası sayfası (GitHub Pages'te yayımlanır), Play beyanları ve veri envanteri, mağaza metinleri, kontrol listesi
  (`docs/PLAY_YAYIN.md`), reklam rehberi (`docs/REKLAM_REHBERI.md`). Sürüm 0.6.0-beta (versionCode 7).

## 0.5.0-beta — 14 Ekim 2026
- **Canlı, otomatik veri hattı:** elle hazırlanan anlık görüntü yerine her gün iki kez (07:05, 18:05) çalışan
  GitHub Actions iş akışı (`.github/workflows/daily.yml`, ücretsiz). 10 marketin tamamı için otomatik bağlayıcı:
  BİM (aktüel ürünler + afişler), A101 (Aldın Aldın + afişler), ŞOK (Haftanın Fırsatları + kampanyalar), Migros
  (Migroskop ürünleri + **Migroskop dijital katalog/PDF**), Tarım Kredi, Hakmar (broşür sayfaları), Watsons, Metro,
  Bizim Toptan, Gratis (kapak + resmî katalog). Ürün fotoğrafları her ürünün kendi resmî sayfasından otomatik
  doğrulanır (yalnızca yeni/7 günden eski olanlar açılır). Depodaki anlık görüntüler yalnızca boş veritabanında ilk
  tohum olarak kullanılır. Kurulum: `docs/CANLI_KURULUM.md`.
- **Günün İndirimleri:** her gün birçok marketten otomatik seçilen ürünler (mağazanın beyan ettiği indirim ≥ %10 veya
  yeni aktüel; görseli doğrulanmış; fiyat uyuşmazlığı olmayan; market başına en fazla 6, marketler sırayla).
  "Fiyatlar ve stok durumu mağazaya, şubeye ve saate göre değişebilir" notuyla.
- **Günlük katalog bildirimi:** "🛒 Günün katalogları yayınlandı!" — yeni katalogları tek bildirimde birleştirir, aynı
  katalog için tekrar bildirmez, eski kataloğu yeni göstermez (ilk görülme ≤ 72 saat), market bazında açılıp
  kapatılır, saat ayarlanır (varsayılan 09.00). Android 13+ izni doğru istenir; reddedilirse uygulama çalışmaya devam
  eder ve ayarlara nasıl gidileceği gösterilir. Dokununca ilgili katalog/market/yeni kataloglar açılır.
  Sunucu tarafı: duyurular tekilleştirilir, isteğe bağlı ntfy push 09.00'a zamanlanır (en fazla 3 deneme).
- **Kaldırıldı:** fiyat alarmı ve bildirimi; "Güncelleme sunucusu" ayarı (yayın adresi derlemede gömülü); tıklama
  ölçümü; kullanıcıya gösterilen teknik bilgiler (veri yöntemi, hukuki notlar, doğrulama ayrıntıları, ham durum kodları).
- **Premium arayüz:** yeni renk paleti, selamlama + arama, "Bugün" kahraman kartı, yeni kataloglar için hikâye
  halkaları, Günün İndirimleri şeridi ve ekranı, market ızgarası, renkli kategori çipleri, "YENİ" katalog rozeti,
  vurgulu alt gezinme, sadeleştirilmiş ürün/katalog/ayarlar ekranları, uygulama içi **PDF katalog okuyucu**.
- **Depolama ve pil:** görsel önbelleği sınırı (cihaza göre 40–100 MB, ayarlardan 50/100/200), LRU temizlik, etkin
  indirmeye dokunmayan temizlik, yarım indirmelerin silinmesi, PDF'ler yalnızca açılınca indirilir (en fazla 2 tutulur),
  isteğe bağlı **çevrim dışı kaydet** (önbellekten ayrı, ayarlardan silinir), koşullu feed indirme (ETag/304, gzip),
  bozuk/yarım veri önbelleğe yazılmaz, artan beklemeli yeniden deneme, Wi-Fi/mobil veri tercihleri, düşük bellekli
  cihazda daha az eş zamanlı indirme. Kısa günlük: üretimde yalnızca hata/uyarı, ≤7 gün, ≤5 MB, gizli değer maskeleme.
  Sunucu günlükleri de 5 MB / 7 gün sınırlı; eski fiyat gözlemleri (180 gün) ve çalıştırma kayıtları (60 gün) budanır.
- Feed şeması 2 (eklemeli; uygulama 1 ve 2'yi okur). Sürüm 0.5.0-beta (versionCode 6), 0.4.0 ile aynı imza anahtarı →
  üzerine güncellenir, favoriler/liste korunur.

## 0.4.0-beta — 13 Ekim 2026
- **Her ürüne doğrulanmış resmî fotoğraf:** 497 ürün sayfası tek tek açıldı (BİM 344, A101 39, ŞOK 49, Tarım Kredi 65);
  her ürünün fotoğrafı **kendi resmî ürün sayfasından** alındı. Eşleşme içe aktarmada otomatik doğrulanıyor
  (`backend/firsat360/imagematch.py`): aynı detay adresi + ad benzerliği ≥ 0,5 + ambalaj ölçüsü uyumu (1 L = 1000 ml).
  Uyuşmayan görsel eklenmiyor. Sonuç: **531 üründen 525'inde resmî fotoğraf** (495 ürün sayfası doğrulamalı +
  30 Migros resmî verisi). Kalan 6 ürünün resmî kaynakta fotoğrafı yok (Tarım Kredi sayfasında "yok.png" yer tutucusu
  ×4, A101 sayfası boş, ŞOK Kotex ürününün sayfası yok). Bunlarda başka ürünün görseli yerine "resmî fotoğraf yok"
  açıklamalı monogram gösteriliyor.
- **Fiyat uyuşmazlığı işareti:** ürün sayfasındaki fiyat kampanya listesinden farklıysa fiyat değiştirilmiyor,
  kullanıcıya uyarı gösteriliyor (3 ürün: BİM Milföy Hamuru 155,00/149,50; Tarım Kredi Pilavlık Bulgur 99,00/107,50;
  Limonata Karadut 32,90/38,50).
- **Yeni marketler (6):** Tarım Kredi Kooperatif (65 ürün + fiyat, "Kat Kat Fırsat 13–19 Ekim"), Hakmar (2 broşür,
  16 resmî sayfa görseli, uygulama içi okuyucuda), Watsons, Metro, Bizim Toptan (3 katalog), Gratis (resmî kapak + resmî
  katalog bağlantısı). Toplam 10 market, 34 katalog.
- **Premium arayüz:** büyük fotoğraflı 2 sütunlu ürün ızgarası (liste görünümüne geçilebilir), ana sayfada
  "Öne çıkan fırsatlar" (marketler arasında dengeli), kaydırılabilir mağaza çipleri/kutucukları, fotoğraflı indirim
  kartları, ürün detayında büyük fotoğraf + fotoğrafın kaynağı ("ürünün resmî sayfasından · eşleşme doğrulandı",
  dokununca o sayfa açılır), **"Diğer marketlerde"** karşılaştırması (yalnızca aynı ölçü + ad örtüşmesiyle; emin
  olunmayan eşleşme gösterilmez), ölçüsü listede yazmayan ürünlerde ürün sayfasındaki ölçü, mağaza rengine göre
  okunabilir yazı rengi, yeni bildirim simgesi, liste görsellerinde mağazanın kendi küçük boyut parametresi (veri
  tasarrufu; yüklenmezse özgün adres).
- **Düzeltildi:** uygulama güncellendiğinde eski sunucu önbelleği yeni gömülü veriyi gölgeliyordu → artık
  `generated_at`'i yeni olan seçiliyor. Mağaza sırası/renkleri/kısaltmaları 4 mağazaya sabit kodluydu → feed'den.
  Doğrulanmış ürün sayfası görseli, kaynağı görselsiz sonraki okumada silinmiyor.
- **İmza:** proje içinde sabit DEBUG anahtarı (`android-app/signing/`). Ortam sıfırlandığı için 0.4.0 önceki
  sürümlerden FARKLI anahtarla imzalı → **eski sürümü kaldırıp kurun**; bundan sonraki sürümler üzerine güncellenir.
- **Kurulum yedeği:** `FIRSAT360-0.4.0-beta-yeni-paket-debug.apk` — aynı uygulama, farklı kurulum kimliği
  (`com.firsat360.beta`). Eski sürüm/kalıntı kaydı olsa bile imza çakışması olmadan kurulur (`APP_ID=` ile derlenir).

## 0.3.0-beta — 13 Ekim 2026
- **Gerçek katalog sayfaları:** BİM resmî "Afişler" sayfasından 11 tarihe ait 28 katalog sayfası (tam boy + küçük
  resim), ŞOK'un 55 kampanya görseli; Migroskop Dijital Sayı 908 (8–21 Ekim) resmî bağlantısı. Görseller mağazaların
  kendi CDN'inden gösterilir, FIRSAT360 sunucusuna kopyalanmaz; mağaza bazında `IMAGE_POLICY_<MAĞAZA>=link_only` ile
  uygulama güncellemesi gerekmeden kapatılabilir.
- **Katalog okuyucu (dijital dergi):** tam ekran, kaydırarak sayfa geçişi, iki parmakla 6x'e kadar ve çift dokunuşla
  yakınlaştırma, sayfa küçük resimleriyle istenen sayfaya gitme, sonraki sayfayı önden yükleme, disk önbelleği,
  katalog favorisi, paylaşma, sayfaya özel resmî kaynak bağlantısı.
- **Gerçek ürün görselleri:** Migros'un 30 ürününde resmî ürün fotoğrafı (liste + büyük boy); görseli olmayan ürüne
  asla başka ürünün görseli konmaz (monogram gösterilir). Canlı bağlayıcılar BİM/A101/ŞOK görsellerini de okur.
- **Yeni:** alışveriş listesi (mağazaya göre gruplu, bilinen toplam, paylaşma), fiyat alarmı (hedef fiyat veya
  herhangi bir düşüş), favori mağazalar ve kataloglar, yeni katalog ve fiyat düşüşü bildirimleri (Android 13+ izin,
  6 saatte bir sunucu kontrolü), görselleri kapatma/önbellek temizleme, alt menüde "Listem".
- **Gelir altyapısı (pasif):** feed'de reklam/satış ortaklığı/sponsorlu/premium bayrakları (hepsi kapalı), "Sponsorlu"
  etiketli ayrı alan, anonim tıklama ölçümü (`POST /v1/events`, `GET /admin/events`); gelir ölçülmüyor diye açıkça raporlanır.
- **Backend:** BİM afiş ayrıştırıcı, görsel adresi çıkarımı (gecikmeli yükleme dahil), iki farklı aylı tarih aralıkları
  ("24 Mart-31 Aralık"), var olan veritabanına eksik sütunları ekleyen otomatik göç.
- Rakip analizi: `docs/RAKIP_ANALIZI.md`.

## 0.2.1-beta — 13 Ekim 2026 (acil düzeltme)
- **Düzeltildi — açılışta çökme (Android 11+, ör. Xiaomi 14T Pro):** `MainActivity` durum çubuğu görünümünü
  `window.insetsController` ile `setContentView()`'dan ÖNCE ayarlıyordu; pencere görünümü henüz oluşmadığı için
  `NullPointerException` (MainActivity.kt:75). Ayar artık pencere oluştuktan sonra ve hata korumalı yapılıyor.
- **Güvenlik ağı:** açılışta veya bir ekranda beklenmeyen hata olursa uygulama kapanmaz; hata ekranı ve
  "Hata raporunu paylaş" düğmesi gösterilir. Yakalanmayan hatalar cihazda kaydedilir ve sonraki açılışta ana sayfada
  paylaşma seçeneği sunulur (rapor internete otomatik gönderilmez).
- **Derleme denetimi:** `tools/check_startup_order.py` — pencereye bağlı çağrıların `setContentView`'dan önce
  yapılmasını derlemede engeller (0.2.0 koduna karşı çalıştırıldığında bu hatayı yakalıyor).

## 0.2.0-beta — 13 Ekim 2026
- **Gerçek veri:** 13.10.2026 anlık görüntüsü — 466 yayında ürün (BİM 347 / 5 sekme, ŞOK 50, A101 39, Migros 30).
  12.10 ile birlikte içe aktarılınca 257 üründe 2 günlük fiyat gözlemi oluşur.
- **ŞOK:** "Haftanın Fırsatları / İndirimli Ürünler" listesi bağlayıcıya eklendi (sayfalı; üstü çizili fiyat ya da
  işaretsiz iki fiyat kuralı; belirsiz kart reddedilir). Liste okunamazsa kampanya sayfalarıyla devam eder.
- **Veri hattı:** kararlı ürün kimliği (marka+ad+ambalaj ölçüsü), eksik açıklamada kimlik sürekliliği, kaynaktan
  kalkan ürünlerin yayından alınması (kısmi okumada asla), günlük fiyat gözlemi, tüm zamanlar UTC saklanır.
- **Yönetici bildirimi:** `ALERT_WEBHOOK_URL` ile başarısız taramalar webhook'a gönderilir.
- **Feed:** ürün başına fiyat geçmişi; anlık görüntüler tarih sırasıyla içe aktarılır (`import-snapshot`).
- **Kategoriler:** Giyim & Ayakkabı, Oyuncak & Kitap eklendi; spor/elektronik/ev kuralları genişletildi
  ("kutulu"→"ütü" gibi alt dize tuzakları düzeltildi).
- **Android:** targetSdk 36 (Play şartı), Android 16 geri hareketi (OnBackInvokedCallback), API 30+ sistem çubuğu
  görünümü, fiyat geçmişi özeti ve gözlem listesi, 36 saatten eski veri uyarısı.
- **Düzeltildi (Android):** katalog/resmî sayfa görüntüleyici boş adres yüklüyordu (`apply` içinde `url` gölgelenmesi).
  Yalnızca Android API'leriyle derleme (`-no-jdk`) sayesinde yakalandı.
- **Derleme:** aapt2 + d8 + API 36 araç zinciri, SHA-256 doğrulamalı kurulum betiği, minSdk 26 API denetleyicisi,
  hizalama denetimi.

## 0.1.0-beta — 12 Ekim 2026
- İlk beta: A101, BİM, ŞOK ve Migros için ayrı bağlayıcılar; robots.txt kontrolü, hız sınırlama, yeniden deneme,
  zaman aşımı; kaynak bazlı sağlık durumu ve hata kaydı; bir kaynağın hatası diğerlerini durdurmaz.
- 12.10.2026 tarihli gerçek veri anlık görüntüsü (269 ürün, 11 katalog) kaynak ve okunma zamanıyla.
- FastAPI okuma API'si, HTTP Basic korumalı yönetim paneli, APScheduler ile günde iki otomatik tarama.
- PostgreSQL 16 ile doğrulanmış veri modeli; fiyat geçmişi kaydı; mükerrer kayıt önleme.
- Android uygulaması (Kotlin, yerel Android View'ları): ana sayfa, mağaza sekmeleri, kataloglar, arama, kategori
  filtreleri, ürün detayı, favoriler, resmî sayfa görüntüleyici, ayarlar; açık/koyu tema; Türkçe duyarlı arama.
- Düzeltildi: "10-16 Ekim" gibi tarih aralıkları tire karakteri silindiği için yanlış ayrıştırılıyordu (testlerle yakalandı).
