package org.futo.voiceinput.context

import org.futo.voiceinput.shared.context.DictationAlignment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Locating an inserted dictation again to see what it was corrected to (synthetic text only). */
class DictationAlignmentTest {
    private val b = "We talked about the garden plan. "
    private val a = " The next topic was the budget for spring."
    private val ins = "Mr Lowe will bring the ladder tomorrow."

    private fun run(now: String, before: String = b, after: String = a, atStart: Boolean = false, atEnd: Boolean = false, i: String = ins) =
        DictationAlignment.corrected(i, before.takeLast(30), after.take(30), now.substringBefore('|'), now.substringAfter('|'), atStart, atEnd)

    @Test
    fun unchangedIsNull() {
        assertNull(run(b + ins + "|" + a))
    }

    @Test
    fun correctedWordIsFound() {
        assertEquals("Mr Lowe will bring the ladders tomorrow.", run(b + "Mr Lowe will bring the ladders tomorrow.|" + a))
    }

    @Test
    fun cursorMayBeInsideTheSpan() {
        assertEquals("Mr Lowe will bring the ladders tomorrow.", run(b + "Mr Lowe will bring| the ladders tomorrow." + a))
    }

    @Test
    fun editedAnchorOrMissingTextIsSkipped() {
        assertNull(run("Something else entirely is here.|"))
        assertNull(run("We talked about the garden plan! Mr Lowe will bring the ladders tomorrow.|" + a))
    }

    @Test
    fun repeatedAnchorIsAmbiguous() {
        assertNull(run(b + "Mr Lowe will bring the ladders tomorrow." + a + b + "x|" + a))
    }

    @Test
    fun rewriteSharingLittleIsSkipped() {
        assertNull(run(b + "Completely different sentence about lunch plans and parking.|" + a))
    }

    @Test
    fun fieldEndNeedsExactEnd() {
        val now = b + "Mr Lowe will bring the ladders tomorrow.|"
        assertNull(run(now, after = ""))
        assertEquals("Mr Lowe will bring the ladders tomorrow.", run(now, after = "", atEnd = true))
        // text typed after the dictation is not a correction
        assertNull(run(b + ins + " Then we left.|", after = "", atEnd = true))
    }

    @Test
    fun fieldStartNeedsExactStart() {
        assertEquals("Mr Lowe will bring the ladders tomorrow.", run("Mr Lowe will bring the ladders tomorrow.|" + a, before = "", atStart = true))
        assertNull(run("Mr Lowe will bring the ladders tomorrow.|" + a, before = ""))
    }
}
