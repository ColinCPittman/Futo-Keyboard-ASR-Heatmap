# Pluto Keyboard (next version)

This update is mostly about Smart corrections for typing, which can now catch plain misspellings too, plus a dial for every kind of fix so you can decide how bold it gets. There are also short tips the first time you run into a feature, and a full features guide.

## What's new

- **Attempt to fix missed typos (typing).** When a misspelled word slips past autocorrect (say it was the middle suggestion, so the space bar kept it), Smart corrections works out the word you meant from the letters you typed and the sentence around them, like "ststy the game" → "start the game". It only steps in when one word is the clear answer, and it leaves names, abbreviations and your own words alone.
- **Two switches for typing fixes.** Smart corrections for typing now has separate switches for typos that are real words ("salt an pepper") and for missed typos, so you can use one without the other.
- **Sensitivity dials.** Every kind of voice fix (misheard words, names, numbers, fillers, quotes, emoji, commands, self-corrections and question marks) has its own dial from Fewest to Most changes, and typing has one too. Slide left if it changes things you meant, or right if it misses fixes you want.
- **Add correction.** Select a word you often mistype and tap Add correction in the suggestion bar to tell it what you meant. Next time, it can fix that typo for you.
- **Stop a fix.** Right after you undo a typing fix, the button offers Stop for a few seconds, which turns that fix off everywhere.
- **It learns from you.** Undoing a fix makes it more careful in that context, and fixing a miss yourself makes it more confident next time.
- **Up to two fixes per sentence** for typing, instead of one.
- **Feature tips.** The first time you meet a feature (your first fix, your first undo, selecting a word and so on), a short tip explains it. You can turn tips off, or see them all again, under Help.
- **Live voice typing options.** Under Voice Input → Advanced options you can now choose whether words show while you speak, how often the live text refreshes, and whether voice typing stops by itself after a pause.

## Voice improvements

- Amounts said with a unit always become digits, so "six hundred dollars profit" is now "$600 profit" and "800 dollars" is "$800".
- "You know" is no longer removed as a filler word, since people usually mean it. You can still add it to your own list.
- A self-correction phrase at the very start of a dictation ("No, I mean the blue one") is left alone, because it's usually correcting a message you already sent.
- Names from your personal dictionary need a closer sound match, so "Oh darn" won't turn into a name that only loosely sounds like it.
- Sentences that the punctuation step split before "who" or "which" are joined back up ("to Marcus, who locked…").

## Other changes

- A new [features guide](https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap/blob/main/docs/FEATURES.md) explains every feature, how it works and how to get the most out of it.
- Settings text and docs were rewritten in plainer English.

## Updating

Install the new APK over the old one (or let Obtainium do it). Your settings, learned words and models stay. If you use Smart corrections for typing, the two new switches start out on.
