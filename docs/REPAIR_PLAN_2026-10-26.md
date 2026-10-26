# FIRSAT360 1.8 düzeltme ve doğrulama planı

İnceleme: 26 Ekim 2026, Türkiye saati. Başlangıç kodu: `87a8c293a058dece30988fa1ee5e5fa92ba246b5`.
Kanıt: [issue #1](https://github.com/ebilgi992/firsat360/issues/1), son güncelleme 25 Ekim 05:32:13 UTC;
[başarılı günlük iş](https://github.com/ebilgi992/firsat360/actions/runs/40165366121), Pages artifact 12588611367,
feed üretimi 25 Ekim 05:32:14 UTC. `veri` dalındaki çalışma günlüğü A101 ürününü belirlemek için okundu.
Bu tarihli yayın incelendi; telefondaki kurulum ve bildirim teslimi uzaktan doğrulanmış değildir.

## Öncelikler ve kabul ölçütleri

| Öncelik | Ürün / kontrol | Uygulama / yeniden kontrol | Başarı ölçütü |
|---|---|---|---|
| P0 | Android canlı veri | Boş `feed_url` varsayılanını gerçek Pages adresiyle değiştir; başarısız ağ isteğini başarılı kontrol gibi bekletme | Yeni APK'nın kaynaklarında HTTPS feed adresi bulunur; ağ açıkken son yayın tarihi güncellenir; çevrim dışıyken son kopya korunur |
| P0 | A101 veri güncelliği | `last_success_at=2026-10-13T05:12Z`; günlük işin başarı durumunu market verisinin başarı durumu sanma | Eski A101 ürünleri güncel sonuçlara girmez; A101 kaynak hatası görünür; son başarılı kaynak zamanı geriye dönük değiştirilmez |
| P0 | Bildirim teslimi | Sistem izni/kanal kapalıyken aday kataloğu tüketme; izin verildiğinde kontrolü tekrar planla | İzin kapalı → yeni katalog bekler; izin açık ve saat gelmiş → 1 bildirim; tekrar kontrolde kopya yok; kanal kapalı → gösterildi sayılmaz |
| P1 | Asistan / içilecek süt | Reyon eşleşmesini atla, ürün türünü doğrula; toz, reçel, kozmetik ve geçmiş tarihli ürünleri ele | “en ucuz süt” yalnız içilecek süt döndürür; bulunamazsa ilgisiz ürünle doldurmaz; kayıtlı veride paket fiyatına göre sıralar; litre fiyatı iddiası yok |
| P1 | Güncelleme | Açılış ve Ayarlar kontrolü; resmî depo sürümü, SHA-256, paket, sürüm kodu ve kurulu imzayla karşılaştırma | Eski/eşit sürüm önerilmez; yanlış hash/imza/paket reddedilir; doğru sürüm kullanıcı onayıyla kurulur; iptal normal kullanımını engellemez |
| P1 | Kalite raporu | Tek “kalan hata” yerine kaynak/veri hatası ve görseli eksik ürün sayısını ayrı yaz; her ürünün ID/URL/nedenini ekle | Başlangıçta 1 kaynak hatası + 5 görselsiz ürün ayrı görünür; kısmi ilerleme “çözüldü” sayılmaz; görsel sorunu varken issue kapanmaz |
| P2 | Web vitrini | Uygulamanın grafit/kehribar görünümü, canlı feed araması, market filtresi ve gerçek release APK seçenekleri | Mobil/masaüstü yerleşim; olmayan fiyat/sürüm gösterilmez; veri hatası açıklanır; günlük Pages paketine HTML/CSS/JS eklenir |

## Beş görsel sorunu ayrı ayrı

| Ürün | Kanıt / mevcut neden | Yapılacak kontrol | Kabul ölçütü |
|---|---|---|---|
| A101 **Peynes Tam Yağlı Taze Beyaz Peynir 1 kg** (`_p-12000073`) | Günlükte ürün adresi `https://www.a101.com.tr/kapida/sut-urunleri-kahvaltilik/peynes-tam-yagli-taze-beyaz-peynir-1-kg_p-12000073`; `page_empty`; 403. Aynı işte iki ürün isteği görülüyor. | Engelli kaynak için ürün sayfası doğrulamasını ve kalite ajanı tekrarını atla. Koruma normal erişime izin verince bir sonraki izinli okumada marka/ad/1 kg/resmî ana görsel eşleşmesini denetle. | 403 sırasında 0 ek ürün isteği; görsel boş ve hata görünür. Yalnız başarılı resmî doğrulama sonrası `verified`; afiş bu ürünün görseli sayılmaz. |
| TK Koop **Banvit Piliç Nugget 1000 g** (`tkkoop-f98eca4bca677c`) | `name_mismatch`; resmî sayfada ad/1000 g görünüyor | `/urun/banvit-pilic-nugget-1000-g`: ana ürün başlığının DOM seçicisini ve ana görselin bağlamını incele; ilgili ürün bloklarını dışla | Banvit adı + nugget + 1000 g aynı detay sayfasında eşleşir; resmî ürün CDN görseli kabul edilir; Doyfarm görseli kabul edilmez |
| TK Koop **Doyfarm Piliç Nugget 1 kg** (`tkkoop-6100f8ea35d4f9`) | `name_mismatch`; resmî sayfada ad/1 kg görünüyor | `/urun/doyfarm-pilic-nugget-1-kg`: Banvit'ten ayrı doğrula; 1 kg=1000 g birim eşdeğerliği korunur | Doyfarm marka/ad ve 1 kg eşleşmesi + kendi resmî ana görseli; Banvit ile çapraz eşleşme yok |
| TK Koop **Renax Bulaşık Makinesi Kapsülü 60'lı** (`tkkoop-6c7f2c1be60018`) | Listedeki ad kısaltılmış; `name_mismatch` | `/urun/renax-bulasik-makinesi-kapsulu-60li`: tam başlık ve 60'lı paketini oku; kısaltılmış liste adını rastgele genişletme | Tam Renax ürün adı/60'lı paket ve aynı sayfanın ana görseli; farklı paket reddedilir |
| TK Koop **Solo Ultra Bambu Özlü Kağıt Havlu 16'lı** (`tkkoop-0bd3a7eb9d6a7c`) | Listedeki ad kısaltılmış; `name_mismatch` | `/urun/solo-ultra-bambu-ozlu-kagit-havlu-16li`: tam ad ve 16'lı ambalajı doğrula | Solo/Bambu/kağıt havlu/16'lı eşleşir; tuvalet kâğıdı veya farklı paket görseli reddedilir |

TK Koop'ta h4/h5 başlıklarının da aday olması eklenmiştir; doğrulama eşiği gevşetilmemiştir.
Bu bir ayrıştırıcı düzeltmesidir; dört canlı görselin düzelmiş sayılması için CI'daki resmî HTML kanıtı,
regresyon testi ve sonraki canlı feed sonucu birlikte kontrol edilmelidir. `scripts/check_tk_images.py` dört
sayfayı kibar istemciyle okur, HTML ve ayrıştırma raporunu `tk-evidence` artifact'ına koyar; 403/429/robots engelinde durur.

## A101_Afis ve diğer depolar

- [codermert/A101_Afis](https://github.com/codermert/A101_Afis): incelemede `A101.json` yalnız
  `{"error":"Veriler alınamadı"}` içeriyordu. Ürün/fiyat kaynağı değildir; broşür bağlantısı keşif kaynağıdır.
  Entegrasyon hata/boş/72 saatten eski JSON'u ve resmî CDN dışı URL'yi reddeder. Güncel commit kampanya tarihi
  kabul edilmez. Sağlıklı broşür adayları `status.json/community_a101` altında resmî tarih doğrulaması bekler;
  resmî ürün doğrulaması veya A101 kaynak başarısı olarak yazılmaz. Kod kopyalanmadı; özel bir adaptör yazıldı.
- [mcaitjv/infdatabase](https://github.com/mcaitjv/infdatabase): mimari aday; README BİM'i TODO olarak tanımlıyor.
  Canlı çıktısı ve sürekliliği kanıtlanmadan üretim kaynağına eklenmedi.
- [yibudak/marketfiyati_mcp](https://github.com/yibudak/marketfiyati_mcp): README eğitim amaçlı ve ticari kullanım dışı
  olduğunu belirtiyor. Sağlık/lisans/erişim doğrulanmadan uygulamaya eklenmedi.
- Eski PHP A101/ŞOK botlarının README örnekleri çalışan güncel feed kanıtı değildir; çalışan kaynak diye eklenmedi.

Hiçbir Cloudflare/CAPTCHA çözümü, proxy rotasyonu veya kimlik taklidi eklenmedi. A101'in engeli GitHub üzerinden
resmî siteye istek attırarak aşılmıyor; yalnız bağımsız depoda zaten yayımlı çıktı inceleniyor.

## Sürüm ve cihaz kontrolü

Yeni sürüm: **1.8.0-beta (20)**. Standart paket `com.firsat360.app`; alternatif `com.firsat360.beta`.
Mevcut herkese açık beta imza anahtarı korunur; üretim güvenliği sunan özel imza anahtarı değildir.
Android sessiz güncellemeye genel olarak izin vermez: uygulama içinde indirir, sistem kurucusunu açar;
kullanıcı onayı gerekir ve uygulama süreç olarak yeniden başlatılabilir. Kullanıcıdan uygulamayı kapatıp
APK dosyasını elle bulması istenmez.

Zorunlu cihaz denemeleri: Android 8/13/16 açılış, sistem/kanal bildirim izni aç-kapat, zamanlama, Wi-Fi tercihi,
bozuk APK/hash, yanlış paket/imza, güncellemeyi iptal etme, normal güncelleme ve kullanıcı listelerinin korunması.
CI derleme/imza doğrulaması gerçek telefonda bildirim ve kurulum testinin yerine geçmez.
