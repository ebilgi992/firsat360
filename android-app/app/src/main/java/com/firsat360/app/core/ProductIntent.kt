package com.firsat360.app.core

/** Ürün türü, kelime parçasından ve reyon adından bağımsız doğrulanır. */
object ProductIntent {
    fun wantsDrinkableMilk(terms: List<String>): Boolean = terms.any { Assistant.stem(it) == "sut" } &&
        terms.none { it in setOf("toz", "tozu", "recel", "receli", "vucut", "yuz", "krem", "kremi", "temizleme", "mama", "mamasi", "devam", "bebek", "pompa", "pompasi", "kopurtucu", "kopurtucusu") }

    fun drinkableMilk(o: Offer): Boolean {
        val words = TrNorm.tokens(o.displayTitle + " " + (o.variant ?: ""))
        if ("sut" !in words) return false
        val excluded = setOf("tozu", "toz", "krem", "kremi", "losyon", "losyonu", "sabun", "sabunu",
            "temizleme", "vucut", "dus", "bebek", "devam", "mama", "mamasi", "sutlac", "tatli", "tatlisi",
            "recel", "receli", "biskuvi", "biskuvisi", "cikolata", "cikolatasi", "cicegi", "pompa", "pompasi", "kopurtucu", "kopurtucusu")
        if (words.any { it in excluded }) return false
        return o.category !in setOf("kisisel-bakim", "temizlik", "ev-yasam", "elektronik")
    }
}
