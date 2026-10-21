# FIRSAT360 — İstemci Katalog Köprüsü (React Native)

Resmî broşür sayfası **kullanıcının cihazında** açılır → site'ye özel betik enjekte edilir → sonuç
`window.ReactNativeWebView.postMessage` ile gelir → WebView kapanır, sayfalar **yerel tam ekran galeride** (veya PDF
yerel okuyucuda) açılır. Bu 6 kaynak için sunucuda kazıma yapılmaz (`config/client_bridge.json`, `"client_rendered": true`).

| Market | Resmî sayfa | Profil | Sonuç |
|---|---|---|---|
| A101 | https://www.a101.com.tr/afisler | `a101` | Next.js gömülü verisi + DOM'daki afiş görselleri → galeri |
| ŞOK | https://kurumsal.sokmarket.com.tr/tr/firsatlar | `sok` | Resmî PDF'ler (başlık + tarih) → PDF okuyucu (birden çoksa seçim) |
| Migros | https://migroskop.migros.com.tr/migroskop | `migroskop` | migroskop-assets Cover/Ad sayfaları → galeri |
| Gratis | https://www.gratis.com/gratis-katalog | `fliphtml5` | Kitap bağlantısına geçer → `fliphtml5_pages` tam sayfa listesi → galeri |
| CarrefourSA | https://katalog.carrefoursa.com/books/mwis | `fliphtml5` | FlipHTML5 tam sayfa listesi → galeri |
| Rossmann | https://www.rossmann.com.tr/kataloglar | `generic` | Flipbook bağlantısı → geç; yoksa PDF; yoksa büyük dikey görseller |

## Dosyalar

- `src/CatalogBridge.tsx` — bileşen (`targetMarket` prop'u; yükleme katmanı; onMessage durum makinesi; yerel galeri ve PDF).
- `src/bridgeScripts.ts` — **otomatik üretilir** (`python scripts/gen_rn_bridge.py`). Kaynak: Android uygulamasının
  `android-app/app/src/main/assets/bridge/*.js` dosyaları — iki istemci aynı betikleri kullanır; `--check` ve
  `backend/tests/test_client_bridge_config.py` eşzamanlılığı denetler.
- `src/ExampleUsage.tsx` — feed'deki `client_bridge` alanıyla raf örneği.

## Kurulum

```bash
npm i react-native-webview react-native-pdf react-native-blob-util
# iOS: cd ios && pod install
```

```tsx
<CatalogBridge targetMarket="carrefoursa" marketName="CarrefourSA" onClose={close} />
```

## Akış (durum makinesi)

`loading` (katman görünür, WebView arkada yükleniyor) → `onLoadEnd` → betik 1,2 sn sonra, ardından 0,9 sn'de bir
enjekte edilir (en çok 16 kez):

| Mesaj | Davranış |
|---|---|
| `images` | Adresler https + izinli alan ailesi ile süzülür. Sayfa sonuna gelindi **ve** iki ölçüm aynıysa → `gallery` |
| `pdf` | Tek PDF → `pdf`; birden çok → `pdfChoice` |
| `navigate` | İzinli alandaysa en çok 2 kez o adrese geçilir (ör. Gratis → FlipHTML5 kitabı) |
| `challenge` | Okuma durur, **WebView kullanıcıya gösterilir**; kullanıcı tamamlarsa sayfa yenilenir ve okuma kendiliğinden sürer |
| süre doldu | ≥2 sayfa bulunduysa galeri; yoksa resmî sayfa gösterilir |

## Güvenlik ve sınırlar

- Doğrulama/CAPTCHA sayfaları **otomatik geçilmez**; kimlik bilgisi, çerez kopyalama, gizli anahtar veya özel API yoktur.
  Betik yalnızca sayfanın kullanıcıya zaten gösterdiği görsel/PDF adreslerini okur.
- `postMessage` köprüsüne sayfanın kendi betikleri de erişebilir: bu yüzden mesajın geldiği sayfa ve içindeki **her adres**
  izinli alan ailesiyle doğrulanır; üst çerçeve izinli alan dışına gidemez (dış bağlantılar sistem tarayıcısında açılır).
- `originWhitelist=['https://*']`, `mixedContentMode="never"`, `allowFileAccess={false}`, açılır pencere yok.

## Durum

FIRSAT360'ın yayımlanan uygulaması **yerel Kotlin**'dir; aynı akış orada `WebScreen` + `BridgeScripts` ile çalışır. Bu
paket React Native'e geçiş/ikinci istemci için başvuru uygulamasıdır: `tsc --noEmit` ile tür denetimi geçer; betikler
gerçek Chromium'da hem Android dönüş yolu hem de `postMessage` sarmalayıcısıyla test edilir. Gerçek cihazda/simülatörde
çalıştırma testi yapılmadı (bu ortamda RN projesi/emülatör yok).
