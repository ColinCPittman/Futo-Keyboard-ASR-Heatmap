# Pluto Keyboard 0.5.0

The first public release. Pluto Keyboard is a private, offline Android keyboard based on [FUTO Keyboard](https://github.com/futo-org/android-keyboard), with voice typing that shows words as you speak, optional cleanup of the transcript, and a keyboard that learns where your thumbs really land. It has no internet permission, so nothing you type or say leaves your phone.

Pluto is an independent project and is not affiliated with or endorsed by FUTO.

## What's new

- **Streaming voice typing (English).** Words appear while you speak, with no length limit, using Moonshine. Whisper is still used for other languages.
- **Punctuation and capital letters fix (optional).** Repairs sentences broken up by pauses.
- **Smart corrections (optional).** Fixes words that sound alike but do not fit ("bulls" to "bowls" in a talk about soup) and fixes names. Optional extras: numbers as digits, 31 voice commands ("new line", "scratch that") in a list you can edit, removing filler words, fixing self-corrections, quoted speech, and emoji by name ("laughing emoji" → 😂, "thumbs up emoji" → 👍). Filler words, self-corrections, quoted speech and emoji names each have their own page, where you can see every built-in entry, switch any of them off, add your own, or turn off the whole built-in list. It never rephrases what you said: it only swaps words for ones that sound alike.
- **Cancel button.** Throws away a dictation without typing anything.
- **Adaptive typing heatmap.** Learns where you tap each key, including from your corrections, and adjusts the key tap areas. Only per-key statistics are stored, never your text. Viewable and resettable in settings.
- **Smart corrections for typing (optional).** Fixes typos that are real words, like "salt an pepper", once the words around them make the mistake clear, even while you keep typing. Tap the undo button above the keyboard to change a fix back, or "+N" to review them all. The list of slips it looks for is searchable, and you can switch entries off or add your own. Uses the Smart corrections model.
- **Always use middle suggestion.** SwiftKey-style: space always commits the middle suggestion.
- **Correct words before punctuation** can be switched off, so ! ? and other symbols keep the word as you typed it.
- **Password-like words are not learned**, with a "Looks like a password" notice and a Learn anyway button.
- **Pluto themes.** Pluto Classic (the default), Gilded Horizon, Ember Ridge, Horizon and Frost, plus **Follow system light/dark mode**, which switches between a day theme and a night theme of your choice.

Full list: [README](https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/blob/main/README.md#why-pluto).

## Which files to download

**The app (everyone):**

| File | What it is |
|---|---|
| `pluto-keyboard-0.5.0.apk` | The keyboard. Requires Android 7.0+ on a 64-bit ARM (arm64) phone. |

**Models (only for voice typing; download them to your phone and import them without unzipping):**

**1. A voice model: pick one.** This is what turns your speech into text. English only; other languages keep using FUTO's Whisper models.

| File | Size | Pick it when |
|---|---|---|
| `pluto-voice-moonshine-small-en.zip` | about 124 MB | **Recommended for most phones.** A good balance of accuracy and speed. |
| `pluto-voice-moonshine-medium-en.zip` | about 239 MB | You have a fast, recent phone and want the fewest misheard words. |
| `pluto-voice-moonshine-tiny-en.zip` | about 39 MB | Your phone is older or slow, or Small feels laggy. Fastest, but misses more words. |

**2. Punctuation (optional, recommended).** Adds periods, commas and capital letters after you stop speaking, and fixes sentences broken up by pauses. Pick one.

| File | Size | Pick it when |
|---|---|---|
| `pluto-punctuation-en.zip` | about 67 MB | **Recommended** if you dictate in English. |
| `pluto-punctuation-multilingual.zip` | about 164 MB | You dictate in other languages too (with Whisper). |

**3. Smart corrections (optional).** Fixes words that sound alike but don't fit the sentence, and adds the extras: numbers as digits, voice commands, filler words, self-corrections, quoted speech and emoji by name. Smart corrections for typing uses it too.

| File | Size | Pick it when |
|---|---|---|
| `pluto-smart-corrections-en.zip` | about 470 MB | You dictate in English and want cleaner text, or want Smart corrections for typing. Needs an ARMv8.2 or newer processor; the app tells you if yours isn't. |

`SHA256SUMS.txt` lists a checksum for every file, if you want to verify a download.

Not sure? Get **Moonshine Small** and **English punctuation**. Add Smart corrections later if you want it.

## Install

- **With Obtainium (recommended):** add `https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap` in [Obtainium](https://github.com/ImranR98/Obtainium) and it will install the app and notify you of updates.
- **By hand:** download the APK above, open it, and allow the install when Android asks. Pluto installs alongside FUTO Keyboard.

Then open Pluto Keyboard and follow the steps to enable it.

## Set up voice typing

1. Download the model files you want to your phone (do not unzip them).
2. In Pluto Keyboard's settings, open **Languages & Models**, then your language.
3. In the **Voice Input Model**, **Punctuation Model** or **Smart Corrections** row, choose Import and pick the matching `.zip`.
4. Select the Moonshine model in the Voice Input Model list, then switch on the punctuation and Smart corrections options in the Voice Input settings if you imported those.

## Coming from FUTO Keyboard

In FUTO Keyboard: **Settings → Miscellaneous → Export configuration**. In Pluto Keyboard: **Miscellaneous → Import configuration**. This brings over your settings, learned words and personal dictionary. Import replaces Pluto's current settings, so do it first.

## Known limitations

- Moonshine voice typing and Smart corrections (for voice and typing) are English only. Other languages use Whisper.
- arm64 phones only. Smart corrections needs a processor with ARMv8.2 or newer.
- Voice typing speed depends on your phone; I tested on a MediaTek Dimensity 7050. If it feels slow, try Moonshine Tiny.
- Not on Google Play or F-Droid, because the FUTO Source First License is not an open-source license.

## Reporting problems

Please open an issue: <https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/issues>

## License

[FUTO Source First License 1.1](https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/blob/main/LICENSE.md). FUTO's payment option remains in the app and supports FUTO. Third-party notices: [THIRD_PARTY_NOTICES.md](https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/blob/main/THIRD_PARTY_NOTICES.md).
