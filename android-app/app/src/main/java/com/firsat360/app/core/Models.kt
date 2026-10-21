package com.firsat360.app.core

data class Store(
    val id: String, val name: String, val officialUrl: String, val catalogUrl: String,
    val status: String, val statusMessage: String?, val lastCheckedAt: String?, val lastSuccessAt: String?,
    val lastError: String?, val legalNote: String?, val imagePolicy: String = "official_cdn",
    /** Feed'deki marka rengi (#RRGGBB → ARGB). Yoksa uygulama varsayılanı kullanılır. */
    val color: Int? = null, val short: String? = null, val kind: String = "market", val sort: Int = 99,
    /** Türkiye'deki büyüklük sırası (ana sayfadaki broşür rafının varsayılan sırası). */
    val chainRank: Int = 99,
    /** Mağazanın resmî broşür/afiş sayfası: broşür sayfaları okunamadığında uygulama içinde tam ekran açılır. */
    val brochureUrl: String? = null,
) {
    val imagesAllowed: Boolean get() = imagePolicy == "official_cdn"
    val shortLabel: String get() = short ?: name.take(2)

    companion object {
        fun parseColor(hex: String?): Int? {
            val h = hex?.trim()?.removePrefix("#") ?: return null
            if (h.length != 6 || h.any { it.lowercaseChar() !in "0123456789abcdef" }) return null
            return (0xFF000000.toInt()) or h.toInt(16)
        }
    }
}

/** Katalog sayfası: mağazanın resmî CDN görseli. verified=false → tam boy adres türetilmiş; yüklenemezse thumbUrl kullanılır. */
data class CatalogPage(val url: String, val thumbUrl: String?, val caption: String?, val link: String?, val verified: Boolean)

data class Catalog(
    val id: String, val storeId: String, val title: String, val kind: String,
    val validFrom: String?, val validTo: String?, val officialUrl: String,
    val sourceMethod: String, val observedAt: String?, val offerCount: Int,
    val coverUrl: String? = null, val pages: List<CatalogPage> = emptyList(),
    /** Mağazanın yayımladığı resmî PDF (ör. Migroskop). İstek üzerine indirilir, uygulama içinde açılır. */
    val pdfUrl: String? = null,
    /** Kataloğun kaynakta ilk görüldüğü an (yayımlanma) ve son kontrol. */
    val firstSeenAt: String? = null, val lastCheckedAt: String? = null,
    /** Bölgesel yayın (ör. Onur: istanbul/bursa/trakya); null = tüm Türkiye. */
    val region: String? = null,
    /** Aynı yayının kopyaları (bölge sürümleri) için ortak anahtar; uygulama gruptan tek kart gösterir. */
    val group: String? = null,
) {
    val groupKey: String get() = group ?: id
    val isPoster: Boolean get() = kind == "poster"
    val hasPages: Boolean get() = pages.isNotEmpty()
    val hasPdf: Boolean get() = pdfUrl != null
    /** Okunabilir içerik: sayfa görselleri, PDF veya ürün listesi. */
    val readable: Boolean get() = hasPages || hasPdf || offerCount > 0 || kind == "flipbook"
    /** Sayfa görseli/PDF yok; resmî katalog sayfası uygulama içinde tam ekran açılır. */
    val webOnly: Boolean get() = !hasPages && !hasPdf && offerCount == 0
}

/** Sunucunun her gün otomatik seçtiği "Günün İndirimleri". */
data class DailyDeals(val date: String?, val title: String, val note: String, val offerIds: List<String>)

/**
 * Uzaktan reklam yapılandırması (yönetim panelinden). Bu derlemede reklam SDK'sı yoktur → [enabled] true olsa bile
 * reklam gösterilmez; SDK'lı derleme (docs/REKLAM_REHBERI.md) bunu ve kullanıcı rızasını kontrol eder.
 */
data class AdsConfig(val enabled: Boolean = false, val homeBanner: Boolean = false, val listBanner: Boolean = false,
                     val nativeEvery: Int = 0, val testMode: Boolean = true)

/** Sunucunun bir kez yaptığı yeni katalog duyurusu (bildirimler bundan türetilir). */
data class Announcement(val id: String, val createdAt: String?, val deliverAt: String?, val title: String,
                        val body: String, val catalogIds: List<String>, val storeIds: List<String>)

data class Sponsored(val type: String, val id: String, val label: String)

data class PricePoint(val price: Double, val observedAt: String)

