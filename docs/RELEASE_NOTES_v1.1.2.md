# BiasharaAI 1.1.2 — Release Notes

**Build:** `versionCode = 14`, `versionName = "1.1.2"`
**Release date:** May 11, 2026
**Min Android:** 8.0 (API 26) · **Target:** Android 15 (API 35)

This release is the first build on the **LiteRT-LM** on-device LLM runtime and
the first build on the **Kotlin 2.2.21 / Hilt 2.57.2 / Room 2.8.4** toolchain.
It also lands a set of UX upgrades borrowed from Google AI Edge Gallery
(streaming chat, suggested-prompt chips, "New chat", on-device benchmarks) and
an on-device product-label OCR scanner.

---

## 1. At a glance

| Area | Change |
|---|---|
| **LLM runtime** | Migrated from `com.google.mediapipe:tasks-genai:0.10.33` to `com.google.ai.edge.litertlm:litertlm-android:0.11.0` |
| **Kotlin** | 2.0.21 → **2.2.21** |
| **KSP** | 2.0.21-1.0.28 → **2.2.21-2.0.4** |
| **Hilt** | 2.52 → **2.57.2** |
| **Room** | 2.6.1 → **2.8.4** |
| **AGP** | 8.8.2 (unchanged) |
| **Chat UX** | Streaming responses, cancel, new-chat, suggested prompts, voice input |
| **Inventory** | New on-device OCR scanner for product labels |
| **Settings** | "Run benchmark" button; live capability tier |
| **versionCode/Name** | 11 → **14** / "1.0.10" → **"1.1.2"** |

---

## 2. Why we migrated the LLM runtime

The model we ship (`gemma-4-E2B-it.litertlm`, ~2.5 GB) is in the **LiteRT-LM**
file format. Earlier releases loaded it through the **MediaPipe `tasks-genai`**
runtime — a format/runtime mismatch that produced three reproducible failure
modes:

1. **Control-token leaks** — `<start_of_turn>`, `</start_of_turn>`,
   `<end_of_turn>` rendered verbatim in chat bubbles because the runtime never
   stripped the chat template tokens.
2. **Hallucinated extra turns** — the model "continued" the conversation by
   inventing fake `user:` / `assistant:` exchanges, since the EOS token wasn't
   being honored.
3. **Degenerate decoding loops** — outputs like `"29. 29. 29. 29. ..."`
   because sampler config and stop conditions weren't applied correctly to a
   `.litertlm` file.

