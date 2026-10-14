package com.firsat360.app.core

/**
 * Küçük, bağımlılıksız JSON ayrıştırıcı. Android'e özgü sınıf kullanmaz; JVM'de test edilebilir.
 * Sonuç tipleri: Map<String, Any?>, List<Any?>, String, Double, Long, Boolean, null.
 */
class JsonException(msg: String) : Exception(msg)

object Json {
    fun parse(text: String): Any? {
        val p = Parser(text)
        p.ws()
        val v = p.value()
        p.ws()
        if (p.i != text.length) throw JsonException("Fazla karakter: ${p.i}")
        return v
    }

    /** Güvenlik: aşırı iç içe (yığın taşırma) ve kesik girdiler JsonException ile reddedilir; uygulama çökmez. */
    const val MAX_DEPTH = 64

    private class Parser(val s: String) {
        var i = 0
        var depth = 0

        fun at(): Char = if (i < s.length) s[i] else throw JsonException("Beklenmeyen son: $i")

        fun ws() {
            while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++
        }

        fun value(): Any? {
            if (i >= s.length) throw JsonException("Beklenmeyen son")
            return when (s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> num()
            }
        }

        fun lit(word: String, v: Any?): Any? {
            if (!s.startsWith(word, i)) throw JsonException("Geçersiz değer: $i")
            i += word.length
            return v
        }

        fun obj(): Map<String, Any?> {
            if (++depth > MAX_DEPTH) throw JsonException("Çok derin iç içe yapı")
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (at() == '}') { i++; depth--; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                if (at() != ':') throw JsonException("':' bekleniyordu: $i")
                i++
                ws()
                m[k] = value()
                ws()
                when (at()) {
                    ',' -> i++
                    '}' -> { i++; depth--; return m }
                    else -> throw JsonException("',' veya '}' bekleniyordu: $i")
                }
            }
        }

        fun arr(): List<Any?> {
            if (++depth > MAX_DEPTH) throw JsonException("Çok derin iç içe yapı")
            val l = ArrayList<Any?>()
            i++
            ws()
            if (at() == ']') { i++; depth--; return l }
            while (true) {
                ws()
                l.add(value())
                ws()
                when (at()) {
                    ',' -> i++
                    ']' -> { i++; depth--; return l }
                    else -> throw JsonException("',' veya ']' bekleniyordu: $i")
                }
            }
        }

        fun str(): String {
            if (at() != '"') throw JsonException("'\"' bekleniyordu: $i")
            i++
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) throw JsonException("Kapanmamış metin")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        val e = at(); i++
                        when (e) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000C'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) throw JsonException("Kesik \\u kaçışı")
                                val code = s.substring(i, i + 4).toIntOrNull(16) ?: throw JsonException("Geçersiz \\u kaçışı")
                                sb.append(code.toChar()); i += 4
                            }
                            else -> throw JsonException("Geçersiz kaçış: $e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        fun num(): Any {
            val start = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] == '.' || s[i] == 'e' || s[i] == 'E' || s[i] == '+' || s[i] == '-')) i++
            val t = s.substring(start, i)
            if (t.isEmpty() || t == "-") throw JsonException("Geçersiz sayı: $start")
            return try {
                if (t.contains('.') || t.contains('e') || t.contains('E')) t.toDouble() else t.toLong()
            } catch (e: NumberFormatException) { throw JsonException("Geçersiz sayı: $start") }
        }
    }
}

@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.obj(k: String): Map<String, Any?>? = this[k] as? Map<String, Any?>
@Suppress("UNCHECKED_CAST")
fun Map<String, Any?>.list(k: String): List<Any?> = (this[k] as? List<Any?>) ?: emptyList()
fun Map<String, Any?>.str(k: String): String? = (this[k] as? String)?.takeIf { it.isNotBlank() }
fun Map<String, Any?>.dbl(k: String): Double? = when (val v = this[k]) { is Double -> v; is Long -> v.toDouble(); else -> null }
fun Map<String, Any?>.int(k: String): Int = when (val v = this[k]) { is Long -> v.toInt(); is Double -> v.toInt(); else -> 0 }
fun Map<String, Any?>.bool(k: String): Boolean = this[k] as? Boolean ?: false

/** Bağımlılıksız JSON yazıcı (kullanıcı verisini cihazda saklamak için). */
object JsonWriter {
    fun write(v: Any?): String = StringBuilder().also { w(it, v) }.toString()

    private fun w(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is String -> str(sb, v)
            is Boolean -> sb.append(if (v) "true" else "false")
            is Int, is Long -> sb.append(v.toString())
            is Double -> if (v.isNaN() || v.isInfinite()) sb.append("null") else sb.append(v.toString())
            is Float -> w(sb, v.toDouble())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, value) in v) {
                    if (!first) sb.append(',')
                    first = false
                    str(sb, k.toString()); sb.append(':'); w(sb, value)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; w(sb, x) }
                sb.append(']')
            }
            else -> str(sb, v.toString())
        }
    }

    private fun str(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> if (ch < ' ') sb.append("\\u").append(Integer.toHexString(ch.code).padStart(4, '0')) else sb.append(ch)
            }
        }
        sb.append('"')
    }
}
