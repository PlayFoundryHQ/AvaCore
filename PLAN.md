# AvaCore — Plan & Change Log

> Status (2026-09-12): **multi-language engine shipped and verified** (Persian /
> English / Swedish). Two real production bugs found live and fixed. This
> document is the single place tracking what changed, why, and what's left —
> mirrors the pattern used in the companion **AdaptiveFlow** repo.

## 0. What AvaCore is

An offline-first, streaming Text-to-Speech engine for Android, built on
Sherpa-ONNX + Piper VITS + eSpeak-NG, registering as a system
`TextToSpeechService` so any app on the device can use it. **Persian is the
heart of the project** — a full custom NLP front-end (ezafe restoration,
number expansion, script normalisation, a curated pronunciation lexicon)
exists specifically because eSpeak's own Persian support is weak. English and
Swedish ride the same pipeline as plain, high-quality Piper voices, no custom
front-end needed since eSpeak already handles their numbers/punctuation well.

Primary consumer today: **AdaptiveFlow** (`PlayFoundryHQ/adaptiveflow`), which
prefers AvaCore for any language it reports supporting, asked live via
`TextToSpeech.isLanguageAvailable()` — no hardcoded language list on the
caller's side.

## 1. Change log

### PR #5 — Multi-language engine (Persian + English + Swedish)
Generalised `AvaTtsService` from one hardcoded Persian `OfflineTts` instance
to a per-language `VoiceModel` registry, each lazily bound on first request
(Persian eager-loaded in `onCreate`, since it's the primary language).
`TextProcessor` gained an `applyPersianPipeline` flag — non-Persian languages
skip straight to SSML + sentence segmentation. `download_assets.sh`
generalised to a `VOICES` list, fetching one Piper bundle per language from
the same `k2-fsa/sherpa-onnx` release (`en_US-amy-medium`,
`sv_SE-nst-medium`, alongside the existing `fa_IR-gyro-medium`).

**Bug found + fixed during that work:** `onIsLanguageAvailable` only matched
2-letter language codes; some Android framework call sites send ISO-3
(`"eng"`, not `"en"`). Silently disabled the Settings TTS language picker for
every voice. The original Persian-only code had special-cased `fa`/`fas` —
the generalisation dropped it. Fixed to match either form.

### PR #6 — `setVoice()` was silently failing
Found *live, by ear* while demoing the three languages: Persian and Swedish
samples were coming out in the English voice. Root cause —
`TextToSpeech.setVoice(Voice)` calls the **per-voice** hooks
`onIsValidVoiceName(name)` / `onLoadVoice(name)`, not the legacy
locale-based `onIsLanguageAvailable`/`onLoadLanguage` hooks `AvaTtsService`
already had. Without them, the framework's default `onIsValidVoiceName`
rejects every voice, `setVoice()` returns `ERROR`, and the client's
previously-loaded voice (whichever bound first — English) silently stays
active regardless of what was actually requested.

**This had been broken since PR #5 shipped** — every multi-language request
from any real caller (not just this repo's own demo screen) was affected.
Fixed by implementing both hooks against the `VOICES` registry.

**Verified on-device (OnePlus) after both fixes:** Settings → Text-to-speech
lists all three languages; a live demo cycled fa → en → sv with logs
confirming `setVoice()` → `SUCCESS` and `onSynthesizeText` resolving the
matching engine each time; confirmed end-to-end through **AdaptiveFlow's own**
`TtsController` speaking a real flashcard (not just this repo's test screen).
By ear: **English and Swedish sound very good; Persian is acceptable but not
yet as natural as the other two** — expected, matches the known eSpeak
Persian-phonemization gap below, not a routing defect.

### Companion fix — AdaptiveFlow's own manifest
Separately, AdaptiveFlow (the consumer) had **no `<queries>` declaration** for
AvaCore's package. On API 30+, Android's package-visibility rules made every
`PackageManager.getPackageInfo(AVACORE_PACKAGE)` call throw silently, so
`isAvaCoreInstalled()` always returned `false` — AvaCore routing had **never**
actually fired from AdaptiveFlow, regardless of whether AvaCore was installed.
Fixed on that side (`adaptiveflow` v0.8.0); noted here because it's the other
half of why "nothing seemed to reach AvaCore" for so long.

## 2. Current state (2026-09-12)

- Languages: **Persian** (custom NLP front-end), **English** (`en_US-amy-medium`),
  **Swedish** (`sv_SE-nst-medium`). All Piper VITS "medium" quality.
- Each language's `OfflineTts` engine binds lazily on first request (Persian
  eager in `onCreate`).
- `AvaTtsService` correctly implements both the locale-based and per-voice
  framework hooks — `onIsLanguageAvailable`/`onLoadLanguage`/`onGetLanguage`
  **and** `onIsValidVoiceName`/`onLoadVoice`.
- Debug APK ≈ 190MB (three float32 medium models bundled in `assets/`, all
  provisioned at build time via `download_assets.sh`; none downloaded at
  runtime).
- `release-please` on `main` currently **fails**: "GitHub Actions is not
  permitted to create or approve pull requests" — a repo Settings → Actions
  toggle, not a code issue. Needs a human with repo admin to flip it
  (Settings → Actions → General → Workflow permissions → allow PR creation).

## 3. Punch list — what's left

### Phase 1 — Quick wins (this pass)
| # | Item | Status | Notes |
|---|---|---|---|
| 1 | Fix `TextProcessor.spellOut()` for non-Persian SSML `say-as` | ✅ Done | Now branches on `applyPersianPipeline`: Persian keeps cardinal-word expansion + `، ` separator; en/sv spell out the raw characters with a plain `, ` separator and let eSpeak's own per-language reader take it from there. Covered by two new tests. |
| 2 | Regression tests for the voice-selection bug class | ✅ Done | Extracted `VoiceModel`/`VOICES`/`voiceForLang`/`voiceForName` out of `AvaTtsService` into a pure-Kotlin `VoiceRegistry` object; added `VoiceRegistryTest` (6 cases) locking in 2-letter *and* ISO-3 language matching, exact `voiceName` matching, and the Persian-first/pipeline-flag invariants — the exact shape of both real bugs found this session. |
| 3 | Try Piper "high" quality voices | ⚠️ Investigated, not shipped | Checked upstream (`k2-fsa/sherpa-onnx` `tts-models` release): `en_US-lessac-high` and `en_US-ryan-high` exist (HTTP 200), but there is **no** `-high` bundle for `sv_SE-nst` or `fa_IR-gyro` (HTTP 404 — those voices only ship at `-medium`). So a "try high quality" pass can only touch English, and choosing between amy/lessac/ryan is a voice-*character* preference, not a bug fix — it needs the same on-device listening test the user did for the language routing, not a unilateral swap. Also weighed against the standing "minimum GitHub Actions bandwidth" constraint: bundling an extra ~60MB alt-voice just to A/B would grow every CI run and every install for a call only the user's ears can make. **Left as a documented option, not implemented** — say the word and it's a one-line change to `download_assets.sh`'s `VOICES` array (swap `en_US-amy-medium` → `en_US-lessac-high` or `en_US-ryan-high`). |
| 4 | *(manual, not code)* Fix the `release-please` Actions permission | ⏳ Deferred to user | Needs repo admin: Settings → Actions → General → Workflow permissions → "Allow GitHub Actions to create and approve pull requests." Blocked from being done via API (Claude Code's own auto-mode classifier refuses org/repo security-permission changes on the user's behalf) — this is intentional and by design, not a bug. |

### Phase 2 — Medium effort, real payoff
| # | Item | Status | Notes |
|---|---|---|---|
| 5 | On-demand model download instead of bundling all languages in the APK | ⏳ Not started | Every install currently carries every language (~190MB). Downloading a voice on first use (the same fetch `download_assets.sh` does at dev-time, run from the live app instead) shrinks the install and lets new languages be added without a full app update. Biggest architectural lift of the three — touches storage, permissions, and the service's init/gating path. |
| 6 | NNAPI hardware-acceleration trial | ✅ Tried, self-fallback confirmed | `createOfflineTts()` now tries `provider = "nnapi"` first, catching init failure and falling back to `"cpu"`. Verified live on-device: sherpa-onnx's own native layer logs `"Android NNAPI requires API level >= 27... Fallback to cpu!"` and runs on CPU regardless — this AAR build's own API-level gate never actually engages NNAPI on this hardware, independent of our try/catch. No crash, no regression, but also no measured speedup here; leaving the nnapi-first attempt in since it's free and may pay off on a device/AAR build where the gate passes. Real before/after latency numbers would need a newer AAR build or a different device — not chased further this pass. |
| 7 | A real in-app language/voice test screen | ✅ Done | `MainActivity` now builds one button per `VoiceRegistry.VOICES` entry (Persian/English/Swedish), each calling the real `setVoice()` → `speak()` path with a per-language sample phrase and showing the `setVoice()` return code + resulting active voice name on-screen. Verified live: tapping the English button flipped the active voice and produced `synthesize[en]` in logcat — this is exactly the diagnostic that used to require a live adb logcat session to find the `setVoice()` bug. |

### Phase 3 — The actual ceiling (long-term, research-scale)
| # | Item | Why |
|---|---|---|
| 8 | **Ezafe prediction + homograph disambiguation** (GE2PE-style) | The one thing that would meaningfully close the gap to cloud-quality Persian — see §5. Real ML project: data, training, eval. Weeks, not a quick add. |
| 9 | Richer normaliser (dates, currency, abbreviations, DadmaTools-style) | Smaller version of the same idea — more robust Persian text handling beyond the current lexicon. |
| 10 | SSML prosody/emphasis/phoneme tags | Expands expressiveness once core voice quality is settled. |

## 4. Original roadmap items (carried over, still open)

From the initial README roadmap, not yet superseded by anything above:
- **Smaller distribution** — per-ABI splits / an Android App Bundle. Dynamic
  INT8 quantisation was evaluated and **dropped**: it crashes this Sherpa/ORT
  build at load and gives no APK-size win since the zip already compresses
  the fp32 weights. Don't re-attempt that specific approach — Phase 2 item 5
  (on-demand download) is the better lever for size now anyway.
  in
- **SOTA model evaluation** — Matcha-TTS and Kokoro are drop-in candidates via
  Sherpa's existing `OfflineTtsMatchaModelConfig`/`OfflineTtsKokoroModelConfig`.
  Kokoro-82M specifically: excellent English, **no real Persian or Swedish
  voices** — would only ever be an *additional* English option, not a
  replacement for the Piper voices already in place.
- **Training methodology** (reference, unchanged): the Piper Persian voice
  traces to the **ManaTTS** corpus (~86h, 44.1kHz, Spleeter-cleaned), selected
  via multi-model ASR-voting forced alignment with strict CER thresholds.

## 5. On "is this the best option available"

Answered directly once, worth recording: **within its actual category —
offline, on-device, runs on an ordinary phone, free, private, multi-language
— yes.** Piper + Sherpa-ONNX is the best widely-available open combination
for this; nothing else open-source beats it broadly at this size/quality/
coverage tradeoff, and AvaCore's Persian model is trained on the same
corpus (ManaTTS) the best open Persian TTS research uses.

The one thing that still beats it is **unconstrained cloud TTS**
(Gemini/Azure/ElevenLabs) — not because of a better idea, but because those
aren't limited by phone size/latency and can spend far more model capacity
and training budget. That's the ceiling above what any phone-run model
reaches today, for any language. The remaining gap to that ceiling, for
Persian specifically, isn't a better acoustic model — it's the linguistic
front-end (Phase 3, item 8). Everything else (model family, runtime, size)
is already the right call.

## 6. What does *not* need doing

- **No "few-megabyte LLM that does both linguistics and voice"** — evaluated
  and rejected as physically unrealistic. No neural vocoder that sounds
  natural exists below ~15–20MB (Piper's ~60MB voices are already close to
  the practical minimum); the genuinely more-natural LLM-token architectures
  (Bark, XTTS, Fish-Speech) are 10–50× bigger and need GPU-class inference,
  not phone-viable. See §5 discussion for the full reasoning.
- **No re-attempt of dynamic INT8 quantisation** — already tried, already
  dead-ended (§4).
- **No switch away from Piper/Sherpa-ONNX** as the base stack — it's the
  right choice; see §5.
