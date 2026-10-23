# Building Pluto Keyboard

This page is for people who want to build the app or the model packages themselves. If you just want to use Pluto, see the [README](../README.md).

## What you need

- **JDK 17 to 24.** The Gradle wrapper downloads Gradle 8.14.3 itself.
- **Android SDK** with platform `android-35` and build-tools 35 (Android Studio installs these for you). Point Gradle at it with `ANDROID_HOME` or a `local.properties` file containing `sdk.dir=...`.
- **Android NDK `28.2.13676358`** and **CMake `3.22.1`** (both installable from the SDK Manager). They are used for the keyboard's native code and for the llama.cpp-based Smart corrections library.
- **Python 3**. Gradle runs a small script during the build to generate keyboard text resources (`python` on Windows, `python3` elsewhere).
- **Git**, with submodule support.

## Get the source

The repository uses submodules (keyboard layouts, themes, translations, native libraries and llama.cpp), so clone recursively:

```
git clone --recursive https://github.com/ColinCPittman/Futo-Keyboard-ASR-Heatmap.git
cd Futo-Keyboard-ASR-Heatmap
```

If you forgot `--recursive`:

```
git submodule update --init --recursive
```

The Smart corrections library needs `voiceinput-shared/src/main/cpp/llama.cpp` to be populated. If that folder is empty, the submodules were not fetched.

## Build the app

```
./gradlew assembleStableRelease
```

(On Windows use `gradlew.bat`, or run `./gradlew` from Git Bash.) The APK ends up under `build/outputs/apk/stable/release/`.

Useful variations:

| Command | Result |
|---|---|
| `./gradlew assembleStableRelease` | Release APK for **arm64-v8a** only. This is what Pluto ships. |
| `./gradlew assembleStableRelease -PallAbis` | Includes every ABI (for example x86_64 for an emulator). The ONNX Runtime library alone adds roughly 55 MB per extra ABI. |
| `./gradlew assembleUnstableDebug` | Debug build with an `.unstable` application ID suffix, so it installs next to the stable build. |

The version name and code come from the `VERSION_NAME` and `VERSION_CODE` environment variables if they are set, and otherwise fall back to `git describe --tags` and `git rev-list --count HEAD`.

### Signing a release

Without any setup, Gradle signs the release build with the shared debug keystore (`java/shared.keystore`). That is fine for testing but is not a key you should publish updates with. To sign with your own key, create a `keystore.properties` file in the repository root (it is git-ignored):

```
storeFile=/path/to/your.jks
storePassword=...
keyAlias=...
keyPassword=...
```

`storeFile` is resolved relative to the repository root. Keep this file and the keystore out of version control. Android only installs an update if it is signed with the same key as the installed app, so back your key up.

## Building the model packages

The app has no internet permission, so voice models are imported from `.zip` "model packages". The scripts in `tools/` build them. Each package is a flat zip with a `futo-model.json` manifest first (`format`, `kind`, `id`, `name`, `language`, `engine`, `license`, `source`), followed by the model files. The `name` is what the app shows in its model lists.

The scripts need only the Python standard library, except where noted.

To make packages from other models (what the manifest fields mean and which model types Pluto can run), see [MODELS.md](MODELS.md).

### Voice models (Moonshine, English)

```
python tools/package-models.py --out ./packages
```

Downloads the Moonshine streaming weights and the Silero VAD model, caches them, and writes:

- `pluto-voice-moonshine-small-en.zip` (recommended)
- `pluto-voice-moonshine-medium-en.zip`
- `pluto-voice-moonshine-tiny-en.zip`

Options: `--models tiny,small,medium` to pick some, `--cache DIR` for the download cache.

### Punctuation models

```
python tools/package-punctuation-models.py --reduce --out ./packages
```

Writes `pluto-punctuation-en.zip` (pcs_en, engine `pcs`) and `pluto-punctuation-multilingual.zip` (Horizon-Labs small, engine `horizon`). `--reduce` shrinks the ONNX files by storing weights as fp16; it needs `pip install onnx numpy`, and `--python` lets you pick the interpreter that has them. Use `--only pcs` or `--only horizon` to build one. `--work DIR` is the download cache.

