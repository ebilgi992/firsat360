package com.firsat360.app.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Paint
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import com.firsat360.app.BuildInfo
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Money
import com.firsat360.app.core.OfferQuery
import com.firsat360.app.core.PriceTrend
import com.firsat360.app.core.Search
import com.firsat360.app.core.SortMode
import com.firsat360.app.ui.*

fun MainActivity.topBar(title: String, vararg actions: View): LinearLayout = hbox().apply {
    setPadding(dp(12), dp(12), dp(12), dp(8))
    setBackgroundColor(Theme.p.bg)
    addView(iconButton(Icon.BACK, desc = "Geri") { onBackPressedCompat() })
    addView(text(title, 16f, Theme.p.text, Theme.semibold, 1), lp(0, WRAP, l = dp(12), r = dp(8), weight = 1f))
    for (v in actions) addView(v, lp(dp(42), dp(42), l = dp(8)))
}

@Suppress("DEPRECATION")
fun MainActivity.onBackPressedCompat() { if (!handleBack()) finish() }

class OfferScreen(a: MainActivity, private val offerId: String) : Screen(a) {
    override val tab: Tab get() = Tab.HOME

    override fun create(): View {
        val f = a.feed
        val o = f.offer(offerId) ?: return a.emptyState(Icon.INFO, "Ürün artık listede değil",
            "Kampanya bitmiş veya veri güncellenmiş olabilir.", "Geri dön") { a.onBackPressedCompat() }
        val store = f.store(o.storeId)
        val catalog = f.catalog(o.catalogId)
        val fav = a.repo.isFavorite(o.id)
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar(store?.name ?: "",
            a.iconButton(Icon.SHARE, desc = "Paylaş") {
                a.share("${o.displayTitle} — ${Money.format(o.price)} (${store?.name}). Kaynak: ${o.productUrl ?: o.sourceUrl}")
            },
            a.iconButton(if (fav) Icon.HEART_FILLED else Icon.HEART, if (fav) Theme.p.danger else Theme.p.text,
                desc = if (fav) "Favoriden çıkar" else "Favoriye ekle") {
                val now = a.repo.toggleFavorite(o.id)
                a.toast(if (now) "Favorilere eklendi" else "Favorilerden çıkarıldı")
                a.refreshCurrent()
            }))

