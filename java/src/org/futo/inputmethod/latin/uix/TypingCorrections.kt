package org.futo.inputmethod.latin.uix

import android.content.Context
import android.text.SpannableString
import android.text.Spanned
import android.text.style.SuggestionSpan
import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.futo.inputmethod.engine.IMEHelper
import org.futo.inputmethod.event.Event
import org.futo.inputmethod.latin.common.Constants
import org.futo.inputmethod.latin.inputlogic.InputLogic
import org.futo.inputmethod.latin.settings.Settings
import org.futo.voiceinput.shared.context.TypingConfusions
import org.futo.voiceinput.shared.context.TypingCorrectionConfig
import org.futo.voiceinput.shared.context.TypingCorrector
import java.util.Locale

/** Smart corrections for typing (Text prediction settings): off by default. */
val TYPING_SMART_CORRECTIONS = SettingsKey(
    key = booleanPreferencesKey("typing_smart_corrections"),
    default = false
)

/** Where the strip shows the typing fix chip: on the right (next to the pinned actions) or the left. */
val TYPING_FIX_CHIP_ON_RIGHT = SettingsKey(
    key = booleanPreferencesKey("typing_fix_chip_on_right"),
    default = true
)

/**
 * Your changes to the typing corrections list: your own pairs, built-in pairs you switched off and
 * words that are never changed (org.futo.voiceinput.shared.context.TypingPairEdits); empty = no changes.
 */
val TYPING_PAIRS_LIST = SettingsKey(
    key = stringPreferencesKey("typing_pairs_list"),
    default = ""
)

/** Whether the built-in typing pairs are used; off: only your own are (your list is kept). */
val TYPING_PAIRS_BUILT_IN = SettingsKey(
    key = booleanPreferencesKey("typing_pairs_built_in"),
    default = true
)

/**
 * A fix Smart corrections for typing made in the current text field: [original] at [start] was
 * replaced by [replacement]. [before] / [after] are the characters around it when it was made,
 * so a revert can tell whether the text there was edited since ([stale]).
 */
data class TypingFix(
    val id: Long,
    val start: Int,
    val original: String,
    val replacement: String,
    val before: String,
    val after: String,
    val stale: Boolean = false,
) {
    val end: Int get() = start + replacement.length

    /**
     * Whether [now] (the text from [before]'s start up to the cursor) still shows this fix as it
     * was made: the text after it may have been cut short by the cursor, but not edited.
     */
    fun matches(now: String): Boolean {
        val expected = before + replacement + after
        return now.startsWith(expected) ||
            (now.length < expected.length && now.length >= before.length + replacement.length && expected.startsWith(now))
    }
}

/** [fixes] after a change of length [delta] at [at]: the fixes after it moved. */
fun shiftFixes(fixes: List<TypingFix>, at: Int, delta: Int): List<TypingFix> =
    if (delta == 0) fixes else fixes.map { if (it.start > at) it.copy(start = it.start + delta) else it }

/** What the suggestion strip shows: the fixes of the current text field (newest last). */
object TypingFixes {
    val fixes = mutableStateOf<List<TypingFix>>(emptyList())

    /** The chip gives way while a word is being typed. */
    val hiddenWhileTyping = mutableStateOf(false)

    /** The review list is open. */
    val listOpen = mutableStateOf(false)

    internal var controller: TypingCorrections? = null

    fun revert(fix: TypingFix) {
        controller?.revert(fix)
    }

    /** Marks fixes whose surroundings changed (when the list opens). */
    fun refreshStale() {
        controller?.refreshStale()
    }
}

/**
 * Reverted swaps make the same swap more careful next time: per swap ("an>and") a count, and per
 * swap in a context (a hash of the words before and after) another. Never the text itself.
 */
