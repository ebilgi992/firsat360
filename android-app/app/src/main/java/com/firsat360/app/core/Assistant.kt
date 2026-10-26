package com.firsat360.app.core

import java.time.LocalDate

/** Asistanın yanıtı: metin, gösterilecek ürün/katalog kartları ve hızlı yanıt önerileri. */
data class AssistantReply(
    val text: String,
    val offers: List<Offer> = emptyList(),
    val catalogs: List<Catalog> = emptyList(),
    val suggestions: List<String> = emptyList(),
    /** Uygulama içi kısayol: "settings", "add_store", "favorites", "list", "search:<sorgu>" */
    val action: String? = null,
    val actionLabel: String? = null,
)

/**
 * Fırsat Asistanı: tamamen cihazda çalışan, ücretsiz ve anahtarsız sohbet. Soruyu sunucuya göndermez; yanıtları
 * uygulamanın elindeki güncel broşür/ürün verisinden (Feed) üretir. Niyetler: selam/teşekkür, bugünün fırsatları,
 * ürün fiyatı ("en ucuz süt"), market ("BİM'de ne var"), kategori, uygulama yardımı ve biraz neşe.
 * Uydurmaz: veride olmayan fiyat veya ürün söylenmez.
 */
class Assistant(private val feed: Feed, private val today: LocalDate, seed: Long = System.nanoTime()) {
    private var previousTerms: List<String> = emptyList()
    private val rnd = java.util.Random(seed)
    private val engine = SearchEngine(feed)
    private fun pick(vararg s: String) = s[rnd.nextInt(s.size)]

    val starters = listOf("100 TL altı süt", "Litre fiyatına göre süt", "Bugün neler indirimde?", "En ucuz süt nerede?", "BİM'de bu hafta ne var?", "Uygulama nasıl çalışır?")

    fun hello(): AssistantReply {
        val n = activeCatalogs().size
        val part = when {
            n > 0 -> "Bu hafta $n broşür ve ${liveOffers().size} ürün elimde, ${liveOffers().count { discount(it) != null }} tanesi indirimli."
            else -> "Broşürler güncelleniyor, yine de sorularını bekliyorum."
        }
        return AssistantReply(pick("Merhaba! Ben Fırsat Asistanı 🛒", "Selam! Fırsat Asistanı burada 👋",
            "Hoş geldin! Ben senin market kankan, Fırsat Asistanı 😊") + " " + part +
            " Ne arıyorsun, birlikte bulalım?", suggestions = starters)
    }

