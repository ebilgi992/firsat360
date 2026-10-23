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
    // Vitrin kartı: katmanlı yüzey; içinde yuvarlatılmış beyaz ürün alanı; market satırı; büyük fiyat (tabela harfi).
    private val media = SquareFrame(ctx)
    private val mono = Monogram(ctx, 72)
    private val photo = NetImage(ctx, 0f).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val discount: TextView = ctx.text("", 14.5f, 0xFFFFFFFF.toInt(), Theme.display, 1).apply {
        setPadding(dp(8), dp(3), dp(8), dp(4))
        background = rounded(Theme.p.deal, 8f)
    }
    val fav = IconView(ctx, Icon.HEART, 0xFF4C566A.toInt(), 19)
    private val favWrap = FrameLayout(ctx)
    private val storeColor = View(ctx)
    private val store: TextView = ctx.text("", 12f, Theme.p.text2, Theme.semibold, 1)
    private val title: TextView = ctx.text("", 14.5f, Theme.p.text, Theme.medium, 2).apply { minLines = 2 }
    private val sub: TextView = ctx.text("", 12f, Theme.p.text3, Theme.regular, 1)
    private val price: TextView = ctx.text("", 27f, Theme.p.text, Theme.display, 1)
    private val old: TextView = ctx.text("", 12.5f, Theme.p.text3, Theme.medium, 1).apply {
        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    }
    private val warn: TextView = ctx.pill("Fiyatı kontrol edin", Theme.p.warn, Theme.p.warnSoft, 11f)

    init {
        orientation = VERTICAL
        background = ripple(surfaceBg(20f), 20f)
        setPadding(dp(6), dp(6), dp(6), dp(4))
        val mediaWrap = FrameLayout(ctx).apply {
            background = rounded(0xFFFFFFFF.toInt(), 15f)
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) =
                    outline.setRoundRect(0, 0, view.width, view.height, dp(15).toFloat())
            }
        }
        media.addView(mono, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        photo.setPadding(dp(12), dp(12), dp(12), dp(12))
        media.addView(photo, FrameLayout.LayoutParams(MATCH, MATCH))
        media.addView(discount, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(8), dp(8), 0, 0) })
        favWrap.addView(View(ctx).apply { background = rounded(0xF2FFFFFF.toInt(), 99f, 0x1A0E1420, 1f) },
            FrameLayout.LayoutParams(dp(34), dp(34), Gravity.CENTER))
        favWrap.addView(fav, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        media.addView(favWrap, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END))
        mediaWrap.addView(media, FrameLayout.LayoutParams(MATCH, WRAP))
        addView(mediaWrap, LinearLayout.LayoutParams(MATCH, WRAP))
        val body = ctx.vbox().apply { setPadding(dp(8), dp(10), dp(8), 0) }
        val meta = ctx.hbox()
        meta.addView(storeColor, LinearLayout.LayoutParams(dp(9), dp(9)))
        meta.addView(store, lp(0, WRAP, l = dp(6), weight = 1f))
        body.addView(meta)
        body.addView(title, lp(t = dp(6)))
        body.addView(sub, lp(t = dp(2)))
        body.addView(warn, lp(WRAP, WRAP, t = dp(6)))
        addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))  // aynı satırdaki kartlarda fiyatlar aynı hizada
        val pr = ctx.hbox(Gravity.BOTTOM).apply { setPadding(dp(8), dp(8), dp(8), dp(8)) }
        pr.addView(price)
        pr.addView(old, lp(WRAP, WRAP, l = dp(6), b = dp(3)))
        addView(pr, LinearLayout.LayoutParams(MATCH, WRAP))
        pressFeedback()
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
        if (pct != null) { discount.text = "%$pct"; discount.contentDescription = "Yüzde $pct indirim (market beyanı)" }
        storeColor.background = rounded(Theme.store(o.storeId), 2.5f)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, today)
        store.text = if (v.kind == ValidityKind.UNKNOWN || v.kind == ValidityKind.ACTIVE) storeName else "$storeName, ${v.label}"
        title.text = o.displayTitle
        val s = o.subtitle
        sub.text = when {
            o.optionCount >= 2 -> "${o.optionCount} seçenek"
            s != null -> s
            o.channel == "online" -> "Online fiyat"
            else -> "Mağaza fiyatı"
        }
        price.text = priceSpan(Money.format(o.price))
        old.visibility = if (o.storeDeclaredOldPrice != null) VISIBLE else GONE
        o.storeDeclaredOldPrice?.let { old.text = Money.format(it) }
        warn.visibility = if (o.pagePriceConflict != null) VISIBLE else GONE
        fav.icon = if (isFav) Icon.HEART_FILLED else Icon.HEART
        fav.color = if (isFav) Theme.p.danger else 0xFF4C566A.toInt()
        favWrap.contentDescription = if (isFav) "Favoriden çıkar" else "Favoriye ekle"
        contentDescription = "${o.displayTitle}, ${Money.format(o.price)}, $storeName"
    }

    /** Favori düğmesine dokunma (kartın kendisinden ayrı). */
    fun onFav(action: () -> Unit) { favWrap.isClickable = true; favWrap.setOnClickListener { action() } }
}