class TypingRevertMemory(private val get: (String) -> Int, private val put: (Map<String, Int>) -> Unit) {
    constructor(context: Context) : this(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE))

    private constructor(prefs: android.content.SharedPreferences) : this(
        { prefs.getInt(it, 0) },
        { m -> prefs.edit().apply { m.forEach { (k, v) -> putInt(k, v) } }.apply() },
    )

    private fun ctx(prev: String, next: String) = Integer.toHexString("$prev|$next".hashCode())

    fun recordRevert(from: String, to: String, prev: String, next: String) {
        val pair = "$from>$to"
        val inContext = "$pair@${ctx(prev, next)}"
        put(mapOf(pair to get(pair) + 1, inContext to get(inContext) + 1))
    }

    /** Extra log-prob margin for this swap here. */
    fun caution(from: String, to: String, prev: String, next: String): Double {
        val pair = "$from>$to"
        return minOf(PAIR_STEP * get(pair) + CONTEXT_STEP * get("$pair@${ctx(prev, next)}"), MAX)
    }

    companion object {
        const val PAIR_STEP = 1.5
        const val CONTEXT_STEP = 4.0
        const val MAX = 15.0
        const val PREFS = "typing_reverts"
    }
}

/**
 * Smart corrections for typing in one text field: after a sentence ends (or a few words after a
 * word that may be a slip), the sentence is re-checked in the background with the context model
 * ([ContextModels.checkTyping]); a fix is applied behind the cursor, with an auto-correction
 * [SuggestionSpan] (an underline offering the original, where the app supports it), and listed
 * in [TypingFixes] for the strip's chip and review list until the field closes.
 */
class TypingCorrections(private val helper: IMEHelper, private val inputLogic: InputLogic) {
    private val context = helper.context
    private val memory = TypingRevertMemory(context)
    private var open = false

    /** The field's settings are read when typing: when a field opens they may be the last field's. */
    private val fieldOk: Boolean
        get() {
            if (!open) return false
            val settings = Settings.getInstance().current
            val attrs = settings.mInputAttributes
            if (settings.mLocale.language != locale.language) modelOk = null
            locale = settings.mLocale
            return !attrs.mNoLearning && !attrs.mIsPasswordField && attrs.mShouldShowSuggestions &&
                !attrs.mInputTypeNoAutoCorrect && !attrs.mIsUriField && !attrs.mIsEmailField
        }
    private var modelOk: Boolean? = null

    /**
     * Read on every event: right after the keyboard starts, settings may not be loaded yet when
     * the first field opens. Whether a model is installed is looked up once per field.
     */
    private val enabled: Boolean
        get() {
            if (!fieldOk || !context.getSetting(TYPING_SMART_CORRECTIONS)) return false
            val ok = modelOk ?: ContextModels.typingAvailable(context, locale).also {
                modelOk = it
                Log.i(TAG, "Smart corrections for typing: ${if (it) "on" else "no model for ${locale.language}"}")
            }
            return ok
        }
    private var locale: Locale = Locale.ENGLISH
    private var running: Job? = null
    private var again = false

    /** A fix found while a word was being typed: applied when the word ends. */
    private var waiting: (() -> Unit)? = null
    private var nextId = 1L

    /** Starts of words the user changed by hand (picked from the strip, deleted into): never touched. */
    private val handTouched = HashSet<Int>()

    /** Sentence starts that already got a fix (one per sentence), and checks already made. */
    private val fixedSentences = HashSet<Int>()
    private val checked = HashSet<String>()

    fun onStartInput() {
        running?.cancel()
        waiting = null
        TypingFixes.controller = this
        TypingFixes.fixes.value = emptyList()
        TypingFixes.listOpen.value = false
        TypingFixes.hiddenWhileTyping.value = false
        handTouched.clear()
        fixedSentences.clear()
        checked.clear()
        open = true
        modelOk = null
    }

    fun onFinishInput() {
        running?.cancel()
        waiting = null
        // the fixes stay; only the list of them goes
        TypingFixes.fixes.value = emptyList()
        TypingFixes.listOpen.value = false
        if (modelOk == true) ContextModels.releaseTypingSoon()
        open = false
    }

    private val conn get() = inputLogic.mConnection

    fun afterEvent(event: Event) {
        if (!enabled) return
        val cursor = conn.expectedSelectionStart
        when {
            event.eventType == Event.EVENT_TYPE_SUGGESTION_PICKED -> wordStartBefore(cursor)?.let { handTouched.add(it) }
            event.mKeyCode == Constants.CODE_DELETE || event.mCodePoint == Constants.CODE_DELETE ->
                wordStartBefore(cursor)?.let { handTouched.add(it) }
        }
        TypingFixes.hiddenWhileTyping.value = inputLogic.mWordComposer.isComposingWord
        schedule()
    }

