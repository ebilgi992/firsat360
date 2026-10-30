package com.firsat360.app.data

import android.content.Context
import com.firsat360.app.core.Json
import java.io.File

/**
 * Cihazda resmî sayfadan okunan broşürler (istemci köprüsü ve kullanıcının eklediği marketler).
 *
 * - Güncel kayıt [FRESH_MS] boyunca "güncel" sayılır (raftaki kapak, dokununca anında açılış). Süre dolunca kayıt
 *   silinmez; köprü yeniden okuyana kadar kapak olarak gösterilmeye devam eder ([get] fresh=false).
 * - İçerik değişince (yeni hafta) önceki kayıt geçmişe taşınır: mağaza ekranındaki "Geçmiş kataloglar" (en çok
 *   [HISTORY] kayıt, [HISTORY_DAYS] gün).
 * - Görsel kopyalanmaz; resmî adresleri tutulur. PDF broşürde ilk sayfa cihazda küçük kapak görseline çizilir.
 */
object DeviceBrochures {
    const val FRESH_MS = 12L * 60 * 60 * 1000
    private const val HISTORY = 8
    private const val HISTORY_DAYS = 90L
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("device_brochures", Context.MODE_PRIVATE)

    data class Pdf(val url: String, val title: String, val dates: String)

    /** Köprü betiklerinin sürümü: eski betikle kaydedilmiş SAYFA listeleri (ör. yanlış FlipHTML5 adresleri) yok sayılır. */
    const val FORMAT = 2

    data class Entry(val title: String, val sourceUrl: String, val pages: List<String>, val savedAt: Long,
                     val pdfs: List<Pdf> = emptyList(), val cover: String? = null, val v: Int = FORMAT) {
        val hasContent: Boolean get() = pages.isNotEmpty() || pdfs.isNotEmpty()
        /** Kapak: PDF'in cihazda çizilen ilk sayfası veya ilk sayfa görseli. */
        val coverUrl: String? get() = cover ?: pages.firstOrNull()
        /** İçerik anahtarı: aynı dosya adı yeniden kullanılsa da başlık/tarih/sayfa sayısı değişince "yeni broşür". */
        val key: String get() = pdfs.joinToString(";") { it.url + "|" + it.title + "|" + it.dates } + "#" +
            pages.size + "|" + pages.firstOrNull().orEmpty() + "|" + pages.lastOrNull().orEmpty()
    }

    /** Kapak dosyası içeriğe göre adlandırılır: geçmiş haftaların kartları kendi kapağını korur. */
    fun coverFile(ctx: Context, storeId: String, contentKey: String): File =
        File(File(ctx.filesDir, "covers").apply { mkdirs() },
            "${storeId.filter { it.isLetterOrDigit() || it == '-' }}-${Download.hash(contentKey).take(12)}.jpg")

    /** Hiçbir kaydın kullanmadığı eski kapak dosyalarını siler. */
    fun pruneCovers(ctx: Context) {
        try {
            val p = prefs(ctx)
            val used = HashSet<String>()
            for ((k, v) in p.all) if (v is String && !k.endsWith("#tried")) Regex("file://[^\"]+").findAll(v).forEach { used.add(it.value.removePrefix("file://")) }
            File(ctx.filesDir, "covers").listFiles()?.forEach { f -> if (f.path !in used && System.currentTimeMillis() - f.lastModified() > 3_600_000L) f.delete() }
        } catch (e: Exception) { }
    }

    /** Kaydeder. Önceki broşürden farklı içerik geldiyse (yeni hafta) true. */
    fun save(ctx: Context, storeId: String, title: String, sourceUrl: String, pages: List<String>,
             pdfs: List<Pdf> = emptyList(), cover: String? = null): Boolean {
        var changed = false
        try {
            val e = Entry(title, sourceUrl, pages.take(80), System.currentTimeMillis(), pdfs.take(6), cover)
            val prev = read(ctx, storeId)
            val ed = prefs(ctx).edit().putString(storeId, encode(e))
            if (prev != null && prev.hasContent && prev.key != e.key) {
                changed = true
                // Yeni hafta: önceki broşür geçmişe
                val hist = (listOf(prev) + history(ctx, storeId)).distinctBy { it.key }.take(HISTORY)
                ed.putString("$storeId#history", "[" + hist.joinToString(",") { encode(it) } + "]")
            }
            ed.apply()
        } catch (e: Exception) { }
        return changed
    }

