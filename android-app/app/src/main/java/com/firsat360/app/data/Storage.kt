package com.firsat360.app.data

import android.content.Context
import com.firsat360.app.BuildInfo
import com.firsat360.app.core.AppLog
import com.firsat360.app.core.Catalog
import com.firsat360.app.core.DiskBudget
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Depolama düzeni:
 *  cacheDir/img      görsel önbelleği (LRU, varsayılan ≤100 MB; sistem de gerektiğinde silebilir)
 *  cacheDir/pdf      açılan katalog PDF'leri (en fazla 2 dosya / 120 MB; geçici)
 *  filesDir/offline  kullanıcının AÇIKÇA "çevrim dışı kaydet" dediği kataloglar (otomatik silinmez; ayarlardan silinir)
 *  filesDir/logs     kısa günlük (≤7 gün, ≤5 MB)
 */
object Maintenance {
    @Volatile private var lastRun = 0L

    /** Açılışta ve arka plan işinde çağrılır; en fazla 6 saatte bir çalışır. Hata fırlatmaz, etkin dosyalara dokunmaz. */
    fun run(ctx: Context, force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastRun < 6 * 3600_000L) return
        lastRun = now
        try {
            AppLog.cleanup(File(ctx.filesDir, "logs"))
            ImageLoader.init(ctx)
            ImageLoader.trim()
            PdfCache.trim(ctx)
            // Eski sürümlerden kalan yarım feed dosyası
            File(ctx.filesDir, "feed-cache.json.part").takeIf { it.exists() && now - it.lastModified() > 600_000 }?.delete()
        } catch (t: Throwable) { }
    }
}

/** İstek üzerine indirilen dosyalar için ortak, sınırlı ve yarım kalmayan indirme. */
internal object Download {
    val active: MutableSet<String> = ConcurrentHashMap.newKeySet()

    fun hash(s: String): String {
        val md = MessageDigest.getInstance("SHA-1").digest(s.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(); for (b in md) sb.append(String.format("%02x", b)); return sb.toString()
    }

    /**
     * Adres GERÇEKTEN bir görsel mi (JPEG/PNG/WebP/GIF imzası)? Köprünün bulduğu sayfaları kaydetmeden önce
     * denetlenir: görüntüleyici adres biçimini değiştirdiyse ya da görsel yalnızca kendi sayfasında gösterilmeye
     * izinliyse (403/HTML) broşür uygulama okuyucusu yerine marketin kendi görüntüleyicisinde açılır.
     */
    fun isImage(url: String): Boolean {
        if (!url.startsWith("https://")) return false
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000; readTimeout = 12_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FIRSAT360/${BuildInfo.VERSION} (Android)")
                setRequestProperty("Range", "bytes=0-31")
            }
            if (conn.responseCode !in 200..206) return false
            val b = ByteArray(16)
            var n = 0
            conn.inputStream.use { while (n < 12) { val r = it.read(b, n, 16 - n); if (r < 0) break; n += r } }
            fun u(i: Int) = b[i].toInt() and 0xFF
            n >= 4 && ((u(0) == 0xFF && u(1) == 0xD8) || (u(0) == 0x89 && u(1) == 0x50 && u(2) == 0x4E && u(3) == 0x47) ||
                (u(0) == 0x47 && u(1) == 0x49 && u(2) == 0x46) ||
                (n >= 12 && u(0) == 0x52 && u(1) == 0x49 && u(8) == 0x57 && u(9) == 0x45 && u(10) == 0x42 && u(11) == 0x50))
        } catch (e: Exception) { false } finally { conn?.disconnect() }
    }

    /** [url]'yi [target]'a indirir. İptal için [cancelled]; ilerleme (0..1, bilinmiyorsa -1). */
    fun to(url: String, target: File, maxBytes: Long, progress: ((Float) -> Unit)? = null,
           cancelled: () -> Boolean = { false }): Boolean {
        if (!url.startsWith("https://")) return false
        val tmp = File(target.path + ".part")
        active.add(tmp.name)
        var conn: HttpURLConnection? = null
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 30_000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "FIRSAT360/${BuildInfo.VERSION} (Android)")
            }
            if (conn.responseCode != 200) return false
            val len = conn.contentLengthLong
            if (len > maxBytes) return false
            var total = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(32 * 1024)
                    while (true) {
                        if (cancelled()) return false
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > maxBytes) return false
                        out.write(buf, 0, n)
                        if (len > 0) progress?.invoke(total.toFloat() / len)
                    }
                }
            }
            if (total == 0L) return false
            target.delete()
            return tmp.renameTo(target)
        } catch (e: IOException) {
            AppLog.w("download", e.message)
            return false
        } finally {
            conn?.disconnect()
            tmp.delete()  // başarısız / iptal edilen indirme iz bırakmaz
            active.remove(tmp.name)
        }
    }
}

