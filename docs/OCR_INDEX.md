# Broşür sayfalarında metin arama (OCR sayfa dizini)

Kullanıcı "süt" aradığında, yalnızca ürün kartlarında değil, **bu kelimenin geçtiği broşür sayfalarında** da sonuç
görebilsin diye günlük iş, yayımlanan broşür sayfa görsellerini OCR ile okuyup küçük bir kelime dizini üretir.
Yalnızca görselden oluşan broşürler (afiş, insert) de böylece aranabilir hale gelir.

## Ne yapar

1. Günlük işin en sonunda, `feed.json` yazıldıktan sonra çalışır (`firsat360/ocr_index.py`, `sync.run_daily` adım 6).
2. `feed.json`'daki yayındaki katalogların sayfa görsellerini alır. **İstemci köprüsündeki mağazalar hariç tutulur:**
   A101, ŞOK, Migros, Gratis, CarrefourSA, Rossmann ve `client_bridge` alanındaki her mağaza. Bu mağazaların
   broşürleri sunucuda hiç indirilmez.
3. Görseli mevcut kibar istemciyle (`PoliteClient`: robots.txt, istekler arası bekleme, SSRF koruması) indirir;
   sihirli baytlarla gerçekten JPEG/PNG/WebP olduğunu doğrular (HTML hata sayfaları elenir), 15 MB ve 40 megapiksel
   sınırı uygular.
4. RapidOCR (Apache-2.0, CPU, anahtar gerekmez) ile okur. Tercih edilen motor `rapidocr` 3.10 + PP-OCRv6 modelinin
   Türkçe tanıması (`tr`); modeller pakete gömülüdür, indirme yapılmaz. Yalnızca eski `rapidocr-onnxruntime` kuruluysa
   onun varsayılan (Çince+Latin) modeli kullanılır; Türkçe harfleri düz harfle okur ama katlama sayesinde arama yine
   eşleşir.
5. Kelimeleri uygulamanın arama anahtarıyla aynı biçimde normalleştirir: Türkçe küçük harf (I→ı, İ→i), sonra
   ç→c ğ→g ı→i ö→o ş→s ü→u (â, î, û ve diğer aksanlar da). En az 3 karakterli, en az bir harf içeren **tekil**
   kelimeler tutulur; salt sayılar ve `49tl` gibi fiyat parçaları atılır. Güveni 0,5'in altındaki satırlar atlanır.
6. Sonucu `site/page_index.json` olarak `feed.json`'un yanına yazar:

```json
{"generated_at": "2026-10-30T05:12:00+00:00",
 "pages": {"bim-1696": [[1, "haftanin firsatlari tam yagli sut kasar peyniri"], [2, "sampuan ..."]]}}
```

Uygulama adresi feed adresinden türetir (`.../feed.json` → `.../page_index.json`); `feed.json`'a alan eklenmedi.

## Önbellek ve süre sınırı

- Her sayfa **bir kez** okunur: sonuç görsel adresi + içerik özeti (SHA-256) ile `state/ocr-cache.json`'a yazılır
  (veri dalında saklanır). Aynı görsel başka adreste yayımlanırsa özet eşleşir, yeniden OCR yapılmaz.
- Bir çalıştırmada en fazla `OCR_MAX_PAGES` (150) yeni sayfa ve `OCR_TIME_BUDGET_S` (600 sn) süre. Kalan sayfalar
  sonraki çalıştırmada okunur. Önce her kataloğun ilk sayfaları, yeni kataloglar önde.
- İndirilemeyen/okunamayan sayfa 3 gün sonra yeniden denenir. 45 gündür feed'de görülmeyen kayıtlar silinir.
- Ölçülen hız (2 çekirdekli makine, A4 boyutunda 1240×1754 örnek sayfa): PP-OCRv6 `tr` sayfa başına ~3–4 sn,
  eski model ~1,5–2,5 sn. Model yüklemesi < 1 sn.

## Hata dayanıklılığı

- OCR paketi kurulu değilse (`requirements-ocr.txt` iş akışında `continue-on-error`), yeni sayfa okunmaz; dizin
  önbellekteki sonuçlarla yine yazılır.
- Hiçbir indirme, OCR veya dosya hatası günlük işi durdurmaz; özet raporun `page_index` alanına yazılır.
- `OCR_INDEX=0` adımı tamamen kapatır.

## Sınırlar (önemli)

- **Bu yalnızca bir metin dizinidir. OCR çıktısından fiyat, ürün adı veya kampanya bilgisi çıkarılmaz ve
  yayımlanmaz.** Arama sonucu kullanıcıyı resmî broşür sayfasının kendisine götürür; fiyat her zaman sayfada görülür.
- OCR hatalı okuyabilir: küçük/eğik/süslü yazılar kaçabilir, benzer harfler karışabilir (ör. `l`/`i`). Sonuç
  "bu sayfada geçiyor olabilir" anlamındadır.
- Yalnızca sayfa görseli olan kataloglar dizine girer; yalnızca PDF'i olan kataloglar (ör. Mopaş, Yunus insertleri)
  ve istemci köprüsündeki mağazaların broşürleri dizinde yoktur.
- Yeni broşürler, süre sınırı nedeniyle ilk çalıştırmada tamamen dizine girmeyebilir; birkaç çalıştırmada tamamlanır.
