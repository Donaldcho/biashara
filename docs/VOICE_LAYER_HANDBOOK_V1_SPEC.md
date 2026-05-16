# Biashara AI — Voice Layer — Full specification (v1.0)

**Development Handbook v1.0 — May 2026 | Prompts V0–V9**

> *"The owner speaks. The app listens, thinks, and speaks back."*

**Note:** This file is the **full prompt-by-prompt specification**. For **BiasharaAI repository-specific** adjustments (Room version, package names, screen class names), read **`VOICE_LAYER_HANDBOOK_V1.md`** first (Repository alignment section).

---

## Overview

This handbook adds a complete voice layer to Biashara AI. It covers two distinct but complementary capabilities:

| Capability | Direction | Technology | What it enables |
|------------|-----------|------------|-----------------|
| Speech-to-Text (STT) | Owner speaks → app understands | Argmax Pro SDK (Whisper) + Android SpeechRecognizer fallback | Owner asks questions aloud, gives commands, adds notes by voice |
| Text-to-Speech (TTS) | App responds → owner hears | Android TextToSpeech engine | Agent alerts, query answers, and AI responses spoken aloud |

Together they form a complete voice interaction loop: the owner speaks a business question in Swahili, the app transcribes it, Gemma reasons over the data using the Skills Engine, and the answer is both displayed and read aloud — all on-device, fully offline, zero data cost.

---

## Pre-requisite checklist — confirm before Prompt V0

1. Phase 6 complete — Skills Engine, 12 core skills, AgentLoopRunner working.
2. HANDOFF.md shows 'Last Completed: Prompt X12'.
3. AppDatabase at version 11.
4. TranscribeVoiceSkill exists (Phase 6 X7) — this handbook upgrades it.
5. GemmaService migrated to Engine API (Phase 6 X1).

---

## The full voice flow

| Step | Stage | Behaviour |
|------|-------|-----------|
| 1 | Owner speaks | Owner taps the microphone button on any screen. AudioCaptureHelper begins recording from the device microphone. A recording indicator pulse is shown. |
| 2 | Whisper transcribes | Argmax Pro SDK streams audio chunks to the on-device Whisper model (Tiny or Base). Partial transcriptions appear in real time as the owner speaks. Final transcription locked when owner taps stop or 3 seconds of silence detected. |
| 3 | Language normalisation | VoiceInputProcessor normalises the raw transcription: strips filler words, corrects common Swahili/Hausa/Yoruba/Amharic transcription artefacts, detects the language if multilingual mode is on. |
| 4 | Intent routing | VoiceRouter classifies the transcription: is it a query (route to Conversational Query Engine), a command (route to CommandHandler), or a data entry (route to the active EditText field)? |
| 5 | Gemma reasons | If a query: AgentLoopRunner fires with the transcription as input, calls relevant Skills, produces a structured answer. |
| 6 | TTS speaks the answer | BiasharaTtsEngine receives the response text. Applies language-appropriate voice. Speaks the answer aloud while simultaneously displaying it on screen. A speaker icon lets the owner replay or stop at any time. |

---

## Prompt map

| Prompt | Focus | Key Output |
|--------|-------|------------|
| V0 | Dependencies, DB migration, HANDOFF.md | Argmax Pro SDK, updated RECORD_AUDIO flow, DB migration v11→v12 |
| V1 | AudioCaptureHelper v2 | Streaming audio capture with silence detection and chunk callbacks |
| V2 | Argmax Pro SDK integration — WhisperTranscriber | WhisperTranscriber.kt — real-time STT for all 4 languages |
| V3 | VoiceInputProcessor upgrade | Full tier-based routing: Whisper → Gemma 3n → SpeechRecognizer fallback |
| V4 | VoiceRouter — intent classification | VoiceRouter.kt — classifies transcription into query/command/data-entry |
| V5 | BiasharaTtsEngine — text-to-speech | BiasharaTtsEngine.kt with African language voice mapping |
| V6 | Voice UI components | MicrophoneButton, TranscriptionBanner, SpeakerButton — reusable across all screens |
| V7 | Integration — AgentFeed read-aloud | Speaker button on every AgentAction card reads it aloud |
| V8 | Integration — Conversational Query voice | Full voice Q&A: speak question → Gemma answers → TTS reads answer |
| V9 | Voice settings, testing, acceptance criteria | VoiceSettingsFragment + full test suite |

