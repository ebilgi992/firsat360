package com.firsat360.app.core

import java.time.Duration
import java.time.OffsetDateTime

/**
 * Günlük katalog bildirimi kararı (saf mantık; Android'den bağımsız, JVM'de test edilir).
 *
 * - Yalnızca gerçekten yeni kataloglar: kaynakta ilk görülmesi son [MAX_AGE_HOURS] saat içinde, süresi bitmemiş,
 *   içeriği (sayfa/PDF/ürün/kapak) olan ve daha önce bildirilmemiş olanlar.
 * - Kullanıcının kapattığı mağazaların katalogları bildirilmez ve "işlendi" sayılır (sonradan açınca yığılma olmaz).
 * - Bildirim saatinden önce bildirim gösterilmez; kataloglar bekletilir (işlenmiş sayılmaz).
 * - Birden fazla yeni katalog tek bildirimde birleştirilir.
 */
object Digest {
    const val TITLE = "🛒 Günün katalogları yayınlandı!"
    const val BODY = "Yeni aktüel ürünleri, fiyatları ve güncel broşürleri keşfetmek için dokun."
    const val MAX_AGE_HOURS = 72L

    /** [handled]: bildirilen veya bilerek atlanan katalog kimlikleri; [waiting]: saat gelince bildirilecekler. */
    data class Decision(val notice: Notice?, val handled: Set<String>, val waiting: List<String>)

    fun fresh(feed: Feed, handled: Set<String>, now: OffsetDateTime): List<Catalog> {
        val today = now.atZoneSameInstant(Dates.TR).toLocalDate()
        return feed.catalogs.filter { c ->
            if (c.id in handled || c.kind == "link_only") return@filter false
            if (!(c.readable || c.coverUrl != null)) return@filter false
            if (Dates.status(c.validFrom, c.validTo, today).kind == ValidityKind.EXPIRED) return@filter false
            val first = Dates.dateTime(c.firstSeenAt ?: c.observedAt) ?: return@filter false
            val hours = Duration.between(first, now).toHours()
            hours in -6..MAX_AGE_HOURS  // küçük saat farkı toleransı
        }
    }

    fun decide(feed: Feed, handled: Set<String>, disabledStores: Set<String>, now: OffsetDateTime,
               notifyMinuteOfDay: Int): Decision {
        val all = fresh(feed, handled, now)
        val off = all.filter { it.storeId in disabledStores }.map { it.id }.toSet()
        val on = all.filter { it.storeId !in disabledStores }
        if (on.isEmpty()) return Decision(null, off, emptyList())
        val local = now.atZoneSameInstant(Dates.TR)
        if (local.hour * 60 + local.minute < notifyMinuteOfDay) return Decision(null, off, on.map { it.id })
        val stores = on.map { it.storeId }.distinct()
        val names = stores.map { feed.store(it)?.name ?: it }
        val (kind, target) = when {
            on.size == 1 -> "catalog" to on[0].id
            stores.size == 1 -> "store" to stores[0]
            else -> "new_catalogs" to ""
        }
        val detail = if (on.size == 1) "${names[0]}: ${on[0].title}" else "${names.joinToString(", ")} · ${on.size} yeni katalog"
        val key = "digest-" + on.map { it.id }.sorted().joinToString("|").hashCode().let { Integer.toHexString(it) }
        return Decision(Notice(kind, key, TITLE, "$BODY\n$detail", target), off + on.map { it.id }, emptyList())
    }

    /** Ana sayfada "Bugün yeni" için: son 48 saatte ilk görülen kataloglar (bildirim durumundan bağımsız). */
    fun newToday(feed: Feed, now: OffsetDateTime): List<Catalog> = feed.catalogs.filter { c ->
        val first = Dates.dateTime(c.firstSeenAt) ?: return@filter false
        c.kind != "link_only" && Duration.between(first, now).toHours() in -6..48 &&
            Dates.status(c.validFrom, c.validTo, now.atZoneSameInstant(Dates.TR).toLocalDate()).kind != ValidityKind.EXPIRED
    }
}
