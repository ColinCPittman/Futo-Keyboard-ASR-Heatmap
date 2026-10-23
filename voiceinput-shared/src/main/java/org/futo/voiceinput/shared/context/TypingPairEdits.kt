package org.futo.voiceinput.shared.context

/** A typed word with the words you meant by it ("teh" -> "the"): your own pair, switched on or off. */
data class OwnTypingPair(val typed: String, val intended: List<String>, val enabled: Boolean = true)

/**
 * Your changes to the typing corrections list (the "Typing corrections" settings page): your own
 * pairs, built-in pairs you switched off and words that are never changed. Stored one per line:
 * `typed<TAB>intended intended` (a switched-off one ends in a tab and "0"), `-typed<TAB>intended` for
 * a switched-off built-in pair, `=word` for a word that is never changed.
 */
data class TypingPairEdits(
    val own: List<OwnTypingPair> = emptyList(),
    val offBuiltIn: List<Pair<String, String>> = emptyList(),
    val never: List<String> = emptyList(),
) {
    val isEmpty: Boolean get() = own.isEmpty() && offBuiltIn.isEmpty() && never.isEmpty()

    fun serialize(): String = buildString {
        for (o in own) append(o.typed).append('\t').append(o.intended.joinToString(" ")).append(if (o.enabled) "" else "\t0").append('\n')
        for ((t, i) in offBuiltIn) append('-').append(t).append('\t').append(i).append('\n')
        for (w in never) append('=').append(w).append('\n')
    }

    companion object {
        private val WORD = Regex("[a-z]+(?:'[a-z]+)?")

        /** [text] as one lowercase word, or null when it isn't one. */
        fun word(text: String): String? = text.trim().lowercase().takeIf { WORD.matches(it) }

        /** The words of [text] (separated by spaces or commas), lowercase, without repeats or invalid ones. */
        fun words(text: String): List<String> =
            text.split(' ', ',', ';').mapNotNull { word(it) }.distinct()

        fun parse(stored: String): TypingPairEdits {
            val own = ArrayList<OwnTypingPair>()
            val off = ArrayList<Pair<String, String>>()
            val never = ArrayList<String>()
            for (line in stored.lines()) {
                if (line.isBlank()) continue
                val p = line.split('\t')
                when {
                    line.startsWith("=") -> word(line.drop(1))?.let { if (it !in never) never.add(it) }
                    line.startsWith("-") && p.size >= 2 -> {
                        val t = word(p[0].drop(1)); val i = word(p[1])
                        if (t != null && i != null && (t to i) !in off) off.add(t to i)
                    }
                    p.size >= 2 -> {
                        val t = word(p[0]); val i = words(p[1])
                        if (t != null && i.isNotEmpty()) own.add(OwnTypingPair(t, i, enabled = p.getOrNull(2) != "0"))
                    }
                }
            }
            return TypingPairEdits(own, off, never)
        }

        /** A typed word found by [search]: its built-in pairs (with whether each is on) and your own pairs. */
        data class Row(
            val typed: String,
            /** Built-in intended words, in order, each with whether it is switched on. */
            val builtIn: List<Pair<String, Boolean>>,
            /** Indices into [TypingPairEdits.own] of your pairs for this word. */
            val own: List<Int>,
            val never: Boolean,
        )

        /**
         * The typed words starting with [query] from the built-in pairs (unless [builtInOn] is off)
         * and your own pairs, exact match first, at most [limit] + 1 rows.
         */
        fun search(builtIn: TypingConfusions, edits: TypingPairEdits, builtInOn: Boolean, query: String, limit: Int): List<Row> {
            val q = word(query) ?: return emptyList()
            val words = LinkedHashSet<String>()
            if (builtInOn) builtIn.pairs.keys.filterTo(words) { it.startsWith(q) }
            edits.own.map { it.typed }.filterTo(words) { it.startsWith(q) }
            return words.sortedWith(compareBy({ it != q }, { it.length }, { it })).take(limit + 1).map { t ->
                val off = edits.offBuiltIn.filter { it.first == t }.map { it.second }
                Row(
                    t,
                    if (builtInOn) builtIn.candidates(t).map { it to (it !in off) } else emptyList(),
                    edits.own.indices.filter { edits.own[it].typed == t },
                    t in edits.never,
                )
            }
        }

        /**
         * The pairs in use: [builtIn] (unless [builtInOn] is off) without the pairs you switched off,
         * plus your switched-on own pairs, without the words that are never changed.
         */
        fun merge(builtIn: TypingConfusions, edits: TypingPairEdits, builtInOn: Boolean = true): TypingConfusions {
            if (edits.isEmpty && builtInOn) return builtIn
            val out = LinkedHashMap<String, List<String>>()
            if (builtInOn) {
                val off = edits.offBuiltIn.groupBy({ it.first }, { it.second })
                for ((t, c) in builtIn.pairs) {
                    val keep = c.filter { it !in (off[t] ?: emptyList()) }
                    if (keep.isNotEmpty()) out[t] = keep
                }
            }
            for (o in edits.own) {
                if (!o.enabled) continue
                out[o.typed] = ((out[o.typed] ?: emptyList()) + o.intended.filter { it != o.typed }).distinct()
            }
            for (w in edits.never) out.remove(w)
            out.values.removeAll { it.isEmpty() }
            return TypingConfusions(out)
        }
    }
}
