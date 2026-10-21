/* ŞOK kurumsal /tr/firsatlar: broşürler resmî PDF ("Haftanın Fırsatları", "Hafta Sonu Fırsatları") olarak yayımlanır.
 * PDF bağlantıları + yakınındaki başlık/tarih → yerel PDF okuyucu. */
(function () { try {
  if (F360.challenge()) return F360.out({ type: 'challenge' });
  var items = [], seen = {};
  Array.prototype.forEach.call(document.querySelectorAll('a[href*=".pdf"]'), function (a) {
    var u = F360.abs(a.getAttribute('href')); if (!/^https:\/\/kurumsal\.sokmarket\.com\.tr\//.test(u) || seen[u]) return;
    seen[u] = 1;
    var box = a.closest('div,li,article,section') || a.parentElement, txt = ((box && box.innerText) || a.innerText || '').replace(/\s+/g, ' ').trim();
    var d = txt.match(/\d{1,2}\s*-\s*\d{1,2}\s+[A-Za-zÇĞİÖŞÜçğıöşü]+(?:\s+20\d\d)?/);
    items.push({ url: u, title: (a.innerText || '').trim().slice(0, 80) || txt.slice(0, 60), dates: d ? d[0] : '' });
  });
  return F360.out(items.length ? { type: 'pdf', items: items } : { type: 'pending' });
} catch (e) { return JSON.stringify({ type: 'pending', error: String(e) }); } })()
