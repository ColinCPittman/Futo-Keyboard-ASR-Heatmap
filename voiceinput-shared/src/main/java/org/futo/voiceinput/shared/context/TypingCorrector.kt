package org.futo.voiceinput.shared.context

/** Thresholds of Smart corrections for typing (tuned on tools/context-bench/testset_typing.jsonl). */
data class TypingCorrectionConfig(
    /** Prompt the typed text is scored after. */
    val prompt: String = "A text message:\n",
    /** A swap must raise the log-prob of the whole text by this much ... */
    val margin: Double = 6.5,
    /** ... plus this for the first word of a sentence (less context to go on). */
    val openingMargin: Double = 3.0,
    /** The words after the swapped one must not get less likely than this (log-prob change). */
    val tailMin: Double = -1.5,
    /** Sentences with fewer words are not checked (too little to go on: "Than what?"). */
    val minWords: Int = 3,
    /** Before its sentence ends, a word is checked once this many words follow it. */
    val earlyWords: Int = 3,
    /**
     * A candidate is scored only if its first token is at most this much less likely than the
     * typed word's where it starts (one pass for all); 0 scores them all.
     */
    val probeDrop: Double = 6.0,
    /** "then" right after a comparative ("taller then her") needs this much less than [margin]. */
    val comparativeRelief: Double = 3.5,
    /** Characters of text before the checked sentence that are scored with it. */
    val leftChars: Int = 300,
)

/**
 * Smart corrections for typing: re-checks a finished sentence for real-word typos
 * ("some new salt an pepper" -> "and") with the context LM, which reads the whole sentence,
 * including the words after the typo that make it obvious.
 *
 * Candidates are one-key-off swaps from [TypingConfusions]; each is scored as the whole text
 * (the sentence after [TypingCorrectionConfig.leftChars] of what came before). At most one
 * swap per sentence is returned: the one that raises the log-prob most, if it beats the margin.
 */
