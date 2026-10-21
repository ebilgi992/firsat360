/* Migroskop dijital (migroskop.migros.com.tr): sayfa görselleri migroskop-assets alanında; Cover (kapak) + Ad (sayfa).
 * <img> ve CSS arka plan görselleri birlikte taranır; /Product/ küçük resimleri sayfa değildir. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var end = F360.scrollStep(), urls = [], seen = {};
  var re = /https?:\/\/migroskop-assets\.migros\.com\.tr\/migrosdigitalinsert\/[^"')]+?\.(?:jpe?g|png|webp)(?:\?[^"')\s]*)?/gi;
  var html = document.documentElement.innerHTML, m;
  while ((m = re.exec(html))) { var u = m[0].replace(/&amp;/g, '&').replace(/ /g, '%20');
    if (!/\/(Cover|Ad)\//.test(u)) continue; var k = u.split('?')[0]; if (seen[k]) continue; seen[k] = 1; urls.push(u); }
  return F360.out({ type: urls.length ? 'images' : 'pending', urls: urls, end: end });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