    fun reply(input: String): AssistantReply {
        val raw = input.trim().take(400)
        if (raw.isEmpty()) return AssistantReply("Bir şey yazmayı unuttun galiba 🙂 Ürün adı, market ya da soru yazabilirsin.",
            suggestions = starters)
        val request = ShoppingRequest.parse(raw)
        val q = TrNorm.loose(request.query)
        val words = TurkishText.words(q)
        fun has(vararg k: String) = k.any { key -> if (key.contains(' ')) q.contains(key) else words.any { it == key || (key.length >= 4 && it.startsWith(key)) } }
        // Sohbet/yardım niyetleri yalnızca TAM kelimeyle (ürün adlarıyla karışmasın: "robot süpürge", "renkli kalem")
        fun word(vararg k: String) = k.any { key -> if (key.contains(' ')) (" $q ").contains(" $key ") else words.any { it == key } }

        // --- sohbet
        if (words.size <= 4 && word("merhaba", "merhabalar", "selam", "selamlar", "slm", "sa", "hey", "gunaydin", "iyi aksamlar", "iyi geceler", "naber", "nasilsin", "selamun aleykum"))
            return if (word("nasilsin", "naber")) AssistantReply(pick("Harikayım, indirimleri görünce keyfim yerine geliyor 😄",
                "Bomba gibiyim! Broşürler taze, fiyatlar düşük 🎉") + " Sen nasılsın, bugün ne lazım?", suggestions = starters)
            else hello()
        if (words.size <= 5 && word("tesekkur", "tesekkurler", "tesekkur ederim", "sagol", "sag ol", "sagolun", "eyvallah", "mersi", "super", "harika", "efsane"))
            return AssistantReply(pick("Rica ederim, ne zaman istersen buradayım! 💛", "Ne demek, iyi alışverişler! 🛍️",
                "Asıl ben teşekkür ederim, sepetin bereketli olsun! 😊"), suggestions = starters)
        if (word("gorusuruz", "hosca kal", "bay bay", "bye", "iyi gunler"))
            return AssistantReply(pick("Görüşürüz! Fırsatlar kaçmasın diye ben buradayım 👋", "Hoşça kal, bol indirimli günler! 🎈"))
        if (word("kimsin", "adin ne", "sen nesin", "sen kimsin", "yapay zeka misin", "bot musun", "robot musun"))
            return AssistantReply("Ben Fırsat Asistanı! Telefonunun içinde çalışırım; sorularını hiçbir sunucuya göndermem. " +
                "Bildiğim her şey marketlerin resmî broşürlerinden gelen güncel veriler. Fiyat uydurmam, söz! 🤞", suggestions = starters)
        if (word("saka", "saka yap", "espri", "espri yap", "guldur", "beni guldur", "sikildim", "fikra", "fikra anlat"))
            return AssistantReply(pick(
                "Market arabası neden hep yorgun? Çünkü her hafta aktüel koşusu var! 🛒😄",
                "İndirimi kaçıran domates ne demiş? \"Kızardım valla!\" 🍅",
                "Bir peynir kendini çok değerli hissetmiş… sonra BİM broşürüne girmiş, ayakları yere basmış 🧀😅"),
                suggestions = listOf("Bugün neler indirimde?", "Rastgele bir fırsat göster"))

        // --- uygulama yardımı
        help(q, ::word)?.let { return it }

        // --- bugünün fırsatları
        val dealWords = has("indirim", "firsat", "kampanya", "kelepir", "ucuzluk", "bugun", "ne var", "neler var", "oneri", "rastgele")
        val (stores, rest) = engine.parseStores(words)
        val terms = rest.filter { it !in FILLER && it.length >= 2 }
        if (stores.isEmpty() && terms.isEmpty() && dealWords) return todayDeals(has("rastgele"))

        // --- market
        if (stores.isNotEmpty() && terms.isEmpty()) {
            if (previousTerms.isNotEmpty() && !dealWords) return product(previousTerms, stores, true, request)
            return storeSummary(stores.first())
        }

        // --- kategori
        val cat = feed.categories.firstOrNull { c -> TrNorm.loose(c.name).let { n -> n.length >= 3 && (q.contains(n) || terms.any { t -> t.length >= 4 && n.startsWith(t) }) } }
        if (cat != null && request.budget == null && request.pack == null && !request.unitSort && terms.size <= 2 && terms.none { stem(it) == "sut" }) {
            val l = live().filter { it.category == cat.id && (stores.isEmpty() || stores.any { s -> s.id == it.storeId }) }
                .sortedByDescending { discount(it) ?: 0 }.take(6)
            if (l.isNotEmpty()) return AssistantReply("${cat.name} reyonunda gezindim, işte bu haftanın en dikkat çekenleri 👇",
                offers = l, action = "search:${cat.name}", actionLabel = "Tümünü gör")
        }

        // --- ürün
        if (terms.isNotEmpty()) {
            previousTerms = terms
            return product(terms, stores, cheap = has("ucuz", "fiyat", "kac", "kadar", "nerede", "hangi"), request = request)
        }
        if (previousTerms.isNotEmpty() && (request.budget != null || request.pack != null || request.unitSort)) return product(previousTerms, stores, true, request)
        if (dealWords) return todayDeals(false)
        return AssistantReply(pick("Hımm, bunu tam çözemedim 🙈", "Bunu anlayamadım ama pes etmem! 🤔") +
            " Bir ürün adı (ör. \"süt\"), bir market (ör. \"ŞOK\") ya da \"bugünün fırsatları\" yazmayı dener misin?", suggestions = starters)
    }

    // ------------------------------------------------------------------

