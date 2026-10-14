package com.firsat360.app.core

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

/**
 * Feed indirme (saf java.net; JVM'de yerel sunucuyla test edilir):
 * - Koşullu istek (If-None-Match / If-Modified-Since): değişmeyen veri yeniden indirilmez (304).
 * - gzip ile sıkıştırılmış aktarım.
 * - Boyut sınırı; geçici dosyaya yazılır, doğrulanır, sonra atomik olarak yerine konur (yarım dosya kalmaz).
 * - Ağ/sunucu hatasında sınırlı yeniden deneme (artan bekleme); 4xx'te yeniden denenmez.
 * - Hata olursa mevcut önbellek dosyasına dokunulmaz.
 */
class FeedFetch(
    private val url: String,
    private val target: File,
    private val userAgent: String,
    private val maxBytes: Long = 25L * 1024 * 1024,
    private val connectTimeoutMs: Int = 12_000,
    private val readTimeoutMs: Int = 30_000,
) {
    enum class Status { UPDATED, NOT_MODIFIED, FAILED }

    data class Result(val status: Status, val text: String? = null, val etag: String? = null,
                      val lastModified: String? = null, val error: String? = null, val attempts: Int = 1)

    fun fetch(etag: String?, lastModified: String?, attempts: Int = 3, validate: (String) -> Unit = {},
              sleep: (Long) -> Unit = { Thread.sleep(it) }): Result {
        var last: String? = null
        for (i in 1..attempts) {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = connectTimeoutMs; readTimeout = readTimeoutMs
                    instanceFollowRedirects = true
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Accept-Encoding", "gzip")
                    setRequestProperty("User-Agent", userAgent)
                    if (target.exists()) {
                        etag?.let { setRequestProperty("If-None-Match", it) }
                        lastModified?.let { setRequestProperty("If-Modified-Since", it) }
                    }
                }
                val code = conn.responseCode
                if (code == 304) return Result(Status.NOT_MODIFIED, etag = etag, lastModified = lastModified, attempts = i)
                if (code in 400..499) return Result(Status.FAILED, error = "Sunucu $code döndürdü", attempts = i)
                if (code != 200) throw IOException("Sunucu $code döndürdü")
                val raw: InputStream = conn.inputStream
                val input = if ("gzip".equals(conn.getHeaderField("Content-Encoding"), true)) GZIPInputStream(raw) else raw
                val tmp = File(target.path + ".part")
                try {
                    var total = 0L
                    input.use { inp ->
                        tmp.outputStream().use { out ->
                            val buf = ByteArray(32 * 1024)
                            while (true) {
                                val n = inp.read(buf)
                                if (n < 0) break
                                total += n
                                if (total > maxBytes) throw IOException("Veri boyutu sınırı aşıldı")
                                out.write(buf, 0, n)
                            }
                        }
                    }
                    val text = tmp.readText(Charsets.UTF_8)
                    validate(text)  // bozuk/eksik veri önbelleğe yazılmaz
                    if (!tmp.renameTo(target)) { target.delete(); if (!tmp.renameTo(target)) throw IOException("Önbelleğe yazılamadı") }
                    return Result(Status.UPDATED, text, conn.getHeaderField("ETag"), conn.getHeaderField("Last-Modified"),
                        attempts = i)
                } finally {
                    tmp.delete()
                }
            } catch (e: IOException) {
                last = e.message ?: e.javaClass.simpleName
            } catch (e: Exception) {
                // Doğrulama hatası (bozuk veri): yeniden denemek aynı sonucu verir
                return Result(Status.FAILED, error = "Veri doğrulanamadı: ${e.message}", attempts = i)
            } finally {
                conn?.disconnect()
            }
            if (i < attempts) sleep(2000L shl (i - 1))  // 2 sn, 4 sn, 8 sn …
        }
        return Result(Status.FAILED, error = last ?: "Bağlantı kurulamadı", attempts = attempts)
    }
}