        val col = a.vbox()
        col.setPadding(dp(20), dp(4), dp(20), dp(32))
        // Kahraman blok
        val hero = a.vbox(dp(20)).apply {
            background = rounded(Theme.blend(Theme.p.surface, Theme.category(o.category), if (Theme.p.dark) 0.16f else 0.08f), 24f)
            setGravity(Gravity.CENTER_HORIZONTAL)
        }
        val imgUrl = o.imageLargeUrl ?: o.imageUrl
        if (imgUrl != null && com.firsat360.app.data.ImageLoader.enabled) {
            val mono = Monogram(a, 88).apply { bind(o); visibility = View.GONE }
            val photo = NetImage(a, 18f).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                setBackgroundColor(0xFFFFFFFF.toInt())
                contentDescription = "${o.displayTitle} görseli, büyütmek için dokunun"
                onTap {
                    a.push(CatalogReaderScreen(a, null, 0,
                        com.firsat360.app.core.CatalogPage(imgUrl, o.imageUrl, o.displayTitle, o.productUrl, true), o.displayTitle))
                }
            }
            photo.load(imgUrl, dp(720), o.imageUrl) { photo.visibility = View.GONE; mono.visibility = View.VISIBLE }
            hero.addView(photo, lp(MATCH, dp(300)))
            hero.addView(mono)
            if (o.imageVerified || o.imageSource == "official_json") {
                val src = a.hbox(Gravity.CENTER).apply { setPadding(dp(10), dp(6), dp(12), dp(6)) }
                src.addView(IconView(a, Icon.CHECK, Theme.p.ok, 15))
                src.addView(a.text("${store?.name ?: ""} resmî ürün görseli", 11.5f, Theme.p.text2, Theme.medium, 1), lp(WRAP, WRAP, l = dp(6)))
                src.background = rounded(Theme.p.surface, 99f, Theme.p.outline)
                hero.addView(src, lp(WRAP, WRAP, t = dp(12)))
            }
        } else {
            hero.addView(Monogram(a, 96).apply { bind(o) })
            hero.addView(a.text(if (imgUrl != null) "Görseller ayarlardan kapatıldı" else "Bu ürünün resmî fotoğrafı yayınlanmamış",
                11.5f, Theme.p.text3, Theme.regular, gravity = Gravity.CENTER), lp(t = dp(12)))
        }
        col.addView(hero)

        val badges = a.hbox()
        badges.addView(a.storeBadge(o.storeId, store?.name ?: o.storeId, small = false))
        badges.addView(a.pill(f.categoryName(o.category), Theme.p.text2, Theme.p.surface2, 12f, Theme.medium), lp(WRAP, WRAP, l = dp(8)))
        col.addView(badges, lp(t = dp(20)))
        if (o.brand != null) col.addView(a.text(TurkishTextUpper(o.brand), 12.5f, Theme.p.text3, Theme.semibold), lp(t = dp(16)))
        col.addView(a.text(o.name, 22f, Theme.p.text, Theme.bold), lp(t = dp(6)))
        if (o.variant != null) col.addView(a.text(o.variant, 14f, Theme.p.text2, Theme.regular), lp(t = dp(6)))
        else if (o.pageSizeText != null) col.addView(a.text(o.pageSizeText, 14f,
            Theme.p.text2, Theme.regular), lp(t = dp(6)))

        // Fiyat kartı
        val priceCard = a.card(dp(18))
        val pr = a.hbox(Gravity.BOTTOM)
        pr.addView(a.text(Money.format(o.price), 30f, Theme.p.text, Theme.bold, 1))
        val pct = Money.declaredDiscountPercent(o.price, o.storeDeclaredOldPrice)
        if (pct != null) { pr.addView(a.flex()); pr.addView(a.pill("%$pct indirim", 0xFFFFFFFF.toInt(), Theme.p.deal, 13f, Theme.bold)) }
        priceCard.addView(pr)
        if (o.storeDeclaredOldPrice != null) {
            priceCard.addView(a.text(Money.format(o.storeDeclaredOldPrice), 14f, Theme.p.text3, Theme.medium).apply {
                paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            }, lp(t = dp(6)))
            priceCard.addView(a.text("Üstü çizili fiyat, marketin sayfasında yazan önceki fiyattır.", 12.5f, Theme.p.text2,
                Theme.regular), lp(t = dp(8)))
        }
        if (o.condition != null) priceCard.addView(a.notice(Icon.TAG, o.condition, Theme.p.warn, Theme.p.warnSoft), lp(t = dp(12)))
        o.pagePriceConflict?.let { pp ->
            priceCard.addView(a.notice(Icon.INFO, "Ürünün kendi sayfasında ${Money.format(pp)} yazıyor. Kasada fark olabilir; " +
                "mağazada kontrol edin.", Theme.p.warn, Theme.p.warnSoft), lp(t = dp(12)))
        }
        col.addView(priceCard, lp(t = dp(18)))

        // Diğer marketlerde: TEMKİNLİ eşleşme (aynı ölçü + ad sözcüklerinin büyük kısmı ortak)
        val others = com.firsat360.app.core.SimilarOffers.find(f.offers, o)
        if (others.isNotEmpty()) {
            val cmp = a.card(dp(16))
            cmp.addView(a.text("Diğer marketlerde", 16f, Theme.p.text, Theme.bold))
            cmp.addView(a.text("Aynı ürün ve aynı ambalaj ölçüsü", 12f, Theme.p.text2, Theme.regular), lp(t = dp(4)))
            for (x in others.take(5)) {
                val r = a.hbox().apply { setPadding(0, dp(12), 0, dp(4)) }
                r.addView(a.storeBadge(x.storeId, f.store(x.storeId)?.name ?: x.storeId, small = false))
                r.addView(a.text(x.displayTitle, 13.5f, Theme.p.text, Theme.medium, 1), lp(0, WRAP, l = dp(10), r = dp(8), weight = 1f))
                val diff = x.price - o.price
                r.addView(a.text(Money.format(x.price), 15f, if (diff < 0) Theme.p.ok else Theme.p.text, Theme.bold, 1))
                r.onTap { a.openOffer(x.id) }
                cmp.addView(r)
                if (Math.abs(diff) >= 0.01) cmp.addView(a.text(if (diff < 0) "${Money.format(-diff)} daha ucuz" else
                    "${Money.format(diff)} daha pahalı", 11.5f, if (diff < 0) Theme.p.ok else Theme.p.text3, Theme.medium, 1, Gravity.END))
            }
            col.addView(cmp, lp(t = dp(14)))
        }

        // Alışveriş listesi
        val inList = a.user.inList(o.id)
        col.addView(a.button(if (inList) "Alışveriş listende" else "Alışveriş listesine ekle", if (inList) Icon.CHECK else Icon.LIST,
            !inList) {
            if (a.user.addToList(o.displayTitle, o.storeId, o.id, o.price)) a.toast("Alışveriş listesine eklendi")
            else a.toast("Bu ürün zaten listenizde")
            a.refreshCurrent()
        }, lp(t = dp(12)))

        // Geçerlilik (tek satır)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, a.today)
        val info = a.card(dp(16))
        info.addView(a.infoRow(Icon.CALENDAR, if (catalog != null) catalog.title else "Geçerlilik",
            v.label + " · stoklarla sınırlı", validityColor(v.kind).first))
        col.addView(info, lp(t = dp(14)))

        // Fiyat geçmişi (yalnızca değişim varsa)
        val points = o.history
        if (points.size >= 2 && points.map { it.price }.distinct().size > 1) {
            val hist = a.card(dp(16))
            val trend = PriceTrend.of(points)
            hist.addView(a.text("Fiyat geçmişi", 16f, Theme.p.text, Theme.bold))
            hist.addView(a.text(trend.summary, 13.5f, when (trend.direction) {
                -1 -> Theme.p.ok; 1 -> Theme.p.danger; else -> Theme.p.text2 }, Theme.semibold), lp(t = dp(6)))
            hist.addView(Sparkline(a, points.map { it.price }), lp(MATCH, dp(80), t = dp(10)))
            col.addView(hist, lp(t = dp(14)))
        }

        val url = o.productUrl ?: o.sourceUrl
        col.addView(a.button("${store?.name ?: "Market"} sayfasında gör", Icon.GLOBE, true) { a.openWeb(url, store?.name ?: "") },
            lp(t = dp(22)))
        col.addView(a.text(Uri.parse(url).host ?: url, 12f, Theme.p.text3, Theme.regular, 1, Gravity.CENTER), lp(t = dp(10)))
        col.addView(a.button("Hatalı bilgi bildir", Icon.INFO, false) { reportProblem(a, o, store?.name) }, lp(t = dp(18)))
        col.addView(a.text("Yanlış görsel, fiyat veya ad görürseniz bildirin; ürün resmî sayfasından otomatik yeniden " +
            "kontrol edilir. Bildirim GitHub üzerinden herkese açık yazılır; kişisel bilgi eklemeyin.", 11.5f, Theme.p.text3,
            Theme.regular, gravity = Gravity.CENTER), lp(t = dp(8)))

        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }

    @Suppress("FunctionName")
    private fun TurkishTextUpper(s: String) = com.firsat360.app.core.TurkishText.upper(s)

}

