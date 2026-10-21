# Yayın kontrol listesi — 1.3.0 (versionCode 15)

| Madde | Durum |
|---|---|
| Sürüm 1.3.0 (versionCode 15) — build.gradle.kts, BuildInfo.kt, build-nogradle.sh | ✅ |
| Debug APK (`dist/FIRSAT360-1.3.0.apk`, proje debug anahtarı — eski sürümün üzerine kurulur) | ✅ |
| Release APK + AAB (`dist/FIRSAT360-1.3.0-release-unsigned.*`) | ✅ imzasız — `tools/sign-release.sh` ile **sizin yükleme anahtarınızla** imzalanmalı |
| İmza anahtarı depoda yok | ✅ |
| Gizlilik politikası (docs/privacy.html → GitHub Pages) | ⚠️ iletişim e-postası yer tutucu, doldurulmalı |
| Veri güvenliği beyanı: hesap yok, kişisel veri toplanmaz, favoriler cihazda | ✅ (Play formu sizin tarafınızdan doldurulur) |
| İzinler: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED | ✅ |
| Veritabanı geçişi | ✅ `catalogs.region` sütunu açılışta otomatik eklenir (`db.make_session_factory` → ADD COLUMN, geri alınabilir: sütun yok sayılır) |
| Geri dönüş planı | `git revert 1.0.0 commit'i` → Actions → Run workflow; uygulama tarafında önceki APK (0.8.0) üzerine kurulamaz (versionCode düşük) → kaldır/kur |
| Cihaz/emülatör testi | ❌ BLOCKED — yapılmadı |
| Google Play yükleme | ❌ yapılmadı (hesap + yükleme anahtarı sizde; 12 test kullanıcısı / 14 gün kuralı) |
| Bilinen sınırlamalar | A101/CarrefourSA/File/Onur broşürleri sunucuda toplanamıyor (403/robots/JS); uygulama resmî sayfayı cihazda açar |

## Yönetim (sunucusuz, ücretsiz)
- **Market kapat/aç:** `config/sources.json` → `"disabled": ["file"]` → commit.
- **Şimdi dene:** GitHub → Actions → gunluk-veri → Run workflow → `stores` = `bim,sok` (boş = hepsi).
- **Son tarama / son başarı / hata:** Actions iş özeti, `status.json`, uygulamada Ayarlar → Kaynak durumu,
  açık hata varsa "🔧 Otomatik kalite raporu" issue'su.
- **Hatalı katalog:** kalite ajanları otomatik kaldırır; elle: issue başlığı `[Hatalı veri]` + `offer:<id>`.
