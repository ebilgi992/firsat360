package com.firsat360.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import com.firsat360.app.core.Feed
import com.firsat360.app.data.CrashLog
import com.firsat360.app.data.Repository
import com.firsat360.app.screens.CatalogScreen
import com.firsat360.app.screens.FavoritesScreen
import com.firsat360.app.screens.HomeScreen
import com.firsat360.app.screens.OfferScreen
import com.firsat360.app.screens.SearchScreen
import com.firsat360.app.screens.SettingsScreen
import com.firsat360.app.screens.StoresScreen
import com.firsat360.app.screens.WebScreen
import com.firsat360.app.ui.Icon
import com.firsat360.app.ui.IconView
import com.firsat360.app.ui.MATCH
import com.firsat360.app.ui.Theme
import com.firsat360.app.ui.WRAP
import com.firsat360.app.ui.dp
import com.firsat360.app.ui.emptyState
import com.firsat360.app.ui.lp
import com.firsat360.app.ui.onTap
import com.firsat360.app.ui.rounded
import com.firsat360.app.ui.skeleton
import com.firsat360.app.ui.text
import java.time.LocalDate
import java.time.OffsetDateTime

enum class Tab(val label: String, val icon: Icon) {
    HOME("Ana Sayfa", Icon.HOME), STORES("Mağazalar", Icon.STORE), SEARCH("Ara", Icon.SEARCH),
    FAVORITES("Favoriler", Icon.HEART), LIST("Listem", Icon.LIST)
}

abstract class Screen(val a: MainActivity) {
    open val tab: Tab = Tab.HOME
    abstract fun create(): View
    /** true dönerse geri tuşu ekran içinde tüketildi. */
    open fun onBack(): Boolean = false
    open fun onHide() {}
}

class MainActivity : Activity() {
    lateinit var repo: Repository
    lateinit var user: com.firsat360.app.data.UserStore
    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private val stack = ArrayList<Screen>()
    private val navItems = HashMap<Tab, Pair<IconView, android.widget.TextView>>()

