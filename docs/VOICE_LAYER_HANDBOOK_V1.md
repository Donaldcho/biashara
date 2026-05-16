# Biashara AI — Voice Layer Development Handbook

**Version:** v1.0 — May 2026  
**Prompts:** V0–V9  
**Branch:** `feat/voice-layer-stt-tts` (integration base: `release/biashara-phase4`)

> *"The owner speaks. The app listens, thinks, and speaks back."*

**Documents:** This file is the **short guide + repository alignment**. The **full V0–V9 specification** (verbatim structure, tables, acceptance criteria, extended code) is in **[`VOICE_LAYER_HANDBOOK_V1_SPEC.md`](./VOICE_LAYER_HANDBOOK_V1_SPEC.md)**.

---

## Repository alignment (BiasharaAI — read before V0)

The template below references generic version numbers and class names. In **this** codebase:

| Handbook template | This repository |
|---------------------|-----------------|
| `AppDatabase` v11 → v12 | **`AppDatabase` is version 20.** Add voice/TTS columns with **`Migration(20, 21)`** (and matching `AppSettings` / Room entity fields). |
| `HANDOFF.md` — "Last Completed: X12" | Complete or explicitly defer **Prompt X12** in `HANDOFF.md` before treating Phase 6 as closed for voice prerequisites. |
| `com.biashara.voice.*` in XML examples | Use **`com.biasharaai.voice.*`** (applicationId / namespace). |
| `InsightsFragment`, `ConversationalQueryViewModel`, `fragment_insights.xml` | Conversational Q&A lives in **`ChatFragment`** + **`ChatViewModel`**. Insights are **`CashFlowInsightsFragment`** (+ tabs). Map **V8** to the screens you wire (chat is the natural home for voice Q&A). |
| `capabilityChecker.getTier()` | Use **`DeviceCapabilityChecker.evaluate(...)`** (or the injected **`CapabilityTier`** where appropriate). |
| `AppSettingsDao.getSettingsSync()` | Implement or expose a **suspend** / **runBlocking** / **Flow**-backed accessor consistent with existing DAO patterns (avoid blocking main thread). |
| `AgentLoopRunner.answerQuery(...)` | Align with existing **`AgentLoopRunner`** + **`ChatViewModel`** / query layer APIs when implementing V8. |

Dependency coordinates (`com.argmaxinc:whisperkit`, Qualcomm QNN) must be **verified on Maven** and against min/target SDK before merge; packaging `jniLibs.useLegacyPackaging = true` is required per handbook.

---

## Overview

This handbook adds a complete **voice layer** to Biashara AI: two complementary capabilities.

| Capability | Direction | Technology | What it enables |
|------------|-----------|------------|-----------------|
| **Speech-to-Text (STT)** | Owner speaks → app understands | Argmax Pro SDK (Whisper) + Android `SpeechRecognizer` fallback | Questions aloud, commands, voice notes |
| **Text-to-Speech (TTS)** | App responds → owner hears | Android `TextToSpeech` | Agent alerts, query answers, AI read-aloud |

Together: owner speaks in Swahili (etc.) → transcribe → Gemma + Skills → show answer + **read aloud** — on-device, offline-first, no cloud STT/TTS cost.

---

## Pre-requisite checklist (before Prompt V0)

1. Phase 6 complete — Skills Engine, core skills, `AgentLoopRunner` working.  
2. `HANDOFF.md` shows **Last Completed: Prompt X12** (or documented waiver).  
3. ~~AppDatabase at version 11~~ → **use current Room version + next migration** (see alignment table).  
4. `TranscribeVoiceSkill` exists (Phase 6 X7) — this handbook **upgrades** the overall voice path.  
5. Inference on **LiteRT `Engine` / `Conversation` API** via `ActiveModelStore` (Phase 6 X1); `GemmaService` is deprecated façade only.

---

## The full voice flow

1. **Owner speaks** — Mic tap; `AudioCaptureHelper` records; pulsing indicator.  
2. **Whisper transcribes** — Argmax WhisperKit streams chunks; partial text live; final on stop or silence (~3s).  
3. **Language normalisation** — `VoiceInputProcessor` cleans filler / script artefacts; optional multilingual detect.  
4. **Intent routing** — `VoiceRouter`: query vs command vs data-entry.  
5. **Gemma reasons** — Queries → `AgentLoopRunner` + skills.  
6. **TTS speaks** — `BiasharaTtsEngine` reads response; speaker icon replay/stop.

