# Katalog kurtarma raporu — 1.4.0 (21.10.2026)

## 1.4.0 — İstemci Katalog Köprüsü
Sunucu tarafı broşür kazıma bu 6 kaynak için tamamen kaldırıldı; broşür kullanıcının cihazında, resmî sayfadan okunur.

| Market | Resmî sayfa | Bulgu (21.10.2026) | Profil → sonuç | Durum |
|---|---|---|---|---|
| A101 | /afisler | Next.js; afişler gömülü veride (`__NEXT_DATA__`/`self.__next_f`, kaçışlı `\/`) | `a101` → galeri | Chromium PASS · cihaz BLOCKED |
| ŞOK | kurumsal /tr/firsatlar | Resmî PDF'ler (ör. `…21-27ekimhaftaninfirsatlari.pdf`, `…24-27haftasonufirsatlari.pdf`) | `sok` → PDF okuyucu, tarih TrDates ile | Chromium PASS · cihaz BLOCKED |
| Migros | migroskop.migros.com.tr | `migroskop-assets…/migrosdigitalinsert/…/(Cover\|Ad)/` | `migroskop` → galeri | Chromium PASS · cihaz BLOCKED |
| Gratis | /gratis-katalog | Kitap online.fliphtml5.com'da | `fliphtml5` → navigate → galeri | Chromium PASS · cihaz BLOCKED |
| CarrefourSA | katalog.carrefoursa.com/books/mwis | FlipHTML5 ("16 Ekim – 1 Kasım"); sayfa listesi `fliphtml5_pages` | `fliphtml5` → galeri | Chromium PASS · cihaz BLOCKED |
| Rossmann | /kataloglar | Flipbook/PDF/büyük görsel | `generic` | Chromium PASS · cihaz BLOCKED |

Doğrulama sayfası çıkarsa betik `challenge` döndürür; uygulama sayfayı kullanıcıya gösterir, otomatik çözmez.

---

# Katalog kurtarma raporu — 1.3.0 (21.10.2026)

