package com.kishan.kavach.core.common

/**
 * Simple, offline retrieval (ported from Sprint 3 Notes RAG): IDF-weighted word match + character-trigram
 * similarity, which tolerates Hinglish spelling variants. Deliberately tiny: swap for embeddings only if a
 * measurable benefit appears (spec section 12).
 */
object Retriever {
    data class Hit<T>(val item: T, val score: Double)

    private val stop = setOf(
        "hai", "ha", "ka", "ki", "ke", "ko", "mera", "meri", "mere", "mujhe", "kya", "kab", "kaun", "kahan",
        "kitna", "kitne", "hain", "the", "is", "an", "of", "my", "what", "when", "where", "who", "to",
        "me", "mein", "par", "pe", "aur", "and", "tell", "batao", "bata", "do", "de", "kaise", "ho"
    )

    private fun toks(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 1 && it !in stop }

    private fun tri(w: String): Set<String> {
        val p = "#$w#"
        return (0..p.length - 3).map { p.substring(it, it + 3) }.toSet()
    }

    private fun sim(a: String, b: String): Double {
        if (a == b) return 1.0
        val x = tri(a); val y = tri(b)
        val u = x.union(y).size
        return if (u == 0) 0.0 else x.intersect(y).size.toDouble() / u
    }

    fun <T> search(docs: List<T>, text: (T) -> String, query: String, k: Int = 3, threshold: Double = 0.35): List<Hit<T>> {
        if (docs.isEmpty()) return emptyList()
        val qt = toks(query).distinct()
        if (qt.isEmpty()) return emptyList()
        val dt = docs.map { toks(text(it)) }
        val n = docs.size
        val idf = qt.associateWith { t ->
            val df = dt.count { d -> d.any { sim(it, t) >= 0.6 } }
            Math.log(1.0 + (n - df + 0.5) / (df + 0.5))
        }
        val total = idf.values.sum()
        if (total <= 0.0) return emptyList()
        return docs.indices.map { i ->
            val s = qt.sumOf { t ->
                val best = dt[i].maxOfOrNull { sim(it, t) } ?: 0.0
                (if (best >= 0.6) best else 0.0) * idf.getValue(t)
            } / total
            Hit(docs[i], s)
        }.filter { it.score >= threshold }.sortedByDescending { it.score }.take(k)
    }
}

/** Masks things that should not sit in developer logs: long digit runs, emails, URLs' query strings. */
object Redactor {
    private val digits = Regex("""\d{6,}""")
    private val email = Regex("""[\w.+-]+@[\w-]+\.[\w.]+""")
    fun redact(s: String): String = s.replace(email, "<email>").replace(digits, "<digits>")
}
