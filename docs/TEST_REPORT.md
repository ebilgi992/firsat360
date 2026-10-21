# Test raporu

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
