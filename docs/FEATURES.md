# Pluto Keyboard features guide

This guide goes through everything Pluto Keyboard adds on top of [FUTO Keyboard](https://github.com/futo-org/android-keyboard), including what each feature does, how to set it up, how it works under the hood, and how to get the most out of it. For the features Pluto shares with FUTO Keyboard, see FUTO's guide (Settings → Help → FUTO Keyboard guide).

Everything here runs on your phone. Pluto has no internet permission, so nothing you type or say ever leaves the device.

**Contents**

- [Voice typing](#voice-typing)
  - [Live voice typing with Moonshine](#live-voice-typing-with-moonshine)
  - [Fix punctuation](#fix-punctuation)
  - [Smart corrections for voice](#smart-corrections-for-voice)
  - [Sensitivity dials](#sensitivity-dials)
  - [Advanced voice options](#advanced-voice-options)
  - [Saving dictations for tuning](#saving-dictations-for-tuning)
- [Typing](#typing)
  - [Smart corrections for typing](#smart-corrections-for-typing)
  - [The typing corrections list](#the-typing-corrections-list)
  - [Adaptive typing heatmap](#adaptive-typing-heatmap)
  - [Always use middle suggestion](#always-use-middle-suggestion)
  - [Correct words before punctuation](#correct-words-before-punctuation)
- [Privacy](#privacy)
- [Themes](#themes)
- [Feature tips](#feature-tips)
- [Getting the models](#getting-the-models)

---

## Voice typing

Voice input happens in a few short steps. Only the first one is required, and each of the others has its own switch.

1. **Speech recognition** turns your voice into words. English uses Moonshine (live), and other languages use FUTO's Whisper (after you finish).
2. **Fix punctuation** adds periods, commas and capital letters, and repairs sentences that pauses broke apart.
3. **Smart corrections** reads the finished text with a small language model and fixes what only the whole sentence can tell, like misheard words, names, numbers and spoken commands.

Tap the microphone to start. Tap again to finish, or tap **Cancel** to throw the dictation away without typing anything.

### Live voice typing with Moonshine

**What it does.** Words appear while you speak, with no time limit on how long you talk. English only. Other languages keep using Whisper, which types your words after you stop.

**Set it up.** Import a Moonshine voice model under Languages & Models → English → Voice Input Model, then select it. The three sizes trade speed for accuracy.

| Model | Size | Best for |
|---|---|---|
| Small | about 124 MB | Most phones. Recommended. |
| Medium | about 239 MB | Fast, recent phones, for the fewest misheard words. |
| Tiny | about 39 MB | Older or slower phones. Fastest, but misses more words. |

**How it works.** Moonshine runs on ONNX Runtime. A voice activity detector (Silero VAD) splits long dictation into utterances at your pauses, so the model only ever works on a short stretch of audio and dictation can go on indefinitely. After two idle minutes the model is let go, to free up memory.

**Getting the most out of it.**
- Speak in phrases with natural pauses. Pauses tell the later steps where sentences and commands begin.
- If live text feels laggy, try Tiny, or turn down **Live text refresh** under Advanced options.

### Fix punctuation

**What it does.** After you stop speaking, adds periods, commas, question marks and capital letters. It never changes your words.

**Set it up.** Import a punctuation model (Languages & Models → Punctuation Model), then turn on **Fix punctuation** in Voice Input settings. The English model (`pcs_en`, about 67 MB) restores punctuation, capitals and sentence breaks. The multilingual model (about 164 MB) adds punctuation in many languages, with simpler rule-based capitals. Whisper adds its own punctuation, so this step is skipped while Whisper is your voice model.

**How it works.** Moonshine ends a sentence at every pause, so "We should. You know, leave early." is common raw output. The punctuation model reads the whole transcript at once and decides where sentences really end. When Smart corrections is on, a further check (the *sentence-break veto*) asks the language model whether each break the punctuation step added mid-utterance reads better joined. A clause starting with "who" or "which" is rejoined with a comma ("to Marcus. Who locked…" becomes "to Marcus, who locked…"), unless that sentence is a question.

### Smart corrections for voice

**What it does.** Reads each finished dictation and fixes what only the whole sentence can tell. Every kind of fix has its own switch, under Voice Input → Smart corrections. It never rephrases what you said.

**Set it up.** Import the Smart corrections model (about 470 MB, English only), then turn on **Use Smart corrections**. It needs an ARMv8.2 or newer processor, and the app tells you if yours isn't. The same model powers [Smart corrections for typing](#smart-corrections-for-typing).

**How it works.** Rules propose candidate edits, and a small language model (Qwen3-0.6B, run with llama.cpp) scores the sentence with and without each one. An edit is applied only when it makes the sentence clearly more likely, by a margin set per kind of fix. A guard then checks every change against what that kind of fix is allowed to do, so a sound-alike fix can never insert a new idea, for example. The model is released as soon as the text is done.

Here are the kinds of fixes it can make.

#### Misheard words

Swaps a word for one that sounds the same when the sentence calls for it, like "bulls" → "bowls" in a sentence about soup, "their" → "there", or "in to" → "into". Candidates come from a sound-alike lexicon built from a pronouncing dictionary (words with the same number of sounds and at most one different).

#### Names and your words

Spells names and words from your personal dictionary the way you do ("Katelyn" → "Caitlin" if Caitlin is in your dictionary), and fixes stray capital letters. Names the keyboard learned from your typing count too.

A spoken word, or a few words run together ("see ya mara" → "Xiomara"), must sound like the name. Several words must match the name's main pronunciation, and an exclamation is never part of a name, so "Oh darn" can't become a name that only loosely sounds like it.

*Tip.* Add unusual names you dictate often to your personal dictionary (long-press a suggestion → Add to dictionary, or Settings → Copy learned words to dictionary).

#### Numbers, times and amounts

Writes spoken numbers as digits, like "twenty five dollars" → "$25", "three thirty pm" → "3:30 PM", "fifty percent" → "50%" and "the twenty first" → "the 21st".
- Amounts with a unit (dollars, percent and so on) always become digits, anywhere in a sentence. "Six hundred dollars profit" becomes "$600 profit". The same goes for digits followed by a spoken unit, so "800 dollars" becomes "$800".
- A plain number that starts a sentence stays in words ("Three hundred people came"), as style guides recommend.
- A lone number word with no unit needs more evidence, since "forty winks" and "the eleventh hour" read better spelled out.

#### Filler words

Removes "um", "uh", a "like" or "basically" between pauses, and words you said twice by accident ("I I think"). Fillers inside a quote are kept.
- The Filler words page (under Smart corrections) shows every built-in filler. Switch any off, add your own ("or whatever"), or turn off the whole built-in list.
- Fillers you add are only removed in the middle of a sentence.
- "You know" isn't a built-in filler, because it is often meant. Add it if you want it removed.

#### Self-corrections

When you change your mind mid-sentence, it keeps only the correction. "Let's meet Tuesday, no wait, Wednesday" becomes "Let's meet Wednesday". "I mean it" and "what I mean is" stay as they are.
- Built-in phrases include "I mean", "no wait", "sorry", "actually", "scratch that", "make that", "or rather", "let me rephrase" and more. You can switch any off or add your own on the Self-corrections page.
- **A cue at the very start of a dictation is left alone.** "No, I mean the blue folder." is usually correcting a message you already sent, so there's nothing earlier in it to replace.
- Corrections work best with a short pause before the phrase.

#### Quoted speech

Adds quotation marks when you repeat what someone said, so `I was like um what are you doing` becomes `I was like "Um, what are you doing?"`. It only does this when the sentence makes it clear (a question, or a reply that starts with "um", "oh" or "wow"). "He was like ten feet tall" stays as it is. The phrases that introduce a quote ("she said", "he goes", "I was like" and others) are listed on the Quoted speech page, where you can switch them off or add your own.

#### Emoji

Say "laughing emoji" → 😂, "heart emoji" → ❤️ or "thumbs up emoji" → 👍. Each emoji answers to its nicknames, Unicode name and keywords, in the plural, in any word order, and without "face" or "with". When you're talking *about* an emoji ("the heart emoji is my favorite"), the words stay. A name that fits several emoji gives none. On the Emoji page you can search to see which names give which emoji, switch names off, or add your own ("lol emoji" → 😂).

#### Voice commands

These let you format as you speak, with a short pause before or after the command. When the words are part of your sentence ("a new line formed at the door"), they stay as words. Here's what's built in.

| Say | Gives |
|---|---|
| new paragraph, paragraph break | a blank line |
| new line, next line, line break | a line break |
| period, full stop · comma · question mark · exclamation point/mark · colon · semicolon | . , ? ! : ; |
| ellipsis, dot dot dot · em dash · hyphen | … — - |
| open/close parenthesis · open/close bracket | ( ) [ ] |
| open quote(s) · close quote(s), end quote, unquote | " " |
| ampersand · at sign · hashtag · dollar sign · percent sign · asterisk · plus sign · equals sign · forward slash · underscore | & @ # $ % * + = / _ |
| smiley face · frowny face | :) :( |
| scratch that, delete that, delete last sentence | deletes the last sentence |
| delete last word | deletes the last word |

Edit, add or remove commands on the Voice commands page (one to a few words each).

#### Question marks

Turns a "." into a "?" when the sentence is clearly a question. This is part of Smart corrections itself and has no separate switch.

### Sensitivity dials

Smart corrections → **Sensitivity** has one dial per kind of fix (misheard words, names, question marks, numbers, fillers, quoted speech, emoji, voice commands and self-corrections). Each goes from *Fewest* to *Most* changes, with *Default* in the middle.

**How it works.** Each fix is applied only when the language model finds the sentence clearly better with it, by a set margin. Each step of a dial moves that margin by about four times the odds. Left asks for stronger evidence, and right accepts weaker evidence. Fixes the rules are certain of (like "new paragraph" said on its own after a pause) don't depend on the dials.

**Getting the most out of it.** Change one dial at a time and live with it for a few days. If one kind of fix changes words you meant, slide its dial left. If it misses fixes you want, slide it right.

### Advanced voice options

These are under Voice Input → **Advanced options**, and they apply when a Moonshine model is your English voice model.

- **Show words while speaking.** When it's off, each phrase appears when you pause. That uses less battery but takes a moment longer when you finish.
- **Live text refresh.** How often the words on screen catch up with your speech. Faster feels more live but uses more battery.
- **Stop after silence.** Finishes voice typing after you've been quiet for the time you choose, as if you tapped to finish. Background noise can keep it listening.
- **Processing mode** and **Processor cores.** How the model runs on your phone. Standard is usually fastest. Turn on **Show timing info** to compare settings on your phone.

### Saving dictations for tuning

Voice Input → **Save dictations for tuning** keeps your last 200 dictations on the phone, each one as the voice model heard it, after punctuation, and after Smart corrections. It's for studying and improving voice typing on a computer. It saves what you say, so it's off by default. Nothing leaves your phone on its own, and turning it off deletes the saved dictations.

---

## Typing

### Smart corrections for typing

**What it does.** It rereads each sentence you finish and fixes typos that slipped past the usual autocorrect, using the words around them. It fixes two kinds, each with its own switch.

- **Fix typos that are real words.** These are slips that spell another word, which a spell checker can't catch, like "salt an pepper" → "salt and pepper" or "an email form my boss" → "from". It uses the [typing corrections list](#the-typing-corrections-list).
- **Attempt to fix missed typos.** These are misspelled words that got through anyway, for example because the typo was the middle suggestion and the space bar kept it. It works out the word you meant from the letters you typed *and* the sentence around them, like "ststy the game" → "start the game".

**Set it up.** It uses the Smart corrections model (the same one as voice). Turn it on in Text prediction → Smart corrections for typing. English only.

**Using fixes.**
- Each fix shows up as a small button in the suggestion bar above the keyboard. Tap it to change the word back.
- With several fixes, "+N" opens the list of all of them. A fix whose text you have since edited can't be undone from there.
- Right after an undo, the button offers **Stop** for a few seconds. Tapping it stops that fix everywhere by switching off its pair in your corrections list (or, for a missed typo, by adding the word to your never-change list).
- **Typing fix buttons on the right** puts the undo and list buttons near your right thumb. Turn it off to put them on the left.

**It learns from you.**
- *Undoing* a fix makes that same change more cautious, a little everywhere and much more in the same context (the words around it).
- *Fixing a miss yourself* makes it more ready. If a word it could have fixed was left and you change it into its pair word by hand ("pour" → "pout"), that fix needs less evidence next time, within limits.
- Only counts per change and per context are kept, never your text.

**Sensitivity.** There is one dial for typing. Slide left and it holds back, changing your words only when it's sure. Slide right and it steps in more readily.

**How it works.**
- After a sentence ends (or a few words after a possible slip, while you keep typing), the sentence is checked in the background. Each candidate fix is scored as the whole text, including up to 300 characters before the sentence, with the same language model as voice. A sentence gets at most two fixes, and a word that was fixed or that you changed by hand is never changed again.
- *Real-word typos.* Candidates are the pairs of the typed word, for example "an" → "and". A fix must raise the probability of the whole text by a margin, and more for a sentence's first word (which has less context). The words after the fix must not get less likely. Your own pairs need less evidence.
- *Missed typos.* A word none of the keyboard's dictionaries knows (the main dictionary, your personal dictionary, contacts and learned words) gets candidates from about 55,000 common words. They're ranked by how likely the slip is on a QWERTY keyboard. A neighbouring key costs less than a far one, and swapped, doubled and missing letters each have their own cost ("ststy" → "start" is three neighbour-key slips). Each candidate's score is how much more likely the sentence becomes, minus the cost of the slip. The best candidate is used only if it scores well **and** clearly beats the runner-up, and a near-tie is left alone rather than guessed. So it combines both kinds of evidence, the way you'd fill in the blank in "I've sent the order, so ____ the game now" knowing the typo was "ststy".
- Missed typos never touch words with capitals inside or in all caps ("MCP"), a capitalized word mid-sentence (likely a name), words with digits or symbols, words shorter than three letters, or anything a dictionary knows. Words under five letters need stronger evidence, since they have many neighbours and are often abbreviations ("brb").

**Getting the most out of it.**
- Add words you often mistype as your own pairs (see [Add correction](#add-correction)). Your own pairs are trusted more.
- If a fix is wrong, undo it rather than retyping the word, because undoing is what teaches it.
- It doesn't run in password, email, URL or no-suggestion fields.

### The typing corrections list

Text prediction → Smart corrections for typing → **Typing corrections list** shows which typed words can be fixed into which. A word is only fixed into one of its pairs, and only when the sentence makes it clear.

- **Built-in pairs.** Real words one key off from other common words ("an"/"and", "or"/"of", "form"/"from"). Search a typed word to see its pairs and switch any off, or turn the whole built-in list off to use only yours.
- **Your pairs.** A typed word and one or more words you meant ("teh" → "the", "pour" → "pout").
- **Never change.** Words that are never fixed into another word.
- **Undo your changes** removes your pairs and never-change words and switches the built-in pairs back on.

#### Add correction

Select one word in any text field and an **Add correction** button appears in the suggestion bar. Tap it, type the word you meant and tap Save. The pair is added to your list, and the selected word is replaced. Next time you make that typo, Smart corrections can fix it.

### Adaptive typing heatmap

**What it does.** It learns from the times you hit a neighbouring key by mistake and slowly shifts each key's tap area toward where you actually tap, so you can type faster without aiming so carefully.

**Set it up.** It's on by default (Text prediction → Adaptive typing heatmap). Open it to see your heatmap, switch it off, or reset what it learned. Portrait and landscape are learned separately.

**How it works.** The code is in `java/src/org/futo/inputmethod/latin/heatmap/`.
- For every letter key, `HeatmapModel` keeps running statistics of where taps landed relative to the key (average offset and spread, in units of the key's size). It doesn't store any text. Statistics are blended with a prior, so a key with few samples barely moves, and old samples fade so the model keeps following how you type.
- Learning uses words you finish. `RecentWordBuffer` holds recent words' raw tap positions for a moment. If you then correct a word (choose a suggestion, let autocorrect change it, go back and edit it, or delete and retype it), the taps are relabelled with the word you meant. `TapAlignment` accepts only plausible typos (same length, few substitutions, plain taps). Nothing is learned in password or incognito fields.
- The learned offsets shift and resize each key's effective hit area in `KeyDetector`, by at most half a key. The corrected tap positions are also passed to the suggestion engines.

### Always use middle suggestion

Like SwiftKey, space always picks the middle suggestion, even when the keyboard isn't sure. Words the keyboard doesn't know stay in the middle as you typed them. While it's on, the Autocorrect Threshold setting isn't used. It pairs well with [missed typo fixing](#smart-corrections-for-typing), which catches the typos this lets through.

### Correct words before punctuation

This is on by default, and typing punctuation like ! or ? corrects the word before it, just like space. When it's off, punctuation keeps the word exactly as you typed it, and symbols become part of the word. Space still corrects.

---

## Privacy

- **No internet permission.** Models are imported from files, and nothing is uploaded.
- **Protect password-like words** (on by default). Words that look like passwords, keys or email addresses aren't learned. A "Looks like a password" notice lets you learn one anyway.
- **Copy learned words to dictionary** adds names and other new words the keyboard learned to Android's personal dictionary, so Smart corrections and other apps can use them. Words that look sensitive are flagged and not copied. Android's personal dictionary can be read by other apps, so only copy what you're comfortable sharing. You can hide words from this list, or make the keyboard forget them.
- What Smart corrections for typing learns is kept as counts per change and context, never your text.

## Themes

Pluto Classic (the default), Gilded Horizon, Ember Ridge, Horizon and Frost sit alongside FUTO's themes. **Follow system light/dark mode** switches between a day theme and a night theme of your choice when your phone changes mode.

## Feature tips

The first time you meet a feature, a short tip appears over the top of the keyboard. For example, you'll see one

- for your first typing fix or missed-typo fix, with how to undo it,
- for your first undo, with what it teaches,
- the first time you fix a missed typo yourself,
- the first time you select a single word, with what Add correction does,
- and the first time Smart corrections tidies a dictation.

Tips only appear for features you have switched on, each only once. Tap **Got it** to close a tip, or **Turn off tips** to stop them all. Under Settings → Help you can switch tips back on (**Show feature tips**) or see them all again (**Show tips again**).

## Getting the models

Pluto can't download anything by itself (that's the point of having no internet permission). Get model files from the [release page](https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/releases/latest) in your browser and import them without unzipping (Voice Input → Set up voice typing, or Languages & Models → English). To build your own model packages, see [MODELS.md](MODELS.md). To build the app and the official packages, see [BUILDING.md](BUILDING.md).
