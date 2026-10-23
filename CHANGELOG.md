# Değişiklik günlüğü

## 1.6.0 — 23 Ekim 2026
- **Yeni tasarım "Vitrin".** Ana sayfanın başında kaydırılan büyük broşür vitrini: her marketin güncel broşür kapağı
  büyük, arkasında aynı kapağın bulanık ve koyulaştırılmış hâli (renk içerikten gelir). Sakin grafit/porselen zemin,
  tek vurgu kehribar, kırmızı yalnızca indirim; katmanlı kartlar, yüzen alt menü, dokununca hafif küçülme. Market
  kutucukları, ürün kartları, katalog kartları, ayarlar ve market ekranı yeniden tasarlandı.
- **Broşür kapakları açılışta görünüyor.** Okuma motoru ekrandan ayrıldı (BridgeSession); Wi‑Fi'de, arka planda, 6 saatte
  bir resmî broşür sayfaları sırayla okunur. PDF broşürlerin (ŞOK vb.) ilk sayfası kapak olarak cihazda çizilir ve PDF
  önbelleğe alınır, dokununca anında açılır. Cihazda okunan broşür yoksa marketin sunucudaki kapaklı kataloğu gösterilir.
  Doğrulama sayfası çıkan market arka planda atlanır (asla aşılmaz).
- **Kopya ürünler giderildi:** madde işaretli açıklamalar temizlendi ("• 2 L" → "2 L", yalnız "•" → yok); aynı
  katalogda aynı ad+açıklamalı farklı fiyatlar tek kartta "N seçenek"; listelerde bu hafta geçerli olan, gelecek
  haftanın broşüründeki kopyasına tercih edilir; günün indirimlerinde ürün başına tek aday. Tamamı büyük harf ürün
  adları okunur hâle getirildi ("GLASS IN LOVE … 3'LÜ" → "Glass In Love … 3'lü").
- **Eski veriler yayından kalkar:** bitiş tarihi olmayan ürün/katalog kaynakta 5 gün görülmezse gösterilmez
  (A101'in 13 Ekim'den kalan fiyatları artık "güncel" görünmüyor).
- **Geçmiş kataloglar:** market ekranında ay ay; sunucudaki süresi bitmiş okunabilir kataloglar (90 gün) + bu cihazda
  okunmuş önceki haftaların broşürleri.
- **Market ekle:** kullanıcı marketin broşür sayfası adresini yazar; uygulama sayfayı cihazda açıp broşürü bulur,
  sonucu bildirir, marketi vitrine ekler; sonra Wi‑Fi'de kendiliğinden yenilenir, yeni broşür çıkınca bildirim gelir.
  Adres denetimi: yalnızca herkese açık https sayfalar.
