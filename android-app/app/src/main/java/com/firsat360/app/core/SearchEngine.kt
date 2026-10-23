package com.firsat360.app.core

import java.text.Normalizer

/**
 * Türkçe arama normalizasyonu (saf Kotlin; JVM'de test edilir).
 *
 * İki anahtar üretilir:
 *  - [exact]: Türkçe kurallarına göre küçük harf (I→ı, İ→i), noktalama → boşluk, boşluklar tekleştirilir.
 *    "IŞIK" → "ışık", "İNCİ" → "inci".
 *  - [loose]: exact + aksan/Türkçe harf katlama (ç→c, ğ→g, ı→i, ö→o, ş→s, ü→u, â→a …).
 *    "ışık", "Işık", "isik", "ISIK" hepsi → "isik". Eşleşme bu anahtarla yapılır; exact eşleşme sıralamada öne alınır.
 * Böylece "I/İ/ı/i" dönüşümü gelişigüzel değil, iki ayrı ve kontrollü adımda yapılır.
 */
object TrNorm {
    fun exact(text: String?): String {
        if (text.isNullOrEmpty()) return ""
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val c = when (ch) {
                'I' -> 'ı'; 'İ' -> 'i'
                else -> ch.lowercaseChar()
            }
            sb.append(if (c.isLetterOrDigit() || c == '%') c else ' ')
        }
        return collapse(sb)
    }

    fun loose(text: String?): String {
        val e = exact(text)
        if (e.isEmpty()) return ""
        val sb = StringBuilder(e.length)
        for (ch in e) {
            sb.append(when (ch) {
                'ç' -> 'c'; 'ğ' -> 'g'; 'ı' -> 'i'; 'ö' -> 'o'; 'ş' -> 's'; 'ü' -> 'u'
                'â' -> 'a'; 'î' -> 'i'; 'û' -> 'u'
                else -> ch
            })
        }
        // Diğer aksanlar (é, ñ …): birleşik işaretleri at
        val n = Normalizer.normalize(sb, Normalizer.Form.NFD)
        val out = StringBuilder(n.length)
        for (ch in n) if (Character.getType(ch) != Character.NON_SPACING_MARK.toInt()) out.append(ch)
        return collapse(out)
    }

    fun tokens(text: String?): List<String> = TurkishText.words(loose(text))

    private fun collapse(sb: CharSequence): String {
        val out = StringBuilder(sb.length)
        var space = true
        for (ch in sb) {
            if (ch == ' ' || ch.isWhitespace()) { if (!space) { out.append(' '); space = true } }
            else { out.append(ch); space = false }
        }
        if (out.isNotEmpty() && out[out.length - 1] == ' ') out.setLength(out.length - 1)
        return out.toString()
    }

    /** Sınırlı Damerau-Levenshtein (komşu harf yer değiştirmesi tek hata): [max]'ı aşarsa max+1 döner. */
    fun distance(a: String, b: String, max: Int): Int {
        if (Math.abs(a.length - b.length) > max) return max + 1
        val n = b.length
        var pp = IntArray(n + 1)
        var prev = IntArray(n + 1) { it }
        var cur = IntArray(n + 1)
        for (i in 1..a.length) {
            cur[0] = i
            var rowMin = i
            for (j in 1..n) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var v = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) v = minOf(v, pp[j - 2] + 1)
                cur[j] = v
                if (v < rowMin) rowMin = v
            }
            if (rowMin > max) return max + 1
            val t = pp; pp = prev; prev = cur; cur = t
        }
        return minOf(prev[n], max + 1)
    }

    /** Terim uzunluğuna göre izin verilen yazım hatası: 1–3 harf: 0, 4–6: 1, 7+: 2. */
    fun allowedTypos(term: String): Int = when { term.length < 4 -> 0; term.length < 7 -> 1; else -> 2 }
}

