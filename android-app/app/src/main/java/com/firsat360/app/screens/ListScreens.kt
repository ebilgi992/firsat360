package com.firsat360.app.screens

import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.ListItem
import com.firsat360.app.core.Money
import com.firsat360.app.core.ShoppingList
import com.firsat360.app.core.Watch
import com.firsat360.app.ui.*

/** Alışveriş listesi: ürün detayından veya serbest metinle eklenir; mağazaya göre gruplanır; cihazda saklanır. */
class ShoppingListScreen(a: MainActivity) : Screen(a) {
    override val tab = Tab.LIST

    override fun create(): View {
        val items = a.user.list()
        val col = a.vbox()
        col.setPadding(0, dp(18), 0, dp(32))
        val head = a.hbox().apply { setPadding(dp(20), 0, dp(16), 0) }
        head.addView(a.text("Alışveriş listem", 30f, Theme.p.text, Theme.display).apply { accessibilityHeading() }, lp(0, WRAP, weight = 1f))
        if (items.isNotEmpty()) head.addView(a.iconButton(Icon.SHARE, desc = "Listeyi paylaş") {
            a.share(ShoppingList.shareText(items) { id -> a.feed.store(id)?.name ?: id })
        })
        col.addView(head)

        val s = ShoppingList.summary(items)
        col.addView(a.text(if (items.isEmpty()) "Ürün detayında \"Alışveriş listesine ekle\"ye dokunun ya da aşağıya yazın."
            else "${s.open} ürün kaldı · bilinen toplam ${Money.format(s.knownTotal)}" +
                (if (s.unknownPrices > 0) " · ${s.unknownPrices} ürünün fiyatı yok" else ""),
            13f, Theme.p.text2, Theme.medium), lp(l = dp(20), r = dp(20), t = dp(6)))

        // Serbest metin ekleme
        val box = a.hbox().apply {
            setPadding(dp(14), dp(4), dp(6), dp(4)); background = rounded(Theme.p.surface, 8f)
        }
        val input = EditText(a).apply {
            hint = "Örn. ekmek, deterjan"; setHintTextColor(Theme.p.text3); setTextColor(Theme.p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f); typeface = Theme.regular; background = null
            setSingleLine(true); inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_DONE
            setPadding(dp(4), dp(12), dp(4), dp(12))
        }
        fun add() {
            val t = input.text.toString().trim()
            if (t.isEmpty()) return
            a.user.addToList(t, null, null, null); a.hideKeyboard(); a.refreshCurrent()
        }
        input.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_DONE) { add(); true } else false }
        box.addView(input, lp(0, WRAP, weight = 1f))
        box.addView(a.iconButton(Icon.PLUS, Theme.p.onAccent, Theme.p.accent, "Ekle") { add() }.apply {
            background = rounded(Theme.p.accent, 8f) })
        col.addView(box, lp(l = dp(20), r = dp(20), t = dp(16)))

        for ((storeId, list) in ShoppingList.grouped(items)) {
            val name = storeId?.let { a.feed.store(it)?.name ?: it } ?: "Diğer"
            val h = a.hbox().apply { setPadding(dp(20), dp(22), dp(20), dp(8)) }
            if (storeId != null) h.addView(a.storeBadge(storeId, name, small = false)) else h.addView(a.text(name, 15f, Theme.p.text, Theme.bold))
            col.addView(h)
            val card = a.vbox(0, rounded(Theme.p.surface, 8f))
            for ((i, item) in list.withIndex()) {
                card.addView(row(item))
                if (i < list.size - 1) card.addView(View(a).apply { setBackgroundColor(Theme.p.outline) }, lp(MATCH, 1, l = dp(52)))
            }
            col.addView(card, lp(l = dp(16), r = dp(16)))
        }
        if (items.any { it.checked }) {
            col.addView(a.button("İşaretlenenleri temizle", Icon.TRASH, false) {
                a.user.saveList(a.user.list().filter { !it.checked }); a.refreshCurrent()
            }, lp(l = dp(20), r = dp(20), t = dp(20)))
        }
        if (items.isEmpty()) col.addView(a.emptyState(Icon.LIST, "Listeniz boş",
            "Katalogdaki ürünleri listeye ekleyerek mağaza mağaza alışverişinizi planlayın."), lp(t = dp(24)))
        return ScrollView(a).apply { isVerticalScrollBarEnabled = false; setBackgroundColor(Theme.p.bg); addView(col) }
    }

    private fun row(item: ListItem): View = a.hbox().apply {
        setPadding(dp(12), dp(12), dp(8), dp(12))
        val check = IconView(a, if (item.checked) Icon.CHECK_BOX else Icon.BOX, if (item.checked) Theme.p.ok else Theme.p.text3, 26)
        check.contentDescription = if (item.checked) "Alındı işaretini kaldır" else "Alındı olarak işaretle"
        check.onTap {
            a.user.saveList(a.user.list().map { if (it.id == item.id) it.copy(checked = !it.checked) else it }); a.refreshCurrent()
        }
        addView(check)
        val mid = a.vbox()
        mid.addView(a.text(item.title, 14.5f, if (item.checked) Theme.p.text3 else Theme.p.text, Theme.medium, 2).apply {
            if (item.checked) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
        })
        val offer = item.offerId?.let { a.feed.offer(it) }
        val note = when {
            item.offerId != null && offer == null -> "Kampanya listede artık yok"
            offer != null && item.price != null && offer.price < item.price - 0.004 -> "Fiyatı düştü: ${Money.format(offer.price)}"
            else -> null
        }
        if (note != null) mid.addView(a.text(note, 12f, Theme.p.text2, Theme.regular, 1), lp(t = dp(2)))
        addView(mid, lp(0, WRAP, l = dp(12), weight = 1f))
        item.price?.let { addView(a.text(Money.format(it), 14f, Theme.p.text, Theme.semibold, 1), lp(WRAP, WRAP, r = dp(4))) }
        addView(a.iconButton(Icon.TRASH, Theme.p.text3, Theme.p.surface, "Listeden çıkar") {
            a.user.saveList(a.user.list().filter { it.id != item.id }); a.refreshCurrent()
        }.apply { background = null })
        if (offer != null) onTap { a.openOffer(offer.id) }
    }
}

