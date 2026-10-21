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

// ---- Bileşenler (1.5.0 "market rafı" dili): köşeler kâğıt/etiket gibi küçük, gölge yerine düz yüzey; yuvarlak hap yok.

/** Küçük etiket (raf etiketi gibi köşeli). */
fun Context.pill(label: String, fg: Int, bg: Int, sizeSp: Float = 11.5f, face: Typeface = Theme.semibold): TextView =
    text(label, sizeSp, fg, face, lines = 1).apply {
        setPadding(dp(7), dp(3), dp(7), dp(3))
        background = rounded(bg, 3f)
    }

fun Context.chip(label: String, selected: Boolean, accent: Int = Theme.p.accent, onClick: () -> Unit): TextView =
    text(label, 14.5f, if (selected) Theme.p.onAccent else Theme.p.text, if (selected) Theme.semibold else Theme.medium, 1).apply {
        setPadding(dp(14), dp(14), dp(14), dp(14))
        background = ripple(if (selected) rounded(accent, 6f) else rounded(Theme.p.surface, 6f, Theme.p.outline, 1.5f), 6f)
        if (selected) contentDescription = "$label (seçili)"
        onTap(onClick)
    }

/** Birincil: kalem mavisi dolu. İkincil: çizgili (yazı renginde ince çerçeve). */
fun Context.button(label: String, icon: Icon?, primary: Boolean, onClick: () -> Unit): LinearLayout = hbox(Gravity.CENTER).apply {
    val fg = if (primary) Theme.p.onAccent else Theme.p.text
    setPadding(dp(18), dp(13), dp(18), dp(13))
    minimumHeight = dp(48)
    background = ripple(if (primary) rounded(Theme.p.accent, 8f)
        else rounded(Theme.p.surface, 8f, Theme.tint(Theme.p.text, 0.55f), 1.5f), 8f)
    if (icon != null) { addView(IconView(context, icon, fg, 20)); addView(space(dp(8))) }
    addView(text(label, 15.5f, fg, Theme.semibold, 1))
    onTap(onClick)
}

