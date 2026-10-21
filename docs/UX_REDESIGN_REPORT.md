# UX yeniden düzenleme raporu — 1.2.0

**Doğrulama durumu:** cihaz/emülatör yok → ekran görüntüsü ve kullanılabilirlik testi **yapılmadı**. Aşağıdakiler kod
düzeyindeki kararlar ve testlerdir; görsel kalite gerçek cihazda kontrol edilmelidir.

## Bilgi mimarisi (ana sayfa, yukarıdan aşağı)
1. Arama (tam genişlik, tek dokunuş) · 2. **Market broşürleri rafı** (büyük zincirler, kullanıcı sırası, "Sırala") ·
3. Son güncelleme satırı · 4. Bugün başlayanlar · 5. Süresi dolmak üzere · 6. Günün İndirimleri · 7. Diğer kataloglar ·
8. Marketler ızgarası.

Kural: bir yayın (grup) ana sayfada **tek bir** yerde görünür — öncelik raf → bugün başlayan → bitmek üzere → diğer.
Kanıt: `onur.noGroupDup`, `onur.oneCardPerInsert`, `home.noDuplicates` (çekirdek testleri).

## Bu sürümdeki değişiklikler
| Sorun (kullanıcı geri bildirimi / kanıt) | Çözüm |
|---|---|
| Onur hem katalog listesinde hem "Süresi dolmak üzere"de (3 bölge × 2 insert = 6 kart) | Bölge kopyaları tek kart; kullanıcının bölgesi (Ayarlar → Bölge, varsayılan İstanbul); market ekranında bölge çipleri |
| File afişleri "katalog" gibi görünüyor | `poster` türü, kartta "N afiş" etiketi; "bugün başlayan/bitmek üzere" bölümlerine girmez |
| Tarihsiz katalog "bitmek üzere" sayılabilirdi | Tek durum kuralı `CatalogRules.state`; tarihsiz = UNKNOWN |
| Market ekranında süresi dolmuş kataloglar | Market ekranı ve "Tüm kataloglar" süresi dolanları ve bölge kopyalarını göstermez |

## Bilinen eksikler (sonraki sürüm)
- Gerçek cihazda yazı boyutu/kontrast ve 320 dp ekran kontrolü.
- Okuyucuda sayfa küçük resim şeridinin büyük kataloglarda (Migroskop 160 sayfa) performans ölçümü.
- Koyu tema: tema altyapısı var (`Theme.p.dark`); ekran bazında görsel kontrol yapılmadı.
