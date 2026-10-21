package com.firsat360.app.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.firsat360.app.core.Catalog
import com.firsat360.app.core.Dates
import com.firsat360.app.core.Money
import com.firsat360.app.core.Offer
import com.firsat360.app.core.TurkishText
import com.firsat360.app.core.ValidityKind
import java.time.LocalDate

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

fun lp(w: Int = MATCH, h: Int = WRAP, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0, weight: Float = 0f) =
    LinearLayout.LayoutParams(w, h, weight).apply { setMargins(l, t, r, b) }

fun Context.text(s: CharSequence?, size: Float = 15f, color: Int = Theme.p.text, face: Typeface = Theme.regular,
                 lines: Int = 0, gravity: Int = Gravity.START): TextView = TextView(this).apply {
    text = s
    setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
    setTextColor(color)
    typeface = face
    includeFontPadding = false
    setLineSpacing(dp(2).toFloat(), 1f)
    setGravity(gravity)
    if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
}

fun Context.vbox(pad: Int = 0, bg: android.graphics.drawable.Drawable? = null): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    setPadding(pad, pad, pad, pad)
    background = bg
}

fun Context.hbox(gravity: Int = Gravity.CENTER_VERTICAL): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    setGravity(gravity)
}

fun Context.space(w: Int = 0, h: Int = 0): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(w, h) }
fun Context.flex(): View = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 1, 1f) }

fun Context.hscroll(child: View): HorizontalScrollView = HorizontalScrollView(this).apply {
    isHorizontalScrollBarEnabled = false
    overScrollMode = View.OVER_SCROLL_NEVER
    clipToPadding = false
    addView(child)
}

fun View.onTap(action: () -> Unit): View {
    isClickable = true
    isFocusable = true
    setOnClickListener { action() }
    return this
}

fun Context.pill(label: String, fg: Int, bg: Int, sizeSp: Float = 11.5f, face: Typeface = Theme.semibold): TextView =
    text(label, sizeSp, fg, face, lines = 1).apply {
        setPadding(dp(8), dp(4), dp(8), dp(4))
        background = rounded(bg, 99f)
    }

fun Context.chip(label: String, selected: Boolean, accent: Int = Theme.p.accent, onClick: () -> Unit): TextView =
    text(label, 13.5f, if (selected) Theme.p.onAccent else Theme.p.text, if (selected) Theme.semibold else Theme.medium, 1).apply {
        setPadding(dp(14), dp(9), dp(14), dp(9))
        background = ripple(if (selected) rounded(accent, 99f) else rounded(Theme.p.surface, 99f, Theme.p.outline), 99f)
        onTap(onClick)
    }

fun Context.button(label: String, icon: Icon?, primary: Boolean, onClick: () -> Unit): LinearLayout = hbox(Gravity.CENTER).apply {
    val fg = if (primary) Theme.p.onAccent else Theme.p.text
    setPadding(dp(18), dp(14), dp(18), dp(14))
    background = ripple(if (primary) rounded(Theme.p.accent, 14f) else rounded(Theme.p.surface, 14f, Theme.p.outline), 14f)
    if (icon != null) { addView(IconView(context, icon, fg, 20)); addView(space(dp(8))) }
    addView(text(label, 15f, fg, Theme.semibold, 1))
    onTap(onClick)
}

