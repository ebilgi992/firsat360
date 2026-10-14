# Google Play yayın hazırlığı — FIRSAT360 0.6.0

Bu belge Play Console'da **hesap sahibinin** yapması gereken adımları içerir. Hesap açma, kimlik doğrulama, beyanlar ve
yükleme işlemleri tarafımızdan yapılmadı/yapılamaz.

## 1. Teknik gereksinimler (14.10.2026 kontrolü)
| Gereksinim | Durum |
|---|---|
| Hedef API: 31.08.2026'dan itibaren yeni uygulama ve güncellemeler **API 36** hedeflemeli ([kaynak](https://developer.android.com/google/play/requirements/target-sdk)) | ✅ targetSdk 36 |
| minSdk | 26 (Android 8.0) — Android 8.0 ve üzeri cihazlar |
| AAB biçimi | ✅ `FIRSAT360-0.6.0-beta-release-unsigned.aab` (bundletool 1.18.1 ile üretildi, `validate` geçti) |
| 64 bit / 16 KB sayfa boyutu | ✅ Yerel (.so) kitaplık yok → mimariden bağımsız, 16 KB uyumluluğu gerektirmez |
| Debuggable | ✅ Release manifestinde `android:debuggable` yok |
| Gizli anahtar | ✅ APK/AAB taramasında API anahtarı, parola, token, özel anahtar bulunmadı |
| Yedekleme | `allowBackup=false` |
| Açık (cleartext) HTTP | Kapalı (`usesCleartextTraffic=false`) |

## 2. İmzalama (Play App Signing)
1. Yükleme anahtarınızı **kendi bilgisayarınızda** oluşturun ve güvenli yerde saklayın (depoya koymayın):
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 4096 -validity 9125`
2. Parolaları ortam değişkeniyle verip imzalayın (komut geçmişine yazılmaz):
   ```bash
   read -s RELEASE_STORE_PASS; export RELEASE_STORE_PASS; export RELEASE_KEY_PASS=$RELEASE_STORE_PASS
   bash android-app/tools/sign-release.sh upload.jks upload dist/FIRSAT360-0.6.0-beta-release-unsigned.aab
   ```
   veya doğrudan derleyin: `RELEASE=1 RELEASE_KEYSTORE=upload.jks RELEASE_KEY_ALIAS=upload FEED_URL=… bash android-app/build-nogradle.sh`
3. Play Console → Uygulama → **Play App Signing**'i kabul edin (Google uygulama imzalama anahtarını yönetir; siz yalnızca
   yükleme anahtarıyla imzalarsınız).
4. **Önemli:** Yayın sürümü `FEED_URL` ile derlenmeli (canlı veri). Bkz. `docs/CANLI_KURULUM.md`.

## 3. Mağaza girişi (öneri metinleri)
- **Uygulama adı (≤30):** FIRSAT360: Aktüel Katalog
- **Kısa açıklama (≤80):** Marketlerin aktüel katalogları, günün indirimleri ve fiyat karşılaştırması tek yerde.
- **Tam açıklama:**
  > FIRSAT360 ile marketlerin haftalık aktüel kataloglarını, broşürlerini ve indirimli ürünlerini tek uygulamada inceleyin.
  > • Her gün otomatik güncellenen kataloglar ve “Günün İndirimleri”
  > • Yeni katalog yayınlandığında günde tek özet bildirim (saat ve market seçimi sizde)
  > • Türkçe karakterlere duyarlı hızlı arama: ürün, marka veya market adı
  > • Aynı ürünün diğer marketlerdeki fiyatı ve fiyat geçmişi
  > • Favoriler, alışveriş listesi, çevrim dışı katalog kaydetme
  > Hesap gerekmez; kişisel veri toplanmaz.
  > FIRSAT360 marketlerin resmî uygulaması değildir ve marketlerle bağlantılı değildir. Fiyatlar marketlerin resmî
  > sayfalarından alınır; fiyat ve stok durumu mağazaya göre değişebilir.
- **Uyarı (Play politikası — fikri mülkiyet / taklit):** Simge, ekran görüntüsü ve açıklamada market **logoları** kullanmayın;
  market adlarını yalnızca bilgi amaçlı geçirin. Uygulama içi ürün/katalog görselleri marketlerin sitelerinden gösterilir;
  herkese açık yayından önce marketlerden yazılı izin alınması önerilir (`docs/KAYNAKLAR.md`).
- **Ekran görüntüleri:** Bu ortamda emülatör olmadığı için **üretilmedi**. Telefonda alın: en az 2 adet, 1080×1920 önerilir
  (ana sayfa, Günün İndirimleri, katalog okuyucu, arama, ürün detayı).
- **Simge:** `android-app/app/src/main/res/mipmap-*` (512×512 mağaza simgesi `scripts/make_icons.py` ile üretilebilir).
  **Özellik grafiği** 1024×500 hazırlanmalı.

## 4. Uygulama içeriği beyanları
| Bölüm | Cevap |
|---|---|
| Gizlilik politikası URL'si | `https://<kullanıcı-adı>.github.io/firsat360/privacy.html` (iş akışı yayımlar). **İçindeki iletişim e-postasını doldurun.** |
| Reklamlar | **Hayır, reklam içermiyor** (0.6.0). AdMob eklenirse güncellenmeli. |
| Uygulama erişimi | Tüm işlevler giriş gerektirmeden kullanılabilir |
| İçerik derecelendirmesi (IARC) | Alışveriş/bilgi; şiddet, kumar, kullanıcı etkileşimi, konum paylaşımı yok → beklenen: 3+/Herkes |
| Hedef kitle | **18 yaş ve üzeri** önerilir (alışveriş içeriği; Aile Politikası kapsamına girmemek için) |
| Haber uygulaması | Hayır |
| Devlet uygulaması / finans / sağlık | Hayır |

