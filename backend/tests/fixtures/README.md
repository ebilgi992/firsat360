# Test fikstürleri — DEMO / SENTETİK

Bu klasördeki HTML dosyaları **sentetiktir**. Geliştirme sunucusunun ağ politikası mağaza sitelerine doğrudan
erişime izin vermediği için ham HTML indirilemedi. Fikstürler, 12.10.2026'da resmî sayfalarda gözlenen metin
yapısına (başlık sırası, fiyat biçimi, bağlantı kalıpları) göre elle yazıldı. CSS sınıf adları (BİM `div.product`,
`.subTitle`, `.title`, `.quantify`) **doğrulanmamış varsayımlardır**; bağlayıcılarda bu yüzden sınıf adına
bağlı olmayan yedek ayrıştırma yolu vardır.

`migros_search.json`, Migros JSON uç noktasında gözlenen anahtar yapısını (`successful`, `data.searchInfo`,
`storeProductInfos`, `name`, `shownPrice`, `regularPrice`, `prettyName`, `pageCount`) kullanır; değerler gerçek
anlık görüntüden alınmıştır, ancak dosya ham yanıtın birebir kopyası değildir.

Bu fikstürlerle geçen testler "ayrıştırıcı mantığı doğru" anlamına gelir, "canlı sitede çalışıyor" anlamına
gelmez. Canlı doğrulama için: `python -m firsat360 run` (mağaza sitelerine erişimi olan bir makinede).