    private fun help(q: String, has: (Array<out String>) -> Boolean): AssistantReply? {
        fun h(vararg k: String) = has(k)
        return when {
            h("market ekle", "magaza ekle", "yeni market", "marketim yok", "market eklerim", "market nasil eklenir") || (h("ekle", "eklerim", "eklenir", "eklemek", "ekleyebilir miyim") && h("market", "magaza", "marketi")) -> AssistantReply(
                "Listede olmayan marketini kendin ekleyebilirsin: Ayarlar › Market ekle'ye marketin broşür sayfasının adresini " +
                    "yapıştır, gerisini ben hallederim. Broşürü bulunca haber veririm, yeni broşür çıktığında da bildirim gelir 📬",
                action = "add_store", actionLabel = "Market ekle")
            h("bildirim", "bildirimler", "bildirimi", "haber ver", "bildirim gelmiyor") -> AssistantReply(
                "Her sabah seçtiğin saatte günün broşürlerini tek bir özetle haber veririm; favori marketlerinde yeni broşür " +
                    "çıkınca ayrıca söylerim. Ayarlar › Bildirimler'den saatini ve marketleri seçebilirsin ⏰",
                action = "settings", actionLabel = "Bildirim ayarları")
            h("tema", "temayi", "karanlik mod", "koyu mod", "koyu tema", "acik mod", "acik tema", "gece modu") -> AssistantReply(
                "Ayarlar › Görünüm'den Açık, Koyu ya da Cihaz temasını seçebilirsin 🌗", action = "settings", actionLabel = "Görünüm")
            h("favori", "favoriler", "favorilerim", "favoriye", "favorilere") -> AssistantReply("Bir ürünün kalbine dokununca Favoriler'e eklenir; fiyatı değişirse oradan takip edersin 💛",
                action = "favorites", actionLabel = "Favorilerim")
            h("listem", "listeye", "alisveris listesi", "alisveris listem") -> AssistantReply("Ürün sayfasındaki \"Listeye ekle\" ile alışveriş listen " +
                "Listem sekmesinde birikir; markete göre gruplar, toplamı hesaplarım 🧾", action = "list", actionLabel = "Listem")
            h("cevrim disi", "cevrimdisi", "internetsiz", "offline", "indirmek", "indirebilir") -> AssistantReply("Broşürü açıp üstteki indirme simgesine dokun; " +
                "internet olmadan da açılır 📥")
            h("ucretli mi", "ucretsiz mi", "para ister mi", "abonelik", "uygulama ucretli", "bedava mi", "reklam var mi") -> AssistantReply(
                "FIRSAT360 tamamen ücretsiz; hesap ve abonelik yok 🎉")
            h("gizlilik", "kisisel veri", "verilerim", "hesap acmak", "uyelik", "uye olmak", "konumumu") -> AssistantReply("Hesap açmazsın, kişisel veri toplamam. " +
                "Favorilerin, listen ve eklediğin marketler yalnızca bu telefonda durur 🔒")
            h("nasil calisir", "ne ise yarar", "uygulama ne", "nasil kullanilir", "yardim", "yardim et") -> AssistantReply(
                "Kısaca: marketlerin resmî broşürlerini her gün toplarım, ürünleri ve fiyatları çıkarırım. Vitrinde broşürler, " +
                    "Ara'da ürünler, Listem'de alışveriş listen var. Bana da \"en ucuz yumurta\" gibi sorular sorabilirsin 😊",
                suggestions = starters)
            else -> null
        }
    }

    private fun todayDeals(random: Boolean): AssistantReply {
        val pool = feed.dealOffers.filter { isLive(it) }.ifEmpty { live().sortedByDescending { discount(it) ?: 0 } }
        if (pool.isEmpty()) return AssistantReply("Şu an indirimli ürün bilgisi yok gibi görünüyor 😕 Vitrindeki broşürlere göz atabilirsin.")
        val l = if (random) pool.take(30).shuffled(rnd).take(4) else pool.take(6)
        val top = l.first()
        val d = discount(top)
        val head = pick("Bugünün yıldızları burada! ✨", "Sıcak sıcak fırsatlar geldi 🔥", "Bak neler buldum 😍")
        val star = "${at(top)} ${top.displayTitle} ${Money.format(top.price)}" + (d?.let { ", %$it indirimli" } ?: "")
        return AssistantReply("$head Öne çıkan: $star.", offers = l,
            suggestions = listOf("En ucuz deterjan", "Süt fiyatları", "Rastgele bir fırsat göster"))
    }

    private fun storeSummary(s: Store): AssistantReply {
        val cats = activeCatalogs().filter { it.storeId == s.id }
        val offers = live().filter { it.storeId == s.id }.sortedByDescending { discount(it) ?: 0 }.take(6)
        val catText = when (cats.size) {
            0 -> "Bu hafta için broşür bilgisi henüz gelmedi"
            1 -> "Bu hafta \"${cats[0].title}\" broşürü var"
            else -> "Bu hafta ${cats.size} broşürü var: " + cats.take(3).joinToString(", ") { "\"${it.title}\"" }
        }
        val tail = if (offers.isNotEmpty()) ", işte göz atmaya değer birkaç ürün 👇" else ". Broşürü vitrinden açabilirsin 📖"
        return AssistantReply("${s.name} 🛒 $catText$tail", offers = offers, catalogs = cats.take(3),
            action = "search:${s.name}", actionLabel = "${s.name} ürünleri")
    }

