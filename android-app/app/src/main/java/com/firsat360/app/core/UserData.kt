package com.firsat360.app.core

import java.time.LocalDate

/**
 * Kullanıcının cihazda tuttuğu veriler (alışveriş listesi) ve katalog seçimleri.
 * Android'e bağımlı değildir; JVM'de test edilir. Sunucuya gönderilmez.
 */
data class ListItem(
    val id: String, val title: String, val storeId: String?, val offerId: String?, val price: Double?,
    val checked: Boolean, val addedAt: String,
)

data class Notice(val kind: String, val key: String, val title: String, val body: String, val targetId: String)

@Suppress("UNCHECKED_CAST")
object UserCodec {
    fun listToJson(items: List<ListItem>): String = JsonWriter.write(items.map {
        mapOf("id" to it.id, "title" to it.title, "store_id" to it.storeId, "offer_id" to it.offerId,
            "price" to it.price, "checked" to it.checked, "added_at" to it.addedAt)
    })

    fun listFromJson(text: String?): List<ListItem> = parseList(text).mapNotNull { m ->
        ListItem(m.str("id") ?: return@mapNotNull null, m.str("title") ?: return@mapNotNull null, m.str("store_id"),
            m.str("offer_id"), m.dbl("price"), m.bool("checked"), m.str("added_at") ?: "")
    }

    private fun parseList(text: String?): List<Map<String, Any?>> {
        if (text.isNullOrBlank()) return emptyList()
        return try {
            (Json.parse(text) as? List<Any?> ?: emptyList()).mapNotNull { it as? Map<String, Any?> }
        } catch (e: JsonException) {
            emptyList()  // bozuk kayıt uygulamayı çökertmez
        }
    }
}

object ShoppingList {
    data class Summary(val open: Int, val done: Int, val knownTotal: Double, val unknownPrices: Int)

    fun summary(items: List<ListItem>): Summary {
        val open = items.filter { !it.checked }
        return Summary(open.size, items.size - open.size, open.mapNotNull { it.price }.sum(), open.count { it.price == null })
    }

    /** Mağazaya göre gruplar (mağazasız serbest maddeler en sonda). */
    val DEFAULT_ORDER = listOf("a101", "bim", "sok", "migros", "tkkoop", "hakmar", "watsons", "metro", "bizimtoptan", "gratis")

    fun grouped(items: List<ListItem>, order: List<String> = DEFAULT_ORDER): List<Pair<String?, List<ListItem>>> {
        return items.groupBy { it.storeId }.entries
            .sortedBy { e -> e.key?.let { order.indexOf(it).let { i -> if (i < 0) 50 else i } } ?: 99 }
            .map { it.key to it.value.sortedBy { x -> x.checked } }
    }

    fun shareText(items: List<ListItem>, storeName: (String) -> String): String = buildString {
        append("FIRSAT360 alışveriş listem\n")
        for ((store, list) in grouped(items)) {
            append("\n").append(store?.let(storeName) ?: "Diğer").append("\n")
            for (i in list) {
                append(if (i.checked) "  [x] " else "  [ ] ").append(i.title)
                i.price?.let { append(" — ").append(Money.format(it)) }
                append("\n")
            }
        }
        val s = summary(items)
        append("\nKalan: ${s.open} ürün · bilinen toplam ${Money.format(s.knownTotal)}")
        if (s.unknownPrices > 0) append(" (${s.unknownPrices} ürünün fiyatı yok)")
    }
}

object Watch {
    /** Bildirilecek yeni kataloglar: daha önce görülmemiş, bitmemiş ve (seçildiyse) favori mağazalardan. */
    fun newCatalogs(feed: Feed, seen: Set<String>, favStores: Set<String>, today: LocalDate): List<Catalog> =
        feed.catalogs.filter { c ->
            c.id !in seen && c.kind != "link_only" && (c.offerCount > 0 || c.hasPages) &&
                (favStores.isEmpty() || c.storeId in favStores) &&
                Dates.status(c.validFrom, c.validTo, today).kind != ValidityKind.EXPIRED
        }

