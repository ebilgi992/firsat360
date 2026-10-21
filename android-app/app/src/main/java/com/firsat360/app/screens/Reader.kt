package com.firsat360.app.screens

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.CatalogPage
import com.firsat360.app.core.Dates
import com.firsat360.app.data.ImageLoader
import com.firsat360.app.ui.*

/**
 * Dijital dergi gibi katalog okuyucu: tam ekran sayfa, kaydırarak sayfa değiştirme, iki parmakla/çift dokunuşla
 * yakınlaştırma, sayfa küçük resimleriyle istenen sayfaya gitme, favori, paylaşma ve resmî kaynağa gitme.
 * Sayfalar mağazanın resmî CDN'inden yüklenir ve cihazda önbelleğe alınır; sonraki sayfa önden indirilir.
 *
 * [single]: katalog yerine tek bir görsel (ör. ürün fotoğrafını büyütme).
 */
class CatalogReaderScreen(
    a: MainActivity, private val catalogId: String?, private var index: Int = 0,
    private val single: CatalogPage? = null, private val singleTitle: String? = null,
    /** Resmî sayfadan cihazda toplanan broşür sayfaları (feed'de olmayan, ör. A101 afişleri). */
    private val extraPages: List<CatalogPage>? = null,
) : Screen(a) {
    override val tab: Tab get() = Tab.STORES
    private val dark = 0xFF0B0D12.toInt()
    private val onDark = 0xFFF2F3F7.toInt()
    private val dim = 0xB3F2F3F7.toInt()
    private var pageView: ZoomImageView? = null
    private var counter: TextView? = null
    private var caption: TextView? = null
    private var linkBtn: View? = null
    private var progress: ProgressBar? = null
    private var error: View? = null
    private var thumbs: LinearLayout? = null
    private var thumbScroll: HorizontalScrollView? = null
    private var chrome = true
    private var bars: List<View> = emptyList()

    private val pages: List<CatalogPage>
        get() = extraPages ?: single?.let { listOf(it) } ?: (a.feed.catalog(catalogId)?.pages ?: emptyList())

    override fun create(): View {
        val f = a.feed
        val c = f.catalog(catalogId)
        val store = c?.let { f.store(it.storeId) }
        val ps = pages
        if (index !in ps.indices) index = 0
        val root = FrameLayout(a).apply { setBackgroundColor(dark) }

        if (ps.isEmpty()) {
            root.addView(a.emptyState(Icon.CATALOG, "Bu katalogun sayfa görseli yok",
                "Mağaza görselleri yayınlamadıysa veya görsel kullanımı kapalıysa resmî sayfadan inceleyebilirsiniz.",
                "Resmî sayfayı aç") { c?.let { a.openWeb(it.officialUrl, it.title) } },
                FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            return root
        }

        // Sayfa
        val zv = ZoomImageView(a)
        zv.onSwipe = { dir -> go(index + dir, dir) }
        zv.onTap = { toggleChrome() }
        root.addView(zv, FrameLayout.LayoutParams(MATCH, MATCH))
        pageView = zv
        val pb = ProgressBar(a).apply { isIndeterminate = true }
        root.addView(pb, FrameLayout.LayoutParams(dp(40), dp(40), Gravity.CENTER))
        progress = pb
        val err = a.vbox(dp(20)).apply {
            setGravity(Gravity.CENTER_HORIZONTAL); visibility = View.GONE
            addView(IconView(a, Icon.INFO, dim, 32))
            addView(a.text("Sayfa yüklenemedi. İnternet bağlantınızı kontrol edin.", 14f, onDark, Theme.medium,
                gravity = Gravity.CENTER), lp(t = dp(10)))
            addView(a.button("Tekrar dene", Icon.REFRESH, true) { showPage(0) }, lp(WRAP, WRAP, t = dp(14)))
        }
        root.addView(err, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        error = err

        // Üst çubuk
        val top = a.hbox().apply {
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0xE60B0D12.toInt(), 0x000B0D12))
        }
        top.addView(a.iconButton(Icon.CLOSE, onDark, 0x33FFFFFF, "Kapat") { a.onBackPressedCompat() }.apply {
            background = rounded(0x33FFFFFF, 8f) })
        val titles = a.vbox()
        titles.addView(a.text(singleTitle ?: c?.title ?: "", 15f, onDark, Theme.semibold, 1))
        val sub = listOfNotNull(store?.name, c?.let { Dates.status(it.validFrom, it.validTo, a.today).label }).joinToString(" · ")
        titles.addView(a.text(sub, 12f, dim, Theme.medium, 1), lp(t = dp(2)))
        top.addView(titles, lp(0, WRAP, l = dp(12), weight = 1f))
        if (catalogId != null && c != null) {
            val fav = a.user.favoriteCatalogs().contains(c.id)
            top.addView(a.iconButton(if (fav) Icon.STAR_FILLED else Icon.STAR, if (fav) 0xFFFFC53D.toInt() else onDark, 0x33FFFFFF,
                if (fav) "Katalog favorilerden çıkar" else "Katalogu favorilere ekle") {
                val now = a.user.toggleFavoriteCatalog(c.id)
                a.toast(if (now) "Katalog favorilere eklendi" else "Katalog favorilerden çıkarıldı")
                a.refreshCurrent()
            }.apply { background = rounded(0x33FFFFFF, 8f) }, lp(dp(42), dp(42), l = dp(8)))
            val saved = com.firsat360.app.data.OfflineStore.isSaved(a, c.id)
            top.addView(a.iconButton(if (saved) Icon.CHECK else Icon.DOWNLOAD, onDark, 0x33FFFFFF,
                if (saved) "Çevrim dışı kayıtlı" else "Çevrim dışı kaydet") {
                if (saved) a.toast("Bu katalog cihazınıza kayıtlı. Ayarlar › Depolama'dan silebilirsiniz.")
                else a.saveOffline(c)
            }.apply { background = rounded(0x33FFFFFF, 8f) }, lp(dp(42), dp(42), l = dp(8)))
            top.addView(a.iconButton(Icon.SHARE, onDark, 0x33FFFFFF, "Paylaş") {
                val p = pages.getOrNull(index)
                a.share("${store?.name ?: ""} · ${c.title}\nResmî kaynak: ${p?.link ?: c.officialUrl}")
            }.apply { background = rounded(0x33FFFFFF, 8f) }, lp(dp(42), dp(42), l = dp(8)))
        }
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // Alt çubuk: sayaç, açıklama, sayfa küçük resimleri
        val bottom = a.vbox().apply {
            setPadding(0, dp(18), 0, dp(14))
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.BOTTOM_TOP,
                intArrayOf(0xF00B0D12.toInt(), 0x000B0D12))
        }
        val info = a.hbox().apply { setPadding(dp(16), 0, dp(16), 0) }
        val cnt = a.text("", 13f, onDark, Theme.semibold, 1)
        info.addView(cnt)
        val cap = a.text("", 12.5f, dim, Theme.regular, 2)
        info.addView(cap, lp(0, WRAP, l = dp(12), weight = 1f))
        val link = a.text("Resmî sayfa", 12.5f, 0xFFFF8FA3.toInt(), Theme.semibold, 1).apply {
            setPadding(dp(10), dp(6), 0, dp(6))
            onTap {
                val p = pages.getOrNull(index)
                val u = p?.link ?: c?.officialUrl
                if (u != null) a.openWeb(u, c?.title ?: "Kaynak")
            }
        }
        info.addView(link)
        bottom.addView(info)
        counter = cnt; caption = cap; linkBtn = link
        if (ps.size > 1) {
            val strip = a.hbox().apply { setPadding(dp(12), 0, dp(12), 0) }
            for ((i, p) in ps.withIndex()) {
                val th = NetImage(a, 8f).apply {
                    contentDescription = "Sayfa ${i + 1}"
                    onTap { go(i, if (i > index) 1 else -1) }
                }
                th.load(p.thumbUrl ?: p.url, dp(120))
                strip.addView(th, lp(dp(54), dp(74), r = dp(8)))
            }
            val hs = a.hscroll(strip)
            bottom.addView(hs, lp(MATCH, WRAP, t = dp(12)))
            thumbs = strip; thumbScroll = hs
        }
        root.addView(bottom, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        bars = listOf(top, bottom)

        root.post { showPage(0) }
        return root
    }

    private fun go(to: Int, dir: Int) {
        val ps = pages
        if (to == index) return
        if (to !in ps.indices) {
            if (ps.size > 1) a.toast(if (to < 0) "İlk sayfa" else "Son sayfa")
            return
        }
        index = to
        showPage(dir)
    }

    private fun showPage(dir: Int) {
        val zv = pageView ?: return
        val ps = pages
        val p = ps.getOrNull(index) ?: return
        counter?.text = "${index + 1} / ${ps.size}"
        caption?.text = p.caption ?: ""
        linkBtn?.visibility = if (p.link != null || catalogId != null) View.VISIBLE else View.GONE
        progress?.visibility = View.VISIBLE
        error?.visibility = View.GONE
        thumbs?.let { strip ->
            for (i in 0 until strip.childCount) strip.getChildAt(i).alpha = if (i == index) 1f else 0.45f
            strip.getChildAt(index)?.let { v -> thumbScroll?.smoothScrollTo((v.left - dp(120)).coerceAtLeast(0), 0) }
        }
        if (dir != 0) {
            zv.translationX = dir * zv.width * 0.25f; zv.alpha = 0.3f
            zv.animate().translationX(0f).alpha(1f).setDuration(200).start()
        }
        zv.setImageDrawable(ColorDrawable(0))
        val maxPx = 2400
        val local = catalogId?.let { com.firsat360.app.data.OfflineStore.pageFile(a, it, p.url) }
        ImageLoader.load(zv, p.url, maxPx, p.thumbUrl, rgb565 = true, local = local) { ok ->
            progress?.visibility = View.GONE
            error?.visibility = if (ok) View.GONE else View.VISIBLE
        }
        ps.getOrNull(index + 1)?.let { ImageLoader.prefetch(it.url, maxPx, true) }
    }

    private fun toggleChrome() {
        chrome = !chrome
        for (b in bars) b.animate().alpha(if (chrome) 1f else 0f).setDuration(160).withEndAction {
            b.visibility = if (chrome) View.VISIBLE else View.INVISIBLE
        }.start()
        if (chrome) for (b in bars) b.visibility = View.VISIBLE
    }

    override fun onBack(): Boolean {
        val zv = pageView ?: return false
        if (zv.isZoomed()) { zv.reset(); return true }
        return false
    }
}