/** Favoriler: ürünler, kataloglar, mağazalar. */
class FavoritesScreen(a: MainActivity, private var section: Int = 0) : Screen(a) {
    override val tab = Tab.FAVORITES

    override fun create(): View {
        val f = a.feed
        val list = a.offerListView()
        val header = a.vbox()
        header.setPadding(dp(20), dp(18), dp(20), dp(10))
        header.addView(a.text("Favoriler", 30f, Theme.p.text, Theme.display).apply { accessibilityHeading() })
        header.addView(a.text("Takip ettiğiniz ürün, katalog ve mağazalar yalnızca bu cihazda saklanır.", 13f, Theme.p.text2,
            Theme.regular), lp(t = dp(6)))
        val seg = a.hbox().apply { setPadding(dp(4), dp(4), dp(4), dp(4)); background = rounded(Theme.p.surface2, 16f) }
        for ((i, label) in listOf("Ürünler", "Kataloglar", "Marketler").withIndex()) {
            val on = i == section
            seg.addView(a.text(label, 14f, if (on) Theme.p.onAccent else Theme.p.text2, if (on) Theme.bold else Theme.medium, 1, Gravity.CENTER).apply {
                setPadding(0, dp(10), 0, dp(10)); background = if (on) rounded(Theme.p.accent, 12f) else null
                onTap { section = i; a.refreshCurrent() }
            }, lp(0, WRAP, weight = 1f))
        }
        header.addView(seg, lp(t = dp(14)))
        list.addHeaderView(header, null, false)

        when (section) {
            0 -> {
                val favIds = a.repo.favorites()
                val items = f.offers.filter { it.id in favIds }.sortedBy { it.storeId }
                val missing = favIds.size - items.size
                if (missing > 0) header.addView(a.notice(Icon.INFO,
                    "$missing favori ürün güncel listede yok (kampanya bitmiş veya kaldırılmış olabilir).", Theme.p.warn, Theme.p.warnSoft), lp(t = dp(12)))
                if (items.isEmpty()) list.addFooterView(a.emptyState(Icon.HEART, "Henüz favori ürün yok",
                    "Ürün kartlarındaki kalbe dokunarak takip etmeye başlayın.", "Ürünlere göz at") { a.switchTab(Tab.SEARCH) }, null, false)
                list.adapter = OfferAdapter(a, items)
            }
            1 -> {
                val ids = a.user.favoriteCatalogs()
                val cats = f.catalogs.filter { it.id in ids }
                val box = a.vbox().apply { setPadding(dp(4), dp(8), dp(4), 0) }
                for (c in cats) box.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today, wide = true) { a.openCatalog(c.id) },
                    lp(MATCH, WRAP, b = dp(12)))
                if (cats.isEmpty()) box.addView(a.emptyState(Icon.CATALOG, "Favori katalog yok",
                    "Katalog okuyucuda yıldıza dokunarak katalogları kaydedebilirsiniz."))
                header.addView(box)
                list.adapter = OfferAdapter(a, emptyList())
            }
            else -> {
                val fav = a.user.favoriteStores()
                val box = a.vbox().apply { setPadding(0, dp(12), 0, 0) }
                box.addView(a.text("Favori mağazaların katalogları ana sayfada önce gösterilir; bildirim açıksa yalnızca bu " +
                    "mağazaların yeni katalogları bildirilir (hiç seçilmezse hepsi).", 13f, Theme.p.text2, Theme.regular))
                for (s in f.stores) {  // feed sırası (mağaza "sort" alanı)
                    val on = s.id in fav
                    val r = a.hbox().apply {
                        setPadding(dp(14), dp(12), dp(10), dp(12)); background = rounded(Theme.p.surface, 8f)
                    }
                    r.addView(a.storeBadge(s.id, s.name, small = false))
                    r.addView(a.text("${f.catalogs.count { it.storeId == s.id && it.kind != "link_only" }} katalog · " +
                        "${f.offers.count { it.storeId == s.id }} ürün", 13f, Theme.p.text2, Theme.medium, 1), lp(0, WRAP, l = dp(12), weight = 1f))
                    r.addView(IconView(a, if (on) Icon.STAR_FILLED else Icon.STAR, if (on) 0xFFF5A524.toInt() else Theme.p.text3, 26).apply {
                        contentDescription = if (on) "Favori mağazadan çıkar" else "Favori mağaza yap"
                        onTap { a.user.toggleFavoriteStore(s.id); a.refreshCurrent() }
                    })
                    r.onTap { a.openStore(s.id) }
                    box.addView(r, lp(t = dp(10)))
                }
                header.addView(box)
                list.adapter = OfferAdapter(a, emptyList())
            }
        }
        return list
    }
}

/** Ana sayfa için öne çıkan kataloglar (kapaklı). */
fun MainActivity.featuredCatalogRow(): View? {
    val cats = Watch.featuredCatalogs(feed, today, user.favoriteStores(), user.region).take(12)
    if (cats.isEmpty()) return null
    val row = hbox(android.view.Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), dp(4)) }
    for (c in cats) row.addView(catalogCard(c, feed.store(c.storeId)?.name ?: "", today) { openCatalog(c.id) })
    return hscroll(row)
}
