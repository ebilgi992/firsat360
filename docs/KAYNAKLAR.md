# Kaynak envanteri — Beta 0.4.0 (10 market)

## 0.4.0 eklemeleri (13.10.2026, ~17:30–20:35 Europe/Istanbul, WebFetch)
| Market | Resmî kaynak | robots.txt (13.10) | Alınan gerçek veri | Görsel | Otomatik bağlayıcı |
|---|---|---|---|---|---|
| **Tarım Kredi Kooperatif** | tkkoop.com.tr/haftalik-kampanyalar/kat-kat-firsat | `Disallow:` boş — kısıtlama yok | "Kat Kat Fırsat 13–19 Ekim": **65 ürün + fiyat** (önceki fiyat yok → indirim oranı yok). Ürün sayfası notu: "mağaza fiyatı esastır" | 61 ürün sayfasından resmî fotoğraf; 4 sayfada fotoğraf yok (yok.png) | Yok (anlık görüntü) |
| **Hakmar** | hakmar.com.tr/indirim/ | yalnızca /wp-admin/ yasak | 2 broşür: "08-19 Ekim 2026 Genel Insert" (8 sayfa), "Kırtasiye 2026" (21 Ağustos–31 Ekim, 8 sayfa). Ürün/fiyat ayrıştırılmadı (görsel broşür) | Resmî sayfa görselleri (wp-content/uploads) | Yok |
| **Watsons** | katalog.watsons.com.tr/ekim-katalog-2026 (Publitas) | www: hesap/sepet/arama/filtre ve bazı kampanya yolları yasak; katalog alan adının robots.txt'i okunamadı | "Ekim Katalog 2026" kapak + resmî bağlantı; tarih sayfada yok | Kapak (Publitas CDN) | Yok — yalnızca bağlantı |
| **Metro** | brosurler.metro-tr.com/kozmetik-deterjan (Publitas) | www: /sonuclar, /sonuc-yok yasak; broşür alan adı okunamadı | "Temizlik / Kişisel bakım" 1–31 Ekim, kapak + bağlantı | Kapak | Yok — yalnızca bağlantı |
| **Bizim Toptan** | bizimtoptan.com.tr/pages/katalog | sepet/hesap/arama/parametreler yasak; /pages/katalog serbest | Haftalık Katalog, Geleneksel Kanal Fırsat Bülteni, HORECA Kataloğu (heyzine/aflip flipbook) — tarih yok | 3 kapak (mncdn) | Yok — yalnızca bağlantı |
| **Gratis** | gratis.com/gratis-katalog | /search, /cart, /checkout, filtreler yasak; /gratis-katalog serbest | "Gratis Ekim Kataloğu" (fliphtml5) — tarih yok | Kapak (retter.io) | Yok — yalnızca bağlantı |

**Ürün fotoğrafları (tüm marketler):** her ürünün resmî detay sayfası tek tek açıldı (BİM 344, A101 39, ŞOK 49,
Tarım Kredi 65 = 497 sayfa) ve sayfadaki ana ürün görselinin resmî CDN adresi `data/snapshots/2026-10-13/raw/
product_images.tsv` dosyasına sayfanın yazdığı ad/açıklama/fiyatla kaydedildi. Görsel dosyası **kopyalanmadı**;
uygulama görseli doğrudan mağazanın CDN'inden yükler. Eşleşme `imagematch.py` ile otomatik doğrulanır.
- A101'in görsel CDN'i (cdn2.a101.com.tr) robots.txt ile tarayıcı botlarına kapalı; FIRSAT360 sunucusu görsel
  dosyalarını **indirmez/taramaz**, yalnızca ürün sayfasında yayımlanan adresi saklar. Görseli kullanıcının telefonu,
  sitedeki gibi doğrudan gösterir (hotlink). Yayın öncesi bu kullanım için mağazalardan yazılı izin alınmalıdır.
- Resmî kaynakta fotoğrafı olmayan 6 ürün için üçüncü taraf satıcı/pazaryeri görselleri **kullanılmadı** (izin yok,
  doğru ürün olduğu garanti edilemez). Teksüt'ün resmî sitesi robots.txt ile kapalı; bolca.com.tr farklı bir şirkete
  ait (ürün üreticisi Pek Dondurulmuş Gıda) → kullanılmadı.

---

# Önceki envanter (0.2.0, 4 mağaza)

