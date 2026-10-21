package com.firsat360.app.screens

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.ScrollView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.AppLog
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Digest
import com.firsat360.app.core.ValidityKind
import com.firsat360.app.data.PdfCache
import com.firsat360.app.ui.*
import java.io.File
import java.util.concurrent.Executors

/** Günün İndirimleri: sunucunun her gün otomatik seçtiği, birçok marketten ürünler. */
class DealsScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.HOME
    private var store: String? = null

    override fun create(): View {
        val f = a.feed
        val all = f.dealOffers
        val outer = a.vbox().apply { setBackgroundColor(Theme.p.bg) }
        outer.addView(a.topBar("Günün İndirimleri"))
        val list = a.gridListView(true)
        val header = a.vbox()
        val box = a.vbox(dp(18)).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Theme.p.accent2, Theme.p.deal)).apply { cornerRadius = dp(22).toFloat() }
        }
        box.addView(a.hbox().apply {
            addView(IconView(a, Icon.FLAME, 0xFFFFFFFF.toInt(), 22))
            addView(a.text("${all.size} ürün · ${all.map { it.storeId }.distinct().size} market", 15f, 0xFFFFFFFF.toInt(),
                Theme.bold), lp(WRAP, WRAP, l = dp(8)))
        })
        box.addView(a.text("Bugün ${Dates.long(a.today)} için marketlerin resmî sayfalarından otomatik seçildi.",
            13f, 0xE6FFFFFF.toInt(), Theme.medium), lp(t = dp(8)))
        header.addView(box, lp(l = dp(16), r = dp(16), t = dp(4)))
        header.addView(a.notice(Icon.INFO, f.dailyDeals?.note ?: "Fiyatlar ve stok durumu değişebilir.", Theme.p.text2,
            Theme.p.surface2), lp(l = dp(16), r = dp(16), t = dp(10)))
        val chips = a.hbox().apply { setPadding(dp(16), dp(14), dp(8), dp(6)) }
        chips.addView(a.chip("Tümü", store == null) { store = null; a.refreshCurrent() }, lp(WRAP, WRAP, r = dp(8)))
        for (sid in all.map { it.storeId }.distinct()) {
            chips.addView(a.chip(f.store(sid)?.name ?: sid, store == sid, Theme.store(sid)) { store = sid; a.refreshCurrent() },
                lp(WRAP, WRAP, r = dp(8)))
        }
        header.addView(a.hscroll(chips))
        list.addHeaderView(header, null, false)
        val items = all.filter { store == null || it.storeId == store }
        if (items.isEmpty()) list.addFooterView(a.emptyState(Icon.FLAME, "Bugün için seçim yok",
            "Günün İndirimleri her sabah yenilenir.", null, null), null, false)
        list.adapter = OfferGridAdapter(a, items)
        outer.addView(list, lp(MATCH, 0, weight = 1f))
        return outer
    }
}

/** Katalog listesi: tümü veya yalnızca yeni yayınlananlar (bildirime dokunulduğunda da açılır). */
class CatalogListScreen(a: MainActivity, private val newOnly: Boolean) : Screen(a) {
    override val tab: Tab get() = Tab.STORES

    override fun create(): View {
        val f = a.feed
        val cats = com.firsat360.app.core.CatalogRules.collapse(if (newOnly) Digest.newToday(f, a.now) else f.catalogs.filter {
            it.kind != "link_only" && Dates.status(it.validFrom, it.validTo, a.today).kind != ValidityKind.EXPIRED
        }, a.user.region).sortedByDescending { it.firstSeenAt ?: it.validFrom ?: "" }
        val outer = a.vbox().apply { setBackgroundColor(Theme.p.bg) }
        outer.addView(a.topBar(if (newOnly) "Yeni kataloglar" else "Tüm kataloglar"))
        val col = a.vbox().apply { setPadding(dp(16), dp(4), dp(16), dp(28)) }
        if (cats.isEmpty()) col.addView(a.emptyState(Icon.CATALOG, "Şu an yeni katalog yok",
            "Marketler yeni katalog yayınladığında burada görünür.", "Tüm kataloglar") { a.openCatalogList(false) })
        for ((sid, group) in cats.groupBy { it.storeId }) {
            col.addView(a.text(f.store(sid)?.name ?: sid, 16f, Theme.p.text, Theme.bold), lp(t = dp(18), b = dp(10)))
            for (c in group) col.addView(a.catalogCard(c, f.store(sid)?.name ?: "", a.today, wide = true) { a.openCatalog(c.id) },
                lp(b = dp(12)))
        }
        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }
}