- **Yeni marketler (yalnızca market zincirleri):** Mopaş (resmî PDF katalog + kapak), Happy Center (indirim bülteni),
  Yunus Market (insert PDF'leri). robots.txt'i otomatik erişime kapalı olan zincirler (Kim Market, Çağrı, Pehlivanoğlu,
  Özhan) eklenmedi; kullanıcı bunları "Market ekle" ile kendi cihazında takip edebilir.
- **Ayarlar:** uygulama kartı, uygulamayı paylaş, geri bildirim, market ekle, vitrin sırası, Wi‑Fi'de ön hazırlık,
  broşürleri şimdi yenile, sürüm/geliştirici/web sitesi/gizlilik/lisanslar. Kaynak durumu ekranında köprüdeki
  marketler "Cihazda okunur" olarak gösterilir. Tanıtım sayfası (GitHub Pages) eklendi.

## 1.5.0 — 22 Ekim 2026
- **Yeni arayüz: "market rafı" tasarım dili.** Soğuk raf grisi zemin, beyaz kâğıt yüzeyler, tükenmez kalem mavisi
  eylemler, raf etiketi sarısı fiyatlar, kırmızı yalnızca indirimde. Yazı tipi Barlow + Barlow Condensed (tabela harfi:
  fiyatlar, başlıklar, market adları); Inter kaldırıldı (APK ~1 MB küçüldü).
  - Ana sayfa **Broşürlük**: marketlerin broşürleri bir rafta durur, altlarından geçen sarı raf şeridinde market adı ve
    broşür durumu raf etiketi gibi yazar.
  - Ürün kartları ve ürün ekranı **raf etiketi fiyatı** (büyük lira, üstte kuruş), indirim kırmızı damga.
  - Market tabelaları, köşeli etiketler, düz alt çubuk (seçili sekme sarı etikette), mürekkep çizgili arama alanı.
- **Onur Market kaldırıldı** (bağlayıcı, yapılandırma, uygulama, eski önbellek feed'leri ve duyurular). Bölge ayarı
  yalnızca bölgesel broşür yayımlayan bir market olduğunda görünür.
- **Köprü düzeltmeleri (bağımsız kod incelemesi):** görünmez reCAPTCHA/Turnstile içeren sıradan sayfalar doğrulama
  sanılmıyor; doğrulama sırasında 3 sn'de bir kontrol, tamamlanınca broşür kendiliğinden açılıyor; KVKK/koşullar
  PDF'leri broşür sayılmıyor; flipbook sağlayıcısından başka siteye atlanmıyor; geri tuşu döngüsü ve belge başına süre
  sınırı; yeniden kurulan ekranda WebView sızıntısı; yıl sonu tarih aralıkları (30 Aralık'ta "02–08 Ocak");
  cihaz broşür önbelleği 12 saat; ŞOK PDF önbelleği tarih aralığıyla anahtarlanıyor; React Native'de oturum nonce'u.

## 1.4.0 — 21 Ekim 2026
- **İstemci Katalog Köprüsü (6 market):** A101, ŞOK, Migros (Migroskop), Gratis, CarrefourSA ve Rossmann broşürleri
  artık sunucuda kazınmıyor. `config/client_bridge.json` (`"client_rendered": true`) → feed `client_bridge` alanı →
  uygulama market kartına dokunulunca resmî sayfayı cihazda yükleme katmanının arkasında açar, siteye özel betik
  (`assets/bridge/<profil>.js`) broşürü okur ve doğrudan yerel okuyucuya geçer:
  - A101 afişleri (Next.js gömülü verisi + DOM), Migroskop Cover/Ad sayfaları, FlipHTML5 kitaplarının (CarrefourSA,
    Gratis) tam sayfa listesi → yerel tam ekran galeri;
  - ŞOK resmî PDF'leri (başlık + tarih) → yerel PDF okuyucu (birden çok broşürde seçim);
  - Gratis/Rossmann mağaza sayfası → resmî katalog görüntüleyicisine (izinli alanlar, en çok 2 adım) geçiş.
  Doğrulama sayfaları otomatik geçilmez (kullanıcı tamamlar). Okunan sayfalar cihazda 3 gün saklanır → sonraki açılış anında.
- Market ekranında **"Güncel broşürü aç"**; durum etiketi "Resmî broşür". Sunucu bu marketler için hata değil
  `client_bridge` durumu yazar; ürün fiyatları (ŞOK, Migros) sunucuda toplanmaya devam eder.
- **React Native başvuru bileşeni** `clients/react-native/` (`CatalogBridge.tsx`, `targetMarket` prop'u): aynı betikler
  `postMessage` sarmalayıcısıyla (`scripts/gen_rn_bridge.py` üretir), yerel FlatList galeri ve react-native-pdf.

## 1.3.0 — 21 Ekim 2026
- **Kesintisiz yerel geçiş (A101, CarrefourSA):** kullanıcı kartı açar → resmî sayfa WebView'de, üstünde
  "Resmî siteden güncel broşürler alınıyor…" katmanı (+ "Sayfayı göster") → enjekte betik sayfayı kaydırıp büyük dikey
  broşür görsellerini (srcset'in en genişi dahil) toplar → sayfa sonuna ulaşılıp sayı sabitlenince WebView kapanır ve
  yerel tam ekran okuyucu açılır. Doğrulama (Cloudflare/Turnstile/reCAPTCHA) sayfasında katman kalkar, kullanıcı kendisi
  tamamlar; otomatik çözülmez. JavaScript köprüsü yok (evaluateJavascript). Betik gerçek Chromium ile test ediliyor.
- **Migros:** money.com.tr "Kataloglarımız" arşivlerinden süresi dolmamış resmî PDF'ler (5M Migroskop, MacroStyle;
  Migros Jet ve Piknik-Bahçe güncel sayı yayımlandığında). Migroskop'a güncel sayının resmî PDF'si (varsa).
- **ŞOK:** kampanya afişleri yeni sayfa yapısına göre yeniden okunuyor (13 Ekim'den beri 0 → 59), tam boy görsel.

## 1.2.0 — 21 Ekim 2026
- **Merkezi katalog durumu** (`CatalogRules.state`: yaklaşan/geçerli/bitmek üzere/bitti/bilinmiyor) tüm bölümlerde tek kural.
- **Bölgesel kataloglar:** `catalogs.region` + feed `group`; aynı insertin bölge kopyaları tek kart (kullanıcının bölgesi);
  Ayarlar → Bölge; market ekranında bölge çipleri. Onur'un ana sayfada 6 kart olarak tekrarlanması giderildi.
- **File afişleri** `poster` türünde ("N afiş"), bitmek üzere/bugün başlayan bölümlerine girmez.
- **Kontrollü geri çekilme:** son 3 deneme erişim engeliyse kaynak 20 saat denenmez.
- **Proje becerileri** (`.claude/skills/`): kaynak keşfi, dosya doğrulama, mükerrerlik, hata teşhisi, uçtan uca test.

## 1.1.0 — 21 Ekim 2026
- **Onur Market:** resmî insert PDF'leri (3 bölge, kampanya sayfalarından); PDF imzası doğrulanır, geçmiş insertler alınmaz.
- **File Market:** doğru resmî alan adı (file.com.tr) ve resmî ürün afişleri.
- **Gerçek içerik türü doğrulaması:** sayfa görselleri ve PDF'ler ilk baytlarından (JPEG/PNG/WEBP/PDF) doğrulanır;
  HTML hata sayfası broşür olarak kaydedilmez. HEAD'e hatalı yanıt veren sunucular için GET + akış.
- **403 teşhisi:** hata kaydında engelleyen (Cloudflare vb.), içerik türü, yönlendirme zinciri; yönlendirme sınırı 5.
- **Cihazda toplanan broşür önbelleği:** A101/CarrefourSA gibi sunucuda okunamayan marketlerde, resmî sayfadan
  toplanan sayfalar cihazda 3 gün saklanır; sonraki açılışta broşür doğrudan uygulama içi okuyucuda.
- **kaynak-teshis iş akışı:** ham sayfa yanıtlarını `teshis` dalına kaydeder (ayrıştırıcı geliştirme).

## 1.0.0 (V1) — 21 Ekim 2026
- **Mükerrer katalog yok:** ana sayfa bölümleri tek kaynaktan (`HomeCatalogs`) — Market broşürleri → Bugün başlayanlar →
  Süresi dolmak üzere → Diğer kataloglar; her katalog yalnızca bir bölümde. "Son güncelleme" bilgisi rafın altında.
- **Bayat kataloglar gizlenir:** kaynakta 2 günden uzun görülmeyen ürünsüz katalog feed'e girmez (eski Migroskop kaydı).
- **Sahte broşür sayfası engeli:** ürün küçük resimleri (Magento `/catalog/product/`) broşür sayılmaz; kalite ajanı mevcut
  hatalı sayfaları temizler (Rossmann).
- **Resmî sayfadan sayfa sayfa okuma (cihazda):** A101 gibi sunucudan okunamayan marketlerde resmî afiş/broşür sayfası
  açıldığında büyük dikey görseller toplanır; "Sayfa sayfa aç" ile yakınlaştırılabilir okuyucuda gösterilir.
- **SSRF koruması ve yanıt boyutu sınırı** (veri hattı istemcisi), **"Şimdi dene"** (Actions → stores girdisi),
  **market aç/kapat** (`config/sources.json`).
- Belgeler: ROOT_CAUSE_ANALYSIS, COMPETITOR_ANALYSIS, DATA_SOURCE_STATUS, TEST_REPORT, V1_RELEASE_CHECKLIST.

## 0.8.0-beta — 21 Ekim 2026
- **Market broşürleri rafı (ana sayfa):** Türkiye'nin büyük zincirleri (BİM, A101, ŞOK, Migros, CarrefourSA, Tarım
  Kredi, File, Hakmar, Onur…) her zaman görünür; her kartta o marketin güncel broşürü. Sayfa görselli broşür
  yakınlaştırılabilir okuyucuda, PDF uygulama içinde açılır. Broşür sayfaları veri hattında okunamayan marketlerde
  (A101 erişim engeli, CarrefourSA robots.txt, JavaScript'li sayfalar) resmî broşür/afiş sayfası uygulama içinde tam
  ekran, kullanıcının cihazından açılır. **Sırala** ile kullanıcı market sırasını değiştirir (yalnızca cihazda).
- **Migroskop dijital katalog sayfaları:** resmî görüntüleyicinin (migroskop.migros.com.tr) tüm sayfa görselleri.
- **Ağ trafiği yakalama:** tarayıcı motoru sayfanın kendi XHR/fetch JSON yanıtlarındaki broşür görsellerini alır
  (DOM'dan önce); bulunamazsa DOM ve görsel yanıtlarına düşer.
- **İsteğe bağlı salt okunur API** (`python -m firsat360 serve`, `requirements-api.txt`): `/health`, `/catalogs`,
  `/catalogs/{id}`. Varsayılan yayın yine ücretsiz GitHub Pages'teki feed.json.
- **Zamanlama:** günde iki çalıştırma 08:05 ve 00:05 (İstanbul).

## 0.7.0-beta — 21 Ekim 2026
Canlı veritabanı (21.10.2026 04:49 çalıştırması) incelenerek bulunan kök nedenler düzeltildi.
- **Ürün görselleri (kök neden):** ürün sayfası okuyucusu görseli yalnızca og:image/JSON-LD'de arıyordu; BİM, ŞOK ve TK
  sayfalarında bunlar yok. Artık mağazaya özel ana görsel kalıbıyla sayfadaki tüm görseller (gecikmeli yükleme öznitelikleri,
  srcset, noscript, stil içi, `/_next/image` sarmalayıcısı) taranıyor; en sık geçen/en büyük boyutlu ana görsel seçiliyor.
- **Görsel silme hatası:** detay sayfası okunamayınca resmî listedeki doğru görsel siliniyordu → korunuyor.
- **Sahte "doğrulanmış" görsel:** TK'da 212 ürüne sitenin genel paylaşım klasörü (`/storage/assets/images/seo/`) görsel diye
  bağlanmıştı → kalıp dışı og:image reddediliyor; kalite ajanı mevcutları kaldırdı.
- **BİM afişleri:** aynı klasördeki reklam afişleri ürüne bağlanabiliyordu → yalnızca `<anahtar>_kucuk/_buyuk_` ürün görseli kabul.
- **ŞOK adları:** kampanya ürünleri "product-thumb" adıyla kaydediliyordu → gerçek ad; şablon adlar reddediliyor; kampanya
  ürünlerinin görselleri de okunuyor.
- **Kaldırılan sayfalar:** ana sayfaya yönlendirilen ürün sayfası "kaldırılmış" sayılır; oradaki görsel/ad eşleştirilmez.
- **Eski kataloglar:** kaynaktan kalkan veya süresi dolan katalogların ürünleri yayından alınıyor (163 TK + 49 BİM ürünü yayındaydı).
- **Günün İndirimleri her gün değişiyor:** aday havuzu genişledi (satıştaki aktüel ürünler), havuz güne göre gruplara bölünüp
  sırayla gösteriliyor; ardışık günlerde ortak ürün yok (yeterli aday varken). Mağaza başına üst sınır korunuyor.
- **Kalite ajanları + orkestratör** (`backend/firsat360/quality.py`): kaynak, katalog, ürün görseli, veri kalitesi, Günün
  İndirimleri ve kullanıcı bildirimi ajanları her çalıştırmada denetler → orkestratör düzeltir → yeniden denetler → rapor
  (status.json, Actions özeti, açık kalan hata varsa tek bir "Otomatik kalite raporu" issue'su).
- **"Hatalı bilgi bildir"** (ürün ekranı): önceden doldurulmuş GitHub bildirimi; günlük çalıştırma ürünü resmî sayfasından
  yeniden doğrular, sonucu bildirime yazar ve kapatır.
- **Kaynak durumu ekranı** (Ayarlar → Veri kaynakları): mağaza bazlı durum, son başarılı okuma, ürün görseli oranı, anlaşılır hata.
- **Yeni mağazalar (alternatif toplama motoru):** Rossmann, Onur Market (İstanbul/Bursa/Trakya), CarrefourSA, File Market.
  Sıralı kaynak keşfi: statik HTML (PDF/flipbook/broşür görseli) → başsız Chromium (JavaScript ile yüklenen sayfalar) → bulunamazsa
  uydurma yok, denemeler raporlanır. robots.txt'ye uyulur; 403/CAPTCHA/giriş aşılmaz.
- **Gratis:** katalog bağlantısı sayfanın gömülü verisinde de aranıyor.

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
