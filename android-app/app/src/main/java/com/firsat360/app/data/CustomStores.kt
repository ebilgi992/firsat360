package com.firsat360.app.data

import android.content.Context
import android.net.Uri
import com.firsat360.app.core.BridgeEntry
import com.firsat360.app.core.Feed
import com.firsat360.app.core.Json
import com.firsat360.app.core.Store
import com.firsat360.app.screens.siteFamily

/**
 * Kullanıcının kendi eklediği marketler: kullanıcı marketin broşür/aktüel sayfasının adresini verir; uygulama sayfayı
 * cihazda (istemci köprüsü, genel profil) açıp broşür görsellerini/PDF'lerini bulur, sonucu kullanıcıya bildirir ve
 * marketi rafa ekler. Hiçbir şey sunucuya gönderilmez; kayıt yalnızca bu cihazdadır.
 */
object CustomStores {
    data class Custom(val id: String, val name: String, val url: String, val addedAt: Long)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("custom_stores", Context.MODE_PRIVATE)
    private val PALETTE = intArrayOf(0xFF2E7D6B.toInt(), 0xFF6A4FB3.toInt(), 0xFFB5523B.toInt(), 0xFF2F6DB5.toInt(),
        0xFF9A7B12.toInt(), 0xFFB03A6E.toInt(), 0xFF3F7F2E.toInt(), 0xFF5B6472.toInt())

    sealed class Check {
        data class Ok(val url: String, val host: String, val suggestedName: String) : Check()
        data class Bad(val reason: String) : Check()
    }

    /** Adres denetimi: yalnızca herkese açık https sayfaları (IP, yerel ağ, dosya/özel şemalar reddedilir). */
    fun check(input: String): Check {
        var t = input.trim()
        if (t.isEmpty()) return Check.Bad("Marketin broşür sayfasının adresini yazın.")
        if (!t.contains("://")) t = "https://$t"
        if (t.startsWith("http://", ignoreCase = true)) t = "https://" + t.substring(7)
        if (t.length > 400) return Check.Bad("Adres çok uzun.")
        val u = try { Uri.parse(t) } catch (e: Exception) { null } ?: return Check.Bad("Adres okunamadı.")
        if (u.scheme?.lowercase() != "https") return Check.Bad("Yalnızca https:// ile başlayan web sayfaları eklenebilir.")
        val host = u.host?.lowercase()?.trimEnd('.') ?: return Check.Bad("Adreste site adı yok.")
        val labels = host.split('.')
        val numericLabel = Regex("(0x[0-9a-f]+|[0-9]+)")
        if (!host.contains('.') || host.endsWith(".local") || host.endsWith(".internal") || host.endsWith(".localhost") ||
            host.endsWith(".lan") || host.endsWith(".home") || host == "localhost" || host.contains(':') ||
            !host.matches(Regex("[a-z0-9.-]+")) || labels.any { it.isEmpty() } ||
            labels.last().matches(numericLabel) || labels.all { it.matches(numericLabel) } || labels.last().length < 2)
            return Check.Bad("Bu adres herkese açık bir market sitesi gibi görünmüyor.")
        if (!u.userInfo.isNullOrEmpty()) return Check.Bad("Adreste kullanıcı adı/parola olamaz.")
        val fam = siteFamily(host) ?: host
        val base = fam.substringBefore('.')
        val name = base.replaceFirstChar { if (it == 'i') 'İ' else it.uppercaseChar() }
        return Check.Ok(u.toString(), host, name)
    }

    fun list(ctx: Context): List<Custom> = try {
        (Json.parse(prefs(ctx).getString("list", "[]") ?: "[]") as? List<*>)?.mapNotNull { x ->
            val m = x as? Map<*, *> ?: return@mapNotNull null
            Custom(m["id"] as? String ?: return@mapNotNull null, m["name"] as? String ?: "Market",
                (m["url"] as? String)?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null,
                (m["at"] as? Number)?.toLong() ?: 0L)
        } ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun add(ctx: Context, name: String, url: String): Custom {
        val id = "u-" + Download.hash(url).take(10)
        val c = Custom(id, name.trim().take(40).ifBlank { "Market" }, url, System.currentTimeMillis())
        save(ctx, list(ctx).filter { it.id != id } + c)
        return c
    }

    fun rename(ctx: Context, id: String, name: String) =
        save(ctx, list(ctx).map { if (it.id == id) it.copy(name = name.trim().take(40).ifBlank { it.name }) else it })

    fun remove(ctx: Context, id: String) {
        save(ctx, list(ctx).filter { it.id != id })
        DeviceBrochures.remove(ctx, id)
    }

    fun isCustom(id: String) = id.startsWith("u-")

    private val VIEWERS = listOf("fliphtml5.com", "publitas.com", "issuu.com", "heyzine.com", "flipsnack.com", "yumpu.com")

    private fun save(ctx: Context, l: List<Custom>) {
        val body = "[" + l.joinToString(",") {
            "{\"id\":${q(it.id)},\"name\":${q(it.name)},\"url\":${q(it.url)},\"at\":${it.addedAt}}"
        } + "]"
        prefs(ctx).edit().putString("list", body).apply()
    }

    /** Feed + kullanıcının marketleri: market listesine ve istemci köprüsüne eklenir (genel profil, kendi alan adı). */
    fun merge(ctx: Context, feed: Feed): Feed {
        val l = list(ctx)
        if (l.isEmpty()) return feed
        val stores = l.mapIndexed { i, c ->
            Store(c.id, c.name, c.url, c.url, "custom", "Sizin eklediğiniz market", null, null, null,
                "Bu market sizin tarafınızdan eklendi; broşürler cihazınızda, resmî sayfasından okunur.",
                color = PALETTE[(c.id.hashCode() and 0x7fffffff) % PALETTE.size],
                short = c.name.take(6), kind = "market", sort = 90 + i, chainRank = 90 + i, brochureUrl = c.url)
        }
        val bridge = l.associate { c ->
            // Kendi alan adı + yaygın resmî broşür görüntüleyicileri (yalnızca marketin kendi sayfasından geçilir)
            c.id to BridgeEntry(c.url, "generic", listOfNotNull(siteFamily(Uri.parse(c.url).host)) + VIEWERS)
        }
        return feed.copy(stores = feed.stores.filter { s -> l.none { it.id == s.id } } + stores,
            clientBridge = feed.clientBridge + bridge)
    }

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}