fun Context.iconButton(icon: Icon, tint: Int = Theme.p.text, bg: Int = Theme.p.surface, desc: String, onClick: () -> Unit): FrameLayout =
    FrameLayout(this).apply {
        val s = dp(42)
        layoutParams = LinearLayout.LayoutParams(s, s)
        background = ripple(rounded(bg, 99f, Theme.p.outline), 99f)
        contentDescription = desc
        addView(IconView(context, icon, tint, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        onTap(onClick)
    }

fun Context.sectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout = hbox().apply {
    setPadding(dp(20), dp(28), dp(20), dp(12))
    addView(text(title, 19.5f, Theme.p.text, Theme.bold), lp(0, WRAP, weight = 1f))
    if (action != null && onAction != null) {
        addView(text(action, 13.5f, Theme.p.accent, Theme.semibold).apply { setPadding(dp(8), dp(6), 0, dp(6)); onTap(onAction) })
    }
}

/** Ürün görseli kullanım izni olmadığı için kategori renginde marka monogramı. */
class Monogram(ctx: Context, sizeDp: Int) : View(ctx) {
    private val size = dp(sizeDp)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Theme.bold }
    private var label = ""
    private var fg = 0
    fun bind(offer: Offer) {
        val base = offer.brand?.takeIf { it.length > 1 } ?: offer.name
        val words = TurkishText.words(base, " -&.").filter { it[0].isLetterOrDigit() }
        label = TurkishText.upper(if (words.size >= 2) "${words[0][0]}${words[1][0]}" else base.take(2))
        val hue = Theme.category(offer.category)
        fg = if (Theme.p.dark) Theme.blend(hue, 0xFFFFFFFF.toInt(), 0.35f) else hue
        background = rounded(Theme.blend(Theme.p.surface, hue, if (Theme.p.dark) 0.22f else 0.12f), sizeDp(14f))
        invalidate()
    }
    private fun sizeDp(r: Float) = r * size / dp(56).toFloat()
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(size, size)
    override fun onDraw(c: Canvas) {
        paint.color = fg
        paint.textSize = height * 0.34f
        val y = height / 2f - (paint.descent() + paint.ascent()) / 2f
        c.drawText(label, width / 2f, y, paint)
    }
}

fun Context.storeBadge(storeId: String, name: String, small: Boolean = true): TextView =
    pill(name, Theme.onStore(storeId), Theme.store(storeId), if (small) 10.5f else 12.5f, Theme.bold)

fun validityColor(kind: ValidityKind): Pair<Int, Int> = when (kind) {
    ValidityKind.UPCOMING -> Theme.p.accent to Theme.p.accentSoft
    ValidityKind.STARTS_TODAY -> Theme.p.ok to Theme.p.okSoft
    ValidityKind.LAST_DAY -> Theme.p.danger to Theme.p.dangerSoft
    ValidityKind.EXPIRED -> Theme.p.text3 to Theme.p.surface2
    else -> Theme.p.text2 to Theme.p.surface2
}

/** Liste satırı (ListView'da yeniden kullanılır). */
class OfferRow(ctx: Context) : LinearLayout(ctx) {
    val mono = Monogram(ctx, 56)
    private val photo = NetImage(ctx, 14f).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
    private val thumb = android.widget.FrameLayout(ctx)
    private val store: TextView
    private val validity: TextView
    private val title: TextView
    private val sub: TextView
    private val price: TextView
    private val old: TextView
    val fav: IconView

    init {
        orientation = HORIZONTAL
        setGravity(Gravity.CENTER_VERTICAL)
        setPadding(dp(14), dp(14), dp(10), dp(14))
        layoutParams = android.widget.AbsListView.LayoutParams(MATCH, WRAP)
        thumb.addView(mono, android.widget.FrameLayout.LayoutParams(dp(56), dp(56)))
        photo.setBackgroundColor(0xFFFFFFFF.toInt())
        thumb.addView(photo, android.widget.FrameLayout.LayoutParams(dp(56), dp(56)))
        addView(thumb, LinearLayout.LayoutParams(dp(56), dp(56)))
        val mid = ctx.vbox()
        val meta = ctx.hbox()
        store = ctx.storeBadge("bim", "BİM")
        validity = ctx.text("", 11.5f, Theme.p.text3, Theme.medium, 1)
        meta.addView(store)
        meta.addView(validity, lp(WRAP, WRAP, l = dp(8)))
        mid.addView(meta)
        title = ctx.text("", 15f, Theme.p.text, Theme.semibold, 2)
        mid.addView(title, lp(t = dp(6)))
        sub = ctx.text("", 12.5f, Theme.p.text2, Theme.regular, 1)
        mid.addView(sub, lp(t = dp(3)))
        addView(mid, lp(0, WRAP, l = dp(12), r = dp(8), weight = 1f))
        val right = ctx.vbox().apply { setGravity(Gravity.END) }
        fav = IconView(ctx, Icon.HEART, Theme.p.text3, 22).apply { setPadding(0, 0, 0, 0) }
        // Hizalama 'right' kabının kendi gravity'si (END) ile sağlanır; LayoutParams'a ayrıca gravity verilmez.
        right.addView(fav, LinearLayout.LayoutParams(WRAP, WRAP))
        price = ctx.text("", 16.5f, Theme.p.text, Theme.bold, 1, Gravity.END)
        right.addView(price, lp(WRAP, WRAP, t = dp(8)))
        old = ctx.text("", 11.5f, Theme.p.text3, Theme.medium, 1, Gravity.END).apply {
            paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }
        right.addView(old, lp(WRAP, WRAP, t = dp(2)))
        addView(right)
        background = ripple(null, 0f)
    }

    fun bind(o: Offer, storeName: String, catalog: Catalog?, isFav: Boolean, today: LocalDate) {
        mono.bind(o)
        // Gerçek ürün görseli yalnızca kaynakta bu ürüne ait görsel varsa; yoksa monogram (başka ürünün görseli asla).
        if (o.imageUrl != null && com.firsat360.app.data.ImageLoader.enabled) {
            photo.visibility = VISIBLE
            photo.load(com.firsat360.app.core.Thumbs.small(o.imageUrl), dp(128), o.imageUrl) { photo.visibility = GONE }
        } else {
            photo.tag = null; photo.visibility = GONE
        }
        store.text = storeName
        store.setTextColor(Theme.onStore(o.storeId))
        store.background = rounded(Theme.store(o.storeId), 99f)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, today)
        validity.text = when (v.kind) { ValidityKind.UNKNOWN -> catalog?.title ?: ""; else -> v.label }
        validity.setTextColor(if (v.kind == ValidityKind.UNKNOWN) Theme.p.text3 else validityColor(v.kind).first)
        title.text = o.displayTitle
        sub.text = listOfNotNull(o.subtitle, o.condition?.takeIf { it.length < 40 }).joinToString(" · ").ifEmpty { o.catalogTitleFallback() }
        price.text = Money.format(o.price)
        if (o.storeDeclaredOldPrice != null) {
            old.visibility = VISIBLE
            old.text = Money.format(o.storeDeclaredOldPrice)
        } else old.visibility = GONE
        fav.icon = if (isFav) Icon.HEART_FILLED else Icon.HEART
        fav.color = if (isFav) Theme.p.danger else Theme.p.text3
        contentDescription = "${o.displayTitle}, ${Money.format(o.price)}, $storeName"
    }

    private fun Offer.catalogTitleFallback(): String = if (channel == "online") "Online fiyat" else "Mağaza fiyatı"
}

