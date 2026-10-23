package org.futo.inputmethod.latin.uix

import android.os.Handler
import android.os.Looper
import android.util.Log
import org.futo.inputmethod.latin.LatinIME
import org.futo.voiceinput.shared.context.DictationAlignment

/**
 * Part of the developer setting "Save dictations for tuning": remembers the last inserted dictation
 * and where it went, and later (when the field is left, the keyboard is hidden, the next dictation
 * starts, or after [DELAY_MS]) reads that span back to record what the user corrected it to
 * ([DictationRecorder.addCorrection]). Nothing is kept or read while the setting is off, and the
 * text itself is never logged.
 */
object DictationFollowUp {
    private const val TAG = "DictationFollowUp"
    private const val DELAY_MS = 3 * 60 * 1000L
    private const val READ_CHARS = 2000

    private class Pending(
        val time: Long, val inserted: String, val anchorBefore: String, val anchorAfter: String,
        val atFieldStart: Boolean, val atFieldEnd: Boolean,
    )

    private var pending: Pending? = null
    private var ime: LatinIME? = null
    private val handler = Handler(Looper.getMainLooper())
    private val timeout = Runnable { check() }

    /** Called after a dictation was inserted; [beforeCursor]/[afterCursor] are the text around it before the insert. */
    fun arm(ime: LatinIME, inserted: String, beforeCursor: CharSequence?, afterCursor: CharSequence?) {
        if (!DictationRecorder.isEnabled(ime.applicationContext)) return
        val time = DictationRecorder.lastTime
        if (time == 0L || System.currentTimeMillis() - time > 60_000) return
        check()
        val b = beforeCursor?.toString() ?: return
        val a = afterCursor?.toString() ?: return
        this.ime = ime
        pending = Pending(
            time, inserted, b.takeLast(DictationAlignment.ANCHOR), a.take(DictationAlignment.ANCHOR),
            atFieldStart = b.isEmpty(), atFieldEnd = a.isEmpty(),
        )
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, DELAY_MS)
    }

    /** Reads the remembered dictation back, if any, and records the user's correction of it. */
    fun check() {
        val p = pending ?: return
        val ime = ime
        pending = null
        this.ime = null
        handler.removeCallbacks(timeout)
        try {
            if (ime == null || !DictationRecorder.isEnabled(ime.applicationContext)) return
            val ic = ime.currentInputConnection ?: return
            val before = ic.getTextBeforeCursor(READ_CHARS, 0)?.toString() ?: return
            val after = ic.getTextAfterCursor(READ_CHARS, 0)?.toString() ?: return
            val corrected = DictationAlignment.corrected(
                p.inserted, p.anchorBefore, p.anchorAfter, before, after,
                p.atFieldStart && before.length < READ_CHARS, p.atFieldEnd && after.length < READ_CHARS,
            ) ?: return
            DictationRecorder.addCorrection(ime.applicationContext, p.time, corrected)
        } catch (e: Exception) {
            Log.w(TAG, "Could not read back a dictation: ${e.javaClass.simpleName}")
        }
    }
}
