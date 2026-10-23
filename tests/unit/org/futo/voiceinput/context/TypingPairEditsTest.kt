package org.futo.voiceinput.context

import org.futo.voiceinput.shared.context.OwnTypingPair
import org.futo.voiceinput.shared.context.TypingConfusions
import org.futo.voiceinput.shared.context.TypingPairEdits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingPairEditsTest {
    private val builtIn = TypingConfusions.parse(sequenceOf("form for from", "an and", "teh the"))

    @Test fun builtInHasContractionSlips() {
        val b = TypingConfusions.builtIn
        assertTrue("it's" in b.candidates("kit's"))
        assertTrue("can't" in b.candidates("cam't"))
        assertTrue("we're" in b.candidates("wee're"))
        assertTrue("that's" in b.candidates("hat's"))
        // real contractions are not slips of each other
        assertTrue(b.candidates("it's").isEmpty())
    }

    @Test fun searchMergesBuiltInAndOwnPairs() {
        val b = TypingConfusions.parse(sequenceOf("ant any want anti", "form for from"))
        val e = TypingPairEdits(own = listOf(OwnTypingPair("ant", listOf("ent")), OwnTypingPair("antz", listOf("ants"))), offBuiltIn = listOf("ant" to "any"))
        val rows = TypingPairEdits.search(b, e, true, "ant", 30)
        assertEquals(listOf("ant", "antz"), rows.map { it.typed })
        assertEquals(listOf("any" to false, "want" to true, "anti" to true), rows[0].builtIn)
        assertEquals(listOf(0), rows[0].own)
        assertEquals(listOf(1), rows[1].own)
        assertTrue(rows[1].builtIn.isEmpty())
    }

    @Test fun searchWithBuiltInOffShowsOnlyOwnPairs() {
        val b = TypingConfusions.parse(sequenceOf("ant any want anti"))
        val e = TypingPairEdits(own = listOf(OwnTypingPair("ant", listOf("ent"))))
        val rows = TypingPairEdits.search(b, e, false, "ant", 30)
        assertEquals(1, rows.size)
        assertTrue(rows[0].builtIn.isEmpty())
        assertEquals(listOf(0), rows[0].own)
        assertTrue(TypingPairEdits.search(b, e, false, "form", 30).isEmpty())
    }

    @Test fun searchFindsOwnOnlyWord() {
        val b = TypingConfusions.parse(sequenceOf("ant any"))
        val e = TypingPairEdits(own = listOf(OwnTypingPair("zorp", listOf("zorb"))))
        assertEquals(listOf("zorp"), TypingPairEdits.search(b, e, true, "zor", 30).map { it.typed })
    }

    @Test fun noEditsKeepsBuiltIn() {
        assertSame(builtIn, TypingPairEdits.merge(builtIn, TypingPairEdits()))
    }

    @Test fun switchedOffPairIsRemoved() {
        val m = TypingPairEdits.merge(builtIn, TypingPairEdits(offBuiltIn = listOf("form" to "for")))
        assertEquals(listOf("from"), m.candidates("form"))
        val all = TypingPairEdits.merge(builtIn, TypingPairEdits(offBuiltIn = listOf("an" to "and")))
        assertTrue(all.candidates("an").isEmpty())
    }

    @Test fun ownPairsAddToBuiltIn() {
        val e = TypingPairEdits(own = listOf(OwnTypingPair("form", listOf("fort", "from"))))
        assertEquals(listOf("for", "from", "fort"), TypingPairEdits.merge(builtIn, e).candidates("form"))
    }

    @Test fun disabledOwnPairIsIgnored() {
        val e = TypingPairEdits(own = listOf(OwnTypingPair("hte", listOf("the"), enabled = false)))
        assertTrue(TypingPairEdits.merge(builtIn, e).candidates("hte").isEmpty())
    }

    @Test fun builtInOffUsesOnlyOwnPairs() {
        val e = TypingPairEdits(own = listOf(OwnTypingPair("hte", listOf("the"))))
        val m = TypingPairEdits.merge(builtIn, e, builtInOn = false)
        assertEquals(listOf("the"), m.candidates("hte"))
        assertTrue(m.candidates("form").isEmpty())
    }

    @Test fun neverChangeWordsAreDropped() {
        val e = TypingPairEdits(own = listOf(OwnTypingPair("an", listOf("on"))), never = listOf("an"))
        val m = TypingPairEdits.merge(builtIn, e)
        assertTrue(m.candidates("an").isEmpty())
        assertEquals(listOf("for", "from"), m.candidates("form"))
    }

    @Test fun serializeRoundTrip() {
        val e = TypingPairEdits(
            own = listOf(OwnTypingPair("hte", listOf("the", "he")), OwnTypingPair("lets", listOf("let's"), enabled = false)),
            offBuiltIn = listOf("form" to "for"),
            never = listOf("teh"),
        )
        assertEquals(e, TypingPairEdits.parse(e.serialize()))
    }

    @Test fun parseIgnoresBadLines() {
        val e = TypingPairEdits.parse("\n=\nfoo\n12\tab\n-x\n=Teh\nTeh\tThe, THEE\n")
        assertEquals(listOf("teh"), e.never)
        assertEquals(listOf(OwnTypingPair("teh", listOf("the", "thee"))), e.own)
    }
}