## 1.3.0
| Market | Kaynak | Bulgu | Sonuç |
|---|---|---|---|
| Migros | money.com.tr/mc/kataloglarimiz/* | Her yayın `<a\|button class="…_df_button" source="PDF" mcdate="tarih">`; tarihlerde HTML varlıkları (`Eyl&#xFC;l`) | 5M Migroskop 284 (8–21 Ekim), MacroStyle (1–31 Ekim) — PDF imzası çalıştırmada doğrulanır |
| ŞOK | sokmarket.com.tr/kampanyalar | Kart yapısı değişti: görsel, başlık, "Kampanya Detayı" bağlantısı ayrı öğelerde | 59 resmî kampanya afişi (önceden 0) |
| A101, CarrefourSA | resmî sayfa, cihazda | Sunucuda Cloudflare sınaması | Cihazda otomatik toplama + yerel okuyucuya geçiş; betik gerçek Chromium testinde PASS, cihazda BLOCKED (cihaz yok) |

---

# Katalog kurtarma raporu — 1.2.0 (21.10.2026)

## 1.2.0 yeniden doğrulama (17:15 UTC canlı çalıştırma)
- A101: 403 devam ediyor (Cloudflare, `cf-mitigated: challenge`). Alternatifler: site haritasında afiş/katalog adresi yok
  (16 alt harita: kategori/kampanya/ürün); görsel CDN'i robots ile kapalı; resmî Instagram yalnızca anahtarlı resmî API ile
  okunabilir (kullanıcı anahtar eklemiyor). → Sunucuda **BLOCKED**, cihaz yolu var.
- CarrefourSA: robots.txt dahil 403. Resmî web sitesinde broşür/katalog yayını bulunamadı; Cimri'deki CarrefourSA broşürleri
  kaynak göstermiyor (izin bilinmiyor → kullanılmadı). → **BLOCKED**.
- Onur: 6 PDF, bölge kopyaları artık `region` + `group` ile tek kartta. File: afişler `poster` türünde.
- Tekrarlanan engel için kontrollü geri çekilme: son 3 deneme engelse kaynak 20 saat denenmez.

---

# Katalog kurtarma raporu — 1.1.0 (21.10.2026)

Kanıt: GitHub Actions "Şimdi dene" çalıştırmaları 39640218389 ve 39641952548 (yalnızca A101, CarrefourSA, File, Onur),
`kaynak-teshis` iş akışıyla ham yanıt kaydı (`teshis` dalı), canlı veritabanı (`veri` dalı).

| Market | İstek | Yanıt | Engelleyen / neden | Kök neden | Sonuç |
|---|---|---|---|---|---|
| **Onur Market** | `insertlerimiz?region=istanbul` | 200 HTML | — | İnsertler bölge sayfasında değil, `&campaign=<bölge>-genel-insert` kampanya sayfasında; adresler `<base href>` ile göreli (`download/944_….pdf`, `pics/inserts/944_….jpg`) | ✅ **6 resmî PDF** (İstanbul, Bursa, Trakya × "Çok Al Az Öde 13–26 Ekim" + "Taze Ürünlerde İndirimler 15–21 Ekim"); her PDF ilk baytlarından (`%PDF`) doğrulandı; "Geçmiş İnsert" bölümü alınmaz |
| **File Market** | `www.filemarket.com.tr` | robots.txt okunamadı | — | **Yanlış alan adı.** Resmî site `file.com.tr`. Broşür sayfası yok; ana sayfada resmî ürün afişleri (`uploads/file/banner/…AFIS…`). Ayrıca sunucu HEAD isteğini sonsuz 302 döngüsüne sokuyor (GET 200 image/JPEG) | ✅ **5 resmî afiş** (JPEG olarak doğrulandı). Doğrulama HEAD yerine GET + ilk bayt kontrolüne çevrildi |
| **A101** | `/aldin-aldin`, `/afisler` | 403 text/html | **Cloudflare** (`cf-mitigated: challenge`) | robots.txt bu sayfalara izin veriyor; engel veri merkezi IP'lerine uygulanan bot sınaması (CAPTCHA türü). Görsel CDN'i (cdn2.a101.com.tr) robots ile botlara kapalı | ⚠️ Sunucuda toplanamaz (sınama aşılmaz). **Cihazda:** resmî afiş sayfası uygulama içinde açılır, büyük dikey görseller toplanır → "Sayfa sayfa aç" ile uygulama içi okuyucu; sayfalar cihazda 3 gün saklanır, sonraki açılış doğrudan okuyucu |
| **CarrefourSA** | `/robots.txt`, `/` | 403 text/html | **Cloudflare** | robots.txt'nin kendisi bile 403: tüm site veri merkezi IP'lerini sınıyor. Resmî web sitesinde ayrı broşür sayfası bulunamadı (Cimri'deki broşürler kaynak göstermiyor) | ⚠️ Sunucuda toplanamaz. Cihazda resmî site açılır; broşür görseli bulunursa aynı cihaz içi okuyucu |

## Korunan çalışan kaynaklar (bu çalıştırmalarda dokunulmadı)
BİM (aktüel + afiş sayfaları), Migroskop dijital (160 sayfa), ŞOK (ürün listesi), Hakmar, Metro, Watsons — değişiklik yok.

## Tekrar engelleme önlemleri
- 403/429'da aynı kaynak yeniden denenmez (kalite ajanı engel mesajını tanır); tek çalıştırmada en fazla 1 deneme.
- Yönlendirme sınırı 5 (önceden 20): File'daki döngü sunucuya 200 istek atmıştı.
- Hata yanıtına engelleyen (Cloudflare/Akamai/Imperva/CloudFront), içerik türü ve yönlendirme zinciri yazılır.