---

## SETUP — Prompt V0: Dependencies, Permissions, and DB Migration

**Goal:** Add Argmax Pro SDK and audio permissions. Migrate DB to track voice preferences.

**Modifies:** `build.gradle.kts`, `AndroidManifest.xml`, `AppDatabase.kt`, `HANDOFF.md`  
**Next Prompt:** V1: AudioCaptureHelper v2

**Instructions:** Read HANDOFF.md. Confirm Phase 6 complete (Prompt X12 done). Then:

Add to `build.gradle.kts` (Module: app):

```kotlin
// Argmax Pro SDK — on-device Whisper speech recognition
// Built on Google LiteRT. Supports Qualcomm, Google Tensor, MediaTek NPUs.
implementation("com.argmaxinc:whisperkit:0.3.3")

// QNN delegates for Qualcomm NPU acceleration (optional but recommended)
implementation("com.qualcomm.qnn:qnn-runtime:2.34.0")
implementation("com.qualcomm.qnn:qnn-litert-delegate:2.34.0")
```

Required JNI packaging config:

```kotlin
android {
    packaging {
        jniLibs { useLegacyPackaging = true }
    }
}
```

**AndroidManifest.xml** — RECORD_AUDIO is already declared. Add:

```xml
<uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS"/>
<!-- Allows app to reduce background noise during recording -->
```

**DB migration v11 → v12** — add voice preferences to `app_settings`:

```kotlin
val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Voice input preferences
        db.execSQL("ALTER TABLE app_settings ADD COLUMN voiceInputEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN whisperModelId TEXT NOT NULL DEFAULT 'whisper-tiny'")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN silenceTimeoutMs INTEGER NOT NULL DEFAULT 2500")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN voiceLanguageMode TEXT NOT NULL DEFAULT 'AUTO'")
        // TTS preferences
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsEnabled INTEGER NOT NULL DEFAULT 1")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsSpeechRate REAL NOT NULL DEFAULT 0.9")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsPitch REAL NOT NULL DEFAULT 1.0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsAutoReadAgentAlerts INTEGER NOT NULL DEFAULT 0")
        db.execSQL("ALTER TABLE app_settings ADD COLUMN ttsAutoReadQueryAnswers INTEGER NOT NULL DEFAULT 1")
    }
}
```

**Handoff V0**

| Field | Value |
|-------|-------|
| Last Completed | V0: Dependencies and Migration |
| Next Prompt | V1: AudioCaptureHelper v2 |
| DB version | 12 |
| New dependency | com.argmaxinc:whisperkit:0.3.3 |
| Files modified | build.gradle.kts, AndroidManifest.xml, AppDatabase.kt, AppSettings.kt, HANDOFF.md |

---

## PHASE V-A — SPEECH TO TEXT

### Prompt V1: AudioCaptureHelper v2 — Streaming Capture with Silence Detection

**Goal:** Replace the static PCM buffer capture with streaming audio chunks and automatic silence detection.

**Modifies:** `AudioCaptureHelper.kt` (v1 from Phase 2 — full rewrite)  
**Creates:** `AudioChunk.kt`, `SilenceDetector.kt`  
**Next Prompt:** V2: WhisperTranscriber

**Instructions:** Read HANDOFF.md. The existing AudioCaptureHelper.kt captures a fixed audio buffer. Replace it with a streaming version that emits audio chunks and detects silence automatically.

