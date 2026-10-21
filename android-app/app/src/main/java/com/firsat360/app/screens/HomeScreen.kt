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

    override fun create(): View {
        val f = a.feed
        val col = a.vbox()
        col.setPadding(0, 0, 0, dp(32))

        // ---- Üst bölüm: yazı markası (tabela harfi), tarih, ayarlar
        val top = a.hbox(Gravity.TOP)
        top.setPadding(dp(20), dp(20), dp(16), dp(2))
        val titleCol = a.vbox()
        titleCol.addView(a.text("FIRSAT360", 36f, Theme.p.text, Theme.display, 1).apply {
            letterSpacing = -0.01f; contentDescription = "FIRSAT360" }, lp(WRAP, WRAP))
        titleCol.addView(a.text(Dates.long(a.today), 14f, Theme.p.text2, Theme.medium, 1), lp(t = dp(2)))
        top.addView(titleCol, lp(0, WRAP, weight = 1f))
        top.addView(a.iconButton(Icon.SETTINGS, desc = "Ayarlar") { a.openSettings() }, lp(dp(44), dp(44), t = dp(4)))
        col.addView(top)

        // ---- Arama: kâğıt beyazı, mürekkep çizgili düz alan
        val search = a.hbox().apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            background = ripple(rounded(Theme.p.surface, 8f, Theme.p.text, 1.5f), 8f)
            contentDescription = "Ara: ürün, marka veya market"
            addView(IconView(a, Icon.SEARCH, Theme.p.text, 20))
            addView(a.text("Ürün, marka veya market ara", 15.5f, Theme.p.text3, Theme.regular, 1), lp(0, WRAP, l = dp(12), weight = 1f))
            onTap { a.switchTab(Tab.SEARCH) }
        }
        col.addView(search, lp(l = dp(20), r = dp(20), t = dp(16)))

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

        val deals = f.dealOffers

        // Veri tazeliği: güncelleme 36 saatten eskiyse dürüstçe belirt
        val gen = Dates.dateTime(f.generatedAt)
        if (gen != null && java.time.Duration.between(gen, a.now).toHours() >= 36) {
            col.addView(a.notice(Icon.CLOCK, "Fırsatlar ${Dates.relative(f.generatedAt, a.now)} güncellendi. İnternete " +
                "bağlanınca yenilenir; fiyatlar bu arada değişmiş olabilir.", Theme.p.warn, Theme.p.warnSoft),
                lp(l = dp(20), r = dp(20), t = dp(12)))
        }

        // ---- Market broşürleri: Türkiye'nin büyük zincirleri her zaman, kullanıcının sırasıyla
        val home = com.firsat360.app.core.HomeCatalogs.build(f, a.user.chainOrder, a.today, a.user.favoriteStores(), a.user.region)
        val shelf = home.shelf
        if (shelf.isNotEmpty()) {
            col.addView(a.sectionHeader("Broşürlük", "Sırala") { a.push(ShelfOrderScreen(a)) })
            col.addView(a.shelfRack(shelf))
            col.addView(a.text("Resmî market sayfalarından, ${Dates.relative(f.generatedAt, a.now)} güncellendi",
                12.5f, Theme.p.text3, Theme.regular, 1), lp(l = dp(20), r = dp(20), t = dp(10)))
        }
        fun catalogRow(title: String, list: List<Catalog>) {
            if (list.isEmpty()) return
            col.addView(a.sectionHeader(title))
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), dp(4)) }
            for (c in list.take(12)) row.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today) { a.openCatalog(c.id) })
            col.addView(a.hscroll(row))
        }
        catalogRow("Bugün başlayanlar", home.startingToday)
        catalogRow("Süresi dolmak üzere", home.endingSoon)

        // ---- Günün İndirimleri
        if (deals.isNotEmpty()) {
            col.addView(dealsHeader())
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), dp(4)) }
            val favs = a.repo.favorites()
            for (o in deals.take(12)) {
                val card = ProductCard(a)
                card.bind(o, f.store(o.storeId)?.name ?: "", f.catalog(o.catalogId), o.id in favs, a.today)
                card.setOnClickListener { a.openOffer(o.id) }
                card.onFav { a.repo.toggleFavorite(o.id); a.refreshCurrent() }
                row.addView(card, lp(dp(168), MATCH, r = dp(12)))
            }
            if (deals.size > 12) row.addView(seeAll("${deals.size - 12} indirim daha") { a.openDeals() }, lp(dp(120), MATCH, r = dp(12)))
            col.addView(a.hscroll(row))
            col.addView(a.text(f.dailyDeals?.note ?: "", 12.5f, Theme.p.text3, Theme.regular),
                lp(l = dp(20), r = dp(20), t = dp(10)))
        }

        // ---- Diğer kataloglar: yukarıdaki bölümlerde gösterilmeyenler (aynı katalog ana sayfada iki kez görünmez)
        if (home.others.isNotEmpty()) {
            col.addView(a.sectionHeader("Diğer kataloglar", "Tümü") { a.openCatalogList(false) })
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), dp(4)) }
            for (c in home.others.take(12)) row.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today) { a.openCatalog(c.id) })
            col.addView(a.hscroll(row))
        }

        // ---- Marketler
        col.addView(a.sectionHeader("Marketler", "Tümü") { a.switchTab(Tab.STORES) })
        val favStores = a.user.favoriteStores()
        val byStore = f.catalogs.filter { it.kind != "link_only" && Dates.status(it.validFrom, it.validTo, a.today).kind != ValidityKind.EXPIRED }
            .groupBy { it.storeId }
        val grid = a.vbox().apply { setPadding(dp(16), 0, dp(16), 0) }
        // Market tabelaları: marka renginde blok, kısa ad sol altta
        val stores = f.stores.sortedWith(compareBy({ if (it.id in favStores) 0 else 1 }, { it.sort }))
        for (chunk in stores.chunked(4)) {
            val r = a.hbox(Gravity.TOP)
            for (s in chunk) {
                val n = f.offers.count { it.storeId == s.id }
                val nc = byStore[s.id]?.size ?: 0
                r.addView(storeTile(s.id, s.name, if (n > 0) "$n fırsat" else if (nc > 0) "$nc katalog" else "",
                    s.id in favStores) { a.openStore(s.id) }, lp(0, WRAP, l = dp(4), r = dp(4), weight = 1f))
            }
            repeat(4 - chunk.size) { r.addView(a.space(), lp(0, 1, l = dp(4), r = dp(4), weight = 1f)) }
            grid.addView(r, lp(b = dp(8)))
        }
        col.addView(grid)

        // ---- Bildirim önerisi (bir kez)
        if (!a.user.askedNotifications && !a.user.notifyCatalogs) {
            val card = a.card(dp(18))
            val h = a.hbox(Gravity.TOP)
            h.addView(FrameLayout(a).apply {
                background = rounded(Theme.p.label, 4f)
                addView(IconView(a, Icon.BELL, Theme.p.onLabel, 20), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(44), dp(36)))
            val t = a.vbox()
            t.addView(a.text("Yeni broşür çıkınca haber verelim", 20f, Theme.p.text, Theme.displayBold))
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

    private fun dealsHeader(): View = a.vbox().apply {
        addView(a.sectionHeader("Günün indirimleri", "Tümü") { a.openDeals() })
        val end = ZonedDateTime.now(Dates.TR).let { java.time.Duration.between(it, it.toLocalDate().plusDays(1).atStartOfDay(Dates.TR)) }
        addView(a.text("Her gün yeniden seçilir. Yenisi ${end.toHours()} sa ${end.toMinutes() % 60} dk sonra.", 13f,
            Theme.p.text2, Theme.medium, 1), lp(l = dp(20), r = dp(20), t = -dp(6), b = dp(12)))
    }

    private fun seeAll(label: String, onClick: () -> Unit): View = a.vbox().apply {
        setGravity(Gravity.CENTER)
        background = ripple(rounded(Theme.p.surface, 6f), 6f)
        addView(IconView(a, Icon.CHEVRON, Theme.p.accent, 26))
        addView(a.text(label, 17f, Theme.p.text, Theme.displayBold, 2, Gravity.CENTER), lp(WRAP, WRAP, t = dp(8)))
        onTap(onClick)
    }

    private fun storeTile(id: String, name: String, sub: String, fav: Boolean, onClick: () -> Unit): View = a.vbox().apply {
        contentDescription = (if (fav) "Favori market, " else "") + "$name, $sub"
        val sign = FrameLayout(a).apply { background = ripple(rounded(Theme.store(id), 4f), 4f) }
        val short = Theme.storeShort[id]?.takeIf { it.length >= 3 } ?: name
        sign.addView(a.text(short, if (short.length > 6) 16f else 21f, Theme.onStore(id), Theme.display, 1),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, dp(6), dp(6)) })
        if (fav) sign.addView(IconView(a, Icon.STAR_FILLED, Theme.onStore(id), 14),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(6), dp(6), 0) })
        addView(sign, LinearLayout.LayoutParams(MATCH, dp(62)))
        addView(a.text(sub.ifEmpty { " " }, 12f, Theme.p.text2, Theme.medium, 1), lp(WRAP, WRAP, t = dp(5)))
        onTap(onClick)
    }
}

