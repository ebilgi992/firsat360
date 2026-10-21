package com.firsat360.app.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import com.firsat360.app.BuildInfo
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.concurrent.Executors

/**
 * Bağımlılıksız görsel yükleyici: bellek (LRU) + disk önbelleği, boyuta göre küçültme, görünüm yeniden kullanıldığında
 * eski isteğin sonucunu yok sayma, tam boy yüklenemezse yedek (küçük) görsele düşme.
 *
 * Görseller yalnızca mağazaların resmî CDN adreslerinden, yalnızca HTTPS ile indirilir; cihazın önbelleğinde tutulur,
 * başka bir sunucuya yüklenmez. Hiçbir hata uygulamayı çökertmez (bellek yetersizliği dahil).
 */
object ImageLoader {
    private const val MAX_DOWNLOAD = 20L * 1024 * 1024
    private val main = Handler(Looper.getMainLooper())
    private var lowRam = false
    // Eş zamanlı indirme sınırı: düşük bellekli cihazda 2, diğerlerinde 3
    private val pool by lazy { Executors.newFixedThreadPool(if (lowRam) 2 else 3) }
    private val mem: LruCache<String, Bitmap> by lazy {
        object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / (if (lowRam) 10 else 6)).toInt()
            .coerceAtLeast(6 * 1024)) {
            override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
        }
    }
    private var dir: File? = null
    private val active = java.util.Collections.synchronizedSet(HashSet<String>())
    @Volatile var enabled: Boolean = true
    /** Disk önbelleği sınırı (bayt). Varsayılan cihaza göre 40–100 MB; kullanıcı ayarlardan değiştirebilir. */
    @Volatile var limitBytes: Long = 100L * 1024 * 1024
    /** Ağdan indirmeye izin var mı (ör. "yalnızca Wi-Fi" seçiliyken mobil veride false). Önbellek her zaman kullanılır. */
    @Volatile var allowNetwork: () -> Boolean = { true }

    fun init(ctx: Context) {
        if (dir != null) return
        lowRam = try { ctx.getSystemService(android.app.ActivityManager::class.java)?.isLowRamDevice ?: false } catch (e: Exception) { false }
        dir = File(ctx.cacheDir, "img").apply { mkdirs() }
        // Önceki oturumdan kalan yarım indirmeler
        Thread { com.firsat360.app.core.DiskBudget.trim(dir, limitBytes, protect = active.toSet()) }.start()
    }

    fun trim() { com.firsat360.app.core.DiskBudget.trim(dir, limitBytes, protect = active.toSet()) }

    private fun key(url: String, maxPx: Int) = "$maxPx|$url"

    /**
     * [view] için [url]'yi yükler. Başarısız olursa [fallback] denenir. [done] ana iş parçacığında çağrılır.
     * [rgb565]: alfa gerekmeyen büyük katalog sayfaları için yarım bellek.
     */
    fun load(view: ImageView, url: String?, maxPx: Int, fallback: String? = null, rgb565: Boolean = false,
              local: File? = null, done: ((Boolean) -> Unit)? = null) {
        view.tag = url
        if (url == null || !enabled) { done?.invoke(false); return }
        if (local != null && local.exists()) {  // çevrim dışı kaydedilmiş sayfa
            pool.execute {
                val bmp = try { decode(local, maxPx, rgb565) } catch (e: OutOfMemoryError) { mem.evictAll(); null }
                main.post { if (view.tag == url) { if (bmp != null) view.setImageBitmap(bmp); done?.invoke(bmp != null) } }
            }
            return
        }
        val cached = mem.get(key(url, maxPx))
        if (cached != null && !cached.isRecycled) {
            view.setImageBitmap(cached); done?.invoke(true); return
        }
        pool.execute {
            val bmp = fetch(url, maxPx, rgb565)
            main.post {
                if (view.tag != url) return@post  // görünüm başka ürün için yeniden kullanıldı
                if (bmp != null) {
                    view.setImageBitmap(bmp); done?.invoke(true)
                } else if (fallback != null && fallback != url) {
                    load(view, fallback, maxPx, null, rgb565, null, done)
                } else done?.invoke(false)
            }
        }
    }

    /** Arka planda önden indirir (katalogda sonraki sayfa). */
    fun prefetch(url: String?, maxPx: Int, rgb565: Boolean = false) {
        if (url == null || !enabled || mem.get(key(url, maxPx)) != null) return
        pool.execute { fetch(url, maxPx, rgb565) }
    }

    private fun fetch(url: String, maxPx: Int, rgb565: Boolean): Bitmap? {
        return try {
            val f = diskFile(url)
            if (f == null || !f.exists() || f.length() == 0L) {
                if (!url.startsWith("https://") || !allowNetwork()) return null
                if (!download(url, f)) return null
            } else f.setLastModified(System.currentTimeMillis())
            val bmp = decode(f ?: return null, maxPx, rgb565) ?: run { f.delete(); return null }
            mem.put(key(url, maxPx), bmp)
            bmp
        } catch (e: OutOfMemoryError) {
            mem.evictAll(); null
        } catch (e: Exception) {
            null
        }
    }

    private fun diskFile(url: String): File? {
        val d = dir ?: return null
        val md = MessageDigest.getInstance("SHA-1").digest(url.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder()
        for (b in md) sb.append(String.format("%02x", b))
        return File(d, sb.toString())
    }

    private fun download(url: String, target: File?): Boolean {
        target ?: return false
        var conn: HttpURLConnection? = null
        val tmp = File(target.path + ".part")
        active.add(tmp.name)
        try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10000; readTimeout = 20000; instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android ${android.os.Build.VERSION.RELEASE}) " +
                    "FIRSAT360/${BuildInfo.VERSION}")
                setRequestProperty("Accept", "image/webp,image/jpeg,image/png,image/*;q=0.8")
            }
            if (conn.responseCode != 200) return false
            var total = 0L
            conn.inputStream.use { input ->
                tmp.outputStream().use { out ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_DOWNLOAD) return false
                        out.write(buf, 0, n)
                    }
                }
            }
            if (!tmp.renameTo(target)) return false
            trimDisk()
            return true
        } catch (e: Exception) {
            return false
        } finally {
            conn?.disconnect()
            tmp.delete()
            active.remove(tmp.name)
        }
    }

    private fun decode(f: File, maxPx: Int, rgb565: Boolean): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx || bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = if (rgb565) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(f.path, opts)
    }

    @Synchronized
    private fun trimDisk() { com.firsat360.app.core.DiskBudget.trim(dir, limitBytes, protect = active.toSet()) }

    fun clear() {
        mem.evictAll()
        dir?.listFiles()?.forEach { if (it.name !in active) it.delete() }  // etkin indirmeye dokunma
    }

    fun diskUsageMb(): Double = (dir?.listFiles()?.sumOf { it.length() } ?: 0L) / 1048576.0
}