/** Market adı eşleşmesi: "bim", "BİM market", "a 101", "şok marketi", "tarım kredi" … */
object StoreAliases {
    private val extra = mapOf(
        "a101" to listOf("a 101", "a101 market"), "bim" to listOf("bim market", "bim marketleri"),
        "sok" to listOf("sok market", "sok marketleri", "cepte sok"), "migros" to listOf("migros jet", "macrocenter", "migroskop"),
        "tkkoop" to listOf("tarim kredi", "tarim kredi kooperatif", "tk koop", "koop"), "hakmar" to listOf("hakmar express"),
        "watsons" to emptyList(), "metro" to listOf("metro market", "metro toptan"),
        "bizimtoptan" to listOf("bizim toptan", "bizim"), "gratis" to emptyList(),
    )
    /** Tek başına market anlamı taşımayan dolgu kelimeleri (yalnızca market eşleştiğinde atılır). */
    val FILLER = setOf("market", "marketi", "marketler", "marketleri", "magaza", "magazasi", "magazalari", "indirim",
        "indirimleri", "aktuel", "katalog", "katalogu", "brosur", "kampanya", "urunleri", "urunler", "firsat", "firsatlari")

    fun aliases(store: Store): Set<String> {
        val out = LinkedHashSet<String>()
        out += TrNorm.loose(store.name)
        out += TrNorm.loose(store.id)
        store.short?.let { if (it.length >= 2) out += TrNorm.loose(it) }
        extra[store.id]?.forEach { out += TrNorm.loose(it) }
        return out.filter { it.isNotEmpty() }.toSet()
    }
}

data class SearchResult(
    val offers: List<Offer>,
    /** Sorgu bir market adını içeriyorsa o market(ler). */
    val stores: List<Store>,
    val catalogs: List<Catalog>,
    /** Tam eşleşme yoksa yazım hatasına toleranslı (yakın) sonuçlar gösteriliyor. */
    val fuzzy: Boolean,
    /** Kullanıcıya gösterilecek düzeltilmiş sorgu (yakın sonuçlarda). */
    val suggestion: String?,
)

/**
 * Arama motoru. Feed başına bir kez dizin kurulur (ürün başına katlanmış metin + kelime sözlüğü); sorgu başına
 * yalnızca dizin taranır. Alanlar: ürün adı, marka, açıklama/ölçü, kategori adı, market adı, katalog başlığı.
 */
class SearchEngine(private val feed: Feed) {
    private class Doc(val offer: Offer, val loose: String, val tokens: List<String>, val exact: String)

    private val docs: List<Doc> = feed.listOffers.map { o ->
        val parts = listOfNotNull(o.brand, o.name, o.variant, o.pageSizeText, feed.categoryName(o.category),
            feed.store(o.storeId)?.name, feed.catalog(o.catalogId)?.title)
        val joined = parts.joinToString(" ")
        val l = TrNorm.loose(joined)
        Doc(o, l, TurkishText.words(l), TrNorm.exact(joined))
    }
    private val vocabulary: Set<String> = docs.flatMapTo(HashSet()) { it.tokens }
    private val storeAlias: List<Pair<Store, Set<String>>> = feed.stores.map { it to StoreAliases.aliases(it) }

    /** Sorgudan market adlarını ayıklar: (eşleşen marketler, kalan terimler). Çok kelimeli adlar önce denenir. */
    fun parseStores(terms: List<String>): Pair<List<Store>, List<String>> {
        val matched = LinkedHashSet<Store>()
        val rest = ArrayList<String>()
        var i = 0
        loop@ while (i < terms.size) {
            for (n in 3 downTo 1) {
                if (i + n > terms.size) continue
                val phrase = terms.subList(i, i + n).joinToString(" ")
                val hit = storeAlias.firstOrNull { (_, al) -> phrase in al }
                if (hit != null) { matched += hit.first; i += n; continue@loop }
            }
            rest += terms[i]; i++
        }
        val cleaned = if (matched.isNotEmpty()) rest.filter { it !in StoreAliases.FILLER } else rest
        return matched.toList() to cleaned
    }