    fun onSelectionChanged(newSelStart: Int) {
        if (!enabled) return
        // the app cleared the field (sent): the fixes are kept, the list goes
        if (newSelStart == 0 && TypingFixes.fixes.value.isNotEmpty() &&
            conn.getTextBeforeCursor(1, 0).isNullOrEmpty() && conn.getTextAfterCursor(1, 0).isNullOrEmpty()) {
            TypingFixes.fixes.value = emptyList()
            TypingFixes.listOpen.value = false
            fixedSentences.clear()
            handTouched.clear()
            checked.clear()
        }
        TypingFixes.hiddenWhileTyping.value = inputLogic.mWordComposer.isComposingWord
    }

    private fun wordStartBefore(cursor: Int): Int? {
        val before = conn.getTextBeforeCursor(64, 0)?.toString() ?: return null
        var i = before.length
        while (i > 0 && before[i - 1].isWhitespace()) i--
        val end = i
        while (i > 0 && (before[i - 1].isLetter() || before[i - 1] == '\'')) i--
        return if (i < end) cursor - before.length + i else null
    }

    private val sentenceEndAtCursor = Regex("[.?!][\"')]*\\s*$")

    /**
     * Checks sentences as words end, while typing goes on: a running check is never cancelled by
     * typing (it just runs again afterwards), and a fix found in the middle of a word waits for
     * the word to end.
     */
    private fun schedule() {
        if (inputLogic.mWordComposer.isComposingWord || conn.hasSelection()) return
        waiting?.let { waiting = null; it() }
        if (running?.isActive == true) {
            again = true
            return
        }
        val cursor = conn.expectedSelectionStart
        val before = conn.getTextBeforeCursor(TEXT_CHARS, 0)?.toString() ?: return
        if (before.isEmpty() || !(before.last().isWhitespace() || before.last() in ".?!,;:")) return
        val base = cursor - before.length
        val sentences = TypingCorrector.sentences(before)
        val last = sentences.lastOrNull() ?: return
        val lastFinished = sentenceEndAtCursor.containsMatchIn(before.substring(last.first))
        // every finished sentence in reach that wasn't checked yet, oldest first, then the
        // unfinished one (early check)
        val targets = sentences.mapIndexedNotNull { i, range ->
            val finished = i < sentences.size - 1 || lastFinished
            if (base + range.first in fixedSentences) return@mapIndexedNotNull null
            // the first one may be cut off by the window
            if (i == 0 && base > 0) return@mapIndexedNotNull null
            val to = if (finished) range.last + 1 else before.trimEnd().length
            val region = before.substring(range.first, to)
            if (!finished && !hasEarlyCandidate(region)) return@mapIndexedNotNull null
            val key = "${base + range.first}:${region.hashCode()}:$finished"
            if (key in checked) null else Triple(range.first, to, finished) to key
        }
        if (targets.isEmpty()) return
        running = helper.lifecycleScope.launch {
            for ((target, key) in targets) {
                val (from, to, finished) = target
                val fix = withContext(Dispatchers.Default) {
                    ContextModels.checkTyping(
                        context, locale, before, from, to, finished,
                        skip = { base + it in handTouched },
                        caution = { a, b, c, d -> memory.caution(a, b, c, d) },
                    )
                }
                checked.add(key)
                if (fix != null) {
                    val applyIt = { apply(base, before.substring(0, to), fix, base + from) }
                    if (inputLogic.mWordComposer.isComposingWord || conn.hasSelection()) waiting = applyIt else applyIt()
                    // the text moved: look again from the new text
                    again = true
                    break
                }
            }
            running = null
            if (again) {
                again = false
                schedule()
            }
        }
    }

    /**
     * Whether some word of an unfinished sentence that may be a slip has just got enough words
     * after it to be checked early (a few more words get it checked again; the sentence end
     * checks the rest).
     */
    private fun hasEarlyCandidate(region: String): Boolean {
        val words = Regex("[A-Za-z]+(?:'[A-Za-z]+)?").findAll(region).map { it.value }.toList()
        val n = TypingCorrectionConfig().earlyWords
        return words.indices.any { i ->
            val after = words.size - 1 - i
            after in n..n + EARLY_RECHECKS && TypingConfusions.builtIn.candidates(words[i]).isNotEmpty()
        }
    }

