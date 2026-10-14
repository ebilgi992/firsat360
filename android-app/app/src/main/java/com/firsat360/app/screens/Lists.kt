package com.firsat360.app.screens

import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.InsetDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import com.firsat360.app.MainActivity
import com.firsat360.app.core.Offer
import com.firsat360.app.ui.OfferRow
import com.firsat360.app.ui.Theme
import com.firsat360.app.ui.dp
import com.firsat360.app.ui.ripple
import android.graphics.drawable.ColorDrawable as CD

class OfferAdapter(private val a: MainActivity, items: List<Offer>) : BaseAdapter() {
    var items: List<Offer> = items
        set(v) { field = v; notifyDataSetChanged() }
    private var favs = a.repo.favorites()

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun notifyDataSetChanged() {
        favs = a.repo.favorites()
        super.notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView as? OfferRow ?: OfferRow(a)
        val o = items[position]
        val store = a.feed.store(o.storeId)
        row.bind(o, store?.name ?: o.storeId, a.feed.catalog(o.catalogId), o.id in favs, a.today)
        row.background = ripple(CD(Theme.p.surface), 0f)
        row.setOnClickListener { a.openOffer(o.id) }
        row.fav.setOnClickListener {
            val now = a.repo.toggleFavorite(o.id)
            a.toast(if (now) "Favorilere eklendi" else "Favorilerden çıkarıldı")
            notifyDataSetChanged()
        }
        row.fav.contentDescription = if (o.id in favs) "Favoriden çıkar" else "Favoriye ekle"
        return row
    }
}

fun MainActivity.offerListView(): ListView = ListView(this).apply {
    divider = InsetDrawable(ColorDrawable(Theme.p.outline), dp(82), 0, 0, 0)
    dividerHeight = 1
    selector = ColorDrawable(0)
    setBackgroundColor(Theme.p.bg)
    cacheColorHint = 0
    isVerticalScrollBarEnabled = false
    clipToPadding = false
    setPadding(0, 0, 0, dp(24))
}

/** İki sütunlu ızgara: ListView satırı başına iki [com.firsat360.app.ui.ProductCard] (görünümler yeniden kullanılır). */
class OfferGridAdapter(private val a: MainActivity, items: List<Offer>) : BaseAdapter() {
    var items: List<Offer> = items
        set(v) { field = v; notifyDataSetChanged() }
    private var favs = a.repo.favorites()

    override fun getCount() = (items.size + 1) / 2
    override fun getItem(position: Int) = items[position * 2]
    override fun getItemId(position: Int) = position.toLong()
    override fun isEnabled(position: Int) = false  // dokunma kartların kendisinde

    override fun notifyDataSetChanged() {
        favs = a.repo.favorites()
        super.notifyDataSetChanged()
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val row = convertView as? android.widget.LinearLayout ?: android.widget.LinearLayout(a).apply {
            orientation = android.widget.LinearLayout.HORIZONTAL
            setPadding(dp(14), dp(6), dp(14), dp(6))
            layoutParams = android.widget.AbsListView.LayoutParams(com.firsat360.app.ui.MATCH, com.firsat360.app.ui.WRAP)
            for (i in 0..1) addView(com.firsat360.app.ui.ProductCard(a),
                com.firsat360.app.ui.lp(0, com.firsat360.app.ui.WRAP, l = if (i == 1) dp(6) else 0,
                    r = if (i == 0) dp(6) else 0, weight = 1f))
        }
        for (i in 0..1) {
            val card = row.getChildAt(i) as com.firsat360.app.ui.ProductCard
            val idx = position * 2 + i
            if (idx >= items.size) { card.visibility = View.INVISIBLE; continue }
            card.visibility = View.VISIBLE
            val o = items[idx]
            card.bind(o, a.feed.store(o.storeId)?.name ?: o.storeId, a.feed.catalog(o.catalogId), o.id in favs, a.today)
            card.setOnClickListener { a.openOffer(o.id) }
            card.onFav {
                val now = a.repo.toggleFavorite(o.id)
                a.toast(if (now) "Favorilere eklendi" else "Favorilerden çıkarıldı")
                notifyDataSetChanged()
            }
        }
        return row
    }
}

/** Kullanıcının seçtiği görünüme göre ızgara ya da liste bağdaştırıcısı. */
fun MainActivity.offerAdapter(items: List<Offer>, grid: Boolean): BaseAdapter =
    if (grid) OfferGridAdapter(this, items) else OfferAdapter(this, items)

fun BaseAdapter.setOffers(items: List<Offer>) {
    when (this) { is OfferGridAdapter -> this.items = items; is OfferAdapter -> this.items = items }
}

fun MainActivity.gridListView(grid: Boolean): ListView = offerListView().apply {
    if (grid) { divider = null; dividerHeight = 0; setBackgroundColor(Theme.p.bg) }
}
