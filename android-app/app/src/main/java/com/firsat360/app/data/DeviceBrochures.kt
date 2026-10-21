package com.firsat360.app.data

import android.content.Context
import com.firsat360.app.core.Json

/**
 * Sunucuda toplanamayan (bot sınaması/robots.txt) marketlerin broşür sayfaları: kullanıcı resmî sayfayı uygulama içinde
 * açtığında cihazda toplanır ve YALNIZCA bu cihazda, en fazla [MAX_AGE_MS] saklanır. Görsel dosyası kopyalanmaz;
 * yalnızca resmî görsel adresleri tutulur. Bir sonraki açılışta broşür doğrudan uygulama içi okuyucuda açılır.
 */
object DeviceBrochures {
    private const val MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("device_brochures", Context.MODE_PRIVATE)

    data class Entry(val title: String, val sourceUrl: String, val pages: List<String>, val savedAt: Long)

    fun save(ctx: Context, storeId: String, title: String, sourceUrl: String, pages: List<String>) {
        try {
            val body = "{\"title\":${q(title)},\"source\":${q(sourceUrl)},\"at\":${System.currentTimeMillis()}," +
                "\"pages\":[${pages.take(80).joinToString(",") { q(it) }}]}"
            prefs(ctx).edit().putString(storeId, body).apply()
        } catch (e: Exception) { }
    }

    fun get(ctx: Context, storeId: String, now: Long = System.currentTimeMillis()): Entry? = try {
        val m = Json.parse(prefs(ctx).getString(storeId, null) ?: return null) as? Map<*, *> ?: return null
        val at = (m["at"] as? Number)?.toLong() ?: 0L
        if (now - at > MAX_AGE_MS) null
        else Entry(m["title"] as? String ?: "", m["source"] as? String ?: "",
            (m["pages"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.startsWith("https://") } ?: emptyList(), at)
            .takeIf { it.pages.size >= 2 }
    } catch (e: Exception) { null }

    fun clear(ctx: Context) = prefs(ctx).edit().clear().apply()

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}
