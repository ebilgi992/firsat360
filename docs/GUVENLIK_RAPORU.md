# Güvenlik denetimi — FIRSAT360 0.6.0 (14.10.2026)

> **0.6.1 notu:** Kullanıcı isteğiyle sunucu, HTTP API ve yönetim paneli **tamamen kaldırıldı** (bulgu 1–6, 10, 12, 13
> artık geçerli değil: saldırı yüzeyi yok). Sistem yalnızca GitHub Actions (çıkış bağlantıları) + GitHub Pages (statik,
> salt okunur feed) ile çalışır. Android bulguları (7–9) ve düzeltmeleri geçerlidir.

Kapsam: Android uygulaması (kaynak + derlenmiş APK/AAB), backend (FastAPI), yönetim paneli, günlük veri hattı,
bağımlılıklar. Yöntem: kaynak kod incelemesi (OWASP MASVS başlıklarına göre), **bandit** 1.9.4 statik analiz,
**pip-audit** 2.10.1 bağımlılık denetimi, APK/AAB içerik taraması (gizli anahtar desenleri, yerel kitaplık, manifest),
otomatik güvenlik testleri (`backend/tests/test_admin.py`), gerçek uvicorn sunucusuna HTTP denemeleri.
Yalnızca kendi test ortamımızda; üçüncü taraf sistemlere saldırı testi yapılmadı.
**Yapılamayanlar:** MobSF (Docker/indirme yok), Android Lint (Gradle/Google Maven yok), cihaz üzerinde dinamik analiz.

