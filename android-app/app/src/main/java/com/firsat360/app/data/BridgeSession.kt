package com.firsat360.app.data

import android.content.Context
import android.net.Uri
import android.webkit.WebView
import com.firsat360.app.core.Json
import com.firsat360.app.screens.siteFamily

/**
 * İstemci katalog köprüsünün okuma motoru — görünür ekran (WebScreen) ve Wi‑Fi'de arka plan ön yüklemesi
 * (BridgePrefetch) aynı motoru kullanır.
 *
 * Akış: sayfa yüklenince assets/bridge/common.js + <profil>.js ~0,9 sn'de bir çalıştırılır (evaluateJavascript; JS
 * köprüsü yok). Sonuç: sayfa görselleri / PDF'ler / izinli katalog görüntüleyicisine geçiş / doğrulama sayfası.
 * Doğrulama sayfası OTOMATİK GEÇİLMEZ: görünür modda kullanıcı tamamlar, arka planda o market atlanır.
 */
class BridgeSession(
    private val ctx: Context,
    private val startUrl: String,
    private val profile: String,
    private val allowedHosts: List<String>,
    private val listener: Listener,
    /** Görünür mod: doğrulamada beklenir (kullanıcı tamamlar). Arka plan: doğrulamada hemen vazgeçilir. */
    private val waitOnChallenge: Boolean = true,
    private val maxMs: Long = 15_000L,
) {
    data class PdfItem(val url: String, val title: String, val dates: String)

    interface Listener {
        fun onProgress(pages: Int) {}
        fun onNavigating() {}
        fun onChallenge(first: Boolean) {}
        fun onImages(pages: List<String>)
        fun onPdf(items: List<PdfItem>)
        /** Süre doldu veya doğrulama (arka plan): bulunan en iyi sayfalar (boş olabilir). */
        fun onGiveUp(best: List<String>, reason: String)
    }

    var finished = false
        private set
    private var view: WebView? = null
    private var polling = false
    private var lastCount = -1
    private var best: List<String> = emptyList()
    private var navigations = 0
    private var challengeShown = false
    /** Oturum boyunca TEK süre sınırı (yalnızca kendi "navigate" adımımızda ve doğrulama sırasında uzar). */
    private var deadline = 0L
    /** Her yoklamanın kimliği: geri dönüşü kaybolan yoklama (JS yönlendirmesi) bekçi tarafından yenilenir. */
    private var generation = 0
    private val main = android.os.Handler(android.os.Looper.getMainLooper())

    /** Sayfa yüklendi (onPageFinished): okuma başlar ya da sürer. */
    fun onPageFinished(w: WebView) {
        if (finished) return
        view = w
        lastCount = -1
        if (deadline == 0L) deadline = System.currentTimeMillis() + maxMs
        if (!polling) { polling = true; schedule(w, 1200) }
    }

    fun cancel() { finished = true; polling = false; view = null; main.removeCallbacksAndMessages(null) }

    private fun schedule(w: WebView, delay: Long) { main.postDelayed({ poll(w) }, delay) }

    /** Kullanıcı "Sayfa sayfa aç" dedi: bulunanlarla bitir. */
    fun finishWithBest(): Boolean {
        if (finished || best.size < 2) return false
        finish(); listener.onImages(best); return true
    }

    private fun finish() { finished = true; polling = false; main.removeCallbacksAndMessages(null) }

    private fun poll(w: WebView) {
        if (finished || w !== view) { polling = false; return }
        val gen = ++generation
        try {
            w.evaluateJavascript(BridgeScripts.get(ctx, profile)) { raw -> if (gen == generation) onResult(w, raw) }
            // Bekçi: sonuç 5 sn'de gelmezse (sayfa yönlendirdi, geri dönüş kayboldu) yeniden yokla
            main.postDelayed({ if (!finished && gen == generation && w === view) poll(w) }, 5_000)
        } catch (e: Exception) { polling = false; finish(); listener.onGiveUp(best, "error") }
    }

    private fun onResult(w: WebView, raw: String?) {
        if (finished || w !== view) { polling = false; return }
        generation++  // bu yoklama tamamlandı; bekçi devre dışı
        val res = try {
            val inner = Json.parse(raw ?: "null") as? String
            if (inner == null) null else Json.parse(inner) as? Map<*, *>
        } catch (e: Exception) { null }
        when (res?.get("type")) {
            "challenge" -> {
                if (!waitOnChallenge) { finish(); listener.onGiveUp(best, "challenge"); return }
                listener.onChallenge(!challengeShown)
                challengeShown = true
                deadline = System.currentTimeMillis() + maxMs  // süre doğrulama bitince işlemeye başlar
                schedule(w, CHALLENGE_POLL_MS)
                return
            }
            "pdf" -> {
                val items = ((res["items"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it as? Map<*, *> }
                    .mapNotNull { m ->
                        val u = m["url"] as? String
                        if (u != null && u.startsWith("https://") && hostAllowed(u))
                            PdfItem(u, (m["title"] as? String).orEmpty().trim(), (m["dates"] as? String).orEmpty().trim()) else null
                    }.distinctBy { it.url }
                if (items.isNotEmpty()) { finish(); listener.onPdf(items); return }
            }
            "navigate" -> {
                val to = res["url"] as? String
                // Yalnızca marketin KENDİ sayfasından (başlangıç alanı) katalog görüntüleyicisine geçilir.
                val onStart = siteFamily(Uri.parse(w.url ?: startUrl).host) == siteFamily(Uri.parse(startUrl).host)
                if (to != null && to.startsWith("https://") && hostAllowed(to) && to != w.url && navigations < 2 && onStart) {
                    navigations++; lastCount = -1; polling = false
                    deadline = System.currentTimeMillis() + maxMs
                    listener.onNavigating()
                    w.loadUrl(to)  // onPageFinished → okuma sürer
                    return
                }
            }
        }
        val pages = ((res?.get("urls") as? List<*>) ?: emptyList<Any>()).mapNotNull { it as? String }
            .filter { it.startsWith("https://") && hostAllowed(it) }.distinct()
        if (pages.size > best.size) best = pages
        // Geçiş: sayfa sonuna ulaşıldı VE son iki ölçüm aynı (tembel görseller yüklendi) → eksik sayfa kalmaz.
        val atEnd = res?.get("end") == true
        if (pages.size >= 2 && pages.size == lastCount && atEnd) { finish(); listener.onImages(pages); return }
        lastCount = pages.size
        listener.onProgress(pages.size)
        if (System.currentTimeMillis() > deadline) {
            finish()
            if (best.size >= 2) listener.onImages(best) else listener.onGiveUp(best, "timeout")
            return
        }
        schedule(w, POLL_MS)
    }

    /** Görsel/PDF/gezinme hedefi izinli mi: feed'deki alan adı aileleri; yoksa başlangıç sayfasının ailesi. */
    fun hostAllowed(u: String): Boolean {
        val fam = siteFamily(Uri.parse(u).host) ?: return false
        val allowed = if (allowedHosts.isNotEmpty()) allowedHosts.mapNotNull { siteFamily(it) }
            else listOfNotNull(siteFamily(Uri.parse(startUrl).host))
        return fam in allowed
    }

    companion object {
        const val POLL_MS = 900L
        const val CHALLENGE_POLL_MS = 3_000L
    }
}
