/* Genel profil (A101 afişler, Rossmann kataloglar): önce sayfanın işaret ettiği flipbook/PDF, yoksa büyük dikey görseller. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var fb = document.querySelector('a[href*="fliphtml5.com"],a[href*="publitas.com"],a[href*="issuu.com"],a[href*="heyzine.com"],a[href*="flipsnack.com"],iframe[src*="fliphtml5.com"],iframe[src*="publitas.com"],iframe[src*="issuu.com"]');
  var pdfs = Array.prototype.slice.call(document.querySelectorAll('a[href$=".pdf"],a[href*=".pdf?"]')).map(function (a) {
    return { url: F360.abs(a.getAttribute('href')), title: (a.textContent || '').trim().slice(0, 80), dates: '' }; });
  var end = F360.scrollStep();
  var urls = F360.portraitImages(function (u) { return !/\/catalog\/product\/|\/products?\/|logo|icon|avatar/i.test(u); });
  if (urls.length >= 2) return F360.out({ type: 'images', urls: urls, end: end });
  if (pdfs.length) return F360.out({ type: 'pdf', items: pdfs });
  if (fb && end) return F360.out({ type: 'navigate', url: F360.abs(fb.getAttribute('href') || fb.getAttribute('src')) });
  return F360.out({ type: urls.length ? 'images' : 'pending', urls: urls, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
