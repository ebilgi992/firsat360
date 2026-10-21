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
    // Raf etiketi kartı: beyaz ürün alanı, altta sarı fiyat etiketi (büyük lira, üstte kuruş), indirim kırmızı damga.
    private val media = SquareFrame(ctx)
    private val mono = Monogram(ctx, 72)
    private val photo = NetImage(ctx, 0f).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
    private val discount: TextView = ctx.text("", 17f, 0xFFFFFFFF.toInt(), Theme.display, 1).apply {
        setPadding(dp(8), dp(2), dp(8), dp(3))
        background = rounded(Theme.p.deal, 2f)
        rotation = -5f
    }
    val fav = IconView(ctx, Icon.HEART, Theme.p.text2, 20)
    private val favWrap = FrameLayout(ctx)
    private val store: TextView = ctx.storeBadge("bim", "BİM")
    private val validity: TextView = ctx.text("", 12f, Theme.p.text3, Theme.medium, 1)
    private val title: TextView = ctx.text("", 14f, Theme.p.text, Theme.semibold, 2).apply { minLines = 2 }
    private val sub: TextView = ctx.text("", 12f, Theme.p.text2, Theme.regular, 1)
    private val price: TextView = ctx.text("", 30f, Theme.p.onLabel, Theme.display, 1)
    private val old: TextView = ctx.text("", 12.5f, Theme.blend(Theme.p.onLabel, Theme.p.label, 0.35f), Theme.medium, 1).apply {
        paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
    }
    private val warn: TextView = ctx.pill("Fiyatı kontrol edin", Theme.p.warn, Theme.p.warnSoft, 11f)

    init {
        orientation = VERTICAL
        background = ripple(rounded(Theme.p.surface, 6f), 6f)
        clipToOutline = true
        outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) =
                outline.setRoundRect(0, 0, view.width, view.height, dp(6).toFloat())
        }
        media.setBackgroundColor(0xFFFFFFFF.toInt())
        media.addView(mono, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        photo.setBackgroundColor(0xFFFFFFFF.toInt())
        photo.setPadding(dp(12), dp(12), dp(12), dp(12))
        media.addView(photo, FrameLayout.LayoutParams(MATCH, MATCH))
        media.addView(discount, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(6), dp(10), 0, 0) })
        favWrap.background = rounded(0xF2FFFFFF.toInt(), 6f, 0x1F14213D)
        favWrap.addView(fav, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        media.addView(favWrap, FrameLayout.LayoutParams(dp(48), dp(48), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(4), dp(4), 0) })
        addView(media, LinearLayout.LayoutParams(MATCH, WRAP))
        val body = ctx.vbox().apply { setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val meta = ctx.hbox()
        meta.addView(store)
        meta.addView(validity, lp(0, WRAP, l = dp(6), weight = 1f))
        body.addView(meta)
        body.addView(title, lp(t = dp(8)))
        body.addView(sub, lp(t = dp(3)))
        body.addView(warn, lp(WRAP, WRAP, t = dp(6)))
        addView(body, LinearLayout.LayoutParams(MATCH, 0, 1f))  // aynı satırdaki kartlarda etiketler aynı hizada
        // Fiyat etiketi: tam genişlik sarı şerit, alt kenarında hafif koyu çizgi (rafa takılı etiket)
        val tag = ctx.vbox().apply {
            setPadding(dp(10), dp(8), dp(10), dp(9))
            background = android.graphics.drawable.LayerDrawable(arrayOf(
                android.graphics.drawable.ColorDrawable(Theme.p.labelShade),
                android.graphics.drawable.ColorDrawable(Theme.p.label))).apply { setLayerInset(1, 0, 0, 0, dp(3)) }
        }
        // Eski fiyat üstte, yeni fiyat altta büyük: dar kartta (≈134 dp) "1.299,⁹⁰ ₺" ile yan yana sığmaz
        tag.addView(old)
        tag.addView(price)
        addView(tag, LinearLayout.LayoutParams(MATCH, WRAP))
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
        store.text = storeName
        store.setTextColor(Theme.onStore(o.storeId))
        store.background = rounded(Theme.store(o.storeId), 3f)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, today)
        validity.text = if (v.kind == ValidityKind.UNKNOWN) "" else v.label
        validity.setTextColor(validityColor(v.kind).first)
        title.text = o.displayTitle
        val s = o.subtitle
        sub.text = s ?: (if (o.channel == "online") "Online fiyat" else "Mağaza fiyatı")
        price.text = priceSpan(Money.format(o.price))
        old.visibility = if (o.storeDeclaredOldPrice != null) VISIBLE else GONE
        o.storeDeclaredOldPrice?.let { old.text = Money.format(it) }
        warn.visibility = if (o.pagePriceConflict != null) VISIBLE else GONE
        fav.icon = if (isFav) Icon.HEART_FILLED else Icon.HEART
        fav.color = if (isFav) Theme.p.danger else 0xFF4B5670.toInt()
        favWrap.contentDescription = if (isFav) "Favoriden çıkar" else "Favoriye ekle"
        contentDescription = "${o.displayTitle}, ${Money.format(o.price)}, $storeName"
    }

    /** Favori düğmesine dokunma (kartın kendisinden ayrı). */
    fun onFav(action: () -> Unit) { favWrap.isClickable = true; favWrap.setOnClickListener { action() } }
}