    /** Öne çıkan kataloglar: bugün/yakında geçerli, sayfası veya ürünü olan; yakın tarihli önce. */
    fun featuredCatalogs(feed: Feed, today: LocalDate, favStores: Set<String> = emptySet(),
                         region: String = CatalogRules.DEFAULT_REGION): List<Catalog> {
        val sorted = CatalogRules.collapse(feed.catalogs, region).filter { c ->
            c.kind != "link_only" && (c.readable || c.coverUrl != null) &&
                Dates.status(c.validFrom, c.validTo, today).kind != ValidityKind.EXPIRED
        }.sortedWith(compareBy<Catalog>({ if (it.storeId in favStores) 0 else 1 },
            { if (it.hasPages || it.hasPdf) 0 else if (it.coverUrl != null) 1 else 2 },
            { val d = Dates.date(it.validFrom); if (d == null) 99L else Math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, d)) }))
        // Mağazalar arasında sırayla (tek mağaza satırı doldurmasın); favori mağazalar yine önce.
        val queues = LinkedHashMap<String, ArrayDeque<Catalog>>()
        for (c in sorted) queues.getOrPut(c.storeId) { ArrayDeque() }.addLast(c)
        val out = ArrayList<Catalog>(sorted.size)
        while (queues.values.any { it.isNotEmpty() }) for (q in queues.values) if (q.isNotEmpty()) out.add(q.removeFirst())
        return out
    }
}


/** Ana sayfadaki "Market broşürleri" rafının bir kartı: mağaza + en iyi güncel broşürü (yoksa resmî broşür sayfası). */
data class ShelfEntry(val store: Store, val catalog: Catalog?, val others: Int, val bridge: BridgeEntry? = null)

object Shelf {
    /** Kullanıcının kaydettiği sıra önce; listede olmayan mağazalar büyüklük sırasıyla sona eklenir. */
    fun order(feed: Feed, saved: List<String>): List<Store> {
        val byId = feed.stores.associateBy { it.id }
        val first = saved.mapNotNull { byId[it] }
        val rest = feed.stores.filter { it.id !in saved }.sortedWith(compareBy({ it.chainRank }, { it.sort }, { it.name }))
        return first + rest
    }

    /** Broşür olarak gösterilecek en iyi katalog: sayfa görselli/PDF > ürün listeli > diğer; satışta olan > yakında. */
    fun best(feed: Feed, storeId: String, today: java.time.LocalDate,
             region: String = CatalogRules.DEFAULT_REGION): Pair<Catalog?, Int> {
        val list = CatalogRules.collapse(feed.catalogs, region).filter { c ->
            c.storeId == storeId && c.kind != "link_only" &&
                Dates.status(c.validFrom, c.validTo, today).kind != ValidityKind.EXPIRED
        }
        val pick = list.sortedWith(compareBy<Catalog>(
            { if (it.hasPages || it.hasPdf) 0 else if (it.coverUrl != null) 1 else 2 },
            { val d = Dates.date(it.validFrom); if (d == null || !d.isAfter(today)) 0 else 1 },
            { val d = Dates.date(it.validFrom); if (d == null) 0L else -d.toEpochDay() })).firstOrNull()
        return pick to (list.size - if (pick != null) 1 else 0)
    }

    fun build(feed: Feed, saved: List<String>, today: java.time.LocalDate,
              region: String = CatalogRules.DEFAULT_REGION): List<ShelfEntry> =
        order(feed, saved).mapNotNull { s ->
            val bridge = feed.clientBridge[s.id]
            // İstemci köprüsündeki market: raf kartı resmî sayfadan cihazda okunan güncel broşürü açar (sunucu kataloğu
            // seçilmez; o marketin diğer resmî katalogları "Diğer kataloglar"da kalır).
            if (bridge != null) return@mapNotNull ShelfEntry(s, null, 0, bridge)
            val (c, others) = best(feed, s.id, today, region)
            if (c == null && s.brochureUrl.isNullOrBlank()) null else ShelfEntry(s, c, others)
        }
}


