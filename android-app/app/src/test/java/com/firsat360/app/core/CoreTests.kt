package com.firsat360.app.core

import java.io.File
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * Android çekirdek mantığı için JVM testleri. Bu ortamda JUnit indirilemediği için (Maven Central erişimi yok)
 * küçük bir test koşucusu kullanılır. Kullanım: CoreTestsKt <feed.json yolu>
 */
private var passed = 0
private val failures = mutableListOf<String>()

private fun check(name: String, cond: Boolean, detail: () -> String = { "" }) {
    if (cond) passed++ else failures += "$name ${detail()}"
}

fun main(args: Array<String>) {
    val feedText = File(args.getOrElse(0) { "app/src/main/assets/feed.json" }).readText(Charsets.UTF_8)

    // JSON
    val j = Json.parse("""{"a":[1,2.5,"şç\n",true,null],"b":{"c":-3}}""") as Map<*, *>
    check("json.list", (j["a"] as List<*>).size == 5)
    check("json.unicode", (j["a"] as List<*>)[2] == "şç\n")
    check("json.nested", ((j["b"] as Map<*, *>)["c"]) == -3L)
    check("json.error", runCatching { Json.parse("{\"a\":}") }.isFailure)

    // Gerçek feed (12.10 + 13.10.2026 anlık görüntüleri)
    val feed = Feed.parse(feedText)
    check("feed.real", !feed.isDemo && feed.dataMode == "real")
    check("feed.count", feed.offers.size == 531) { "${feed.offers.size}" }
    check("feed.skipped", feed.skipped == 0) { "${feed.skipped}" }
    check("feed.stores", feed.stores.map { it.id } == listOf("a101", "bim", "sok", "migros", "tkkoop", "hakmar", "watsons",
        "metro", "bizimtoptan", "gratis")) { feed.stores.map { it.id }.toString() }
    check("feed.byStore", feed.offers.groupingBy { it.storeId }.eachCount() ==
        mapOf("a101" to 39, "bim" to 347, "migros" to 30, "sok" to 50, "tkkoop" to 65)) { feed.offers.groupingBy { it.storeId }.eachCount().toString() }
    check("store.color", feed.store("sok")?.color == 0xFFF2B600.toInt() && feed.store("tkkoop")?.short == "TK")
    check("store.colorParse", Store.parseColor("#0b8a3e") == 0xFF0B8A3E.toInt() && Store.parseColor("red") == null &&
        Store.parseColor(null) == null)

    // Görseller: yalnızca https, doğrulama etiketi, görselsiz ürün dürüstçe görselsiz
    val withImg = feed.offers.filter { it.hasImage }
    check("img.count", withImg.size == 525) { "${withImg.size}" }
    check("img.verified", feed.offers.count { it.imageVerified } == 495 && feed.offers.filter { it.imageVerified }
        .all { it.imagePageUrl != null && it.imageUrl!!.startsWith("https://") })
    check("img.none", feed.offers.filter { !it.hasImage }.all { it.imageSource == null } &&
        feed.offers.any { !it.hasImage && it.imageStatus == "no_image_on_page" })
    val bulgur = feed.offers.first { it.name.contains("Pilavlık Bulgur") }
    check("img.priceConflict", bulgur.price == 99.0 && bulgur.pagePriceConflict == 107.5 && bulgur.subtitle == "2,5 kg") {
        "${bulgur.price} ${bulgur.pagePriceConflict} ${bulgur.subtitle}" }
    check("thumbs.a101", Thumbs.small("https://cdn2.a101.com.tr/dbmk89vnr/CALL/Image/get/x_2048x2048.png") ==
        "https://cdn2.a101.com.tr/dbmk89vnr/CALL/Image/get/x_384x384.png")
    check("thumbs.sok", Thumbs.small("https://images.ceptesok.com/cdn-cgi/image/width=600,height=600,fit=pad/p.jpg")!!
        .contains("width=320,height=320"))
    check("thumbs.other", Thumbs.small("https://cdn1.bim.com.tr/a.jpg") == "https://cdn1.bim.com.tr/a.jpg")

    // Diğer marketlerde: temkinli eşleşme
    fun mk(id: String, store: String, name: String, variant: String?, price: Double, brand: String? = null) =
        Offer(id, store, null, name, brand, variant, "temel-gida", "k", price, null, null, null, null, null, null, null,
            null, "https://x", "m", "v", "real", emptyList())
    val o1 = mk("1", "bim", "Ayçiçek Yağı", "5 L", 445.0, "ABALI")
    val o2 = mk("2", "sok", "Abalı Ayçiçek Yağı 5 L", null, 459.0)
    val o3 = mk("3", "a101", "Abalı Ayçiçek Yağı 1 L", null, 99.0)
    val o4 = mk("4", "migros", "Ayçiçek Yağı", null, 400.0)  // ölçü yok → eşleşmez
    val o5 = mk("5", "tkkoop", "Abalı Ayçiçek Yağı", "5000 ml", 440.0)
    check("similar.same", SimilarOffers.same(o1, o2) && SimilarOffers.same(o1, o5))
    check("similar.size", !SimilarOffers.same(o1, o3) && !SimilarOffers.same(o1, o4))
    check("similar.sameStore", !SimilarOffers.same(o1, o1.copy(id = "9")))
    check("similar.find", SimilarOffers.find(listOf(o1, o2, o3, o4, o5), o1).map { it.id } == listOf("5", "2"))
    check("similar.realFeed", feed.offers.take(80).all { o -> SimilarOffers.find(feed.offers, o).all { it.storeId != o.storeId } })

    // Önbellek / gömülü akış seçimi: yenisi kazanır
    val older = feed.copy(generatedAt = "2026-10-12T06:00:00+00:00")
    check("feedChoice.bundledNewer", FeedChoice.newer(older, feed) === feed)
    check("feedChoice.cacheNewer", FeedChoice.newer(feed.copy(generatedAt = "2026-10-20T06:00:00+00:00"), feed)!!.generatedAt!!
        .startsWith("2026-10-20"))
    check("feedChoice.null", FeedChoice.newer(null, feed) === feed && FeedChoice.newer(older, null) === older)
    check("feed.source", feed.offers.all { it.sourceUrl.startsWith("https://") && it.observedAt != null })

    // Demo verisi gerçek akışa karışamaz
    val marker = Regex("\"data_mode\":\\s*\"real\"")
    val hits = marker.findAll(feedText).toList()
    check("feed.markers", hits.size >= 3) { "${hits.size}" }
    val second = hits[1].range  // ilk eşleşme akışın kendisi, ikincisi ilk ürün
    val mixed = feedText.replaceRange(second, "\"data_mode\": \"demo\"")
    val mixedFeed = Feed.parse(mixed)
    check("feed.noMix", mixedFeed.offers.size == 530 && mixedFeed.skipped == 1 &&
        mixedFeed.offers.all { it.dataMode == "real" }) { "${mixedFeed.offers.size} ${mixedFeed.skipped}" }

    // Türkçe arama
    check("fold.1", TurkishText.fold("ŞAMPUAN") == "sampuan")
    check("fold.2", TurkishText.fold("ÇİKOLATALI GOFRET MAXİ") == "cikolatali gofret maxi")
    check("fold.3", TurkishText.fold("Işıklı İğne") == "isikli igne")
    check("upper", TurkishText.upper("istanbul ılık") == "İSTANBUL ILIK")
    val s1 = Search.filter(feed.offers, OfferQuery("şampuan"))
    val s2 = Search.filter(feed.offers, OfferQuery("SAMPUAN"))
    check("search.tr", s1.isNotEmpty() && s1.map { it.id }.toSet() == s2.map { it.id }.toSet()) { "${s1.size} ${s2.size}" }
    check("search.multi", Search.filter(feed.offers, OfferQuery("çikolatalı gofret")).any { it.name == "Çikolatalı Gofret Maxi" })
    check("search.none", Search.filter(feed.offers, OfferQuery("zzqqxx")).isEmpty())
    val migrosAsc = Search.filter(feed.offers, OfferQuery(storeIds = setOf("migros"), sort = SortMode.PRICE_ASC))
    check("filter.store.sort", migrosAsc.size == 30 && migrosAsc.zipWithNext().all { it.first.price <= it.second.price })
    val el = Search.filter(feed.offers, OfferQuery(category = "elektronik"))
    check("filter.category", el.isNotEmpty() && el.all { it.category == "elektronik" })
    val cat = Search.filter(feed.offers, OfferQuery(catalogId = "bim-1682"))
    check("filter.catalog", cat.size == 13) { "${cat.size}" }

    // Para ve indirim
    check("money.1", Money.format(34990.0) == "34.990,00 ₺") { Money.format(34990.0) }
    check("money.2", Money.format(19.75) == "19,75 ₺")
    check("money.3", Money.format(499.95) == "499,95 ₺")
    check("disc.1", Money.declaredDiscountPercent(49.5, 65.0) == 23)
    check("disc.none", Money.declaredDiscountPercent(10.0, null) == null && Money.declaredDiscountPercent(10.0, 9.0) == null)

    // Tarihler
    val today = LocalDate.of(2026, 10, 12)
    check("val.upcoming", Dates.status("2026-10-13", null, today).label == "Yarın başlıyor")
    check("val.upcoming2", Dates.status("2026-10-14", null, today).label == "14 Eki başlıyor")
    check("val.active", Dates.status("2026-10-10", "2026-10-16", today).label == "16 Eki tarihine kadar")
    check("val.last", Dates.status("2026-10-10", "2026-10-12", today).kind == ValidityKind.LAST_DAY)
    check("val.unknown", Dates.status(null, null, today).kind == ValidityKind.UNKNOWN)
    check("val.expired", Dates.status(null, "2026-10-01", today).kind == ValidityKind.EXPIRED)
    check("date.long", Dates.long(LocalDate.of(2026, 10, 14)) == "14 Ekim Çarşamba")
    val now = OffsetDateTime.parse("2026-10-12T15:05:00+03:00")
    check("rel", Dates.relative("2026-10-12T09:05:00+00:00", now) == "3 saat önce") { Dates.relative("2026-10-12T09:05:00+00:00", now) }
    check("stamp", Dates.stamp("2026-10-12T09:05:00+00:00") == "12 Ekim 2026, 12:05") { Dates.stamp("2026-10-12T09:05:00+00:00") }

    // Fiyat geçmişi (gerçek feed: 12.10 ve 13.10 gözlemleri)
    val komili = feed.offers.first { it.name == "Komili Ayçiçek Yağı 5 L" }
    check("hist.real", komili.history.size == 2 && komili.history.all { it.price == 499.95 }) { komili.history.toString() }
    val tr = PriceTrend.of(komili.history)
    check("trend.flat", tr.direction == 0 && tr.summary == "Fiyat 1 gündür değişmedi") { tr.summary }
    val down = PriceTrend.of(listOf(PricePoint(120.0, "2026-10-10T09:00:00+00:00"), PricePoint(99.5, "2026-10-13T09:00:00+00:00")))
    check("trend.down", down.direction == -1 && down.summary == "İlk gözleme göre 20,50 ₺ düştü" && down.min == 99.5) { down.summary }
    val up = PriceTrend.of(listOf(PricePoint(10.0, "2026-10-13T06:00:00+00:00"), PricePoint(12.0, "2026-10-13T15:00:00+00:00")))
    check("trend.up", up.direction == 1 && up.summary == "İlk gözleme göre 2,00 ₺ arttı") { up.summary }
    check("hist.count", feed.offers.count { it.history.size >= 2 } >= 250) { "${feed.offers.count { it.history.size >= 2 }}" }
    check("cat.new", feed.categories.any { it.id == "giyim" } && feed.offers.any { it.category == "oyuncak" })
    val tv = Search.filter(feed.offers, OfferQuery("qned tv"))
    check("search.tv", tv.size == 2 && tv.all { it.storeId == "bim" }) { "${tv.size}" }

    // 0.3.0: katalog sayfaları, ürün görselleri, gelir bayrakları
    val bimTue = feed.catalog("bim-1681")!!
    check("cat.pages", bimTue.pages.size == 3 && bimTue.pages[0].verified && !bimTue.pages[1].verified &&
        bimTue.pages[1].thumbUrl!!.contains("/k_")) { bimTue.pages.toString() }
    check("cat.cover", bimTue.coverUrl?.startsWith("https://cdn1.bim.com.tr/uploads/afisler/") == true)
    check("cat.posterOnly", feed.catalog("bim-afis-1413")?.let { it.offerCount == 0 && it.hasPages } == true)
    check("cat.sokGallery", feed.catalog("sok-kampanya-gorselleri")?.pages?.size == 55)
    check("img.migros", feed.offers.filter { it.storeId == "migros" }.all { it.imageUrl != null && it.imageLargeUrl != null })
    // 0.4.0: Migros dışındaki her görsel, ürünün KENDİ resmî sayfasından doğrulanmış ve o mağazanın kendi CDN'inde
    val cdn = mapOf("bim" to "https://cdn1.bim.com.tr/", "a101" to "https://cdn2.a101.com.tr/",
        "sok" to "https://images.ceptesok.com/", "tkkoop" to "https://www.tkkoop.com.tr/assets/images/urun/")
    check("img.noFake", feed.offers.filter { it.storeId != "migros" && it.imageUrl != null }.all {
        it.imageVerified && it.imageUrl!!.startsWith(cdn.getValue(it.storeId)) && it.imagePageUrl == it.productUrl })
    check("money.flags", !feed.adsActive && !feed.affiliateActive && feed.sponsored.isEmpty())
    val today13 = LocalDate.of(2026, 10, 13)
    val featured = Watch.featuredCatalogs(feed, today13)
    check("featured", featured.isNotEmpty() && featured.all { it.kind != "link_only" } && featured.first().hasPages) { featured.take(3).map { it.id }.toString() }
    check("featured.fav", Watch.featuredCatalogs(feed, today13, setOf("migros")).first().storeId == "migros")

    // Kullanıcı verisi: JSON gidiş-dönüş (Türkçe karakter, tırnak, boş fiyat)
    val items = listOf(ListItem("i1", "Süt \"yarım\" yağlı", "bim", "bim-x", 39.5, false, "t"),
        ListItem("i2", "Ekmek", null, null, null, true, "t"))
    check("list.roundtrip", UserCodec.listFromJson(UserCodec.listToJson(items)) == items)
    check("list.corrupt", UserCodec.listFromJson("{bozuk").isEmpty() && UserCodec.listFromJson(null).isEmpty())
    val sum = ShoppingList.summary(items + ListItem("i3", "Peynir", "a101", null, 100.0, false, "t"))
    check("list.summary", sum.open == 2 && sum.done == 1 && Math.abs(sum.knownTotal - 139.5) < 0.001 && sum.unknownPrices == 0)
    check("list.group", ShoppingList.grouped(items).map { it.first } == listOf("bim", null))
    check("list.share", ShoppingList.shareText(items) { it.uppercase() }.contains("[x] Ekmek"))

    // Şema 2: Günün İndirimleri, duyurular, yayın zamanı
    check("schema2", feed.schemaVersion == 2 && feed.dailyDeals != null) { "${feed.schemaVersion}" }
    val deals = feed.dealOffers
    check("deals.count", deals.size == feed.dailyDeals!!.offerIds.size && deals.size in 1..30) { "${deals.size}" }
    check("deals.multiStore", deals.map { it.storeId }.distinct().size >= 3)
    check("deals.images", deals.all { it.imageUrl != null && it.pagePriceConflict == null })
    check("deals.note", feed.dailyDeals!!.note.startsWith("Fiyatlar ve stok durumu"))
    check("catalog.firstSeen", feed.catalogs.all { it.firstSeenAt != null })
    check("schema.old", runCatching { Feed.parse(feedText.replaceFirst("\"schema_version\": 2", "\"schema_version\": 1")) }.isSuccess)
    check("schema.future", runCatching { Feed.parse(feedText.replaceFirst("\"schema_version\": 2", "\"schema_version\": 9")) }.isFailure)

    // Günlük katalog bildirimi kararı (Digest)
    val bimAfis = feed.catalogs.first { it.id.startsWith("bim-afis") && it.firstSeenAt != null }
    val seenAt = Dates.dateTime(bimAfis.firstSeenAt)!!
    val morning = seenAt.atZoneSameInstant(Dates.TR).withHour(8).withMinute(0).plusDays(1).toOffsetDateTime()  // ertesi gün 08:00
    val nine = morning.plusHours(1)
    val allIds = feed.catalogs.map { it.id }.toSet()
    val handled = allIds - bimAfis.id
    check("digest.none", Digest.decide(feed, allIds, emptySet(), nine, 540).notice == null)
    val early = Digest.decide(feed, handled, emptySet(), morning, 540)
    check("digest.waitsForTime", early.notice == null && early.waiting == listOf(bimAfis.id) && bimAfis.id !in early.handled)
    val at9 = Digest.decide(feed, handled, emptySet(), nine, 540)
    check("digest.single", at9.notice?.kind == "catalog" && at9.notice?.targetId == bimAfis.id && bimAfis.id in at9.handled)
    check("digest.text", at9.notice?.title == "🛒 Günün katalogları yayınlandı!" &&
        at9.notice!!.body.startsWith("Yeni aktüel ürünleri, fiyatları ve güncel broşürleri keşfetmek için dokun."))
    check("digest.noRepeat", Digest.decide(feed, handled + at9.handled, emptySet(), nine.plusHours(2), 540).notice == null)
    val muted = Digest.decide(feed, handled, setOf("bim"), nine, 540)
    check("digest.muted", muted.notice == null && bimAfis.id in muted.handled)
    val multi = feed.catalogs.filter { Digest.fresh(feed, emptySet(), nine).any { f -> f.id == it.id } }
    val combined = Digest.decide(feed, emptySet(), emptySet(), nine, 540)
    check("digest.combined", multi.size < 2 || (combined.notice != null && combined.handled.size == multi.size &&
        combined.notice!!.kind in setOf("store", "new_catalogs"))) { "${multi.size} ${combined.notice}" }
    val weekLater = nine.plusDays(5)
    check("digest.tooOld", Digest.decide(feed, handled, emptySet(), weekLater, 540).notice == null)
    check("digest.newToday", Digest.newToday(feed, nine).isNotEmpty() && Digest.newToday(feed, weekLater).isEmpty())

    // Günlük (log) sınırları: 7 gün, 5 MB, maskeleme
    val tmp = java.nio.file.Files.createTempDirectory("f360").toFile()
    val logs = File(tmp, "logs").apply { mkdirs() }
    val old = File(logs, "app-2026-10-01.log").apply { writeText("eski"); setLastModified(System.currentTimeMillis() - 8 * 86_400_000L) }
    for (i in 0..2) File(logs, "app-2026-10-1$i.log").apply { writeBytes(ByteArray(2_500_000)); setLastModified(System.currentTimeMillis() - (3 - i) * 60_000L) }
    val removed = AppLog.cleanup(logs)
    check("log.age", !old.exists() && removed >= 2)
    check("log.size", logs.listFiles()!!.sumOf { it.length() } <= AppLog.MAX_TOTAL && File(logs, "app-2026-10-12.log").exists())
    check("log.noThrow", AppLog.cleanup(File(tmp, "yok")) == 0)
    val masked = AppLog.sanitize("hata https://ex.com/feed.json?token=abc password=gizli")
    check("log.mask", !masked.contains("abc") && !masked.contains("gizli") && masked.length <= 200) { masked }
    AppLog.init(logs, verbose = false); AppLog.d("ayrinti", "görünmemeli"); AppLog.e("sync.fail", "deneme")
    val todayLog = logs.listFiles()!!.filter { it.name.startsWith("app-${AppLog.today()}") }.joinToString { it.readText() }
    check("log.release", todayLog.contains("sync.fail") && !todayLog.contains("görünmemeli"))

    // Önbellek bütçesi: LRU, etkin dosya korunur, yarım indirme temizlenir
    val img = File(tmp, "img").apply { mkdirs() }
    val now0 = System.currentTimeMillis()
    for (i in 0 until 12) File(img, "f$i").apply { writeBytes(ByteArray(10_000)); setLastModified(now0 - (12 - i) * 1000L) }
    File(img, "f0.part").apply { writeBytes(ByteArray(10)); setLastModified(now0 - 3_600_000L) }
    File(img, "f1.part").apply { writeBytes(ByteArray(10)); setLastModified(now0 - 3_600_000L) }
    DiskBudget.trim(img, 100_000, protect = setOf("f0", "f1.part"))
    val left = img.listFiles()!!.map { it.name }.toSet()
    check("cache.limit", img.listFiles()!!.filter { !it.name.endsWith(".part") }.sumOf { it.length() } <= 100_000)
    check("cache.lru", "f11" in left && "f2" !in left)
    check("cache.protect", "f0" in left && "f1.part" in left && "f0.part" !in left)
    check("cache.default", DiskBudget.defaultImageLimitMb(64L * 1024 * 1048576) == 100 && DiskBudget.defaultImageLimitMb(200L * 1048576) == 40)

    // Feed indirme: koşullu istek (304), gzip, bozuk veri önbelleği bozmaz, bağlantı kesintisi ve sonra düzelme
    var srvHits = 0
    var mode = "ok"
    val body = feedText.toByteArray(Charsets.UTF_8)
    val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress("127.0.0.1", 0), 0)
    server.createContext("/feed.json") { ex ->
        srvHits++
        when {
            mode == "down" -> { ex.sendResponseHeaders(503, -1); ex.close() }
            mode == "bad" -> { val b = "{bozuk".toByteArray(); ex.sendResponseHeaders(200, b.size.toLong()); ex.responseBody.use { it.write(b) } }
            ex.requestHeaders.getFirst("If-None-Match") == "\"v1\"" -> { ex.sendResponseHeaders(304, -1); ex.close() }
            else -> {
                val gz = java.io.ByteArrayOutputStream().also { o -> java.util.zip.GZIPOutputStream(o).use { it.write(body) } }.toByteArray()
                ex.responseHeaders.add("ETag", "\"v1\""); ex.responseHeaders.add("Content-Encoding", "gzip")
                ex.sendResponseHeaders(200, gz.size.toLong()); ex.responseBody.use { it.write(gz) }
            }
        }
    }
    server.start()
    val url = "http://127.0.0.1:${server.address.port}/feed.json"
    val target = File(tmp, "feed-cache.json")
    val ff = FeedFetch(url, target, "test")
    val r1 = ff.fetch(null, null, validate = { Feed.parse(it) }, sleep = {})
    check("fetch.updated", r1.status == FeedFetch.Status.UPDATED && r1.etag == "\"v1\"" && target.length() == body.size.toLong())
    val r2 = ff.fetch(r1.etag, null, validate = { Feed.parse(it) }, sleep = {})
    check("fetch.304", r2.status == FeedFetch.Status.NOT_MODIFIED && target.length() == body.size.toLong())
    mode = "bad"
    val r3 = ff.fetch(null, null, validate = { Feed.parse(it) }, sleep = {})
    check("fetch.badKeepsCache", r3.status == FeedFetch.Status.FAILED && Feed.parse(target.readText()).offers.size == 531 &&
        !File(target.path + ".part").exists())
    mode = "down"; srvHits = 0
    val slept = ArrayList<Long>()
    val r4 = ff.fetch(null, null, attempts = 3, validate = { Feed.parse(it) }, sleep = { slept.add(it) })
    check("fetch.retryBackoff", r4.status == FeedFetch.Status.FAILED && srvHits == 3 && slept == listOf(2000L, 4000L)) { "$srvHits $slept" }
    check("fetch.downKeepsCache", target.exists() && Feed.parse(target.readText()).offers.size == 531)
    mode = "ok"
    check("fetch.recovers", ff.fetch(null, null, validate = { Feed.parse(it) }, sleep = {}).status == FeedFetch.Status.UPDATED)
    server.stop(0)
    val offline = FeedFetch("http://127.0.0.1:1/feed.json", File(tmp, "x.json"), "t", connectTimeoutMs = 500)
        .fetch(null, null, attempts = 2, sleep = {})
    check("fetch.offline", offline.status == FeedFetch.Status.FAILED && offline.attempts == 2)
    tmp.deleteRecursively()
    // ---- Arama motoru (Türkçe normalizasyon, market adı, bulanık eşleşme)
    check("norm.exact", TrNorm.exact("  IŞIK   İnci\tŞampuan! ") == "ışık inci şampuan") { TrNorm.exact("  IŞIK   İnci\tŞampuan! ") }
    check("norm.loose", listOf("ışık", "Işık", "ISIK", "isik", "IŞIK").map { TrNorm.loose(it) }.toSet() == setOf("isik"))
    check("norm.dotted", TrNorm.loose("İNCİ") == "inci" && TrNorm.exact("INCI") == "ıncı" && TrNorm.loose("INCI") == "inci")
    check("norm.accent", TrNorm.loose("Café Crème") == "cafe creme" && TrNorm.loose("Çağdaş Üzüm Öğür") == "cagdas uzum ogur")
    check("norm.empty", TrNorm.loose("   ") == "" && TrNorm.tokens(null).isEmpty())
    val eng = SearchEngine(feed)
    val bimCount = feed.listOffers.count { it.storeId == "bim" }
    for (q in listOf("bim", "BİM", "Bim", "BIM", "  bim  ", "BİM market", "bim marketi", "Bim  Market")) {
        val r = eng.search(OfferQuery(q))
        check("search.store[$q]", r.stores.map { it.id } == listOf("bim") && r.offers.size == bimCount && r.offers.all { it.storeId == "bim" }) {
            "${r.stores.map { it.id }} ${r.offers.size}" }
    }
    check("search.a101", eng.search(OfferQuery("a 101")).stores.map { it.id } == listOf("a101") &&
        eng.search(OfferQuery("A101")).stores.map { it.id } == listOf("a101"))
    check("search.sok", eng.search(OfferQuery("şok")).stores.map { it.id } == listOf("sok") &&
        eng.search(OfferQuery("SOK market")).stores.map { it.id } == listOf("sok"))
    check("search.tarimKredi", eng.search(OfferQuery("Tarım Kredi")).stores.map { it.id } == listOf("tkkoop"))
    val sutAll = eng.search(OfferQuery("süt")).offers
    val sutBim = eng.search(OfferQuery("bim süt")).offers
    check("search.storePlusTerm", sutBim.isNotEmpty() && sutBim.all { it.storeId == "bim" } && sutBim.size < sutAll.size) { "${sutBim.size} ${sutAll.size}" }
    check("search.caseTr", eng.search(OfferQuery("ŞAMPUAN")).offers.size == eng.search(OfferQuery("şampuan")).offers.size &&
        eng.search(OfferQuery("sampuan")).offers.size == eng.search(OfferQuery("şampuan")).offers.size)
    val shamp = eng.search(OfferQuery("şampuan")).offers.size
    check("search.hasShampoo", shamp > 0) { "$shamp" }
    val typo = eng.search(OfferQuery("sampuqn"))
    check("search.fuzzy", typo.fuzzy && typo.offers.size == shamp && typo.suggestion == "sampuan") { "${typo.fuzzy} ${typo.suggestion} ${typo.offers.size}" }
    val swap = eng.search(OfferQuery("sapmuan"))
    check("search.transposition", swap.fuzzy && swap.offers.isNotEmpty())
    check("search.noFuzzyShort", eng.search(OfferQuery("xq")).offers.isEmpty() && !eng.search(OfferQuery("xq")).fuzzy)
    check("search.noResult", eng.search(OfferQuery("zzzzqqqxxx")).offers.isEmpty())
    check("search.exactBeatsFuzzy", !eng.search(OfferQuery("peynir")).fuzzy)
    // Tam sonuç varken bulanık eşleşme devreye girmez: her sonuç terimi gerçekten içerir (benzer ama farklı kelime eklenmez)
    for (q in listOf("peynir", "kahve", "deterjan")) {
        val r = eng.search(OfferQuery(q))
        check("search.precise[$q]", !r.fuzzy && r.offers.isNotEmpty() && r.offers.all {
            TrNorm.loose(listOfNotNull(it.brand, it.name, it.variant, it.pageSizeText, feed.categoryName(it.category),
                feed.store(it.storeId)?.name, feed.catalog(it.catalogId)?.title).joinToString(" ")).contains(TrNorm.loose(q)) }) { "${r.offers.size}" }
    }
    check("search.shortNoFuzzy", eng.search(OfferQuery("tzu")).offers.isEmpty())
    check("search.empty", eng.search(OfferQuery("   ")).offers.size == feed.listOffers.size && eng.search(OfferQuery("")).stores.isEmpty())
    check("search.category", eng.search(OfferQuery("elektronik")).offers.isNotEmpty())
    check("search.catalog", eng.search(OfferQuery("migroskop")).catalogs.any { it.storeId == "migros" } ||
        eng.search(OfferQuery("migroskop")).stores.any { it.id == "migros" })
    check("search.filterOverride", eng.search(OfferQuery("bim", storeIds = setOf("a101"))).offers.all { it.storeId == "a101" })
    check("dist", TrNorm.distance("sampuan", "sampuqn", 1) == 1 && TrNorm.distance("sapmuan", "sampuan", 1) == 1 &&
        TrNorm.distance("abc", "xyz", 1) == 2 && TrNorm.allowedTypos("süt") == 0)
    var h = emptyList<String>()
    for (q in listOf("Süt", "  süt ", "SÜT", "peynir", "a", "x".repeat(3))) h = SearchHistory.add(h, q)
    check("history.dedupe", h == listOf("xxx", "peynir", "SÜT")) { h.toString() }
    for (i in 0 until 20) h = SearchHistory.add(h, "ürün $i")
    check("history.limit", h.size == SearchHistory.MAX && h.first() == "ürün 19")
    val t0 = System.nanoTime()
    val big = Feed.parse(feedText).let { f -> f.copy(offers = (0 until 20).flatMap { k -> f.offers.map { it.copy(id = it.id + k) } }) }
    val bigEng = SearchEngine(big)
    val tIndex = (System.nanoTime() - t0) / 1_000_000
    val t1 = System.nanoTime()
    repeat(20) { bigEng.search(OfferQuery("bim süt")); bigEng.search(OfferQuery("sampuqn")) }
    val perQuery = (System.nanoTime() - t1) / 1_000_000.0 / 40
    println("Arama performansı: ${big.offers.size} ürün, dizin $tIndex ms, sorgu başına ${String.format("%.1f", perQuery)} ms")
    check("search.perf", perQuery < 150) { "$perQuery ms" }

    // ---- JSON güvenliği: derin iç içe ve kesik girdi çökmeden reddedilir
    check("json.deep", runCatching { Json.parse("[".repeat(5000) + "]".repeat(5000)) }.exceptionOrNull() is JsonException)
    check("json.truncated", listOf("{", "{\"a\"", "{\"a\":", "[1,", "\"\\u12", "{\"a\":1,").all {
        runCatching { Json.parse(it) }.exceptionOrNull() is JsonException })
    check("json.badNumber", runCatching { Json.parse("[1e]") }.exceptionOrNull() is JsonException)

    // Tüm geçerli kataloglar uygulamada açılabilir (sayfa / PDF / ürün listesi / uygulama içi resmî katalog)
    val visible = feed.catalogs.filter { it.kind != "link_only" && Dates.status(it.validFrom, it.validTo, today13).kind != ValidityKind.EXPIRED }
    check("catalogs.allReadable", visible.all { it.readable }) { visible.filter { !it.readable }.map { it.id }.toString() }
    val feat = Watch.featuredCatalogs(feed, today13).map { it.id }.toSet()
    check("catalogs.flipbooksShown", listOf("a101-afisler", "migros-migroskop-dijital-908", "watsons-ekim-katalog-2026", "gratis-ekim-katalog")
        .all { it in feat }) { (listOf("a101-afisler", "migros-migroskop-dijital-908", "watsons-ekim-katalog-2026", "gratis-ekim-katalog") - feat).toString() }

    check("json.writer", (Json.parse(JsonWriter.write(mapOf("a" to listOf(1, 2.5, null, true), "ş" to "\n\u0001"))) as Map<*, *>)["ş"] == "\n\u0001")

    // 0.8: market broşürü rafı — kullanıcı sırası önce, kalanlar büyüklük sırasıyla; her mağaza bir kez
    val defaultOrder = Shelf.order(feed, emptyList()).map { it.id }
    check("shelf.allStores", defaultOrder.toSet() == feed.stores.map { it.id }.toSet() && defaultOrder.size == feed.stores.size)
    val custom = Shelf.order(feed, listOf("migros", "yok", "sok")).map { it.id }
    check("shelf.customFirst", custom.take(2) == listOf("migros", "sok") && custom.size == feed.stores.size) { custom.toString() }
    val shelf = Shelf.build(feed, emptyList(), today13)
    check("shelf.bimHasBrochure", shelf.firstOrNull { it.store.id == "bim" }?.catalog != null)
    check("shelf.noExpired", shelf.all { e -> e.catalog == null ||
        Dates.status(e.catalog.validFrom, e.catalog.validTo, today13).kind != ValidityKind.EXPIRED })
    val rich = Shelf.best(feed, "bim", today13).first
    check("shelf.prefersPages", rich == null || rich.hasPages || rich.hasPdf ||
        feed.catalogs.none { it.storeId == "bim" && (it.hasPages || it.hasPdf) &&
            Dates.status(it.validFrom, it.validTo, today13).kind != ValidityKind.EXPIRED })

    // V1: ana sayfada aynı katalog iki kez görünmez
    val home = HomeCatalogs.build(feed, emptyList(), today13)
    check("home.noDuplicates", home.allIds.size == home.allIds.toSet().size) { home.allIds.groupingBy { it }.eachCount().filter { it.value > 1 }.toString() }
    check("home.startingToday", home.startingToday.all { Dates.date(it.validFrom) == today13 })
    check("home.endingSoon", home.endingSoon.all { val t = Dates.date(it.validTo)!!; !t.isBefore(today13) && t.toEpochDay() - today13.toEpochDay() <= 2 })

    // 1.2: merkezi durum kuralı + bölgesel insert kopyaları (21.10.2026 canlı veriyle aynı yapı)
    val d21 = LocalDate.of(2026, 10, 21)
    fun regional(id: String, t: String, r: String, f: String, to: String, g: String) = Catalog(id, "bolgeselmarket", "$t · $r", "pdf", f, to,
        "https://hakmar.com.tr/x", "live_html", null, 0, "https://hakmar.com.tr/c.jpg",
        pdfUrl = "https://hakmar.com.tr/download/$id.pdf", region = r.lowercase().replace("İ", "i").replace("ı", "i"), group = g)
    val regCats = listOf(
        regional("bolge-istanbul-944", "Çok Al Az Öde", "istanbul", "2026-10-13", "2026-10-26", "g944"),
        regional("bolge-istanbul-959", "Taze Ürünler", "istanbul", "2026-10-15", "2026-10-21", "g959"),
        regional("bolge-bursa-945", "Çok Al Az Öde", "bursa", "2026-10-13", "2026-10-26", "g944"),
        regional("bolge-bursa-957", "Taze Ürünler", "bursa", "2026-10-15", "2026-10-21", "g959"),
        regional("bolge-trakya-947", "Çok Al Az Öde", "trakya", "2026-10-13", "2026-10-26", "g944"),
        regional("bolge-trakya-956", "Taze Ürünler", "trakya", "2026-10-15", "2026-10-21", "g959"))
    val regStore = Store("bolgeselmarket", "Bölgesel Market", "u", "u", "ok", null, null, null, null, null, chainRank = 8)
    val oFeed = feed.copy(stores = feed.stores + regStore, catalogs = regCats)
    val oh = HomeCatalogs.build(oFeed, emptyList(), d21, emptySet(), "istanbul")
    check("region.noGroupDup", oh.allGroups.size == oh.allGroups.toSet().size) { oh.allGroups.toString() }
    check("region.oneCardPerInsert", oh.allIds.size == 2) { oh.allIds.toString() }
    check("region.userRegion", oh.allIds.all { it.startsWith("bolge-istanbul-") }) { oh.allIds.toString() }
    check("region.bursa", HomeCatalogs.build(oFeed, emptyList(), d21, emptySet(), "bursa").allIds.all { it.startsWith("bolge-bursa-") })
    check("state.expiring", CatalogRules.state(regCats[1], d21) == CatalogState.EXPIRING_SOON)
    check("state.active", CatalogRules.state(regCats[0], d21) == CatalogState.ACTIVE)
    check("state.expired", CatalogRules.state(regCats[1], d21.plusDays(1)) == CatalogState.EXPIRED)
    check("state.unknownNotExpiring", CatalogRules.state(regCats[0].copy(validFrom = null, validTo = null), d21) == CatalogState.UNKNOWN)
    check("state.upcoming", CatalogRules.state(regCats[0], LocalDate.of(2026, 10, 1)) == CatalogState.UPCOMING)
    val expiredGone = HomeCatalogs.build(oFeed, emptyList(), LocalDate.of(2026, 10, 22), emptySet(), "istanbul")
    check("region.expiredRemoved", expiredGone.allIds == listOf("bolge-istanbul-944")) { expiredGone.allIds.toString() }
    // aynı yayın farklı adreslerden (farklı id, aynı grup) → tek kart
    val dupUrl = listOf(regCats[0], regCats[0].copy(id = "bolge-istanbul-944b", officialUrl = "https://hakmar.com.tr/y"))
    check("dedupe.sameContentDifferentUrl", CatalogRules.collapse(dupUrl, "istanbul").size == 1)

    // köprü: PDF tarih metni (ŞOK kurumsal)
    check("trdates.single", TrDates.range("21 - 27 Ekim 2026", d21) == (LocalDate.of(2026, 10, 21) to LocalDate.of(2026, 10, 27)))
    check("trdates.twoMonths", TrDates.range("24 Eylül - 07 Ekim 2026", d21) == (LocalDate.of(2026, 9, 24) to LocalDate.of(2026, 10, 7)))
    check("trdates.noYear", TrDates.range("16 Ekim - 1 Kasım", d21) == (LocalDate.of(2026, 10, 16) to LocalDate.of(2026, 11, 1)))
    val dec30 = LocalDate.of(2026, 12, 30)
    check("trdates.yearEndNoYear", TrDates.range("02 - 08 Ocak", dec30) == (LocalDate.of(2027, 1, 2) to LocalDate.of(2027, 1, 8)))
    check("trdates.crossYear", TrDates.range("29 Aralık 2026 - 04 Ocak 2027", dec30) == (LocalDate.of(2026, 12, 29) to LocalDate.of(2027, 1, 4)))
    check("trdates.crossYearNoYear", TrDates.range("29 Aralık - 4 Ocak", LocalDate.of(2027, 1, 3)) == (LocalDate.of(2026, 12, 29) to LocalDate.of(2027, 1, 4)))
    // 1.6.0: kopyalar ve büyük harfli adlar
    check("case.upper", TurkishText.displayCase("GLASS IN LOVE GRAVÜRLÜ AYAKLI CAM KASE 3'LÜ") == "Glass In Love Gravürlü Ayaklı Cam Kase 3'lü") {
        TurkishText.displayCase("GLASS IN LOVE GRAVÜRLÜ AYAKLI CAM KASE 3'LÜ") }
    check("case.acronym", TurkishText.displayCase("SAMSUNG 55 İNÇ QLED TV") == "Samsung 55 İnç QLED TV") { TurkishText.displayCase("SAMSUNG 55 İNÇ QLED TV") }
    check("case.asciiBrand", TurkishText.displayCase("NIVEA SOFT KREM 50 ML") == "Nivea Soft Krem 50 ml") { TurkishText.displayCase("NIVEA SOFT KREM 50 ML") }
    check("case.mixedUntouched", TurkishText.displayCase("Coca-Cola Light Pet 1 L") == "Coca-Cola Light Pet 1 L")
    check("variant.bullets", TurkishText.cleanVariant("• • Napoliten 360 g • Arrabbiata 360 g") == "Napoliten 360 g, Arrabbiata 360 g" &&
        TurkishText.cleanVariant("•") == null && TurkishText.cleanVariant("• 2 L") == "2 L")
    run {
        val base = feed.offers.first { it.storeId == "bim" }
        val cur = base.copy(id = "d-now", name = "Labne", variant = null, validFrom = "2026-10-20", validTo = null, price = 179.0)
        val next = base.copy(id = "d-next", name = "Labne", variant = null, validFrom = "2026-10-27", validTo = null, price = 149.0)
        val other = base.copy(id = "d-size", name = "Labne", variant = "400 g", validFrom = "2026-10-20", validTo = null)
        val out = Dedupe.forLists(listOf(next, cur, other), LocalDate.of(2026, 10, 23)).map { it.id }
        check("dedupe.preferThisWeek", out == listOf("d-now", "d-size")) { out.toString() }
    }
    check("trdates.none", TrDates.range("Haftanın Fırsatları", d21) == null)

    // Fırsat Asistanı (cihazda, kural tabanlı)
    run {
        val asst = Assistant(feed, LocalDate.of(2026, 10, 14), seed = 7)
        val hi = asst.reply("Merhaba")
        check("asst.hello", hi.text.contains("Asistan") && hi.suggestions.isNotEmpty()) { hi.text }
        val milk = asst.reply("en ucuz süt nerede?")
        check("asst.product", milk.offers.isNotEmpty() && milk.text.contains("₺") &&
            milk.offers.all { TrNorm.loose(it.displayTitle + " " + (it.variant ?: "")).contains("sut") }) { milk.text }
        val bim = asst.reply("BİM'de bu hafta ne var")
        check("asst.store", bim.text.startsWith("BİM") && bim.action == "search:BİM") { bim.text }
        val deals = asst.reply("bugün neler indirimde")
        check("asst.deals", deals.offers.isNotEmpty()) { deals.text }
        check("asst.notGreeting", !asst.reply("sabun").text.contains("Ben Fırsat Asistanı"))
        check("asst.help", asst.reply("market nasıl eklerim").action == "add_store")
        check("asst.offlineNotDeal", asst.reply("indirimli deterjan").action != null)
        val miss = asst.reply("xqzvw")
        check("asst.fallback", miss.offers.isEmpty() && miss.suggestions.isNotEmpty()) { miss.text }
        check("asst.stem", Assistant.stem("sutun") == "sut" && Assistant.stem("deterjanlar") == "deterjan")
        check("asst.robot", !asst.reply("robot süpürge").text.contains("Ben Fırsat Asistanı"))
        check("asst.renk", asst.reply("renkli kalem").action != "settings")
        check("asst.hesap", !asst.reply("hesap makinesi").text.contains("kişisel veri"))
        check("asst.loc", Assistant.locative("sok" to "ŞOK") == "ŞOK'ta" && Assistant.locative("x" to "Hakmar") == "Hakmar'da" &&
            Assistant.locative("y" to "Yunus Market") == "Yunus Market'te" && Assistant.locative("z" to "Özdilek") == "Özdilek'te" &&
            Assistant.locative("bim" to "BİM") == "BİM'de") { Assistant.locative("y" to "Yunus Market") }
        check("asst.noInvent", asst.reply("uzay mekiği fiyatı").offers.isEmpty())
    }

    // 1.8: içilecek süt, reyon/alt-dize eşleşmelerinden ayrılır.
    run {
        val sample = feed.offers.first()
        fun offer(id: String, name: String, price: Double) = sample.copy(id = id, name = name, brand = null,
            variant = null, category = "sut-kahvaltilik", price = price, validFrom = "2026-10-01", validTo = "2026-10-30")
        val drink = offer("drink", "UHT Tam Yağlı Süt 1 L", 35.0)
        val drink2 = offer("drink2", "Laktozsuz Süt 1 L", 45.0)
        val others = listOf(offer("powder", "Süt Tozu 200 g", 2.0), offer("jam", "Süt Reçeli", 3.0),
            offer("biscuit", "Sütlü Bisküvi", 1.0), offer("body", "Vücut Sütü", 1.0),
            offer("expired", "Süt 1 L", 1.0).copy(validTo = "2026-10-20"))
        val synthetic = feed.copy(offers = listOf(drink, drink2) + others)
        val response = Assistant(synthetic, LocalDate.of(2026, 10, 26), 1).reply("en ucuz süt")
        check("asst.drinkableMilk", response.offers.map { it.id } == listOf("drink", "drink2")) { response.offers.toString() }
        check("asst.noMilkFallback", Assistant(feed.copy(offers = others), LocalDate.of(2026, 10, 26), 1).reply("en ucuz süt").offers.isEmpty())
    }

    println("Çekirdek testleri: $passed geçti, ${failures.size} başarısız")
    failures.forEach { println("  BAŞARISIZ: $it") }
    if (failures.isNotEmpty()) kotlin.system.exitProcess(1)
}