    fun search(q: OfferQuery): SearchResult {
        val all = TrNorm.tokens(q.text)
        val (stores, terms) = parseStores(all)
        val storeFilter = if (q.storeIds.isNotEmpty()) q.storeIds else stores.map { it.id }.toSet()
        val base = docs.filter { d ->
            (storeFilter.isEmpty() || d.offer.storeId in storeFilter) &&
                (q.category == null || d.offer.category == q.category) &&
                (q.catalogId == null || d.offer.catalogId == q.catalogId)
        }
        var fuzzy = false
        var suggestion: String? = null
        var hits = base.filter { d -> terms.all { t -> d.loose.contains(t) } }
        if (hits.isEmpty() && terms.isNotEmpty() && terms.any { TrNorm.allowedTypos(it) > 0 }) {
            // Kontrollü bulanık eşleşme: yalnızca tam sonuç yoksa; her terim sözlükteki en yakın kelimeye düzeltilir.
            val fixed = terms.map { t -> if (vocabulary.any { it.contains(t) }) t else closest(t) ?: t }
            if (fixed != terms) {
                val f = base.filter { d -> fixed.all { t -> d.loose.contains(t) } }
                if (f.isNotEmpty()) { hits = f; fuzzy = true; suggestion = fixed.joinToString(" ") }
            }
        }
        val exactQ = TrNorm.exact(q.text)
        val ordered = when (q.sort) {
            SortMode.PRICE_ASC -> hits.sortedBy { it.offer.price }
            SortMode.PRICE_DESC -> hits.sortedByDescending { it.offer.price }
            SortMode.NAME -> hits.sortedBy { TrNorm.loose(it.offer.displayTitle) }
            SortMode.RELEVANCE -> if (terms.isEmpty()) hits else hits.sortedByDescending { score(it, if (fuzzy) TurkishText.words(suggestion!!) else terms, exactQ) }
        }.map { it.offer }
        val catTerms = if (fuzzy) TurkishText.words(suggestion!!) else terms
        val catalogs = if (all.isEmpty()) emptyList() else feed.catalogs.filter { c ->
            (storeFilter.isEmpty() || c.storeId in storeFilter) && c.kind != "link_only" &&
                (catTerms.isEmpty() || TrNorm.loose(c.title).let { t -> catTerms.all { t.contains(it) } })
        }.take(10)
        return SearchResult(ordered, stores, catalogs, fuzzy, suggestion)
    }

    private fun closest(term: String): String? {
        val max = TrNorm.allowedTypos(term)
        var best: String? = null
        var bestD = max + 1
        for (w in vocabulary) {
            if (w.length < 3) continue
            // Kelimenin başı ile de karşılaştır ("samp" yazarken "sampuan")
            val cand = if (w.length > term.length + max) w.substring(0, term.length) else w
            val d = TrNorm.distance(term, cand, max)
            if (d < bestD || (d == bestD && best != null && w.length < best.length)) { bestD = d; best = w }
        }
        return if (bestD <= max) best else null
    }

    private fun score(d: Doc, terms: List<String>, exactQ: String): Int {
        val title = TrNorm.loose(d.offer.displayTitle)
        val titleWords = TurkishText.words(title)
        var s = 0
        for (t in terms) {
            if (title.startsWith(t)) s += 4
            if (titleWords.any { it == t }) s += 3 else if (titleWords.any { it.startsWith(t) }) s += 2
            if (TrNorm.loose(d.offer.brand).startsWith(t)) s += 2
        }
        if (exactQ.isNotEmpty() && d.exact.contains(exactQ)) s += 2  // Türkçe harfleriyle birebir yazılmışsa öne
        return s
    }
}

/** Arama geçmişi: en fazla [MAX] kayıt, yinelenen (katlanmış) sorgular tekilleştirilir, 2 harften kısa kaydedilmez. */
object SearchHistory {
    const val MAX = 10
    fun add(history: List<String>, query: String): List<String> {
        val q = TrNorm.exact(query).let { e -> query.trim().replace(Regex("\\s+"), " ").takeIf { e.length >= 2 } } ?: return history
        val key = TrNorm.loose(q)
        return (listOf(q) + history.filter { TrNorm.loose(it) != key }).take(MAX)
    }
}
