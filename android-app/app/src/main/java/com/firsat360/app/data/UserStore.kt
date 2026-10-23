package com.firsat360.app.data

import android.content.Context
import com.firsat360.app.core.ListItem
import com.firsat360.app.core.UserCodec
import java.time.OffsetDateTime

/**
 * Cihazda saklanan kullanıcı tercihleri ve listeleri (SharedPreferences). Sunucuya gönderilmez.
 * Uygulama güncellemesinde korunur (aynı imza anahtarı + aynı anahtar adları).
 */
class UserStore(ctx: Context) {
    private val prefs = ctx.applicationContext.getSharedPreferences("firsat360_user", Context.MODE_PRIVATE)

    private fun set(key: String): Set<String> = prefs.getStringSet(key, emptySet())?.toSet() ?: emptySet()
    private fun toggle(key: String, id: String): Boolean {
        val s = set(key).toMutableSet()
        val now = if (!s.remove(id)) { s.add(id); true } else false
        prefs.edit().putStringSet(key, s).apply()
        return now
    }

    fun favoriteStores(): Set<String> = set("fav_stores")
    fun toggleFavoriteStore(id: String) = toggle("fav_stores", id)
    fun favoriteCatalogs(): Set<String> = set("fav_catalogs")
    fun toggleFavoriteCatalog(id: String) = toggle("fav_catalogs", id)

    // ---- alışveriş listesi
    fun list(): List<ListItem> = UserCodec.listFromJson(prefs.getString("shopping_list", null))
    fun saveList(items: List<ListItem>) { prefs.edit().putString("shopping_list", UserCodec.listToJson(items)).apply() }
    fun addToList(title: String, storeId: String?, offerId: String?, price: Double?): Boolean {
        val items = list()
        if (offerId != null && items.any { it.offerId == offerId && !it.checked }) return false
        val id = "i" + System.currentTimeMillis().toString(36) + (items.size)
        saveList(items + ListItem(id, title.trim(), storeId, offerId, price, false, OffsetDateTime.now().toString()))
        return true
    }
    fun inList(offerId: String): Boolean = list().any { it.offerId == offerId && !it.checked }

    // ---- günlük katalog bildirimi
    var notifyCatalogs: Boolean
        get() = prefs.getBoolean("notify_catalogs", false)
        set(v) { prefs.edit().putBoolean("notify_catalogs", v).apply() }
    /** Bildirim saati (gün içindeki dakika). Varsayılan 09:00. */
    var notifyMinute: Int
        get() = prefs.getInt("notify_minute", 9 * 60)
        set(v) { prefs.edit().putInt("notify_minute", v.coerceIn(0, 24 * 60 - 1)).apply() }
    /** Bildirimi kapatılan mağazalar (boş = tüm mağazalar açık). */
    /** Bölgesel yayınlarda gösterilecek bölge. */
    var region: String
        get() = prefs.getString("region", com.firsat360.app.core.CatalogRules.DEFAULT_REGION) ?: com.firsat360.app.core.CatalogRules.DEFAULT_REGION
        set(v) { prefs.edit().putString("region", v).apply() }

    /** Ana sayfadaki market broşürü rafının kullanıcı sırası (mağaza kimlikleri). Boş = büyüklük sırası. */
    var chainOrder: List<String>
        get() = (prefs.getString("chain_order", "") ?: "").split(",").filter { it.isNotBlank() }
        set(v) { prefs.edit().putString("chain_order", v.joinToString(",")).apply() }

    fun mutedStores(): Set<String> = set("muted_stores")
    fun toggleMutedStore(id: String) = toggle("muted_stores", id)
    var askedNotifications: Boolean
        get() = prefs.getBoolean("asked_notifications", false)
        set(v) { prefs.edit().putBoolean("asked_notifications", v).apply() }

