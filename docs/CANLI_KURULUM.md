# FIRSAT360 canlı sistem kurulumu (ücretsiz, ~15 dakika)

Canlı sistem iki parçadır:

1. **Günlük veri hattı (sunucu tarafı):** GitHub Actions her gün 08:05 ve 00:05'te (Türkiye saati) çalışır. 10 marketin
   resmî sayfalarını kontrol eder, ürün fotoğraflarını ürünün kendi sayfasından doğrular, yeni katalogları **bir kez**
   duyurur, "Günün İndirimleri"ni seçer ve `feed.json` dosyasını GitHub Pages'te yayımlar.
2. **Uygulama:** veriyi bu sabit adresten alır (`https://<kullanıcı-adı>.github.io/firsat360/feed.json`). Adres APK'ya
   derlemede gömülür; kullanıcı görmez, değiştirmez.

Gizli bilgiler (push anahtarı vb.) yalnızca GitHub **Secrets** içinde durur; APK'da ve depoda yoktur.

## 1. Depoyu oluşturun
1. github.com'da ücretsiz hesap açın (varsa geçin).
2. **New repository** → ad: `firsat360` → **Public** → Create. (GitHub Pages ücretsiz planda herkese açık depoda çalışır.
   Depoda gizli bilgi yoktur; veri zaten marketlerin herkese açık sayfalarından gelir.)
3. Proje klasörünü bu depoya yükleyin (bilgisayarda):
   ```bash
   cd firsat360
   git init -b main && git add -A && git commit -m "FIRSAT360 0.5.0"
   git remote add origin https://github.com/<kullanıcı-adı>/firsat360.git
   git push -u origin main
   ```

## 2. GitHub Pages ve Actions'ı açın
1. Depo → **Settings → Pages → Build and deployment → Source: GitHub Actions**.
2. Depo → **Settings → Actions → General → Workflow permissions: Read and write permissions** → Save.
3. Depo → **Actions** → "gunluk-veri" → **Run workflow** (ilk çalıştırmayı hemen başlatır).
4. İş bitince özet sayfasında her marketin durumu tablo olarak görünür. Yayın adresi:
   `https://<kullanıcı-adı>.github.io/firsat360/feed.json` ve izleme için `.../status.json`.

İsteğe bağlı ayarlar (Settings → Secrets and variables → Actions → **Variables**):
| Değişken | Varsayılan | Açıklama |
|---|---|---|
| `NOTIFY_TIME` | `09:00` | Push bildiriminin zamanlanacağı saat (Türkiye) |
| `SCRAPER_USER_AGENT` | `FIRSAT360Bot/0.5 (+https://github.com/firsat360)` | Marketlerin sizi tanıyabilmesi için iletişim adresinizi yazın |
| `PRODUCT_PAGES_PER_STORE` | `150` | Her çalıştırmada market başına açılacak en fazla ürün sayfası |

## 3. APK'yı yayın adresiyle derleyin
Kullanıcı adınızı bana iletin; APK'yı bu adresle derleyip gönderirim. Kendiniz derlemek için:
```bash
cd android-app
FEED_URL=https://<kullanıcı-adı>.github.io/firsat360/feed.json bash build-nogradle.sh
```
Gradle ile: `app/src/main/res/values/config.xml` içindeki `feed_url` değerini yazıp `./gradlew assembleDebug`.

## 4. (İsteğe bağlı) Uygulama kapalıyken anında push: ntfy
Uygulama, yeni katalogları arka planda ~3 saatte bir ve bildirim saatinde kontrol eder; Android'in pil kısıtlamaları bunu
geciktirebilir. Tam zamanında push için ücretsiz, açık kaynak **ntfy** kullanılabilir (Firebase gerekmez):
1. Telefona **ntfy** uygulamasını kurun, tahmin edilmesi zor bir konuya abone olun (ör. `firsat360-8f3k2q`).
   Yalnızca belirli marketler için `firsat360-8f3k2q-bim`, `…-sok` gibi alt konulara abone olun (ikisini birden değil).
2. GitHub → Variables: `NTFY_URL=https://ntfy.sh`, `NTFY_TOPIC=firsat360-8f3k2q`.
   Kendi ntfy sunucunuzda erişim anahtarı kullanıyorsanız → **Secrets**: `NTFY_TOKEN`.
3. Push, sabah çalıştırmasında bulunan yeni kataloglar için `NOTIFY_TIME` saatine zamanlanır; bildirime dokununca FIRSAT360
   ilgili kataloğu açar (`firsat360://catalog/<id>`). Aynı katalog için ikinci push gönderilmez; başarısız gönderim en
   fazla 3 kez denenir.
4. ntfy.sh herkese açık sunucusunun ücretsiz kullanım sınırları vardır; güncel sınırları ntfy.sh/docs adresinden
   kontrol edin. Günde ~11 mesaj (genel + market konuları) bu sınırların çok altındadır.

**Firebase Cloud Messaging (FCM)** ile push isterseniz: Firebase projesi, `google-services.json` ve sunucu için servis
hesabı anahtarı gerekir; bunlar bu sürümde yoktur (bu derleme ortamı Google Maven'a da erişemedi).

## 5. Bilinmesi gerekenler
- **GitHub Actions zamanlaması** yoğun saatlerde birkaç dakika–yarım saat gecikebilir. Herkese açık depoda 60 gün hiç
  etkinlik olmazsa zamanlanmış iş akışları GitHub tarafından durdurulabilir; GitHub e-postayla uyarır, Actions
  sekmesinden tekrar etkinleştirilir.
- Bir market erişilemezse o marketin **son doğrulanmış verisi korunur**, durum dosyasına hata yazılır, diğer marketler
  etkilenmez. Hiçbir market güncellenemezse iş "başarısız" görünür (veri yine yayımlanır).
- Durum, `veri` dalında tek bir commit olarak saklanır (her çalıştırmada üzerine yazılır; depo büyümez).
  Sunucu günlükleri bu dalda `logs/` altında: en fazla 5 MB, 7 gün.
- Marketlerin kullanım koşulları okunmadı; herkese açık yayın öncesi marketlerden izin alınması önerilir
  (`docs/KAYNAKLAR.md`).
