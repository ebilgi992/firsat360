# FIRSAT360 — 1.9.0 Beta

Market broşürleri, ürün fiyatları, alışveriş listesi ve cihazda çalışan Fırsat Asistanı.
Android 8.0 ve üzeri; ücretsiz, hesap gerektirmez. Canlı yayın GitHub Actions ile toplanıp GitHub Pages'te yayımlanır.

## İndir

[GitHub sürümleri ve APK seçenekleri](https://github.com/ebilgi992/firsat360/releases)

- **Standart APK:** `FIRSAT360-21-app.apk` — paket `com.firsat360.app`.
- **Alternatif paket:** `FIRSAT360-21-beta.apk` — yalnız `com.firsat360.beta` kuruluysa seçin.
- Mevcut uygulamanın üzerine kurmak için aynı paket ve imza gerekir. Bu beta önceki beta ile aynı,
  depodaki herkese açık geliştirme anahtarıyla imzalanır. Özel üretim imzası değildir.
- APK yanındaki `.sha256` dosyası bütünlük kontrolü içindir. Android kurulum onayı ister.

[Web vitrini](https://ebilgi992.github.io/firsat360/) · [Ürün bazlı düzeltme planı](docs/REPAIR_PLAN_2026-10-26.md)

## 1.9 değişiklikleri

Broşür görselleri/PDF artık ürün listesinden önce açılır; tek sayfalık afişler de okunur. Asistan bütçe,
ambalaj hacmi ve litre/kilogram fiyatıyla karşılaştırır; açık ürün özelliklerini kaybetmez. Market ekranında
resmî Instagram bağlantıları bulunur. A101 yapılandırılmış ürün verisi ve TK Koop tam ad ayrıştırması geliştirildi.

[Ayrıntılı sürüm ve kaynak inceleme raporu](docs/RELEASE_1.9.md).

## 1.8 değişiklikleri

- Varsayılan canlı feed adresi, çevrim dışı önbellek ve başarısız kontrol sonrası tekrar yenileme.
- Bildirim izni/kanalı engelinde yeni katalog adaylarının korunması; Ayarlar'da deneme bildirimi.
- “En ucuz süt” sorgusunda içilecek süt; açıkça “süt tozu” sorgusunda süt tozu. Süresi bitmiş ürün önerilmez.
  En düşük fiyat kayıtlı veriler içindeki **paket fiyatıdır**, farklı hacimlerde litre fiyatı iddiası değildir.
- Açılış ve Ayarlar'da güncelleme kontrolü. Uygulama içinde indirme, SHA-256/paket/sürüm/imza kontrolü ve
  Android'in kullanıcı onaylı kurucusu. Sistem güncelleme sırasında uygulamayı yeniden başlatabilir.
- Gerçek yayını okuyan web vitrini, market filtresi ve yayımlanmış APK seçenekleri.
- Kalite raporunda kaynak hataları ile görseli eksik ürünlerin ayrı sayılması ve ürün bazlı kanıt.

## Bilinen dış kaynak eksikleri

- A101 Cloudflare engeli aşılmaz. Kaynak engelliyken ek ürün sayfası istekleri ve kalite tekrarları atlanır;
  eski ürünler güncel gösterilmez. A101_Afis yardımcı broşür keşfi için denetlenir; hata, eski çıktı ve resmî CDN
  dışı adresler reddedilir. İncelemede bu depo çalışan güncel veri sağlamıyordu.
- TK Koop'taki Banvit Nugget 1000 g, Doyfarm Nugget 1 kg, Renax 60'lı kapsül ve Solo Bambu 16'lı havlunun resmî
  detay sayfaları `yok.png` yayımlıyor. Başlık ayrıştırması düzeltildi; ilgisiz ürün fotoğrafı kullanılmaz.
  [Dört gerçek HTML üzerindeki doğrulama sonucu](docs/TK_IMAGE_EVIDENCE_2026-10-26.json).
- Android pil tasarrufu ve zorla durdurma bildirimleri geciktirebilir. Gerçek telefonda bildirim ve kurulum
  teslimi ayrıca kontrol edilmelidir. Ayarlar › Deneme bildirimi gönder, izin ve kanalı denetlemeyi sağlar.

## Geliştirme

```bash
cd backend
pip install -r requirements.txt
python -m pytest -q
cd ..
PYTHONPATH=backend python scripts/export_core_test_feed.py /tmp/core-test-feed.json
cd android-app
kotlinc app/src/main/java/com/firsat360/app/core app/src/test/java -d /tmp/core.jar
kotlin -cp /tmp/core.jar com.firsat360.app.core.CoreTestsKt /tmp/core-test-feed.json
```

JVM testleri 12–13 Ekim tarihsel kaynaklarından üretilir; değişen canlı APK verisi test girdisi değildir.
Doğrulama: **175 Python testi geçti, 25 atlandı; 216 Kotlin testi geçti**. CI ayrıca APK oluşturur, minSdk 26
API başvurularını, açılış sırasını ve imzayı denetler. Dal testi başarıyla tamamlanmadan APK yayımlanmaz.

`android-app/tools/setup-toolchain.sh` araç zincirini SHA-256 ile doğrular. Derleme:
`TOOLCHAIN=<araç-dizini> bash android-app/build-nogradle.sh`.
Canlı veri adresi varsayılan olarak `https://ebilgi992.github.io/firsat360/feed.json`; `FEED_URL` ile değiştirilebilir.

`gunluk-veri` günde iki kez (08:05 ve 00:05 Türkiye saati), ilgili ana dal değişikliklerinde veya elle çalışır.
Android derleme iş akışı ana dalda beta GitHub sürümünü ve iki paket seçeneğini yayımlar.

## Kaynak ve görsel politikası

Görseller resmî market adreslerinden bağlantıyla gösterilir; başka ürünün fotoğrafı atanmaz. robots.txt ve
403/429 erişim engellerine uyulur. Resmî kaynaktan doğrulanamayan fiyat, tarih veya görsel uydurulmaz.
Diğer market depoları yalnız güncellik, erişim ve kullanım koşulları doğrulanırsa eklenir.

Kurulum: [CANLI_KURULUM](docs/CANLI_KURULUM.md) · [Gizlilik](docs/privacy.html) · [Kaynaklar](docs/KAYNAKLAR.md)
