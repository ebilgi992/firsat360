# Test raporu

## 1.4.0 (21.10.2026) — İstemci Katalog Köprüsü
| Test | Sonuç | Kanıt |
|---|---|---|
| Köprü betikleri gerçek Chromium'da (ŞOK PDF+tarih, FlipHTML5 tam liste, Gratis → kitap, Migroskop Cover/Ad, A101 Next verisi, genel dikey sayfalar, 5 profilde doğrulama sayfası) | PASS | `tests/test_bridge_scripts.py` |
| React Native `postMessage` sarmalayıcısı Android dönüşüyle aynı sonucu veriyor (7 sayfa) | PASS | `test_react_native_wrapper_posts_same_result` |
| 6 market `client_rendered`, feed `client_bridge` güvenli (https, profil dosyası var), RN betik paketi güncel | PASS | `tests/test_client_bridge_config.py` |
| Backend pytest (tam) | PASS | 176 geçti, 2 atlandı (PostgreSQL) |
| Çekirdek (JVM), 0.6.1 başvuru feed'i — `trdates.*` dahil | PASS | 172/172 |
| RN bileşeni tür denetimi | PASS | `npx tsc --noEmit` (react-native 0.76.5, webview 13.12.5, pdf 6.7.5) |
| Debug + release APK/AAB, zip testi, gizli anahtar taraması, `assets/bridge/*.js` APK'da | PASS | `dist/FIRSAT360-1.4.0*` |
| Cihazda resmî sayfadan köprü akışı (6 market), RN uygulamasında çalıştırma | BLOCKED | cihaz/emülatör ve RN projesi yok |