fun minuteLabel(m: Int): String = String.format(java.util.Locale.ROOT, "%02d.%02d", m / 60, m % 60)


/**
 * Broşürlük: marketlerin güncel broşürleri bir rafta durur; altlarından geçen sarı raf şeridine her marketin adı ve
 * broşür durumu raf etiketi gibi basılır. Ana sayfanın tek "güçlü" öğesi; geri kalan her şey sakin.
 */
fun MainActivity.shelfRack(shelf: List<com.firsat360.app.core.ShelfEntry>): View {
    // Raf şeridi yüksekliği yazı ölçeğiyle büyür (büyük yazıda durum satırı kesilmez)
    val stripH = dp(30f + 36f * resources.configuration.fontScale)
    val row = hbox(Gravity.TOP).apply {
        setPadding(dp(20), dp(4), dp(6), 0)
        background = ShelfDrawable(stripH)
    }
    for (e in shelf) row.addView(shelfSlot(e, stripH))
    return hscroll(row).apply { isFillViewport = true }
}

private fun MainActivity.shelfSlot(e: com.firsat360.app.core.ShelfEntry, stripH: Int): View {
    val cached = if (e.bridge != null) com.firsat360.app.data.DeviceBrochures.get(this, e.store.id) else null
    val c = e.catalog
    val cover = c?.coverUrl ?: cached?.pages?.firstOrNull()
    val w = minOf(dp(132), (resources.displayMetrics.widthPixels - dp(20 + 14 * 2 + 30)) / 3 + dp(20))
    val slot = vbox().apply { layoutParams = lp(w, WRAP, r = dp(14)) }
    val frame = paperFrame(1.38f, Theme.blend(Theme.p.surface, Theme.store(e.store.id), if (Theme.p.dark) 0.16f else 0.07f))
    val ph = vbox().apply {
        addView(View(context).apply { setBackgroundColor(Theme.store(e.store.id)) }, lp(MATCH, dp(10)))
        addView(IconView(context, if (c?.hasPdf == true) Icon.PDF else Icon.CATALOG, Theme.p.text3, 22), lp(WRAP, WRAP, l = dp(10), t = dp(10)))
        addView(text(if (e.bridge != null || c == null) "Resmî sayfadan açılır" else c.title, 13f, Theme.p.text2, Theme.medium, 3),
            lp(l = dp(10), r = dp(10), t = dp(8)))
    }
    frame.addView(ph, FrameLayout.LayoutParams(MATCH, MATCH))
    if (cover != null && com.firsat360.app.data.ImageLoader.enabled) {
        val img = NetImage(this, 0f).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        ph.visibility = View.INVISIBLE
        img.load(cover, dp(360)) { img.visibility = View.GONE; ph.visibility = View.VISIBLE }
        frame.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
    }
    slot.addView(frame, lp(MATCH, WRAP, r = dp(4)))
    // Raf etiketi (şeridin üzerinde): market adı + durum
    val label = vbox().apply { setPadding(0, dp(9), dp(4), 0) }
    label.addView(text(e.store.name, 20f, Theme.p.onLabel, Theme.display, 1))
    val status = when {
        c != null -> Dates.status(c.validFrom, c.validTo, today).label
        e.bridge != null -> if (cached != null) "Bu hafta okundu" else "Güncel broşür"
        else -> "Resmî sayfa"
    } + if (e.others > 0) ", +${e.others} katalog" else ""
    label.addView(text(status, 12.5f, Theme.blend(Theme.p.onLabel, Theme.p.label, 0.25f), Theme.medium, 1), lp(t = dp(2)))
    slot.addView(label, lp(MATCH, stripH))  // kapak doğrudan rafın üstünde durur
    slot.contentDescription = "${e.store.name} broşürü, $status. Açmak için dokunun"
    slot.background = ripple(null, 4f)
    slot.onTap { openBrochure(e) }
    return slot
}