    private fun product(terms: List<String>, stores: List<Store>, cheap: Boolean, request: ShoppingRequest = ShoppingRequest("")): AssistantReply {
        val storeIds = stores.map { it.id }.toSet()
        fun run(t: List<String>) = engine.search(OfferQuery(text = t.joinToString(" "), storeIds = storeIds, sort = SortMode.RELEVANCE))
        var used = terms
        var r = run(terms)
        if (r.offers.isEmpty()) {
            val stemmed = terms.map { stem(it) }
            if (stemmed != terms) { r = run(stemmed); used = stemmed }
        }
        val label = r.suggestion ?: used.joinToString(" ")
        // Önce adı/açıklaması aranan kelimeyi gerçekten içerenler (kategori adından gelen eşleşmeler geride kalır)
        val keys = TurkishText.words(TrNorm.loose(label))
        val named = r.offers.filter { o -> TrNorm.loose(o.displayTitle + " " + (o.variant ?: "")).let { t -> keys.all { key -> TurkishText.words(t).any { it == key || (key.length >= 4 && it.startsWith(key)) } } } }
        val candidates = named.ifEmpty { r.offers }
        val milkIntent = ProductIntent.wantsDrinkableMilk(used)
        val pool = candidates.filter { o ->
            Dates.status(o.validFrom, o.validTo, today).kind != ValidityKind.EXPIRED &&
                (!milkIntent || ProductIntent.drinkableMilk(o)) && request.accepts(o)
        }
        val nowOnes = pool.filter { isLive(it) }
        val available = nowOnes.ifEmpty { pool }
        val kinds = available.mapNotNull { Pack.of(it)?.kind }.distinct()
        val unitKind = request.unitKind ?: kinds.singleOrNull()
        if (request.unitSort && unitKind == null && available.isNotEmpty()) return AssistantReply(
            "Bu sonuçlarda farklı ölçü türleri var. Litre fiyatı mı, kilogram fiyatı mı karşılaştırayım?",
            suggestions = listOf("Litre fiyatına göre ${used.joinToString(" ")}", "Kilogram fiyatına göre ${used.joinToString(" ")}"))
        val comparable = if (request.unitSort) available.filter { Pack.of(it)?.kind == unitKind } else available
        val ranked = when {
            request.unitSort -> comparable.sortedWith(compareBy<Offer> { it.price / Pack.of(it)!!.amount }.thenBy { it.id })
            cheap || request.budget != null -> comparable.sortedWith(compareBy<Offer> { PriceCents.of(it.price) }.thenBy { it.id })
            else -> comparable
        }
        val list = ranked.take(6)
        val where = if (stores.isNotEmpty()) " ${stores.joinToString(", ") { it.name }} için" else ""
        if (list.isEmpty() && stores.any { it.status == "error" }) return AssistantReply(
            "${stores.filter { it.status == "error" }.joinToString(", ") { it.name }} kaynağı şu anda güncellenemiyor. Bu istek için güncel fiyat doğrulayamıyorum. Market ekranındaki resmî broşürü kontrol edebilirsin.",
            suggestions = listOf("Bugün neler indirimde?", "En ucuz süt"))
        if (list.isEmpty()) return AssistantReply(pick("\"$label\"$where bu haftanın broşürlerinde yok gibi 😕",
            "Aradım taradım ama \"$label\"$where bulamadım 🔍") + " Başka bir adla dener misin? (ör. marka adı)",
            suggestions = listOf("Bugün neler indirimde?", "Ayçiçek yağı", "Deterjan"))
        val best = if (request.unitSort) ranked.first() else ranked.minByOrNull { it.price }!!
        val upcoming = nowOnes.isEmpty()
        val msg = buildString {
            append(if (r.fuzzy) "\"$label\" demek istedin sanırım 😊 " else "")
            append(if (list.size == 1) "Bir sonuç buldum!" else pick("${pool.size} sonuç buldum 🎯", "Buldum! ${pool.size} seçenek var 🙌"))
            if (request.unitSort) append(" Kayıtlı verilerde birim fiyatı en düşük seçenek ${at(best)} ${best.displayTitle}: ${Money.format(best.price / Pack.of(best)!!.amount)}/$unitKind (paket ${Money.format(best.price)})")
            else append(" Kayıtlı verilerde en düşük paket fiyatı ${at(best)} ${best.displayTitle}: ${Money.format(best.price)}")
            discount(best)?.let { append(" (%$it indirim)") }
            append(".")
            if (!request.unitSort) append(" Kartlarda paket fiyatı gösterilir. Litre/kilogram fiyatına göre de karşılaştırabilirim.")
            if (request.budget != null) append(" Bütçe: en fazla ${Money.format(request.budget / 100.0)}.")
            request.pack?.let { append(" Ambalaj filtresi: ${it.amount} ${it.kind}.") }
            append(" Şube ve stok durumu garanti edilmez.")
            if (upcoming) append(" Bunlar yakında başlayacak broşürlerde ⏳")
            else Dates.status(best.validFrom, best.validTo, today).takeIf { it.kind == ValidityKind.LAST_DAY }?.let { append(" Son gün, kaçırma! ⏰") }
        }
        return AssistantReply(msg, offers = list, action = "search:$label", actionLabel = "Tüm sonuçlar")
    }

