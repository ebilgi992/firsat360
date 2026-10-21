/* A101 /afisler: Next.js sayfası. 1) Sayfanın kendi gömülü verisi (__NEXT_DATA__ / self.__next_f) içindeki a101 alan
 * adlı afiş görselleri — tarayıcı zaten yüklemiştir, ek istek yok. 2) Yoksa DOM'daki büyük dikey görseller. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var re = /https?:\\?\/\\?\/[a-z0-9.-]*a101\.com\.tr\\?\/[^"'\s<>]{0,400}?\.(?:jpe?g|png|webp)/gi, found = [], seen = {};
  var blobs = [];
  var nd = document.getElementById('__NEXT_DATA__'); if (nd) blobs.push(nd.textContent || '');
  try { (self.__next_f || []).forEach(function (x) { if (x && x[1]) blobs.push(String(x[1])); }); } catch (e) {}
  blobs.forEach(function (b) { var m; while ((m = re.exec(b))) { var u = m[0].replace(/\\\//g, '/');
    if (/afis|poster|katalog|brosur|insert/i.test(u) && !/logo|icon/i.test(u) && !seen[u]) { seen[u] = 1; found.push(u); } } });
  var end = F360.scrollStep();
  var dom = F360.portraitImages(function (u) { return /a101\.com\.tr/i.test(u) && !/logo|icon|\/kapida\//i.test(u); });
  dom.forEach(function (u) { if (!seen[u]) { seen[u] = 1; found.push(u); } });
  return F360.out({ type: found.length ? 'images' : 'pending', urls: found, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