---

## Prompt map

| Prompt | Focus | Key output |
|--------|-------|------------|
| **V0** | Dependencies, DB migration, `HANDOFF.md` | WhisperKit + QNN deps, `RECORD_AUDIO` / `MODIFY_AUDIO_SETTINGS`, **Room migration** for `app_settings` voice columns |
| **V1** | `AudioCaptureHelper` v2 | Streaming PCM chunks + silence detection |
| **V2** | WhisperKit integration | `WhisperTranscriber`, `WhisperModelManager`, `TranscriptionResult` |
| **V3** | `VoiceInputProcessor` upgrade | Tier routing: Whisper → Gemma 3n (where available) → `SpeechRecognizer` |
| **V4** | `VoiceRouter` | `VoiceIntent`, `CommandHandler`, pattern + optional model classification |
| **V5** | `BiasharaTtsEngine` | `TtsLanguageMapper`, sanitisation, `TextToSpeech` |
| **V6** | Voice UI components | `MicrophoneButtonView`, `TranscriptionBannerView`, `SpeakerButtonView` |
| **V7** | Agent feed read-aloud | Speaker on cards; optional CRITICAL auto-read |
| **V8** | Conversational voice Q&A | Speak → answer → TTS; POS / AddEdit data-entry mic |
| **V9** | Voice settings + tests | `VoiceSettingsFragment` + unit/instrumented acceptance |

---

## V0 — Dependencies, permissions, DB migration

**Goal:** Argmax Pro SDK, audio permissions, Room columns for voice/TTS preferences.

**`app/build.gradle.kts` (app module):**

```kotlin
// Argmax Pro SDK — on-device Whisper (verify artifact + version on Maven)
implementation("com.argmaxinc:whisperkit:0.3.3")

// QNN delegates for Qualcomm NPU (optional; verify compatibility)
implementation("com.qualcomm.qnn:qnn-runtime:2.34.0")
implementation("com.qualcomm.qnn:qnn-litert-delegate:2.34.0")
```

**`android` block:**

```kotlin
packaging {
    jniLibs {
        useLegacyPackaging = true
    }
}
```