## 1.2.0 (21.10.2026)
| Market | Test | Sonuç | Kanıt |
|---|---|---|---|
| A101 | Sunucuda gerçek katalog | BLOCKED | 17:15 UTC: `/aldin-aldin`, `/afisler` → 403, Cloudflare sınaması; robots.txt izin veriyor, engel IP tabanlı |
| A101 | Cihazda resmî sayfadan sayfa toplama | BLOCKED (cihaz yok) | kod: `WebScreen.collectPages`, `DeviceBrochures` |
| CarrefourSA | Sunucuda gerçek katalog | BLOCKED | robots.txt dahil 403 (Cloudflare); resmî sitede broşür bölümü yok (WebFetch: yalnızca "Katalog Ürünleri" menü etiketleri) |
| File | Afişler doğru sınıflandırılıyor | PASS | `test_file_posters_are_not_brochures`; feed'de `kind=poster` |
| Onur | Mükerrer gösterim giderildi | PASS | `onur.noGroupDup`, `onur.oneCardPerInsert`, `onur.userRegion`, `onur.bursa` |
| Onur | Süresi dolan insert çıkarılıyor | PASS | `onur.expiredRemoved` (22.10'da yalnızca 944 kalır) |
| BİM | Regresyon | PASS | 12 güncel sayfalı aktüel feed'de |
| Migroskop | Regresyon | PASS | 160 sayfa feed'de |
| ŞOK | Regresyon | PASS | 112 ürün, %100 görsel |
| Hakmar, Metro | Regresyon | PASS | 2 + 1 sayfalı broşür |
| Merkezi durum kuralı | PASS | `state.expiring/active/expired/unknownNotExpiring/upcoming` |
| Aynı içerik farklı adres → tek kart | PASS | `dedupe.sameContentDifferentUrl` |
| Kontrollü geri çekilme | PASS | `test_backoff_after_three_blocked_runs` |
| Backend pytest | PASS | 150 geçti, 2 atlandı |
| Çekirdek (JVM) | PASS | 168/168 |
| Debug + release APK/AAB | PASS | `dist/FIRSAT360-1.2.0*` |
| Uygulama ekranları, PDF açma, okuyucu | BLOCKED | cihaz/emülatör yok |

## 1.1.0 (21.10.2026)
| Market | Gerçek katalog bulundu mu | Sayfa/PDF | Doğrulandı mı | Uygulamada açılıyor mu | Güncel mi |
|---|---|---|---|---|---|
| Onur | ✅ | 6 PDF | ✅ `%PDF` imzası | PDF okuyucu (uygulama içi) — cihazda BLOCKED | ✅ 13–26 / 15–21 Ekim |
| File | ✅ | 5 afiş görseli | ✅ JPEG imzası | okuyucu — cihazda BLOCKED | tarih yok (afiş) |
| A101 | ❌ sunucuda (Cloudflare) | — | — | cihazda resmî sayfa + "Sayfa sayfa aç" — BLOCKED | — |
| CarrefourSA | ❌ sunucuda (Cloudflare) | — | — | cihazda resmî sayfa — BLOCKED | — |
| BİM | ✅ | 12 güncel sayfalı aktüel | ✅ | BLOCKED (cihaz) | ✅ |
| Migros/Migroskop | ✅ | 160 sayfa | ✅ | BLOCKED (cihaz) | ✅ Sayı 908 (8–21 Ekim) |
| ŞOK | ✅ ürün listesi (112 ürün, %100 görsel) | broşür yok (kampanya afişleri 13.10'dan beri kaynakta yok → gizlendi) | ✅ | BLOCKED | ✅ |
| Hakmar | ✅ | 2 broşür (20–28 Ekim insert, Kırtasiye) | ✅ | BLOCKED | ✅ |
| Metro | ✅ | 1 broşür | ✅ | BLOCKED | ✅ |

| Test | Sonuç |
|---|---|
| Python `pytest` | PASS — 147 geçti, 2 atlandı (PostgreSQL) |
| Onur gerçek HTML'de ayrıştırma (teşhis dalındaki ham sayfa) | PASS — 944, 13–26 Ekim, doğru PDF/kapak |
| Hata sayfası görsel/PDF sayılmıyor (`sniff`) | PASS |
| Ana sayfa mükerrer (`home.noDuplicates`) | PASS |
| Android çekirdek 157/157, debug + release (APK+AAB) derleme | PASS |
| Cihaz/emülatör akışları | BLOCKED (bu ortamda KVM yok) |

## 1.0.0 (V1) — 21.10.2026

Sonuçlar: PASS = çalıştırıldı ve geçti · FAIL = çalıştırıldı, başarısız · BLOCKED = bu ortamda çalıştırılamadı.

## P0
| Test | Nasıl | Sonuç |
|---|---|---|
| Gerçek kaynak uçtan uca (kaynak → DB → feed → uygulama verisi) | Canlı GitHub Actions çalıştırması 39633480295 (16:18 UTC): Migroskop 908 resmî dijital katalog **160 sayfa**, BİM 22 sayfalı aktüel, Hakmar/Metro/Watsons sayfalı broşürler veritabanına ve feed.json'a yazıldı; uygulamaya gömülü feed'de mevcut | PASS |
| Katalog uygulamada açılıyor (görsel/PDF) | Okuyucu kodu + feed'deki sayfa adresleri; cihazda açılış | BLOCKED (cihaz/emülatör yok) |
| Mükerrer katalog engelleniyor | `home.noDuplicates` (çekirdek, gerçek feed) + bayat katalog testi `test_stale_catalog_hidden_from_feed`; canlı veride Migroskop mükerreri kaldırıldı | PASS |
| Market filtresi / Türkçe arama | Çekirdek testleri (arama, TrNorm, market adı algılama) | PASS |
| Hatalı kaynak uygulamayı/hattı çökertmiyor | Kaynak izolasyonu testleri; canlıda 5 hatalı kaynakla çalıştırma başarılı | PASS |
| Boş veri / çevrimdışı | `fetch.badKeepsCache`, `fetch.downKeepsCache` (çekirdek) | PASS |
| Yönetici olmayan yönetim işlemi yapamaz | Yönetim GitHub üzerinden (depo sahibi + GitHub 2FA); salt okunur API'de yazma 405 (`test_readonly_api`) | PASS |
| Gizli anahtar istemcide yok | APK içerik taraması (desen araması) | PASS |
| Testler üretim verisine dokunmuyor | Tüm testler geçici SQLite; canlı DB yalnızca kopyası üzerinde | PASS |
| SSRF koruması | `test_ssrf_guard_*` (localhost, özel IP, meta-data adresi, file://) | PASS |

## P1
| Test | Sonuç |
|---|---|
| Favoriler kalıcı (SharedPreferences) | PASS (kod incelemesi; cihaz testi BLOCKED) |
| Paylaşım (okuyucu, ürün) | BLOCKED (cihaz) |
| Bildirim izni yönetimi | PASS (kod) / BLOCKED (cihaz) |
| Farklı ekran boyutları | BLOCKED |

## Otomatik test paketleri
| Paket | Sonuç |
|---|---|
| Python `pytest` (backend) | **142 geçti, 2 atlandı** (PostgreSQL sunucusu gerektiren testler) |
| Android çekirdeği JVM (`CoreTestsKt`, 0.6.1 başvuru feed'i) | **157/157** |
| Android çekirdeği, güncel canlı feed ile — raf/ana sayfa testleri | PASS (sayım testleri başvuru feed'ine göre yazıldığından o feed'de çalıştırılır) |
| Android derleme (debug + release APK + AAB), min-API ve açılış sırası denetimi | PASS |
| bandit (statik güvenlik) | 0 bulgu |
| REA `doctor` | PASS — REA 6.7.0 çalışıyor; Claude Code kaydı yapılmadı (geçici bulut ortamı) |
| REA `inspect-android-package` (kendi APK'mız) | BLOCKED — `jadx-headless-mcp 0.7.1` JAR'ı gerekli, REA indirmiyor |
| Zamanlanmış görev | PASS — 21.10 günü 4 başarılı çalıştırma (2 zamanlanmış, 2 elle), günlükler `veri` dalında |
