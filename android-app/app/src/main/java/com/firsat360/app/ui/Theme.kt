package com.firsat360.app.ui

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList

class Palette(
    val dark: Boolean,
    val bg: Int, val surface: Int, val surface2: Int, val outline: Int,
    val text: Int, val text2: Int, val text3: Int,
    val accent: Int, val accentSoft: Int, val onAccent: Int,
    val warn: Int, val warnSoft: Int, val ok: Int, val okSoft: Int, val danger: Int, val dangerSoft: Int,
    val ripple: Int,
    /** Premium vurgu: koyu "mürekkep" kahraman zemini, ikinci vurgu ve altın. */
    val ink: Int = 0xFF0B0F1A.toInt(), val ink2: Int = 0xFF2A1036.toInt(), val accent2: Int = 0xFFFF7A45.toInt(),
    val gold: Int = 0xFFE9B949.toInt(), val deal: Int = 0xFFE5484D.toInt(),
    /** Raf etiketi sarısı (fiyat etiketi, broşürlük rafı) ve üzerindeki yazı. */
    val label: Int = 0xFFFFD60A.toInt(), val onLabel: Int = 0xFF14213D.toInt(), val labelShade: Int = 0xFFE3BC00.toInt(),
)

object Theme {
    lateinit var p: Palette
    lateinit var regular: Typeface
    lateinit var medium: Typeface
    lateinit var semibold: Typeface
    lateinit var bold: Typeface
    /** Barlow Condensed: market tabelası sesi — fiyatlar, başlıklar, market adları. */
    lateinit var display: Typeface
    lateinit var displayBold: Typeface
    lateinit var displaySemi: Typeface
    var density = 3f

    // Tasarım dili (1.6.0 "Vitrin"): içerik (broşür kapakları) renk verir; arayüz sakin grafit/porselen, tek vurgu
    // kehribar (fiyat ve etkin öğeler), kırmızı YALNIZCA indirim. Kartlar ince çizgili ve katmanlı (gölge yerine derinlik).
    private val light = Palette(
        dark = false,
        bg = 0xFFF3F4F7.toInt(), surface = 0xFFFFFFFF.toInt(), surface2 = 0xFFEDEFF3.toInt(), outline = 0xFFE1E4EA.toInt(),
        text = 0xFF0E1420.toInt(), text2 = 0xFF4C566A.toInt(), text3 = 0xFF5F687D.toInt(),
        accent = 0xFF9A6400.toInt(), accentSoft = 0xFFFFF1D1.toInt(), onAccent = Color.WHITE,
        warn = 0xFF9A5B00.toInt(), warnSoft = 0xFFFFF1CC.toInt(), ok = 0xFF1E7F4F.toInt(), okSoft = 0xFFDDF2E6.toInt(),
        danger = 0xFFD62828.toInt(), dangerSoft = 0xFFFBE2E2.toInt(), ripple = 0x1F0E1420,
        ink = 0xFF0C1017.toInt(), ink2 = 0xFF151B25.toInt(), accent2 = 0xFFD62828.toInt(), gold = 0xFFFFC53D.toInt(),
        deal = 0xFFD62828.toInt(),
        label = 0xFFFFC53D.toInt(), onLabel = 0xFF14110A.toInt(), labelShade = 0xFFE0A520.toInt(),
    )
    private val darkP = Palette(
        dark = true,
        bg = 0xFF0C1017.toInt(), surface = 0xFF151B25.toInt(), surface2 = 0xFF1D2532.toInt(), outline = 0xFF232B38.toInt(),
        text = 0xFFF3F5F8.toInt(), text2 = 0xFFA4AEC0.toInt(), text3 = 0xFF8A93AA.toInt(),
        accent = 0xFFFFC53D.toInt(), accentSoft = 0xFF3A2E12.toInt(), onAccent = 0xFF14110A.toInt(),
        warn = 0xFFFFC857.toInt(), warnSoft = 0xFF332810.toInt(), ok = 0xFF5BD69A.toInt(), okSoft = 0xFF12301F.toInt(),
        danger = 0xFFFF6B6B.toInt(), dangerSoft = 0xFF3A1A1E.toInt(), ripple = 0x29FFFFFF,
        ink = 0xFF080B10.toInt(), ink2 = 0xFF151B25.toInt(), accent2 = 0xFFD62828.toInt(), gold = 0xFFFFC53D.toInt(),
        deal = 0xFFD62828.toInt(),
        label = 0xFFFFC53D.toInt(), onLabel = 0xFF14110A.toInt(), labelShade = 0xFFD9A11F.toInt(),
    )

    /** Varsayılan marka renkleri; feed'deki "color" alanı geldiğinde [registerStores] ile güncellenir. */
    val storeColors = mutableMapOf(
        "a101" to 0xFF00A0E3.toInt(), "bim" to 0xFFE30613.toInt(),
        "sok" to 0xFFF2B600.toInt(), "migros" to 0xFFF27A00.toInt(),
        "tkkoop" to 0xFF0B8A3E.toInt(), "hakmar" to 0xFFD7141A.toInt(), "watsons" to 0xFF00A3AD.toInt(),
        "metro" to 0xFF00327D.toInt(), "bizimtoptan" to 0xFFE2001A.toInt(), "gratis" to 0xFFE6007E.toInt(),
    )
    val storeShort = mutableMapOf("a101" to "A101", "bim" to "BİM", "sok" to "ŞOK", "migros" to "M", "tkkoop" to "TK",
        "hakmar" to "H", "watsons" to "W", "metro" to "MT", "bizimtoptan" to "BZ", "gratis" to "G")

