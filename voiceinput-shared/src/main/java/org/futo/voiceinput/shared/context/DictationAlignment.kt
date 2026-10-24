package org.futo.voiceinput.shared.context

/**
 * Finds again, in the text around the cursor, a dictation that was inserted earlier, to see what
 * the user changed it to (developer setting "Save dictations for tuning"). The inserted text is
 * located by the text that surrounded it when it was inserted ([anchorBefore], [anchorAfter]), never
 * by guessing, so anything ambiguous or too different gives null.
 */
object DictationAlignment {
    /** Characters of the surrounding text kept as anchors. */
    const val ANCHOR = 30
    private const val MIN_ANCHOR = 6

    /**
     * What the span [inserted] was edited to, or null when it is unchanged or cannot be lined up.
     * [before] / [after] are the text before / after the cursor now. [atFieldStart] / [atFieldEnd] say the
     * dictation was inserted at the very start / end of the field (so an empty anchor is exact).
     */
    fun corrected(
        inserted: String, anchorBefore: String, anchorAfter: String,
        before: String, after: String, atFieldStart: Boolean, atFieldEnd: Boolean,
    ): String? {
        val text = before + after
        val ins = inserted.trim()
        if (ins.isEmpty()) return null
        val start: Int = if (anchorBefore.isEmpty()) {
            if (!atFieldStart) return null
            0
        } else {
            if (anchorBefore.length < MIN_ANCHOR) return null
            val i = text.indexOf(anchorBefore)
            if (i < 0 || text.indexOf(anchorBefore, i + 1) >= 0) return null
            i + anchorBefore.length
        }
        val end: Int = if (anchorAfter.isEmpty()) {
            if (!atFieldEnd) return null
            text.length
        } else {
            if (anchorAfter.length < MIN_ANCHOR) return null
            val j = text.indexOf(anchorAfter, start)
            if (j < 0 || text.indexOf(anchorAfter, j + 1) >= 0) return null
            j
        }
        if (end < start) return null
        val now = text.substring(start, end).trim()
        if (now == ins) return null
        // text typed after the dictation at the end of the field is not a correction of it
        if (anchorAfter.isEmpty() && now.startsWith(ins)) return null
        if (now.isEmpty() || now.length > ins.length * 2 + 40) return null
        // a rewrite that shares little with the dictation is not a correction of it
        if (distance(now, ins) > maxOf(now.length, ins.length) * 0.6) return null
        return now
    }

    /** Levenshtein distance. */
    fun distance(a: String, b: String): Int {
        var prev = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            val cur = IntArray(b.length + 1)
            cur[0] = i
            for (j in 1..b.length) {
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1)
            }
            prev = cur
        }
        return prev[b.length]
    }
}