**`AndroidManifest.xml`:** `RECORD_AUDIO` already present. Add:

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
```

**Room migration (template was 11→12; implement as next version, e.g. 20→21):** add to `app_settings`:

- `voiceInputEnabled` INTEGER NOT NULL DEFAULT 1  
- `whisperModelId` TEXT NOT NULL DEFAULT `'whisper-tiny'`  
- `silenceTimeoutMs` INTEGER NOT NULL DEFAULT 2500  
- `voiceLanguageMode` TEXT NOT NULL DEFAULT `'AUTO'`  
- `ttsEnabled` INTEGER NOT NULL DEFAULT 1  
- `ttsSpeechRate` REAL NOT NULL DEFAULT 0.9  
- `ttsPitch` REAL NOT NULL DEFAULT 1.0  
- `ttsAutoReadAgentAlerts` INTEGER NOT NULL DEFAULT 0  
- `ttsAutoReadQueryAnswers` INTEGER NOT NULL DEFAULT 1  

**Handoff:** Last completed **V0** → Next **V1**. Update `AppSettings.kt`, `AppSettingsDao`, `HANDOFF.md`.

---

## V1 — `AudioCaptureHelper` v2

Streaming **16 kHz mono PCM**; `Flow<AudioChunk>`; `SilenceDetector` (RMS → dB vs threshold; timeout); `stopRecording()`; `channelFlow` + `Dispatchers.IO`.

**Types:** `AudioChunk(pcmData, sampleRate=16000, isFinal)`, `SilenceDetector`.

---

## V2 — `WhisperTranscriber`

`TranscriptionResult`, `TranscriptionEngine` enum (`WHISPER`, `GEMMA_3N`, `SPEECH_RECOGNIZER`).  
`WhisperTranscriber`: init WhisperKit; `transcribeStream(Flow<AudioChunk>, languageHint?)`; **`AgentMutex`** around LiteRT-heavy calls.  
`WhisperModelManager`: downloads under `filesDir/whisper/{modelId}/` (reuse patterns from `ModelDownloadManager` where sensible).

**Languages (WER expectations):** Swahili / Hausa: good; Yoruba / Amharic: moderate — prefer Gemma multimodal path on **FULL_AI** when available (V3).

---

## V3 — `VoiceInputProcessor` upgrade

Single entry: `startListening() -> Flow<VoiceInputEvent>`.  
Engines: **WHISPER**, **GEMMA_3N**, **SPEECH_RECOGNIZER**.  
Routing: yo/am + FULL_AI → Gemma 3n; else Whisper if initialised; else `SpeechRecognizer` intent path.

---

## V4 — `VoiceRouter`

`VoiceIntent` sealed hierarchy (Query, Command variants, DataEntry, Unclassified).  
Pattern-first classification; optional `ActiveModelStore.sendPrompt` one-word classifier on **PARTIAL_AI+** for ambiguous cases.  
`CommandHandler` dispatches parsed commands.

---

## V5 — `BiasharaTtsEngine`

`TtsLanguageMapper`: `sw` → `Locale("sw","KE")`, `ha`, `yo`, `am` → `Locale("am","ET")`, etc.; `isAvailable(tts, code)`.  
`BiasharaTtsEngine`: init `TextToSpeech`, `UtteranceProgressListener`, `MutableStateFlow` for speaking, `speak()` / `stop()` / `release()`, `sanitiseForSpeech` (markdown, `KSh` → Shilingi, `₦` → Naira, newlines → pauses; extend for ETB → Birr as needed).  
Pre-warm optional silent utterance. **Do not block main thread** on `getSettingsSync` — use established async settings access.

---

## V6 — Reusable voice UI

Custom views under **`com.biasharaai.voice`**:

- **MicrophoneButtonView** — IDLE / LISTENING / PROCESSING / ERROR.  
- **TranscriptionBannerView** — partial text, dismiss, auto-hide on final.  
- **SpeakerButtonView** — bind text + language; observes TTS speaking state.

Layouts: `view_microphone_button.xml`, `view_transcription_banner.xml`, `view_speaker_button.xml`.

---

## V7 — Agent feed read-aloud

- `item_agent_action_card.xml`: `SpeakerButtonView`.  
- `AgentActionCardAdapter`: bind headline + detail + language.  
- `AgentFeedFragment`: optional **CRITICAL** auto-read when foreground + `ttsAutoReadAgentAlerts` + **1s delay**; weekly review “Listen” uses `QUEUE_ADD`.

---

## V8 — Conversational query voice

Wire full loop: mic → transcribe → `VoiceRouter` → query path → answer → optional TTS.  
Handbook names **Insights** + **ConversationalQueryViewModel** — **map to `ChatFragment` / `ChatViewModel`** (or add dedicated query bar to `CashFlowInsightsFragment` if product prefers).  
POS search mic: **data-entry only** (filter grid, no Gemma).  
AddEditProduct: per-field mic → data-entry only.  
Bot bubbles: inline speaker for replay.

Latency target (handbook): e.g. &lt;12s on FULL_AI for short query (device-dependent).

---

## V9 — Voice settings + tests

**`VoiceSettingsFragment`:** master toggles, Whisper model/size, silence timeout, language mode, Whisper download CTA, TTS rate/pitch, auto-read policies, “test voice”, per-language TTS install status + `ACTION_INSTALL_TTS_DATA`.

**Tests (examples):** `AudioCaptureHelper` / `SilenceDetector` / mocked Whisper / `VoiceRouter` / mocked TTS / `TtsLanguageMapper`; optional on-device full-loop recording test.

**Acceptance criteria (summary):** STT partial latency, Swahili/Hausa WER targets, fallback &lt;200ms when Whisper unavailable, TTS first audio &lt;500ms, full-loop ceilings, currency sanitisation audibly sane, no crash without voice pack, POS voice filters without Gemma.

---

## Final voice layer handoff (target state after V9)

| Item | Target |
|------|--------|
| Last completed | **V9** — Voice layer complete |
| Room | **21** (after **20→21** migration in this repo) |
| New `app_settings` columns | 10 voice/TTS preference columns (as V0) |
| Primary STT | WhisperKit (Argmax) |
| Fallback STT | Gemma multimodal audio (FULL_AI, yo/am) + `SpeechRecognizer` |
| TTS | Android `TextToSpeech` |
| Voice input surfaces | Chat (primary Q&A), POS search, AddEdit product fields |
| TTS surfaces | Agent feed cards, chat answers, any `SpeakerButtonView` host |

**Phase 7 candidates:** wake word, background voice, kiosk mode, voice expense notes.

---

*End of handbook v1.0 — amend this file when prompts V0–V9 are implemented or scope changes.*