    val feed: Feed get() = repo.feed ?: throw IllegalStateException("Veri yüklenmedi")
    val today: LocalDate get() = LocalDate.now(com.firsat360.app.core.Dates.TR)
    val now: OffsetDateTime get() = OffsetDateTime.now()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        try {
            buildUi()
        } catch (t: Throwable) {
            // Açılışta beklenmeyen bir hata uygulamayı kapatmasın: raporu kaydet ve sade bir hata ekranı göster.
            CrashLog.save(this, t, "açılış (onCreate)")
            showStartupError(t)
        }
    }

    private fun buildUi() {
        Theme.init(this)
        repo = Repository(this)
        user = com.firsat360.app.data.UserStore(this)
        user.migrate()
        com.firsat360.app.core.AppLog.init(java.io.File(filesDir, "logs"), verbose = false)
        applyStorageSettings()
        com.firsat360.app.data.ImageLoader.init(this)
        com.firsat360.app.data.ImageLoader.enabled = user.showImages
        com.firsat360.app.data.ImageLoader.allowNetwork = {
            user.imagesOnMobile || !com.firsat360.app.data.Sync.metered(this)
        }
        com.firsat360.app.data.Notifier.channels(this)
        Thread { com.firsat360.app.data.Maintenance.run(applicationContext) }.start()

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Theme.p.bg) }
        content = FrameLayout(this)
        root.addView(content, lp(MATCH, 0, weight = 1f))
        nav = buildNav()
        root.addView(nav, lp(MATCH, WRAP))
        setContentView(root)
        // ÖNEMLİ: Pencere görünümü (DecorView) setContentView ile oluşur. Sistem çubuğu ayarları ondan SONRA yapılmalı;
        // 0.2.0'da window.insetsController daha önce çağrıldığı için Android 11+ cihazlarda açılışta çöküyordu.
        applySystemBars()
        // targetSdk 35+ ile Android 15+ kenardan kenara düzeni zorunlu kılar; o durumda sistem çubukları için boşluk bırak.
        if (applicationInfo.targetSdkVersion >= 35 && android.os.Build.VERSION.SDK_INT >= 35) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                @Suppress("DEPRECATION")
                v.setPadding(0, insets.systemWindowInsetTop, 0, insets.systemWindowInsetBottom)
                insets
            }
            root.requestApplyInsets()
        }

        content.addView(skeleton())
        repo.loadInitial { f, err ->
            try {
                if (f == null) showFatal(err ?: "Veri yüklenemedi") else {
                    Theme.registerStores(f.stores)
                    switchTab(Tab.HOME)
                    handleOpenIntent(intent)
                    if (user.seenCatalogs() == null) user.markCatalogsSeen(f.catalogs.map { it.id })
                    com.firsat360.app.data.Notifier.schedule(this)
                    // Açılışta güncel veriyi kontrol et (değişmemişse indirilmez; hata olursa mevcut veri kalır)
                    repo.refresh { nf, _ -> if (nf != null) onFeedUpdated() }
                }
            } catch (t: Throwable) {
                CrashLog.save(this, t, "ilk ekran")
                showScreenError(t)
            }
        }
    }

    /** Durum/gezinme çubuğu renk ve simge görünümü. Yalnızca görünüm ayarıdır; hiçbir koşulda uygulamayı çökertmez. */
    private fun applySystemBars() {
        try {
            val decor = window.decorView  // DecorView'ın var olduğundan emin ol
            @Suppress("DEPRECATION")
            run {
                window.statusBarColor = Theme.p.bg
                window.navigationBarColor = Theme.p.surface
            }
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                val light = android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                    android.view.WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                val controller = decor.windowInsetsController ?: window.insetsController
                controller?.setSystemBarsAppearance(if (Theme.p.dark) 0 else light, light)
            } else {
                var flags = 0
                if (!Theme.p.dark) flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or 0x10 /* LIGHT_NAVIGATION_BAR (API 26) */
                @Suppress("DEPRECATION")
                decor.systemUiVisibility = flags
            }
        } catch (t: Throwable) {
            // Görünüm ayarı başarısız olabilir (üretici değişiklikleri vb.); uygulama çalışmaya devam eder.
        }
    }

    /** Theme yüklenemese bile çalışan, en sade hata ekranı. */
    private fun showStartupError(t: Throwable) {
        val report = CrashLog.format(t, "açılış (onCreate)")
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(48, 160, 48, 48) }
        col.addView(android.widget.TextView(this).apply { text = "FIRSAT360 açılırken bir hata oluştu"; textSize = 20f })
        col.addView(android.widget.TextView(this).apply {
            text = "Hata raporunu paylaşarak geliştiriciye iletebilirsiniz.\n\n" + report.take(1500); textSize = 12f
            setPadding(0, 24, 0, 24)
        })
        col.addView(android.widget.Button(this).apply { text = "Hata raporunu paylaş"; setOnClickListener { share(report) } })
        setContentView(android.widget.ScrollView(this).apply { addView(col) })
    }

    private fun showScreenError(t: Throwable) {
        val report = CrashLog.format(t, "ekran: ${stack.lastOrNull()?.javaClass?.simpleName ?: "?"}")
        content.removeAllViews()
        content.addView(emptyState(Icon.INFO, "Bu ekran açılamadı",
            "Beklenmeyen bir hata oluştu. Raporu paylaşarak bize iletebilirsiniz.", "Hata raporunu paylaş") { share(report) },
            FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (::repo.isInitialized && repo.feed != null) handleOpenIntent(intent)
    }

    /** Bildirime veya push bağlantısına (firsat360://…) dokunulduğunda ilgili ekranı açar. */
    private fun handleOpenIntent(i: Intent?) {
        try {
            var kind = i?.getStringExtra("open_kind")
            var id = i?.getStringExtra("open_id") ?: ""
            val uri = i?.data
            if (kind == null && uri != null && uri.scheme == "firsat360") {
                kind = uri.host; id = uri.pathSegments.firstOrNull() ?: ""
                if (kind == "catalogs") kind = "new_catalogs"
            }
            kind ?: return
            i?.removeExtra("open_kind"); i?.data = null
            when {
                kind == "catalog" && id.isNotEmpty() && feed.catalog(id) != null -> openCatalog(id)
                kind == "store" && id.isNotEmpty() && feed.store(id) != null -> openStore(id)
                kind == "catalog" || kind == "new_catalogs" || kind == "store" -> openCatalogList(true)
            }
        } catch (t: Throwable) { CrashLog.save(this, t, "bildirim açma") }
    }

    /** Günlük katalog bildirimini açar/kapatır; Android 13+ izni ister; reddedilirse nasıl açılacağını gösterir. */
    fun enableNotifications(on: Boolean) {
        user.notifyCatalogs = on
        if (on) {
            // Açıldığı andaki kataloglar için geriye dönük bildirim yapılmaz
            repo.feed?.let { f -> user.markCatalogsSeen(com.firsat360.app.core.Digest.fresh(f, emptySet(), now).map { it.id }) }
            if (android.os.Build.VERSION.SDK_INT >= 33 && !com.firsat360.app.data.Notifier.permitted(this)) {
                try { requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 701) } catch (e: Exception) { }
            }
        }
        com.firsat360.app.data.Notifier.schedule(this)
        if (on) toast("Her gün ${com.firsat360.app.screens.minuteLabel(user.notifyMinute)}'da yeni kataloglar bildirilecek")
        refreshCurrent()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 701) {
            val ok = grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED
            if (ok) toast("Bildirimler açıldı") else try {
                android.app.AlertDialog.Builder(this)
                    .setTitle("Bildirim izni verilmedi")
                    .setMessage("Katalog bildirimlerini almak için: Ayarlar › Uygulamalar › FIRSAT360 › Bildirimler › İzin ver. " +
                        "İzin vermeseniz de uygulamayı kullanmaya devam edebilirsiniz.")
                    .setPositiveButton("Ayarları aç") { _, _ -> openNotificationSettings() }
                    .setNegativeButton("Şimdi değil", null)
                    .show()
            } catch (e: Exception) { toast("Bildirim izni verilmedi; Android ayarlarından açabilirsiniz") }
            refreshCurrent()
        }
    }

    fun openNotificationSettings() {
        try {
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        } catch (e: Exception) {
            try { startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            catch (e2: Exception) { toast("Ayarlar açılamadı") }
        }
    }

    /** Yeni veri geldiğinde: mağaza renklerini kaydet, ana sayfadaysak yenile. */
    fun onFeedUpdated() {
        repo.feed?.let { Theme.registerStores(it.stores) }
        val top = stack.lastOrNull()
        if (top is HomeScreen || top is StoresScreen || top is com.firsat360.app.screens.DealsScreen) refreshCurrent()
    }

    fun applyStorageSettings() {
        val free = try { cacheDir.usableSpace } catch (e: Exception) { 0L }
        val mb = if (user.imageCacheMb > 0) user.imageCacheMb else com.firsat360.app.core.DiskBudget.defaultImageLimitMb(free)
        com.firsat360.app.data.ImageLoader.limitBytes = mb * 1048576L
        Thread { com.firsat360.app.data.ImageLoader.trim() }.start()
    }

    /** Kataloğu çevrim dışı kaydet (yalnızca kullanıcı isteyince; önce boyut uyarısı). */
    fun saveOffline(c: com.firsat360.app.core.Catalog) {
        val n = c.pages.size + if (c.hasPdf) 1 else 0
        if (n == 0) { toast("Bu katalog uygulamada kaydedilemiyor"); return }
        val mobile = com.firsat360.app.data.Sync.metered(this)
        try {
            android.app.AlertDialog.Builder(this).setTitle("Çevrim dışı kaydet")
                .setMessage("${c.title} cihazınıza indirilecek (${if (c.hasPdf) "PDF" else "$n sayfa"}). " +
                    (if (mobile) "Mobil veri kullanılır. " else "") + "Ayarlar › Depolama'dan istediğiniz zaman silebilirsiniz.")
                .setPositiveButton("İndir") { _, _ ->
                    toast("İndiriliyor…")
                    Thread {
                        val ok = com.firsat360.app.data.OfflineStore.save(applicationContext, c, { _, _ -> }, { false })
                        runOnUiThread { toast(if (ok) "Kaydedildi: internetsiz açılabilir" else "Kaydedilemedi; bağlantıyı kontrol edin"); refreshCurrent() }
                    }.start()
                }
                .setNegativeButton("Vazgeç", null).show()
        } catch (e: Exception) { }
    }

    private fun buildNav(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        // Düz alt çubuk: gölge yok, üstte ince çizgi; seçili sekmenin adı raf etiketi sarısında.
        background = android.graphics.drawable.LayerDrawable(arrayOf(
            android.graphics.drawable.ColorDrawable(Theme.p.outline),
            android.graphics.drawable.ColorDrawable(Theme.p.surface))).apply { setLayerInset(1, 0, dp(1), 0, 0) }
        setPadding(dp(6), dp(6), dp(6), dp(8))
        for (t in Tab.values()) {
            val item = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setGravity(Gravity.CENTER)
                setPadding(0, dp(6), 0, dp(4))
                contentDescription = t.label
            }
            val icon = IconView(context, t.icon, Theme.p.text3, 24)
            val label = context.text(t.label, 12.5f, Theme.p.text3, Theme.medium, 1).apply { setPadding(dp(5), dp(1), dp(5), dp(2)) }
            item.addView(icon)
            item.addView(label, lp(WRAP, WRAP, t = dp(3)))
            item.onTap { if (repo.feed != null) switchTab(t) }
            navItems[t] = icon to label
            addView(item, lp(0, WRAP, weight = 1f))
        }
    }

    private fun highlight(tab: Tab) {
        for ((t, pair) in navItems) {
            val on = t == tab
            pair.first.color = if (on) Theme.p.text else Theme.p.text3
            pair.first.icon = if (t == Tab.FAVORITES && on) Icon.HEART_FILLED else t.icon
            pair.second.setTextColor(if (on) Theme.p.onLabel else Theme.p.text3)
            pair.second.background = if (on) rounded(Theme.p.label, 2f) else null
            pair.second.typeface = if (on) Theme.semibold else Theme.medium
            (pair.first.parent as? View)?.let { it.contentDescription = t.label + if (on) " (seçili)" else ""; it.isSelected = on }
        }
    }

    private fun show(screen: Screen, forward: Boolean) {
        hideKeyboard()
        val v = try {
            screen.create()
        } catch (t: Throwable) {
            // Bir ekrandaki hata tüm uygulamayı kapatmasın.
            CrashLog.save(this, t, "ekran: ${screen.javaClass.simpleName}")
            val report = CrashLog.format(t, "ekran: ${screen.javaClass.simpleName}")
            emptyState(Icon.INFO, "Bu ekran açılamadı", "Beklenmeyen bir hata oluştu. Raporu paylaşarak bize iletebilirsiniz.",
                "Hata raporunu paylaş") { share(report) }
        }
        content.removeAllViews()
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
        v.alpha = 0f
        v.translationX = if (forward) dp(24).toFloat() else -dp(24).toFloat()
        v.animate().alpha(1f).translationX(0f).setDuration(180).start()
        // Alt ekranlar (detay, katalog, web) açıldığı sekmeyi vurgulu tutar.
        highlight(stack.firstOrNull()?.tab ?: screen.tab)
        updateBackCallback()
    }

    // ---- Geri hareketi -------------------------------------------------------------------------------------
    // targetSdk 36 + Android 16'da onBackPressed() sistem tarafından çağrılmaz; geri hareketi
    // OnBackInvokedCallback ile alınır. Ana sayfa kökündeyken geri çağrısı kaldırılır ki sistem uygulamadan
    // çıkış (ve tahmine dayalı "ana ekrana dön" animasyonu) davranışını kendisi uygulasın.
    private var backCallback: Any? = null
    private var backRegistered = false

    private fun needsBackHandling(): Boolean {
        val top = stack.lastOrNull() ?: return false
        return stack.size > 1 || top.tab != Tab.HOME
    }

    private fun updateBackCallback() {
        if (android.os.Build.VERSION.SDK_INT < 33) return
        val dispatcher = onBackInvokedDispatcher
        if (backCallback == null) backCallback = android.window.OnBackInvokedCallback { handleBack() }
        val cb = backCallback as android.window.OnBackInvokedCallback
        val want = needsBackHandling()
        if (want && !backRegistered) {
            dispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb)
            backRegistered = true
        } else if (!want && backRegistered) {
            dispatcher.unregisterOnBackInvokedCallback(cb)
            backRegistered = false
        }
    }

    /** Uygulama içi geri. true: işlendi; false: kökteyiz (sistem davranışı). */
    fun handleBack(): Boolean {
        val top = stack.lastOrNull()
        if (top != null && top.onBack()) return true
        return when {
            stack.size > 1 -> { top?.onHide(); stack.removeAt(stack.size - 1); show(stack.last(), false); true }
            top != null && top.tab != Tab.HOME -> { switchTab(Tab.HOME); true }
            else -> false
        }
    }

    fun switchTab(tab: Tab) {
        stack.lastOrNull()?.onHide()
        stack.clear()
        val s = when (tab) {
            Tab.HOME -> HomeScreen(this)
            Tab.STORES -> StoresScreen(this, null)
            Tab.SEARCH -> SearchScreen(this)
            Tab.FAVORITES -> FavoritesScreen(this)
            Tab.LIST -> com.firsat360.app.screens.ShoppingListScreen(this)
        }
        stack.add(s)
        show(s, true)
    }

    fun push(s: Screen) {
        stack.lastOrNull()?.onHide()
        stack.add(s)
        show(s, true)
    }

    /** Üstteki ekranı kapatıp yerine [s]'yi açar (geri tuşu bir önceki ekrana döner; ara ekran yığında kalmaz). */
    fun replaceTop(s: Screen) {
        stack.lastOrNull()?.onHide()
        if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
        stack.add(s)
        show(s, true)
    }

    fun refreshCurrent() { stack.lastOrNull()?.let { show(it, false) } }

    fun openOffer(id: String) = push(OfferScreen(this, id))
    fun openDeals() = push(com.firsat360.app.screens.DealsScreen(this))
    fun openCatalogList(newOnly: Boolean) = push(com.firsat360.app.screens.CatalogListScreen(this, newOnly))
    fun openCatalog(id: String) {
        val c = feed.catalog(id) ?: return
        when {
            c.offerCount > 0 -> push(CatalogScreen(this, id))
            c.hasPdf -> push(com.firsat360.app.screens.PdfScreen(this, id))
            c.hasPages && com.firsat360.app.data.ImageLoader.enabled -> openReader(id)
            // Flipbook / resmî katalog sayfası: uygulama içinde tam ekran katalog görünümü
            else -> push(WebScreen(this, c.officialUrl, c.title, catalog = true))
        }
    }
    fun openReader(id: String, page: Int = 0) = push(com.firsat360.app.screens.CatalogReaderScreen(this, id, page))
    fun openStore(id: String) { stack.clear(); val s = StoresScreen(this, id); stack.add(s); show(s, true) }
    fun openSearch(category: String? = null) { stack.clear(); val s = SearchScreen(this, category); stack.add(s); show(s, true) }
    fun openWeb(url: String, title: String) = push(WebScreen(this, url, title))
    fun openSettings() = push(SettingsScreen(this))

    fun openExternal(url: String) {
        val scheme = Uri.parse(url).scheme?.lowercase()
        if (scheme !in setOf("https", "http", "tel", "mailto", "market")) { toast("Bu bağlantı açılamıyor"); return }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: ActivityNotFoundException) {
            toast("Bağlantıyı açacak uygulama bulunamadı")
        }
    }

    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        try { startActivity(Intent.createChooser(i, "Paylaş")) } catch (e: ActivityNotFoundException) { toast("Paylaşım uygulaması yok") }
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        currentFocus?.let { imm.hideSoftInputFromWindow(it.windowToken, 0) }
    }

    /** API 32 ve altı (ve geri hareketi geri çağrısı kayıtlı değilken eski sürümler) için. */
    @Deprecated("Activity API")
    override fun onBackPressed() {
        if (!handleBack()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun showFatal(msg: String) {
        content.removeAllViews()
        content.addView(emptyState(Icon.INFO, "Veriler açılamadı", msg, "Tekrar dene") {
            repo.clearCache(); recreate()
        }, FrameLayout.LayoutParams(MATCH, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))
    }
}