/**
 * Ana sayfanın katalog bölümleri, her katalog EN FAZLA BİR bölümde (mükerrer kart yok). Öncelik: market broşürü rafı →
 * bugün başlayanlar → süresi dolmak üzere (2 gün içinde) → diğer kataloglar. Yalnızca gerçek veriden; boş bölüm gösterilmez.
 */
data class HomeCatalogs(val shelf: List<ShelfEntry>, val startingToday: List<Catalog>, val endingSoon: List<Catalog>,
                        val others: List<Catalog>) {
    val allIds: List<String> get() = shelf.mapNotNull { it.catalog?.id } + startingToday.map { it.id } +
        endingSoon.map { it.id } + others.map { it.id }
    val allGroups: List<String> get() = shelf.mapNotNull { it.catalog?.groupKey } + startingToday.map { it.groupKey } +
        endingSoon.map { it.groupKey } + others.map { it.groupKey }

    companion object {
        fun build(feed: Feed, saved: List<String>, today: LocalDate, favStores: Set<String> = emptySet(),
                  region: String = CatalogRules.DEFAULT_REGION): HomeCatalogs {
            val shelf = Shelf.build(feed, saved, today, region)
            // Kimlik VE grup birlikte izlenir: bir yayının hiçbir kopyası ikinci bir bölümde görünmez.
            val used = shelf.mapNotNull { it.catalog?.groupKey }.toMutableSet()
            val visible = Watch.featuredCatalogs(feed, today, favStores, region)
            val starting = visible.filter { it.groupKey !in used && !it.isPoster && Dates.date(it.validFrom) == today &&
                CatalogRules.state(it, today) != CatalogState.EXPIRED }
            used += starting.map { it.groupKey }
            val ending = visible.filter { it.groupKey !in used && CatalogRules.state(it, today) == CatalogState.EXPIRING_SOON }
                .sortedBy { Dates.date(it.validTo) }
            used += ending.map { it.groupKey }
            val others = visible.filter { it.groupKey !in used }
            return HomeCatalogs(shelf, starting, ending, others)
        }
    }
}


/** Kataloğun TEK temel durumu (Türkiye tarihine göre). Tüm bölümler bu kuralı kullanır; ikinci bir durum sistemi yok. */
enum class CatalogState { UPCOMING, ACTIVE, EXPIRING_SOON, EXPIRED, UNKNOWN }

object CatalogRules {
    const val DEFAULT_REGION = "istanbul"
    /** "Süresi dolmak üzere": bitiş tarihi BİLİNEN ve bugünden en fazla bu kadar gün sonra olan geçerli kataloglar. */
    const val EXPIRING_DAYS = 2L

    fun state(c: Catalog, today: LocalDate): CatalogState {
        val f = Dates.date(c.validFrom)
        val t = Dates.date(c.validTo)
        return when {
            t != null && t.isBefore(today) -> CatalogState.EXPIRED
            f != null && f.isAfter(today) -> CatalogState.UPCOMING
            t != null && java.time.temporal.ChronoUnit.DAYS.between(today, t) <= EXPIRING_DAYS -> CatalogState.EXPIRING_SOON
            f != null || t != null -> CatalogState.ACTIVE
            else -> CatalogState.UNKNOWN  // tarih yok: süre hesabı yapılmaz, "bitmek üzere" sayılmaz
        }
    }

