package org.futo.voiceinput.context

import org.futo.inputmethod.latin.uix.TypingFix
import org.futo.inputmethod.latin.uix.TypingRevertMemory
import org.futo.inputmethod.latin.uix.shiftFixes
import org.futo.voiceinput.shared.context.ContextLm
import org.futo.voiceinput.shared.context.TypingConfusions
import org.futo.voiceinput.shared.context.TypingCorrectionConfig
import org.futo.voiceinput.shared.context.TypingCorrector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Word-level fake LM: log P(word) depends on the previous word only (bigram table, else -3). */
private class BigramLm(private val bigrams: Map<Pair<String, String>, Float>) : ContextLm {
    private val ids = LinkedHashMap<String, Int>()
    private val pieces = ArrayList<String>()
    private var seq0 = IntArray(0)

    private fun id(p: String) = ids.getOrPut(p) { pieces.add(p); pieces.size - 1 }

    override fun tokenize(text: String, addSpecial: Boolean): IntArray {
        if (addSpecial) return intArrayOf(id("<prompt>"))
        return Regex("\\s*[A-Za-z']+|\\s*[^\\sA-Za-z']").findAll(text).map { id(it.value) }.toList().toIntArray()
    }

    override fun pieceBytes(token: Int): ByteArray = pieces[token].toByteArray()
    override fun ensureCapacity(nTokens: Int) {}

    private fun word(t: Int) = pieces[t].trim().lowercase()
    private fun lp(tokens: IntArray, i: Int, token: Int): Float =
        bigrams[(if (i > 0) word(tokens[i - 1]) else "") to word(token)] ?: -3f

    override fun evalBase(tokens: IntArray, keep: Int, needFrom: Int, wantOff: IntArray, wantIds: IntArray): ContextLm.BaseEval {
        var lcp = 0
        while (lcp < seq0.size && lcp < tokens.size && seq0[lcp] == tokens[lcp]) lcp++
        val first = maxOf(minOf(keep, lcp, tokens.size - 1) + 1, needFrom)
        val out = ArrayList<Float>()
        for (i in first until tokens.size) {
            out.add(lp(tokens, i, tokens[i]))
            for (q in wantOff[i] until wantOff[i + 1]) out.add(lp(tokens, i, wantIds[q]))
        }
        seq0 = tokens
        return ContextLm.BaseEval(first, out.toFloatArray())
    }

    override fun evalVariants(variants: List<IntArray>, starts: IntArray): List<FloatArray> =
        variants.mapIndexed { v, t -> FloatArray(t.size - starts[v]) { lp(t, starts[v] + it, t[starts[v] + it]) } }

    override fun close() {}
}

class TypingCorrectorTest {
    private val confusions = TypingConfusions.parse(sequenceOf("an and any", "or of for", "is it"))

    // "salt and pepper" is likely, "an pepper" is not; "an apple" is likely, "and apple" is not
    private val lm = BigramLm(
        mapOf(
            ("salt" to "and") to -1f, ("and" to "pepper") to -1f, ("salt" to "any") to -6f, ("any" to "pepper") to -8f, ("salt" to "an") to -4f, ("an" to "pepper") to -12f,
            ("an" to "apple") to -1f, ("and" to "apple") to -10f, ("ate" to "an") to -1f, ("ate" to "and") to -6f,
            ("king" to "of") to -1f, ("of" to "the") to -1f, ("king" to "or") to -3f, ("or" to "the") to -9f,
        )
    )

    private fun corrector(caution: (String, String, String, String) -> Double = { _, _, _, _ -> 0.0 }) =
        TypingCorrector(lm, confusions, TypingCorrectionConfig(margin = 5.0, openingMargin = 4.0), caution)

    @Test
    fun builtInListHasTheCommonSlips() {
        val list = TypingConfusions.builtIn
        assertTrue(list.size > 50)
        assertTrue("and" in list.candidates("an"))
        assertTrue("of" in list.candidates("or"))
        assertTrue("from" in list.candidates("form"))
        assertTrue("than" in list.candidates("then"))
        assertTrue("and" in list.candidates("An"))
        // a slip works both ways when both words are common
        assertTrue("trick" in list.candidates("truck"))
        assertTrue("truck" in list.candidates("trick"))
    }

    @Test
    fun fixesASlipTheNextWordGivesAway() {
        val text = "I bought some salt an pepper shakers."
        val fix = corrector().check(text).fix
        assertNotNull(fix)
        assertEquals("an", fix!!.original)
        assertEquals("and", fix.replacement)
        assertEquals(text.indexOf(" an ") + 1, fix.start)
        assertTrue(fix.tailGain > 0)
    }

    @Test
    fun fixesMistForMust() {
        val bigrams = mapOf(
            ("we" to "must") to -1f, ("must" to ".") to -1f, ("we" to "mist") to -9f, ("mist" to ".") to -9f,
        )
        val c = TypingCorrector(
            BigramLm(bigrams),
            TypingConfusions.builtIn,
            TypingCorrectionConfig(margin = 6.5),
        )
        assertTrue("must" in TypingConfusions.builtIn.candidates("mist"))
        val fix = c.check("I think we do what we mist.").fix
        assertNotNull(fix)
        assertEquals("must", fix!!.replacement)
    }

