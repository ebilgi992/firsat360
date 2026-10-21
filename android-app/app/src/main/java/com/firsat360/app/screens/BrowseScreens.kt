package com.firsat360.app.screens

import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.core.Dates
import com.firsat360.app.core.OfferQuery
import com.firsat360.app.core.Search
import com.firsat360.app.core.SearchHistory
import com.firsat360.app.core.SortMode
import com.firsat360.app.ui.*

/** Mağaza sekmeleri: feed'deki tüm mağazalar (sıra feed'den), kaydırılabilir renkli çipler. */
class StoresScreen(a: MainActivity, initial: String?) : Screen(a) {
    override val tab = Tab.STORES
    private var storeId = initial ?: a.feed.stores.firstOrNull()?.id ?: "a101"
    private var category: String? = null

    override fun create(): View {
        val f = a.feed
        val store = f.store(storeId)
        val grid = true  // sade arayüz: her zaman büyük fotoğraflı ızgara
        val list = a.gridListView(grid)
        val header = a.vbox()
        header.setPadding(0, dp(18), 0, dp(10))
        header.addView(a.text("Mağazalar", 34f, Theme.p.text, Theme.display).apply { accessibilityHeading() }, lp(l = dp(20), r = dp(20)))
        header.addView(a.text("${f.stores.size} market, ${f.offers.size} ürün, ${f.catalogs.count { it.coverUrl != null || it.hasPages }} katalog",
            12.5f, Theme.p.text2, Theme.medium), lp(l = dp(20), r = dp(20), t = dp(4)))

        // Mağaza çipleri (yatay kaydırma): rozet + ad + ürün sayısı
        val seg = a.hbox().apply { setPadding(dp(20), 0, dp(12), 0) }
        var selectedChip: View? = null
        for (s in f.stores) {
            val sid = s.id
            val on = sid == storeId
            val n = f.offers.count { it.storeId == sid }
            val chip = a.hbox().apply {
                setPadding(dp(6), dp(6), dp(14), dp(6))
                background = ripple(if (on) rounded(Theme.store(sid), 6f) else rounded(Theme.p.surface, 6f, Theme.p.outline, 1.5f), 6f)
                val dot = FrameLayout(a).apply { background = rounded(if (on) Theme.tint(0xFFFFFFFF.toInt(), 0.25f) else Theme.store(sid), 2f) }
                dot.addView(a.text(Theme.storeShort[sid] ?: s.shortLabel, 12f, Theme.onStore(sid), Theme.display, 1),
                    FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                addView(dot, LinearLayout.LayoutParams(dp(30), dp(30)))
                val col = a.vbox()
                col.addView(a.text(s.name, 16f, if (on) Theme.onStore(sid) else Theme.p.text, Theme.displayBold, 1))
                col.addView(a.text(if (n > 0) "$n ürün" else "katalog", 11f,
                    if (on) Theme.tint(Theme.onStore(sid), 0.8f) else Theme.p.text3, Theme.medium, 1))
                addView(col, lp(WRAP, WRAP, l = dp(8)))
                contentDescription = "${s.name}, ${if (n > 0) "$n ürün" else "yalnızca katalog"}"
                onTap { storeId = sid; category = null; a.refreshCurrent() }
            }
            if (on) selectedChip = chip
            seg.addView(chip, lp(WRAP, WRAP, r = dp(8)))
        }
        val segScroll = a.hscroll(seg)
        selectedChip?.let { sel -> segScroll.post { segScroll.smoothScrollTo(maxOf(0, sel.left - dp(20)), 0) } }
        header.addView(segScroll, lp(t = dp(16)))

        if (store != null) {
            val info = a.card()
            val row = a.hbox()
            val statusLabel = when (store.status) {
                "ok", "partial" -> "Güncel"; "link_only" -> "Katalog"; "client_bridge" -> "Resmî broşür"
                "error" -> "Güncelleniyor"; else -> "Yakında"
            }
            val (fg, bg) = when (store.status) {
                "ok", "partial", "link_only", "client_bridge" -> Theme.p.ok to Theme.p.okSoft
                else -> Theme.p.warn to Theme.p.warnSoft
            }
            row.addView(a.pill(statusLabel, fg, bg))
            row.addView(a.flex())
            row.addView(a.text("Güncellendi ${Dates.relative(store.lastSuccessAt, a.now)}", 12f, Theme.p.text2, Theme.medium, 1))
            info.addView(row)
            if (store.status == "error") {
                info.addView(a.notice(Icon.CLOCK, "Bu marketin son güncellemesi gecikti; gösterilen fırsatlar " +
                    "${Dates.relative(store.lastSuccessAt, a.now)} alındı. Fiyatlar değişmiş olabilir.", Theme.p.warn, Theme.p.warnSoft),
                    lp(t = dp(10)))
            }
            a.feed.clientBridge[store.id]?.let { br ->
                // İstemci köprüsü: resmî broşür sayfası cihazda açılır, sayfalar doğrudan yerel okuyucuya aktarılır
                info.addView(a.button("Güncel broşürü aç", Icon.CATALOG, true) { a.openBridge(store, br) }, lp(MATCH, WRAP, t = dp(12)))
            }
            val btns = a.hbox()
            btns.addView(a.button("Resmî sayfa", Icon.GLOBE, false) { a.openWeb(store.catalogUrl, store.name) }, lp(0, WRAP, weight = 1f))
            val favStore = store.id in a.user.favoriteStores()
            btns.addView(a.button(if (favStore) "Favori mağaza" else "Favorilere ekle", if (favStore) Icon.STAR_FILLED else Icon.STAR, favStore) {
                val now = a.user.toggleFavoriteStore(store.id)
                a.toast(if (now) "${store.name} favori mağazalara eklendi" else "${store.name} favorilerden çıkarıldı")
                a.refreshCurrent()
            }, lp(0, WRAP, l = dp(8), weight = 1f))
            info.addView(btns, lp(t = dp(14)))
            header.addView(info, lp(l = dp(20), r = dp(20), t = dp(14)))
        }

        // Süresi dolmuşlar çıkarılır; bölge kopyaları kullanıcının bölgesine göre tek karta indirgenir.
        val all = f.catalogs.filter { it.storeId == storeId &&
            com.firsat360.app.core.CatalogRules.state(it, a.today) != com.firsat360.app.core.CatalogState.EXPIRED }
        val catalogs = com.firsat360.app.core.CatalogRules.collapse(all, a.user.region)
        if (catalogs.isNotEmpty()) {
            header.addView(a.sectionHeader("Kataloglar"))
            val regions = all.mapNotNull { it.region }.distinct()
            if (regions.size > 1) {
                val names = mapOf("istanbul" to "İstanbul", "bursa" to "Bursa", "trakya" to "Trakya")
                val chips = a.hbox().apply { setPadding(dp(20), 0, dp(20), dp(10)) }
                for (r in regions) {
                    val on = r == a.user.region
                    chips.addView(a.pill(names[r] ?: r, if (on) 0xFFFFFFFF.toInt() else Theme.p.text2,
                        if (on) Theme.p.accent else Theme.p.surface2, 13f, Theme.semibold).apply {
                        contentDescription = "Bölge: ${names[r] ?: r}"
                        onTap { a.user.region = r; a.refreshCurrent() }
                    }, lp(WRAP, WRAP, r = dp(8)))
                }
                header.addView(chips)
            }
            val row = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), 0) }
            for (c in catalogs.sortedWith(compareBy<com.firsat360.app.core.Catalog> { it.kind == "link_only" }.thenBy { it.validFrom ?: "" }))
                row.addView(a.catalogCard(c, store?.name ?: "", a.today) { a.openCatalog(c.id) })
            header.addView(a.hscroll(row))
        }

        val storeOffers = f.offers.filter { it.storeId == storeId }
        if (storeOffers.isEmpty()) {
            header.addView(a.notice(Icon.CATALOG, "${store?.name ?: "Bu market"} fırsatlarını yukarıdaki katalog ve broşürlerde sayfa sayfa inceleyebilirsiniz.", Theme.p.text2, Theme.p.surface2),
                lp(l = dp(20), r = dp(20), t = dp(18)))
            list.addHeaderView(header, null, false)
            list.adapter = OfferAdapter(a, emptyList())
            return list
        }
        val hdr = a.sectionHeader("Ürünler (${storeOffers.size})")
        header.addView(hdr)
        val chips = a.hbox().apply { setPadding(dp(20), 0, dp(8), dp(10)) }
        chips.addView(a.chip("Tümü", category == null) { category = null; a.refreshCurrent() }, lp(WRAP, WRAP, r = dp(8)))
        for (k in Search.categoriesIn(storeOffers)) {
            chips.addView(a.chip(f.categoryName(k), category == k) { category = k; a.refreshCurrent() }, lp(WRAP, WRAP, r = dp(8)))
        }
        header.addView(a.hscroll(chips))

        val items = Search.filter(storeOffers, OfferQuery(category = category, sort = SortMode.NAME))
        list.addHeaderView(header, null, false)
        if (items.isEmpty()) list.addFooterView(a.emptyState(Icon.STORE, "Bu mağaza için ürün yok",
            "Mağazanın resmî sayfasını açarak güncel kampanyaları görebilirsiniz."), null, false)
        list.adapter = a.offerAdapter(items, grid)
        return list
    }
}

