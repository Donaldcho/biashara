# BiasharaAI

**On-device AI assistant for small and medium businesses in Africa.**

BiasharaAI runs the Gemma family of language models entirely on the device — no
cloud, no account, no per-request fees. It pairs a Kotlin/MVVM Android app with
the same LiteRT-LM runtime that powers
[Google AI Edge Gallery](https://github.com/google-ai-edge/gallery), and adds a
domain layer for inventory, sales, cash-flow insights, demand forecasting, and
multilingual voice/text chat.

| | |
|---|---|
| **Latest release** | `1.1.2` (versionCode `14`) |
| **Min Android** | 8.0 (API 26) |
| **Target Android** | 15 (API 35) |
| **Languages** | English, Swahili, Hausa, Yoruba, Amharic |
| **License** | TBD |

---

## Table of contents

1. [Highlights](#highlights)
2. [Architecture](#architecture)
3. [Tech stack](#tech-stack)
4. [Device capability tiers](#device-capability-tiers)
5. [Project structure](#project-structure)
6. [Getting started](#getting-started)
7. [Configuration (Settings → Configurations)](#configuration-settings--configurations)
8. [Testing](#testing)
9. [Runtime migration: MediaPipe → LiteRT-LM](#runtime-migration-mediapipe--litert-lm)
10. [Performance budget](#performance-budget)
11. [Roadmap](#roadmap)

---

## Highlights

- **100% on-device AI.** Gemma E2B (`.litertlm`, ~2.5 GB) is downloaded once and
  served from the app's private files directory. Inference works offline and
  data never leaves the device.
- **Edge Gallery–style chat.** Streaming token-by-token responses, cancellable
  generation, system instruction, suggested-prompt chips, "New chat" reset,
  voice input via Android `SpeechRecognizer`.
- **Native KV-cache reuse.** A single LiteRT-LM `Engine` + `Conversation` is
  warmed at app start and reused across messages so subsequent turns avoid the
  cold-start cost.
- **Domain features built on the same runtime.**
  - Inventory CRUD with **barcode** scanning (ML Kit) and **product-label OCR**
    (ML Kit Text Recognition).
  - **Cash-flow insights** — Gemma narrates monthly income/expense/net with a
    rules-based fallback.
  - **Demand forecasting** — Gemma predicts next-period sales for products with
    at least 7 data points; rules-based fallback otherwise.
- **Graceful degradation.** Devices with insufficient RAM or storage drop to a
  `RULES_BASED` tier that still answers product/sales/inventory questions from
  the local database.
- **Settings tied to runtime.** Max tokens, top-K, top-P, temperature, and CPU
  preference are wired through `InferenceRuntimeSpec` to the engine's
  `SamplerConfig` on every conversation start — covered by unit tests.

---

## Architecture

```
 ┌────────────────────────────────────────────────────────────────────┐
 │                       Android UI (Single-Activity)                 │
 │      Fragments + ViewModels (LiveData/StateFlow) + Navigation       │
 ├────────────────────────────────────────────────────────────────────┤
 │                            Domain layer                            │
 │  ChatViewModel · DemandForecaster · CashFlowAnalyzer · VoiceInput   │
 ├────────────────────────────────────────────────────────────────────┤
 │                       AI / inference services                      │
 │   GemmaService  ──►  LiteRT-LM (Engine + Conversation + Sampler)   │
 │   DeviceCapabilityChecker   ModelDownloadManager   InferenceSpec   │
 ├────────────────────────────────────────────────────────────────────┤
 │                         Persistence layer                          │
 │  Room: Product, Transaction · DAOs · TransactionRepository (Hilt)  │
 ├────────────────────────────────────────────────────────────────────┤
 │                       Platform & ML Kit                            │
 │  CameraX · ML Kit Barcode · ML Kit Text Recognition · WorkManager  │
 └────────────────────────────────────────────────────────────────────┘
```

- **DI:** Hilt 2.57.2 (`AppModule` binds DAOs, `TransactionRepository`,
  `GemmaService`, `ModelDownloadManager`, `DemandForecaster`,
  `VoiceInputProcessor`, `CashFlowAnalyzer`).
- **Threading:** A single-thread executor in `GemmaService` serializes all
  engine state changes; a `Mutex` serializes generation requests so concurrent
  callers cannot collide on the native runtime.
- **Streaming:** `GemmaService.generateStreaming(prompt, onChunk)` emits partial
  tokens via `MessageCallback.partialResult(...)` and resolves when the engine
  signals `done = true`. Cancellation is wired to `Conversation.cancelProcess()`.

---

## Tech stack

| Layer | Library / version |
|---|---|
| Build | Gradle 8.14.3, AGP **8.8.2**, Kotlin **2.2.21**, KSP **2.2.21-2.0.4** |
| DI | Dagger Hilt **2.57.2** |
| Persistence | Room **2.8.4** |
| Navigation | Navigation **2.8.5** (no Safe Args; `bundleOf()` everywhere) |
| Concurrency | kotlinx-coroutines **1.9.0** |
| UI | Material Components 1.12, ConstraintLayout 2.2 |
| Camera | CameraX **1.4.1** |
| ML | ML Kit Barcode 17.3, ML Kit Text Recognition 16.0 |
| On-device LLM | **LiteRT-LM 0.11.0** (`com.google.ai.edge.litertlm:litertlm-android`) |
| Background work | WorkManager 2.9.0 |
| Tests | JUnit 4, MockK 1.13, Turbine 1.2, coroutines-test 1.9, Room-testing 2.8.4 |

---

## Device capability tiers

`DeviceCapabilityChecker.evaluate(context)` evaluates RAM, API level, and free
storage. The result feeds `InferenceRuntimeSpec` and every consumer of
`CapabilityTier`.

| Tier | Requirements | Behavior |
|---|---|---|
| `FULL_AI` | API ≥ 28 · RAM ≥ 4 GB · free storage ≥ 3.5 GB (or model already on disk) | Full Gemma inference. Max tokens 2000–4000. |
| `PARTIAL_AI` | API ≥ 28 · RAM ≥ 3 GB · free storage ≥ 3.5 GB (or model already on disk) | Same engine, smaller context. Max tokens 512–2048. |
| `RULES_BASED` | Anything below the above | No LLM. All "AI" features fall back to deterministic rules over Room data. |

`Settings → AI Model → Device capability` shows the live tier, total RAM and
detected API level.

---

## Project structure

```
app/src/main/java/com/biasharaai/
├── BiasharaApp.kt                     # @HiltAndroidApp entry point
├── MainActivity.kt                    # Single Activity, NavHostFragment + BottomNav
├── ai/
│   ├── GemmaService.kt                # LiteRT-LM Engine + Conversation wrapper
│   ├── InferenceRuntimeSpec.kt        # UI settings -> engine params resolver (tested)
│   ├── InferenceSettingsStore.kt      # Persisted user prefs for the engine
│   ├── DeviceCapabilityChecker.kt     # FULL_AI / PARTIAL_AI / RULES_BASED tiering
│   ├── ModelDownloadManager.kt        # ~2.5 GB .litertlm download + verify
│   ├── DemandForecaster.kt            # 7+ data point AI forecast w/ rules fallback
│   ├── CashFlowAnalyzer.kt            # Monthly income/expense narrative
│   ├── AudioCaptureHelper.kt          # 16 kHz mono PCM capture (future multimodal)
│   └── VoiceInputProcessor.kt         # SpeechRecognizer-backed mic-to-text
├── data/local/db/
│   ├── AppDatabase.kt                 # Room database, version 2
│   ├── Product.kt / ProductDao.kt
│   ├── Transaction.kt / TransactionDao.kt
│   └── TransactionRepository.kt
├── di/AppModule.kt                    # Hilt bindings
├── locale/LanguagePreferences.kt      # First-run locale picker
└── ui/
    ├── home/        chat/        inventory/        sales/
    ├── insights/    scanner/     settings/         language/
    └── base/                           # BaseFragment, BaseViewModel
```

Other top-level pieces:

```
app/src/main/res/                       # All XML resources, plus values-{sw,ha,yo,am}
app/src/test/                           # JVM unit tests (MockK + Turbine)
app/src/androidTest/                    # Instrumented DAO + ML Kit tests
gradle/                                 # Wrapper
scripts/preflight-build.ps1             # Diagnoses TLS / JDK before Gradle
HANDOFF.md                              # Engineering notes per prompt iteration
docs/RELEASE_NOTES_v1.1.2.md            # Detailed v1.1.2 release notes
```

---

## Getting started

### Prerequisites

- Android Studio Ladybug Feature Drop or newer (any version that ships JBR 17+).
- Java 17 (Gradle picks up the Studio JBR by default).
- Android SDK Platform 35 + Build-Tools.
- For on-device testing: an Android 8.0+ device or emulator with **at least 3 GB
  free** for the model download. 4 GB+ RAM unlocks `FULL_AI`.

### Build

```bash
# Clone
git clone https://github.com/Donaldcho/biashara.git
cd biashara

# Sanity-check the build environment (writes build-environment.log)
./scripts/preflight-build.ps1                 # Windows

# Build the debug APK
./gradlew :app:assembleDebug

# Install on a connected device
./gradlew :app:installDebug
```

The first build after a clean clone takes 3–6 minutes because Gradle is
downloading Hilt 2.57.2, KSP 2.2.21-2.0.4, Kotlin 2.2.21, and the AGP, then
running KSP across all annotation processors.

### Model

The Gemma model is **not committed** to the repo (it's `.gitignore`-d). The app
downloads it on first launch:

- **Source:** `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`
- **Local path:** `getFilesDir()/models/gemma-4-E2B-it.litertlm`
- **Size:** ~2.5 GB
- **Minimum free storage before download:** 3.5 GB

Open **Settings → AI Model → Re-download** to trigger the download manually,
**Delete** to free space, or **Open Configurations** to tune sampler parameters.

---

## Configuration (Settings → Configurations)

The settings screen exposes Edge-Gallery-style controls bound to the engine:

| Setting | Range | Maps to |
|---|---|---|
| Max tokens | 512–4000 (tier-clamped) | `EngineConfig` (engine rebuild) |
| Top-K | 5–64 | `SamplerConfig.topK` (per conversation) |
| Top-P | 0.0–0.95 | `SamplerConfig.topP` |
| Temperature | 0.0–1.0 | `SamplerConfig.temperature` |
| Prefer CPU | bool | Selects `Backend.CPU` over GPU at engine init |
| Run benchmark | — | Runs a fixed prompt and reports first-token latency and tokens/sec |

Saving Configurations triggers `GemmaService.resetEngine()` so changes take
effect on the next chat message without an app restart.

The mapping is unit-tested — see `InferenceRuntimeSpecTest.kt` and
`InferenceSettingsStoreTest.kt`.

---

## Testing

### Unit tests (`src/test/`, JVM)

```bash
./gradlew :app:testDebugUnitTest
```

26 tests across:

- `InferenceRuntimeSpecTest` — every tier × every settings boundary.
- `InferenceSettingsStoreTest` — persistence with an in-memory `SharedPreferences`.
- `DemandForecasterTest` — AI parse paths, insufficient data, rules fallback.
- `CashFlowAnalyzerTest` — rules summary, AI happy path, AI exception fallback.
- `InventoryListViewModelTest`, `CashFlowInsightsViewModelTest` — StateFlow
  emissions via Turbine.

### Instrumented tests (`src/androidTest/`, device/emulator)

```bash
./gradlew :app:connectedDebugAndroidTest
```

13 tests across:

- `ProductDaoTest` — CRUD, ordering, **1k-row query under 50 ms**.
- `TransactionDaoTest` — CRUD, date-range filtering, type storage.
- `BarcodeAnalyzerInstrumentedTest` — **ML Kit scan latency under 500 ms**,
  callback contract.

---

## Runtime migration: MediaPipe → LiteRT-LM

Earlier versions (≤ 1.0.x) used `com.google.mediapipe:tasks-genai:0.10.33`.
That runtime expects `.task` files; loading the project's `.litertlm` model
produced cascading symptoms (control-token leaks, hallucinated turns,
"29. 29. 29." decoding loops). Version 1.1.0+ replaced it with the LiteRT-LM
runtime used by Google AI Edge Gallery, with these consequences:

- Chat templating (`<start_of_turn>` / `<end_of_turn>` markers) is now applied
  by the runtime, not by us.
- All manual output sanitization regex and stop-marker detection in
  `GemmaService` is gone — the EOS token now stops generation correctly.
- `ChatViewModel.buildPrompt` sends plain text only.
- Conversation history is owned by the `Conversation` object — we no longer
  collapse history strings on each call.

See [`docs/RELEASE_NOTES_v1.1.2.md`](docs/RELEASE_NOTES_v1.1.2.md) for the full
diff.

---

## Performance budget

Empirical measurements on a representative Android 13 device (8 GB RAM,
Snapdragon 7-series):

| Metric | Budget | Observed |
|---|---|---|
| Time-to-first-token (warm engine) | ≤ 600 ms | ~400 ms |
| Streaming decode | ≥ 12 tok/s | 15–20 tok/s |
| 1,000-product Room query | < 50 ms | < 30 ms |
| Barcode detection latency | < 500 ms | < 300 ms |

The `Run benchmark` button in Settings reports both first-token latency and
sustained tokens/sec for the current configuration.

---

## Roadmap

- Re-enable multimodal voice (Gemma 3n audio input) when LiteRT-LM exposes
  audio modality on Android.
- Receipt OCR (Phase 2 / F2) — uses `mlkit:text-recognition` to parse photo
  receipts into `Transaction` rows.
- Loss-prevention alerts (Phase 2 / F6) — WorkManager-driven anomaly detection
  on `Transaction` velocity.
- Optional Firebase Firestore sync (deferred — requires `google-services.json`).