fun Context.iconButton(icon: Icon, tint: Int = Theme.p.text, bg: Int = Theme.p.surface, desc: String, onClick: () -> Unit): FrameLayout =
    FrameLayout(this).apply {
        val s = dp(48)
        layoutParams = LinearLayout.LayoutParams(s, s)
        background = ripple(rounded(bg, 8f, Theme.tint(tint, 0.35f), 1.5f), 8f)
        contentDescription = desc
        addView(IconView(context, icon, tint, 22), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        onTap(onClick)
    }

/** Bölüm başlığı: dar tabela harfiyle büyük, sola hizalı; eylem düz metin bağlantı. */
fun Context.sectionHeader(title: String, action: String? = null, onAction: (() -> Unit)? = null): LinearLayout = hbox(Gravity.BOTTOM).apply {
    setPadding(dp(20), dp(30), dp(20), dp(2))
    addView(text(title, 25f, Theme.p.text, Theme.displayBold, 1).apply {
        accessibilityHeading()
    }, lp(0, WRAP, b = dp(10), weight = 1f))
    if (action != null && onAction != null) {
        addView(text(action, 14.5f, Theme.p.accent, Theme.semibold).apply {
            setPadding(dp(12), dp(14), dp(4), dp(10)); minimumHeight = dp(48); gravity = Gravity.CENTER_VERTICAL
            contentDescription = "$title: $action"; onTap(onAction) })
    }
}

fun TextView.accessibilityHeading() {
    if (android.os.Build.VERSION.SDK_INT >= 28) isAccessibilityHeading = true
}

/**
 * Raf etiketi fiyatı: lira büyük, kuruş üstte küçük, ₺ küçük ("299,⁹⁰ ₺" gibi). [Money.format] çıktısından üretilir.
 */
fun priceSpan(formatted: String): CharSequence {
    val m = Regex("""^([\d.]+)(,\d\d)\s*₺$""").find(formatted) ?: return formatted
    val (whole, frac) = m.destructured
    val sb = android.text.SpannableStringBuilder(whole)
    val s1 = sb.length
    sb.append(frac)
    sb.setSpan(android.text.style.RelativeSizeSpan(0.5f), s1, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    sb.setSpan(android.text.style.SuperscriptSpan(), s1, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    val s2 = sb.length
    sb.append(" ₺")
    sb.setSpan(android.text.style.RelativeSizeSpan(0.45f), s2, sb.length, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
    return sb
}

/** Fiyat metni (tabela harfi + raf etiketi biçimi). Ekran okuyucu için düz biçim içerik açıklamasında kalır. */
fun Context.priceText(formatted: String, sizeSp: Float, color: Int = Theme.p.text): TextView =
    text(priceSpan(formatted), sizeSp, color, Theme.display, 1).apply { contentDescription = formatted }

/** Ürün görseli kullanım izni olmadığı için kategori renginde marka monogramı. */
class Monogram(ctx: Context, sizeDp: Int) : View(ctx) {
    private val size = dp(sizeDp)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; typeface = Theme.display }
    private var label = ""
    private var fg = 0
    fun bind(offer: Offer) {
        val base = offer.brand?.takeIf { it.length > 1 } ?: offer.name
        val words = TurkishText.words(base, " -&.").filter { it[0].isLetterOrDigit() }
        label = TurkishText.upper(if (words.size >= 2) "${words[0][0]}${words[1][0]}" else base.take(2))
        val hue = Theme.category(offer.category)
        fg = if (Theme.p.dark) Theme.blend(hue, 0xFFFFFFFF.toInt(), 0.35f) else hue
        background = rounded(Theme.blend(Theme.p.surface, hue, if (Theme.p.dark) 0.22f else 0.12f), sizeDp(6f))
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

/** Market etiketi: marka renginde köşeli tabela, dar harf. */
fun Context.storeBadge(storeId: String, name: String, small: Boolean = true): TextView =
    pill(name, Theme.onStore(storeId), Theme.store(storeId), if (small) 13f else 15f, Theme.displayBold)

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
    private val photo = NetImage(ctx, 6f).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER }
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
        photo.background = rounded(0xFFFFFFFF.toInt(), 6f)
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
        price = ctx.text("", 24f, Theme.p.text, Theme.display, 1, Gravity.END)
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
        store.background = rounded(Theme.store(o.storeId), 3f)
        val v = Dates.status(o.validFrom ?: catalog?.validFrom, o.validTo ?: catalog?.validTo, today)
        validity.text = when (v.kind) { ValidityKind.UNKNOWN -> catalog?.title ?: ""; else -> v.label }
        validity.setTextColor(if (v.kind == ValidityKind.UNKNOWN) Theme.p.text3 else validityColor(v.kind).first)
        title.text = o.displayTitle
        sub.text = listOfNotNull(o.subtitle, o.condition?.takeIf { it.length < 40 }).joinToString(", ").ifEmpty { o.catalogTitleFallback() }
        price.text = priceSpan(Money.format(o.price))
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
    // Broşür kâğıdı: kart çerçevesi yok; kapak, ince gölgeli bir kâğıt gibi zemin üzerinde durur, bilgiler altında.
    val w = if (wide) MATCH else catalogCardWidth()
    layoutParams = lp(w, WRAP, r = if (wide) 0 else dp(14))
    background = ripple(null, 6f)
    val v = Dates.status(c.validFrom, c.validTo, today)
    contentDescription = "$storeName, ${c.title}, ${v.label}. Kataloğu açmak için dokunun"

    val frame = paperFrame(if (wide) 0.56f else 1.36f, Theme.blend(Theme.p.surface, Theme.store(c.storeId), if (Theme.p.dark) 0.16f else 0.07f))
    // Yer tutucu (kapak yokken veya yüklenemezse): market tabelası şeridi + market adı
    val ph = vbox().apply {
        addView(View(context).apply { setBackgroundColor(Theme.store(c.storeId)) }, lp(MATCH, dp(10)))
        val short = Theme.storeShort[c.storeId] ?: storeName.take(2)
        addView(text(short, 26f, Theme.p.text, Theme.display, 1), lp(WRAP, WRAP, l = dp(12), t = dp(12)))  // marka rengi üst şeritte
        addView(IconView(context, if (c.hasPdf) Icon.PDF else Icon.CATALOG, Theme.p.text3, 20), lp(WRAP, WRAP, l = dp(12), t = dp(6)))
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
        frame.addView(pill("Yeni", Theme.p.onLabel, Theme.p.label, 13f, Theme.displayBold),
            FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(8), dp(8), 0) })
    val count = when {
        c.isPoster -> if (c.hasPages) "${c.pages.size} afiş" else "Afiş"
        c.hasPdf -> "PDF"
        c.hasPages -> "${c.pages.size} sayfa"
        c.offerCount > 0 -> "${c.offerCount} ürün"
        c.kind == "flipbook" -> "Katalog"
        else -> null
    }
    if (count != null) frame.addView(pill(count, 0xFFFFFFFF.toInt(), 0xCC14213D.toInt(), 12.5f, Theme.displaySemi),
        FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.START).apply { setMargins(dp(8), 0, 0, dp(8)) })
    addView(frame, LinearLayout.LayoutParams(MATCH, WRAP).apply { setMargins(0, dp(2), 0, 0) })

    val body = vbox().apply { setPadding(dp(2), dp(10), dp(2), dp(6)) }
    val top = hbox()
    top.addView(text(storeName, 17f, Theme.p.text, Theme.displayBold, 1), lp(0, WRAP, weight = 1f))
    top.addView(IconView(context, when {
        c.hasPdf -> Icon.PDF; c.hasPages -> Icon.CATALOG; c.kind == "link_only" || c.kind == "flipbook" -> Icon.EXTERNAL
        else -> Icon.TAG }, Theme.p.text3, 16).apply { importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO })
    body.addView(top)
    body.addView(text(c.title, 14f, Theme.p.text2, Theme.medium, 2).apply { minLines = 2 }, lp(t = dp(4)))
    val (fg, _) = validityColor(v.kind)
    body.addView(text(v.label, 12.5f, if (v.kind == ValidityKind.ACTIVE) Theme.p.text3 else fg, Theme.semibold, 1), lp(t = dp(6)))
    addView(body)
    onTap(onClick)
}

/** Broşür kâğıdı: sabit oranlı, küçük köşeli, altında ince gölge (raftaki basılı broşür). */
fun Context.paperFrame(ratio: Float, fill: Int): RatioFrame = RatioFrame(this, ratio).apply {
    background = rounded(fill, 3f)
    elevation = dp(3).toFloat()
    clipToOutline = true
    outlineProvider = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) =
            outline.setRoundRect(0, 0, view.width, view.height, dp(3).toFloat())
    }
}