class CatalogScreen(a: MainActivity, private val catalogId: String) : Screen(a) {
    override val tab: Tab get() = Tab.STORES
    private var category: String? = null

    override fun create(): View {
        val f = a.feed
        val c = f.catalog(catalogId) ?: return a.emptyState(Icon.CATALOG, "Katalog bulunamadı", "")
        val store = f.store(c.storeId)
        val all = f.offers.filter { it.catalogId == c.id }
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar(store?.name ?: ""))
        val list = a.offerListView()
        val header = a.vbox()
        val hero = a.vbox(dp(20)).apply {
            background = gradient(intArrayOf(Theme.store(c.storeId), Theme.blend(Theme.store(c.storeId), 0xFF000000.toInt(), 0.25f)), 24f)
        }
        val onS = Theme.onStore(c.storeId)
        hero.addView(a.text(store?.name ?: "", 13f, onS, Theme.semibold))
        hero.addView(a.text(c.title, 24f, onS, Theme.bold, 2), lp(t = dp(6)))
        val v = Dates.status(c.validFrom, c.validTo, a.today)
        hero.addView(a.pill(v.label, onS, Theme.tint(onS, 0.18f), 12f), lp(WRAP, WRAP, t = dp(12)))
        hero.addView(a.text(listOfNotNull(if (all.isNotEmpty()) "${all.size} ürün" else null,
            if (c.hasPages) "${c.pages.size} sayfa" else null, if (c.hasPdf) "PDF katalog" else null,
            "güncellendi ${Dates.relative(c.lastCheckedAt ?: c.observedAt, a.now)}").joinToString(" · "),
            12.5f, Theme.tint(onS, 0.85f), Theme.medium), lp(t = dp(12)))
        header.addView(hero, lp(l = dp(20), r = dp(20), t = dp(4)))
        if (c.hasPdf) {
            header.addView(a.button("Kataloğu aç (PDF)", Icon.PDF, true) { a.push(PdfScreen(a, c.id)) },
                lp(l = dp(20), r = dp(20), t = dp(14)))
            header.addView(a.button("Resmî sayfada aç", Icon.GLOBE, false) { a.openWeb(c.officialUrl, c.title) },
                lp(l = dp(20), r = dp(20), t = dp(8)))
        } else if (c.hasPages && com.firsat360.app.data.ImageLoader.enabled) {
            header.addView(a.button("Katalog sayfalarını incele (${c.pages.size})", Icon.CATALOG, true) { a.openReader(c.id) },
                lp(l = dp(20), r = dp(20), t = dp(14)))
            header.addView(a.button("Resmî sayfada aç", Icon.GLOBE, false) { a.openWeb(c.officialUrl, c.title) },
                lp(l = dp(20), r = dp(20), t = dp(8)))
        } else {
            header.addView(a.button("Resmî kataloğu incele", Icon.CATALOG, true) { a.openWeb(c.officialUrl, c.title) },
                lp(l = dp(20), r = dp(20), t = dp(14)))
        }
        if (c.hasPages || c.hasPdf) {
            val saved = com.firsat360.app.data.OfflineStore.isSaved(a, c.id)
            header.addView(a.button(if (saved) "Çevrim dışı kayıtlı" else "Çevrim dışı kaydet", if (saved) Icon.CHECK else Icon.DOWNLOAD,
                false) { if (!saved) a.saveOffline(c) else a.toast("Ayarlar › Depolama'dan silebilirsiniz") },
                lp(l = dp(20), r = dp(20), t = dp(8)))
        }
        val chips = a.hbox().apply { setPadding(dp(20), dp(16), dp(8), dp(10)) }
        chips.addView(a.chip("Tümü", category == null) { category = null; a.refreshCurrent() }, lp(WRAP, WRAP, r = dp(8)))
        for (k in Search.categoriesIn(all)) chips.addView(a.chip(f.categoryName(k), category == k) { category = k; a.refreshCurrent() }, lp(WRAP, WRAP, r = dp(8)))
        header.addView(a.hscroll(chips))
        list.addHeaderView(header, null, false)
        list.adapter = OfferAdapter(a, Search.filter(all, OfferQuery(category = category, sort = SortMode.NAME)))
        outer.addView(list, lp(MATCH, 0, weight = 1f))
        return outer
    }
}

/** Resmî sayfaları uygulama içinde gösterir (katalog görselini inceleme). İçerik kopyalanmaz, kaynağında açılır. */
/** Alan adı ailesi: "cdn2.a101.com.tr" ve "www.a101.com.tr" → "a101.com.tr" (iki parçalı Türk uzantıları dahil). */
fun siteFamily(host: String?): String? {
    val p = host?.lowercase()?.split('.')?.filter { it.isNotEmpty() } ?: return null
    if (p.size < 2) return host
    val twoLevel = p.size >= 3 && p[p.size - 2] in setOf("com", "net", "org", "gen", "biz", "info", "web", "av", "bel")
    return p.takeLast(if (twoLevel) 3 else 2).joinToString(".")
}