```kotlin
// AudioChunk.kt — a slice of PCM audio
data class AudioChunk(
    val pcmData: ShortArray,
    val sampleRate: Int = 16000,  // Whisper requires 16kHz mono
    val isFinal: Boolean = false   // true = silence detected, stop recording
)

// SilenceDetector.kt — detects when the owner has stopped speaking
class SilenceDetector(private val silenceThresholdDb: Float = -40f,
                      private val silenceTimeoutMs: Long = 2500) {
    private var lastNonSilentAt = System.currentTimeMillis()

    // Returns true when silence has persisted for silenceTimeoutMs
    fun process(chunk: ShortArray): Boolean {
        val rms = sqrt(chunk.map { it.toDouble().pow(2) }.average()).toFloat()
        val db = if (rms > 0) 20 * log10(rms / Short.MAX_VALUE) else -100f
        if (db > silenceThresholdDb) lastNonSilentAt = System.currentTimeMillis()
        return System.currentTimeMillis() - lastNonSilentAt > silenceTimeoutMs
    }
    fun reset() { lastNonSilentAt = System.currentTimeMillis() }
}

// AudioCaptureHelper v2
@Singleton
class AudioCaptureHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: AppSettingsDao
) {
    private var audioRecord: AudioRecord? = null
    private val SAMPLE_RATE = 16000
    private val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private val FORMAT   = AudioFormat.ENCODING_PCM_16BIT
    private val CHUNK_MS = 100  // emit a chunk every 100ms

    // Emits AudioChunks until silence detected or stopRecording() called
    fun startRecording(): Flow<AudioChunk> = channelFlow {
        val settings = settingsDao.getSettingsSync()
        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)
        val silenceDetector = SilenceDetector(silenceTimeoutMs = settings.silenceTimeoutMs)
        audioRecord = AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,
            SAMPLE_RATE, CHANNEL, FORMAT, bufSize).also { it.startRecording() }

        val chunkSize = SAMPLE_RATE * CHUNK_MS / 1000
        val buffer = ShortArray(chunkSize)

        while (isActive) {
            val read = audioRecord!!.read(buffer, 0, chunkSize)
            if (read > 0) {
                val isSilent = silenceDetector.process(buffer.copyOf(read))
                send(AudioChunk(buffer.copyOf(read), SAMPLE_RATE, isSilent))
                if (isSilent) break
            }
        }
        audioRecord?.stop()
    }.flowOn(Dispatchers.IO)

    fun stopRecording() { audioRecord?.stop() }
}
```

**Handoff V1**

| Field | Value |
|-------|-------|
| Last Completed | V1: AudioCaptureHelper v2 |
| Next Prompt | V2: WhisperTranscriber |
| Files modified | AudioCaptureHelper.kt (full rewrite) |
| Files created | AudioChunk.kt, SilenceDetector.kt |
| Key note | 16kHz mono PCM — required by Whisper. Silence auto-stops recording after configurable timeout. |

---

### Prompt V2: WhisperTranscriber — Argmax Pro SDK Integration

**Goal:** Real-time speech-to-text using Argmax Pro SDK (Whisper) with African language support.

**Creates:** `WhisperTranscriber.kt`, `WhisperModelManager.kt`, `TranscriptionResult.kt`  
**Next Prompt:** V3: VoiceInputProcessor upgrade

**Language accuracy (WER guidance):** Swahili: Good (10–25%). Hausa: Good (10–25%). Yoruba: Moderate (25–50%). Amharic: Moderate (25–50%). For Yoruba and Amharic, Gemma 3n audio fallback on FULL_AI devices gives better results. VoiceInputProcessor handles routing.

