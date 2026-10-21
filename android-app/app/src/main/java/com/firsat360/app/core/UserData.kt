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
    fun featuredCatalogs(feed: Feed, today: LocalDate, favStores: Set<String> = emptySet()): List<Catalog> {
        val sorted = feed.catalogs.filter { c ->
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
data class ShelfEntry(val store: Store, val catalog: Catalog?, val others: Int)

object Shelf {
    /** Kullanıcının kaydettiği sıra önce; listede olmayan mağazalar büyüklük sırasıyla sona eklenir. */
    fun order(feed: Feed, saved: List<String>): List<Store> {
        val byId = feed.stores.associateBy { it.id }
        val first = saved.mapNotNull { byId[it] }
        val rest = feed.stores.filter { it.id !in saved }.sortedWith(compareBy({ it.chainRank }, { it.sort }, { it.name }))
        return first + rest
    }

    /** Broşür olarak gösterilecek en iyi katalog: sayfa görselli/PDF > ürün listeli > diğer; satışta olan > yakında. */
    fun best(feed: Feed, storeId: String, today: java.time.LocalDate): Pair<Catalog?, Int> {
        val list = feed.catalogs.filter { c ->
            c.storeId == storeId && c.kind != "link_only" &&
                Dates.status(c.validFrom, c.validTo, today).kind != ValidityKind.EXPIRED
        }
        val pick = list.sortedWith(compareBy<Catalog>(
            { if (it.hasPages || it.hasPdf) 0 else if (it.coverUrl != null) 1 else 2 },
            { val d = Dates.date(it.validFrom); if (d == null || !d.isAfter(today)) 0 else 1 },
            { val d = Dates.date(it.validFrom); if (d == null) 0L else -d.toEpochDay() })).firstOrNull()
        return pick to (list.size - if (pick != null) 1 else 0)
    }

    fun build(feed: Feed, saved: List<String>, today: java.time.LocalDate): List<ShelfEntry> =
        order(feed, saved).mapNotNull { s ->
            val (c, others) = best(feed, s.id, today)
            if (c == null && s.brochureUrl.isNullOrBlank()) null else ShelfEntry(s, c, others)
        }
}