    fun registerStores(stores: List<com.firsat360.app.core.Store>) {
        for (s in stores) {
            s.color?.let { storeColors[s.id] = it }
            s.short?.let { storeShort[s.id] = it }
        }
    }

    private val categoryHue = mapOf(
        "elektronik" to 0xFF4F46E5.toInt(), "ev-yasam" to 0xFF0E9F8E.toInt(), "spor" to 0xFF16A34A.toInt(),
        "bebek" to 0xFFEC4899.toInt(), "temizlik" to 0xFF0284C7.toInt(), "kisisel-bakim" to 0xFFA855F7.toInt(),
        "sut-kahvalti" to 0xFFEAB308.toInt(), "et-tavuk" to 0xFFDC2626.toInt(), "dondurulmus" to 0xFF06B6D4.toInt(),
        "atistirmalik" to 0xFFEA580C.toInt(), "icecek" to 0xFF2563EB.toInt(), "temel-gida" to 0xFF65A30D.toInt(),
        "saglik" to 0xFF059669.toInt(), "giyim" to 0xFFDB2777.toInt(), "oyuncak" to 0xFFF59E0B.toInt(),
    )

    /** [mode]: "system" | "light" | "dark" (Ayarlar › Görünüm). */
    fun init(ctx: Context, mode: String = "system") {
        val night = when (mode) {
            "dark" -> true
            "light" -> false
            else -> (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        p = if (night) darkP else light
        density = ctx.resources.displayMetrics.density
        fun font(name: String, fallback: Int): Typeface = try {
            Typeface.createFromAsset(ctx.assets, "fonts/$name")
        } catch (e: RuntimeException) {
            Typeface.create(Typeface.SANS_SERIF, fallback)
        }
        regular = font("Barlow_400Regular.ttf", Typeface.NORMAL)
        medium = font("Barlow_500Medium.ttf", Typeface.NORMAL)
        semibold = font("Barlow_600SemiBold.ttf", Typeface.BOLD)
        bold = font("Barlow_700Bold.ttf", Typeface.BOLD)
        display = font("BarlowCondensed_800ExtraBold.ttf", Typeface.BOLD)
        displayBold = font("BarlowCondensed_700Bold.ttf", Typeface.BOLD)
        displaySemi = font("BarlowCondensed_600SemiBold.ttf", Typeface.BOLD)
    }

    fun store(id: String): Int = storeColors[id] ?: p.accent
    /** Mağaza renginin üzerindeki yazı rengi: açık zeminde koyu, koyu zeminde beyaz (WCAG göreli parlaklık). */
    fun onStore(id: String): Int = if (luminance(store(id)) > 0.19) 0xFF1A1400.toInt() else Color.WHITE

    fun luminance(c: Int): Double {
        fun ch(v: Int): Double { val x = v / 255.0; return if (x <= 0.03928) x / 12.92 else Math.pow((x + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch((c shr 16) and 0xFF) + 0.7152 * ch((c shr 8) and 0xFF) + 0.0722 * ch(c and 0xFF)
    }
    fun category(id: String): Int = categoryHue[id] ?: 0xFF64748B.toInt()

    fun tint(color: Int, alpha: Float): Int {
        val a = (alpha * 255).toInt().coerceIn(0, 255)
        return (a shl 24) or (color and 0x00FFFFFF)
    }

    /** Arka plan için renk karışımı (yüzey üzerine yarı saydam renk). */
    fun blend(base: Int, over: Int, t: Float): Int {
        fun ch(c: Int, s: Int) = (c shr s) and 0xFF
        fun mix(s: Int) = (ch(base, s) + (ch(over, s) - ch(base, s)) * t).toInt()
        return Color.rgb(mix(16), mix(8), mix(0))
    }
}

fun dp(v: Float): Int = (v * Theme.density + 0.5f).toInt()
fun dp(v: Int): Int = dp(v.toFloat())

fun rounded(color: Int, radiusDp: Float, strokeColor: Int? = null, strokeDp: Float = 1f): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        setCornerRadius(dp(radiusDp).toFloat())
        if (strokeColor != null) setStroke(dp(strokeDp), strokeColor)
    }

fun gradient(colors: IntArray, radiusDp: Float, orientation: GradientDrawable.Orientation =
    GradientDrawable.Orientation.TL_BR): GradientDrawable =
    GradientDrawable(orientation, colors).apply { setCornerRadius(dp(radiusDp).toFloat()) }

fun ripple(bg: android.graphics.drawable.Drawable?, radiusDp: Float): RippleDrawable {
    val mask = rounded(Color.WHITE, radiusDp)
    return RippleDrawable(ColorStateList.valueOf(Theme.p.ripple), bg, mask)
}