/**
 * Resmî PDF katalog (ör. Migroskop): kullanıcı açtığında indirilir (ilerleme gösterilir, iptal edilebilir), Android'in
 * kendi PdfRenderer'ı ile sayfa sayfa gösterilir. Bellek: yalnızca görünen sayfalar çizilir (küçük LRU).
 */
class PdfScreen(a: MainActivity, private val catalogId: String,
                /** Feed'de olmayan, köprünün resmî sayfadan bulduğu PDF (ör. ŞOK "Haftanın Fırsatları"). */
                private val adhoc: com.firsat360.app.core.Catalog? = null) : Screen(a) {
    override val tab: Tab get() = Tab.STORES
    @Volatile private var cancelled = false
    private var renderer: PdfRenderer? = null
    private var pfd: ParcelFileDescriptor? = null
    private var worker = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<Int, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 8).toInt().coerceAtLeast(8 * 1024)) {
        override fun sizeOf(key: Int, value: Bitmap) = value.byteCount / 1024
    }

    override fun create(): View {
        // Ekran yeniden gösterildiğinde (geri dönüş) kaynaklar yeniden açılır
        cancelled = false
        if (worker.isShutdown) worker = Executors.newSingleThreadExecutor()
        val c = adhoc ?: a.feed.catalog(catalogId) ?: return a.emptyState(Icon.CATALOG, "Katalog bulunamadı", "")
        val root = a.vbox().apply { setBackgroundColor(0xFF0B0D12.toInt()) }
        val bar = a.hbox().apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        bar.addView(a.iconButton(Icon.CLOSE, 0xFFF2F3F7.toInt(), 0x33FFFFFF, "Kapat") { a.onBackPressedCompat() }
            .apply { background = rounded(0x33FFFFFF, 99f) })
        val titles = a.vbox()
        titles.addView(a.text(c.title, 15f, 0xFFF2F3F7.toInt(), Theme.semibold, 1))
        val counter = a.text(Dates.status(c.validFrom, c.validTo, a.today).label, 12f, 0xB3F2F3F7.toInt(), Theme.medium, 1)
        titles.addView(counter, lp(t = dp(2)))
        bar.addView(titles, lp(0, WRAP, l = dp(12), weight = 1f))
        val saved = com.firsat360.app.data.OfflineStore.isSaved(a, c.id)
        bar.addView(a.iconButton(if (saved) Icon.CHECK else Icon.DOWNLOAD, 0xFFF2F3F7.toInt(), 0x33FFFFFF,
            if (saved) "Çevrim dışı kayıtlı" else "Çevrim dışı kaydet") {
            if (saved) a.toast("Bu katalog cihazınıza kayıtlı") else a.saveOffline(c)
        }.apply { background = rounded(0x33FFFFFF, 99f) }, lp(dp(42), dp(42), l = dp(8)))
        root.addView(bar)

        val body = FrameLayout(a)
        val loading = a.vbox(dp(24)).apply { setGravity(Gravity.CENTER_HORIZONTAL) }
        val pb = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = true
            progressTintList = android.content.res.ColorStateList.valueOf(Theme.p.accent)
        }
        val label = a.text("Katalog indiriliyor…", 14f, 0xFFF2F3F7.toInt(), Theme.medium, gravity = Gravity.CENTER)
        loading.addView(label)
        loading.addView(pb, lp(dp(220), WRAP, t = dp(14)))
        loading.addView(a.text("Yalnızca bu katalog indirilir; cihazda en fazla son 2 PDF geçici olarak tutulur.", 12f,
            0x99F2F3F7.toInt(), Theme.regular, gravity = Gravity.CENTER), lp(t = dp(14)))
        body.addView(loading, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
        root.addView(body, lp(MATCH, 0, weight = 1f))

        Thread {
            val file = try {
                PdfCache.get(a, c, { p -> main.post { pb.isIndeterminate = false; pb.progress = (p * 100).toInt() } }, { cancelled })
            } catch (t: Throwable) { null }
            main.post {
                if (cancelled) return@post
                if (file == null) {
                    body.removeAllViews()
                    body.addView(a.emptyState(Icon.INFO, "Katalog açılamadı", "İnternet bağlantınızı kontrol edin veya " +
                        "kataloğu marketin sayfasında açın.", "Resmî sayfada aç") { a.openWeb(c.officialUrl, c.title) },
                        FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
                } else showPdf(file, body, counter)
            }
        }.start()
        return root
    }

    private fun showPdf(file: File, body: FrameLayout, counter: android.widget.TextView) {
        val r = try {
            pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfRenderer(pfd!!)
        } catch (t: Throwable) {
            AppLog.e("pdf.open", t.message); file.delete(); null
        }
        body.removeAllViews()
        if (r == null) {
            body.addView(a.emptyState(Icon.INFO, "PDF açılamadı", "Dosya bozuk olabilir; tekrar açmayı deneyin.", null, null),
                FrameLayout.LayoutParams(MATCH, WRAP, Gravity.CENTER))
            return
        }
        renderer = r
        val n = r.pageCount
        counter.text = "$n sayfa"
        val width = (a.resources.displayMetrics.widthPixels).coerceAtMost(1440)
        val list = ListView(a).apply {
            divider = null; dividerHeight = 0; setBackgroundColor(0xFF0B0D12.toInt()); selector = android.graphics.drawable.ColorDrawable(0)
            isVerticalScrollBarEnabled = true
        }
        list.adapter = object : BaseAdapter() {
            override fun getCount() = n
            override fun getItem(p: Int) = p
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val iv = (convert as? ImageView) ?: ImageView(a).apply {
                    adjustViewBounds = true; scaleType = ImageView.ScaleType.FIT_CENTER
                    setBackgroundColor(Color.WHITE)
                    layoutParams = AbsListView.LayoutParams(MATCH, WRAP)
                    minimumHeight = width * 4 / 3
                }
                iv.tag = p
                iv.contentDescription = "Sayfa ${p + 1}"
                val bmp = cache.get(p)
                if (bmp != null) iv.setImageBitmap(bmp) else {
                    iv.setImageDrawable(null)
                    worker.execute {
                        val b = render(p, width)
                        main.post { if (iv.tag == p && b != null) iv.setImageBitmap(b) }
                    }
                }
                iv.setOnClickListener { zoom(p) }
                return iv
            }
        }
        list.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: AbsListView?, s: Int) {}
            override fun onScroll(v: AbsListView?, first: Int, visible: Int, total: Int) { counter.text = "${first + 1} / $n" }
        })
        body.addView(list, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** Sayfayı büyük çizip yakınlaştırılabilir görünümde açar. */
    private fun zoom(p: Int) {
        worker.execute {
            val b = render(p, (a.resources.displayMetrics.widthPixels * 2).coerceAtMost(2400), keep = false) ?: return@execute
            main.post {
                val zv = ZoomImageView(a).apply { setBackgroundColor(0xFF0B0D12.toInt()); setImageBitmap(b) }
                val dlg = android.app.Dialog(a, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                zv.onTap = { dlg.dismiss() }
                dlg.setContentView(zv)
                try { dlg.show() } catch (e: Exception) { }
            }
        }
    }

    private fun render(p: Int, width: Int, keep: Boolean = true): Bitmap? {
        val r = renderer ?: return null
        return try {
            synchronized(r) {
                val page = r.openPage(p)
                try {
                    val h = (width.toLong() * page.height / page.width.coerceAtLeast(1)).toInt().coerceAtMost(width * 3)
                    val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    if (keep) cache.put(p, bmp)
                    bmp
                } finally { page.close() }
            }
        } catch (e: OutOfMemoryError) { cache.evictAll(); null } catch (t: Throwable) { null }
    }

    override fun onHide() {
        cancelled = true
        worker.execute {
            try { renderer?.close() } catch (t: Throwable) { }
            try { pfd?.close() } catch (t: Throwable) { }
            renderer = null; pfd = null
        }
        worker.shutdown()
        cache.evictAll()
    }
}
