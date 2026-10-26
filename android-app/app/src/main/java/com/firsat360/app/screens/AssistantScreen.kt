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
import com.firsat360.app.core.Assistant
import com.firsat360.app.core.AssistantReply
import com.firsat360.app.core.Dates
import com.firsat360.app.ui.*

/**
 * Fırsat Asistanı sohbet ekranı. Yanıtlar [Assistant] ile tamamen cihazda üretilir (ücretsiz, anahtarsız, internete
 * soru göndermez). Sohbet geçmişi uygulama açık kaldığı sürece korunur.
 */
class AssistantScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.HOME
    private var busy = false
    private var log: LinearLayout? = null
    private var scroll: ScrollView? = null
    private var chips: LinearLayout? = null
    private var input: EditText? = null

    private fun bot(): Assistant {
        val f = a.feed
        if (engine?.first !== f || engineDay != a.today) { engine = f to Assistant(f, a.today); engineDay = a.today }
        return engine!!.second
    }

    override fun create(): View {
        val root = a.vbox().apply { setBackgroundColor(Theme.p.bg) }

        // Başlık
        val top = a.hbox(Gravity.CENTER_VERTICAL).apply { setPadding(dp(12), dp(12), dp(16), dp(10)) }
        top.addView(a.iconButton(Icon.BACK, desc = "Geri") { a.onBackPressedCompat() })
        top.addView(android.widget.FrameLayout(a).apply {
            background = rounded(Theme.p.label, 14f)
            addView(IconView(a, Icon.SPARK, Theme.p.onLabel, 20), android.widget.FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
        }, lp(dp(40), dp(40), l = dp(10)))
        val tt = a.vbox()
        tt.addView(a.text("Fırsat Asistanı", 22f, Theme.p.text, Theme.display, 1).apply { accessibilityHeading() })
        tt.addView(a.text("Bütçe, ambalaj ve birim fiyat karşılaştırması", 12.5f, Theme.p.text2, Theme.regular, 1))
        top.addView(tt, lp(0, WRAP, l = dp(10), weight = 1f))
        top.addView(a.iconButton(Icon.REFRESH, Theme.p.text2, desc = "Sohbeti temizle") {
            if (busy) a.toast("Yanıt tamamlandıktan sonra temizleyebilirsin") else { history.clear(); engine = null; a.refreshCurrent() }
        })
        root.addView(top)

        // Mesajlar
        val l = a.vbox().apply { setPadding(dp(16), dp(6), dp(16), dp(16)) }
        val sv = ScrollView(a).apply { isFillViewport = true; addView(l) }
        root.addView(sv, lp(MATCH, 0, weight = 1f))
        log = l; scroll = sv

        // Hızlı yanıtlar
        val ch = a.hbox().apply { setPadding(dp(16), dp(4), dp(16), dp(6)) }
        chips = ch
        root.addView(a.hscroll(ch))

        // Giriş
        val bar = a.hbox(Gravity.CENTER_VERTICAL).apply { setPadding(dp(12), dp(6), dp(12), dp(12)) }
        val box = a.hbox(Gravity.CENTER_VERTICAL).apply { background = rounded(Theme.p.surface, 22f, Theme.p.outline, 1f) }
        val inp = EditText(a).apply {
            hint = "Ör. en ucuz yumurta, ŞOK'ta ne var?"
            contentDescription = "Asistana mesaj yazın"
            setHintTextColor(Theme.p.text3); setTextColor(Theme.p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15.5f); typeface = Theme.regular
            background = null; maxLines = 3
            filters = arrayOf(android.text.InputFilter.LengthFilter(400))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
            imeOptions = EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_EXTRACT_UI
            setPadding(dp(16), dp(12), dp(8), dp(12))
            setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { send(); true } else false }
        }
        input = inp
        box.addView(inp, lp(0, WRAP, weight = 1f))
        bar.addView(box, lp(0, WRAP, weight = 1f))
        bar.addView(a.iconButton(Icon.CHEVRON, Theme.p.onAccent, Theme.p.accent, "Gönder") { send() }.apply {
            background = rounded(Theme.p.accent, 24f) }, lp(dp(48), dp(48), l = dp(8)))
        root.addView(bar)

        if (history.isEmpty()) history += false to bot().hello()
        for ((mine, r) in history) render(mine, r)
        setChips(history.lastOrNull { !it.first }?.second?.suggestions?.ifEmpty { null } ?: bot().starters)
        sv.post { sv.fullScroll(View.FOCUS_DOWN) }
        return root
    }

    private fun send(text: String? = null) {
        val t = (text ?: input?.text?.toString() ?: "").trim()
        if (t.isEmpty()) return
        if (busy) { a.toast("Yanıt hazırlanıyor…"); return }
        busy = true
        val assistant = bot()
        input?.setText("")
        val mine = AssistantReply(t)
        history += true to mine
        render(true, mine)
        val typing = a.text("Yazıyor…", 13f, Theme.p.text3, Theme.medium)
        log?.addView(typing, lp(t = dp(8)))
        scrollDown()
        worker.execute {
            val r = try { assistant.reply(t) } catch (e: Exception) {
                com.firsat360.app.core.AppLog.w("asistan", e.javaClass.simpleName)
                AssistantReply("Soruyu işleyemedim. Ürün adı, market veya bütçe yazarak yeniden deneyebilirsin.")
            }
            a.runOnUiThread {
                busy = false
                history += false to r
                while (history.size > 60) history.removeAt(0)
                if (log?.isAttachedToWindow == true && !a.isDestroyed && !a.isFinishing) {
                    log?.removeView(typing)
                    render(false, r)
                    log?.announceForAccessibility(r.text)
                    setChips(r.suggestions.ifEmpty { assistant.starters })
                    scrollDown()
                }
            }
        }
    }


    private fun scrollDown() { scroll?.post { scroll?.fullScroll(View.FOCUS_DOWN) } }

    private fun setChips(list: List<String>) {
        val ch = chips ?: return
        ch.removeAllViews()
        for (s in list.take(5)) ch.addView(a.chip(s, false) { send(s) }, lp(WRAP, WRAP, r = dp(8)))
    }

    private fun render(mine: Boolean, r: AssistantReply) {
        val l = log ?: return
        val bubble = a.text(r.text, 15f, if (mine) Theme.p.onAccent else Theme.p.text, Theme.regular).apply {
            setPadding(dp(14), dp(10), dp(14), dp(11))
            background = if (mine) rounded(Theme.p.accent, 18f) else rounded(Theme.p.surface, 18f, Theme.p.outline, 1f)
            setLineSpacing(0f, 1.12f)
            setTextIsSelectable(!mine)
        }
        val row = a.hbox(if (mine) Gravity.END else Gravity.START).apply { gravity = if (mine) Gravity.END else Gravity.START }
        row.addView(bubble, LinearLayout.LayoutParams(WRAP, WRAP).apply { if (mine) leftMargin = dp(48) else rightMargin = dp(36) })
        l.addView(row, lp(MATCH, WRAP, t = dp(10)))
        if (mine) return

        val f = a.feed
        if (r.offers.isNotEmpty()) {
            val favs = a.repo.favorites()
            val strip = a.hbox(Gravity.TOP)
            for (o in r.offers) {
                val card = ProductCard(a)
                card.bind(o, f.store(o.storeId)?.name ?: "", f.catalog(o.catalogId), o.id in favs, a.today)
                card.setOnClickListener { a.openOffer(o.id) }
                card.onFav { a.repo.toggleFavorite(o.id); a.refreshCurrent() }
                strip.addView(card, lp(dp(156), WRAP, r = dp(10)))
            }
            l.addView(a.hscroll(strip), lp(MATCH, WRAP, t = dp(10)))
        }
        for (c in r.catalogs) {
            val st = Dates.status(c.validFrom, c.validTo, a.today).label
            val cr = a.hbox(Gravity.CENTER_VERTICAL).apply {
                setPadding(dp(14), dp(10), dp(12), dp(10)); background = ripple(rounded(Theme.p.surface2, 14f), 14f)
                addView(IconView(a, Icon.CATALOG, Theme.p.accent, 18))
                addView(a.text("${c.title} · $st", 14f, Theme.p.text, Theme.medium, 2), lp(0, WRAP, l = dp(10), weight = 1f))
                addView(IconView(a, Icon.CHEVRON, Theme.p.text3, 16))
                onTap { a.openCatalog(c.id) }
            }
            l.addView(cr, lp(MATCH, WRAP, t = dp(8), r = dp(36)))
        }
        val act = r.action
        if (act != null) {
            l.addView(a.ghostButton(r.actionLabel ?: "Aç", Icon.CHEVRON) {
                when {
                    act.startsWith("search:") -> a.openSearch(null, act.removePrefix("search:"))
                    act == "add_store" -> a.push(AddStoreScreen(a))
                    act == "settings" -> a.openSettings()
                    act == "favorites" -> a.switchTab(Tab.FAVORITES)
                    act == "list" -> a.switchTab(Tab.LIST)
                }
            }, lp(WRAP, WRAP, t = dp(8)))
        }
    }

    companion object {
        private val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        /** (benim mesajım mı, içerik) — uygulama açık kaldıkça korunur. */
        private val history = ArrayList<Pair<Boolean, AssistantReply>>()
        private var engineDay: java.time.LocalDate? = null
        private var engine: Pair<com.firsat360.app.core.Feed, Assistant>? = null
    }
}