```kotlin
// TranscriptionResult.kt
data class TranscriptionResult(
    val text: String,
    val language: String,        // detected language code e.g. 'sw', 'ha'
    val confidence: Float,       // 0.0 - 1.0
    val isPartial: Boolean,      // true = still transcribing
    val engine: TranscriptionEngine  // WHISPER | GEMMA_3N | SPEECH_RECOGNIZER
)

enum class TranscriptionEngine { WHISPER, GEMMA_3N, SPEECH_RECOGNIZER }

// WhisperTranscriber.kt (sketch — align with actual WhisperKit Android API)
@Singleton
class WhisperTranscriber @Inject constructor(
    @ApplicationContext private val context: Context,
    private val whisperModelManager: WhisperModelManager,
    private val agentMutex: AgentMutex  // WhisperKit shares LiteRT — must serialise
) {
    private var whisperKit: WhisperKit? = null

    @OptIn(ExperimentalWhisperKit::class)
    suspend fun initialize(modelId: String = "whisper-tiny") {
        whisperKit = WhisperKit.Builder()
            .setModel(if (modelId == "whisper-base")
                WhisperKit.OPENAI_BASE else WhisperKit.OPENAI_TINY)
            .setApplicationContext(context)
            .build()
    }

    @OptIn(ExperimentalWhisperKit::class)
    fun transcribeStream(
        audioChunks: Flow<AudioChunk>,
        languageHint: String? = null
    ): Flow<TranscriptionResult> = flow {
        val kit = whisperKit ?: throw IllegalStateException("WhisperKit not initialised")
        val buffer = mutableListOf<Short>()

        audioChunks.collect { chunk ->
            buffer.addAll(chunk.pcmData.toList())

            if (buffer.size >= 8000 || chunk.isFinal) {
                val pcm = buffer.toShortArray()
                agentMutex.mutex.withLock {
                    kit.transcribe(pcm,
                        language = languageHint,
                        callback = { result ->
                            // called with partial results
                        }
                    )
                }
                emit(TranscriptionResult(
                    text = kit.lastTranscription(),
                    language = kit.detectedLanguage() ?: languageHint ?: "sw",
                    confidence = kit.confidence(),
                    isPartial = !chunk.isFinal,
                    engine = TranscriptionEngine.WHISPER
                ))
                if (chunk.isFinal) buffer.clear()
            }
        }
    }

    fun isAvailable() = whisperKit != null
    fun release() { whisperKit = null }
}
```

**WhisperModelManager:** downloads; Tiny ~75MB, Base ~145MB; `https://huggingface.co/argmaxinc/whisperkit-coreml`; store under `getFilesDir()/whisper/{modelId}/`; reuse Phase 6 download patterns where appropriate.

**Handoff V2**

| Field | Value |
|-------|-------|
| Last Completed | V2: WhisperTranscriber |
| Next Prompt | V3: VoiceInputProcessor upgrade |
| Files created | WhisperTranscriber.kt, WhisperModelManager.kt, TranscriptionResult.kt |
| Key note | AgentMutex for WhisperKit / LiteRT serialisation |

---

### Prompt V3: VoiceInputProcessor Upgrade — Full Tier-Based Routing

**Goal:** Single entry point for all voice input. Routes to the best available engine based on device tier, language, and context.

**Modifies:** `VoiceInputProcessor.kt`  
**Next Prompt:** V4: VoiceRouter

```kotlin
// VoiceInputProcessor.kt — upgraded (sketch)
@Singleton
class VoiceInputProcessor @Inject constructor(
    private val whisperTranscriber: WhisperTranscriber,
    private val audioCaptureHelper: AudioCaptureHelper,
    private val activeModelStore: ActiveModelStore,
    private val capabilityChecker: DeviceCapabilityChecker,
    private val settingsDao: AppSettingsDao
) {
    fun startListening(languageHint: String? = null): Flow<VoiceInputEvent> = flow {
        emit(VoiceInputEvent.Listening)
        val settings = settingsDao.getSettingsSync()
        val lang = languageHint ?: settings.voiceLanguageMode.let {
            if (it == "AUTO") null else it
        }

        val engine = selectEngine(lang, capabilityChecker.getTier())
        emit(VoiceInputEvent.EngineSelected(engine))

        val audioFlow = audioCaptureHelper.startRecording()

        when (engine) {
            Engine.WHISPER -> {
                whisperTranscriber.transcribeStream(audioFlow, lang).collect { result ->
                    if (result.isPartial) emit(VoiceInputEvent.PartialTranscription(result.text))
                    else emit(VoiceInputEvent.FinalTranscription(result))
                }
            }
            Engine.GEMMA_3N -> {
                val fullBuffer = mutableListOf<Short>()
                audioFlow.collect { chunk ->
                    fullBuffer.addAll(chunk.pcmData.toList())
                    if (chunk.isFinal) {
                        val result = transcribeWithGemma3n(fullBuffer.toShortArray(), lang)
                        emit(VoiceInputEvent.FinalTranscription(result))
                    }
                }
            }
            Engine.SPEECH_RECOGNIZER -> {
                emit(VoiceInputEvent.UseSpeechRecognizerFallback)
            }
        }
    }

    private fun selectEngine(lang: String?, tier: CapabilityTier): Engine {
        val prefersGemma = lang in listOf("yo", "am")
        return when {
            prefersGemma && tier == CapabilityTier.FULL_AI -> Engine.GEMMA_3N
            whisperTranscriber.isAvailable() -> Engine.WHISPER
            else -> Engine.SPEECH_RECOGNIZER
        }
    }
}

sealed class VoiceInputEvent {
    object Listening : VoiceInputEvent()
    data class EngineSelected(val engine: Engine) : VoiceInputEvent()
    data class PartialTranscription(val text: String) : VoiceInputEvent()
    data class FinalTranscription(val result: TranscriptionResult) : VoiceInputEvent()
    object UseSpeechRecognizerFallback : VoiceInputEvent()
    data class Error(val message: String) : VoiceInputEvent()
}

enum class Engine { WHISPER, GEMMA_3N, SPEECH_RECOGNIZER }
```

