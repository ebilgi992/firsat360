package com.firsat360.app.core

import java.time.Duration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeParseException

object TurkishText {
    private val fromChars = "çğıöşüâîûÇĞIİÖŞÜÂÎÛ"
    private val toChars = "cgiosuaiucgiiosuaiu"

    /** Türkçe duyarlı arama anahtarı: 'ŞAMPUAN' == 'şampuan' == 'sampuan', 'İ'/'I' doğru işlenir. */
    fun fold(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) {
            val idx = fromChars.indexOf(ch)
            val c = if (idx >= 0) toChars[idx] else ch.lowercaseChar()
            sb.append(if (c.isLetterOrDigit() || c == '%') c else ' ')
        }
        return words(sb.toString()).joinToString(" ")
    }

    /** Boşluk/ayraç karakterlerine göre kelimelere böler (stdlib split yerine; Android'de invokedynamic üretmez). */
    fun words(text: String, separators: String = " "): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        for (ch in text) {
            if (separators.indexOf(ch) >= 0) { if (cur.isNotEmpty()) { out.add(cur.toString()); cur.setLength(0) } }
            else cur.append(ch)
        }
        if (cur.isNotEmpty()) out.add(cur.toString())
        return out
    }

    fun upper(text: String): String {
        val sb = StringBuilder(text.length)
        for (ch in text) sb.append(when (ch) { 'i' -> 'İ'; 'ı' -> 'I'; else -> ch.uppercaseChar() })
        return sb.toString()
    }
}

enum class SortMode(val label: String) {
    RELEVANCE("Önerilen"), PRICE_ASC("Fiyat: artan"), PRICE_DESC("Fiyat: azalan"), NAME("A-Z")
}

data class OfferQuery(
    val text: String = "", val storeIds: Set<String> = emptySet(), val category: String? = null,
    val catalogId: String? = null, val sort: SortMode = SortMode.RELEVANCE,
)

object Search {
    /** Eski çağrılar için: tek motor (SearchEngine) kullanılır. Feed başına dizin önbelleklenir. */
    @Volatile private var cached: Pair<Feed, SearchEngine>? = null

    fun engine(feed: Feed): SearchEngine {
        cached?.let { if (it.first === feed) return it.second }
        return SearchEngine(feed).also { cached = feed to it }
    }

    fun filter(feed: Feed, q: OfferQuery): List<Offer> = engine(feed).search(q).offers

    /** Feed'siz basit filtre (katalog/market ekranlarında, metin araması olmadan). */
    fun filter(offers: List<Offer>, q: OfferQuery): List<Offer> {
        val terms = TrNorm.tokens(q.text)
        val out = offers.filter { o ->
            (q.storeIds.isEmpty() || o.storeId in q.storeIds) &&
                (q.category == null || o.category == q.category) &&
                (q.catalogId == null || o.catalogId == q.catalogId) &&
                terms.all { TrNorm.loose(listOfNotNull(o.brand, o.name, o.variant).joinToString(" ")).contains(it) }
        }
        return when (q.sort) {
            SortMode.PRICE_ASC -> out.sortedBy { it.price }
            SortMode.PRICE_DESC -> out.sortedByDescending { it.price }
            SortMode.NAME -> out.sortedBy { TrNorm.loose(it.displayTitle) }
            SortMode.RELEVANCE -> out
        }
    }

    fun categoriesIn(offers: List<Offer>): List<String> =
        offers.groupingBy { it.category }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
}

object Money {
    /** 34990.0 → "34.990,00 ₺" */
    fun format(v: Double): String {
        val cents = Math.round(v * 100)
        val whole = cents / 100
        val frac = (cents % 100).toInt()
        val digits = whole.toString()
        val sb = StringBuilder()
        for ((i, ch) in digits.withIndex()) {
            if (i > 0 && (digits.length - i) % 3 == 0) sb.append('.')
            sb.append(ch)
        }
        val w = sb.toString()
        return "$w,${frac.toString().padStart(2, '0')} ₺"
    }

    /** Yalnızca mağazanın beyan ettiği önceki fiyata göre hesap; doğrulanmış indirim DEĞİLDİR. */
    fun declaredDiscountPercent(price: Double, old: Double?): Int? {
        if (old == null || old <= price || old <= 0) return null
        val pct = ((old - price) / old * 100).toInt()
        return if (pct in 1..95) pct else null
    }
}