data class Offer(
    val id: String, val storeId: String, val catalogId: String?, val name: String, val brand: String?,
    val variant: String?, val category: String, val categorySource: String, val price: Double,
    val priceText: String?, val storeDeclaredOldPrice: Double?, val condition: String?, val channel: String?,
    val productUrl: String?, val validFrom: String?, val validTo: String?, val observedAt: String?,
    val sourceUrl: String, val sourceMethod: String, val verification: String, val dataMode: String,
    val history: List<PricePoint>, val imageUrl: String? = null, val imageLargeUrl: String? = null,
    /** product_page_verified | official_listing | official_json | null (görsel yok) */
    val imageSource: String? = null, val imageStatus: String? = null, val imagePageUrl: String? = null,
    val imageCheckedAt: String? = null,
    /** Ürünün kendi sayfasında görülen farklı fiyat (fiyat değiştirilmez, kullanıcıya uyarı gösterilir). */
    val pagePriceConflict: Double? = null,
    /** Liste açıklamasında ölçü yoksa ürün sayfasındaki ölçü. */
    val pageSizeText: String? = null,
) {
    val hasImage: Boolean get() = imageUrl != null
    val imageVerified: Boolean get() = imageSource == "product_page_verified"
    /** Kartta gösterilecek açıklama: listedeki açıklama, yoksa ürün sayfasındaki ölçü. */
    val subtitle: String? get() = variant ?: pageSizeText
    /** Ad + ölçü anahtarı: farklı mağazalarda "aynı ürün" eşleştirmesi için (marka dahil, sözcük sırası bağımsız). */
    val matchKey: String by lazy { SimilarOffers.key(this) }
    val searchKey: String by lazy { TurkishText.fold(listOfNotNull(brand, name, variant).joinToString(" ")) }
    val displayTitle: String get() = if (brand != null && !name.startsWith(brand, ignoreCase = true)) "$brand $name" else name
}

data class Category(val id: String, val name: String)

class FeedException(msg: String) : Exception(msg)