    /**
     * Aynı yayının kopyalarını (bölge sürümleri, farklı adresler) tek karta indirger. Kullanıcının bölgesine ait kopya,
     * yoksa bölgesiz kopya, yoksa ilk kopya seçilir. Sıra korunur (grubun ilk görüldüğü yer).
     */
    fun collapse(list: List<Catalog>, region: String = DEFAULT_REGION): List<Catalog> {
        val groups = LinkedHashMap<String, MutableList<Catalog>>()
        for (c in list) groups.getOrPut(c.groupKey) { ArrayList() }.add(c)
        return groups.values.map { g -> g.firstOrNull { it.region == region } ?: g.firstOrNull { it.region == null } ?: g[0] }
    }

    /** Bu katalog grubunun mevcut bölgeleri (bölge seçici için). */
    fun regions(feed: Feed, c: Catalog): List<String> =
        feed.catalogs.filter { it.groupKey == c.groupKey }.mapNotNull { it.region }.distinct()
}

/** Türkçe tarih aralığı metni → (başlangıç, bitiş). "21 - 27 Ekim 2026", "24 Eylül - 07 Ekim 2026", "21-27 Ekim". */
object TrDates {
    private val months = listOf("ocak", "şubat", "mart", "nisan", "mayıs", "haziran", "temmuz", "ağustos", "eylül",
        "ekim", "kasım", "aralık")
    private fun month(s: String): Int? {
        val k = s.lowercase(java.util.Locale.forLanguageTag("tr"))
        val i = months.indexOfFirst { k.startsWith(it.take(3)) }
        return if (i >= 0) i + 1 else null
    }

    /**
     * "21 - 27 Ekim 2026", "24 Eylül - 07 Ekim", "29 Aralık 2026 - 04 Ocak 2027". Yıl yazmıyorsa bugüne en yakın yıl
     * seçilir (30 Aralık'ta "02-08 Ocak" → gelecek yıl; 3 Ocak'ta "29 Aralık - 4 Ocak" → başlangıç geçen yıl).
     */
    fun range(text: String?, today: LocalDate): Pair<LocalDate, LocalDate>? {
        val t = text?.trim() ?: return null
        val two = Regex("""\b(\d{1,2})\s+(\p{L}+)(?:\s+(20\d\d))?\s*[-–]\s*(\d{1,2})\s+(\p{L}+)(?:\s+(20\d\d))?""").find(t)
        val one = Regex("""\b(\d{1,2})\s*[-–]\s*(\d{1,2})\s+(\p{L}+)(?:\s+(20\d\d))?""").find(t)
        return try {
            when {
                two != null -> {
                    val (d1, m1, y1, d2, m2, y2) = two.destructured
                    val mm1 = month(m1) ?: return null; val mm2 = month(m2) ?: return null
                    var end = LocalDate.of(y2.toIntOrNull() ?: y1.toIntOrNull()?.let { if (mm2 < mm1) it + 1 else it } ?: today.year, mm2, d2.toInt())
                    if (y2.isEmpty() && y1.isEmpty()) end = nearest(end, today)
                    val ys = y1.toIntOrNull() ?: if (mm1 > mm2) end.year - 1 else end.year
                    LocalDate.of(ys, mm1, d1.toInt()) to end
                }
                one != null -> {
                    val (d1, d2, m, y) = one.destructured
                    val mm = month(m) ?: return null
                    var end = LocalDate.of(y.toIntOrNull() ?: today.year, mm, d2.toInt())
                    if (y.isEmpty()) end = nearest(end, today)
                    LocalDate.of(end.year, mm, d1.toInt()) to end
                }
                else -> null
            }
        } catch (e: Exception) { null }
    }

    /** Yılsız tarih: ±1 yıldan bugüne en yakın olanı (broşürler en fazla birkaç hafta önce/sonra yayımlanır). */
    private fun nearest(d: LocalDate, today: LocalDate): LocalDate =
        listOf(d.minusYears(1), d, d.plusYears(1)).minByOrNull { kotlin.math.abs(java.time.temporal.ChronoUnit.DAYS.between(today, it)) }!!
}