object Dates {
    val TR: ZoneId = ZoneId.of("Europe/Istanbul")
    private val months = arrayOf("Oca", "Şub", "Mar", "Nis", "May", "Haz", "Tem", "Ağu", "Eyl", "Eki", "Kas", "Ara")
    private val monthsLong = arrayOf("Ocak", "Şubat", "Mart", "Nisan", "Mayıs", "Haziran", "Temmuz", "Ağustos",
        "Eylül", "Ekim", "Kasım", "Aralık")
    private val days = arrayOf("Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar")

    fun date(iso: String?): LocalDate? = try { iso?.let { LocalDate.parse(it.take(10)) } } catch (e: DateTimeParseException) { null }
    fun dateTime(iso: String?): OffsetDateTime? = try { iso?.let { OffsetDateTime.parse(it) } } catch (e: DateTimeParseException) { null }

    fun short(d: LocalDate): String = "${d.dayOfMonth} ${months[d.monthValue - 1]}"
    fun long(d: LocalDate): String = "${d.dayOfMonth} ${monthsLong[d.monthValue - 1]} ${days[d.dayOfWeek.value - 1]}"

    fun stamp(iso: String?): String {
        val t = dateTime(iso)?.atZoneSameInstant(TR) ?: return "bilinmiyor"
        return "%d %s %d, %02d:%02d".format(t.dayOfMonth, monthsLong[t.monthValue - 1], t.year, t.hour, t.minute)
    }

    fun relative(iso: String?, now: OffsetDateTime): String {
        val t = dateTime(iso) ?: return "bilinmiyor"
        val mins = Duration.between(t, now).toMinutes()
        return when {
            mins < 0 -> stamp(iso)
            mins < 1 -> "az önce"
            mins < 60 -> "$mins dk önce"
            mins < 24 * 60 -> "${mins / 60} saat önce"
            mins < 7 * 24 * 60 -> "${mins / (24 * 60)} gün önce"
            else -> stamp(iso)
        }
    }

    /** Kataloğun bugünkü durumu. Bilinmeyen bitiş tarihi uydurulmaz. */
    fun status(from: String?, to: String?, today: LocalDate): Validity {
        val f = date(from)
        val t = date(to)
        return when {
            t != null && t.isBefore(today) -> Validity(ValidityKind.EXPIRED, "Sona erdi")
            f != null && f.isAfter(today) -> {
                val n = java.time.temporal.ChronoUnit.DAYS.between(today, f)
                Validity(ValidityKind.UPCOMING, if (n == 1L) "Yarın başlıyor" else "${short(f)} başlıyor")
            }
            f != null && f == today -> Validity(ValidityKind.STARTS_TODAY, "Bugün başladı")
            t != null && t == today -> Validity(ValidityKind.LAST_DAY, "Son gün")
            t != null -> Validity(ValidityKind.ACTIVE, "${short(t)} tarihine kadar")
            f != null -> Validity(ValidityKind.ACTIVE, "${short(f)} itibarıyla · stoklarla sınırlı")
            else -> Validity(ValidityKind.UNKNOWN, "Geçerlilik tarihi belirtilmemiş")
        }
    }
}

enum class ValidityKind { UPCOMING, STARTS_TODAY, ACTIVE, LAST_DAY, EXPIRED, UNKNOWN }
data class Validity(val kind: ValidityKind, val label: String)

/** Fiyat gözlemlerinden özet. Yalnızca FIRSAT360'ın kendi gözlemlerine dayanır (mağaza beyanı değil). */
data class PriceTrend(val min: Double, val max: Double, val first: Double, val last: Double, val direction: Int, val summary: String) {
    companion object {
        fun of(points: List<PricePoint>): PriceTrend {
            require(points.isNotEmpty())
            val prices = points.map { it.price }
            val first = prices.first(); val last = prices.last()
            val diff = last - first
            val dir = when { diff < -0.004 -> -1; diff > 0.004 -> 1; else -> 0 }
            val days = Dates.date(points.first().observedAt)?.let { f -> Dates.date(points.last().observedAt)?.let { l ->
                java.time.temporal.ChronoUnit.DAYS.between(f, l) } } ?: 0L
            val span = if (days >= 1) "$days gündür" else "${points.size} gözlemde"
            val summary = when (dir) {
                0 -> "Fiyat $span değişmedi"
                -1 -> "İlk gözleme göre ${Money.format(-diff)} düştü"
                else -> "İlk gözleme göre ${Money.format(diff)} arttı"
            }
            return PriceTrend(prices.min(), prices.max(), first, last, dir, summary)
        }
    }
}

/**
 * "Diğer marketlerde" eşleştirmesi — TEMKİNLİ: yalnızca ambalaj ölçüsü İKİ tarafta da yazılı ve aynıysa ve
 * ad sözcükleri büyük ölçüde örtüşüyorsa (Jaccard ≥ 0,75, en az 2 ortak sözcük) aynı ürün sayılır.
 * Emin olunamayan durumda eşleşme gösterilmez (yanlış karşılaştırma, eksik karşılaştırmadan daha zararlıdır).
 */
