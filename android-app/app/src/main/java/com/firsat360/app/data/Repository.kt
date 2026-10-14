package com.firsat360.app.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import com.firsat360.app.BuildInfo
import com.firsat360.app.R
import com.firsat360.app.core.AppLog
import com.firsat360.app.core.Feed
import com.firsat360.app.core.FeedChoice
import com.firsat360.app.core.FeedFetch
import java.io.File

/**
 * Veri kaynağı: FIRSAT360'ın her gün otomatik güncellenen yayını (sabit adres, derlemede gömülü) → cihazdaki son
 * başarılı kopya → uygulama içinde gelen veri. Kullanıcı sunucu adresi girmez/görmez. Demo veri yoktur.
 */
class Repository(private val ctx: Context) {
    var feed: Feed? = null
        private set
    var lastError: String? = null
        private set

    private val prefs = ctx.getSharedPreferences("firsat360", Context.MODE_PRIVATE)
    private val main = Handler(Looper.getMainLooper())
    private val cacheFile get() = File(ctx.filesDir, "feed-cache.json")

    val feedUrl: String get() = Sync.feedUrl(ctx)
    val lastCheckAt: Long get() = prefs.getLong("last_check_at", 0L)
    val lastSuccessAt: Long get() = prefs.getLong("last_success_at", 0L)
    @Volatile var refreshing = false
        private set

    fun clearCache() { cacheFile.delete(); Sync.clearValidators(ctx) }

    fun favorites(): Set<String> = prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet()
    fun isFavorite(id: String) = id in favorites()
    fun toggleFavorite(id: String): Boolean {
        val s = favorites().toMutableSet()
        val now = if (!s.remove(id)) { s.add(id); true } else false
        prefs.edit().putStringSet("favorites", s).apply()
        return now
    }

    fun loadInitial(done: (Feed?, String?) -> Unit) {
        Thread {
            var err: String? = null
            var cached: Feed? = null
            if (cacheFile.exists()) {
                try { cached = Feed.parse(cacheFile.readText(Charsets.UTF_8)) } catch (e: Exception) {
                    AppLog.w("cache.bad", e.message); cacheFile.delete(); Sync.clearValidators(ctx)
                }
            }
            var bundled: Feed? = null
            try {
                val text = ctx.assets.open("feed.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
                bundled = Feed.parse(text)
            } catch (e: Exception) {
                err = "Veriler açılamadı"
                AppLog.e("bundled.bad", e.message)
            }
            // Uygulama güncellendiğinde gömülü veri, eski önbellekten YENİ olabilir → yenisi seçilir.
            val pick = FeedChoice.newer(cached, bundled)
            if (pick === bundled && cached != null) { cacheFile.delete(); Sync.clearValidators(ctx) }
            if (pick != null) err = null
            main.post { feed = pick ?: feed; lastError = err; done(pick, err) }
        }.start()
    }

    /**
     * Güncel veriyi ister (açılışta, elle yenilemede). Değişmemişse indirmez (304). Başarısız olursa mevcut veri
     * korunur. [done]: (yeni feed veya null, kullanıcıya gösterilecek kısa hata veya null).
     */
    fun refresh(force: Boolean = false, done: (Feed?, String?) -> Unit) {
        if (feedUrl.isBlank()) { main.post { done(null, null) }; return }
        if (refreshing) { main.post { done(null, null) }; return }
        if (!force && System.currentTimeMillis() - lastCheckAt < 30 * 60 * 1000L) { main.post { done(null, null) }; return }
        refreshing = true
        Thread {
            val r = Sync.fetch(ctx, attempts = if (force) 2 else 3)
            var parsed: Feed? = null
            if (r.status == FeedFetch.Status.UPDATED && r.text != null) {
                parsed = try { Feed.parse(r.text) } catch (e: Exception) { null }
                if (parsed != null) Notifier.evaluate(ctx, parsed)
            }
            val err = if (r.status == FeedFetch.Status.FAILED) {
                if (!Sync.online(ctx)) "İnternet bağlantısı yok. Son kaydedilen fırsatlar gösteriliyor."
                else "Şu anda güncellenemedi. Son kaydedilen fırsatlar gösteriliyor."
            } else null
            main.post {
                refreshing = false
                if (parsed != null) feed = parsed
                lastError = err
                done(parsed, err)
            }
        }.start()
    }
}

/** Feed indirme ve ağ durumu (ön plan ve arka plan ortak). */
object Sync {
    fun feedUrl(ctx: Context): String = try { ctx.getString(R.string.feed_url).trim() } catch (e: Exception) { "" }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("firsat360", Context.MODE_PRIVATE)

    fun clearValidators(ctx: Context) { prefs(ctx).edit().remove("feed_etag").remove("feed_last_modified").apply() }

    @Synchronized
    fun fetch(ctx: Context, attempts: Int = 3): FeedFetch.Result {
        val p = prefs(ctx)
        val url = feedUrl(ctx)
        if (url.isBlank()) return FeedFetch.Result(FeedFetch.Status.FAILED, error = "adres yok")
        val target = File(ctx.filesDir, "feed-cache.json")
        val r = FeedFetch(url, target, "FIRSAT360/${BuildInfo.VERSION} (Android)")
            .fetch(p.getString("feed_etag", null), p.getString("feed_last_modified", null), attempts,
                validate = { Feed.parse(it) })
        val now = System.currentTimeMillis()
        val e = p.edit().putLong("last_check_at", now)
        when (r.status) {
            FeedFetch.Status.UPDATED -> e.putLong("last_success_at", now).putString("feed_etag", r.etag)
                .putString("feed_last_modified", r.lastModified).putString("last_sync_result", "updated")
            FeedFetch.Status.NOT_MODIFIED -> e.putLong("last_success_at", now).putString("last_sync_result", "unchanged")
            FeedFetch.Status.FAILED -> e.putString("last_sync_result", "failed")
        }
        e.apply()
        if (r.status == FeedFetch.Status.FAILED) AppLog.w("sync.fail", r.error)
        return r
    }

    fun online(ctx: Context): Boolean = try {
        val cm = ctx.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (e: Exception) { true }

    /** Ücretli (mobil) bağlantı mı? Belirlenemezse güvenli taraf: hayır. */
    fun metered(ctx: Context): Boolean = try {
        ctx.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered ?: false
    } catch (e: Exception) { false }
}