/** Katalog PDF'leri: yalnızca kullanıcı açtığında indirilir; en fazla son 2 PDF tutulur. */
object PdfCache {
    private const val MAX_PDF = 80L * 1024 * 1024
    private const val KEEP = 2

    fun dir(ctx: Context) = File(ctx.cacheDir, "pdf").apply { mkdirs() }
    fun file(ctx: Context, url: String) = File(dir(ctx), Download.hash(url) + ".pdf")

    fun key(c: Catalog, url: String): String = url

    /** Önce çevrim dışı kopyaya, sonra önbelleğe bakar; yoksa indirir. */
    fun get(ctx: Context, c: Catalog, progress: (Float) -> Unit, cancelled: () -> Boolean): File? {
        val url = c.pdfUrl ?: return null
        OfflineStore.pdf(ctx, c.id)?.let { return it }
        if (c.sourceMethod == "client_bridge") {
            val f = bridgeFile(ctx, c, url)
            if (bridgeFresh(f)) return f
            if (!Download.to(url, f, MAX_PDF, progress, cancelled)) return f.takeIf { it.exists() && it.length() > 0 }
            markDownloaded(f); trimBridge(ctx, protect = setOf(f.name))
            return f
        }
        val f = file(ctx, key(c, url))
        if (f.exists() && f.length() > 0) { f.setLastModified(System.currentTimeMillis()); return f }
        if (!Download.to(url, f, MAX_PDF, progress, cancelled)) return null
        trim(ctx, protect = setOf(f.name))
        return f
    }

    // ---- Cihazda okunan resmî PDF'ler (istemci köprüsü): ayrı klasör (son 2 PDF kuralı bunları silmez), 16 dosya.
    // Market aynı dosya adını her hafta yeniden kullanabildiğinden dosya 36 saatten eskiyse yeniden indirilir.
    private const val BRIDGE_KEEP = 16
    private const val BRIDGE_FRESH_MS = 36L * 60 * 60 * 1000
    fun bridgeDir(ctx: Context) = File(ctx.cacheDir, "pdf-bridge").apply { mkdirs() }
    fun bridgeFile(ctx: Context, c: Catalog, url: String) =
        File(bridgeDir(ctx), Download.hash("$url|${c.validFrom ?: ""}|${c.validTo ?: ""}|${c.title}") + ".pdf")
    fun bridgeFresh(f: File): Boolean {
        if (!f.exists() || f.length() == 0L) return false
        val at = try { File(f.path + ".at").readText().trim().toLong() } catch (e: Exception) { 0L }
        return System.currentTimeMillis() - at < BRIDGE_FRESH_MS
    }
    fun markDownloaded(f: File) { try { File(f.path + ".at").writeText(System.currentTimeMillis().toString()) } catch (e: Exception) { } }
    fun trimBridge(ctx: Context, protect: Set<String> = emptySet()) {
        val files = bridgeDir(ctx).listFiles()?.filter { it.name.endsWith(".pdf") }?.sortedByDescending { it.lastModified() } ?: return
        for (f in files.drop(BRIDGE_KEEP)) if (f.name !in protect && f.name + ".part" !in Download.active) { f.delete(); File(f.path + ".at").delete() }
    }