object SimilarOffers {
    private val sizeRe = Regex("(?<![0-9])(\\d+(?:[.,]\\d+)?)\\s*(?:x\\s*(\\d+(?:[.,]\\d+)?)\\s*)?(kg|gr|g|ml|lt|l|cl|cc)(?![a-z])")
    private val stop = setOf("ve", "ile", "li", "lu", "adet", "x", "kg", "gr", "g", "ml", "lt", "l", "cl", "cc",
        "cesit", "cesitleri", "farkli", "yeni")

    private fun lowerTr(t: String): String {
        val sb = StringBuilder(t.length)
        for (ch in t) sb.append(when (ch) {
            'I' -> 'ı'; 'İ' -> 'i'; 'ç', 'Ç' -> 'c'; 'ğ', 'Ğ' -> 'g'; 'ı' -> 'i'; 'ö', 'Ö' -> 'o'; 'ş', 'Ş' -> 's'
            'ü', 'Ü' -> 'u'; else -> ch.lowercaseChar()
        })
        return sb.toString().replace('ı', 'i')
    }

    /** Temel birime çevrilmiş ilk ölçü: "2,5 KG" → "g2500", "1 L" → "ml1000". Yoksa null. */
    fun size(vararg texts: String?): String? {
        for (t in texts) {
            val m = sizeRe.find(lowerTr(t ?: "")) ?: continue
            val a = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: continue
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.replace(',', '.')?.toDoubleOrNull() ?: 1.0
            val (unit, k) = when (m.groupValues[3]) {
                "kg" -> "g" to 1000.0; "g", "gr" -> "g" to 1.0; "l", "lt" -> "ml" to 1000.0
                "cl" -> "ml" to 10.0; else -> "ml" to 1.0
            }
            return unit + Math.round(a * b * k)
        }
        return null
    }

    fun tokens(o: Offer): Set<String> {
        val t = TurkishText.fold(listOfNotNull(o.brand, o.name).joinToString(" "))
        return TurkishText.words(t).filter { w -> w.length >= 2 && w !in stop && !w.all { it.isDigit() } &&
            !(w.first().isDigit() && sizeRe.containsMatchIn(w)) }.toSet()
    }

    fun key(o: Offer): String {
        val s = size(o.variant, o.name, o.pageSizeText) ?: return ""
        return tokens(o).sorted().joinToString(" ") + "|" + s
    }

    fun same(a: Offer, b: Offer): Boolean {
        if (a.storeId == b.storeId) return false
        val sa = size(a.variant, a.name, a.pageSizeText) ?: return false
        val sb = size(b.variant, b.name, b.pageSizeText) ?: return false
        if (sa != sb) return false
        val ta = tokens(a); val tb = tokens(b)
        val inter = ta.count { it in tb }
        val union = ta.size + tb.size - inter
        return inter >= 2 && union > 0 && inter.toDouble() / union >= 0.75
    }

    /** Diğer mağazalardaki aynı ürünler, fiyata göre artan. */
    fun find(all: List<Offer>, o: Offer): List<Offer> = all.filter { same(o, it) }.sortedBy { it.price }
}

object FeedChoice {
    /** İki akıştan generated_at'i daha yeni olanı (biri yoksa diğeri). Eşitlikte önbellek (sunucu) tercih edilir. */
    fun newer(cache: Feed?, bundled: Feed?): Feed? {
        if (cache == null) return bundled
        if (bundled == null) return cache
        val c = Dates.dateTime(cache.generatedAt)
        val b = Dates.dateTime(bundled.generatedAt)
        return when {
            c == null -> bundled
            b == null -> cache
            b.isAfter(c) -> bundled
            else -> cache
        }
    }
}

/**
 * Liste küçük görselleri: mağaza görsel servisinin KENDİ boyut parametresiyle küçük sürüm (veri tasarrufu).
 * Türetilmiş adres yüklenemezse uygulama özgün adrese düşer (ImageLoader fallback). Başka kaynak kullanılmaz.
 */
object Thumbs {
    fun small(url: String?): String? {
        if (url == null) return null
        if (url.contains("cdn2.a101.com.tr") && url.contains("/Image/get/") && url.contains("_2048x2048."))
            return url.replace("_2048x2048.", "_384x384.")
        if (url.contains("images.ceptesok.com/cdn-cgi/image/width=600,height=600"))
            return url.replace("width=600,height=600", "width=320,height=320")
        return url
    }
}
