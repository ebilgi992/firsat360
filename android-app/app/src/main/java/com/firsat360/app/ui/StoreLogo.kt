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
    private val urls = HashMap<String, String>()
    private val failed = HashSet<String>()

    fun register(stores: List<com.firsat360.app.core.Store>) {
        for (s in stores) {
            val host = try { android.net.Uri.parse(s.officialUrl).host } catch (e: Exception) { null } ?: continue
            urls[s.id] = "https://$host/apple-touch-icon.png"
        }
    }
    fun url(id: String): String? = urls[id]?.takeIf { it !in failed }
    fun fail(u: String) { failed += u }
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
