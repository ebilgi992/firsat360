package com.firsat360.app.ui

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import com.firsat360.app.data.ImageLoader

/**
 * Market logosu: önce marka renginde harf rozeti çizilir; marketin KENDİ sitesindeki uygulama simgesi
 * (apple-touch-icon, ≥ 96 px) bulunursa onunla değiştirilir. Görsel kopyalanmaz/paketlenmez; resmî adresinden
 * yüklenip önbelleğe alınır. Başarısız/küçük simgeler bir sonraki açılışa kadar tekrar denenmez.
 */
object StoreLogos {
    private val urls = java.util.concurrent.ConcurrentHashMap<String, String>()
    private val failed = java.util.Collections.newSetFromMap(java.util.concurrent.ConcurrentHashMap<String, Boolean>())
    private var prefs: android.content.SharedPreferences? = null
    private const val RETRY_MS = 3L * 24 * 60 * 60 * 1000
    @Volatile private var running = false

    /**
     * Logolar marketin KENDİ ana sayfasındaki simge bildirimlerinden bulunur (apple-touch-icon, büyük "icon",
     * web uygulaması manifest'i). Sonuç cihazda saklanır; bulunamayan site 3 gün sonra yeniden denenir.
     */
    fun register(stores: List<com.firsat360.app.core.Store>, ctx: Context? = null) {
        if (ctx != null && prefs == null) prefs = ctx.applicationContext.getSharedPreferences("store_logos", Context.MODE_PRIVATE)
        val p = prefs
        val todo = ArrayList<Pair<String, String>>()
        for (s in stores) {
            val host = try { android.net.Uri.parse(s.officialUrl).host } catch (e: Exception) { null } ?: continue
            val known = p?.getString(s.id, null)
            if (known != null && known.startsWith("https://")) urls[s.id] = known
            else if (System.currentTimeMillis() - (p?.getLong("${s.id}#t", 0L) ?: 0L) > RETRY_MS) todo += s.id to host
        }
        if (todo.isEmpty() || running || p == null) return
        running = true
        Thread {
            try {
                for ((id, host) in todo) {
                    val u = try { discover(host) } catch (e: Exception) { null }
                    p.edit().putString(id, u ?: "").putLong("$id#t", System.currentTimeMillis()).apply()
                    if (u != null) urls[id] = u
                }
            } finally { running = false }
        }.start()
    }

    fun url(id: String): String? = urls[id]?.takeIf { it !in failed }
    fun fail(u: String) { failed += u }

    private fun get(url: String, max: Int): String? {
        var c: java.net.HttpURLConnection? = null
        return try {
            c = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
                connectTimeout = 8000; readTimeout = 10000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android) FIRSAT360/${com.firsat360.app.BuildInfo.VERSION}")
            }
            if (c.responseCode != 200) return null
            val buf = ByteArray(max); var n = 0
            c.inputStream.use { while (n < max) { val r = it.read(buf, n, max - n); if (r < 0) break; n += r } }
            String(buf, 0, n, Charsets.UTF_8)
        } catch (e: Exception) { null } finally { c?.disconnect() }
    }

    private fun attr(tag: String, name: String): String? =
        Regex("""\b$name\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(tag)?.groupValues?.get(1)

    private fun size(s: String?): Int = s?.let { Regex("(\\d+)x\\d+").findAll(it).maxOfOrNull { m -> m.groupValues[1].toInt() } } ?: 0

    internal fun discover(host: String): String? {
        val base = "https://$host/"
        val html = get(base, 400_000) ?: ""
        val cands = ArrayList<Pair<Int, String>>()  // (puan, adres)
        fun abs(h: String) = try { java.net.URL(java.net.URL(base), h.replace("&amp;", "&")).toString() } catch (e: Exception) { null }
        for (m in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html)) {
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            val href = attr(tag, "href") ?: continue
            if (href.endsWith(".svg", true) || href.startsWith("data:")) continue
            val sz = size(attr(tag, "sizes"))
            when {
                "apple-touch-icon" in rel -> abs(href)?.let { cands += (if (sz > 0) sz else 180) + 40 to it }
                rel == "icon" || rel == "shortcut icon" -> if (sz >= 96) abs(href)?.let { cands += sz to it }
                rel == "manifest" -> abs(href)?.let { mu ->
                    val js = get(mu, 120_000) ?: return@let
                    for (im in Regex("\\{[^{}]*\"src\"\\s*:\\s*\"([^\"]+)\"[^{}]*\\}").findAll(js)) {
                        val src = im.groupValues[1]
                        if (src.endsWith(".svg", true)) continue
                        val s2 = size(Regex("\"sizes\"\\s*:\\s*\"([^\"]+)\"").find(im.value)?.groupValues?.get(1))
                        if (s2 >= 96) try { cands += s2 to java.net.URL(java.net.URL(mu), src).toString() } catch (e: Exception) { }
                    }
                }
            }
        }
        cands += 140 to "${base}apple-touch-icon.png"
        for ((_, u) in cands.filter { it.second.startsWith("https://") }.sortedByDescending { it.first }.distinctBy { it.second }.take(5))
            if (com.firsat360.app.data.Download.isImage(u)) return u
        return null
    }
}

fun Context.storeLogo(id: String, name: String, sizeDp: Int, radiusDp: Float = 12f): FrameLayout {
    val box = FrameLayout(this).apply { background = rounded(Theme.store(id), radiusDp); contentDescription = name }
    val short = Theme.storeShort[id] ?: name.take(3)
    val mono = text(short, if (short.length >= 4) sizeDp * 0.29f else sizeDp * 0.34f, Theme.onStore(id), Theme.display, 1)
    box.addView(mono, FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    val u = StoreLogos.url(id) ?: return box
    if (!ImageLoader.enabled) return box
    val img = NetImage(this, radiusDp).apply {
        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = View.INVISIBLE
    }
    box.addView(img, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    img.loadResult(u, dp(sizeDp) * 2) { ok ->
        val w = (img.drawable as? BitmapDrawable)?.bitmap?.width ?: 0
        if (ok && w >= 72) {
            img.visibility = View.VISIBLE; mono.visibility = View.GONE
            box.background = rounded(0xFFFFFFFF.toInt(), radiusDp)
            val pad = dp(sizeDp) / 10; img.setPadding(pad, pad, pad, pad)
        } else { StoreLogos.fail(u); box.removeView(img) }
    }
    return box
}