    @Test
    fun comparativeThenNeedsLessEvidence() {
        val bigrams = mapOf(("taller" to "than") to -3f, ("taller" to "then") to -6.5f, ("than" to "her") to -1f, ("then" to "her") to -3f)
        val conf = TypingConfusions.parse(sequenceOf("then than"))
        val text = "She is taller then her brother."
        // gain 3.5 + 2: under the plain 6.5 margin, enough after the comparative relief
        assertNull(TypingCorrector(BigramLm(bigrams), conf, TypingCorrectionConfig(margin = 6.5, comparativeRelief = 0.0)).check(text).fix)
        assertEquals("than", TypingCorrector(BigramLm(bigrams), conf, TypingCorrectionConfig(margin = 6.5)).check(text).fix?.replacement)
        // a comma after it: "taller then, ..." is left alone
        assertNull(TypingCorrector(BigramLm(bigrams), conf, TypingCorrectionConfig(margin = 6.5)).check("She was taller then, her brother.").fix)
    }

    @Test
    fun keepsLiteralUses() {
        assertNull(corrector().check("I ate an apple today.").fix)
    }

    @Test
    fun onlyTheCheckedSentenceChanges() {
        val text = "Some salt an pepper. I ate an apple."
        val second = text.indexOf("I ate")
        assertNull(corrector().check(text, second, text.length).fix)
        assertNotNull(corrector().check(text, 0, second).fix)
    }

    @Test
    fun handCorrectedWordsAreSkipped() {
        val text = "Some salt an pepper shakers."
        val at = text.indexOf(" an ") + 1
        assertNull(corrector().check(text, skip = { it == at }).fix)
    }

    @Test
    fun revertedSwapsNeedMoreEvidence() {
        val text = "Some salt an pepper shakers."
        assertNull(corrector { a, b, _, _ -> if (a == "an" && b == "and") 20.0 else 0.0 }.check(text).fix)
    }

    @Test
    fun atMostOneFixPerSentenceTheBestOne() {
        val text = "He is king or the salt an pepper shakers."
        val fix = corrector().check(text).fix!!
        // or->of gains 2 + 8 = 10, an->and gains 3 + 11 = 14
        assertEquals("and", fix.replacement)
    }

    @Test
    fun earlyCheckNeedsWordsAfterTheSlip() {
        assertNull(corrector().check("Some salt an pepper", finished = false).fix)
        assertNotNull(corrector().check("Some salt an pepper shakers for", finished = false).fix)
    }

    @Test
    fun shortSentencesAreNotChecked() {
        assertNull(TypingCorrector(lm, confusions, TypingCorrectionConfig(margin = 5.0, minWords = 4)).check("salt an pepper.").fix)
    }

    @Test
    fun matchesCase() {
        assertEquals("And", TypingCorrector.matchCase("An", "and"))
        assertEquals("AND", TypingCorrector.matchCase("AN", "and"))
        assertEquals("and", TypingCorrector.matchCase("an", "and"))
    }

    @Test
    fun splitsSentences() {
        val text = "First one. Second one? \"Third!\" Last"
        val s = TypingCorrector.sentences(text).map { text.substring(it.first, it.last + 1).trim() }
        assertEquals(listOf("First one.", "Second one?", "\"Third!\"", "Last"), s)
    }
}

class TypingFixTest {
    private val fix = TypingFix(1, 20, "an", "and", before = "some new salt ", after = " pepper shakers")

    @Test
    fun matchesUnchangedText() {
        assertTrue(fix.matches("some new salt and pepper shakers and more"))
        // the cursor is right after the fix: the text after it is cut short
        assertTrue(fix.matches("some new salt and pep"))
    }

    @Test
    fun editedTextIsStale() {
        assertFalse(fix.matches("some new salt an pepper shakers"))
        assertFalse(fix.matches("some old salt and pepper shakers"))
        assertFalse(fix.matches("some new salt and peppers"))
    }

    @Test
    fun laterFixesShift() {
        val other = fix.copy(id = 2, start = 40)
        val shifted = shiftFixes(listOf(fix, other), at = 20, delta = -1)
        assertEquals(20, shifted[0].start)
        assertEquals(39, shifted[1].start)
    }

    @Test
    fun revertMemoryIsPerSwapAndContext() {
        val store = HashMap<String, Int>()
        val memory = TypingRevertMemory({ store[it] ?: 0 }, { store.putAll(it) })
        assertEquals(0.0, memory.caution("an", "and", "salt", "pepper"), 0.0)
        memory.recordRevert("an", "and", "salt", "pepper")
        val here = memory.caution("an", "and", "salt", "pepper")
        val elsewhere = memory.caution("an", "and", "milk", "bread")
        assertEquals(TypingRevertMemory.PAIR_STEP + TypingRevertMemory.CONTEXT_STEP, here, 1e-9)
        assertEquals(TypingRevertMemory.PAIR_STEP, elsewhere, 1e-9)
        assertEquals(0.0, memory.caution("or", "of", "salt", "pepper"), 0.0)
        // no words are stored, only the swap and a hash of its neighbours
        assertTrue(store.keys.none { "salt" in it || "pepper" in it })
    }
}
