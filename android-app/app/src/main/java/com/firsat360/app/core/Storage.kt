package com.firsat360.app.core

import java.io.File
import java.time.LocalDate
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/**
 * Kısa, sınırlı uygulama günlüğü (saf Java/Kotlin; JVM'de test edilir).
 * - Üretim sürümünde yalnızca hata (E) ve uyarı (W); ayrıntı (D) yalnızca [verbose] açıkken.
 * - Satır başına en fazla 200 karakter; adreslerin sorgu kısmı ve anahtar benzeri değerler maskelenir.
 * - En fazla 7 gün, toplam en fazla 5 MB; günlük dosya başına 1 MB. Temizlik asla hata fırlatmaz.
 */
object AppLog {
    const val MAX_TOTAL = 5L * 1024 * 1024
    const val MAX_FILE = 1L * 1024 * 1024
    const val KEEP_DAYS = 7
    @Volatile var dir: File? = null
    @Volatile var verbose: Boolean = false
    private val TIME = DateTimeFormatter.ofPattern("HH:mm:ss")
    private val URL_QUERY = Regex("(https?://[^\\s?#]+)[?#]\\S*")
    private val SECRET = Regex("(?i)(token|key|password|parola|secret|authorization)([=:\\s]+)\\S+")

    fun init(d: File, verbose: Boolean) {
        dir = d.apply { mkdirs() }
        this.verbose = verbose
    }

    fun e(code: String, msg: String? = null) = write("E", code, msg)
    fun w(code: String, msg: String? = null) = write("W", code, msg)
    fun d(code: String, msg: String? = null) { if (verbose) write("D", code, msg) }

    fun sanitize(s: String?): String = (s ?: "")
        .replace(URL_QUERY, "$1?…")
        .replace(SECRET) { m -> m.groupValues[1] + m.groupValues[2] + "***" }
        .replace(Regex("\\s+"), " ").take(200)

    @Synchronized
    private fun write(level: String, code: String, msg: String?) {
        try {
            val d = dir ?: return
            val now = ZonedDateTime.now(Dates.TR)
            val f = File(d, "app-${now.toLocalDate()}.log")
            if (f.length() > MAX_FILE) return  // aynı gün çok tekrar eden hata dosyayı şişirmez
            f.appendText("${now.format(TIME)} $level $code ${sanitize(msg)}\n", Charsets.UTF_8)
        } catch (t: Throwable) { }
    }

    /** Eski ve sınırı aşan günlükleri siler; silinen dosya sayısını döner. */
    fun cleanup(d: File? = dir, nowMs: Long = System.currentTimeMillis(), maxTotal: Long = MAX_TOTAL,
                keepDays: Int = KEEP_DAYS): Int {
        var removed = 0
        try {
            val files = d?.listFiles()?.filter { it.isFile }?.toMutableList() ?: return 0
            val it = files.iterator()
            while (it.hasNext()) {
                val f = it.next()
                if (nowMs - f.lastModified() > keepDays * 86_400_000L) { if (f.delete()) removed++; it.remove() }
            }
            var total = files.sumOf { it.length() }
            for (f in files.sortedBy { it.lastModified() }) {
                if (total <= maxTotal) break
                val len = f.length()
                if (f.delete()) { removed++; total -= len }
            }
        } catch (t: Throwable) { }
        return removed
    }

    fun today(): LocalDate = LocalDate.now(Dates.TR)
}

/** Disk önbelleği bütçesi: LRU (son kullanım = lastModified) ile boyut sınırı ve yarım indirme temizliği. */
object DiskBudget {
    const val PART_MAX_AGE_MS = 10 * 60 * 1000L

    fun size(dir: File?): Long {
        if (dir == null || !dir.exists()) return 0
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    /**
     * [dir] toplamı [limit]'i aşarsa en eski dosyalardan başlayarak [target]'a kadar siler.
     * [protect] içindeki adlar (etkin indirme/görüntüleme) silinmez. Eski ".part" dosyaları her durumda silinir.
     * Silinen bayt sayısını döner; hata fırlatmaz.
     */
    fun trim(dir: File?, limit: Long, target: Long = limit * 8 / 10, protect: Set<String> = emptySet(),
             nowMs: Long = System.currentTimeMillis()): Long {
        var freed = 0L
        try {
            val files = dir?.listFiles()?.filter { it.isFile } ?: return 0
            val live = ArrayList<File>()
            for (f in files) {
                if (f.name.endsWith(".part")) {
                    if (f.name !in protect && nowMs - f.lastModified() > PART_MAX_AGE_MS) {
                        val l = f.length(); if (f.delete()) freed += l
                    }
                } else live.add(f)
            }
            var total = live.sumOf { it.length() }
            if (total <= limit) return freed
            for (f in live.sortedBy { it.lastModified() }) {
                if (total <= target) break
                if (f.name in protect) continue
                val l = f.length()
                if (f.delete()) { total -= l; freed += l }
            }
        } catch (t: Throwable) { }
        return freed
    }

    /** Cihaz kapasitesine göre varsayılan görsel önbelleği: boş alanın %5'i, 40–100 MB arası. */
    fun defaultImageLimitMb(freeBytes: Long): Int = (freeBytes / 20 / 1048576).toInt().coerceIn(40, 100)
}