/** Raf şeridi: içeriğin altında boydan boya sarı bant; üst kenarında rafın ışık çizgisi, altında gölge çizgisi. */
class ShelfDrawable(private val stripH: Int) : android.graphics.drawable.Drawable() {
    private val p = android.graphics.Paint()
    override fun draw(c: android.graphics.Canvas) {
        val b = bounds
        val top = (b.bottom - stripH).toFloat()
        p.color = Theme.p.label; c.drawRect(b.left.toFloat(), top, b.right.toFloat(), b.bottom.toFloat(), p)
        p.color = Theme.tint(0xFFFFFFFF.toInt(), 0.55f); c.drawRect(b.left.toFloat(), top, b.right.toFloat(), top + dp(2), p)
        p.color = Theme.p.labelShade; c.drawRect(b.left.toFloat(), b.bottom - dp(4).toFloat(), b.right.toFloat(), b.bottom.toFloat(), p)
    }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(cf: android.graphics.ColorFilter?) {}
    @Deprecated("Drawable API") override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

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
 * İstemci katalog köprüsü: market kartına dokunulunca. Bu cihazda son 3 günde resmî sayfadan okunmuş sayfalar varsa
 * doğrudan yerel okuyucu; yoksa resmî sayfa yükleme katmanıyla açılır, profil betiği broşürü okur ve yerel okuyucuya geçer.
 */
fun MainActivity.openBridge(store: com.firsat360.app.core.Store, bridge: com.firsat360.app.core.BridgeEntry?, fallbackUrl: String? = null) {
    val cached = com.firsat360.app.data.DeviceBrochures.get(this, store.id)
    if (cached != null && com.firsat360.app.data.ImageLoader.enabled) {
        val ps = cached.pages.mapIndexed { i, u -> com.firsat360.app.core.CatalogPage(u, u, "Sayfa ${i + 1}", cached.sourceUrl, true) }
        push(CatalogReaderScreen(this, null, 0, null, "${store.name} broşür", extraPages = ps))
        return
    }
    push(WebScreen(this, bridge?.url ?: fallbackUrl ?: store.brochureUrl ?: store.catalogUrl, "${store.name} broşür",
        catalog = true, storeId = store.id, profile = bridge?.profile ?: "generic",
        allowedHosts = bridge?.allowedHosts ?: emptyList()))
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
        col.addView(a.text("Ana sayfadaki broşür rafında marketler bu sırayla görünür.", 13f, Theme.p.text2, Theme.regular))
        order.forEachIndexed { i, id ->
            val s = a.feed.store(id) ?: return@forEachIndexed
            val r = a.hbox().apply { setPadding(dp(14), dp(10), dp(8), dp(10)); background = rounded(Theme.p.surface, 8f) }
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