We worked around it for several releases (regex sanitizers, stop-marker
detection, repetition heuristics in `GemmaService`). With Google's reference
implementation in [`google-ai-edge/gallery`](https://github.com/google-ai-edge/gallery)
available, the principled fix was to swap to the correct runtime.

### LiteRT-LM in three sentences

LiteRT-LM is the on-device runtime that backs Google AI Edge Gallery. It
applies the model's chat template internally, stops cleanly on the model's
real EOS token, and exposes a stable `Engine` + `Conversation` API that
preserves the KV cache across turns. The host app passes plain user text
through `Contents.of(Content.Text(prompt))` and receives streamed tokens via
`MessageCallback.partialResult(...)`.

### What's gone after the migration

- All manual regex sanitization in `GemmaService` (`SPECIAL_TOKEN_REGEX`,
  `STOP_MARKERS`, `trimAtStopMarker`, `detectRepetitionPrefix`).
- All chat-template wrapping in `ChatViewModel`, `CashFlowAnalyzer`,
  `DemandForecaster`, and `SettingsViewModel`. Plain text in, response out.
- The `buildConversationHistory()` / `collapseHistoryToSingleLine()` helpers —
  history now lives in the LiteRT-LM `Conversation`.

### What replaced it

- `GemmaService.ensureEngineOnGateThread()` — builds the `Engine` once and
  reuses it.
- `GemmaService.ensureConversationOnGateThread()` — creates a `Conversation`
  with the system prompt baked into turn 0 and a `SamplerConfig` derived from
  `InferenceSettingsStore`.
- `GemmaService.generateStreaming(prompt, onChunk)` — coroutine wrapper around
  `conversation.sendMessageAsync(...)` that streams chunks and resolves when
  `done = true`. Cancellation propagates to `conversation.cancelProcess()`.
- Concurrency: `generationMutex` plus a single-thread executor still serialize
  all native calls.

---

## 3. Toolchain upgrade

The migration forced two related upgrades:

### Kotlin 2.0.21 → 2.2.21

LiteRT-LM 0.11.0 ships with Kotlin metadata version 2.3.0. Kotlin 2.0.x only
reads metadata up to 2.0, which produced

```
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.3.0, expected version is 2.0.0.
```

Kotlin 2.2.21 reads it (with the `-Xskip-metadata-version-check` flag set in
`app/build.gradle.kts` to tolerate the half-step gap), and matches the Kotlin
the LiteRT-LM transitive `kotlin-stdlib` is built against.

### Room 2.6.1 → 2.8.4

Kotlin 2.2.x emits a different JVM signature (`V` for void) for `@Delete` /
`@Insert` / `@Update` shortcut methods. Room 2.6.1's annotation processor
chokes on it:

```
java.lang.IllegalStateException: unexpected jvm signature V
  at androidx.room.compiler.processing.javac.kotlin.JvmDescriptorUtilsKt
       .typeNameFromJvmSignature
  at androidx.room.processor.ShortcutMethodProcessor.extractReturnType
  at androidx.room.processor.DeletionMethodProcessor.process
```

Room 2.8.4 (Nov 2025) is the first stable that handles the new descriptor. No
DAO/Entity source changes were required.

### Hilt 2.52 → 2.57.2

Matches Edge Gallery's setup and is the first Hilt release that fully supports
Kotlin 2.2.x KSP processing.

---

## 4. Chat improvements (inspired by Edge Gallery)

`fragment_chat.xml`, `ChatFragment.kt`, `ChatViewModel.kt`, and
`menu_chat.xml` were extended with:

- **`MaterialToolbar` + `action_new_chat`** — resets `Conversation` (KV cache)
  and clears the message list.
- **Suggested-prompt chips** — a `ChipGroup` in the empty state with starter
  questions (`"Do I have any low stock?"`, `"What were my sales today?"`).
  Tapping a chip drops the text into the input bar.
- **Voice input** — a `btn_mic` next to the send button that launches
  `SpeechRecognizer` via `VoiceInputProcessor`. The mic is dual-path-ready
  (multimodal AI fallback exists but is stubbed pending audio modality in
  LiteRT-LM).
- **Cancellation** — the send button becomes a stop button while a response is
  streaming; tapping it cancels `generateStreaming` and the response stops
  mid-token cleanly.
- **Streaming render** — each partial token chunk patches the same chat
  bubble; no flicker, no re-layout per token.
- **Smarter fallbacks** — when the LLM is unavailable, `generateFallbackResponse`
  now answers specific product lookups (`"Do we have milo?"`), sales summaries,
  and gives a business overview instead of the old "Try asking about one of
  these…" message.

---

## 5. On-device product-label OCR

A new `LabelScannerFragment` mirrors `BarcodeScannerFragment` but uses ML Kit
**Text Recognition** instead of barcode scanning.

- `TextAnalyzer.kt` — CameraX `ImageAnalysis.Analyzer` running
  `TextRecognition.getClient(...)`, picking the largest stable text block.
- `AddEditProductFragment` — new **"Scan label"** button that navigates to
  `labelScannerFragment` and observes its `savedStateHandle` result key
  `scanned_label_text`. The first non-empty line is pre-filled into the
  product-name field.
- `fragment_label_scanner.xml` — viewfinder overlay sized for text rather than
  barcodes.

All processing is on-device. Nothing leaves the phone.

---

## 6. Settings — "Run benchmark"

The Settings → Configurations card now includes **Run benchmark** alongside
**Open configurations**. The benchmark:

1. Calls `viewModel.runBenchmark()`, which sends a fixed prompt through
   `GemmaService.generateStreaming(...)`.
2. Reports **first-token latency** and **sustained tokens/sec**.

The card is shown whenever the device is AI-capable, even before the model is
downloaded — previously it was hidden until the model file existed, which made
the settings feel broken on a fresh install.

---

## 7. Engineering details worth knowing

### KV-cache reuse + warm-up

`GemmaService.warmUp()` is now invoked from `ChatViewModel.init` and from the
Settings screen. It eagerly builds the `Engine` and `Conversation` on the
single-thread executor so the first user message doesn't pay the cold-start
cost (~1.5–2 s on mid-range hardware).

### Concurrency contract

- A `Mutex` serializes `generateStreaming` calls so concurrent UI callers
  can't crash the runtime with `Previous invocation still processing`.
- All engine state changes happen on a single-thread `ExecutorService` so the
  native runtime never sees parallel writes.
- `cancelGeneration()` calls `conversation.cancelProcess()` and returns
  immediately; the streaming coroutine resolves with whatever was emitted so
  far.

### Settings → engine mapping (unit-tested)

`InferenceRuntimeSpec.resolve(tier, cfg)` produces a `Resolved` struct that
becomes:

- `EngineConfig.maxTokens` — tier-clamped (`FULL` 2000–4000, `PARTIAL` 512–2048,
  `RULES` falls back to 512).
- `SamplerConfig.topK` (5–64), `topP` (0.0–0.95), `temperature` (0.0–1.0).
- `Backend.CPU` if `preferCpu`, otherwise GPU (with CPU fallback if GPU init
  fails).

Tests in `InferenceRuntimeSpecTest.kt` cover every tier × every boundary.

### Build flags added

```kotlin
kotlinOptions {
    jvmTarget = "17"
    // LiteRT-LM 0.11.0 ships with Kotlin metadata 2.3.0 while our compiler is
    // on the 2.2.x line. The class layout is still readable, but the metadata
    // version guard refuses to load it by default. This flag relaxes it.
    freeCompilerArgs += "-Xskip-metadata-version-check"
}
```

---

## 8. Repository hygiene

The `.gitignore` was hardened in this release to exclude:

- All `*.log` files (e.g. `ksp_error.log`, `build-environment.log`)
- Signing material (`*.jks`, `*.keystore`, `keystore.properties`)
- On-device model files (`*.litertlm`, `*.task`, `*.tflite`, `*.bin`, `*.onnx`)
- The personal resume PDF that previously lived in the project folder

The model file is downloaded at runtime to
`getFilesDir()/models/gemma-4-E2B-it.litertlm` and never enters version control.

---

## 9. Upgrade checklist for contributors

If you're pulling this release into an existing local checkout:

1. **Stop the Gradle daemon** — `./gradlew --stop`. A stale daemon will keep
   the old Kotlin 2.0 compiler in memory and produce confusing errors.
2. **Wipe KSP-generated directories** to be safe:
   ```powershell
   Remove-Item -Recurse -Force app\build\generated\ksp -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force app\build\tmp\kapt3       -ErrorAction SilentlyContinue
   Remove-Item -Recurse -Force app\build\tmp\kotlin-classes -ErrorAction SilentlyContinue
   ```
3. **Rebuild fresh** — `./gradlew clean :app:assembleDebug`. Expect 3–6 minutes
   on the first build while Gradle downloads the new toolchain.
4. **Re-download the model** if you previously used the `tasks-genai` build —
   the file format is the same, but `Settings → Delete → Re-download` ensures
   a clean state.

---

## 10. Known limitations

- **Multimodal voice (Gemma 3n audio path) is stubbed.** `VoiceInputProcessor`
  always uses Android `SpeechRecognizer` because LiteRT-LM 0.11.0 doesn't yet
  expose audio modality on Android. The dual-path code is in place for when
  it does.
- **No Firebase sync.** Optional cloud sync is intentionally not shipped.
- **First-build cost is high.** The Kotlin 2.0 → 2.2 jump means the first
  build after upgrade is slow. Subsequent incremental builds are fast.

---

## 11. Versions referenced in this release

| Component | Version |
|---|---|
| AGP | 8.8.2 |
| Gradle wrapper | 8.14.3 |
| Kotlin | 2.2.21 |
| KSP | 2.2.21-2.0.4 |
| Hilt | 2.57.2 |
| Room | 2.8.4 |
| Navigation | 2.8.5 |
| Lifecycle | 2.8.7 |
| Material | 1.12.0 |
| CameraX | 1.4.1 |
| ML Kit Barcode | 17.3.0 |
| ML Kit Text Recognition | 16.0.1 |
| LiteRT-LM | 0.11.0 |
| Coroutines | 1.9.0 |
| MockK | 1.13.13 |
| Turbine | 1.2.0 |

---

## 12. References

- Google AI Edge Gallery — <https://github.com/google-ai-edge/gallery>
- LiteRT-LM Android — `com.google.ai.edge.litertlm:litertlm-android:0.11.0`
- Gemma 4 E2B (LiteRT) — <https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm>
