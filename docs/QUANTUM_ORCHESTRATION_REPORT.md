# Orkestrasyon raporu — 1.2.0 (21.10.2026)

## Gerçekte kullanılan ajan yapısı (varsayım değil)
Bu oturumda ayrı alt ajan **çalıştırılmadı**: kullanıcı talimatı olmadan alt ajan başlatılmıyor ve işler aynı dosyalarda
sıralı bağımlıydı. Rol ayrımı, veri hattındaki **gerçek kalite ajanları** (`backend/firsat360/quality.py`) ve her değişiklik
grubundan sonra çalıştırılan testlerle uygulandı. Ortak dosyaları tek "orkestra şefi" (bu oturum) birleştirdi.

| Rol | Bu sürümde yaptığı | Kanıt |
|---|---|---|
| Kaynak istihbaratı | A101/CarrefourSA engelini canlı günlükle yeniden doğruladı (Cloudflare, 17:15 UTC hâlâ geçerli) | `veri` dalı `stores.last_error` |
| Tarayıcı otomasyonu | `probe()` (GET + ilk bayt), yönlendirme sınırı 5, 403 teşhisi | `tests/test_onur.py`, `test_discovery.py` |
| PDF/görsel | `%PDF`/JPEG/PNG/WEBP imzası; File afişleri `poster` türü | `tests/test_v12.py` |
| Veri bütünlüğü | `catalog_group`, `region` sütunu (otomatik migration), merkezi `CatalogRules.state` + `collapse` | `CoreTestsKt` onur.*, state.*, dedupe.* |
| UI/UX | bölge seçici, afiş etiketi, tek-bölüm kuralı | `UX_REDESIGN_REPORT.md` |
| QA/güvenlik | 150 backend testi, 168 çekirdek testi, bandit, derleme | `TEST_REPORT.md` |
| Veri hattı kalite ajanları (çalışır) | kaynak, katalog (sahte sayfa), ürün görseli, veri kalitesi, günün indirimleri, kullanıcı bildirimi | her çalıştırmada Actions özeti + issue |

## Açık kaynak değerlendirmesi
| Proje | Lisans | Karar | Gerekçe |
|---|---|---|---|
| REA 6.7.0 (morluto/rea) | MIT | Denendi, entegre edilmedi | `doctor` çalışıyor; APK analizi ayrıca `jadx-headless-mcp 0.7.1` JAR'ı istiyor; rakip APK'ları yasal olarak edinilemiyor |
| Playwright (microsoft/playwright) | Apache-2.0 | **Kullanılıyor** (isteğe bağlı) | JS'li sayfalar + XHR/fetch JSON yakalama; Actions'ta kuruluyor |
| Crawlee (apify/crawlee) | Apache-2.0 | Eklenmedi | Node/Python sürümü kuyruk/oturum yönetimi sağlar; mevcut `PoliteClient` + bağlayıcı mimarisi aynı işi yapıyor, engelli siteleri de açmaz → ölçülebilir fayda yok |
| PDF.js (mozilla/pdf.js) | Apache-2.0 | Eklenmedi | Uygulama Android'in yerleşik `PdfRenderer`'ı ile PDF'yi yerel olarak gösteriyor (WebView'e bağımlı olmadan, daha hafif) |
| n8n | Sustainable Use License (kaynak açık, tam açık kaynak değil) | Eklenmedi | Sunucu gerektirir (ücretsiz/sunucusuz kuralı); zamanlama zaten GitHub Actions'ta |

## Proje becerileri (`.claude/skills/`)
`catalog-source-discovery`, `catalog-file-validation`, `catalog-deduplication`, `source-failure-diagnostics`,
`catalog-e2e-testing` — her biri depodaki gerçek komut ve dosyalara işaret eder. Bu oturumda tetiklenmeleri
**denenmedi** (beceriler depo yeniden açıldığında yüklenir); `premium-mobile-ux-review` cihaz olmadan doğrulanamayacağı için yazılmadı.
