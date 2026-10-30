package com.firsat360.app.screens

import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import com.firsat360.app.MainActivity
import com.firsat360.app.Screen
import com.firsat360.app.Tab
import com.firsat360.app.data.CustomStores
import com.firsat360.app.data.DeviceBrochures
import com.firsat360.app.ui.*

/**
 * Kullanıcının kendi marketini eklemesi: marketin broşür/aktüel sayfasının adresi verilir; uygulama sayfayı cihazda
 * açıp broşür görsellerini/PDF'lerini bulur, sonucu bildirir ve marketi vitrine ekler. Sonraki açılışlarda Wi‑Fi'de
 * arka planda yenilenir; yeni broşür çıkınca bildirim gelir. Hiçbir veri sunucuya gönderilmez.
 */
class AddStoreScreen(a: MainActivity) : Screen(a) {
    override val tab: Tab get() = Tab.STORES

    override fun create(): View {
        val outer = a.vbox()
        outer.setBackgroundColor(Theme.p.bg)
        outer.addView(a.topBar("Market ekle"))
        val col = a.vbox().apply { setPadding(dp(20), dp(4), dp(20), dp(36)) }
        col.addView(a.text("Listede olmayan bir marketin broşürlerini takip edin. Marketin broşür, aktüel veya katalog " +
            "sayfasının adresini yapıştırın; broşürü bulup vitrininize ekleyelim.", 14.5f, Theme.p.text2, Theme.regular))
        col.addView(a.text("İpucu: Tek bir broşürün değil, marketin \"Kataloglar/Aktüel\" LİSTE sayfasının adresini verirseniz her " +
            "hafta en yeni broşür otomatik gelir. FlipHTML5, Publitas, Issuu gibi görüntüleyiciler ve PDF'ler tanınır.",
            13f, Theme.p.text3, Theme.regular), lp(t = dp(8)))

        val urlBox = field("Broşür sayfasının adresi", "ornekmarket.com.tr/aktuel", InputType.TYPE_TEXT_VARIATION_URI)
        val nameBox = field("Market adı (isteğe bağlı)", "Örnek Market", InputType.TYPE_TEXT_FLAG_CAP_WORDS)
        col.addView(urlBox.first, lp(t = dp(20)))
        col.addView(nameBox.first, lp(t = dp(12)))
        val error = a.text("", 13f, Theme.p.danger, Theme.medium).apply { visibility = View.GONE }
        col.addView(error, lp(t = dp(8)))

        val go = a.button("Broşürleri bul ve ekle", Icon.SEARCH, true) {
            when (val r = CustomStores.check(urlBox.second.text.toString())) {
                is CustomStores.Check.Bad -> { error.text = r.reason; error.visibility = View.VISIBLE }
                is CustomStores.Check.Ok -> {
                    error.visibility = View.GONE
                    if (a.feed.stores.any { !CustomStores.isCustom(it.id) && siteFamily(android.net.Uri.parse(it.officialUrl).host) == siteFamily(r.host) }) {
                        val st = a.feed.stores.first { siteFamily(android.net.Uri.parse(it.officialUrl).host) == siteFamily(r.host) }
                        a.toast("${st.name} zaten listede"); a.openStore(st.id); return@button
                    }
                    val name = nameBox.second.text.toString().trim().ifBlank { r.suggestedName }
                    val c = CustomStores.add(a, name, r.url)
                    a.repo.refreshCustom()
                    Theme.registerStores(a.feed.stores); StoreLogos.register(a.feed.stores, a)
                    val entry = a.feed.clientBridge[c.id]
                    a.push(WebScreen(a, r.url, "$name broşür", catalog = true, storeId = c.id, profile = "generic",
                        allowedHosts = entry?.allowedHosts ?: emptyList()) { found ->
                        if (found > 0) a.toast("$name eklendi: $found ${if (found == 1) "broşür" else "sayfa"} bulundu")
                        else a.toast("$name eklendi. Broşür bulunamadı; dokunduğunuzda resmî sayfası açılır.")
                    })
                }
            }
        }
        col.addView(go, lp(t = dp(16)))
        col.addView(a.notice(Icon.SHIELD, "Sayfa yalnızca bu telefonda açılır; adres ve broşürler hiçbir sunucuya gönderilmez. " +
            "Giriş yapılması gereken veya doğrulama isteyen sayfalarda broşür otomatik bulunamayabilir.", Theme.p.text2, Theme.p.surface2),
            lp(t = dp(16)))

        val mine = CustomStores.list(a)
        if (mine.isNotEmpty()) {
            col.addView(a.text("Eklediğiniz marketler", 18f, Theme.p.text, Theme.semibold).apply { accessibilityHeading() }, lp(t = dp(28)))
            for (c in mine) {
                val e = DeviceBrochures.get(a, c.id, freshOnly = false)
                val row = a.hbox().apply { setPadding(dp(14), dp(10), dp(6), dp(10)); background = surfaceBg(18f) }
                row.addView(FrameLayout(a).apply {
                    background = rounded(Theme.store(c.id), 12f)
                    addView(a.text(c.name.take(3), 13f, Theme.onStore(c.id), Theme.display, 1), FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(40), dp(40)))
                val t = a.vbox()
                t.addView(a.text(c.name, 15f, Theme.p.text, Theme.semibold, 1))
                t.addView(a.text(when {
                    e == null -> android.net.Uri.parse(c.url).host ?: c.url
                    e.pdfs.isNotEmpty() -> "${e.pdfs.size} PDF broşür, ${com.firsat360.app.core.Dates.relative(java.time.Instant.ofEpochMilli(e.savedAt).toString(), a.now)}"
                    else -> "${e.pages.size} sayfa, ${com.firsat360.app.core.Dates.relative(java.time.Instant.ofEpochMilli(e.savedAt).toString(), a.now)}"
                }, 12.5f, Theme.p.text3, Theme.regular, 1), lp(t = dp(2)))
                row.addView(t, lp(0, WRAP, l = dp(12), weight = 1f))
                row.addView(a.iconButton(Icon.CLOSE, Theme.p.text2, desc = "${c.name} marketini kaldır") {
                    try {
                        android.app.AlertDialog.Builder(a).setTitle("${c.name} kaldırılsın mı?")
                            .setMessage("Bu cihazdaki broşür kayıtları da silinir.")
                            .setPositiveButton("Kaldır") { _, _ -> CustomStores.remove(a, c.id); a.repo.refreshCustom(); a.toast("${c.name} kaldırıldı"); a.refreshCurrent() }
                            .setNegativeButton("Vazgeç", null).show()
                    } catch (ex: Exception) { }
                })
                row.onTap { a.openStore(c.id) }
                col.addView(row, lp(t = dp(10)))
            }
        }
        outer.addView(ScrollView(a).apply { isVerticalScrollBarEnabled = false; addView(col) }, lp(MATCH, 0, weight = 1f))
        return outer
    }

    private fun field(label: String, hint: String, type: Int): Pair<View, EditText> {
        val box = a.vbox()
        box.addView(a.text(label, 13f, Theme.p.text2, Theme.medium))
        val et = EditText(a).apply {
            this.hint = hint
            setHintTextColor(Theme.p.text3)
            setTextColor(Theme.p.text)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            typeface = Theme.regular
            inputType = InputType.TYPE_CLASS_TEXT or type
            imeOptions = EditorInfo.IME_ACTION_DONE
            isSingleLine = true
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = surfaceBg(16f)
            contentDescription = label
        }
        box.addView(et, lp(t = dp(6)))
        return box to et
    }
}

/** Geçmiş (süresi bitmiş) katalog kartı: tarih aralığıyla; dokununca sayfalar/PDF yerel okuyucuda. */
fun MainActivity.archiveCard(c: com.firsat360.app.core.Catalog, storeName: String): View {
    val card = catalogCard(c.copy(firstSeenAt = null), storeName, today) { openArchived(c) }
    // Son satır (geçerlilik) yerine tarih aralığı
    val body = (card.getChildAt(card.childCount - 1) as? LinearLayout)
    (body?.getChildAt(body.childCount - 1) as? android.widget.TextView)?.let { tv ->
        tv.text = com.firsat360.app.core.Dates.rangeLabel(c.validFrom, c.validTo) ?: "Geçmiş katalog"
        tv.setTextColor(Theme.p.text3)
    }
    card.contentDescription = "$storeName, ${c.title}, ${com.firsat360.app.core.Dates.rangeLabel(c.validFrom, c.validTo) ?: ""}. Geçmiş katalog"
    return card
}

fun MainActivity.openArchived(c: com.firsat360.app.core.Catalog) {
    when {
        c.hasPages && com.firsat360.app.data.ImageLoader.enabled ->
            push(CatalogReaderScreen(this, null, 0, null, c.title, extraPages = c.pages))
        c.hasPdf -> push(PdfScreen(this, "", c))
        else -> openWeb(c.officialUrl, c.title)
    }
}

/** Bu cihazda daha önce okunmuş broşür (önceki hafta). */
fun MainActivity.deviceHistoryCard(store: com.firsat360.app.core.Store?, e: DeviceBrochures.Entry, d: java.time.LocalDate): View {
    val sid = store?.id ?: ""
    val name = store?.name ?: ""
    val c = com.firsat360.app.core.Catalog("device-$sid-${e.savedAt}", sid,
        e.pdfs.firstOrNull()?.title?.ifBlank { null } ?: "$name broşür", if (e.pdfs.isNotEmpty()) "pdf" else "aktuel",
        null, null, e.sourceUrl, "client_bridge", null, 0, e.coverUrl)
    val card = catalogCard(c, name, today) {
        if (e.pdfs.isNotEmpty()) {
            val p = e.pdfs.first()
            push(PdfScreen(this, "", com.firsat360.app.data.BridgeCatalogs.pdf(sid, p, e.sourceUrl, "$name broşür", d)))
        } else if (com.firsat360.app.data.ImageLoader.enabled) {
            val ps = e.pages.mapIndexed { i, u -> com.firsat360.app.core.CatalogPage(u, u, "Sayfa ${i + 1}", e.sourceUrl, true) }
            push(CatalogReaderScreen(this, null, 0, null, "$name broşür", extraPages = ps))
        } else openWeb(e.sourceUrl, name)
    }
    val body = (card.getChildAt(card.childCount - 1) as? LinearLayout)
    (body?.getChildAt(body.childCount - 1) as? android.widget.TextView)?.let { tv ->
        tv.text = "${com.firsat360.app.core.Dates.short(d)} tarihinde okundu"; tv.setTextColor(Theme.p.text3)
    }
    return card
}
