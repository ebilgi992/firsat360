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

        // ---- Üst bölüm: selamlama, marka, yenile / ayarlar
        val top = a.hbox()
        top.setPadding(dp(20), dp(18), dp(14), dp(4))
        val titleCol = a.vbox()
        val hour = ZonedDateTime.now(Dates.TR).hour
        val greet = when (hour) { in 5..11 -> "Günaydın"; in 12..17 -> "İyi günler"; in 18..22 -> "İyi akşamlar"; else -> "İyi geceler" }
        titleCol.addView(a.text("$greet · ${Dates.long(a.today)}", 13f, Theme.p.text2, Theme.medium, 1))
        val brand = SpannableString("FIRSAT360").apply {
            setSpan(ForegroundColorSpan(Theme.p.accent), 6, 9, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        titleCol.addView(a.text(brand, 28f, Theme.p.text, Theme.bold), lp(t = dp(2)))
        top.addView(titleCol, lp(0, WRAP, weight = 1f))
        top.addView(a.iconButton(Icon.SETTINGS, desc = "Ayarlar") { a.openSettings() }, lp(dp(42), dp(42)))
        col.addView(top)

        // ---- Arama
        val search = a.hbox().apply {
            setPadding(dp(16), dp(15), dp(16), dp(15))
            background = ripple(rounded(Theme.p.surface, 18f, Theme.p.outline), 18f)
            elevation = dp(1).toFloat()
            addView(IconView(a, Icon.SEARCH, Theme.p.text2, 20))
            addView(a.text("Ürün, marka veya market ara", 15f, Theme.p.text3, Theme.regular, 1), lp(0, WRAP, l = dp(12), weight = 1f))
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
        val home = com.firsat360.app.core.HomeCatalogs.build(f, a.user.chainOrder, a.today, a.user.favoriteStores())
        val shelf = home.shelf
        if (shelf.isNotEmpty()) {
            col.addView(a.sectionHeader("Market broşürleri", "Sırala") { a.push(ShelfOrderScreen(a)) })
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), dp(4)) }
            for (e in shelf) row.addView(a.shelfCard(e))
            col.addView(a.hscroll(row))
            col.addView(a.text("Son güncelleme: ${Dates.relative(f.generatedAt, a.now)} · resmî market kaynaklarından",
                11.5f, Theme.p.text3, Theme.regular, 1), lp(l = dp(20), r = dp(20), t = dp(8)))
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
                row.addView(card, lp(dp(172), WRAP, r = dp(12)))
            }
            if (deals.size > 12) row.addView(seeAll("${deals.size - 12}+ indirim daha") { a.openDeals() }, lp(dp(120), dp(200), r = dp(12)))
            col.addView(a.hscroll(row))
            col.addView(a.text(f.dailyDeals?.note ?: "", 11.5f, Theme.p.text3, Theme.regular),
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
            val card = a.vbox(dp(18)).apply { background = rounded(Theme.p.accentSoft, 22f) }
            val h = a.hbox()
            h.addView(FrameLayout(a).apply {
                background = rounded(Theme.p.accent, 99f)
                addView(IconView(a, Icon.BELL, 0xFFFFFFFF.toInt(), 20), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            }, LinearLayout.LayoutParams(dp(40), dp(40)))
            val t = a.vbox()
            t.addView(a.text("Kataloglar çıkınca haber verelim", 15.5f, Theme.p.text, Theme.bold))
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
            "değişebilir. FIRSAT360'ın marketlerle resmî bir bağı yoktur.", 11.5f, Theme.p.text3, Theme.regular, gravity = Gravity.CENTER),
            lp(l = dp(28), r = dp(28), t = dp(28)))

        return ScrollView(a).apply {
            isVerticalScrollBarEnabled = false
            setBackgroundColor(Theme.p.bg)
            addView(col)
        }
    }

    private fun dealsHeader(): View = a.hbox().apply {
        setPadding(dp(20), dp(28), dp(20), dp(12))
        addView(FrameLayout(a).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(Theme.p.accent2, Theme.p.deal))
                .apply { cornerRadius = dp(12).toFloat() }
            addView(IconView(a, Icon.FLAME, 0xFFFFFFFF.toInt(), 18), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(34), dp(34)))
        val t = a.vbox()
        t.addView(a.text("Günün İndirimleri", 19f, Theme.p.text, Theme.bold))
        val end = ZonedDateTime.now(Dates.TR).let { java.time.Duration.between(it, it.toLocalDate().plusDays(1).atStartOfDay(Dates.TR)) }
        t.addView(a.text("Her gün otomatik seçilir · yenisine ${end.toHours()} sa ${end.toMinutes() % 60} dk", 12f,
            Theme.p.text2, Theme.medium, 1), lp(t = dp(2)))
        addView(t, lp(0, WRAP, l = dp(12), weight = 1f))
        addView(a.text("Tümü", 13.5f, Theme.p.accent, Theme.semibold).apply {
            setPadding(dp(8), dp(6), 0, dp(6)); onTap { a.openDeals() } })
    }

    private fun seeAll(label: String, onClick: () -> Unit): View = a.vbox().apply {
        setGravity(Gravity.CENTER)
        background = ripple(rounded(Theme.p.surface, 20f, Theme.p.outline), 20f)
        addView(FrameLayout(a).apply {
            background = rounded(Theme.p.accentSoft, 99f)
            addView(IconView(a, Icon.CHEVRON, Theme.p.accent, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        addView(a.text(label, 13f, Theme.p.text, Theme.semibold, 2, Gravity.CENTER), lp(WRAP, WRAP, t = dp(10)))
        onTap(onClick)
    }

    private fun storeTile(id: String, name: String, sub: String, fav: Boolean, onClick: () -> Unit): View = a.vbox(dp(10)).apply {
        setGravity(Gravity.CENTER_HORIZONTAL)
        background = ripple(rounded(Theme.p.surface, 18f, Theme.p.outline), 18f)
        contentDescription = "$name, $sub"
        val circle = FrameLayout(a).apply { background = rounded(Theme.store(id), 14f) }
        val short = Theme.storeShort[id] ?: name.take(2)
        circle.addView(a.text(short, if (short.length >= 4) 11.5f else 13.5f, Theme.onStore(id), Theme.bold),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        addView(circle, LinearLayout.LayoutParams(dp(44), dp(44)))
        addView(a.text((if (fav) "★ " else "") + name, 12f, Theme.p.text, Theme.semibold, 1, Gravity.CENTER), lp(WRAP, WRAP, t = dp(8)))
        addView(a.text(sub, 10.5f, Theme.p.text3, Theme.medium, 1, Gravity.CENTER), lp(WRAP, WRAP, t = dp(2)))
        onTap(onClick)
    }
}

fun minuteLabel(m: Int): String = String.format(java.util.Locale.ROOT, "%02d.%02d", m / 60, m % 60)


/** Raf kartı: güncel broşür varsa onun kapağı; yoksa mağazanın resmî broşür sayfası (uygulama içinde tam ekran). */
fun MainActivity.shelfCard(e: com.firsat360.app.core.ShelfEntry): View {
    val c = e.catalog ?: Catalog(id = "web-${e.store.id}", storeId = e.store.id, title = "Güncel broşür ve afişler",
        kind = "flipbook", validFrom = null, validTo = null, officialUrl = e.store.brochureUrl ?: e.store.catalogUrl,
        sourceMethod = "official_page", observedAt = null, offerCount = 0)
    val card = catalogCard(c, e.store.name, today) { openBrochure(e) }
    if (e.others > 0) card.addView(text("+${e.others} katalog daha", 11.5f, Theme.p.accent, Theme.semibold, 1),
        lp(l = dp(12), b = dp(12)))
    return card
}

/** Broşürü en iyi biçimde açar: sayfa görselleri → yakınlaştırılabilir okuyucu; PDF → PDF; diğerleri → resmî sayfa. */
fun MainActivity.openBrochure(e: com.firsat360.app.core.ShelfEntry) {
    val c = e.catalog
    when {
        c != null && c.hasPages && com.firsat360.app.data.ImageLoader.enabled -> openReader(c.id)
        c != null && c.hasPdf -> push(PdfScreen(this, c.id))
        c != null && !c.webOnly -> openCatalog(c.id)
        else -> push(WebScreen(this, c?.officialUrl ?: e.store.brochureUrl ?: e.store.catalogUrl,
            "${e.store.name} broşür", catalog = true))
    }
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
            val r = a.hbox().apply { setPadding(dp(14), dp(10), dp(8), dp(10)); background = rounded(Theme.p.surface, 16f, Theme.p.outline) }
            r.addView(a.text("${i + 1}", 14f, Theme.p.text3, Theme.semibold), lp(dp(26), WRAP))
            r.addView(a.storeBadge(s.id, s.name))
            r.addView(a.text(s.name, 15f, Theme.p.text, Theme.medium, 1), lp(0, WRAP, l = dp(10), weight = 1f))
            r.addView(a.iconButton(Icon.CHEVRON_UP, if (i > 0) Theme.p.text else Theme.p.text3, desc = "${s.name} yukarı") { move(i, -1) },
                lp(dp(40), dp(40)))
            r.addView(a.iconButton(Icon.CHEVRON_DOWN, if (i < order.size - 1) Theme.p.text else Theme.p.text3,
                desc = "${s.name} aşağı") { move(i, 1) }, lp(dp(40), dp(40), l = dp(4)))
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