class SearchScreen(a: MainActivity, initialCategory: String? = null) : Screen(a) {
    override val tab = Tab.SEARCH
    private var query = ""
    private val stores = LinkedHashSet<String>()
    private var category: String? = initialCategory
    private var sort = SortMode.RELEVANCE
    private var adapter: android.widget.BaseAdapter? = null
    private var countView: TextView? = null
    private var empty: LinearLayout? = null
    private var dynamic: LinearLayout? = null
    private var clearBtn: View? = null
    private var input: EditText? = null
    private val handler = android.os.Handler(android.os.Looper.getMainLooper())
    private val runUpdate = Runnable { update() }

    override fun create(): View {
        val f = a.feed
        val grid = true
        val list = a.gridListView(grid)
        val header = a.vbox()
        header.setPadding(0, dp(18), 0, dp(8))
        val titleRow = a.hbox()
        titleRow.addView(a.text("Ara", 34f, Theme.p.text, Theme.display).apply { accessibilityHeading() }, lp(0, WRAP, weight = 1f))
        header.addView(titleRow, lp(l = dp(20), r = dp(20)))

        val box = a.hbox().apply {
            setPadding(dp(14), dp(2), dp(4), dp(2))
            background = rounded(Theme.p.surface, 8f, Theme.p.text, 1.5f)
        }
        box.addView(IconView(a, Icon.SEARCH, Theme.p.text, 20))
        val inp = EditText(a).apply {
            setText(query)
            hint = "Ürün, marka veya market (ör. bim süt)"
            contentDescription = "Arama kutusu"
            setHintTextColor(Theme.p.text3)
            setTextColor(Theme.p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f)
            typeface = Theme.regular
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setPadding(dp(10), dp(14), dp(4), dp(14))
            setSelection(text.length)
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, aa: Int) {}
                override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    query = s?.toString() ?: ""
                    clearBtn?.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE
                    // Yazarken anlık sonuç (kısa gecikmeyle: her tuşta yeniden aramaz)
                    handler.removeCallbacks(runUpdate); handler.postDelayed(runUpdate, 140)
                }
            })
            setOnEditorActionListener { _, id, _ ->
                if (id == EditorInfo.IME_ACTION_SEARCH) { a.hideKeyboard(); remember(); handler.removeCallbacks(runUpdate); update(); true } else false
            }
        }
        input = inp
        box.addView(inp, lp(0, WRAP, weight = 1f))
        val clear = a.iconButton(Icon.CLOSE, Theme.p.text2, Theme.p.surface2, "Aramayı temizle") {
            inp.setText(""); inp.requestFocus()
            (a.getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
                .showSoftInput(inp, 0)
        }.apply { background = rounded(Theme.p.surface2, 6f); visibility = if (query.isEmpty()) View.GONE else View.VISIBLE }
        clearBtn = clear
        box.addView(clear, lp(dp(48), dp(48)))
        header.addView(box, lp(l = dp(20), r = dp(20), t = dp(14)))

        // Mağaza filtreleri + sıralama
        val row = a.hbox().apply { setPadding(dp(20), dp(12), dp(8), 0) }
        for (s in f.stores.filter { st -> f.offers.any { it.storeId == st.id } }) {
            val sid = s.id
            row.addView(a.chip(s.name, sid in stores, Theme.store(sid)) {
                if (!stores.remove(sid)) stores.add(sid); a.refreshCurrent()
            }.apply { if (sid in stores) setTextColor(Theme.onStore(sid)); contentDescription = "${s.name} filtresi" + if (sid in stores) " (seçili)" else "" },
                lp(WRAP, WRAP, r = dp(8)))
        }
        header.addView(a.hscroll(row))

        val dyn = a.vbox()
        header.addView(dyn)
        dynamic = dyn
        val cnt = a.text("", 12.5f, Theme.p.text2, Theme.medium)
        header.addView(cnt, lp(l = dp(20), t = dp(16), b = dp(4)))
        countView = cnt

        list.addHeaderView(header, null, false)
        val e = a.vbox()
        list.addFooterView(FrameLayout(a).apply { addView(e) }, null, false)
        empty = e
        adapter = a.offerAdapter(emptyList(), grid)
        list.adapter = adapter
        // Kaydırınca klavye kapansın (arama sonuçlarını rahat görmek için)
        list.setOnScrollListener(object : android.widget.AbsListView.OnScrollListener {
            override fun onScrollStateChanged(v: android.widget.AbsListView?, st: Int) {
                if (st == android.widget.AbsListView.OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) a.hideKeyboard()
            }
            override fun onScroll(v: android.widget.AbsListView?, f1: Int, f2: Int, f3: Int) {}
        })
        update()
        return list
    }

    private fun remember() {
        if (a.user.saveSearchHistory && query.isNotBlank()) a.user.searchHistory = SearchHistory.add(a.user.searchHistory, query)
    }

    override fun onHide() { handler.removeCallbacks(runUpdate); remember() }

    override fun onBack(): Boolean {
        // Geri: önce aramayı temizle, sonra ana sayfaya dön
        if (query.isNotEmpty()) { input?.setText(""); return true }
        return false
    }

    private fun update() {
        val f = a.feed
        val r = Search.engine(f).search(OfferQuery(query, stores, category, sort = sort))
        val items = r.offers
        adapter?.setOffers(items)
        val dyn = dynamic ?: return
        dyn.removeAllViews()
        // Arama geçmişi (boş sorguda)
        val hist = a.user.searchHistory
        if (query.isBlank() && a.user.saveSearchHistory && hist.isNotEmpty()) {
            val h = a.hbox().apply { setPadding(dp(20), dp(18), dp(20), dp(6)) }
            h.addView(a.text("Son aramalar", 14f, Theme.p.text, Theme.semibold), lp(0, WRAP, weight = 1f))
            h.addView(a.text("Temizle", 13f, Theme.p.accent, Theme.semibold).apply {
                setPadding(dp(8), dp(6), 0, dp(6)); contentDescription = "Arama geçmişini temizle"
                onTap { a.user.searchHistory = emptyList(); update() }
            })
            dyn.addView(h)
            val hr = a.hbox().apply { setPadding(dp(20), 0, dp(8), 0) }
            for (q in hist) hr.addView(a.chip(q, false) { input?.setText(q); input?.setSelection(q.length) }, lp(WRAP, WRAP, r = dp(8)))
            dyn.addView(a.hscroll(hr))
        }
        // Yazım düzeltmesi bilgisi
        if (r.fuzzy && r.suggestion != null) {
            dyn.addView(a.notice(Icon.SEARCH, "“${query.trim()}” bulunamadı; “${r.suggestion}” için sonuçlar gösteriliyor.",
                Theme.p.text2, Theme.p.surface2), lp(l = dp(20), r = dp(20), t = dp(14)))
        }
        // Market eşleşmesi: "bim", "BİM market" → market kartı
        for (s in r.stores) {
            val n = f.offers.count { it.storeId == s.id }
            val nc = f.catalogs.count { it.storeId == s.id && it.kind != "link_only" }
            val card = a.hbox().apply {
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = ripple(rounded(Theme.p.surface, 8f), 8f)
                contentDescription = "${s.name} marketini aç"
                addView(FrameLayout(a).apply {
                    background = rounded(Theme.store(s.id), 4f)
                    addView(a.text(Theme.storeShort[s.id] ?: s.shortLabel, 15f, Theme.onStore(s.id), Theme.display),
                        FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(42), dp(42)))
                val t = a.vbox()
                t.addView(a.text(s.name, 19f, Theme.p.text, Theme.displayBold, 1))
                t.addView(a.text("$n fırsat, $nc katalog", 12.5f, Theme.p.text2, Theme.medium, 1), lp(t = dp(2)))
                addView(t, lp(0, WRAP, l = dp(12), weight = 1f))
                addView(IconView(a, Icon.CHEVRON, Theme.p.text3, 18))
                onTap { a.openStore(s.id) }
            }
            dyn.addView(card, lp(l = dp(20), r = dp(20), t = dp(12)))
        }
        if (r.catalogs.isNotEmpty() && query.isNotBlank()) {
            dyn.addView(a.text("Kataloglar", 14f, Theme.p.text, Theme.semibold), lp(l = dp(20), t = dp(16), b = dp(8)))
            val cr = a.hbox(Gravity.TOP).apply { setPadding(dp(20), 0, dp(8), 0) }
            for (c in r.catalogs) cr.addView(a.catalogCard(c, f.store(c.storeId)?.name ?: "", a.today) { a.openCatalog(c.id) })
            dyn.addView(a.hscroll(cr))
        }
        countView?.text = if (query.isBlank() && stores.isEmpty() && category == null) "Tüm fırsatlar: ${items.size} ürün"
            else "${items.size} ürün bulundu"
        val e = empty ?: return
        e.removeAllViews()
        if (items.isEmpty()) {
            val filters = stores.isNotEmpty() || category != null
            e.addView(a.emptyState(Icon.SEARCH, if (query.isBlank()) "Bu filtrelerle ürün yok" else "“${query.trim()}” için sonuç yok",
                "Yazımı kontrol edin, daha genel bir kelime deneyin" + (if (filters) " veya filtreleri kaldırın." else "."),
                if (filters) "Filtreleri temizle" else null, if (filters) ({ stores.clear(); category = null; a.refreshCurrent() }) else null))
        }
    }
}