class WebScreen(a: MainActivity, private val url: String, private val title: String,
                private val catalog: Boolean = false, private val storeId: String? = null,
                /** Köprü okuma profili (assets/bridge/<profile>.js) ve izinli alan adı aileleri (feed client_bridge). */
                private val profile: String = "generic", private val allowedHosts: List<String> = emptyList()) : Screen(a) {
    override val tab: Tab get() = Tab.STORES
    private var web: WebView? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun create(): View {
        val outer = a.vbox()
        outer.setBackgroundColor(if (catalog) 0xFF0B0D12.toInt() else Theme.p.bg)
        val host = if (catalog) "Resmî katalog · iki parmakla yakınlaştırabilirsiniz" else Uri.parse(url).host ?: ""
        val bar = a.hbox().apply {
            setPadding(dp(12), dp(10), dp(12), dp(8))
            addView(a.iconButton(Icon.CLOSE, desc = "Kapat") { a.onBackPressedCompat() })
            val t = a.vbox()
            t.addView(a.text(title, 15f, if (catalog) 0xFFF2F3F7.toInt() else Theme.p.text, Theme.semibold, 1))
            t.addView(a.hbox().apply {
                addView(IconView(a, Icon.SHIELD, Theme.p.ok, 14))
                addView(a.text(host, 12f, if (catalog) 0xB3F2F3F7.toInt() else Theme.p.text2, Theme.medium, 1), lp(WRAP, WRAP, l = dp(4)))
            }, lp(t = dp(2)))
            addView(t, lp(0, WRAP, l = dp(12), weight = 1f))
            addView(a.iconButton(Icon.EXTERNAL, desc = "Tarayıcıda aç") { a.openExternal(web?.url ?: url) })
        }
        outer.addView(bar)
        val progress = ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; progressTintList = android.content.res.ColorStateList.valueOf(Theme.p.accent)
        }
        outer.addView(progress, lp(MATCH, dp(3)))
        val w = WebView(a).apply {
            // Yalnızca marketlerin resmî sayfaları için: JS gerekli; dosya/içerik erişimi, karışık içerik ve
            // JavaScript köprüsü kapalı (addJavascriptInterface kullanılmaz).
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
            settings.javaScriptCanOpenWindowsAutomatically = false
            settings.setSupportMultipleWindows(false)
            settings.setGeolocationEnabled(false)
            if (android.os.Build.VERSION.SDK_INT >= 26) settings.safeBrowsingEnabled = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.loadWithOverviewMode = true
            settings.useWideViewPort = true
            setWebViewClient(object : WebViewClient() {
                // API 24+ cihazlarda yeni imzanın varsayılan uygulaması bu yöntemi çağırır.
                @Deprecated("WebViewClient API")
                override fun shouldOverrideUrlLoading(view: WebView?, u: String?): Boolean {
                    val s = Uri.parse(u ?: return true).scheme?.lowercase() ?: return true
                    if (s == "https") return false
                    if (s == "http") { view?.loadUrl(u.replaceFirst("http://", "https://")); return true }  // HTTPS'e yükselt
                    // tel:, intent:, file:, javascript: vb. WebView içinde açılmaz; yalnızca güvenli şemalar dışarıda
                    if (s == "tel" || s == "mailto" || s == "market") a.openExternal(u)
                    return true
                }
                override fun onPageStarted(view: WebView?, u: String?, favicon: Bitmap?) { progress.visibility = View.VISIBLE }
                override fun onPageFinished(view: WebView?, u: String?) {
                    progress.visibility = View.GONE
                    if (catalog && view != null) startExtraction(view)
                }
            })
            setWebChromeClient(object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, p: Int) { progress.progress = p }
            })
            if (this@WebScreen.url.startsWith("https://")) loadUrl(this@WebScreen.url)
            else loadUrl(this@WebScreen.url.replaceFirst("http://", "https://"))  // DİKKAT: apply içinde "url" WebView.getUrl() olurdu (null)
        }
        web = w
        val frame = FrameLayout(a)
        frame.addView(w, FrameLayout.LayoutParams(MATCH, MATCH))
        webFrame = frame
        if (catalog && storeId != null) {
            overlay = extractionOverlay()
            frame.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        outer.addView(frame, lp(MATCH, 0, weight = 1f))
        return outer
    }

    // ------------------------------------------------------------------ cihazda resmî sayfadan broşür alma
    //
    // Akış (kullanıcı market kartına dokunduğunda başlar):
    //  1. Resmî sayfa bu WebView'de, kullanıcının kendi bağlantısıyla açılır; üstünde yerel bir yükleme katmanı
    //     ("Resmî siteden güncel broşürler alınıyor…") ve "Sayfayı göster" düğmesi vardır (kullanıcı her an siteyi görebilir).
    //  2. Sayfa yüklenince assets/bridge/common.js + <profil>.js çalıştırılır: tembel yüklenen görseller için sayfa adım adım kaydırılır, ardından
    //     büyük ve dikey (broşür/afiş sayfası oranında) görsellerin en yüksek çözünürlüklü adresi toplanır.
    //  3. Görsel sayısı art arda iki ölçümde sabitse (≥2 sayfa) WebView kapanır ve yerel tam ekran okuyucu açılır.
    //  4. Bot sınaması / doğrulama sayfası görülürse katman kalkar: kullanıcı adımı KENDİSİ tamamlar (otomatik çözülmez);
    //     sayfa yeniden yüklenince alma kaldığı yerden sürer. 15 sn içinde broşür bulunamazsa resmî sayfa olduğu gibi kalır.
    //
    // Bağ: evaluateJavascript (tek yönlü, sonuç dönüşü). addJavascriptInterface/postMessage köprüsü KULLANILMAZ:
    // üçüncü taraf sayfaya uygulama içine çağrı yapabileceği bir yüzey açılmaz. Görseller kopyalanmaz; resmî adresinden yüklenir.

    private var overlay: View? = null
    private var overlayText: android.widget.TextView? = null
    private var pagesBtn: View? = null
    private var webFrame: FrameLayout? = null
    private var collected: List<String> = emptyList()
    private var lastCount = -1
    private var polls = 0
    private var handedOff = false
    private var extracting = false

    private fun extractionOverlay(): View = a.vbox().apply {
        setBackgroundColor(0xF20B0D12.toInt())
        setGravity(Gravity.CENTER)
        isClickable = true  // alttaki sayfaya dokunulmasın (katman görünürken)
        contentDescription = "Resmî siteden güncel broşürler alınıyor"
        addView(ProgressBar(a).apply { isIndeterminate = true }, lp(dp(44), dp(44)))
        overlayText = a.text("Resmî siteden güncel broşürler alınıyor…", 15f, 0xFFF2F3F7.toInt(), Theme.semibold,
            gravity = Gravity.CENTER)
        addView(overlayText, lp(WRAP, WRAP, t = dp(16)))
        addView(a.text(Uri.parse(url).host ?: "", 12.5f, 0xB3F2F3F7.toInt(), Theme.regular, 1, Gravity.CENTER), lp(t = dp(6)))
        addView(a.button("Sayfayı göster", Icon.GLOBE, false) { hideOverlay(null) }, lp(WRAP, WRAP, t = dp(22)))
    }

    private fun hideOverlay(message: String?) {
        overlay?.visibility = View.GONE
        if (message != null) a.toast(message)
    }

    private fun startExtraction(view: WebView) {
        if (handedOff) return
        polls = 0; lastCount = -1
        if (!extracting) { extracting = true; view.postDelayed({ poll(view) }, 1200) }
    }

    private fun poll(view: WebView) {
        if (handedOff || web == null) { extracting = false; return }
        try {
            view.evaluateJavascript(com.firsat360.app.data.BridgeScripts.get(a, profile)) { raw -> onExtracted(view, raw) }
        } catch (e: Exception) { extracting = false; hideOverlay(null) }
    }

    private fun onExtracted(view: WebView, raw: String?) {
        if (handedOff || web == null) return
        val res = try {
            val inner = com.firsat360.app.core.Json.parse(raw ?: "null") as? String
            if (inner == null) null else com.firsat360.app.core.Json.parse(inner) as? Map<*, *>
        } catch (e: Exception) { null }
        polls++
        when (res?.get("type")) {
            "challenge" -> {
                // Doğrulama sayfası: otomatik geçilmez. Kullanıcı görür ve kendisi tamamlar; sayfa yenilenince sürer.
                extracting = false
                hideOverlay("Site bir doğrulama istiyor. Adımı tamamladığınızda broşürler otomatik açılır.")
                return
            }
            "pdf" -> {
                val items = ((res["items"] as? List<*>) ?: emptyList<Any>()).mapNotNull { it as? Map<*, *> }
                    .mapNotNull { m -> val u = m["url"] as? String
                        if (u != null && u.startsWith("https://") && hostAllowed(u)) Triple(u, (m["title"] as? String).orEmpty(), (m["dates"] as? String).orEmpty()) else null }
                    .distinctBy { it.first }
                if (items.isNotEmpty()) { handOffPdf(items); return }
            }
            "navigate" -> {
                val to = res["url"] as? String
                if (to != null && to.startsWith("https://") && hostAllowed(to) && to != view.url && navigations < 2) {
                    navigations++; lastCount = -1; polls = 0
                    overlayText?.text = "Resmî katalog açılıyor…"
                    extracting = false
                    view.loadUrl(to)  // onPageFinished → okuma yeniden başlar
                    return
                }
            }
        }
        val pages = ((res?.get("urls") as? List<*>) ?: emptyList<Any>()).mapNotNull { it as? String }
            .filter { it.startsWith("https://") && hostAllowed(it) }.distinct()
        if (pages.size > collected.size) collected = pages
        // Geçiş: sayfanın sonuna ulaşıldı VE son iki ölçüm aynı (tembel görseller yüklendi) → eksik sayfa kalmaz.
        val atEnd = res?.get("end") == true
        if (pages.size >= 2 && pages.size == lastCount && atEnd) { handOff(pages); return }
        lastCount = pages.size
        overlayText?.text = if (pages.isEmpty()) "Resmî siteden güncel broşürler alınıyor…"
            else "${pages.size} broşür sayfası bulundu…"
        if (pages.size >= 2) showPagesButton(pages)
        if (polls >= MAX_POLLS) {
            extracting = false
            if (pages.size >= 2) { handOff(pages); return }  // süre doldu ama sayfalar var → bulunanlarla aç
            hideOverlay("Bu sayfada broşür görseli bulunamadı; resmî sayfa gösteriliyor.")
            return
        }
        view.postDelayed({ poll(view) }, POLL_MS)
    }

    private var navigations = 0

    /** Görsel/PDF/gezinme hedefi izinli mi: feed'deki alan adı aileleri; yoksa açılan sayfanın kendi ailesi. */
    private fun hostAllowed(u: String): Boolean {
        val fam = siteFamily(Uri.parse(u).host) ?: return false
        val allowed = if (allowedHosts.isNotEmpty()) allowedHosts.mapNotNull { siteFamily(it) }
            else listOfNotNull(siteFamily(Uri.parse(url).host))
        return fam in allowed
    }

    /** PDF broşür(ler): tek ise doğrudan yerel PDF okuyucu; birden çoksa kısa seçim listesi. */
    private fun handOffPdf(items: List<Triple<String, String, String>>) {
        if (handedOff) return
        handedOff = true; extracting = false
        fun open(i: Int) {
            val (u, t, d) = items[i]
            val range = com.firsat360.app.core.TrDates.range(d, a.today)
            val c = com.firsat360.app.core.Catalog("bridge-${storeId ?: "x"}-${u.hashCode().toUInt()}", storeId ?: "",
                t.ifBlank { title }, "pdf", range?.first?.toString(), range?.second?.toString(), url, "client_bridge",
                null, 0, pdfUrl = u)
            a.replaceTop(PdfScreen(a, c.id, c))
        }
        if (items.size == 1) { open(0); return }
        try {
            android.app.AlertDialog.Builder(a).setTitle(title)
                .setItems(items.map { (_, t, d) -> listOf(t, d).filter { it.isNotBlank() }.joinToString(" · ") }.toTypedArray()) { _, i -> open(i) }
                .setOnCancelListener { handedOff = false; hideOverlay(null) }
                .show()
        } catch (e: Exception) { open(0) }
    }

    /** Yerel okuyucuya geçiş: WebView yığından çıkar, yerine okuyucu açılır (geri → bir önceki ekran). */
    private fun handOff(pages: List<String>) {
        if (handedOff) return
        handedOff = true; extracting = false
        collected = pages
        storeId?.let { com.firsat360.app.data.DeviceBrochures.save(a, it, title, url, pages) }
        val ps = pages.mapIndexed { i, u -> com.firsat360.app.core.CatalogPage(u, u, "Sayfa ${i + 1}", url, true) }
        a.replaceTop(CatalogReaderScreen(a, null, 0, null, title, extraPages = ps))
    }

    private fun showPagesButton(pages: List<String>) {
        collected = pages
        pagesBtn?.let { webFrame?.removeView(it) }
        val b = a.button("Sayfa sayfa aç (${pages.size})", Icon.CATALOG, true) { handOff(collected) }
        pagesBtn = b
        webFrame?.addView(b, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL)
            .apply { bottomMargin = dp(20) })
        overlay?.bringToFront()
    }

    companion object {
        private const val POLL_MS = 900L
        private const val MAX_POLLS = 16  // ≈ 15 sn

        // Okuma betikleri: assets/bridge/*.js (React Native bileşeniyle ortak). Testler: backend/tests/test_bridge_scripts.py
    }

    override fun onBack(): Boolean {
        val w = web ?: return false
        if (w.canGoBack()) { w.goBack(); return true }
        return false
    }

    override fun onHide() { web?.stopLoading(); web?.destroy(); web = null }
}