/** Genişliğe göre sabit en-boy oranlı kap (yükseklik = genişlik × oran): farklı oranlı kapaklar kartı bozmaz. */
class RatioFrame(ctx: Context, private val ratio: Float) : FrameLayout(ctx) {
    override fun onMeasure(w: Int, h: Int) {
        val width = MeasureSpec.getSize(w)
        super.onMeasure(w, MeasureSpec.makeMeasureSpec((width * ratio).toInt(), MeasureSpec.EXACTLY))
    }
}

/** Katalog kartı genişliği: dar ekranda (320 dp) da iki kart sığar, geniş ekranda 172 dp'de sabit kalır. */
fun Context.catalogCardWidth(): Int {
    val screen = resources.displayMetrics.widthPixels
    return minOf(dp(172), (screen - dp(20 + 12 + 24)) / 2)
}

/**
 * Tek tasarım sistemli katalog kartı: aynı satırdaki tüm kartlar eşit genişlik ve eşit yükseklikte.
 *  - Kapak alanı sabit 4:3 dikey oran; görsel FIT_CENTER ile kırpılmadan sığdırılır (farklı oranlar kartı bozmaz).
 *  - Kapak yoksa / yüklenemezse: market renginde monogram + katalog simgesi (boş kalmaz).
 *  - Başlık her zaman 2 satır yer kaplar (kısa başlıkta da), market adı ve tarih tek satır, taşma "…" ile.
 */
