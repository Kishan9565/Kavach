package com.kishan.kavach.core.common

class JsonException(msg: String) : Exception(msg)

/**
 * Tiny, dependency-free, forgiving JSON reader/writer (pure Kotlin, so it is unit-testable on the JVM).
 * Tolerates what small models emit: single quotes, trailing commas, text after the value.
 * Numbers become Long (integral) or Double. Objects -> Map<String, Any?>, arrays -> List<Any?>.
 */
object Json {
    fun parse(s: String): Any? {
        val p = Reader(s)
        p.ws()
        return p.value(0)
    }

    fun parseOrNull(s: String): Any? = try { parse(s) } catch (e: JsonException) { null }

    @Suppress("UNCHECKED_CAST")
    fun parseObject(s: String): Map<String, Any?>? = parseOrNull(s) as? Map<String, Any?>

    fun stringify(v: Any?): String = StringBuilder().also { write(it, v) }.toString()

    private fun write(sb: StringBuilder, v: Any?) {
        when (v) {
            null -> sb.append("null")
            is Boolean, is Int, is Long -> sb.append(v.toString())
            is Double -> sb.append(if (v % 1.0 == 0.0 && !v.isInfinite()) v.toLong().toString() else v.toString())
            is Number -> sb.append(v.toString())
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, x) in v) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString()); sb.append(':'); write(sb, x)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (x in v) { if (!first) sb.append(','); first = false; write(sb, x) }
                sb.append(']')
            }
            else -> quote(sb, v.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when (c) {
            '"' -> sb.append("\\\"")
            '\\' -> sb.append("\\\\")
            '\n' -> sb.append("\\n")
            '\r' -> sb.append("\\r")
            '\t' -> sb.append("\\t")
            else -> if (c < ' ') sb.append("\\u%04x".format(c.code)) else sb.append(c)
        }
        sb.append('"')
    }

    private class Reader(val s: String) {
        var i = 0

        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }

        fun value(depth: Int): Any? {
            if (depth > 32) throw JsonException("too deep")
            ws()
            if (i >= s.length) throw JsonException("unexpected end")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"', '\'' -> str(c)
                else -> literal()
            }
        }

        private fun obj(depth: Int): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++ // {
            while (true) {
                ws()
                if (i >= s.length) throw JsonException("unterminated object")
                if (s[i] == '}') { i++; return m }
                if (s[i] == ',') { i++; continue }
                val key = if (s[i] == '"' || s[i] == '\'') str(s[i]) else bareKey()
                ws()
                if (i >= s.length || s[i] != ':') throw JsonException("expected ':' at $i")
                i++
                m[key] = value(depth + 1)
            }
        }

        private fun bareKey(): String {
            val st = i
            while (i < s.length && (s[i].isLetterOrDigit() || s[i] == '_')) i++
            if (st == i) throw JsonException("bad key at $i")
            return s.substring(st, i)
        }

        private fun arr(depth: Int): List<Any?> {
            val l = ArrayList<Any?>()
            i++ // [
            while (true) {
                ws()
                if (i >= s.length) throw JsonException("unterminated array")
                if (s[i] == ']') { i++; return l }
                if (s[i] == ',') { i++; continue }
                l.add(value(depth + 1))
            }
        }

        private fun str(q: Char): String {
            i++ // opening quote
            val sb = StringBuilder()
            while (i < s.length) {
                val c = s[i++]
                if (c == q) return sb.toString()
                if (c == '\\' && i < s.length) {
                    when (val e = s[i++]) {
                        'n' -> sb.append('\n'); 'r' -> sb.append('\r'); 't' -> sb.append('\t')
                        'b' -> sb.append('\b'); 'f' -> sb.append('\u000c')
                        'u' -> {
                            if (i + 4 > s.length) throw JsonException("bad unicode escape")
                            sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: '?'); i += 4
                        }
                        else -> sb.append(e)
                    }
                } else sb.append(c)
            }
            throw JsonException("unterminated string")
        }

        private fun literal(): Any? {
            val st = i
            while (i < s.length && s[i] !in ",}]" && !s[i].isWhitespace()) i++
            val t = s.substring(st, i)
            return when {
                t == "true" -> true
                t == "false" -> false
                t == "null" -> null
                t.toLongOrNull() != null -> t.toLong()
                t.toDoubleOrNull() != null -> t.toDouble()
                else -> throw JsonException("bad literal '$t' at $st")
            }
        }
    }
}