/** Önceden doldurulmuş "[Hatalı veri]" bildirimi (ürün kimliği gövdede `offer:<id>`). Kişisel veri eklenmez. */
fun reportProblem(a: MainActivity, o: com.firsat360.app.core.Offer, storeName: String?) {
    val base = try { a.getString(com.firsat360.app.R.string.report_url).trim() } catch (e: Exception) { "" }
    if (base.isBlank()) { a.toast("Bu derlemede bildirim adresi tanımlı değil"); return }
    val title = "[Hatalı veri] ${storeName ?: o.storeId}: ${o.displayTitle}".take(120)
    val body = "Sorun (görsel / fiyat / ad / tarih):\n\n\n---\noffer:${o.id}\nmağaza: ${o.storeId}\n" +
        "kaynak: ${o.productUrl ?: o.sourceUrl}\nuygulama: ${BuildInfo.VERSION}"
    a.openExternal(Uri.parse(base).buildUpon().appendQueryParameter("title", title)
        .appendQueryParameter("body", body).build().toString())
}

/** Kaynak durumu: mağaza bazlı son başarılı güncelleme, hata ve ürün görseli oranı (feed'den hesaplanır). */
class SourceStatusScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.HOME

    override fun create(): View {
        val f = a.feed
        val now = java.time.OffsetDateTime.now()
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar("Kaynak durumu"))
        val col = a.vbox()
        col.setPadding(dp(20), dp(4), dp(20), dp(36))
        col.addView(a.text("Veriler her gün 08.05 ve 00.05'te marketlerin resmî sayfalarından otomatik güncellenir. " +
            "Son yayın: ${Dates.relative(f.generatedAt, now)}.", 13f, Theme.p.text2, Theme.regular), lp(t = dp(6)))
        val byStore = f.offers.groupBy { it.storeId }
        val catCount = f.catalogs.groupBy { it.storeId }.mapValues { it.value.size }
        for (s in f.stores.sortedWith(compareBy({ statusRank(it.status) }, { it.sort }, { it.name }))) {
            val card = a.card(dp(16))
            val h = a.hbox()
            h.addView(a.storeBadge(s.id, s.name))
            h.addView(a.text(s.name, 15f, Theme.p.text, Theme.semibold, 1), lp(0, WRAP, l = dp(10), weight = 1f))
            val (label, fg, bg) = when (s.status) {
                "ok" -> Triple("Güncel", Theme.p.ok, Theme.p.okSoft)
                "partial" -> Triple("Kısmi", Theme.p.warn, Theme.p.warnSoft)
                "link_only" -> Triple("Bağlantı", Theme.p.text2, Theme.p.surface2)
                "error" -> Triple("Hata", Theme.p.danger, Theme.p.dangerSoft)
                else -> Triple("Bekliyor", Theme.p.text2, Theme.p.surface2)
            }
            h.addView(a.pill(label, fg, bg, 12f, Theme.semibold))
            card.addView(h)
            val offers = byStore[s.id].orEmpty()
            val withImg = offers.count { it.hasImage }
            val lines = mutableListOf("Son başarılı okuma: ${Dates.relative(s.lastSuccessAt, now)}",
                "${catCount[s.id] ?: 0} katalog · ${offers.size} ürün")
            if (offers.isNotEmpty()) lines.add("Ürün görseli: $withImg/${offers.size} (%${withImg * 100 / offers.size})")
            for (l in lines) card.addView(a.text(l, 13f, Theme.p.text2, Theme.regular), lp(t = dp(6)))
            if (s.status == "error" && !s.lastError.isNullOrBlank()) {
                card.addView(a.notice(Icon.INFO, plainError(s.lastError), Theme.p.danger, Theme.p.dangerSoft), lp(t = dp(10)))
            }
            col.addView(card, lp(t = dp(12)))
        }
        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }

    private fun statusRank(s: String) = when (s) { "error" -> 0; "partial" -> 1; "ok" -> 2; else -> 3 }

    /** Teknik hata metnini kullanıcının anlayacağı kısa cümleye çevirir. */
    private fun plainError(e: String): String = when {
        "403" in e || "aşılmaya" in e -> "Market sitesi otomatik erişimi engelliyor; engel aşılmaz. Son kaydedilen veriler gösteriliyor."
        "robots" in e -> "Market sitesi otomatik okumaya izin vermiyor; yalnızca resmî bağlantı gösteriliyor."
        "bulunamadı" in e -> "Resmî sayfada şu an katalog bulunamadı. Her güncellemede yeniden aranıyor."
        else -> "Son okuma başarısız oldu; bir sonraki güncellemede yeniden denenecek."
    }
}

class SettingsScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.HOME

    override fun create(): View {
        val f = a.feed
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar("Ayarlar"))
        val col = a.vbox()
        col.setPadding(dp(20), dp(4), dp(20), dp(36))

        fun section(title: String, icon: Icon): LinearLayout {
            val card = a.card(dp(18))
            val h = a.hbox()
            h.addView(FrameLayout(a).apply {
                background = rounded(Theme.p.accentSoft, 12f)
                addView(IconView(a, icon, Theme.p.accent, 18), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(34), dp(34)))
            h.addView(a.text(title, 16.5f, Theme.p.text, Theme.bold), lp(0, WRAP, l = dp(12), weight = 1f))
            card.addView(h)
            col.addView(card, lp(t = dp(14)))
            return card
        }
        fun toggle(parent: LinearLayout, title: String, sub: String?, on: Boolean, onClick: () -> Unit) {
            val r = a.hbox().apply { setPadding(0, dp(14), 0, dp(2)) }
            val t = a.vbox()
            t.addView(a.text(title, 14.5f, Theme.p.text, Theme.medium))
            if (sub != null) t.addView(a.text(sub, 12.5f, Theme.p.text2, Theme.regular), lp(t = dp(2)))
            r.addView(t, lp(0, WRAP, weight = 1f))
            r.addView(android.widget.Switch(a).apply {
                isChecked = on; setOnClickListener { onClick() }; contentDescription = title
            }, lp(WRAP, WRAP, l = dp(8)))
            parent.addView(r)
        }
        fun valueRow(parent: LinearLayout, title: String, value: String, onClick: (() -> Unit)?) {
            val r = a.hbox().apply { setPadding(0, dp(14), 0, dp(4)) }
            r.addView(a.text(title, 14.5f, Theme.p.text, Theme.medium), lp(0, WRAP, weight = 1f))
            r.addView(a.text(value, 14.5f, if (onClick != null) Theme.p.accent else Theme.p.text2, Theme.semibold, 1))
            if (onClick != null) { r.addView(IconView(a, Icon.CHEVRON, Theme.p.text3, 16), lp(WRAP, WRAP, l = dp(4))); r.onTap(onClick) }
            parent.addView(r)
        }

        // ---- Bildirimler
        val notif = section("Bildirimler", Icon.BELL)
        toggle(notif, "Günün katalogları", "Yeni katalog yayınlandığında günde tek bir özet", a.user.notifyCatalogs) {
            a.enableNotifications(!a.user.notifyCatalogs)
        }
        if (a.user.notifyCatalogs) {
            valueRow(notif, "Bildirim saati", minuteLabel(a.user.notifyMinute)) {
                try {
                    android.app.TimePickerDialog(a, { _, h, m ->
                        a.user.notifyMinute = h * 60 + m
                        com.firsat360.app.data.Notifier.schedule(a)
                        a.toast("Bildirim saati ${minuteLabel(h * 60 + m)}")
                        a.refreshCurrent()
                    }, a.user.notifyMinute / 60, a.user.notifyMinute % 60, true).show()
                } catch (e: Exception) { }
            }
            if (a.repo.feedUrl.isBlank()) {
                notif.addView(a.notice(Icon.INFO, "Bu derlemede canlı güncelleme adresi tanımlı değil; günlük bildirimler " +
                    "canlı sürümde çalışır.", Theme.p.warn, Theme.p.warnSoft), lp(t = dp(12)))
            } else if (!com.firsat360.app.data.Notifier.permitted(a)) {
                notif.addView(a.notice(Icon.INFO, "Bildirim izni kapalı. Bildirim alabilmek için Android ayarlarından " +
                    "FIRSAT360 bildirimlerine izin verin.", Theme.p.warn, Theme.p.warnSoft), lp(t = dp(12)))
                notif.addView(a.button("Bildirim ayarlarını aç", Icon.SETTINGS, false) { a.openNotificationSettings() }, lp(t = dp(8)))
            } else {
                notif.addView(a.text("Telefonunuzun pil tasarrufu bildirimi birkaç dakika geciktirebilir.", 12f, Theme.p.text3,
                    Theme.regular), lp(t = dp(10)))
            }
            notif.addView(a.text("Hangi marketler?", 13.5f, Theme.p.text2, Theme.semibold), lp(t = dp(16)))
            val muted = a.user.mutedStores()
            for (s in f.stores) toggle(notif, s.name, null, s.id !in muted) {
                a.user.toggleMutedStore(s.id); a.refreshCurrent()
            }
        }

        // ---- Depolama
        val st = section("Görseller ve depolama", Icon.BOX)
        toggle(st, "Görselleri göster", "Ürün fotoğrafları ve katalog sayfaları", a.user.showImages) {
            a.user.showImages = !a.user.showImages
            com.firsat360.app.data.ImageLoader.enabled = a.user.showImages
            a.refreshCurrent()
        }
        val usedMb = com.firsat360.app.data.ImageLoader.diskUsageMb() +
            com.firsat360.app.core.DiskBudget.size(com.firsat360.app.data.PdfCache.dir(a)) / 1048576.0
        valueRow(st, "Önbellek", String.format(java.util.Locale.ROOT, "%.0f MB (en fazla %d MB)", usedMb,
            com.firsat360.app.data.ImageLoader.limitBytes / 1048576), null)
        st.addView(a.button("Önbelleği temizle", Icon.TRASH, false) {
            com.firsat360.app.data.ImageLoader.clear()
            com.firsat360.app.data.PdfCache.dir(a).listFiles()?.forEach { it.delete() }
            com.firsat360.app.data.DeviceBrochures.clear(a)
            a.toast("Önbellek temizlendi"); a.refreshCurrent()
        }, lp(t = dp(12)))
        val saved = com.firsat360.app.data.OfflineStore.list(a)
        if (saved.isNotEmpty()) st.addView(a.text("Çevrim dışı kataloglar", 13.5f, Theme.p.text2, Theme.semibold), lp(t = dp(18)))
        for (sv in saved) {
            val r = a.hbox().apply { setPadding(0, dp(10), 0, dp(2)) }
            r.addView(a.storeBadge(sv.storeId, f.store(sv.storeId)?.name ?: sv.storeId))
            r.addView(a.text("${sv.title} · ${String.format(java.util.Locale.ROOT, "%.1f", sv.bytes / 1048576.0)} MB", 13.5f,
                Theme.p.text, Theme.medium, 1), lp(0, WRAP, l = dp(8), weight = 1f))
            r.addView(a.iconButton(Icon.TRASH, Theme.p.danger, desc = "${sv.title} sil") {
                com.firsat360.app.data.OfflineStore.delete(a, sv.catalogId); a.toast("Silindi"); a.refreshCurrent()
            }, lp(dp(40), dp(40), l = dp(8)))
            st.addView(r)
        }

        // ---- Veri
        val data = section("Veri kaynakları", Icon.GLOBE)
        val regionNames = linkedMapOf("istanbul" to "İstanbul", "bursa" to "Bursa", "trakya" to "Trakya")
        valueRow(data, "Bölge (bölgesel broşürler)", regionNames[a.user.region] ?: a.user.region) {
            try {
                val keys = regionNames.keys.toList()
                android.app.AlertDialog.Builder(a).setTitle("Bölgenizi seçin")
                    .setSingleChoiceItems(regionNames.values.toTypedArray(), keys.indexOf(a.user.region).coerceAtLeast(0)) { d, i ->
                        a.user.region = keys[i]; d.dismiss(); a.toast("Bölge: ${regionNames[keys[i]]}"); a.refreshCurrent()
                    }.show()
            } catch (e: Exception) { }
        }
        val errs = f.stores.count { it.status == "error" }
        valueRow(data, "Kaynak durumu", if (errs > 0) "$errs hata" else "Tümü çalışıyor") { a.push(SourceStatusScreen(a)) }

        // ---- Hakkında
        val about = section("Hakkında", Icon.INFO)
        about.addView(a.text("FIRSAT360 ${BuildInfo.VERSION}. Fiyatlar marketlerin resmî sayfalarından otomatik alınır; stok, " +
            "şube ve kampanya koşulları için marketin sayfasını kontrol edin. Marketlerle resmî bir bağımız yoktur; market " +
            "adları yalnızca kaynağı belirtmek için kullanılır. Hesap açılmaz, kişisel veri toplanmaz; favoriler ve " +
            "listeleriniz yalnızca bu cihazda saklanır.", 13f, Theme.p.text2, Theme.regular), lp(t = dp(10)))
        about.addView(a.text("Yazı tipi: Inter — SIL Open Font License 1.1", 12f, Theme.p.text3, Theme.regular), lp(t = dp(10)))

        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }
}

class Sparkline(ctx: android.content.Context, private val values: List<Double>) : View(ctx) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2.5f).toFloat(); color = Theme.p.accent; strokeCap = Paint.Cap.ROUND }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Theme.p.accent }
    override fun onDraw(c: android.graphics.Canvas) {
        if (values.size < 2) return
        val mn = values.min(); val mx = values.max(); val span = if (mx - mn < 0.01) 1.0 else mx - mn
        val pad = dp(6).toFloat()
        val w = width - 2 * pad; val h = height - 2 * pad
        val path = android.graphics.Path()
        values.forEachIndexed { i, v ->
            val x = pad + w * i / (values.size - 1)
            val y = pad + h * (1 - ((v - mn) / span)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            if (i == values.size - 1) c.drawCircle(x, y, dp(4).toFloat(), dot)
        }
        c.drawPath(path, line)
    }
}
