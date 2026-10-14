# FIRSAT360 Beta 0.6.1 — Test ve derleme raporu

## 0.6.1 (14.10.2026) — sunucusuz ve sade sürüm, tüm kataloglar uygulamada
| Test | Komut / yöntem | Sonuç |
|---|---|---|
| Veri hattı testleri | `cd backend && python -m pytest -q` | ✅ 105 + yeni 3 = **108 geçti** (2 PostgreSQL testi ortam değişkeni olmadan atlanır); panel/API testleri kodla birlikte kaldırıldı |
| Publitas sayfa çıkarma (SENTETİK) | `test_publitas_all_pages_extracted`: yönlendirme → /page/1…N, sayfa görseli, ekran görüntüsü kapak yapılmaz, son sayfada durma, tarih | ✅ |
| FlipHTML5 sayfaları (SENTETİK) | `test_fliphtml5_pages_only_verified`: yalnızca HEAD 200 + görsel türü olan sayfalar | ✅ |
| Migroskop PDF (SENTETİK) | `test_migroskop_pdf_probe_same_issue_only`: yalnızca aynı sayı no; yoksa uygulama içi resmî sayfa | ✅ |
| Android çekirdeği (JVM) | `CoreTestsKt feed.json` — yeni: tüm geçerli kataloglar okunabilir, flipbook kataloglar listede | ✅ **149/149** |
| APK'lar | debug, yeni-paket debug, imzasız release APK + AAB | ✅ derlendi; aynı imza (`ef4197…4540`) |
| Gerçek sitelerde Publitas/FlipHTML5 çekimi | — | ❌ buradan erişim yok; ilk GitHub çalıştırmasında görülecek. Sayfa görseli bulunamazsa katalog yine uygulama içinde resmî sayfasıyla açılır |
| Cihazda arayüz | — | ❌ emülatör yok |

---

# Beta 0.6.0 — Test ve derleme raporu

## 0.6.0 (14.10.2026) — arama motoru, katalog kartları, güvenlik, yönetim paneli, yayın hazırlığı
Ortam: bulut derleme sunucusu, Python 3.13, PostgreSQL 16 (yerel), Kotlin 2.2.20, aapt2/d8 (API 36), bundletool 1.18.1.
**KVM/emülatör yok → arayüz testleri cihazda yapılamadı. Mağaza siteleri, Google Maven, Docker Hub erişilemez.**