/** Yükleme iskeleti: yumuşak nabız animasyonlu yer tutucular. */
fun Context.skeleton(): LinearLayout = vbox(dp(20)).apply {
    fun block(h: Int, w: Int = MATCH, top: Int = dp(14)) = View(context).apply {
        background = rounded(Theme.p.surface2, 4f)
        layoutParams = lp(w, h, t = top)
    }
    addView(block(dp(30), dp(180), 0))
    addView(block(dp(16), dp(240)))
    addView(block(dp(48), top = dp(24)))
    val row = hbox()
    repeat(3) { row.addView(View(context).apply { background = rounded(Theme.p.surface2, 3f) }, lp(0, dp(150), r = dp(12), weight = 1f)) }
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
        // Boş raf: raf etiketi biçiminde simge
        val tag = FrameLayout(context).apply { background = rounded(Theme.p.label, 4f) }
        tag.addView(IconView(context, icon, Theme.p.onLabel, 30), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        addView(tag, LinearLayout.LayoutParams(dp(72), dp(56)))
        addView(text(title, 24f, Theme.p.text, Theme.displayBold, gravity = Gravity.CENTER), lp(t = dp(18)))
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

fun Context.card(pad: Int = dp(16)): LinearLayout = vbox(pad, rounded(Theme.p.surface, 6f))

fun Context.notice(icon: Icon, body: String, fg: Int, bg: Int): LinearLayout = hbox(Gravity.TOP).apply {
    setPadding(dp(14), dp(12), dp(14), dp(12))
    background = rounded(bg, 6f)
    addView(IconView(context, icon, fg, 20))
    addView(text(body, 13f, fg, Theme.medium), lp(0, WRAP, l = dp(10), weight = 1f))
}

/** Izgara / liste görünüm seçici (iki simgeli küçük segment). */
fun Context.viewToggle(grid: Boolean, onChange: (Boolean) -> Unit): LinearLayout = hbox().apply {
    setPadding(dp(3), dp(3), dp(3), dp(3))
    background = rounded(Theme.p.surface2, 8f)
    for ((g, icon, label) in listOf(Triple(true, Icon.GRID, "Izgara görünümü"), Triple(false, Icon.LIST, "Liste görünümü"))) {
        val on = g == grid
        val cell = FrameLayout(context).apply {
            background = if (on) rounded(Theme.p.surface, 6f, Theme.p.outline) else null
            contentDescription = label + if (on) " (seçili)" else ""
            addView(IconView(context, icon, if (on) Theme.p.text else Theme.p.text3, 18), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
            onTap { if (!on) onChange(g) }
        }
        addView(cell, LinearLayout.LayoutParams(dp(48), dp(40)))
    }
}
