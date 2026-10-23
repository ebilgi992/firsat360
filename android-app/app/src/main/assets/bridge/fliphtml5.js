/* FlipHTML5 kitapları (CarrefourSA katalog.carrefoursa.com/books/…, Gratis online.fliphtml5.com/gratiscom/…).
 * Görüntüleyici yalnızca açık sayfaları DOM'a koyar; tüm sayfa listesi kitabın kendi yapılandırmasındadır
 * (window.fliphtml5_pages / bookConfig) — sayfa bunu zaten yüklemiştir. Liste yoksa DOM'daki sayfa görselleri.
 * Mağaza sayfasındaysa (ör. gratis.com/gratis-katalog) kitabın bağlantısına geçilir. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var isBook = !!(window.fliphtml5_pages || window.bookConfig || document.querySelector('meta[name="generator"][content*="fliphtml5"]'));
  if (!isBook) {
    var a = document.querySelector('a[href*="fliphtml5.com"],iframe[src*="fliphtml5.com"],a[href*="katalog.carrefoursa.com/books/"]');
    if (a) return F360.out({ type: 'navigate', url: F360.abs(a.getAttribute('href') || a.getAttribute('src')) });
    return F360.out({ type: 'pending' });
  }
  var fp = F360.flipPages();
  if (fp && fp.length) return F360.out({ type: 'images', urls: fp, end: true, total: fp.length, title: document.title });
  if (fp) return F360.out({ type: 'pending' });  // görüntüleyici ilk sayfayı yüklüyor
  var end = F360.scrollStep();
  var dom = F360.portraitImages(function (u) { return /\/files\/(large|page|mobile)\//.test(u); }, 300);
  return F360.out({ type: dom.length ? 'images' : 'pending', urls: dom, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