data class Feed(
    val schemaVersion: Int, val generatedAt: String?, val dataMode: String, val notice: String?,
    val methodLabels: Map<String, String>, val categories: List<Category>, val stores: List<Store>,
    val catalogs: List<Catalog>, val offers: List<Offer>, val skipped: Int,
    val sponsored: List<Sponsored> = emptyList(), val adsActive: Boolean = false, val affiliateActive: Boolean = false,
    val dailyDeals: DailyDeals? = null, val announcements: List<Announcement> = emptyList(),
    val ads: AdsConfig = AdsConfig(),
) {
    /** Günün İndirimleri ürünleri (feed sırasıyla; artık listede olmayanlar atlanır). */
    val dealOffers: List<Offer> by lazy { dailyDeals?.offerIds?.mapNotNull { offer(it) } ?: emptyList() }
    val isDemo: Boolean get() = dataMode != "real"
    fun store(id: String): Store? = stores.firstOrNull { it.id == id }
    fun catalog(id: String?): Catalog? = if (id == null) null else catalogs.firstOrNull { it.id == id }
    fun offer(id: String): Offer? = offers.firstOrNull { it.id == id }
    fun categoryName(id: String): String = categories.firstOrNull { it.id == id }?.name ?: "Diğer"
    fun methodLabel(m: String): String = methodLabels[m] ?: m

    companion object {
        /** 1: eski akış, 2: + Günün İndirimleri, duyurular, PDF ve yayın zamanı (geriye uyumlu, eklemeli). */
        val SUPPORTED_SCHEMAS = setOf(1, 2)

        /** Feed'i ayrıştırır. Gerçek veriyle demo veri ASLA karıştırılmaz: akış "real" ise "real" olmayan kayıt atlanır. */
        @Suppress("UNCHECKED_CAST")
        fun parse(text: String): Feed {
            val root = Json.parse(text) as? Map<String, Any?> ?: throw FeedException("Akış biçimi geçersiz")
            val schema = root.int("schema_version")
            if (schema !in SUPPORTED_SCHEMAS) throw FeedException("Desteklenmeyen akış sürümü: $schema")
            val mode = root.str("data_mode") ?: throw FeedException("data_mode alanı yok")
            val labels = (root.obj("method_labels") ?: emptyMap()).mapValues { it.value.toString() }
            val cats = root.list("categories").mapNotNull { it as? Map<String, Any?> }
                .mapNotNull { m -> m.str("id")?.let { Category(it, m.str("name") ?: it) } }
            val stores = root.list("stores").mapNotNull { it as? Map<String, Any?> }.mapNotNull { m ->
                Store(m.str("id") ?: return@mapNotNull null, m.str("name") ?: "?", m.str("official_url") ?: "",
                    m.str("catalog_url") ?: m.str("official_url") ?: "", m.str("status") ?: "never_run",
                    m.str("status_message"), m.str("last_checked_at"), m.str("last_success_at"),
                    m.str("last_error"), m.str("legal_note"), m.str("image_policy") ?: "official_cdn",
                    Store.parseColor(m.str("color")), m.str("short"), m.str("kind") ?: "market",
                    if (m.containsKey("sort")) m.int("sort") else 99,
                    if (m.containsKey("chain_rank")) m.int("chain_rank") else 99, m.str("brochure_url"))
            }.sortedWith(compareBy({ it.sort }, { it.name }))
            val catalogs = root.list("catalogs").mapNotNull { it as? Map<String, Any?> }.mapNotNull { m ->
                Catalog(m.str("id") ?: return@mapNotNull null, m.str("store_id") ?: return@mapNotNull null,
                    m.str("title") ?: "Katalog", m.str("kind") ?: "aktuel", m.str("valid_from"), m.str("valid_to"),
                    m.str("official_url") ?: return@mapNotNull null, m.str("source_method") ?: "",
                    m.str("observed_at"), m.int("offer_count"), m.str("cover_url"),
                    m.list("pages").mapNotNull { p ->
                        val pm = p as? Map<String, Any?> ?: return@mapNotNull null
                        val u = pm.str("url") ?: return@mapNotNull null
                        if (!u.startsWith("https://")) return@mapNotNull null
                        CatalogPage(u, pm.str("thumb_url")?.takeIf { it.startsWith("https://") }, pm.str("caption"),
                            pm.str("link"), pm.bool("verified"))
                    }, m.str("pdf_url")?.takeIf { it.startsWith("https://") }, m.str("first_seen_at"),
                    m.str("last_checked_at"), m.str("region"), m.str("group"))
            }
            var skipped = 0
            val offers = root.list("offers").mapNotNull { it as? Map<String, Any?> }.mapNotNull { m ->
                val price = m.dbl("price")
                val itemMode = m.str("data_mode") ?: mode
                if (price == null || price <= 0.0 || itemMode != mode || m.str("source_url") == null) {
                    skipped++
                    return@mapNotNull null
                }
                val hist = m.list("price_history").mapNotNull { h ->
                    val hm = h as? Map<String, Any?> ?: return@mapNotNull null
                    val p = hm.dbl("price") ?: return@mapNotNull null
                    PricePoint(p, hm.str("observed_at") ?: return@mapNotNull null)
                }
                val old = m.dbl("store_declared_old_price")?.takeIf { it > price }
                Offer(m.str("id") ?: return@mapNotNull null, m.str("store_id") ?: return@mapNotNull null,
                    m.str("catalog_id"), m.str("name") ?: return@mapNotNull null, m.str("brand"), m.str("variant"),
                    m.str("category") ?: "diger", m.str("category_source") ?: "keyword_rule", price,
                    m.str("price_text"), old, m.str("condition"), m.str("channel"), m.str("product_url"),
                    m.str("valid_from"), m.str("valid_to"), m.str("observed_at"), m.str("source_url")!!,
                    m.str("source_method") ?: "", m.str("verification") ?: "", itemMode, hist,
                    m.str("image_url")?.takeIf { it.startsWith("https://") },
                    m.str("image_large_url")?.takeIf { it.startsWith("https://") },
                    m.str("image_source"), m.str("image_status"),
                    m.str("image_page_url")?.takeIf { it.startsWith("https://") }, m.str("image_checked_at"),
                    m.dbl("page_price_conflict")?.takeIf { it > 0.0 && it != price }, m.str("page_size_text"))
            }
            val money = root.obj("monetization")
            val sponsored = (money?.list("sponsored") ?: emptyList()).mapNotNull { x ->
                val sm = x as? Map<String, Any?> ?: return@mapNotNull null
                Sponsored(sm.str("type") ?: return@mapNotNull null, sm.str("id") ?: return@mapNotNull null,
                    sm.str("label") ?: "Sponsorlu")
            }
            val dd = root.obj("daily_deals")?.let { d ->
                DailyDeals(d.str("date"), d.str("title") ?: "Günün İndirimleri",
                    d.str("note") ?: "Fiyatlar ve stok durumu değişebilir.",
                    d.list("offer_ids").mapNotNull { it as? String })
            }
            val anns = root.list("announcements").mapNotNull { it as? Map<String, Any?> }.mapNotNull { m ->
                Announcement(m.str("id") ?: return@mapNotNull null, m.str("created_at"), m.str("deliver_at"),
                    m.str("title") ?: "", m.str("body") ?: "", m.list("catalog_ids").mapNotNull { it as? String },
                    m.list("store_ids").mapNotNull { it as? String })
            }
            val ac = root.obj("app_config")?.obj("ads")
            val ads = if (ac == null) AdsConfig() else AdsConfig(ac.bool("enabled"), ac.bool("home_banner"),
                ac.bool("list_banner"), if (ac.containsKey("native_every")) ac.int("native_every").coerceIn(0, 50) else 0,
                if (ac.containsKey("test_mode")) ac.bool("test_mode") else true)
            return Feed(schema, root.str("generated_at"), mode, root.str("notice"), labels, cats, stores,
                catalogs, offers, skipped, sponsored, money?.obj("ads")?.bool("active") ?: false,
                money?.obj("affiliate")?.bool("active") ?: false, dd, anns, ads)
        }
    }
}
