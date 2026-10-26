/* Genel profil (A101 afişler, Rossmann kataloglar): önce sayfanın işaret ettiği flipbook/PDF, yoksa büyük dikey görseller. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var fb = document.querySelector('a[href*="fliphtml5.com"],a[href*="publitas.com"],a[href*="issuu.com"],a[href*="heyzine.com"],a[href*="flipsnack.com"],iframe[src*="fliphtml5.com"],iframe[src*="publitas.com"],iframe[src*="issuu.com"]');
  var pdfs = Array.prototype.slice.call(document.querySelectorAll('a[href$=".pdf"],a[href*=".pdf?"]')).filter(function (a) {
    return F360.brochurePdf(a, F360.abs(a.getAttribute('href'))); }).map(function (a) {
    return { url: F360.abs(a.getAttribute('href')), title: (a.textContent || '').trim().slice(0, 80), dates: '' }; });
  // Zaten bir flipbook sağlayıcısındaysak başka yere geçilmez (yatay sayfa çiftleri dikey filtreye takılabilir;
  // sağlayıcının logosu/ana sayfası katalog değildir).
  // FlipHTML5 kitabının içindeysek tüm sayfa listesi kitabın kendi yapılandırmasından alınır (yalnızca 2-3 açık sayfa değil).
  var fp = F360.flipPages();
  if (fp && fp.length) return F360.out({ type: 'images', urls: fp, end: true, total: fp.length, title: document.title });
  if (fp) return F360.out({ type: 'pending' });
  // Kullanıcı belirli (eski) bir katalogun adresini verdiyse ve aynı sitede aynı biçimde DAHA YENİ numaralı katalog
  // bağlantısı varsa ona geçilir: …/kataloglar/193/0 → …/kataloglar/205/0 (en yüksek numara = en yeni yayın).
  var m = location.pathname.match(/^(.*\/(?:katalog|kataloglar|brosur|brosurler|aktuel|insert|firsatlar)[^\/]*\/)(\d+)/i);
  if (m && !window.__f360newest) {
    var cur = parseInt(m[2], 10), top = null, topN = cur;
    Array.prototype.forEach.call(document.querySelectorAll('a[href]'), function (a) {
      var u; try { u = new URL(a.getAttribute('href'), location.href); } catch (e) { return; }
      if (u.host !== location.host || u.pathname.indexOf(m[1]) !== 0) return;
      var n = parseInt(u.pathname.slice(m[1].length), 10); if (n > topN) { topN = n; top = u.href; } });
    if (document.readyState === 'complete') window.__f360newest = 1;
    if (top) return F360.out({ type: 'navigate', url: top });
  }
  var onVendor = /(^|\.)(fliphtml5|publitas|issuu|heyzine|flipsnack)\.com$/i.test(location.hostname);
  if (onVendor) fb = null;
  var end = F360.scrollStep();
  var urls = F360.portraitImages(function (u) { return !/\/catalog\/product\/|\/products?\/|logo|icon|avatar/i.test(u); });
  if (urls.length >= 1) return F360.out({ type: 'images', urls: urls, end: end });
  if (pdfs.length && end) return F360.out({ type: 'pdf', items: pdfs });  // önce görseller yüklensin
  if (fb && end) return F360.out({ type: 'navigate', url: F360.abs(fb.getAttribute('href') || fb.getAttribute('src')) });
  return F360.out({ type: urls.length ? 'images' : 'pending', urls: urls, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
