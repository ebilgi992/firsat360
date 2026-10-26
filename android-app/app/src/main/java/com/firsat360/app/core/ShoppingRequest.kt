package com.firsat360.app.core

import java.math.BigDecimal
import java.math.RoundingMode

/** src/money.ts'deki kuruş önce normalize et yaklaşımının Kotlin uyarlaması.
 * gokhancvs/market-fiyati-mcp, Copyright 2026 Gökhan Çavuş, MIT; LICENSES.md. */
object PriceCents {
    fun of(value: Double): Long {
        require(value.isFinite() && value >= 0 && value <= 9_000_000_000_000.0)
        return BigDecimal(value.toString()).setScale(2, RoundingMode.HALF_UP).movePointRight(2).toLong()
    }
}

data class Pack(val kind: String, val amount: Double) {
    companion object {
        val pattern = Regex("(?<![0-9a-z])(\\d+(?:[.,]\\d+)?)\\s*(?:[x×]\\s*(\\d+(?:[.,]\\d+)?)\\s*)?(kilogram|kiloluk|kilo|kg|gr|gram|g|mililitre|ml|litrelik|litre|lt|l)(?![a-z])", RegexOption.IGNORE_CASE)
        fun parse(text: String?): Pack? {
            val m = pattern.find(TurkishText.lower(text ?: "")) ?: return null
            val a = m.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
            val b = m.groupValues[2].replace(',', '.').toDoubleOrNull() ?: 1.0
            val unit = m.groupValues[3].lowercase()
            val mass = unit in setOf("kilogram", "kiloluk", "kilo", "kg", "gr", "gram", "g")
            val k = if (unit in setOf("ml", "mililitre", "g", "gr", "gram")) 0.001 else 1.0
            return (a * b * k).takeIf { it.isFinite() && it > 0 }?.let { Pack(if (mass) "kg" else "L", it) }
        }
        fun of(o: Offer): Pack? = parse(o.variant) ?: parse(o.name) ?: parse(o.pageSizeText)
    }
}

data class ShoppingRequest(val query: String, val budget: Long? = null, val pack: Pack? = null,
                           val unitSort: Boolean = false, val unitKind: String? = null) {
    fun accepts(o: Offer): Boolean {
        if (!o.price.isFinite() || o.price <= 0 || o.pagePriceConflict != null) return false
        if (budget != null && PriceCents.of(o.price) > budget) return false
        if (pack != null) {
            val actual = Pack.of(o) ?: return false
            if (actual.kind != pack.kind || kotlin.math.abs(actual.amount - pack.amount) > 0.0001) return false
        }
        return true
    }
    companion object {
        private val below = Regex("(\\d+(?:[.,]\\d{1,2})?)\\s*(?:TL|₺|lira)\\s*(?:alt[ıi](?:nda|na)?|aşma(?:yan)?|asma(?:yan)?)", RegexOption.IGNORE_CASE)
        private val maximum = Regex("(?:en fazla|maksimum|bütçem|butcem)\\s*(\\d+(?:[.,]\\d{1,2})?)\\s*(?:TL|₺|lira)", RegexOption.IGNORE_CASE)
        fun parse(raw: String): ShoppingRequest {
            var q = raw.take(400)
            val match = below.find(q) ?: maximum.find(q)
            val budget = match?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()?.let { PriceCents.of(it) }
            if (match != null) q = q.removeRange(match.range)
            val fold = TrNorm.loose(q)
            val unitSort = listOf("birim fiyat", "litre fiyati", "litre basina", "kilogram fiyati", "kilo fiyati", "kg basina").any { fold.contains(it) }
            val unitKind = when {
                fold.contains("litre fiyati") || fold.contains("litre basina") -> "L"
                listOf("kilogram fiyati", "kilo fiyati", "kg basina").any { fold.contains(it) } -> "kg"
                else -> null
            }
            val pack = Pack.parse(q)
            q = TrNorm.loose(Pack.pattern.replace(q, " "))
            q = q.replace(Regex("(?:birim|litre|kilogram|kilo|kg)\\s+(?:fiyat[ıi](?:na)?|basina|başına)(?:\\s+göre|\\s+gore)?", RegexOption.IGNORE_CASE), " ")
            return ShoppingRequest(q, budget, pack, unitSort, unitKind)
        }
    }
}
