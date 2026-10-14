package com.firsat360.app.ui

import android.content.Context
import android.graphics.Paint
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.firsat360.app.core.Catalog
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Money
import com.firsat360.app.core.Offer
import com.firsat360.app.core.Thumbs
import com.firsat360.app.core.ValidityKind
import com.firsat360.app.data.ImageLoader
import java.time.LocalDate

/** Genişliği kadar yüksek (kare) kap: ızgara kartlarında görsel alanı. */
class SquareFrame(ctx: Context) : FrameLayout(ctx) {
    override fun onMeasure(w: Int, h: Int) = super.onMeasure(w, w)
}

/**
 * Izgara ürün kartı: büyük ürün görseli (yalnızca ürünün KENDİ resmî görseli; yoksa monogram — başka ürün görseli asla),
 * mağaza rozeti, beyan indirimi, fiyat, sayfa fiyatı uyuşmazlığı uyarısı.
 */
class ProductCard(ctx: Context) : LinearLayout(ctx) {
    private val media = SquareFrame(ctx)
    private val mono = Monogram(ctx, 72)
    private val photo = NetImage(ctx, 0f).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val discount: TextView = ctx.pill("", 0xFFFFFFFF.toInt(), 0xFFE5484D.toInt(), 11f, Theme.bold)
    val fav = IconView(ctx, Icon.HEART, Theme.p.text2, 20)
    private val favWrap = FrameLayout(ctx)
    private val store: TextView = ctx.storeBadge("bim", "BİM")
    private val validity: TextView = ctx.text("", 11f, Theme.p.text3, Theme.medium, 1)
    private val title: TextView = ctx.text("", 13.5f, Theme.p.text, Theme.semibold, 2)
    private val sub: TextView = ctx.text("", 11.5f, Theme.p.text2, Theme.regular, 1)
    private val price: TextView = ctx.text("", 17f, Theme.p.text, Theme.bold, 1)
    private val old: TextView = ctx.text("", 11.5f, Theme.p.text3, Theme.medium, 1).apply {
        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    }
    private val warn: TextView = ctx.pill("Fiyatı kontrol edin", Theme.p.warn, Theme.p.warnSoft, 10.5f)

    init {
        orientation = VERTICAL
        background = ripple(rounded(Theme.p.surface, 20f, Theme.p.outline), 20f)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
        }
        media.setBackgroundColor(0xFFFFFFFF.toInt())
        media.addView(mono, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        photo.setBackgroundColor(0xFFFFFFFF.toInt())
        photo.setPadding(dp(10), dp(10), dp(10), dp(10))
        media.addView(photo, FrameLayout.LayoutParams(MATCH, MATCH))
        media.addView(discount, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(10), dp(10), 0, 0) })
        favWrap.background = rounded(0xF2FFFFFF.toInt(), 99f, 0x14000000)
        favWrap.addView(fav, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        media.addView(favWrap, FrameLayout.LayoutParams(dp(34), dp(34), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(8), dp(8), 0) })
        addView(media, LinearLayout.LayoutParams(MATCH, WRAP))
        addView(View(ctx).apply { setBackgroundColor(Theme.p.outline) }, LinearLayout.LayoutParams(MATCH, 1))
        val body = ctx.vbox().apply { setPadding(dp(12), dp(10), dp(12), dp(12)) }
        val meta = ctx.hbox()
        meta.addView(store)
        meta.addView(validity, lp(0, WRAP, l = dp(6), weight = 1f))
        body.addView(meta)
        body.addView(title, lp(t = dp(8)))
        body.addView(sub, lp(t = dp(3)))
        val pr = ctx.hbox(Gravity.BOTTOM)
        pr.addView(price)
        pr.addView(old, lp(WRAP, WRAP, l = dp(6)))
        body.addView(pr, lp(t = dp(8)))
        body.addView(warn, lp(WRAP, WRAP, t = dp(6)))
        addView(body)
    }

    fun bind(o: Offer, storeName: String, catalog: Catalog?, isFav: Boolean, today: LocalDate) {
        mono.bind(o)
        if (o.imageUrl != null && ImageLoader.enabled) {
            photo.visibility = VISIBLE; mono.visibility = INVISIBLE
            photo.load(Thumbs.small(o.imageUrl), dp(360), o.imageUrl) { photo.visibility = GONE; mono.visibility = VISIBLE }
        } else {
            photo.tag = null; photo.visibility = GONE; mono.visibility = VISIBLE
        }
        val pct = Money.declaredDiscountPercent(o.price, o.storeDeclaredOldPrice)
        discount.visibility = if (pct != null) VISIBLE else GONE
        if (pct != null) discount.text = "−%$pct"
        store.text = storeName
        store.setTextColor(Theme.onStore(o.storeId))
        store.background = rounded(Theme.store(o.storeId), 99f)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, today)
        validity.text = if (v.kind == ValidityKind.UNKNOWN) "" else v.label
        validity.setTextColor(validityColor(v.kind).first)
        title.text = o.displayTitle
        val s = o.subtitle
        sub.text = s ?: (if (o.channel == "online") "Online fiyat" else "Mağaza fiyatı")
        price.text = Money.format(o.price)
        old.visibility = if (o.storeDeclaredOldPrice != null) VISIBLE else GONE
        o.storeDeclaredOldPrice?.let { old.text = Money.format(it) }
        warn.visibility = if (o.pagePriceConflict != null) VISIBLE else GONE
        fav.icon = if (isFav) Icon.HEART_FILLED else Icon.HEART
        fav.color = if (isFav) Theme.p.danger else Theme.p.text2
        favWrap.contentDescription = if (isFav) "Favoriden çıkar" else "Favoriye ekle"
        contentDescription = "${o.displayTitle}, ${Money.format(o.price)}, $storeName"
    }

    /** Favori düğmesine dokunma (kartın kendisinden ayrı). */
    fun onFav(action: () -> Unit) { favWrap.isClickable = true; favWrap.setOnClickListener { action() } }
}
