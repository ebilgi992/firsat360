package com.firsat360.app.core

/** Aynı açılış önceliği bildirim, arama ve katalog kartlarında korunur. */
object CatalogOpening {
    enum class Target { PAGES, PDF, PRODUCTS, WEB }
    fun target(c: Catalog, images: Boolean): Target = when {
        images && pages(c).isNotEmpty() -> Target.PAGES
        pdf(c) != null -> Target.PDF
        c.offerCount > 0 -> Target.PRODUCTS
        else -> Target.WEB
    }
    fun pdf(c: Catalog): String? = c.pdfUrl ?: c.officialUrl.takeIf { it.startsWith("https://") && it.substringBefore('?').substringBefore('#').endsWith(".pdf", true) }
    fun pages(c: Catalog): List<CatalogPage> {
        if (c.pages.isNotEmpty()) return c.pages
        val direct = c.officialUrl.takeIf { it.startsWith("https://") && Regex("\\.(?:png|jpe?g|webp)(?:[?#]|$)", RegexOption.IGNORE_CASE).containsMatchIn(it) }
        val image = direct ?: c.coverUrl?.takeIf { c.isPoster && it.startsWith("https://") } ?: return emptyList()
        return listOf(CatalogPage(image, null, "Resmî afiş", c.officialUrl, false))
    }
    fun sameSource(a: String, b: String): Boolean = a.substringBefore('#').trimEnd('/') == b.substringBefore('#').trimEnd('/')
}
