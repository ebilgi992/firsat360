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
  var base = location.href.split('#')[0].split('?')[0]; if (!/\/$/.test(base)) base = base.replace(/\/[^\/]*\.[a-z]+$/i, '/') ; if (!/\/$/.test(base)) base += '/';
  var pages = window.fliphtml5_pages, urls = [];
  if (pages && pages.length) {
    pages.forEach(function (p) { var n = p && (Array.isArray(p.n) ? p.n[0] : p.n); if (!n) return;
      n = String(n).replace(/^\.\.\//, '').replace(/^\.\//, '');
      if (!/^files\//.test(n) && !/^https?:/.test(n)) n = 'files/large/' + n;
      urls.push(/^https?:/.test(n) ? n : base + n); });
    return F360.out({ type: 'images', urls: urls, end: true, total: pages.length });
  }
  var end = F360.scrollStep();
  var dom = F360.portraitImages(function (u) { return /\/files\/(large|page|mobile)\//.test(u); }, 300);
  return F360.out({ type: dom.length ? 'images' : 'pending', urls: dom, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
