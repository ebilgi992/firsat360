package com.firsat360.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.firsat360.app.core.Catalog
import com.firsat360.app.core.TrDates
import java.io.File
import java.time.LocalDate

/** Köprüden gelen PDF broşür için uygulama içi (geçici) katalog kaydı: PDF okuyucu ve önbellek anahtarı bunu kullanır. */
object BridgeCatalogs {
    fun pdf(storeId: String, p: DeviceBrochures.Pdf, sourceUrl: String, fallbackTitle: String, today: LocalDate): Catalog {
        val range = TrDates.range(p.dates.ifBlank { p.title }, today)
        return Catalog("bridge-$storeId-${p.url.hashCode().toUInt()}", storeId, p.title.ifBlank { fallbackTitle }, "pdf",
            range?.first?.toString(), range?.second?.toString(), sourceUrl, "client_bridge", null, 0, pdfUrl = p.url)
    }
}

/**
 * Raftaki kapakların uygulama açılır açılmaz görünmesi için: istemci köprüsündeki (ve kullanıcının eklediği)
 * marketlerin resmî broşür sayfalarını cihazda, görünmeyen bir WebView'de sırayla okur.
 *
 * Kurallar: yalnızca ölçülmeyen ağda (Wi‑Fi), görseller açıkken, market başına en çok 6 saatte bir, sırayla (aynı anda
 * tek sayfa), sayfa başına 25 sn. Doğrulama/bot sınaması görülürse o market atlanır (asla aşılmaz; kullanıcı kartı
 * açtığında görünür modda kendisi tamamlayabilir). PDF broşürde ilk PDF indirilir (en çok 40 MB), ilk sayfası küçük
 * kapak görseline çizilir; kullanıcı açtığında PDF önbellekten anında açılır.
 */
object BridgePrefetch {
    private const val MIN_INTERVAL_MS = 6L * 60 * 60 * 1000
    private const val PAGE_TIMEOUT_MS = 25_000L
    private const val MAX_PDF = 40L * 1024 * 1024

    data class Job(val storeId: String, val title: String, val url: String, val profile: String, val hosts: List<String>)

    private val main = Handler(Looper.getMainLooper())
    @Volatile var running = false
        private set
    /** Etkin çalışma; [cancel] ile geçersizleşir (etkinlik yeniden kurulunca eski kuyruk ve WebView bırakılır). */
    private var token = 0
    private var current: (() -> Unit)? = null

    /** Etkinlik kapanırken/yeniden kurulurken: kuyruk temizlenir, WebView yok edilir, deneme "başarısız" sayılmaz. */
    fun cancel() {
        token++
        current?.invoke(); current = null
        main.removeCallbacksAndMessages(null)
        running = false
    }

    fun unmetered(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && !cm.isActiveNetworkMetered
    } catch (e: Exception) { false }

    /** [host]: görünmeyen WebView'in ekleneceği kap (ekranın arkasında). [onUpdated] ana iş parçacığında, market başına. */
    fun start(ctx: Context, host: ViewGroup, jobs: List<Job>, force: Boolean = false, onUpdated: (String, Boolean) -> Unit,
              onDone: (() -> Unit)? = null) {
        if (running || !ImageLoader.enabled || (!force && !unmetered(ctx))) { onDone?.invoke(); return }
        val now = System.currentTimeMillis()
        val queue = jobs.filter { j ->
            force || (DeviceBrochures.get(ctx, j.storeId) == null && now - DeviceBrochures.lastAttempt(ctx, j.storeId) > MIN_INTERVAL_MS)
        }.toMutableList()
        if (queue.isEmpty()) { onDone?.invoke(); return }
        running = true
        next(ctx, host, queue, onUpdated, onDone, ++token)
    }