## Bulgular ve düzeltmeler
| # | Önem | Bulgu | Düzeltme | Yeniden test |
|---|---|---|---|---|
| 1 | **Yüksek** | Yönetim paneli HTTP Basic + form POST'ları → **CSRF** (tarayıcı kimlik bilgisini kendiliğinden gönderir; kötü bir site "tara/pasifleştir" isteği tetikleyebilirdi) | Oturum çerezi (SameSite=Strict) + oturuma bağlı CSRF belirteci + Origin kontrolü | ✅ `test_csrf_required_and_cross_origin_rejected` |
| 2 | **Yüksek** | Kaba kuvvet koruması yok, tek ortak parola, rol yok, 2FA yok, denetim kaydı yok | scrypt parola, kullanıcı kilidi (5→15 dk, 10→60 dk), IP sınırı (20/15 dk), roller (owner/editor/viewer), TOTP 2FA (tekrar kullanım engeli), denetim kaydı | ✅ `test_wrong_password_generic_and_lockout`, `test_ip_rate_limit`, `test_roles_enforced_server_side`, `test_totp_*` |
| 3 | Orta | Varsayılan/ortam değişkeninde açık parola ile yönetici | Varsayılan hesap yok; `admin-create` komutu (parola sorulur). Eski değişkenler yalnızca hiç hesap yokken bir kez kullanılır ve uyarı verir | ✅ `test_no_default_account_and_redirect` |
| 4 | Orta | `/v1/events` kimliksiz yazma ucu, sınırsız → veritabanı doldurma (DoS) | Uç kaldırıldı (uygulama artık olay göndermiyor) | ✅ `test_public_api_rate_limit_and_no_write_endpoints` |
| 5 | Orta | Herkese açık API'de hız sınırı yok; feed her istekte yeniden üretiliyor | IP başına dakikada 120 istek; feed 60 sn önbellek + gzip; `/docs` ve OpenAPI kapalı | ✅ aynı test + uvicorn denemesi (gzip, 404) |
| 6 | Orta | Panelde güvenlik başlıkları yok (clickjacking, önbellek) | CSP (betik yok), X-Frame-Options DENY, nosniff, no-referrer, no-store, HSTS | ✅ `test_login_session_cookie_flags_and_headers`, uvicorn başlık kontrolü |
| 7 | Orta | WebView: dosya/içerik erişimi açık (varsayılan), karışık içerik, `intent:`/`javascript:` şemaları | allowFile/ContentAccess=false, MIXED_CONTENT_NEVER_ALLOW, Safe Browsing, çoklu pencere/konum kapalı, http→https yükseltme, yalnızca https/tel/mailto/market şemaları | ⚠️ derlendi; cihazda denenmedi |
| 8 | Orta | `openExternal` her şemayı açıyordu (feed'den gelen bağlantı ile `intent:` riski) | Şema beyaz listesi | ⚠️ derlendi |
| 9 | Orta | JSON ayrıştırıcı: derin iç içe girdide yığın taşması, kesik girdide beklenmeyen hata türü | Derinlik sınırı 64, sınır kontrolleri, tüm hatalar `JsonException` | ✅ JVM `json.deep`, `json.truncated`, `json.badNumber` |
| 10 | Düşük | `serve` varsayılanı 0.0.0.0 (bandit B104) | Varsayılan 127.0.0.1; Docker'da yalnızca Caddy (HTTPS) erişir, port dışarı açılmaz | ✅ bandit temiz |
| 11 | Düşük | SHA-1 kullanımı (bandit B324) — güvenlik amaçlı değil (kimlik/sıralama özeti) | `usedforsecurity=False` | ✅ bandit temiz |
| 12 | Düşük | SQL: arama terimleri `LIKE` içinde kaçışsız (`%`/`_`) | `contains(..., autoescape=True)`, terim sayısı ve uzunluk sınırı | ✅ `test_sql_injection_like_input_is_harmless` |
| 13 | Bilgi | XSS: paneldeki tüm dinamik değerler `html.escape` | — | ✅ `test_xss_escaped_in_admin` |

## Değişmeyen/olumlu bulgular
- APK/AAB: API anahtarı, parola, token, özel anahtar **bulunmadı** (taranan desenler: Google API, AdMob, ntfy, GitHub,
  Slack, PEM özel anahtar). Yerel `.so` yok. `allowBackup=false`, `usesCleartextTraffic=false`, release'te debuggable yok.
- Dışa açık bileşen: yalnızca `MainActivity` (başlatıcı + `firsat360://` derin bağlantısı; gelen kimlikler yalnızca feed'de
  aranır). Servis/alıcılar `exported=false`; PendingIntent'ler `FLAG_IMMUTABLE`.
- İzinler: INTERNET, ACCESS_NETWORK_STATE, POST_NOTIFICATIONS, RECEIVE_BOOT_COMPLETED. Konum/depolama/kamera/kesin alarm yok.
- Günlükler: uygulamada ≤7 gün / ≤5 MB, satır başına ≤200 karakter, URL sorgu kısmı ve anahtar benzeri değerler
  maskelenir; sunucuda 5 MB / 7 gün, Authorization/token/parola/veritabanı parolası maskelenir (`test_redact_secrets`).
- Gizli bilgiler (ntfy anahtarı, webhook) yalnızca sunucu ortamında / GitHub Secrets'ta.
- `pip-audit`: requirements.txt'deki sürümlerde bilinen açık **yok** (14.10.2026, PyPI danışma veritabanı).

## Kalan riskler / öneriler
- Proje içindeki DEBUG imza anahtarı herkese açıktır (bilerek) → **yalnızca beta**; Play için kendi yükleme anahtarınızı
  kullanın (`docs/PLAY_YAYIN.md`).
- Feed bütünlüğü HTTPS'e dayanır (GitHub Pages/Caddy). İmzalı feed (ör. Ed25519) ileride eklenebilir.
- Market görselleri üçüncü taraf CDN'lerden yüklenir (gizlilik politikasında belirtildi).
- Panel için ek koruma: alan adını Cloudflare Access / IP kısıtı arkasına almak, owner hesabında 2FA zorunlu tutmak.
- Android bağımlılık denetimi: uygulama yalnızca Android SDK + Kotlin stdlib (ProGuard ile küçültülmüş) kullanır; üçüncü
  taraf Android kitaplığı yok.
