package com.firsat360.app.screens

import android.graphics.drawable.GradientDrawable
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.Catalog
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Dedupe
import com.firsat360.app.core.Digest
import com.firsat360.app.core.Money
import com.firsat360.app.core.Offer
import com.firsat360.app.core.Search
import com.firsat360.app.core.ValidityKind
import com.firsat360.app.ui.*
import java.time.ZonedDateTime

/**
 * Ana sayfa: selamlama ve arama → "Bugün" kahraman kartı → yeni kataloglar (hikâye halkaları) → Günün İndirimleri →
 * haftanın katalogları → mağazalar → kategoriler → öne çıkan fırsatlar.
 */
class HomeScreen(a: MainActivity) : Screen(a) {
    override val tab = Tab.HOME
    private var vitrinRow: LinearLayout? = null
    private var vitrinDots: LinearLayout? = null
    private var vitrinScroll: SnapScroll? = null
    private var shelf: List<com.firsat360.app.core.ShelfEntry> = emptyList()

    override fun create(): View {
        val f = a.feed
        val col = a.vbox()
        col.setPadding(0, 0, 0, dp(28))

        // ---- Üst bölüm: yazı markası, tarih, ayarlar
        val top = a.hbox(Gravity.CENTER_VERTICAL)
        top.setPadding(dp(20), dp(18), dp(12), dp(2))
        val titleCol = a.vbox()
        titleCol.addView(a.text("FIRSAT360", 30f, Theme.p.text, Theme.display, 1).apply { contentDescription = "FIRSAT360" })
        titleCol.addView(a.text("${Dates.long(a.today)}, bu haftanın broşürleri", 13.5f, Theme.p.text2, Theme.medium, 1), lp(t = dp(1)))
        top.addView(titleCol, lp(0, WRAP, weight = 1f))
        top.addView(a.iconButton(Icon.SPARK, Theme.p.onLabel, Theme.p.label, "Fırsat Asistanı") { a.openAssistant() }.apply {
            background = rounded(Theme.p.label, 16f) }, lp(dp(48), dp(48), r = dp(8)))
        top.addView(a.iconButton(Icon.SETTINGS, desc = "Ayarlar") { a.openSettings() })
        col.addView(top)

        // ---- Arama
        val search = a.hbox().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = ripple(surfaceBg(18f), 18f)
            contentDescription = "Ara: ürün, marka veya market"
            addView(IconView(a, Icon.SEARCH, Theme.p.text2, 20))
            addView(a.text("Ürün, marka veya market ara", 15.5f, Theme.p.text3, Theme.regular, 1), lp(0, WRAP, l = dp(12), weight = 1f))
            onTap { a.switchTab(Tab.SEARCH) }
        }
        col.addView(search, lp(l = dp(20), r = dp(20), t = dp(16)))
        // Asistan kısayolu: tek satır, sohbet başlatır
        val ask = a.hbox(Gravity.CENTER_VERTICAL).apply {
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = ripple(rounded(Theme.p.accentSoft, 16f), 16f)
            contentDescription = "Fırsat Asistanı'na sor"
            addView(IconView(a, Icon.SPARK, Theme.p.accent, 18))
            addView(a.text("Asistana sor: \"En ucuz süt nerede?\"", 14f, Theme.p.text, Theme.medium, 1), lp(0, WRAP, l = dp(10), weight = 1f))
            addView(IconView(a, Icon.CHEVRON, Theme.p.text2, 16))
            onTap { a.openAssistant() }
            pressFeedback(0.98f)
        }
        col.addView(ask, lp(l = dp(20), r = dp(20), t = dp(10)))