fun Context.catalogCard(c: Catalog, storeName: String, today: LocalDate, wide: Boolean = false,
                        onClick: () -> Unit): LinearLayout = vbox().apply {
    val w = if (wide) MATCH else catalogCardWidth()
    layoutParams = lp(w, WRAP, r = if (wide) 0 else dp(12))
    background = ripple(rounded(Theme.p.surface, 20f, Theme.p.outline), 20f)
    clipToOutline = true
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) =
            outline.setRoundRect(0, 0, view.width, view.height, dp(20).toFloat())
    }
    val v = Dates.status(c.validFrom, c.validTo, today)
    contentDescription = "$storeName, ${c.title}, ${v.label}. Kataloğu açmak için dokunun"

    val frame = RatioFrame(context, if (wide) 0.56f else 1.33f)
    frame.setBackgroundColor(Theme.blend(Theme.p.surface2, Theme.store(c.storeId), if (Theme.p.dark) 0.18f else 0.10f))
    // Yer tutucu (kapak yokken veya yüklenemezse görünür)
    val ph = vbox().apply {
        setGravity(Gravity.CENTER)
        val m = FrameLayout(context).apply { background = rounded(Theme.store(c.storeId), 16f) }
        val short = Theme.storeShort[c.storeId] ?: storeName.take(2)
        m.addView(text(short, if (short.length >= 4) 13f else 16f, Theme.onStore(c.storeId), Theme.bold),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        addView(m, LinearLayout.LayoutParams(dp(52), dp(52)))
        addView(IconView(context, if (c.hasPdf) Icon.PDF else Icon.CATALOG, Theme.p.text3, 20), lp(WRAP, WRAP, t = dp(10)))
    }
    frame.addView(ph, FrameLayout.LayoutParams(MATCH, MATCH))
    if (c.coverUrl != null && com.firsat360.app.data.ImageLoader.enabled) {
        val img = NetImage(context, 0f).apply {
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }
        ph.visibility = View.INVISIBLE
        img.load(c.coverUrl, dp(360)) { img.visibility = View.GONE; ph.visibility = View.VISIBLE }
        frame.addView(img, FrameLayout.LayoutParams(MATCH, MATCH))
    }
    val first = Dates.dateTime(c.firstSeenAt)
    if (first != null && java.time.Duration.between(first, java.time.OffsetDateTime.now()).toHours() in -6..48)
        frame.addView(pill("YENİ", 0xFFFFFFFF.toInt(), Theme.p.accent, 10f, Theme.bold),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.START).apply { setMargins(dp(8), dp(8), 0, 0) })
    val count = when {
        c.isPoster -> if (c.hasPages) "${c.pages.size} afiş" else "Afiş"
        c.hasPdf -> "PDF"
        c.hasPages -> "${c.pages.size} sayfa"
        c.offerCount > 0 -> "${c.offerCount} ürün"
        c.kind == "flipbook" -> "Katalog"
        else -> null
    }
    if (count != null) frame.addView(pill(count, 0xFFFFFFFF.toInt(), 0x99000000.toInt(), 10.5f),
        FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, dp(8), dp(8)) })
    addView(frame, LinearLayout.LayoutParams(MATCH, WRAP))

    val body = vbox().apply { setPadding(dp(12), dp(10), dp(12), dp(12)) }
    val top = hbox()
    top.addView(storeBadge(c.storeId, storeName).apply { maxWidth = dp(120); ellipsize = TextUtils.TruncateAt.END })
    top.addView(flex())
    top.addView(IconView(context, when {
        c.hasPdf -> Icon.PDF; c.hasPages -> Icon.CATALOG; c.kind == "link_only" || c.kind == "flipbook" -> Icon.EXTERNAL
        else -> Icon.TAG }, Theme.p.text3, 16).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
    body.addView(top)
    body.addView(text(c.title, 13.5f, Theme.p.text, Theme.semibold, 2).apply { minLines = 2 }, lp(t = dp(8)))
    val (fg, _) = validityColor(v.kind)
    body.addView(text(v.label, 11.5f, fg, Theme.medium, 1), lp(t = dp(6)))
    addView(body)
    onTap(onClick)
}