    private fun apply(base: Int, checkedText: String, fix: TypingCorrector.Fix, sentenceStart: Int) {
        if (inputLogic.mWordComposer.isComposingWord || conn.hasSelection()) return
        // the text up to the end of the checked part must be unchanged
        val cursor = conn.expectedSelectionStart
        val now = conn.getTextBeforeCursor(cursor - base, 0)?.toString() ?: return
        if (cursor - now.length != base || !now.startsWith(checkedText.trimEnd())) return
        val start = base + fix.start
        val text = SpannableString(fix.replacement)
        try {
            text.setSpan(
                SuggestionSpan(context, locale, arrayOf(fix.original), SuggestionSpan.FLAG_AUTO_CORRECTION, null),
                0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        } catch (e: Exception) {
            // not supported here: commit it plain
        }
        if (!conn.replaceTextBeforeCursor(start, start + fix.original.length, text)) return
        fixedSentences.add(sentenceStart)
        val after = checkedText.substring(fix.end, minOf(checkedText.length, fix.end + CONTEXT_CHARS))
        val beforeCtx = checkedText.substring(maxOf(0, fix.start - CONTEXT_CHARS), fix.start)
        TypingFixes.fixes.value = TypingFixes.fixes.value + TypingFix(nextId++, start, fix.original, fix.replacement, beforeCtx, after)
        // later fixes and hand-touched words after this one moved
        shiftAfter(start, fix.replacement.length - fix.original.length)
    }

    private fun shiftAfter(at: Int, delta: Int) {
        if (delta == 0) return
        val touched = handTouched.map { if (it > at) it + delta else it }
        handTouched.clear(); handTouched.addAll(touched)
        TypingFixes.fixes.value = shiftFixes(TypingFixes.fixes.value, at, delta)
    }

    /** Whether the text around [fix] is still what it was right after the fix. */
    private fun intact(fix: TypingFix): Boolean {
        val cursor = conn.expectedSelectionStart
        if (conn.hasSelection() || fix.end > cursor) return false
        val from = fix.start - fix.before.length
        val now = conn.getTextBeforeCursor(cursor - from, 0)?.toString() ?: return false
        if (cursor - now.length != from) return false
        return fix.matches(now)
    }

    fun refreshStale() {
        if (inputLogic.mWordComposer.isComposingWord) return
        TypingFixes.fixes.value = TypingFixes.fixes.value.map { it.copy(stale = !intact(it)) }
    }

    fun revert(fix: TypingFix) {
        if (inputLogic.mWordComposer.isComposingWord) {
            // the composing word would be committed by the edit: finish it first
            inputLogic.mConnection.finishComposingText()
            inputLogic.mWordComposer.reset(true)
        }
        if (!intact(fix)) {
            TypingFixes.fixes.value = TypingFixes.fixes.value.map { if (it.id == fix.id) it.copy(stale = true) else it }
            return
        }
        if (!conn.replaceTextBeforeCursor(fix.start, fix.end, fix.original)) return
        val prev = Regex("([A-Za-z']+)\\W*$").find(fix.before)?.groupValues?.get(1)?.lowercase() ?: ""
        val next = Regex("^\\W*([A-Za-z']+)").find(fix.after)?.groupValues?.get(1)?.lowercase() ?: ""
        memory.recordRevert(fix.original.lowercase(), fix.replacement.lowercase(), prev, next)
        handTouched.add(fix.start)
        TypingFixes.fixes.value = TypingFixes.fixes.value.filter { it.id != fix.id }
        shiftAfter(fix.start, fix.original.length - fix.replacement.length)
        if (TypingFixes.fixes.value.isEmpty()) TypingFixes.listOpen.value = false
    }

    companion object {
        private const val TAG = "TypingCorrections"

        /** Characters before the cursor a check reads. */
        const val TEXT_CHARS = 600

        /** An unfinished sentence is checked again for this many more words after a possible slip. */
        const val EARLY_RECHECKS = 3

        /** Characters kept around a fix to recognize it later. */
        const val CONTEXT_CHARS = 20
    }
}