| Test | Komut / yöntem | Sonuç | Bulunan hata → düzeltme | Tekrar |
|---|---|---|---|---|
| Backend birim + entegrasyon (SQLite) | `cd backend && python -m pytest -q` | ✅ 124 geçti (2 PG testi ayrı) | Eski analitik ucu testi kaldırıldı (uç güvenlik nedeniyle silindi) | ✅ |
| PostgreSQL 16 | `FIRSAT_TEST_PG_URL=… pytest tests/test_postgres.py` + panel duman testi (giriş, durum, reklam kaydı, denetim) | ✅ 2/2 + ✅ | Test rolü parolası sıfırlandı (ortam) | ✅ |
| Yönetim paneli güvenliği (16 test) | `pytest tests/test_admin.py` — yetkisiz erişim, varsayılan hesap yok, hatalı giriş/kilit, IP sınırı, CSRF, çapraz köken, roller, oturum süresi/çıkış/pasifleştirme, parola kuralları, scrypt, TOTP akışı + RFC 6238 vektörü, bildirim önizleme/gönderme/mükerrer engeli, reklam kimliği doğrulaması, herkese açık hız sınırı, SQL enjeksiyonu benzeri girdi, XSS kaçışı | ✅ 16/16 | — | — |
| Canlı sunucu (uvicorn) | `python -m firsat360 serve --no-scheduler` + curl | ✅ /health 200, feed gzip, /admin→/admin/login, CSP/X-Frame/no-store başlıkları, /docs 404, /v1/events 404 | — | — |
| Statik analiz | `bandit -r backend/firsat360` | ✅ 0 bulgu | 6 bulgu düzeltildi (B104, B324×4, B110) | ✅ 0 |
| Bağımlılık denetimi | `pip-audit -r backend/requirements.txt` | ✅ bilinen açık yok | — | — |
| APK/AAB gizli bilgi taraması | açılan arşivde desen araması (Google API, AdMob, ntfy, GitHub, Slack, PEM) | ✅ 0 eşleşme, yerel .so 0 | — | — |
| Android çekirdeği (JVM) | `kotlinc … && kotlin CoreTestsKt feed.json` | ✅ **147/147** | — | — |
| ↳ Arama | "bim, BİM, Bim, BIM, '  bim  ', BİM market, bim marketi, Bim  Market" → yalnızca BİM'in 347 ürünü; "a 101", "şok", "SOK market", "Tarım Kredi"; "bim süt" market+terim; ŞAMPUAN=şampuan=sampuan; "sampuqn"/"sapmuan" → yakın sonuç ("sampuan"); 3 harfte bulanık yok; tam sonuç varken bulanık yok ve her sonuç terimi içeriyor; boş arama; sonuçsuz arama; kategori/katalog arama; geçmiş tekilleştirme ve 10 sınırı | ✅ | — | — |
| ↳ Türkçe normalizasyon | I→ı, İ→i (exact anahtar), ı/i/ş/ç/ğ/ö/ü katlama (loose anahtar), é/ê aksan, boşluk/sekme/noktalama | ✅ | — | — |
| ↳ Arama performansı | 10.620 ürünlük yapay feed (gerçek feed ×20) | ✅ dizin ~200 ms, sorgu başına ~12–16 ms | — | — |
| ↳ JSON güvenliği | 5.000 seviye iç içe, kesik girdiler, bozuk sayı | ✅ JsonException (çökme yok) | Ayrıştırıcıya derinlik/sınır kontrolü eklendi | ✅ |
| ↳ Önceki sürüm testleri | bildirim kararı, log/önbellek sınırları, feed indirme (304, gzip, kesinti, düzelme) | ✅ | — | — |
| Debug APK | `bash build-nogradle.sh` (+ minSdk 26 API denetimi, açılış sırası denetimi) | ✅ 2,35 MB | `BUNDLETOOL` değişkeni tanım sırası hatası → düzeltildi | ✅ |
| Release APK + AAB | `RELEASE=1 RELEASE_KEYSTORE=<geçici test anahtarı> bash build-nogradle.sh` | ✅ APK imzası doğrulandı, AAB `jarsigner -verify`: jar verified, `bundletool validate` geçti, universal APK üretildi (versionCode 7, targetSdk 36, feed+dex+arsc mevcut) | — | Geçici test anahtarı silindi; teslim edilenler **imzasız** (kendi anahtarınızla imzalayın) |
| Feed boyutu | gzip -9 | 928 KB → **62 KB** aktarım (GitHub Pages/Caddy gzip'li sunar; değişmezse 304) | — | — |

### Test edilemeyenler (neden)
| Alan | Neden |
|---|---|
| Arayüz testleri (arama çubuğu, katalog kartları, geri tuşu, boş/hata ekranları, 320 dp, büyük yazı, döndürme, koyu tema) | Emülatör yok (KVM yok), Espresso/Robolectric için Google Maven erişimi yok. Kartlar sabit oranlı kap + sabit satırlı metinle eşit yükseklik verecek şekilde kodlandı; **cihazda kontrol edilmeli** |
| Soğuk açılış, ANR, bellek sızıntısı, uzun süreli kullanım | Cihaz/profiler yok |
| Görsel önbelleğinin cihazda 100 MB'ı aşmaması | Algoritma JVM'de test edildi; cihazda ölçülmedi |
| Bildirimin telefonda görünmesi, push | Cihaz ve ntfy.sh erişimi yok; sunucu → yerel HTTP sunucusu push isteği test edildi |
| AdMob / UMP | SDK indirilemedi; entegre edilmedi (`docs/REKLAM_REHBERI.md`) |
| MobSF, Android Lint | Docker/Gradle yok |
| Docker Compose + Caddy | Docker Hub erişimi yok; yapılandırma teslim edildi |
| Canlı bağlayıcılar gerçek sitelerde | Mağaza sitelerine erişim yok; ilk GitHub Actions çalıştırmasında görülecek |

---

# Beta 0.5.0 — Test ve derleme raporu

## 0.5.0 (14.10.2026) — canlı günlük veri hattı, günlük bildirim, Günün İndirimleri, depolama sınırları
Ortam: bulut derleme sunucusu. **KVM yok → emülatör yok; mağaza siteleri, ntfy.sh, Google/Firebase bu sunucudan
erişilemez.** Bu yüzden canlı bağlayıcılar gerçek sitelerde ve bildirimler gerçek telefonda burada çalıştırılamadı.
Aşağıda ✅ = otomatik testle doğrulandı, ⚠️ = kısmen (mantık/sentetik), ❌ = burada test edilemedi.

### İstenen 12 senaryo
| # | Senaryo | Sonuç | Nasıl |
|---|---|---|---|
| 1 | Yeni katalog yayımlandığında verinin güncellenmesi | ✅ | `test_first_run_is_silent_then_new_catalog_announced_once` (duyuru, 09:00 teslim, feed'e yazım); JVM `digest.single`, `fetch.updated` |
| 2 | Yeni katalog yoksa bildirim gönderilmemesi | ✅ | aynı gün 2. çalıştırma duyuru üretmiyor; JVM `digest.none`, `digest.tooOld` (eski katalog yeni sayılmıyor) |
| 3 | Aynı katalog için tekrar bildirim olmaması | ✅ | sunucu: tek duyuru + tek push (`test_push_is_sent_once_scheduled_for_0900`); JVM `digest.noRepeat` |
| 4 | Bildirim izni kapalıyken uygulamanın çalışması | ⚠️ | kod yolu: izin yoksa gösterim atlanır, katalog işlendi sayılır, Ayarlar'da uyarı + "Bildirim ayarlarını aç". Cihazda denenmedi |
| 5 | İnternet kesintisi ve bağlantının geri gelmesi | ✅ / ⚠️ | JVM yerel sunucu: 503 → 3 deneme, 2 sn/4 sn artan bekleme, önbellek korunur, sonra düzelir (`fetch.retryBackoff`, `fetch.downKeepsCache`, `fetch.recovers`, `fetch.offline`). Cihazda denenmedi |
| 6 | Büyük görsel ve PDF'te bellek kontrolü | ⚠️ | tasarım: boyuta göre örnekleme, RGB_565 katalog sayfası, bellek LRU (düşük RAM'de 1/10), PDF sayfa sayfa çizim + küçük LRU, indirme sınırları (görsel 20 MB, PDF 80 MB). Cihazda ölçülmedi |
| 7 | Önbelleğin 100 MB sınırını aşmaması | ✅ / ⚠️ | JVM `cache.limit`, `cache.lru`, `cache.protect` (ölçekli sınır), `cache.default` (40–100 MB); sunucu HTTP önbelleği `test_http_cache_size_limit_and_partial_cleanup` |
| 8 | Eski logların ve yarım indirmelerin temizlenmesi | ✅ | JVM `log.age`, `log.size`, `log.noThrow`, `log.release`, `.part` temizliği; sunucu `test_log_cleanup_age_and_size`, `test_redact_secrets` |
| 9 | Uygulama kapalıyken sunucu tabanlı bildirim akışı | ⚠️ / ❌ | sunucu → **gerçek yerel HTTP sunucusuna** gerçek ntfy JSON isteği: konu, başlık, `firsat360://` bağlantısı, 09:00 `delay`, Bearer anahtar, en fazla 3 deneme ✅. ntfy.sh ve telefondaki gösterim ❌ (erişim yok). FCM yok (Firebase hesabı/anahtarı yok) |
| 10 | Bozuk/erişilemeyen kaynakta çökmeden çalışma | ✅ | `test_source_failure_keeps_verified_data` (ürünler korunur, durum "error", son başarılı zaman değişmez); 403'te zorlamadan durma; bozuk feed önbelleğe yazılmaz (`fetch.badKeepsCache`) |
| 11 | Güncelleme sonrası kayıtların korunması | ⚠️ | 0.4.0 ve 0.5.0 aynı sertifika (SHA-256 `ef4197…4540`, apksigner ile doğrulandı), aynı paket ve tercih anahtarları; yalnızca kaldırılan fiyat alarmı anahtarları siliniyor. Cihazda üzerine kurulum denenmedi |
| 12 | Günlük kontrolün tekrarlanabilir ve izlenebilir olması | ✅ | SQLite ve **PostgreSQL 16**'da `daily` iki kez: ikinci çalıştırma yeni duyuru üretmedi; her çalıştırma `sync_runs` + `status.json` (mağaza bazlı son başarılı/son hata) + iş özeti tablosu |

### Toplam
| Test | Sonuç |
|---|---|
| Backend `pytest` (SQLite) | ✅ **112 geçti** (+2 PostgreSQL testi ayrı çalıştırıldı: ✅ 2/2) |
| Yeni sunucu testleri (`tests/test_daily.py`, sentetik sayfalar + yerel HTTP sunucusu) | ✅ 23/23 |
| Android çekirdeği (JVM, gerçek feed + yerel HTTP sunucusu) | ✅ **108/108** |
| Yeni bağlayıcıların **gerçek sitelerde** çalışması (Tarım Kredi, Hakmar, Migroskop dijital, A101 afiş, flipbook) | ❌ buradan erişim yok. Ayrıştırıcılar 13–14.10 sayfa içeriğine göre yazıldı, sentetik sayfalarla test edildi; ilk GitHub çalıştırmasının özet tablosunda mağaza mağaza görülecek. Veri bulunamazsa uydurma veri yok, hata kaydı + eski veri korunur |
| Migroskop PDF | ⚠️ sayfa (Sayı 908, 8–21 Ekim) okundu; sayfada görülen PDF bağlantısı eski bir sayıya ait olabilir → PDF yalnızca **aynı sayı numarasını** içeren bağlantı varsa gösterilir, yoksa resmî sayfa açılır |
| ŞOK broşür/afiş | — ŞOK resmî sitesinde afiş/broşür/PDF yayımlamıyor (14.10 kontrol). Haftanın Fırsatları ürünleri + kampanya görselleri otomatik alınıyor; haber siteleri ve belgelenmemiş mobil uygulama arayüzü kullanılmadı |

### Derleme ve teslim bilgileri
| Kalem | Değer |
|---|---|
| APK | `FIRSAT360-0.5.0-beta-debug.apk` (com.firsat360.app) **2,33 MB** · `…-yeni-paket-debug.apk` (com.firsat360.beta) 2,33 MB · versionCode 6 · minSdk 26 · targetSdk 36 · v2+v3 imza |
| Derleme adımları | aapt2 → Kotlin → ProGuard → minSdk 26 API denetimi → açılış sırası denetimi → d8 → hizalama → imza ✅ |
| Gömülü veri | feed şeması 2: 531 ürün, 525'i resmî fotoğraflı, 34 katalog, Günün İndirimleri 24 ürün / 4 market (13.10.2026 doğrulanmış okumalardan) |
| Yayın adresi | **gömülü değil** — GitHub kullanıcı adı bekleniyor; gömülmeden uygulama yalnızca içindeki veriyle çalışır |
| Görsel önbelleği | varsayılan cihazın boş alanının %5'i, 40–100 MB; ayarlardan 50/100/200 MB; LRU |
| PDF önbelleği | en fazla son 2 PDF / 120 MB; çevrim dışı kayıtlar ayrı klasörde, otomatik silinmez |
| Uygulama günlüğü | yalnızca hata/uyarı, satır ≤200 karakter, ≤7 gün, toplam ≤5 MB (gün başına ≤1 MB) |
| Sunucu günlüğü | 1 MB × 5 dosya = ≤5 MB, ≤7 gün, gizli değer maskeleme |
| Son başarılı senkronizasyon | canlı sistem henüz kurulmadı → yok. Gömülü verinin okunma zamanı 13.10.2026 ~20:35 |
| Cihazda/emülatörde çalıştırma, arayüzün görsel kontrolü | ❌ (KVM yok) — yeni ana sayfa, PDF okuyucu, ayarlar ekranı telefonda kontrol edilmeli |

---

# Beta 0.4.0 — Test ve derleme raporu

## 0.4.0 (13.10.2026) — doğrulanmış ürün fotoğrafları, 6 yeni market, premium arayüz
Ortam: aynı bulut sunucusu (ortam bu oturumda sıfırlandı; proje 0.3.0 ZIP'inden geri yüklendi, araç zinciri yeniden
kuruldu). Mağaza siteleri ve görsel CDN'leri bu sunucunun kabuğundan **erişilemez** (ağ politikası); veriler ve görsel
adresleri WebFetch aracıyla resmî sayfalardan okundu. KVM yok → emülatör çalıştırılamadı.

### Gerçek veri testleri
| Test | Sonuç |
|---|---|
| 497 resmî ürün sayfası tek tek okundu (BİM 344, A101 39, ŞOK 49, Tarım Kredi 65) → `product_images.tsv` | ✅ |
| Otomatik görsel doğrulama (aynı detay adresi + ad benzerliği + ölçü): BİM 347/347, A101 38/39, ŞOK 49/50, Tarım Kredi 61/65 doğrulandı | ✅ |
| Doğrulanamayan/fotoğrafsız 6 ürüne görsel konmadı (durum: `no_image_on_page` ×4, `page_empty` ×1, `no_page` ×1) | ✅ |
| Aynı sayfada birden çok çeşit (BİM oda kokusu yedeği ×2, patates cipsi ×2) → her çeşide kendi fotoğrafı | ✅ (elle denetlendi + test) |
| Sayfa fiyatı ≠ liste fiyatı → fiyat değişmedi, `page_price_conflict` işaretlendi (3 ürün) | ✅ |
| Feed: 531 ürün, 525'i fotoğraflı (%98,9), 495'i "ürün sayfasından doğrulanmış", 10 market, 34 katalog | ✅ |
| Tarım Kredi: 65 ürün, geçerlilik 13–19 Ekim, katalog ürün sayısı 65 | ✅ |
| Hakmar: 2 broşür, 8+8 resmî sayfa görseli, geçerlilik 8–19 Ekim ve 21 Ağustos–31 Ekim | ✅ |
| Watsons/Metro/Bizim Toptan/Gratis: resmî kapak + resmî katalog bağlantısı; tarih yazmıyorsa boş (tahmin yok) | ✅ |
| Doğrulanmış görsel, görselsiz yeniden okumada silinmiyor | ✅ |
| **PostgreSQL 16**: iki günlük içe aktarma + görsel doğrulama (495 doğrulanmış), mükerrer kayıt yok | ✅ 2/2 |
| Canlı API (uvicorn + PostgreSQL): `/health` 0.4.0-beta, `/v1/feed` 531 ürün, `/v1/offers?q=sucuk&store=tkkoop` | ✅ |
| Backend toplam (`pytest`, PostgreSQL dahil) | ✅ **91/91** (gerçek veri 24, sentetik fikstür 31, PostgreSQL 2, diğer birim 36; işaretler kesişebilir) |
| Android çekirdeği (JVM, gerçek feed): 531 ürün, 10 mağaza sırası ve rengi, 525 görsel, görsellerin mağazanın kendi CDN'inde ve kendi ürün sayfasından olması, fiyat uyuşmazlığı, küçük görsel adresi, "diğer marketlerde" eşleşmesi, önbellek/gömülü akış seçimi | ✅ **85/85** |
| Görsel dosyalarının gerçekten yüklenmesi (HTTP 200) | ⚠️ bu sunucudan doğrulanamadı (CDN'ler kabuktan erişilemez, WebFetch ikili dosya okumaz). Adresler resmî sayfaların HTML'inden okundu; cihazda kontrol edilmeli |
| Türetilmiş küçük boyutlar (A101 `_384x384`, ŞOK `width=320`) | ⚠️ doğrulanmadı; yüklenmezse uygulama otomatik olarak sayfadan okunan özgün adrese döner |

### Derleme
| Adım | Sonuç |
|---|---|
| aapt2 → Kotlin → ProGuard → minSdk 26 API denetimi → açılış sırası denetimi → d8 → hizalama → v2+v3 imza | ✅ TAMAM |
| APK: `FIRSAT360-0.4.0-beta-debug.apk`, versionCode 5, minSdk 26, targetSdk 36, ~2,3 MB | ✅ |
| İmza: proje içi sabit DEBUG anahtarı (SHA-256 `EF:41:97:18:…:45:40`) | ✅ — önceki sürümlerden farklı: eski sürüm kaldırılıp kurulmalı |
| Cihazda/emülatörde çalıştırma | ❌ yapılamadı (KVM yok) |

### Demo/sentetik testler (gerçek veri değildir)
Sentetik görsel eşleşme senaryoları: yanlış ad → ret, yanlış ölçü (5 L ↔ 1 L) → ret, farklı detay adresi → ret,
çeşit seçimi, mağaza beyanı önceki fiyata eşit sayfa fiyatı çelişki sayılmaz. Mevcut sentetik HTML fikstür testleri
(BİM/A101/ŞOK/Migros ayrıştırıcıları) değişmeden geçti.

### Kalan eksikler (0.4.0)
1. **Cihaz testi yapılmadı** (emülatör yok). Yeni ızgara, mağaza çipleri ve ürün detayı yalnızca derleme + statik
   denetimle doğrulandı. Çökme olursa uygulama hata ekranı gösterir ve raporu paylaşmanızı ister.
2. **Görsellerin cihazda yüklendiği doğrulanmadı** (bkz. ⚠️ satırları). Yüklenemeyen görselde monogram gösterilir.
3. 6 yeni market **yalnızca anlık görüntü** ile geldi; canlı bağlayıcıları yazılmadı (canlı HTML'e erişim olmadan
   test edilemezdi). Tarım Kredi kampanyası 19 Ekim'de biter → yeni anlık görüntü gerekir.
4. Hakmar, Watsons, Metro, Bizim Toptan, Gratis için ürün bazlı fiyat yok (broşür/katalog görseli + resmî bağlantı).
   Katalog görsellerinden OCR ile ürün/fiyat çıkarımı yapılmadı (doğrulamasız OCR sonucu kabul edilmez).
5. Resmî kaynağında fotoğrafı olmayan 6 ürün fotoğrafsız (üçüncü taraf görseli izinsiz ve doğrulanamaz).
6. ŞOK'un 12.10 kampanya ürünlerinin (10 ürün) kampanya sayfaları 13.10'da yeniden okunmadı (yalnızca ürün sayfaları görsel için açıldı); fiyatın son görülmesi 12.10 olarak gösterilir.
7. Mağazaların kullanım koşulları okunmadı; görsellerin yeniden gösterimi için yayın öncesi yazılı izin önerilir.

---

# Önceki sürümler


## 0.2.1 acil düzeltme (13.10.2026, 0.2.0 cihazda çöktü)
Kullanıcının Xiaomi 14T Pro cihazında 0.2.0 açılışta çöktü:
`NullPointerException … DecorView.getWindowInsetsController() … at MainActivity.onCreate(MainActivity.kt:75)`.
Neden: durum çubuğu görünümü `setContentView()`'dan önce `window.insetsController` ile ayarlanıyordu. Bu ortamda
emülatör/cihaz olmadığı için çalışma zamanı testiyle yakalanamadı; derleme ve statik denetimler bu tür hatayı görmüyordu.

| Düzeltme / doğrulama | Sonuç |
|---|---|
| Sistem çubuğu ayarı `setContentView()`'dan sonra, `try/catch` korumalı | ✅ derlenmiş bayt kodunda sıra doğrulandı (`setContentView` → `applySystemBars`) |
| Açılış hatası → hata ekranı + rapor paylaşma (uygulama kapanmaz); ekran hatası → hata görünümü | ✅ kodda |
| Yakalanmayan hata cihazda kaydedilir, sonraki açılışta ana sayfada paylaşılabilir | ✅ kodda |
| Yeni derleme denetimi `check_startup_order.py`: 0.2.1 → TAMAM; 0.2.0 koduna karşı → hatayı yakaladı | ✅ |
| minSdk 26 API denetimi, hizalama, imza, Android çekirdek testleri (43/43) | ✅ |
| Cihazda/emülatörde çalıştırma | ❌ hâlâ yapılamadı (bu sunucuda KVM/emülatör yok) |

Tarih: 13 Ekim 2026 · Ortam: Ubuntu 24.04 (bulut geliştirme sunucusu), Python 3.13.16, OpenJDK 21,
Kotlin 2.2.20, PostgreSQL 16.15, Android araç zinciri: aapt2 2.20, D8 8.2.2, apksigner, android.jar API 36.

## Ortam kısıtları (raporun okunması için önemli)
Sunucunun ağ politikası (egress proxy) şu adreslere bağlantıyı **reddetti (HTTP 403, CONNECT)**:
`www.a101.com.tr`, `www.bim.com.tr`, `www.sokmarket.com.tr`, `www.migros.com.tr`, `dl.google.com`,
`maven.google.com`, `repo1.maven.org`, `services.gradle.org`, `plugins.gradle.org`, Docker Hub.
Açık olanlar: PyPI, npm, GitHub (sürüm dosyaları, raw), Ubuntu deposu.

Sonuçları:
- Mağaza verisi ayrı bir web okuma aracıyla (WebFetch) resmî sayfalardan alındı ve tarihli anlık görüntü olarak
  kaydedildi. Otomatik bağlayıcılar canlı sitelere **bu sunucudan bağlanamadı**.
- Google Maven indirilemediği için Jetpack Compose/AndroidX kullanılamadı. Uygulama yalnızca Android çerçevesiyle
  yazıldı ve Gradle'sız araç zinciriyle (npm/GitHub kaynaklı, SHA-256 doğrulamalı) API 36'ya derlendi.
- Emülatör/cihaz yok → uygulama **çalıştırılarak test edilemedi**.

## 1. Gerçek veri ile yapılan testler (canlı ağ isteği yok; resmî sayfalardan okunmuş veri)
| Test | Sonuç |
|---|---|
| 12.10 anlık görüntüsü: A101 39, BİM 190, ŞOK 10, Migros 30 ürün; 0 red; bilinen fiyatlar (ör. BİM Ayçiçek Yağı 445,00; Creality 34.990,00; Migros Komili 499,95) | ✅ |
| 13.10 anlık görüntüsü: A101 39, BİM **347** (5 sekme, 2'si yeni), ŞOK **40** (indirimli liste), Migros 30; 0 red | ✅ |
| İki günün art arda içe aktarılması: A101'de **kaynaktan kalkan 2 ürün yayından alındı**, yeni 2 ürün eklendi | ✅ |
| Ürün kimliğinin günler arasında korunması: BİM "13 Ekim Salı" ürünlerinin ≥85/89'u aynı kayıt; açıklaması eksik okunan 4 BİM ürünü aynı kayıt kaldı | ✅ |
| Fiyat geçmişi: Migros 30 ürünün her biri için 2 gözlem; akışta 257 üründe 2 gözlem | ✅ |
| Aynı ad/ölçüde farklı açıklamalı ürünler (2 Airwick yedeği) ayrı kayıt | ✅ |
| Yeni BİM sekmeleri: "16 Ekim Cuma", "18 Ekim Pazar" tarihleri; "10-16 Ekim İndirimleri" başlığı; LG 55" TV 37.500,00 ₺ → elektronik | ✅ |
| ŞOK indirimli liste: 40 üründe önceki fiyat > güncel fiyat; Signal White Now 99,00 / 229,00 | ✅ |
| Bilinmeyenlerin uydurulmaması (stok, şube, A101 tarihleri, görsel yok, eşit fiyatta indirim yok) | ✅ |
| Her üründe kaynak URL, okunma zamanı, yöntem, doğrulama düzeyi | ✅ |
| Süresi dolan kataloğun gizlenmesi (17 Ekim'de "10-16 Ekim") | ✅ |
| Akış: 466 ürün, "Diğer" kategorisinde 0 ürün | ✅ |
| **PostgreSQL 16** (gerçek sunucu): iki günlük içe aktarma, yeniden içe aktarmada mükerrer kayıt/gözlem yok, `timestamptz`, Türkçe karakter, feed | ✅ 2/2 |
| API (canlı uvicorn sunucusu): `/health`, `/v1/feed` 466 ürün, "şampuan" = "SAMPUAN" (11 sonuç), `/admin` kimliksiz 401 / kimlikli 200, zamanlayıcıda 8 iş | ✅ |
| Android çekirdeği (JVM): gerçek feed ayrıştırma (466 ürün, 0 atlanan), demo kaydın karışmaması, Türkçe arama, filtre/sıralama, para biçimi, geçerlilik etiketleri, fiyat geçmişi özeti | ✅ 43/43 |
| **Canlı bağlayıcının mağaza sitesinde çalıştırılması** (yönetim panelinden "Şimdi tara: BİM") | ❌ çalıştırılamadı — `robots.txt okunamadı … ProxyError` kaydedildi, BİM "hata" durumuna geçti, eski veri korundu, diğer mağazalar etkilenmedi |

## 2. Demo / sentetik fikstürlerle yapılan testler
Ayrıştırıcı mantığını doğrular; canlı sitede çalıştığını **kanıtlamaz**. HTML fikstürleri sentetiktir
(`backend/tests/fixtures/README.md`).

| Test | Sonuç |
|---|---|
| BİM: tarih sekmeleri, ürün kartı, fiyatsız ürünün reddi, CSS sınıfları değiştiğinde yedek ayrıştırma | ✅ |
| A101: HTML ürünleri, üstü çizili önceki fiyat, işaretsiz iki fiyatlı ürünün reddi, gömülü JSON'da birimsiz sayının reddi | ✅ |
| ŞOK kampanyalar: liste, kampanya fiyatı/koşul/başlangıç tarihi, 1 alana 1 bedava | ✅ |
| **ŞOK indirimli liste (yeni):** üstü çizili fiyat, işaretsiz iki fiyat (düşük=güncel), tek fiyat, üç fiyatlı kartın reddi, mükerrer bağlantı | ✅ |
| **ŞOK indirimli liste hata verirse kampanyalar yine okunur** (kaynak içi izolasyon, kısmi durumda ürün silinmez) | ✅ |
| Migros: JSON yapısı, kuruş→TL, eksik alanlı ürünün reddi | ✅ |
| Dört mağazanın birlikte çalışması (sahte HTTP) | ✅ |
| Bir mağazada zaman aşımı → diğerleri devam eder, hata ve durum kaydedilir | ✅ |
| 403 yanıtı aşılmaya çalışılmadan raporlanır; robots.txt yasağına uyulur; hız sınırlama; 5xx'te sınırlı yeniden deneme | ✅ |
| **Yönetici bildirimi (yeni):** başarısız taramada webhook'a tek bildirim; webhook çökse de tarama sürer | ✅ |
| Mükerrer kayıt, makul olmayan fiyat reddi, fiyat değişiminde geçmiş kaydı | ✅ |
| Devre dışı mağazanın atlanması; panelden elle tarama ve aç/kapat; yetkilendirme (401), zayıf parolada panel kapalı (503) | ✅ |

**Backend toplam:** 75 geçti, 0 başarısız (PostgreSQL testleri dahil; `FIRSAT_TEST_PG_URL` ile).
Dağılım: gerçek veri 19, sentetik fikstür 24, birim/API 32 (PostgreSQL 2 test gerçek veri grubunda).

## 3. Android derleme (Gradle'sız, API 36)
| Adım | Sonuç |
|---|---|
| Araç zinciri kurulumu (`tools/setup-toolchain.sh`, boş klasöre, SHA-256 doğrulamalı) ve aynı kurulumla derleme | ✅ |
| aapt2 kaynak derleme/bağlama (API 36) | ✅ |
| Kotlin derleme **yalnızca Android API'leriyle** (`-no-jdk`, android-36.jar) | ✅ — ilk denemede **gerçek bir hata yakaladı**: katalog/web görüntüleyicide `WebView.apply { loadUrl(url) }` ekranın adresi yerine WebView'in boş `url` özelliğini yüklüyordu (0.1.0'da resmî sayfa açılmıyordu). Düzeltildi |
| ProGuard küçültme | ✅ |
| **minSdk 26 API denetimi** (488 platform başvurusu, API 26 jar'ına karşı; kalıtım zinciri dahil) | ✅ 0 sorun · 5 başvuru `SDK_INT` korumalı olarak izinli (geri hareketi API 33, pencere çubukları API 30). Denetleyicinin çalıştığı API 23 jar'ıyla doğrulandı (24 sorun buldu) |
| d8 DEX (min-api 26), 4 bayt hizalama, resources.arsc sıkıştırılmamış | ✅ |
| v2+v3 imza, `apksigner verify` | ✅ |
| `aapt2 dump badging`: `com.firsat360.app`, sürüm 0.2.0-beta (2), minSdk 26, **targetSdk 36**, yalnızca INTERNET izni, `enableOnBackInvokedCallback=true` | ✅ |
| APK içinde gerçek feed (466 ürün), Inter yazı tipleri, simgeler; boyut 1,8 MB | ✅ |
| Cihazda/emülatörde kurulum, açılış, ekran testleri | ❌ yapılamadı (emülatör yok) |
| Gradle (Android Studio) derlemesi | ❌ test edilemedi (Google Maven erişimi yok) |

## Çalışan özellikler (kod ve testlerle doğrulanan)
Gerçek veriyle: ana sayfa, 4 mağaza sekmesi, aktif/yakında başlayacak kataloglar, ürün kartları, kategori filtreleri
(16 kategori), Türkçe duyarlı arama, sıralama, ürün detayı, **fiyat geçmişi (2 günlük gözlem, özet + grafik)**,
favoriler (cihazda), resmî sayfayı/katalogu uygulama içinde açma (düzeltildi), tarayıcıda açma, paylaşma,
son güncelleme bilgisi + **36 saatten eski veri uyarısı**, sunucudan güncelleme + önbellek, açık/koyu tema,
Android 15/16 kenardan kenara düzen ve **Android 16 geri hareketi** (targetSdk 36 gereği).

## Kalan eksikler
1. **Cihaz testi yapılmadı.** APK'yı Android 8+ bir cihaza kurup ana akışları deneyin; çökme olursa
   `adb logcat -d | grep -A20 FATAL` çıktısı yeterli.
2. **Canlı bağlayıcılar** mağaza sitelerine erişimi olan bir makinede ilk kez çalıştırılmalı. BİM CSS sınıfları,
   A101 sayfa yapısı ve ŞOK fiyat işaretlemesi gözlenen metne dayalı varsayımdır (yedek ayrıştırma yolları var).
3. **Kullanım koşulları** dört mağaza için okunmadı; yayın öncesi kontrol ve tercihen yazılı izin (özellikle Migros JSON ucu).
4. BİM robots.txt iki gün de okunamadı; bağlayıcı her çalıştırmada kontrol eder.
5. BİM sekmelerinde ve ŞOK listesinde yalnızca ilk yüklenen ürünler alındı (ŞOK: 292 üründen 40).
6. Jetpack Compose yerine yerel Android View'ları kullanıldı (ortam kısıtı). Gradle dosyaları hazır, test edilmedi.
7. Google Play için: release imzası, gizlilik politikası, Data Safety beyanı, kapalı test (12 test kullanıcısı / 14 gün).
8. Fiyat alarmı ve bildirimler bu beta kapsamında değil.
9. `usesCleartextTraffic=true` yerel test içindir; yayından önce kapatılmalı.
10. Araç zinciri provenance: aapt2 ikilisi npm paketinden (Google dağıtımıyla bayt karşılaştırması yapılamadı);
    d8/apksigner hash'leri paket beyanındaki Google build-tools 34.0.0 değerleriyle eşleşti.