    private fun next(ctx: Context, host: ViewGroup, queue: MutableList<Job>, onUpdated: (String, Boolean) -> Unit,
                     onDone: (() -> Unit)?, t: Int) {
        if (t != token) return  // iptal edildi
        val job = queue.removeFirstOrNull()
        if (job == null) { running = false; DeviceBrochures.pruneCovers(ctx); onDone?.invoke(); return }
        run(ctx, host, job, { changed -> if (t == token) onUpdated(job.storeId, changed) }) { ok ->
            if (t != token) return@run
            DeviceBrochures.markAttempt(ctx, job.storeId, ok)
            main.postDelayed({ next(ctx, host, queue, onUpdated, onDone, t) }, 800)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun run(ctx: Context, host: ViewGroup, job: Job, saved: (Boolean) -> Unit = {}, done: (Boolean) -> Unit) {
        var finished = false
        lateinit var w: WebView
        var released = false
        fun release() {
            if (released) return
            released = true
            try { w.stopLoading(); host.removeView(w); w.destroy() } catch (e: Exception) { }
        }
        fun end(ok: Boolean) {
            if (finished) return
            finished = true
            release()
            done(ok)
        }
        lateinit var session: BridgeSession
        // Süre sınırı sayfa okuma içindir; kendi "katalog görüntüleyicisine geç" adımımızda yeniden başlar.
        val timeout = Runnable { if (!released) { session.cancel(); end(false) } }
        current = { finished = true; session.cancel(); main.removeCallbacks(timeout); release() }
        session = BridgeSession(ctx, job.url, job.profile, job.hosts, object : BridgeSession.Listener {
            override fun onNavigating() { main.removeCallbacks(timeout); main.postDelayed(timeout, PAGE_TIMEOUT_MS) }
            override fun onImages(pages: List<String>) {
                release()
                Thread {
                    // Bulunan sayfalar gerçekten açılıyor mu? Açılmıyorsa kaydedilmez (kart resmî görüntüleyiciyi açar).
                    val ok = pages.take(2).any { Download.isImage(it) }
                    main.post {
                        if (finished) return@post
                        if (ok) saved(DeviceBrochures.save(ctx, job.storeId, job.title, job.url, pages))
                        else DeviceBrochures.dropPages(ctx, job.storeId)
                        end(ok)
                    }
                }.start()
            }
            override fun onPdf(items: List<BridgeSession.PdfItem>) {
                val pdfs = items.map { DeviceBrochures.Pdf(it.url, it.title, it.dates) }
                release()  // WebView hemen bırakılır; PDF arka planda indirilir. Sıradaki market, kayıt bitince başlar.
                Thread {
                    val cover = pdfCover(ctx, job, pdfs)
                    main.post {
                        if (!finished) {
                            saved(DeviceBrochures.save(ctx, job.storeId, job.title, job.url, emptyList(), pdfs, cover))
                            end(true)
                        }
                    }
                }.start()
            }
            override fun onGiveUp(best: List<String>, reason: String) { end(false) }
        }, waitOnChallenge = false, maxMs = 15_000L)
        w = WebView(ctx).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.setGeolocationEnabled(false)
            settings.mediaPlaybackRequiresUserGesture = true
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setWebViewClient(object : WebViewClient() {
                @Deprecated("WebViewClient API")
                override fun shouldOverrideUrlLoading(view: WebView?, u: String?): Boolean =
                    !(u ?: "").startsWith("https://") || !session.hostAllowed(u ?: "")
                override fun onPageFinished(view: WebView?, u: String?) { if (view != null) session.onPageFinished(view) }
            })
            importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            isFocusable = false
        }
        // Ekranın ARKASINDA tam boyutlu: tembel yüklenen görseller için gerçek bir görünüm alanı gerekir.
        host.addView(w, 0, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        w.loadUrl(job.url)
        // Süre sınırı yalnızca sayfa okuma için (PDF indirme sürerken WebView zaten bırakılmıştır)
        main.postDelayed(timeout, PAGE_TIMEOUT_MS)
    }

    /** İlk PDF'i önbelleğe indirir ve ilk sayfasını kapak görseline çizer. Kapak "file://" adresi döner. */
    private fun pdfCover(ctx: Context, job: Job, pdfs: List<DeviceBrochures.Pdf>): String? = try {
        val p = pdfs.first()
        val c = BridgeCatalogs.pdf(job.storeId, p, job.url, job.title, LocalDate.now(com.firsat360.app.core.Dates.TR))
        val f = PdfCache.bridgeFile(ctx, c, p.url)
        val ok = PdfCache.bridgeFresh(f) || (Download.to(p.url, f, MAX_PDF, null).also { if (it) PdfCache.markDownloaded(f) })
        if (!ok) null
        else {
            PdfCache.trimBridge(ctx, protect = setOf(f.name))
            val key = pdfs.joinToString(";") { it.url + "|" + it.title + "|" + it.dates } + "|" + f.length()
            renderFirstPage(f, DeviceBrochures.coverFile(ctx, job.storeId, key))?.let { "file://" + it.path }
        }
    } catch (e: Exception) { null }

    fun renderFirstPage(pdf: File, out: File, widthPx: Int = 720): File? {
        ParcelFileDescriptor.open(pdf, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                if (r.pageCount == 0) return null
                r.openPage(0).use { page ->
                    val h = (widthPx.toFloat() * page.height / page.width).toInt().coerceIn(1, widthPx * 3)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val tmp = File(out.path + ".part")
                    tmp.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
                    bmp.recycle()
                    if (!tmp.renameTo(out)) { out.delete(); tmp.renameTo(out) }
                    return out
                }
            }
        }
    }
}
