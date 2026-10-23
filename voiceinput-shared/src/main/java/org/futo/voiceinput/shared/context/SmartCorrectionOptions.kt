package org.futo.voiceinput.shared.context

/**
 * Which kinds of edits the context stage may make (the switches on the "Smart corrections"
 * settings page). With every new option off ([soundAlike] and [questions] on) the stage behaves
 * exactly as before these options existed.
 */
data class SmartCorrectionOptions(
    /** Sound-alike word fixes ("bulls" -> "bowls") and word-boundary fixes ("in to" -> "into"). */
    val soundAlike: Boolean = true,
    /** Names from the personal dictionary that sound alike, and capitalization (case-only) fixes. */
    val names: Boolean = true,
    /** Missed question marks. */
    val questions: Boolean = true,
    /** Spoken numbers, times and amounts as digits ("twenty five dollars" -> "$25"). */
    val numbers: Boolean = false,
    /** Spoken commands at pauses: "new line", "new paragraph", "comma", "period", "scratch that", ... */
    val commands: Boolean = false,
    /** Fillers ("um", "uh") and accidental repeats ("I I think"). */
    val fillers: Boolean = false,
    /** Self-corrections: "the toaster oven, I mean, air fryer" -> "the air fryer". */
    val selfCorrections: Boolean = false,
    /** Quoted speech: `I was like um what are you doing` -> `I was like "Um, what are you doing?"`. */
    val quotes: Boolean = false,
    /** Emoji by name: "thumbs up emoji" -> 👍. */
    val emoji: Boolean = false,
    /** The spoken commands used when [commands] is on (the "Voice commands" page). */
    val commandList: List<VoiceCommand> = VoiceCommands.DEFAULTS,
    /** The phrases that mark a self-correction when [selfCorrections] is on (the "Fix self-corrections" page). */
    val correctionPhrases: List<String> = CorrectionPhrases.DEFAULTS,
    /** The switched-on filler words when [fillers] is on (the "Filler words" page). */
    val fillerPhrases: List<String> = FillerWords.DEFAULTS,
    /** The switched-on phrases that introduce quoted speech when [quotes] is on (the "Quoted speech" page). */
    val quoteTriggerPhrases: List<String> = QuoteTriggers.DEFAULTS,
    /** Name words -> emoji when [emoji] is on (the "Emoji" page; see [EmojiNames.nameMap]). */
    val emojiNames: Map<List<String>, String> = emptyMap(),
) {
    /** [quoteTriggerPhrases] as words. */
    val quoteTriggers: List<List<String>> by lazy { QuoteTriggers.triggers(quoteTriggerPhrases) }

    /** [fillerPhrases] as the rules use them. */
    val fillerList: List<FillerWords.Filler> by lazy { FillerWords.fillers(fillerPhrases) }

    /** [correctionPhrases] as markers for the rules and the guard. */
    val correctionMarkers: List<CorrectionMarker> by lazy { CorrectionPhrases.markers(correctionPhrases) }

    /** Whether any edit kind is enabled. */
    val any: Boolean get() = soundAlike || names || questions || numbers || commands || fillers || selfCorrections || quotes || emoji

    companion object {
        /** The behaviour before the options existed: sound-alike words and question marks only. */
        val LEGACY = SmartCorrectionOptions(names = false)

        val ALL = SmartCorrectionOptions(true, true, true, true, true, true, true, true, true)

        val NONE = SmartCorrectionOptions(false, false, false, false, false, false, false, false, false)

        /** Parses "soundalike,names,questions,numbers,commands,fillers,selfcorrections", "default[,...]" or "all" (tools/tests). */
        fun parse(list: String): SmartCorrectionOptions {
            val s = list.split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
            if ("all" in s) return ALL
            // "default" = the default set, plus whatever else is listed
            val d = "default" in s
            return SmartCorrectionOptions(
                soundAlike = d || "soundalike" in s,
                names = d || "names" in s,
                questions = d || "questions" in s,
                numbers = "numbers" in s,
                commands = "commands" in s,
                fillers = "fillers" in s,
                selfCorrections = "selfcorrections" in s,
                quotes = "quotes" in s,
                emoji = "emoji" in s,
            )
        }
    }
}

/**
 * The user's own words (the app passes Android's personal dictionary and the keyboard's learned
 * words through this; tests use lists). Capitalized entries are treated as names.
 */
fun interface PersonalDictionary {
    /** Words the user added to the personal dictionary (trusted as they are). */
    fun words(): Collection<String>

    /**
     * Capitalized words the keyboard learned from the user's typing, most frequent first. They
     * may include ordinary words the user once capitalized, so common lowercase words are
     * dropped before they are used as names.
     */
    fun learned(): Collection<String> = emptyList()

    companion object {
        val EMPTY = PersonalDictionary { emptyList() }

        fun of(words: Collection<String>, learned: Collection<String> = emptyList()) = object : PersonalDictionary {
            override fun words() = words
            override fun learned() = learned
        }
    }
}