---

### Prompt V4: VoiceRouter — Intent Classification

**Goal:** Classify transcription and route to query, command, or data entry.

**Creates:** `VoiceRouter.kt`, `VoiceIntent.kt`, `CommandHandler.kt`  
**Next Prompt:** V5: BiasharaTtsEngine

```kotlin
// VoiceIntent.kt
sealed class VoiceIntent {
    data class Query(val text: String, val language: String) : VoiceIntent()

    sealed class Command : VoiceIntent() {
        data class Navigate(val destination: String) : Command()
        data class OpenPOS(val productHint: String?) : Command()
        data class RecordSale(val productName: String, val qty: Int) : Command()
        object GoHome : Command()
        object OpenInventory : Command()
        object ReadLastAlert : Command()
    }

    data class DataEntry(val text: String) : VoiceIntent()
    data class Unclassified(val rawText: String) : VoiceIntent()
}

// VoiceRouter.kt (sketch — implement parseCommand, RegexOption.IGNORE_CASE, etc.)
@Singleton
class VoiceRouter @Inject constructor(
    private val activeModelStore: ActiveModelStore,
    private val capabilityChecker: DeviceCapabilityChecker
) {
    // queryPatterns, commandPatterns as in handbook...

    suspend fun classify(
        result: TranscriptionResult,
        currentScreen: String,
    ): VoiceIntent {
        val text = result.text.trim()

        if (currentScreen in listOf("AddEditProduct", "KioskCatalogue"))
            return VoiceIntent.DataEntry(text)

        // pattern command, pattern query, then model fallback on PARTIAL_AI+
        // ...
        return VoiceIntent.Unclassified(text)
    }
}
```

**Classification priority:** (1) Screen context for data entry. (2) Pattern matching. (3) FunctionGemma / model classification on PARTIAL_AI+.

---

## PHASE V-B — TEXT TO SPEECH

### Prompt V5: BiasharaTtsEngine — Text-to-Speech with African Language Voices

**Goal:** On-device TTS for agent responses, query answers, alerts.

**Creates:** `BiasharaTtsEngine.kt`, `TtsLanguageMapper.kt`  
**Next Prompt:** V6: Voice UI Components

See handbook for full `TtsLanguageMapper` and `BiasharaTtsEngine` listings (Locale mapping, `sanitiseForSpeech`, KSh → Shilingi, ₦ → Naira, ETB → Birr for Amharic, `UtteranceProgressListener`, `isSpeaking` StateFlow).

**Currency pronunciation:** Extend `sanitiseForSpeech()` per language as specified.

**Handoff V5:** No extra TTS dependency beyond Android SDK. Initialise in `BiasharaApp.onCreate()` when wired.

---

## PHASE V-C — UI INTEGRATION

### Prompt V6: Reusable Voice UI Components

**Creates:** `MicrophoneButtonView.kt`, `TranscriptionBannerView.kt`, `SpeakerButtonView.kt` + XML layouts.

**XML (template uses `com.biashara.voice` — use `com.biasharaai.voice` in this repo):**

