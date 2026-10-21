# FIRSAT360 — Aktüel katalog ve fırsat uygulaması (Beta 0.6.1)

Marketlerin aktüel katalogları, broşürleri ve indirimli ürünleri tek uygulamada. **Tamamen ücretsiz çalışır:**
sunucu, yönetim paneli veya ücretli servis yoktur. Veri her gün GitHub Actions ile otomatik toplanır ve GitHub
Pages'te yayımlanır; uygulama oradan okur.

```
android-app/   Android uygulaması (Kotlin, ek kütüphane yok) + Gradle'sız derleme betiği
backend/       Günlük veri hattı (Python): 10 marketin otomatik bağlayıcıları, görsel doğrulama, duyuru, feed üretimi
config/        app_config.json — uygulamanın uzaktan ayarları (GitHub'da düzenlenir)
data/          İlk kurulum tohumu: 12–13.10.2026 doğrulanmış okumalar
.github/       Günlük iş akışı (08:05 ve 00:05)
docs/          Kurulum, test, güvenlik, Play yayın, kaynak belgeleri
```

## Kurulum
- **Uygulama:** `dist/FIRSAT360-0.6.1-beta-debug.apk` → telefona aktarın → kurun (Android 8.0+). Önceki beta'ların
  üzerine güncellenir. `com.firsat360.beta` (yeni-paket) kurduysanız `…-yeni-paket-debug.apk`'yı kurun.
- **Canlı veri (ücretsiz):** `docs/CANLI_KURULUM.md` — GitHub'da depo + Pages + Actions açılır, APK yayın adresiyle
  derlenir (`FEED_URL=https://<kullanıcı>.github.io/firsat360/feed.json bash android-app/build-nogradle.sh`).
- **Elle güncelleme:** GitHub → Actions → "gunluk-veri" → Run workflow (telefondaki GitHub uygulamasından da olur).
- **Uzaktan ayar:** GitHub'da `config/app_config.json` dosyasını düzenleyin; sonraki çalıştırmada uygulamaya ulaşır.

## Uygulamada neler var
- **Ana sayfa:** arama, Günün İndirimleri, Kataloglar (yeni olanlar "YENİ"), Marketler.
- **Kataloglar uygulama içinde:** sayfa görselli kataloglar yakınlaştırılabilir okuyucuda, PDF kataloglar PDF
  okuyucuda, flipbook kataloglar (Bizim Toptan, A101 afişleri, Migroskop dijital…) tam ekran uygulama içi görünümde.
  Watsons ve Metro (Publitas) ile Gratis (FlipHTML5) kataloglarının sayfaları günlük çalıştırmada otomatik çekilir.
- **Arama:** Türkçe harf duyarlı, market adıyla ("bim", "BİM market"), yazım hatası toleranslı.
- **Bildirim:** "🛒 Günün katalogları yayınlandı!" — günde tek özet, saat ve market seçimi.
- Ürün: resmî fotoğraf, fiyat, önceki fiyat, diğer marketlerde fiyat, alışveriş listesi, favoriler.

## Geliştirici
```bash
cd backend && pip install -r requirements.txt && python -m pytest -q          # veri hattı testleri
python -m firsat360 daily --no-live                                              # gömülü veriden feed üret
cd android-app && kotlinc app/src/main/java/com/firsat360/app/core app/src/test/java -d /tmp/core.jar \
  && kotlin -cp /tmp/core.jar com.firsat360.app.core.CoreTestsKt app/src/main/assets/feed.json   # çekirdek testleri
TOOLCHAIN=~/firsat360-toolchain bash android-app/build-nogradle.sh              # debug APK
RELEASE=1 bash android-app/build-nogradle.sh                                    # imzasız release APK + AAB
```
Araç zinciri: `android-app/tools/setup-toolchain.sh`. İmza: `android-app/signing/` (yalnızca beta için herkese açık
debug anahtarı); Play için kendi yükleme anahtarınız (`docs/PLAY_YAYIN.md`).

## Görsel ve veri politikası
Görseller ve kataloglar marketlerin **kendi adreslerinden** gösterilir; kopyalanmaz, yeniden yayımlanmaz. robots.txt'ye
uyulur, erişim engelleri aşılmaz. Herkese açık yayından önce marketlerden izin alınması önerilir (`docs/KAYNAKLAR.md`).
Test sonuçları ve bilinen eksikler: `docs/TEST_RAPORU.md`.
