/* FIRSAT360 katalog köprüsü — ortak yardımcılar. Tek kaynak: Android (Kotlin, evaluateJavascript) ve React Native
 * (react-native-webview, injectJavaScript) aynı dosyaları kullanır. Her çalıştırma DURUMSUZDUR ve tek bir JSON metni
 * döndürür; uygulama bunu ~0,9 sn'de bir yeniden çalıştırır (sayfa kaydırıldıkça yüklenen görseller birikir).
 *
 * Dönüş biçimleri:
 *   {type:"images", urls:[...], end:bool}      → broşür sayfa görselleri (belge/sayfa sırası)
 *   {type:"pdf", items:[{url,title,dates}]}    → resmî PDF(ler) → yerel PDF okuyucu
 *   {type:"navigate", url:"https://..."}       → resmî sayfanın işaret ettiği katalog görüntüleyicisine geç (izinli alanlar)
 *   {type:"challenge"}                         → doğrulama/bot sınaması: kullanıcıya gösterilir, OTOMATİK ÇÖZÜLMEZ
 *   {type:"pending"}                           → henüz bir şey yok, beklemeye devam
 */
var F360 = (function () {
  function abs(u) { try { return new URL(u, location.href).href; } catch (e) { return ''; } }
  function challenge() {
    var t = (document.title || '').toLowerCase();
    return t.indexOf('just a moment') >= 0 || t.indexOf('attention required') >= 0 ||
      !!document.querySelector('#challenge-form,#cf-challenge-running,.cf-turnstile,iframe[src*="challenges.cloudflare.com"],iframe[src*="recaptcha"],iframe[src*="hcaptcha"]');
  }
  function scrollStep() {
    window.scrollBy(0, window.innerHeight * 1.5);
    var se = document.scrollingElement || document.documentElement;
    return (window.scrollY + window.innerHeight) >= (se.scrollHeight - 20);
  }
  /** srcset/data-srcset içindeki en geniş aday; yoksa currentSrc/src/data-src. [adres, beyan edilen genişlik] */
  function bestSrc(img) {
    var s = img.getAttribute('srcset') || img.getAttribute('data-srcset') || '', b = null, bw = 0;
    s.split(',').forEach(function (p) { var q = p.trim().split(/\s+/); if (!q[0]) return;
      var w = parseInt(q[1] || '0', 10) || 1; if (w >= bw) { bw = w; b = q[0]; } });
    var u = b || img.currentSrc || img.src || img.getAttribute('data-src') || img.getAttribute('data-original') ||
      img.getAttribute('data-lazy') || '';
    return [abs(u), bw];
  }
  /** Yüklenmiş, ≥minW piksel ve dikey/kareye yakın (h ≥ 0,9·w) görseller: broşür/afiş sayfası; bant ve logo değil. */
  function portraitImages(filter, minW) {
    var out = [], seen = {};
    Array.prototype.forEach.call(document.images, function (img) {
      var w = img.naturalWidth, h = img.naturalHeight; if (!img.complete || !w || !h) return;
      var r = bestSrc(img), u = r[0], size = Math.max(w, r[1]);
      if (size < (minW || 500) || h < w * 0.9 || !u || u.indexOf('data:') === 0 || seen[u]) return;
      if (filter && !filter(u, img)) return;
      seen[u] = 1; out.push(u);
    });
    return out;
  }
  function out(o) { return JSON.stringify(o); }
  return { abs: abs, challenge: challenge, scrollStep: scrollStep, bestSrc: bestSrc, portraitImages: portraitImages, out: out };
})();