        // Önceki açılışta kaydedilmiş hata raporu (beta tanılama; kullanıcı paylaşmadıkça gönderilmez)
        com.firsat360.app.data.CrashLog.pending(a)?.let { crash ->
            val box = a.card()
            box.addView(a.notice(Icon.INFO, "Uygulama önceki kullanımda beklenmedik şekilde kapandı. Raporu paylaşırsanız " +
                "sorunu hızla düzeltebiliriz.", Theme.p.warn, Theme.p.warnSoft))
            val btns = a.hbox()
            btns.addView(a.button("Raporu paylaş", Icon.SHARE, true) { a.share(crash) }, lp(0, WRAP, weight = 1f))
            btns.addView(a.button("Kapat", null, false) { com.firsat360.app.data.CrashLog.clear(a); a.refreshCurrent() },
                lp(0, WRAP, l = dp(8), weight = 1f))
            box.addView(btns, lp(t = dp(10)))
            col.addView(box, lp(l = dp(20), r = dp(20), t = dp(14)))
        }

        // Veri tazeliği: güncelleme 36 saatten eskiyse dürüstçe belirt
        val gen = Dates.dateTime(f.generatedAt)
        if (gen != null && java.time.Duration.between(gen, a.now).toHours() >= 36) {
            col.addView(a.notice(Icon.CLOCK, "Fırsatlar ${Dates.relative(f.generatedAt, a.now)} güncellendi. İnternete " +
                "bağlanınca yenilenir; fiyatlar bu arada değişmiş olabilir.", Theme.p.warn, Theme.p.warnSoft),
                lp(l = dp(20), r = dp(20), t = dp(12)))
        }

        // ---- Vitrin: marketlerin güncel broşürleri, kapak büyük; arka planı kapağın kendisinden (içerik renk verir)
        val home = com.firsat360.app.core.HomeCatalogs.build(f, a.user.chainOrder, a.today, a.user.favoriteStores(), a.user.region)
        shelf = home.shelf
        if (shelf.isNotEmpty()) {
            col.addView(a.sectionHeader("Vitrin", "Sırala") { a.push(ShelfOrderScreen(a)) })
            col.addView(buildVitrin())
            col.addView(a.text("Resmî market sayfalarından, ${Dates.relative(f.generatedAt, a.now)} güncellendi", 12.5f,
                Theme.p.text3, Theme.regular, 1, Gravity.CENTER), lp(l = dp(20), r = dp(20), t = dp(10)))
        }