    // ------------------------------------------------------------------

    private fun storeName(o: Offer) = feed.store(o.storeId)?.name ?: o.storeId.uppercase()
    /** "BİM'de", "ŞOK'ta", "Migros'ta", "CarrefourSA'da" */
    private fun at(o: Offer) = locative(feed.store(o.storeId)?.let { it.id to it.name } ?: (o.storeId to o.storeId.uppercase()))
    private fun discount(o: Offer): Int? = o.storeDeclaredOldPrice?.takeIf { it > o.price && o.price > 0 }
        ?.let { Math.round((1 - o.price / it) * 100).toInt() }?.takeIf { it in 1..90 }
    private fun isLive(o: Offer): Boolean = Dates.status(o.validFrom, o.validTo, today).kind.let {
        it != ValidityKind.EXPIRED && it != ValidityKind.UPCOMING }
    private fun live(): List<Offer> = feed.listOffers.filter { isLive(it) }
    private fun liveOffers() = live()
    private fun activeCatalogs(): List<Catalog> = feed.catalogs.filter {
        Dates.status(it.validFrom, it.validTo, today).kind != ValidityKind.EXPIRED }.distinctBy { it.groupKey }

    companion object {
        /** Ürün aranırken atılan soru/dolgu kelimeleri (katlanmış). */
        val FILLER = setOf("en", "ucuz", "ucuzu", "ucuza", "fiyat", "fiyati", "fiyatlari", "kac", "para", "kadar", "nerede",
            "nerde", "var", "mi", "mu", "ne", "neler", "lira", "tl", "bul", "ara", "istiyorum", "almak", "alacagim", "lazim",
            "bunun", "urunun", "olarak", "peki", "olan", "olani", "sadece", "bana", "icin", "hangi", "markette", "marketlerde", "bu", "hafta", "haftanin", "haftaki", "bugun", "bugunku",
            "indirim", "indirimli", "indirimde", "firsat", "firsati", "kampanya", "kampanyali", "ve", "ile", "de", "da", "bir",
            "biraz", "goster", "listele", "oner", "acaba", "su", "o", "simdi", "guncel", "aktuel", "brosur", "brosurde",
            "katalog", "urun", "urunu", "urunler", "satiyor", "satan", "nereden", "alabilirim", "kim", "en iyi", "iyi",
            "market", "marketi", "marketler", "magaza", "lutfen", "rica", "bakar", "misin", "misiniz", "ister", "isterim", "icilecek", "icmelik", "icmek", "icecegim")
        private val SUFFIXES = listOf("lerinin", "larinin", "lerini", "larini", "leri", "lari", "nin", "nun", "nın", "ler", "lar",
            "dan", "den", "tan", "ten", "yi", "yu", "ya", "ye", "in", "un", "da", "de", "ta", "te", "u", "i", "a", "e")
        private val LOC = mapOf("a101" to "'de", "bim" to "'de", "carrefoursa" to "'da", "happycenter" to "'da", "metro" to "'da",
            "tkkoop" to "'ta", "sok" to "'ta", "migros" to "'ta", "mopas" to "'ta", "watsons" to "'ta", "gratis" to "'te")
        /** Türkçe bulunma eki: son ünlüye göre a/e, sert ünsüzden sonra t. Kısaltmalar için okunuşa göre istisnalar. */
        fun locative(store: Pair<String, String>): String {
            val (id, name) = store
            LOC[id]?.let { return name + it }
            val l = TrNorm.loose(name).filter { it.isLetter() }
            val v = l.lastOrNull { it in "aeiou" } ?: 'e'
            val back = TrNorm.exact(name).lastOrNull { it in "aeıioöuü" }?.let { it in "aıou" } ?: (v in "aou")
            val hard = l.lastOrNull()?.let { it in "fstkcshp" } ?: false
            return name + "'" + (if (hard) "t" else "d") + (if (back) "a" else "e")
        }
        fun stem(w: String): String {
            for (s in SUFFIXES) if (w.length - s.length >= 3 && w.endsWith(s)) return w.dropLast(s.length)
            return w
        }
    }
}