/** Yükleme iskeleti: yumuşak nabız animasyonlu yer tutucular. */
fun Context.skeleton(): LinearLayout = vbox(dp(20)).apply {
    fun block(h: Int, w: Int = MATCH, top: Int = dp(14)) = View(context).apply {
        background = rounded(Theme.p.surface2, 12f)
        layoutParams = lp(w, h, t = top)
    }
    addView(block(dp(30), dp(180), 0))
    addView(block(dp(16), dp(240)))
    addView(block(dp(48), top = dp(24)))
    val row = hbox()
    repeat(4) { row.addView(View(context).apply { background = rounded(Theme.p.surface2, 18f) }, lp(0, dp(84), r = dp(10), weight = 1f)) }
    addView(row, lp(t = dp(24)))
    repeat(5) { addView(block(dp(76))) }
    val anim = ValueAnimator.ofFloat(0.45f, 1f).apply {
        duration = 800; repeatMode = ValueAnimator.REVERSE; repeatCount = ValueAnimator.INFINITE
        addUpdateListener { a -> alpha = a.animatedValue as Float }
    }
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = anim.start()
        override fun onViewDetachedFromWindow(v: View) = anim.cancel()
    })
}

fun Context.emptyState(icon: Icon, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout =
    vbox(dp(32)).apply {
        setGravity(Gravity.CENTER_HORIZONTAL)
        val circle = FrameLayout(context).apply { background = rounded(Theme.p.accentSoft, 99f) }
        circle.addView(IconView(context, icon, Theme.p.accent, 30), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        addView(circle, LinearLayout.LayoutParams(dp(72), dp(72)))
        addView(text(title, 18f, Theme.p.text, Theme.bold, gravity = Gravity.CENTER), lp(t = dp(18)))
        addView(text(body, 14f, Theme.p.text2, Theme.regular, gravity = Gravity.CENTER), lp(t = dp(8)))
        if (action != null && onAction != null) addView(context.button(action, null, true, onAction), lp(WRAP, WRAP, t = dp(20)))
    }

fun Context.infoRow(icon: Icon, label: String, value: String, valueColor: Int = Theme.p.text): LinearLayout = hbox(Gravity.TOP).apply {
    setPadding(0, dp(10), 0, dp(10))
    addView(IconView(context, icon, Theme.p.text3, 20))
    val col = vbox()
    col.addView(text(label, 12f, Theme.p.text3, Theme.medium))
    col.addView(text(value, 14.5f, valueColor, Theme.medium), lp(t = dp(3)))
    addView(col, lp(0, WRAP, l = dp(12), weight = 1f))
}

fun Context.card(pad: Int = dp(16)): LinearLayout = vbox(pad, rounded(Theme.p.surface, 20f, Theme.p.outline))

fun Context.notice(icon: Icon, body: String, fg: Int, bg: Int): LinearLayout = hbox(Gravity.TOP).apply {
    setPadding(dp(14), dp(12), dp(14), dp(12))
    background = rounded(bg, 14f)
    addView(IconView(context, icon, fg, 20))
    addView(text(body, 13f, fg, Theme.medium), lp(0, WRAP, l = dp(10), weight = 1f))
}

/** Izgara / liste görünüm seçici (iki simgeli küçük segment). */
fun Context.viewToggle(grid: Boolean, onChange: (Boolean) -> Unit): LinearLayout = hbox().apply {
    setPadding(dp(3), dp(3), dp(3), dp(3))
    background = rounded(Theme.p.surface2, 12f)
    for ((g, icon, label) in listOf(Triple(true, Icon.GRID, "Izgara görünümü"), Triple(false, Icon.LIST, "Liste görünümü"))) {
        val on = g == grid
        val cell = FrameLayout(context).apply {
            background = if (on) rounded(Theme.p.surface, 10f, Theme.p.outline) else null
            contentDescription = label + if (on) " (seçili)" else ""
            addView(IconView(context, icon, if (on) Theme.p.text else Theme.p.text3, 18), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            onTap { if (!on) onChange(g) }
        }
        addView(cell, LinearLayout.LayoutParams(dp(36), dp(30)))
    }
}