        fun catalogRow(title: String, list: List<Catalog>) {
            if (list.isEmpty()) return
            col.addView(a.sectionHeader(title))
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), dp(4), dp(6), dp(8)) }
            for (c in list.take(12)) row.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today) { a.openCatalog(c.id) })
            col.addView(a.hscroll(row))
        }

        // ---- Günün İndirimleri
        val deals = Dedupe.forLists(f.dealOffers, a.today)
        if (deals.isNotEmpty()) {
            col.addView(dealsHeader())
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), dp(4), dp(8), dp(8)) }
            val favs = a.repo.favorites()
            for (o in deals.take(12)) {
                val card = ProductCard(a)
                card.bind(o, f.store(o.storeId)?.name ?: "", f.catalog(o.catalogId), o.id in favs, a.today)
                card.setOnClickListener { a.openOffer(o.id) }
                card.onFav { a.repo.toggleFavorite(o.id); a.refreshCurrent() }
                row.addView(card, lp(dp(164), MATCH, r = dp(12)))
            }
            if (deals.size > 12) row.addView(seeAll("${deals.size - 12} indirim daha") { a.openDeals() }, lp(dp(120), MATCH, r = dp(12)))
            col.addView(a.hscroll(row))
        }

        catalogRow("Bugün başlayanlar", home.startingToday)
        catalogRow("Bitmek üzere", home.endingSoon)
        if (home.others.isNotEmpty()) {
            col.addView(a.sectionHeader("Diğer kataloglar", "Tümü") { a.openCatalogList(false) })
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), dp(4), dp(6), dp(8)) }
            for (c in home.others.take(12)) row.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today) { a.openCatalog(c.id) })
            col.addView(a.hscroll(row))
        }

        // ---- Marketler: sakin kutucuklar (koyu yüzey, marka renginde monogram)
        col.addView(a.sectionHeader("Marketler", "Tümü") { a.switchTab(Tab.STORES) })
        val favStores = a.user.favoriteStores()
        val byStore = f.catalogs.filter { it.kind != "link_only" && Dates.status(it.validFrom, it.validTo, a.today).kind != ValidityKind.EXPIRED }
            .groupBy { it.storeId }
        val grid = a.vbox().apply { setPadding(dp(16), dp(4), dp(16), 0) }
        val stores = f.stores.sortedWith(compareBy({ if (it.id in favStores) 0 else 1 }, { it.chainRank }, { it.sort }))
        val counts = f.listOffers.groupingBy { it.storeId }.eachCount()
        for (chunk in (stores.map { it as Any } + listOf("add")).chunked(3)) {
            val r = a.hbox(Gravity.TOP)
            for (x in chunk) {
                if (x is String) { r.addView(addTile(), lp(0, MATCH, l = dp(4), r = dp(4), weight = 1f)); continue }
                val s = x as com.firsat360.app.core.Store
                val n = counts[s.id] ?: 0
                val nc = byStore[s.id]?.size ?: 0
                val sub = when {
                    n > 0 -> "$n fırsat"
                    f.clientBridge.containsKey(s.id) -> "Broşür"
                    nc > 0 -> "$nc katalog"
                    else -> "Broşür"
                }
                r.addView(storeTile(s.id, s.name, sub, s.id in favStores) { a.openStore(s.id) }, lp(0, MATCH, l = dp(4), r = dp(4), weight = 1f))
            }
            repeat(3 - chunk.size) { r.addView(a.space(), lp(0, 1, l = dp(4), r = dp(4), weight = 1f)) }
            grid.addView(r, lp(b = dp(8)))
        }
        col.addView(grid)

        // ---- Bildirim önerisi (bir kez)
        if (!a.user.askedNotifications && !a.user.notifyCatalogs) {
            val card = a.card(dp(18))
            val h = a.hbox(Gravity.TOP)
            h.addView(FrameLayout(a).apply {
                background = rounded(Theme.p.accentSoft, 14f)
                addView(IconView(a, Icon.BELL, Theme.p.accent, 20), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(44), dp(44)))
            val t = a.vbox()
            t.addView(a.text("Yeni broşür çıkınca haber verelim", 16.5f, Theme.p.text, Theme.semibold))
            t.addView(a.text("Her sabah ${minuteLabel(a.user.notifyMinute)}'da tek bir özet: hangi markette yeni katalog var.",
                13f, Theme.p.text2, Theme.regular), lp(t = dp(3)))
            h.addView(t, lp(0, WRAP, l = dp(12), weight = 1f))
            card.addView(h)
            val btns = a.hbox()
            btns.addView(a.button("Bildirimleri aç", null, true) { a.user.askedNotifications = true; a.enableNotifications(true) },
                lp(0, WRAP, weight = 1f))
            btns.addView(a.button("Şimdi değil", null, false) { a.user.askedNotifications = true; a.refreshCurrent() },
                lp(0, WRAP, l = dp(8), weight = 1f))
            card.addView(btns, lp(t = dp(14)))
            col.addView(card, lp(l = dp(20), r = dp(20), t = dp(24)))
        }

        col.addView(a.text("Fiyatlar marketlerin resmî sayfalarından otomatik alınır. Fiyat ve stok durumu mağazaya göre " +
            "değişebilir. FIRSAT360'ın marketlerle resmî bir bağı yoktur.", 12f, Theme.p.text3, Theme.regular),
            lp(l = dp(20), r = dp(20), t = dp(32)))

        return ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Theme.p.bg)
            addView(col)
        }
    }

    /** Arka planda bir marketin broşürü okununca: yalnızca o vitrin kartı yenilenir (kaydırma konumu korunur). */
    fun onBrochureUpdated(storeId: String) {
        val row = vitrinRow ?: return
        val i = shelf.indexOfFirst { it.store.id == storeId }
        if (i < 0 || i >= row.childCount) return
        val old = row.getChildAt(i)
        val fresh = vitrinCard(shelf[i])
        fresh.layoutParams = old.layoutParams
        row.removeViewAt(i)
        row.addView(fresh, i)
        fresh.alpha = 0f
        fresh.animate().alpha(1f).setDuration(260).start()
    }

    // ------------------------------------------------------------------ Vitrin

    private fun vitrinWidth(): Int = minOf(dp(250), a.resources.displayMetrics.widthPixels - dp(100))

    private fun buildVitrin(): View {
        val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), dp(6), dp(8), dp(18)) }
        for (e in shelf) row.addView(vitrinCard(e), lp(vitrinWidth(), (vitrinWidth() * 1.32f).toInt(), r = dp(12)))
        vitrinRow = row
        val scroll = SnapScroll(a, dp(20)) { idx -> updateDots(idx) }
        scroll.addView(row)
        vitrinScroll = scroll
        val wrap = a.vbox()
        wrap.addView(scroll)
        val dots = a.hbox(Gravity.CENTER)
        vitrinDots = dots
        wrap.addView(dots, lp(MATCH, WRAP))
        updateDots(0)
        return wrap
    }

    private fun updateDots(idx: Int) {
        val d = vitrinDots ?: return
        d.removeAllViews()
        val n = shelf.size
        if (n <= 1) return
        if (n > 9) {
            d.addView(a.text("${idx + 1} / $n", 12.5f, Theme.p.text3, Theme.medium, 1))
            return
        }
        for (i in 0 until n) {
            val on = i == idx
            d.addView(View(a).apply { background = rounded(if (on) Theme.p.accent else Theme.p.surface2, 3f) },
                LinearLayout.LayoutParams(if (on) dp(18) else dp(6), dp(6)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        d.contentDescription = "Vitrin ${idx + 1} / $n"
    }

    /**
     * Vitrin kartı: büyük kapak; arka planı aynı kapağın bulanıklaştırılmış, koyulaştırılmış hâli (Android 12+ gerçek
     * bulanıklık; eski sürümlerde küçük ölçekten büyütülmüş görsel). Kapak yoksa marka renginde zengin degrade.
     */
    private fun vitrinCard(e: com.firsat360.app.core.ShelfEntry): View {
        val f = a.feed
        val cached = if (e.bridge != null) com.firsat360.app.data.DeviceBrochures.get(a, e.store.id, freshOnly = false) else null
        val c = e.catalog
        val cover = cached?.coverUrl ?: c?.coverUrl ?: c?.pages?.firstOrNull()?.let { it.thumbUrl ?: it.url }
            ?: e.preview?.let { it.coverUrl ?: it.pages.firstOrNull()?.let { p -> p.thumbUrl ?: p.url } }
        val w = vitrinWidth()
        val h = (w * 1.32f).toInt()
        val card = FrameLayout(a).apply {
            layoutParams = LinearLayout.LayoutParams(w, h)
            val sc = Theme.store(e.store.id)
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                intArrayOf(Theme.blend(sc, 0xFF000000.toInt(), 0.15f), Theme.blend(sc, 0xFF000000.toInt(), 0.65f))).apply {
                cornerRadius = dp(26).toFloat() }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(26).toFloat())
            }
            elevation = dp(8).toFloat()
        }
        val showImages = com.firsat360.app.data.ImageLoader.enabled
        if (cover != null && showImages) {
            // Bulanık arka plan
            val bg = NetImage(a, 0f).apply {
                scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
                if (android.os.Build.VERSION.SDK_INT >= 31)
                    setRenderEffect(android.graphics.RenderEffect.createBlurEffect(dp(28).toFloat(), dp(28).toFloat(),
                        android.graphics.Shader.TileMode.CLAMP))
                scaleX = 1.25f; scaleY = 1.25f
            }
            bg.load(cover, if (android.os.Build.VERSION.SDK_INT >= 31) dp(120) else dp(24)) { bg.visibility = View.GONE }
            card.addView(bg, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        // Okunabilirlik: üstten alta koyulaşan perde
        card.addView(View(a).apply {
            background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(0x40000000, 0x26000000, 0xD9000000.toInt()))
        }, FrameLayout.LayoutParams(MATCH, MATCH))
        // Kapak
        val coverW = (w * 0.56f).toInt()
        val coverH = (coverW * 1.38f).toInt()
        val coverBox = FrameLayout(a).apply {
            background = rounded(0x33FFFFFF, 12f)
            elevation = dp(10).toFloat()
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
            }
        }
        val ph = a.vbox().apply { setGravity(Gravity.CENTER) }
        ph.addView(IconView(a, if (cached?.pdfs?.isNotEmpty() == true || c?.hasPdf == true) Icon.PDF else Icon.CATALOG, 0xE6FFFFFF.toInt(), 34))
        ph.addView(a.text(if (e.bridge != null) "Dokunun, resmî sayfadan\nhemen açalım" else "Broşürü açmak için dokunun",
            13.5f, 0xE6FFFFFF.toInt(), Theme.medium, 3, Gravity.CENTER), lp(WRAP, WRAP, t = dp(12)))
        coverBox.addView(ph, FrameLayout.LayoutParams(MATCH, MATCH))
        if (cover != null && showImages) {
            val img = NetImage(a, 0f).apply {
                scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            ph.visibility = View.INVISIBLE
            img.loadResult(cover, dp(520)) { ok ->
                if (ok) { coverBox.background = rounded(0xFFFFFFFF.toInt(), 12f); coverBox.removeView(ph) }
                else { img.visibility = View.GONE; ph.visibility = View.VISIBLE }
            }
            coverBox.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
        }
        card.addView(coverBox, FrameLayout.LayoutParams(coverW, coverH, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = dp(16) })

        // Bilgi satırı: rozet, market adı, durum, "Aç"
        val status = vitrinStatus(e, cached)
        val meta = a.vbox().apply { setPadding(dp(16), 0, dp(14), dp(14)) }
        badgeFor(e, cached)?.let { b -> meta.addView(a.pill(b, Theme.p.onLabel, Theme.p.label, 12f, Theme.semibold), lp(WRAP, WRAP, b = dp(8))) }
        val line = a.hbox(Gravity.BOTTOM)
        val texts = a.vbox()
        texts.addView(a.text(e.store.name, 25f, 0xFFFFFFFF.toInt(), Theme.display, 1))
        texts.addView(a.text(status, 12.5f, 0xD9FFFFFF.toInt(), Theme.medium, 2), lp(t = dp(2)))
        line.addView(texts, lp(0, WRAP, weight = 1f))
        line.addView(a.text("Aç", 15f, Theme.p.onLabel, Theme.displayBold, 1, Gravity.CENTER).apply {
            setPadding(dp(14), dp(8), dp(14), dp(8)); background = rounded(Theme.p.label, 14f)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }, lp(WRAP, WRAP, l = dp(10)))
        meta.addView(line)
        card.addView(meta, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))
        card.contentDescription = "${e.store.name} broşürü. $status. Açmak için dokunun"
        card.onTap { a.openBrochure(e) }
        card.pressFeedback(0.98f)
        return card
    }

    private fun vitrinStatus(e: com.firsat360.app.core.ShelfEntry, cached: com.firsat360.app.data.DeviceBrochures.Entry?): String {
        val c = e.catalog
        val base = when {
            c != null -> listOf(c.title, Dates.status(c.validFrom, c.validTo, a.today).label).distinct().joinToString(", ")
            cached != null && cached.pdfs.isNotEmpty() -> cached.pdfs.first().let { p -> listOf(p.title, p.dates).filter { it.isNotBlank() }.joinToString(", ") }
                .ifBlank { "Güncel broşür" }
            cached != null -> "${cached.pages.size} sayfa, resmî sayfadan"
            e.bridge != null -> "Güncel broşür, resmî sayfadan"
            else -> "Resmî sayfa"
        }
        return base + if (e.others > 0) "\n+${e.others} katalog daha" else ""
    }

    private fun badgeFor(e: com.firsat360.app.core.ShelfEntry, cached: com.firsat360.app.data.DeviceBrochures.Entry?): String? {
        val c = e.catalog
        if (c != null) {
            val k = Dates.status(c.validFrom, c.validTo, a.today).kind
            if (k == ValidityKind.STARTS_TODAY) return "Bugün başladı"
            if (k == ValidityKind.LAST_DAY) return "Son gün"
            if (k == ValidityKind.UPCOMING) return "Yakında"
            val first = Dates.dateTime(c.firstSeenAt)
            if (first != null && java.time.Duration.between(first, java.time.OffsetDateTime.now()).toHours() in -6..48) return "Yeni"
        }
        if (cached != null && System.currentTimeMillis() - cached.savedAt < 24 * 3_600_000L &&
            com.firsat360.app.data.DeviceBrochures.history(a, e.store.id).isNotEmpty()) return "Yeni hafta"
        return null
    }

    private fun dealsHeader(): View = a.vbox().apply {
        addView(a.sectionHeader("Günün indirimleri", "Tümü") { a.openDeals() })
        val end = ZonedDateTime.now(Dates.TR).let { java.time.Duration.between(it, it.toLocalDate().plusDays(1).atStartOfDay(Dates.TR)) }
        addView(a.text("Her gün yeniden seçilir, yenisi ${end.toHours()} sa ${end.toMinutes() % 60} dk sonra", 13f,
            Theme.p.text3, Theme.regular, 1), lp(l = dp(20), r = dp(20), b = dp(8)))
    }

    private fun seeAll(label: String, onClick: () -> Unit): View = a.vbox().apply {
        setGravity(Gravity.CENTER)
        background = ripple(surfaceBg(20f), 20f)
        addView(FrameLayout(a).apply {
            background = rounded(Theme.p.accentSoft, 99f)
            addView(IconView(a, Icon.CHEVRON, Theme.p.accent, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(a.text(label, 14f, Theme.p.text, Theme.medium, 2, Gravity.CENTER), lp(WRAP, WRAP, t = dp(10)))
        onTap(onClick)
    }

    private fun storeTile(id: String, name: String, sub: String, fav: Boolean, onClick: () -> Unit): View = a.vbox().apply {
        setPadding(dp(12), dp(12), dp(10), dp(12))
        background = ripple(surfaceBg(18f), 18f)
        contentDescription = (if (fav) "Favori market, " else "") + "$name, $sub"
        val top = a.hbox()
        top.addView(a.storeLogo(id, name, 42), LinearLayout.LayoutParams(dp(42), dp(42)))
        top.addView(a.flex())
        if (fav) top.addView(IconView(a, Icon.STAR_FILLED, Theme.p.accent, 16))
        addView(top)
        addView(a.text(name, 14.5f, Theme.p.text, Theme.semibold, 1), lp(t = dp(10)))
        addView(a.text(sub, 12f, Theme.p.text3, Theme.medium, 1), lp(t = dp(1)))
        onTap(onClick)
        pressFeedback()
    }

    /** "Market ekle": kullanıcı kendi marketini broşür sayfası adresiyle ekler. */
    private fun addTile(): View = a.vbox().apply {
        setPadding(dp(12), dp(12), dp(10), dp(12))
        background = ripple(rounded(0, 18f, Theme.p.outline, 1.5f), 18f)
        contentDescription = "Market ekle: broşür sayfasının adresiyle"
        addView(FrameLayout(a).apply {
            background = rounded(Theme.p.accentSoft, 12f)
            addView(IconView(a, Icon.PLUS, Theme.p.accent, 20), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(42), dp(42)))
        addView(a.text("Market ekle", 14.5f, Theme.p.text, Theme.semibold, 1), lp(t = dp(10)))
        addView(a.text("Adresiyle", 12f, Theme.p.text3, Theme.medium, 1), lp(t = dp(1)))
        onTap { a.push(AddStoreScreen(a)) }
        pressFeedback()
    }
}

/** Kartlara yapışan yatay kaydırma: bırakınca en yakın karta hizalanır; [onPage] etkin kart değişince. */
class SnapScroll(ctx: android.content.Context, private val startPad: Int, private val onPage: (Int) -> Unit) :
    android.widget.HorizontalScrollView(ctx) {
    private var page = 0
    private var flung = false
    init { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; clipToPadding = false }

    private val maxX: Int get() = ((getChildAt(0)?.width ?: 0) - width).coerceAtLeast(0)
    private fun targetX(i: Int): Int { val r = row() ?: return 0; return (r.getChildAt(i).left - startPad).coerceIn(0, maxX) }

    private fun row(): android.view.ViewGroup? = getChildAt(0) as? android.view.ViewGroup

    private fun nearest(): Int {
        val r = row() ?: return 0
        if (r.childCount > 0 && scrollX >= maxX - dp(8)) return r.childCount - 1  // sona dayandı: son kart etkin
        var best = 0; var bestD = Int.MAX_VALUE
        for (i in 0 until r.childCount) {
            val d = kotlin.math.abs(targetX(i) - scrollX)
            if (d < bestD) { bestD = d; best = i }
        }
        return best
    }

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        val n = nearest()
        if (n != page) { page = n; onPage(n) }
    }

    override fun fling(velocityX: Int) {
        val r = row() ?: return super.fling(velocityX)
        val target = (nearest() + if (velocityX > 1500) 1 else if (velocityX < -1500) -1 else 0).coerceIn(0, r.childCount - 1)
        flung = true
        smoothScrollTo(targetX(target), 0)
    }

    @android.annotation.SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: android.view.MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        if (ev.actionMasked == android.view.MotionEvent.ACTION_DOWN) flung = false
        if (ev.actionMasked == android.view.MotionEvent.ACTION_UP || ev.actionMasked == android.view.MotionEvent.ACTION_CANCEL) {
            postDelayed({
                if (flung) return@postDelayed  // savurma kendi hedefine gidiyor; geri çekme
                val r = row() ?: return@postDelayed
                val target = nearest()
                if (target < r.childCount) smoothScrollTo(targetX(target), 0)
            }, 60)
        }
        return handled
    }
}

fun minuteLabel(m: Int): String = String.format(java.util.Locale.ROOT, "%02d.%02d", m / 60, m % 60)

/** Broşürü en iyi biçimde açar: sayfa görselleri → yakınlaştırılabilir okuyucu; PDF → PDF; diğerleri → resmî sayfa. */
fun MainActivity.openBrochure(e: com.firsat360.app.core.ShelfEntry) {
    val c = e.catalog
    when {
        c != null && c.hasPages && com.firsat360.app.data.ImageLoader.enabled -> openReader(c.id)
        c != null && c.hasPdf -> push(PdfScreen(this, c.id))
        c != null && !c.webOnly -> openCatalog(c.id)
        else -> openBridge(e.store, e.bridge, c?.officialUrl)
    }
}

/**
 * İstemci katalog köprüsü: market kartına dokunulunca. Bu cihazda son 12 saatte resmî sayfadan okunmuş broşür varsa
 * doğrudan yerel okuyucu (sayfalar) veya PDF okuyucu; yoksa resmî sayfa yükleme katmanıyla açılır, profil betiği
 * broşürü okur ve yerel okuyucuya geçer.
 */
fun MainActivity.openBridge(store: com.firsat360.app.core.Store, bridge: com.firsat360.app.core.BridgeEntry?, fallbackUrl: String? = null) {
    val cached = com.firsat360.app.data.DeviceBrochures.get(this, store.id)
    if (cached != null) {
        if (cached.pdfs.isNotEmpty()) { openDevicePdf(store, cached); return }
        if (com.firsat360.app.data.ImageLoader.enabled) {
            val ps = cached.pages.mapIndexed { i, u -> com.firsat360.app.core.CatalogPage(u, u, "Sayfa ${i + 1}", cached.sourceUrl, true) }
            push(CatalogReaderScreen(this, null, 0, null, "${store.name} broşür", extraPages = ps))
            return
        }
    }
    push(WebScreen(this, bridge?.url ?: fallbackUrl ?: store.brochureUrl ?: store.catalogUrl, "${store.name} broşür",
        catalog = true, storeId = store.id, profile = bridge?.profile ?: "generic",
        allowedHosts = bridge?.allowedHosts ?: emptyList()))
}

/** Cihazda okunmuş PDF broşür(ler): tek ise doğrudan, birden çoksa kısa seçim. */
fun MainActivity.openDevicePdf(store: com.firsat360.app.core.Store, e: com.firsat360.app.data.DeviceBrochures.Entry) {
    fun open(i: Int) = push(PdfScreen(this, "", com.firsat360.app.data.BridgeCatalogs.pdf(store.id, e.pdfs[i], e.sourceUrl,
        "${store.name} broşür", today)))
    if (e.pdfs.size == 1) { open(0); return }
    try {
        android.app.AlertDialog.Builder(this).setTitle("${store.name} broşürleri")
            .setItems(e.pdfs.map { p -> listOf(p.title, p.dates).filter { it.isNotBlank() }.joinToString(", ").ifBlank { "Broşür" } }
                .toTypedArray()) { _, i -> open(i) }.show()
    } catch (ex: Exception) { open(0) }
}

/** Raf sırası: yukarı/aşağı ile taşınır, "Varsayılan" büyüklük sırasına döner. Yalnızca cihazda saklanır. */
class ShelfOrderScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.HOME
    private var order: MutableList<String> = com.firsat360.app.core.Shelf.order(a.feed, a.user.chainOrder).map { it.id }.toMutableList()

    override fun create(): View {
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar("Market sırası"))
        val col = a.vbox()
        col.setPadding(dp(20), dp(4), dp(20), dp(36))
        col.addView(a.text("Ana sayfadaki vitrinde marketler bu sırayla görünür.", 13.5f, Theme.p.text2, Theme.regular))
        order.forEachIndexed { i, id ->
            val s = a.feed.store(id) ?: return@forEachIndexed
            val r = a.hbox().apply { setPadding(dp(14), dp(8), dp(8), dp(8)); background = surfaceBg(18f) }
            r.addView(a.text("${i + 1}", 14f, Theme.p.text3, Theme.semibold), lp(dp(26), WRAP))
            r.addView(a.storeBadge(s.id, s.name))
            r.addView(a.text(s.name, 15f, Theme.p.text, Theme.medium, 1), lp(0, WRAP, l = dp(10), weight = 1f))
            r.addView(a.iconButton(Icon.CHEVRON_UP, if (i > 0) Theme.p.text else Theme.p.text3, desc = "${s.name} yukarı") { move(i, -1) },
                lp(dp(48), dp(48)))
            r.addView(a.iconButton(Icon.CHEVRON_DOWN, if (i < order.size - 1) Theme.p.text else Theme.p.text3,
                desc = "${s.name} aşağı") { move(i, 1) }, lp(dp(48), dp(48), l = dp(4)))
            col.addView(r, lp(t = dp(10)))
        }
        col.addView(a.button("Varsayılan sıraya dön", null, false) {
            a.user.chainOrder = emptyList(); order = com.firsat360.app.core.Shelf.order(a.feed, emptyList()).map { it.id }.toMutableList()
            a.toast("Varsayılan sıra"); a.refreshCurrent()
        }, lp(t = dp(18)))
        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }

    private fun move(i: Int, d: Int) {
        val j = i + d
        if (j !in order.indices) return
        val t = order[i]; order[i] = order[j]; order[j] = t
        a.user.chainOrder = order
        a.refreshCurrent()
    }
}
