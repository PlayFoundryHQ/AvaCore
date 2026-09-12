# AvaCore: Native Multi-Language Text-to-Speech (TTS) Engine for Android

AvaCore is a high-performance, on-device Text-to-Speech engine designed to provide a natural and seamless voice experience for Android users. By integrating directly with the `android.speech.tts` framework, AvaCore enables all Android applications to speak with human-like prosody and high clarity. On the device it runs **entirely offline** — no network access is needed at run time.

**Persian (Farsi) is the heart of the project** — the language AvaCore was built to get right, with a real linguistic front-end (see Project Vision below), and the only one bundled inside the APK. **English and Swedish** are first-class Piper voices too, fetched on-device the first time an app actually asks for them (see Setup below) and cached from then on — so any app that lets the user pick AvaCore gets consistently better speech than the stock system engine across all three, not just Persian, without every install paying for every language.

## Project Vision
To bridge the accessibility gap for Persian speakers on Android by delivering a state-of-the-art TTS engine that overcomes the unique linguistic challenges of the Farsi language, such as short-vowel omission and hidden *Ezafe* (کسرهٔ اضافه) constructions — and, from there, to be a genuinely good general-purpose offline voice for the languages around it.

## Setup (first build)
The large binary assets (the `.aar` engine, Persian's ~63 MB neural model, and the eSpeak-NG data) are **not committed to git** — they are provisioned on demand to keep the repo slim. Before the first build, run:

```bash
./download_assets.sh
```

This fetches the Sherpa-ONNX AAR, the eSpeak-NG data, and **Persian's** Piper voice model — the only language bundled in the APK (see `VOICES` in `download_assets.sh`). English and Swedish are **not** fetched by this script and are not part of the install: `ModelDownloader` fetches either one on-device, the first time an app actually requests it, from the same `k2-fsa/sherpa-onnx` release bundles, and caches it in the app's private storage after that (needs the `INTERNET` permission, already declared). If Persian's model fails to fetch, the script keeps going and Persian stays unavailable until you re-run it — once it succeeds, the build itself never needs network access again, and the app is offline for whichever languages are already cached.

---

## Current Architecture (what ships today)

AvaCore is built on a compact, proven, fully-offline stack:

| Layer | Technology | Notes |
| :--- | :--- | :--- |
| **Inference engine** | Sherpa-ONNX 1.10.41 (`app/libs/sherpa-onnx.aar`) | JNI + Kotlin wrapper around ONNX Runtime; tries the `nnapi` execution provider first, falling back to `cpu` — see the NNAPI note below |
| **Acoustic + vocoder** | **Piper VITS**, one model per language (`assets/tts/model_<lang>.onnx`) | End-to-end model — the HiFi-GAN-style decoder *is* the vocoder; 22.05 kHz. Persian, English, Swedish today; only Persian's model ships in the APK. |
| **Voice provisioning** | `service/ModelDownloader.kt` | English/Swedish aren't bundled — fetched on-device (tar+bzip2 via `commons-compress`) the first time that language is actually requested, atomically cached in `filesDir` after |
| **Grapheme-to-phoneme** | **eSpeak-NG** (`espeak-ng-data/`, shared across languages) | Persian phonemization is the hard case (see below); English/Swedish rely on eSpeak's own solid support for those languages |
| **Text front-end** | AvaCore `nlp/` pipeline (Kotlin) | **Persian only:** normalization, number/date/currency/abbreviation expansion, lexicon, segmentation, SSML. Other languages get SSML + segmentation only — eSpeak's own front end already handles their numbers/punctuation. |
| **System integration** | `AvaTtsService : TextToSpeechService` | Serves every app on the device; reports all bundled languages via `onGetVoices`/`onIsLanguageAvailable`/`onIsValidVoiceName`/`onLoadVoice`; each language's model loads lazily on first request |

> Note: VITS is a single end-to-end network. There is **no separate Tacotron front-end or WaveRNN vocoder** in the shipping engine.

> Note on NNAPI: the engine tries Android's NNAPI execution provider before falling back to CPU, but this isn't a guarantee of actually faster inference — on the hardware this was verified against, this Sherpa-ONNX AAR's own native layer gates NNAPI on API level and falls back to CPU regardless (no crash either way). Treat it as "tried, safe, unverified speedup" rather than a shipped acceleration.

### Synthesis pipeline
Text flows through `nlp/TextProcessor` before the neural model (Persian only — see the table above):

1. **SSML** (`nlp/Ssml.kt`) — `<speak>`, `<break>`, `<say-as>`, `<prosody rate/pitch>` and `<emphasis level>` are honored (the latter two as real per-segment rate/pitch multipliers carried through to synthesis); plain text passes through. `<phoneme>` is not supported — the underlying Piper wrapper has no seam for a per-word pronunciation override.
2. **Abbreviation expansion** (`nlp/AbbreviationExpander.kt`) — a short, conservative whole-word table (era markers, metric units) that eSpeak has no sensible reading for.
3. **Date/currency expansion** (`nlp/DateCurrencyExpander.kt`) — Jalali dates written as digit groups (`۱۴۰۴/۳/۱۲` → «دوازدهم خرداد هزار و چهارصد و چهار») and currency symbols (`$€£﷼₹`) attached to a number.
4. **Number expansion** (`nlp/NumberToWords.kt`) — full Persian cardinals/ordinals/decimals/percent (`۱۴۰۳` → «هزار و چهارصد و سه»), Persian/Arabic/ASCII digits.
5. **Normalization** (`nlp/Normalizer.kt`) — Arabic→Persian letter folding, ZWNJ (نیم‌فاصله) normalization, kashida/tanvin/diacritic cleanup, punctuation spacing.
6. **Pronunciation lexicon** (`nlp/PronunciationLexicon.kt` + `assets/tts/lexicon.txt`) — high-precision overrides for short-vowel restoration and fixed *ezafe* compounds (institutional phrases plus language-learning vocabulary — AdaptiveFlow's own domain); curated and easily extensible.
7. **Sentence segmentation** (`nlp/SentenceSegmenter.kt`) — splits text into short, prosodically-paused units, carrying any SSML rate/pitch multiplier along.

### Streaming + responsiveness
- **Incremental streaming:** synthesis uses Sherpa's `generateWithCallback`, so the first audio plays after the first chunk — latency-to-first-audio stays roughly constant regardless of text length.
- **Instant interruption:** `onStop()` aborts the current utterance mid-stream.
- **System speech-rate:** the platform speech-rate is mapped to the engine speed multiplier, further scaled per-segment by any SSML `<prosody>`/`<emphasis>`.
- **System pitch:** the platform pitch setting is honored via a duration-preserving SOLA pitch shifter (`dsp/PitchShifter`), likewise scaled per-segment; the default pitch path streams untouched audio.
- **OEM-safe buffering:** audio is delivered in ≤ 8 KB chunks to satisfy strict OEM audio paths (e.g. Oppo/OnePlus).
- **Robust asset migration:** bundled assets are extracted to `filesDir` once, versioned (`ASSETS_VERSION`) and copied atomically so a stale or partial copy is repaired automatically.
- **In-app voice test screen:** `MainActivity` exercises every installed voice's real `setVoice()` → `speak()` path with a per-language sample and shows the result on-screen — living documentation, not just a demo button.

See `ARCHITECTURE.md` for deployment details (16 KB page-size packaging, background-execution permissions on some OEMs).

---

## Roadmap

Full detail, verification notes and status live in **`PLAN.md`** — this is the short version.

### Done
- **On-demand voice download** — only Persian ships in the APK; English/Swedish are fetched and cached on first use (`ModelDownloader`).
- **NNAPI trial** — tried, confirmed safe (falls back to CPU cleanly); no measured speedup on the hardware this was verified against — see the note above.
- **In-app voice test screen** — every installed voice, one tap each, real `setVoice()`/`speak()` path.
- **Richer normalizer** — date (Jalali) and currency-symbol expansion, plus a small conservative abbreviation table.
- **SSML `<prosody>`/`<emphasis>`** — real per-segment rate/pitch control, verified end-to-end.

### Still open
- **Ezafe prediction & homograph disambiguation** — an ML model (GE2PE-style two-step G2P: large machine-generated pre-training + manual fine-tuning) to resolve مرد/مُرد-type ambiguities and predict ezafe in context, replacing the curated lexicon seed. This is the one item that would meaningfully close the remaining gap to native-sounding Persian — and it's a genuine multi-week ML project (data, training, eval), not a quick add.
- **SSML `<phoneme>`** — no seam in the current Piper wrapper for a per-word pronunciation override; would need engine-level changes.
- **Smaller distribution** — dynamic INT8 quantization was evaluated and dropped (it crashes this Sherpa/ORT build at load and gives no APK-size win since zip already compresses the fp32 weights). The viable lever is per-ABI splits / an Android App Bundle.
- **SOTA model evaluation** — Matcha-TTS (flow-matching) and Kokoro are drop-in candidates via Sherpa's existing `OfflineTtsMatchaModelConfig` / `OfflineTtsKokoroModelConfig`; Kokoro currently has no real Persian/Swedish voices, so this is mainly relevant if English quality alone becomes the priority.

### Training methodology (reference, for a future from-scratch Persian model)
- **Dataset:** the **ManaTTS** corpus (≈86 h, 44.1 kHz), cleaned with Spleeter.
- **Forced alignment:** multi-model ASR voting; strict CER thresholds (HIGH < 0.05, MIDDLE < 0.20) for data selection.

## Current measurements (not targets — what's actually true today)

| Metric | Value | Note |
| :--- | :--- | :--- |
| **Debug APK size** | 89 MB | down from ~199MB of bundled TTS assets before on-demand download |
| **On-demand voice download** | ~60 MB, ~35 s | one-time per language, on a typical Wi-Fi connection |
| **Latency to first audio** | low, not formally benchmarked | streaming synthesis — first chunk plays as soon as it's generated |
| **By-ear quality** | English/Swedish: very good, close to native; Persian: acceptable, noticeably less native | the user's own verdict after live listening; the gap is the front-end (ezafe/homographs), not the acoustic model |

---
AvaCore aims to set a new standard for Persian accessibility on Android: a robust, offline, high-quality voice for navigators, screen readers and virtual assistants.
