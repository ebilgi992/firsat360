# Debug imza anahtarı (yalnızca test sürümleri)

`firsat360-debug.jks` herkese açık parolalı (**android**) bir DEBUG anahtarıdır — Android'in standart `debug.keystore`
uygulamasıyla aynı mantık. Proje içinde tutulur ki her derleme aynı imzayı üretsin ve beta APK'lar birbirinin
**üzerine güncellenebilsin** (0.4.0'dan itibaren).

- Gizli bilgi DEĞİLDİR; Play Store / yayın imzası için ASLA kullanılmaz.
- Yayın (release) anahtarı ayrı oluşturulmalı ve depoya konmamalıdır: `DEBUG_KEYSTORE` yerine kendi
  anahtarınızı ortam değişkeniyle verin ve parolayı `.env`/CI gizli değişkeninde tutun.
- SHA-256 parmak izi: `keytool -list -v -keystore firsat360-debug.jks -storepass android`