### Veri güvenliği formu (veri envanteri)
| Veri | Toplanıyor mu? | Not |
|---|---|---|
| Kişisel bilgiler (ad, e-posta, kimlik) | Hayır | Hesap yok |
| Konum | Hayır | Konum izni yok |
| Kişiler, fotoğraflar, dosyalar | Hayır | Depolama izni yok |
| Uygulama etkinliği / arama geçmişi | **Cihazda kalır, gönderilmez** → "toplanmıyor" | Ayarlardan kapatılır/silinir |
| Uygulama içi mesaj, ödeme | Hayır | |
| Kilitlenme günlükleri | Otomatik gönderilmez; kullanıcı isterse kendi seçtiği uygulamayla paylaşır | |
| Cihaz/reklam kimliği | Hayır | Reklam SDK'sı yok |
| Aktarım sırasında şifreleme | **Evet** (yalnızca HTTPS) | |
| Veri silme talebi | Sunucuda kullanıcı verisi yok; cihazdaki veriler uygulama kaldırılınca silinir | |

> Not: Ağ isteklerinde IP adresi barındırma sağlayıcısına (GitHub Pages) ve görsel indirirken marketlerin CDN'lerine
> doğal olarak ulaşır; uygulama bunu saklamaz/işlemez. Formu doldururken Google'ın güncel tanımlarını kontrol edin.

### İzin gerekçeleri
| İzin | Neden |
|---|---|
| INTERNET | Katalog ve fiyat verisi, ürün görselleri |
| ACCESS_NETWORK_STATE | "Yalnızca Wi-Fi" tercihi ve mobil veride görsel indirme ayarı |
| POST_NOTIFICATIONS | Günlük yeni katalog özeti (kullanıcı açarsa, Android 13+) |
| RECEIVE_BOOT_COMPLETED | Telefon yeniden başlayınca günlük kontrolü yeniden planlamak |
Kesin alarm, konum, kamera, depolama, kişiler izni **yok**.

## 5. Test ve yayın kontrol listesi
- [ ] `docs/CANLI_KURULUM.md` adımları tamam; GitHub Actions ilk çalıştırmasında market durumları kontrol edildi
- [ ] APK `FEED_URL` ile derlendi; telefonda veri güncelleniyor
- [ ] Gizlilik politikasındaki e-posta dolduruldu, URL açılıyor
- [ ] Yükleme anahtarı oluşturuldu, yedeklendi; AAB imzalandı
- [ ] Telefonda: temiz kurulum, 0.5.0'ın üzerine güncelleme (favoriler korunuyor mu), bildirim izni ver/reddet,
      uçak modu → aç, koyu tema, ekran döndürme, büyük yazı boyutu, 320 dp dar ekran (Ayarlar → Ekran boyutu: en büyük)
- [ ] Play Console → **Kapalı test** (en az 12 test kullanıcısı / 14 gün — yeni kişisel geliştirici hesapları için
      Google'ın güncel koşulunu Play Console'da kontrol edin)
- [ ] Mağaza girişi, ekran görüntüleri, özellik grafiği, içerik derecelendirmesi, hedef kitle, veri güvenliği formu
- [ ] Marketlerden görsel/veri kullanım izni (öneri)
