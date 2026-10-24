package org.futo.inputmethod.latin.uix

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.booleanPreferencesKey
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale

/**
 * Developer setting "Save dictations for tuning" keeps the newest [MAX_ENTRIES] dictations, each
 * as the voice model heard it (with its pause segments), the text after every voice-input stage
 * and the Smart corrections options, so they can be copied to a computer and replayed through the
 * test tools (`tools/context-harness`). Off by default, and nothing leaves the phone by itself.
 *
 * After the user edits an inserted dictation, its entry also gets a "corrected" field
 * ([DictationFollowUp]).
 *
 * The file is `Android/data/<package>/files/tuning/dictations.jsonl` on the phone's shared
 * storage, one JSON object per line (`adb pull` reads it).
 */
object DictationRecorder {
    private const val TAG = "DictationRecorder"
    private const val MAX_ENTRIES = 200

    val SAVE_DICTATIONS = SettingsKey(booleanPreferencesKey("pluto_save_dictations_for_tuning"), false)

    fun isEnabled(context: Context): Boolean = context.getSetting(SAVE_DICTATIONS)

    /** Where the dictations are kept, or null when shared storage is unavailable. */
    fun file(context: Context): File? = context.getExternalFilesDir("tuning")?.let { File(it, "dictations.jsonl") }

    private val notes = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** A number (milliseconds, a length) about the dictation being processed, saved in its "timing" field. Numbers only. */
    fun note(context: Context, key: String, value: Long) {
        if (isEnabled(context)) notes[key] = value
    }

    /** Time of the newest saved dictation (0 if none this run). See [DictationFollowUp]. */
    @Volatile
    var lastTime = 0L
        private set

    /** Adds the text the user later corrected the dictation saved at [time] to, as its "corrected" field. */
    @Synchronized
    fun addCorrection(context: Context, time: Long, corrected: String) {
        try {
            val f = file(context)?.takeIf { it.exists() } ?: return
            var changed = false
            val lines = f.readLines().filter { it.isNotBlank() }.map { line ->
                val o = JSONObject(line)
                if (o.optLong("time") == time && !o.has("corrected")) {
                    changed = true
                    o.put("corrected", corrected).toString()
                } else line
            }
            if (changed) f.writeText(lines.joinToString("\n", postfix = "\n"))
        } catch (e: Exception) {
            Log.w(TAG, "Could not save a correction: ${e.javaClass.simpleName}")
        }
    }

    /** Saves one dictation. Never throws, because recording must not break voice input. */
    @Synchronized
    fun record(context: Context, locale: Locale, raw: String, segments: List<String>, stages: List<Pair<String, String>>, stageMs: List<Pair<String, Long>> = emptyList()) {
        try {
            val f = file(context) ?: return
            val options = ContextModels.options(context)
            val now = System.currentTimeMillis()
            val entry = JSONObject()
                .put("time", now)
                .put("locale", locale.toLanguageTag())
                .put("raw", raw)
                .put("segments", JSONArray(segments))
                .put("stages", JSONObject().apply { for ((name, text) in stages) put(name, text) })
                .put("final", stages.lastOrNull()?.second ?: raw)
                .put("options", options.toString())
                .put("timing", JSONObject().apply {
                    for ((name, ms) in stageMs) put(name + "_ms", ms)
                    for ((k, v) in notes) put(k, v)
                })
            val lines = if (f.exists()) f.readLines().filter { it.isNotBlank() } else emptyList()
            f.parentFile?.mkdirs()
            f.writeText((lines.takeLast(MAX_ENTRIES - 1) + entry.toString()).joinToString("\n", postfix = "\n"))
            lastTime = now
            notes.clear()
        } catch (e: Exception) {
            // never log the dictated text itself
            Log.w(TAG, "Could not save a dictation: ${e.javaClass.simpleName}")
        }
    }

    /** Number of saved dictations. */
    fun count(context: Context): Int = try {
        file(context)?.takeIf { it.exists() }?.readLines()?.count { it.isNotBlank() } ?: 0
    } catch (e: Exception) {
        0
    }

    /** Deletes every saved dictation. */
    @Synchronized
    fun deleteAll(context: Context) {
        try {
            file(context)?.delete()
        } catch (e: Exception) {
            Log.w(TAG, "Could not delete saved dictations: ${e.javaClass.simpleName}")
        }
    }
}