Kontrol tarihleri: **12 Ekim 2026** (~12:05–12:12) ve **13 Ekim 2026** (~08:12–08:22), Europe/Istanbul.
Veri iki gün de WebFetch aracıyla resmî sayfalardan okundu. Geliştirme sunucusunun ağ politikası mağaza
sitelerine doğrudan bağlantıya izin vermediği için **otomatik bağlayıcılar canlı sitede çalıştırılamadı**
(13.10'da yönetim panelinden denendi: `robots.txt okunamadı … ProxyError` hatası kaydedildi, diğer mağazalar etkilenmedi).

| Mağaza | Resmî kaynak | Veri edinme yöntemi | Resmî API / feed | robots.txt | Alınan gerçek veri (13.10) | Görsel | Durum |
|---|---|---|---|---|---|---|---|
| **BİM** | bim.com.tr/categories/100/aktuel-urunler.aspx (`?Bim_AktuelTarihKey=`) | Resmî aktüel sayfası, sunucu tarafı HTML; sekme başına bir istek | Bulunamadı | **Okunamadı** (iki gün de erişim hatası). Bağlayıcı her çalıştırmada kontrol eder; 5xx/erişilemezse çalışmaz (RFC 9309) | 5 sekme, **347 ürün**: "10-16 Ekim" İndirim (88), "13 Ekim Salı" (89), "14 Ekim Çarşamba" (13), **"16 Ekim Cuma" (75, yeni)**, **"18 Ekim Pazar" (82, yeni)**. Tüm ürünlerde resmî detay bağlantısı. Sekmelerde "Daha Fazla Ürün Göster" var → ilk yüklenen ürünler | Kopyalanmadı | Gerçek veri ✓ · Canlı bağlayıcı yazıldı, canlı test edilemedi |
| **A101** | a101.com.tr/aldin-aldin | Resmî kampanya sayfası (Next.js); önce gömülü JSON, sonra HTML | Bulunamadı | `/arama?`, `opf=` yasak; `/aldin-aldin` serbest | "Aldın Aldın" — **39 ürün**; 12.10'a göre **2 ürün çıktı, 2 ürün girdi** (veri hattı çıkanları yayından kaldırdı). Kampanya tarihi sayfada yok → **bilinmiyor** | Kopyalanmadı | Gerçek veri ✓ · Canlı test edilemedi |
| **ŞOK** | sokmarket.com.tr/indirimli-urunler-cms-pl1 ("Haftanın Fırsatları") + /kampanyalar | İndirimli ürünler listesi (`?page=N`) + kampanya sayfaları (HTML) | Bulunamadı | Yalnızca `/arama` yasak | **Yeni:** indirimli ürünler listesinin ilk 2 sayfası — **40 ürün**, güncel + önceki fiyat (sayfada 292 ürün). 12.10'dan 10 kampanya ürünü (son görülme 12.10). 55 kampanya listelendi; 2 resmî kampanya bağlantısı | Kopyalanmadı | Gerçek veri ✓ |
| **Migros** | migros.com.tr/migroskop-urunleri-dt-3 | Sitenin kendi ön yüzünün kullandığı JSON (`/rest/search/screens/...`) | **Belgelenmiş resmî API değil**; kimlik doğrulamasız | `/arama`, `espv` yasak; `/rest` yasak değil | Migroskop ilk sayfa — **30 ürün** (toplam 1.819); 13.10 değerleri 12.10 ile aynı → her ürün için 2 günlük fiyat gözlemi. `?sayfa=2` ve `?page=2` aynı farklı ürünleri döndürdü | Kopyalanmadı | Gerçek veri ✓ · Yazılı izin önerilir |

**Uygulamadaki toplam (13.10.2026):** 4 mağaza, **466 yayında ürün**, 15 katalog/kampanya kaydı, 0 reddedilen kayıt,
257 üründe 2 günlük fiyat gözlemi.

## Kullanım koşulları
Dört mağazanın da kullanım koşulları metni **okunmadı**. Otomatik toplama, fiyat verisinin yeniden yayımlanması ve
ticari kullanım hakkında hüküm olup olmadığı kontrol edilmeden uygulama kamuya açık yayınlanmamalıdır. Önerilen:
her mağazaya bilgilendirme/izin e-postası; Migros için özellikle yazılı izin.

## Veri güvenilirliği notları
- Anlık görüntü fiyatları araç aracılığıyla (sayfa metninden) aktarılmıştır: `verification = tek_okuma_arac_aracili`.
  Testler bilinen fiyatları ve sayıları doğrular; sayfayla birebir karşılaştırma için otomatik bağlayıcının canlı
  çalıştırılması gerekir.
- "Önceki fiyat" her zaman **mağaza beyanıdır**; 1 Ağustos 2026'dan geçerli 10 günlük en düşük fiyat kuralına
  uygunluğu FIRSAT360 tarafından doğrulanmamıştır. Uygulama bunu "beyan" etiketiyle gösterir.
- ŞOK indirimli listesinde her kartta iki fiyat var; sayfa açıkça "İndirimli Ürünler" olduğundan düşük fiyat
  güncel, yüksek fiyat beyan edilen önceki fiyat kabul edildi. Canlı bağlayıcı üstü çizili işaret varsa onu kullanır;
  üç ve daha fazla fiyatlı kartı reddeder.
- Stok, şube ve şehir geçerliliği **bilinmiyor** olarak saklanır (`stock_status=unknown`, `scope=unverified`).
- A101 `/kapida/` ürünleri ve ŞOK indirimli liste online kanal fiyatıdır; mağaza rafıyla aynı olmayabilir.
- BİM sekme anlamı: "10-16 Ekim" sayfadaki **İndirim** sütunundadır (haftalık), gün etiketli sekmeler **Aktüel**
  sütunundadır. Aktüel günlerinin bitiş tarihi sayfada yok → "stoklarla sınırlı", bitiş bilinmiyor.
- Kategoriler: A101'de mağazanın kendi URL kategorisi; diğerlerinde anahtar kelime kuralıyla otomatik sınıflandırma
  (13.10 akışında "Diğer" kategorisinde kalan ürün: 0).

## Ürün kimliği ve fiyat geçmişi
Ürün kimliği = mağaza + katalog + marka + ad + ambalaj ölçüsü (ör. "3x140g"). Açıklama metni kimliğe yalnızca ölçü
olarak girer; böylece aynı ürün iki günde biraz farklı yazıldığında fiyat geçmişi kopmaz. Aynı ad/ölçüde ama farklı
açıklamalı iki ürün (ör. iki farklı kokulu oda kokusu yedeği) ayrı tutulur. Fiyat gözlemi günde en fazla bir kez
(fiyat değişirse ek kayıt) yazılır.
