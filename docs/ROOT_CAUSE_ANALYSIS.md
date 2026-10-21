# Kök neden analizi — broşür/katalog ve görsel sorunları (V1, 21.10.2026)

Kanıt: GitHub `veri` dalındaki canlı veritabanı (`firsat360.db`) ve çalıştırma günlükleri (`logs/firsat360.log`),
21.10.2026 04:49, 06:44, 15:43 ve 16:18 UTC çalıştırmaları.

Veri yolu: kaynak → bağlayıcı → ayrıştırma → doğrulama (imagematch) → SQLite → feed.json (GitHub Pages) → uygulama
önbelleği → ekran. Her bulgu, yolun hangi aşamasında olduğu ile birlikte:

| # | Belirti | Aşama | Kök neden | Düzeltme | Kanıt (önce → sonra) |
|---|---|---|---|---|---|
| 1 | BİM/ŞOK ürün görselleri yok | ürün sayfası okuma | Görsel yalnızca og:image/JSON-LD'de aranıyordu; bu sayfalarda yok | Mağazaya özel ana görsel kalıbı + tüm `<img>`/lazy/srcset/noscript taraması | BİM %17 → %100, ŞOK %0 → %100 |
| 2 | Doğru liste görseli kayboluyor | doğrulama → kayıt | Detay sayfası okunamayınca listedeki görsel siliniyordu | Liste/JSON görseli korunur | — |
| 3 | TK'da "doğrulanmış" ama yanlış görsel | doğrulama | og:image sitenin genel paylaşım klasörüydü (212 ürün) | Kalıp dışı og:image reddi + kalite ajanı | %91 sahte → %94 gerçek |
| 4 | ŞOK ürün adı "product-thumb" | ayrıştırma | Görsel alt metni ad sanılıyordu | Ad adayları + şablon ad reddi | 15 kayıt yayından alındı |
| 5 | Eski BİM/TK ürünleri yayında | kayıt | Kaynaktan kalkan kataloglar kontrol edilmiyordu | Kalkan/süresi dolan katalog ürünleri yayından alınır | 313 BİM + 169 TK |
| 6 | Rossmann "kataloğu" ürün fotoğrafları | broşür keşfi | `/media/catalog/product/` yolundaki "catalog" sözcüğü broşür sanıldı | Ürün görseli yolları hariç + kalite ajanı sayfaları temizler | 24 sahte sayfa kaldırıldı |
| 7 | Migroskop iki kez (biri boş) | feed | 13.10 anlık görüntüsünden kalan kayıt hiç güncellenmiyordu | Kaynakta 2 günden uzun görülmeyen ürünsüz katalog feed'e girmez | 1 mükerrer kaldırıldı |
| 8 | Ana sayfada aynı katalog iki bölümde | arayüz | 0.8'de eklenen broşür rafı ve "Kataloglar" satırı aynı listeden besleniyordu | `HomeCatalogs`: her katalog tek bölümde (raf → bugün başlayan → bitmek üzere → diğer) | Çekirdek testi `home.noDuplicates` |
| 9 | A101 broşürü yok | kaynak | A101 GitHub sunucularına 403 veriyor (bot engeli) | Engel aşılmaz; uygulama resmî afiş sayfasını telefonda açar ve büyük dikey görselleri "Sayfa sayfa aç" ile okuyucuya alır | Cihazda doğrulanacak |
| 10 | CarrefourSA, File broşürü yok | kaynak | robots.txt otomatik erişime izin vermiyor / okunamıyor | Uyulur; resmî sayfa uygulama içinde açılır | — |
| 11 | Günün İndirimleri değişmiyor | seçim | Aday havuzu küçük (16) + deterministik sıralama | Havuz genişletildi + günlük grup döngüsü | Ardışık günlerde ortak ürün 0 |

| 12 | Onur insert yok | kaynak keşfi | İnsertler kampanya (`campaign=`) sayfasında, göreli adreslerle | Ayrı Onur bağlayıcısı | 0 → 6 doğrulanmış PDF |
| 13 | File hiçbir şey | kaynak | Yanlış alan adı (filemarket.com.tr) + HEAD'de 302 döngüsü | file.com.tr + GET/ilk bayt doğrulaması | 0 → 5 resmî afiş |
| 14 | A101/CarrefourSA 403 | kaynak | Cloudflare bot sınaması (`cf-mitigated: challenge`) veri merkezi IP'lerine | Aşılmaz; cihazda resmî sayfadan toplama + 3 gün cihaz önbelleği | cihazda doğrulanacak |
| 15 | Hata sayfası "görsel" sayılabilir | doğrulama | Content-Type'a güveniliyordu | İlk baytlardan gerçek tür (JPEG/PNG/WEBP/PDF) | test: `test_html_error_page_is_not_saved_as_brochure_page` |

**Doğrulanamayanlar:** emülatör/cihaz bu ortamda yok (KVM yok); 9. madde ve tüm ekran akışları cihazda denenmedi.