```xml
<com.biasharaai.voice.MicrophoneButtonView
    android:id="@+id/micButton"
    android:layout_width="56dp"
    android:layout_height="56dp"
    app:micColor="@color/voice_teal"
    app:showTranscriptionInline="true" />
```

---

### Prompt V7: AgentFeed Read-Aloud Integration

**Modifies:** `AgentActionCardAdapter.kt`, `item_agent_action_card.xml`, `AgentFeedFragment.kt`

- SpeakerButtonView on each card; bind headline + detail + language.
- Optional CRITICAL auto-read when foreground + `ttsAutoReadAgentAlerts` + 1s delay.
- Weekly review: "Listen to review" with `QUEUE_ADD`.

---

### Prompt V8: Conversational Query Voice — Full Voice Q&A Loop

**Modifies (template names):** `InsightsFragment.kt`, `ConversationalQueryViewModel.kt`, `fragment_insights.xml` — **in BiasharaAI map to Chat / insights screens per `VOICE_LAYER_HANDBOOK_V1.md`.**

Also: `PosFragment.kt`, `fragment_pos.xml`, `AddEditProductFragment.kt`, `fragment_add_edit_product.xml`.

**Latency target:** Under ~12 seconds end-to-end on FULL_AI for a short query (device-dependent).

---

## PHASE V-D — SETTINGS AND TESTING

### Prompt V9: Voice Settings Fragment and Full Test Suite

**Creates:** `VoiceSettingsFragment.kt`, `VoiceSettingsViewModel.kt`, `fragment_voice_settings.xml`, tests.

**Voice settings table (summary):**

| Section | Settings | Default |
|---------|----------|---------|
| Voice Input | Master toggle; Whisper Tiny/Base; silence timeout; language mode; Whisper download | ON; Tiny; 2.5s; Auto; button if missing |
| Text-to-Speech | Master toggle; rate 0.6–1.4; pitch 0.8–1.2; auto-read alerts; auto-read answers; test sample | ON; 0.9; 1.0; Off; ON; — |
| Language voices | Per-language availability; Install TTS data intent | — |

**TTS install intent:**

```kotlin
val intent = Intent().apply {
    action = TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA
}
startActivity(intent)
```

**Testing suite (names):** AudioCaptureHelper; SilenceDetector; WhisperTranscriber (mock); VoiceRouter; BiasharaTtsEngine (mock); TtsLanguageMapper; optional on-device full loop.

**Acceptance criteria (summary):**

| Requirement | Acceptance criteria |
|-------------|----------------------|
| STT latency — Whisper Tiny | Partial within ~1.5s after silence; full ~3s |
| STT accuracy — Swahili / Hausa | WER &lt; 25% on 20-sentence sets (device test) |
| STT fallback | SpeechRecognizer within ~200ms if Whisper unavailable |
| TTS latency | First word within ~500ms of `speak()` |
| TTS language | Correct Locale before speak (sw-KE, am-ET, etc.) |
| Full loop | Under ~15s FULL_AI, ~20s PARTIAL_AI |
| Currency | KSh spoken as Shilingi… (listening test) |
| Missing voice | Fallback English, no crash |
| POS voice | Grid filters within ~3s; no Gemma for data-entry path |

---

## Final Voice Layer Handoff (after V9)

| Item | Value |
|------|-------|
| Last Completed | V9 — Voice Layer Complete |
| DB version (template) | 12 — **use next migration from current Room version in BiasharaAI** |
| New columns | 10 voice/TTS preference columns on `app_settings` |
| New dependency | com.argmaxinc:whisperkit:0.3.3 |
| Model downloads | Whisper Tiny (~75MB) or Base (~145MB) |
| STT engines | WhisperKit; Gemma 3n audio (FULL_AI, yo/am); SpeechRecognizer |
| TTS engine | Android TextToSpeech |
| Screens with voice input (template) | InsightsFragment, PosFragment, AddEditProductFragment |
| Screens with TTS output | AgentFeedFragment, InsightsFragment, SpeakerButtonView hosts |
| Phase 7 candidates | Wake word, background voice, kiosk mode, voice expense notes |

---

*End of full specification. Implement against actual SDK Javadoc and Room schema on branch `feat/voice-layer-stt-tts`.*