    /** İşlenmiş (bildirilmiş veya bilerek atlanmış) kataloglar. null: ilk çalıştırma. */
    fun seenCatalogs(): Set<String>? = if (prefs.contains("seen_catalogs")) set("seen_catalogs") else null
    fun markCatalogsSeen(ids: Collection<String>) {
        // Sınırsız büyümesin: en fazla 600 kimlik (eski kataloglar zaten süresi bitince feed'den çıkar)
        val all = (set("seen_catalogs") + ids).toList().takeLast(600).toSet()
        prefs.edit().putStringSet("seen_catalogs", all).apply()
    }
    fun notifiedKeys(): Set<String> = set("notified_keys")
    fun addNotified(keys: Collection<String>) {
        val all = (set("notified_keys") + keys).toList().takeLast(200).toSet()
        prefs.edit().putStringSet("notified_keys", all).apply()
    }
    var lastNotifiedAt: Long
        get() = prefs.getLong("last_notified_at", 0L)
        set(v) { prefs.edit().putLong("last_notified_at", v).apply() }

    // ---- veri kullanımı ve depolama
    var showImages: Boolean
        get() = prefs.getBoolean("show_images", true)
        set(v) { prefs.edit().putBoolean("show_images", v).apply() }
    /** Görünüm: "system" (cihaz ayarı), "light" (açık), "dark" (koyu). */
    var themeMode: String
        get() = prefs.getString("theme_mode", "system") ?: "system"
        set(v) { prefs.edit().putString("theme_mode", v).apply() }
    /** Wi‑Fi'de broşürleri önceden hazırla (vitrin kapakları açılışta hazır olur). */
    var prefetchBrochures: Boolean
        get() = prefs.getBoolean("prefetch_brochures", true)
        set(v) { prefs.edit().putBoolean("prefetch_brochures", v).apply() }
    /** Mobil veride görsel indirme (kapalıysa yalnızca Wi-Fi'de; önbellekteki görseller her zaman gösterilir). */
    var imagesOnMobile: Boolean
        get() = prefs.getBoolean("images_on_mobile", true)
        set(v) { prefs.edit().putBoolean("images_on_mobile", v).apply() }
    /** Arka plan güncellemesi yalnızca Wi-Fi'de. */
    var syncWifiOnly: Boolean
        get() = prefs.getBoolean("sync_wifi_only", false)
        set(v) { prefs.edit().putBoolean("sync_wifi_only", v).apply() }
    /** Görsel önbelleği sınırı (MB). 0 = cihaza göre otomatik. */
    var imageCacheMb: Int
        get() = prefs.getInt("image_cache_mb", 0)
        set(v) { prefs.edit().putInt("image_cache_mb", v).apply() }
    // ---- arama geçmişi (yalnızca cihazda, en fazla 10, kullanıcı kapatabilir)
    var saveSearchHistory: Boolean
        get() = prefs.getBoolean("save_search_history", true)
        set(v) { prefs.edit().putBoolean("save_search_history", v).apply(); if (!v) searchHistory = emptyList() }
    var searchHistory: List<String>
        get() = try {
            (com.firsat360.app.core.Json.parse(prefs.getString("search_history", "[]") ?: "[]") as? List<*>)
                ?.mapNotNull { it as? String }?.take(com.firsat360.app.core.SearchHistory.MAX) ?: emptyList()
        } catch (e: Exception) { emptyList() }
        set(v) { prefs.edit().putString("search_history", com.firsat360.app.core.JsonWriter.write(v)).apply() }
    var gridView: Boolean
        get() = prefs.getBoolean("grid_view", true)
        set(v) { prefs.edit().putBoolean("grid_view", v).apply() }

    /** 0.4'ten kalan kullanılmayan kayıtları siler (fiyat alarmı kaldırıldı). */
    fun migrate() {
        if (prefs.contains("price_alerts") || prefs.contains("notify_prices"))
            prefs.edit().remove("price_alerts").remove("notify_prices").apply()
    }
}
