# Reklam (AdMob) ve bildirim yapılandırma rehberi

## Mevcut durum (0.6.0) — dürüst özet
- **APK'da reklam SDK'sı yok.** Bu derleme ortamı Google Maven deposuna (maven.google.com) erişemediği için Google Mobile
  Ads SDK'sı ve UMP (rıza) kitaplığı eklenemedi. Sahte reklam kimliğiyle "reklam çalışıyor" görüntüsü verilmedi.
- Hazır olan: `config/app_config.json` içindeki `ads` ayarları (aç/kapat, ana sayfa/liste banner'ı, yerel reklam sıklığı,
  test modu). GitHub'da düzenlenir, günlük çalıştırmada feed'e `app_config.ads` olarak çıkar; uygulama bunu okur (`AdsConfig`).
  Sunucu veya panel gerekmez.
- Varsayılan: **kapalı**, test modu açık.

## AdMob'u etkinleştirmek için gerekenler (sizin işlemleriniz)
1. **AdMob hesabı** açın (admob.google.com), ödeme ve vergi bilgilerini girin, hesap onayını bekleyin.
2. Uygulamayı AdMob'a ekleyin → **uygulama kimliği** (`ca-app-pub-XXXXXXXXXXXXXXXX~YYYYYYYYYY`).
3. Reklam birimleri: **Uyarlanabilir banner** (ana sayfa altı / liste altı) ve isteğe bağlı **Yerel gelişmiş** (katalog
   listesinde "Reklam" etiketiyle). Tam ekran (interstitial) **önerilmez**; sayfa geçişlerinde gösterilmemeli.
4. Play'de yayımlandıktan sonra AdMob'da uygulamayı mağaza girişine bağlayın; **app-ads.txt** dosyasını geliştirici web
  sitenize koyun (GitHub Pages adresi kullanılabilir).
5. **Gizlilik ve mesajlaşma** (UMP): AdMob → Privacy & messaging → AB/BK/İsviçre için GDPR mesajı oluşturun.
   Türkiye kullanıcıları için KVKK açık rıza/aydınlatma metnini gizlilik politikasına ekleyin.
6. Gizlilik politikasını ve Play "Veri güvenliği" formunu güncelleyin (reklam kimliği, yaklaşık konum, uygulama etkileşimi —
   AdMob'un beyan ettiği veri türleri), Play'de **"Reklam içeriyor: Evet"** işaretleyin.

## Geliştirici adımları (Android Studio / Gradle ile)
1. `app/build.gradle.kts` bağımlılıkları: `com.google.android.gms:play-services-ads` ve `com.google.android.ump:user-messaging-platform`
   (sürümleri Google'ın güncel sürüm notlarından alın).
2. `AndroidManifest.xml`: `<meta-data android:name="com.google.android.gms.ads.APPLICATION_ID" android:value="…"/>` — geliştirmede
   Google'ın **test** uygulama kimliği: `ca-app-pub-3940256099942544~3347511713`; test banner birimi:
   `ca-app-pub-3940256099942544/9214589741`.
3. Açılışta sıra: `UserMessagingPlatform.getConsentInformation(ctx).requestConsentInfoUpdate(...)` →
   `loadAndShowConsentFormIfRequired(...)` → yalnızca `canRequestAds()` true ise `MobileAds.initialize(...)`.
4. Yalnızca `feed.ads.enabled && feed.ads.homeBanner` ise banner kabını ekleyin; reklam yüklenemezse kap gizlenir,
   uygulama çalışmaya devam eder. Ayarlar'a "Gizlilik seçenekleri" (UMP `showPrivacyOptionsForm`) bağlantısı ekleyin.
5. Kurallar: kendi reklamınıza tıklamayın; test cihazınızı AdMob'da test cihazı olarak ekleyin; reklamı içerikle
   karıştırmayın ("Reklam" etiketi); çocuklara yönelik değil (hedef kitle 18+).

> Bu adımlar bu ortamda **derlenip test edilmedi** (SDK indirilemedi). Gelir garantisi yoktur; gelir trafik, doluluk oranı,
> ülke ve kullanıcı davranışına bağlıdır.

## Bildirim yapılandırması
- Duyurular günlük çalıştırmada otomatik oluşur (aynı katalog ikinci kez duyurulmaz). Elle tetiklemek için GitHub →
  Actions → Run workflow.
- Push (isteğe bağlı): `NTFY_URL`, `NTFY_TOPIC` (+ gerekirse `NTFY_TOKEN` gizli değişken). Ayrıntı: `docs/CANLI_KURULUM.md`.
- FCM için Firebase projesi + `google-services.json` + sunucu servis hesabı gerekir; 0.6.0'da yok.