    fun trim(ctx: Context, protect: Set<String> = emptySet()) {
        val files = dir(ctx).listFiles()?.filter { it.name.endsWith(".pdf") }?.sortedByDescending { it.lastModified() } ?: return
        for (f in files.drop(KEEP)) if (f.name !in protect && f.name + ".part" !in Download.active) f.delete()
        DiskBudget.trim(dir(ctx), 120L * 1024 * 1024, protect = protect + Download.active)
    }
}

/** Kullanıcının açıkça kaydettiği kataloglar (çevrim dışı). Önbellekten ayrı tutulur; otomatik silinmez. */
object OfflineStore {
    private const val MAX_FILE = 25L * 1024 * 1024
    fun root(ctx: Context) = File(ctx.filesDir, "offline").apply { mkdirs() }
    fun dir(ctx: Context, catalogId: String) = File(root(ctx), Download.hash(catalogId).take(20))

    fun isSaved(ctx: Context, catalogId: String): Boolean = File(dir(ctx, catalogId), "ok").exists()
    fun pageFile(ctx: Context, catalogId: String, url: String): File? =
        File(dir(ctx, catalogId), Download.hash(url)).takeIf { it.exists() && isSaved(ctx, catalogId) }
    fun pdf(ctx: Context, catalogId: String): File? = File(dir(ctx, catalogId), "katalog.pdf").takeIf { it.exists() && isSaved(ctx, catalogId) }

    /** Kataloğun sayfalarını (veya PDF'ini) indirir. Yarıda kalırsa klasör silinir. */
    fun save(ctx: Context, c: Catalog, progress: (Int, Int) -> Unit, cancelled: () -> Boolean): Boolean {
        val d = dir(ctx, c.id)
        d.deleteRecursively(); d.mkdirs()
        val urls = c.pages.map { it.url }
        val total = urls.size + if (c.pdfUrl != null) 1 else 0
        var done = 0
        try {
            c.pdfUrl?.let { u ->
                if (!Download.to(u, File(d, "katalog.pdf"), 80L * 1024 * 1024, null, cancelled)) { d.deleteRecursively(); return false }
                progress(++done, total)
            }
            for (u in urls) {
                if (cancelled()) { d.deleteRecursively(); return false }
                if (!Download.to(u, File(d, Download.hash(u)), MAX_FILE, null, cancelled)) { d.deleteRecursively(); return false }
                progress(++done, total)
            }
            File(d, "title").writeText(c.title + "\n" + c.storeId, Charsets.UTF_8)
            File(d, "ok").writeText(c.id, Charsets.UTF_8)
            return true
        } catch (e: Exception) {
            d.deleteRecursively(); return false
        }
    }

    fun delete(ctx: Context, catalogId: String) { dir(ctx, catalogId).deleteRecursively() }

    data class Saved(val catalogId: String, val title: String, val storeId: String, val bytes: Long)

    fun list(ctx: Context): List<Saved> = root(ctx).listFiles()?.mapNotNull { d ->
        val ok = File(d, "ok"); if (!ok.exists()) { d.deleteRecursively(); return@mapNotNull null }  // yarım kayıt
        val meta = File(d, "title").takeIf { it.exists() }?.readLines(Charsets.UTF_8) ?: emptyList()
        Saved(ok.readText(Charsets.UTF_8).trim(), meta.getOrElse(0) { "Katalog" }, meta.getOrElse(1) { "" }, DiskBudget.size(d))
    } ?: emptyList()

    fun bytes(ctx: Context): Long = DiskBudget.size(root(ctx))
}