    /** Güncel kayıt; [freshOnly] ise yalnızca [FRESH_MS] içinde okunmuşsa. */
    fun get(ctx: Context, storeId: String, freshOnly: Boolean = true, now: Long = System.currentTimeMillis()): Entry? {
        val e = read(ctx, storeId) ?: return null
        if (!e.hasContent || (e.v < FORMAT && e.pdfs.isEmpty())) return null
        return if (freshOnly && now - e.savedAt > FRESH_MS) null else e
    }

    fun history(ctx: Context, storeId: String, now: Long = System.currentTimeMillis()): List<Entry> = try {
        val l = Json.parse(prefs(ctx).getString("$storeId#history", null) ?: "[]") as? List<*> ?: emptyList<Any>()
        l.mapNotNull { (it as? Map<*, *>)?.let { m -> decode(m) } }
            .filter { it.hasContent && now - it.savedAt < HISTORY_DAYS * 86_400_000L }
    } catch (e: Exception) { emptyList() }

    fun lastAttempt(ctx: Context, storeId: String): Long = prefs(ctx).getLong("$storeId#tried", 0L)
    fun markAttempt(ctx: Context, storeId: String, ok: Boolean) {
        prefs(ctx).edit().putLong("$storeId#tried", System.currentTimeMillis()).putBoolean("$storeId#ok", ok).apply()
    }

    /** Sayfaları açılmayan kaydı siler (PDF kayıtlarına dokunulmaz). */
    fun dropPages(ctx: Context, storeId: String) {
        val e = read(ctx, storeId) ?: return
        if (e.pdfs.isEmpty()) prefs(ctx).edit().remove(storeId).apply()
    }

    fun remove(ctx: Context, storeId: String) {
        prefs(ctx).edit().remove(storeId).remove("$storeId#history").remove("$storeId#tried").remove("$storeId#ok").apply()
        pruneCovers(ctx)
    }

    fun clear(ctx: Context) {
        prefs(ctx).edit().clear().apply()
        try { File(ctx.filesDir, "covers").deleteRecursively() } catch (e: Exception) { }
    }

    private fun read(ctx: Context, storeId: String): Entry? {
        val raw = prefs(ctx).getString(storeId, null) ?: return null
        return try { (Json.parse(raw) as? Map<*, *>)?.let { decode(it) } } catch (e: Exception) { null }
    }

    private fun decode(m: Map<*, *>): Entry = Entry(
        m["title"] as? String ?: "", m["source"] as? String ?: "",
        (m["pages"] as? List<*>)?.mapNotNull { it as? String }?.filter { it.startsWith("https://") } ?: emptyList(),
        (m["at"] as? Number)?.toLong() ?: 0L,
        (m["pdfs"] as? List<*>)?.mapNotNull { x ->
            val pm = x as? Map<*, *> ?: return@mapNotNull null
            val u = (pm["url"] as? String)?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
            Pdf(u, pm["title"] as? String ?: "", pm["dates"] as? String ?: "")
        } ?: emptyList(),
        (m["cover"] as? String)?.takeIf { it.startsWith("https://") || it.startsWith("file://") },
        (m["v"] as? Number)?.toInt() ?: 1)

    private fun encode(e: Entry): String =
        "{\"title\":${q(e.title)},\"source\":${q(e.sourceUrl)},\"at\":${e.savedAt},\"v\":${e.v}," +
            "\"pages\":[${e.pages.joinToString(",") { q(it) }}]," +
            "\"pdfs\":[${e.pdfs.joinToString(",") { "{\"url\":${q(it.url)},\"title\":${q(it.title)},\"dates\":${q(it.dates)}}" }}]" +
            (if (e.cover != null) ",\"cover\":${q(e.cover)}" else "") + "}"

    private fun q(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ") + "\""
}