class TypingCorrector(
    lm: ContextLm,
    private val confusions: TypingConfusions = TypingConfusions.builtIn,
    val config: TypingCorrectionConfig = TypingCorrectionConfig(),
    /**
     * Extra margin for a swap the user reverted before: (original, replacement, previous word,
     * next word), all lowercase ("" at an edge) -> log-prob.
     */
    private val caution: (String, String, String, String) -> Double = { _, _, _, _ -> 0.0 },
) {
    private val scorer = LmScorer(lm, config.prompt)

    /** A swap of [original] at [start]..[end] of the checked text for [replacement]. */
    class Fix(
        val start: Int,
        val end: Int,
        val original: String,
        val replacement: String,
        /** Log-prob gain of the whole text. */
        val gain: Double,
        /** Of that, the gain of the words after the swapped one. */
        val tailGain: Double,
    )

    class Result(val fix: Fix?, val trace: List<String>, val timings: List<Double>)

    private val word = Regex("[A-Za-z]+(?:'[A-Za-z]+)?")

    /**
     * Checks the words of [text] in [from]..[to] (the finished sentence; what's before it is
     * context). [skip] tells which word starts must not change (corrected by hand, reverted).
     * While the sentence is not [finished], only words with [TypingCorrectionConfig.earlyWords]
     * words after them are candidates.
     */
    fun check(
        text: String,
        from: Int = 0,
        to: Int = text.length,
        finished: Boolean = true,
        skip: (Int) -> Boolean = { false },
        checkpoint: () -> Unit = {},
    ): Result {
        val trace = ArrayList<String>()
        scorer.timings.clear()
        // the scored text: up to leftChars before the sentence, cut at a word start
        var cut = maxOf(0, from - config.leftChars)
        if (cut > 0) {
            while (cut < from && !text[cut - 1].isWhitespace()) cut++
        }
        val scored = text.substring(cut, to)

        class Cand(val m: MatchResult, val replacement: String, val variant: String)
        val cands = ArrayList<Cand>()
        val words = word.findAll(text, 0).filter { it.range.last < to }.toList()
        if (words.count { it.range.first >= from } < config.minWords) return Result(null, trace, emptyList())
        for ((wi, m) in words.withIndex()) {
            if (m.range.first < from || skip(m.range.first)) continue
            if (!finished && words.size - 1 - wi < config.earlyWords) continue
            for (c in confusions.candidates(m.value)) {
                val rep = matchCase(m.value, c)
                val s = m.range.first - cut
                cands.add(Cand(m, rep, scored.substring(0, s) + rep + scored.substring(s + m.value.length)))
            }
        }
        if (cands.isEmpty()) return Result(null, trace, emptyList())

        checkpoint()
        val groups = cands.groupBy { it.m }.entries.toList()
        val probes = if (config.probeDrop > 0) groups.map { (m, cs) -> (m.range.first - cut) to cs.map { it.replacement } } else emptyList()
        val detail = scorer.detail(scored, emptyList(), probes)
        if (probes.isNotEmpty()) {
            val kept = groups.flatMapIndexed { gi, (_, cs) ->
                val pr = detail.probes.getOrNull(gi)
                if (pr == null || pr.size != cs.size + 1) cs
                else cs.filterIndexed { k, _ -> pr[k] >= pr[cs.size] - config.probeDrop }
            }
            if (kept.size < cands.size) trace.add("probe kept ${kept.size}/${cands.size}")
            cands.retainAll(kept.toSet())
            if (cands.isEmpty()) return Result(null, trace, scorer.timings.toList())
        }
        checkpoint()
        val res = scorer.scoreDetailed(listOf(scored) + cands.map { it.variant })
        val base = res[0]
        var best: Fix? = null
        var bestOver = 0.0
        for ((i, c) in cands.withIndex()) {
            val v = res[i + 1]
            val gain = v.total - base.total
            // the words after the swap: the tokens both texts end with
            var n = 0
            while (n < v.tokens.size && n < base.tokens.size &&
                v.tokens[v.tokens.size - 1 - n] == base.tokens[base.tokens.size - 1 - n]) n++
            var tail = 0.0
            for (k in 1..n) tail += v.lps[v.lps.size - k] - base.lps[base.lps.size - k]
            val idx = words.indexOf(c.m)
            val prev = words.getOrNull(idx - 1)?.takeIf { gapAt(text, it, c.m).none { ch -> ch in ".?!\n" } }
            val next = words.getOrNull(idx + 1)
            val opening = opensSentence(text, c.m.range.first)
            val comparative = c.m.value.equals("then", true) && c.replacement.equals("than", true) &&
                prev != null && isComparative(prev.value) &&
                next != null && gapAt(text, c.m, next).none { ch -> !ch.isWhitespace() }
            val need = config.margin + (if (opening) config.openingMargin else 0.0) -
                (if (comparative) config.comparativeRelief else 0.0) +
                caution(c.m.value.lowercase(), c.replacement.lowercase(), prev?.value?.lowercase() ?: "", next?.value?.lowercase() ?: "")
            val ok = gain >= need && tail >= config.tailMin
            trace.add("${c.m.value}->${c.replacement} gain=%.2f tail=%.2f need=%.2f%s".format(gain, tail, need, if (ok) " OK" else ""))
            if (ok && gain - need > bestOver) {
                bestOver = gain - need
                best = Fix(c.m.range.first, c.m.range.last + 1, c.m.value, c.replacement, gain, tail)
            }
        }
        return Result(best, trace, scorer.timings.toList())
    }

    private fun isComparative(w: String): Boolean {
        val l = w.lowercase()
        return l in COMPARATIVES || (l.length >= 5 && l.endsWith("er"))
    }

    private fun gapAt(text: String, a: MatchResult, b: MatchResult) = text.substring(a.range.last + 1, b.range.first)

    /** Whether the word at [at] opens its sentence. */
    private fun opensSentence(text: String, at: Int): Boolean {
        var i = at - 1
        while (i >= 0 && (text[i] == ' ' || text[i] == '"' || text[i] == '(')) i--
        return i < 0 || text[i] in ".?!\n"
    }

    companion object {
        private val COMPARATIVES = setOf("more", "less", "better", "worse", "rather", "other", "further", "else")

        fun matchCase(src: String, dst: String): String = when {
            src.length > 1 && src.all { !it.isLetter() || it.isUpperCase() } -> dst.uppercase()
            src.first().isUpperCase() -> dst.replaceFirstChar { it.uppercaseChar() }
            else -> dst
        }

        private val sentenceEnd = Regex("[.?!]+[\"')]*(?=\\s|$)|\\n")

        /** Start and end of each sentence of [text] (end after its final mark; the last may have none). */
        fun sentences(text: String): List<IntRange> {
            val out = ArrayList<IntRange>()
            var s = 0
            for (m in sentenceEnd.findAll(text)) {
                val e = m.range.last + 1
                if (text.substring(s, e).any { it.isLetter() }) out.add(s until e)
                s = e
            }
            if (s < text.length && text.substring(s).any { it.isLetter() }) out.add(s until text.length)
            return out
        }
    }
}