### Smart corrections (context model)

This package needs two inputs you prepare first:

1. The language model as a GGUF file: `Qwen_Qwen3-0.6B-Q4_0.gguf` from <https://huggingface.co/bartowski/Qwen_Qwen3-0.6B-GGUF>.
2. The sound-alike lexicon, built from CMUdict and wordfreq. This step needs a Python environment with `pronouncing`, `wordfreq` and `metaphone` installed:

   ```
   cd tools/context-bench
   PYTHONHASHSEED=0 python build_lexicon.py soundalike-en.lex
   ```

Then package them:

```
python tools/package-context-model.py \
    --gguf Qwen_Qwen3-0.6B-Q4_0.gguf \
    --lexicon tools/context-bench/soundalike-en.lex \
    --out packages/pluto-smart-corrections-en.zip
```

The package also contains `context.json` (the thresholds the rescorer uses) and a `NOTICE.txt` with the licenses.

### Checksums

After building, publish checksums next to the files, for example `sha256sum pluto-*.zip > SHA256SUMS.txt`.

## Desktop test tools

Small standalone Gradle projects under `tools/` run the Kotlin voice code on a PC so it can be compared with reference Python implementations:

- `tools/moonshine-harness`: transcribe a 16 kHz WAV with a Moonshine package.
- `tools/punctuation-harness`: run the punctuation engines and compare them with the Python references (`run_comparison.sh`).
- `tools/context-harness` and `tools/context-bench`: the Smart corrections rescorer, its benchmark set, and the Python reference.

## How voice typing works

Voice input is a short pipeline. Everything runs on the phone.

1. **Speech recognition.** Audio is recorded and fed to the selected engine. For English this is Moonshine streaming (`voiceinput-shared/src/main/java/org/futo/voiceinput/shared/moonshine/`), a Kotlin port of parts of the Moonshine C++ core running its models on ONNX Runtime, with Silero VAD splitting long dictation into utterances. Words are shown as they are recognised. For other languages, FUTO's Whisper engine is used, and it transcribes after you stop speaking.
2. **Punctuation (optional).** When you finish, the whole transcript goes through a punctuation restoration model (`.../punctuation/`): `pcs_en` for English, which restores punctuation, casing and sentence boundaries, or Horizon-Labs small for many languages, with rule-based casing. It only changes punctuation and capitalisation, never words.
3. **Smart corrections (optional).** A small language model (Qwen3-0.6B, run through llama.cpp in `libfutoctxlm.so`) scores the transcript (`.../context/`). Candidate replacements come from the sound-alike lexicon (same number of phonemes, at most one different, Double Metaphone agreement), and a guard (`TranscriptGuard.kt`) limits what can change: a word may only be swapped for a sound-alike that fits clearly better, and a `.` or `,` may only become a `?`. Nothing is reworded, added or removed.

Models are released after use. The Moonshine model is dropped after two idle minutes, the punctuation model after three, and the Smart corrections model as soon as the transcript is done.

## How the typing heatmap works

The heatmap code lives in `java/src/org/futo/inputmethod/latin/heatmap/`.

- For every letter key, `HeatmapModel` keeps running statistics of where taps landed relative to the key (average offset and spread, in units of the key's size). It does not store any text. Statistics are blended with a prior, so a key with few samples barely moves, and old samples fade so the model keeps following how you type.
- Learning uses words you finish. `RecentWordBuffer` holds recent words' raw tap positions for a moment; if you then correct a word (choose a suggestion, let autocorrect change it, go back and edit it, or delete and retype it), the taps are relabelled with the word you meant. `TapAlignment` accepts only plausible typos (same length, few substitutions, plain taps), and nothing is learned in password or incognito fields.
- The learned offsets shift and resize each key's effective hit area in `KeyDetector`, by at most half a key. The corrected tap positions are also passed to the suggestion engines.
- The statistics can be viewed as a heatmap or reset in **Predictive text → Adaptive typing heatmap**.
