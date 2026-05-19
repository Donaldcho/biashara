# Biashara AI Project Handoff Document

## Current Project State
- **Stable Pro release:** **`release/pro-stable-v1.3.0`** — app **v1.3.0** (versionCode **20**). See **`docs/RELEASE_PRO_v1.3.0.md`**.
- **Last shipped (Pro):** Services POS, mixed payments, unified receipts (`PosReceiptAssembler`), business profile (Phase 10), net sales intelligence (`SalesIntelligenceRepository`), Pro agent workers.
- **AppDatabase version:** **33** (latest **32→33** `service_vouchers.source_transaction_id`; **31→32** `business_profile`; **30→31** mixed-sale columns; **28→29** services tables).
- **Phase:** Pro POS + intelligence hardening on top of Phase C cash capture, Phase 7 KB, Phase 6 skills/agents, voice V9.
- **Active git lines:** **`release/pro-stable-v1.3.0`** (Pro stable); legacy **`release/biashara-phase4`**; feature **`feat/voice-layer-stt-tts`**.
- **Key Notes (Phase 4 — A track):**
  - All agents are **additive** — no existing shipped code is deleted; extend schema and features in place.
  - **Phase 4a (A1–A4)** targets **zero Gemma dependency** so agent value applies on **all** device tiers (`RULES_BASED` / `PARTIAL_AI` / `FULL_AI`).
  - **`ActiveModelStore`** calls from agents must wrap inference in **`AgentMutex.mutex.withLock { … }`** (LiteRT-LM is not safe under concurrent calls). UI and non-agent code may still inject the deprecated **`GemmaService`** façade until migrated.
  - Every agent action requires **owner approval** unless **auto-approved** in settings (policy to be defined in A-track prompts).
- **Phase 2 (U1–U10) — shipped (reference):** Intelligence upgrade complete through Prompt U10; conversational query layer, POS, debt/credit, loss alerts, order parser, tests — see sections **Phase 2 closure**, **Room Database**, and **Chat** below for file and schema detail.

**Repository snapshot:** POS + Phase 2 + **Room 33** + Phase 4 agents + Phase 6 X0–X12 + **Voice V0–V9** + **Phase 7 K0–K11** + **Pro services/vouchers** + **Business profile** + **SalesIntelligenceRepository**.

### Pro stable — `release/pro-stable-v1.3.0` (v1.3.0)

| Area | Key paths |
|------|-----------|
| Services POS | `service/`, `ui/pos/Service*`, `ui/inventory/AddEditService*` |
| Unified cart | `pos/cart/PosCartLine.kt`, `CartManager.unifiedLines()`, `CartAdapter` |
| Unified receipt | `pos/receipt/PosReceiptAssembler.kt`, `ReceiptViewModel`, `ReceiptFragment` |
| Mixed payment | `pos/payment/MixedSaleAllocator.kt`, `SaleRepository.commitPosSale` |
| Net analytics | `analytics/SalesIntelligenceRepository.kt`, `WeeklyReviewBuilder`, `QuerySalesSkill` |
| Business profile | `profile/`, `data/local/db/BusinessProfile.kt`, `QueryBusinessProfileSkill` |
| Licence / Pro gate | `licence/EditionManager.kt`, `productline/ProductLineManager` |
| Release notes | `docs/RELEASE_PRO_v1.3.0.md`, `USER_MANUAL.md` |

### Phase C — Cash Movement Capture System (on `feat/voice-layer-stt-tts`)

| Step | Deliverable | Status |
|------|-------------|--------|
| C0 | `MIGRATION_26_27`; `CashMovementEvidence` entity + enums; `CashMovementEvidenceDao`; `StorageWatchdogWorker` | ✅ |
| C1 | `RegexParser`, `ParsedFields`, `ProofTypeDetector` — 25+ unit tests in `RegexParserTest` | ✅ |
| C2 | `CashParser` three-tier orchestrator (Regex → FunctionGemma → Full Gemma; `AgentMutex`) | ✅ |
| C3 | `CashScanViewModel` + `CashScanUiState` (Idle/Scanning/Confirming/Saving/Saved/Error) | ✅ |
| C4 | `CashScanFragment` + `fragment_cash_scan.xml` (CameraX 720p, `ImageCapture`, direction banner) | ✅ |
| C5 | `ConfirmationFragment` + `ConfirmationViewModel` + `fragment_confirmation.xml` (direction chips, 15 categories, confidence bar) | ✅ |
| C6 | `ManualEntryFragment` + `fragment_manual_entry.xml` (no camera, unverified-outflow toast) | ✅ |
| C7 | `CashMovementRepository` — `@Transaction` atomic write of `LedgerEntry` + `CashMovementEvidence`; 50 KB thumbnail cap; duplicate-ref fraud log | ✅ |
| C8 | `CashQuickActionBar` custom view + `view_cash_quick_action_bar.xml` (4 buttons: Scan In/Out green/red; Add In/Out outlines) | ✅ |
| C9 | `SmsImportFragment` + `SmsImportViewModel` + `fragment_sms_import.xml` (clipboard auto-detect, Parse → Confirmation) | ✅ |
| C10 | `EvidenceViewerBottomSheet` + `bottom_sheet_evidence_viewer.xml`; `LedgerEntryAdapter` shows 📄 badge on entries with evidence | ✅ |
| C11 | `CashEvidenceAnomalyDetector` (large unverified outflow; repeated unverified outflows); wired into `FraudSentinelWorker` + `LossAlertWorkerFactory` | ✅ |
| C12 | `RegexParserTest` (25 cases), `StorageWatchdogLogicTest`, `CashEvidenceAnomalyDetectorTest`; nav graph entries + global actions for all 4 cash fragments | ✅ |

### Phase 7 — Knowledge Base & Teaching System **K0–K11** (complete on `feat/voice-layer-stt-tts`)

| Step | Deliverable | Status |
|------|-------------|--------|
| K0 | `MIGRATION_25_26`: 4 KB tables; `EmbeddingEngine`; `VectorMath` | ✅ |
| K1 | `assets/knowledge/{en,sw,fr,ar,pt,hi,zh}/` — 20 topics × 7 languages (140 `.md` files) | ✅ |
| K2 | `KnowledgeChunk`, `TeachingEvent`, `LessonCompletion`, `FeatureMastery` entities + DAOs; `KnowledgeChunkParser`; `KnowledgeIngestor` | ✅ |
| K3 | `KnowledgeRetriever` — semantic (cosine) + keyword fallback; `RetrievedChunk`; `buildContext()` | ✅ |
| K4 | `QueryAppKnowledgeSkill` (id=`query_app_knowledge`); `TeachUserSkill` (id=`teach_user`); `skills_catalogue.json` entries; `SkillModule` wiring | ✅ |
| K5 | `OwnerBehaviourTracker`; `FeatureMasteryRepository`; `TeachingEngine` | ✅ |
| K6 | `MicroLesson` / `LessonStep` / `StepActionType`; `LessonLibrary` (20 features); `LessonRunner` | ✅ |
| K7 | `ContextualHelpEngine` — screen→feature map; `ContextualHelp` data class | ✅ |
| K8 | `OnboardingController` — SharedPrefs-backed 5-step onboarding | ✅ |
| K9 | `KnowledgeManifest`; `KnowledgeOtaManager` (SHA256 verify, ZIP extract, reingest) | ✅ |
| K10 | `LessonFragment` + `LessonViewModel`; `fragment_lesson.xml`; `ContextualHelpBottomSheet`; `bottom_sheet_contextual_help.xml` | ✅ |
| K11 | `KnowledgeChunkParserTest`, `KnowledgeRetrieverTest`, `QueryAppKnowledgeSkillTest`, `TeachUserSkillTest`, `LessonLibraryTest`, `FeatureMasteryRepositoryTest`, `TeachingEngineTest` | ✅ |

### Phase 9 — Prompt **L0** (complete)

| Field | Value |
|-------|-------|
| **Last Completed** | **L0:** `MIGRATION_21_22`, `ledger_entries` + 5 indexes, `cash_counts`, `LedgerEntry` / `LedgerEntryType` / `LedgerDirection`, `LedgerEntryDao` (insert-only + `updateBalance` for sync), `CashCount` / `CashCountDao` |
| **Next Prompt** | **L1:** `LedgerRepository` + `LedgerDescriptionBuilder` |
| **Critical rule** | No `@Update` / `@Delete` on `LedgerEntryDao` except `updateBalance` (L9 sync recompute) |

### Stability & integration (2026-05-16 — merge to `release/biashara-phase4`)

`feat/phase6-model-skills` was branched from **`release/biashara-phase4`** / **`experiment/intelligent-chat-routing`** (same tip `dce857c`). Before merge, the following hardening landed on the feature branch:

| Area | Change |
|------|--------|
| **Navigation** | `nav_graph`: explicit actions from `agentFeedFragment` → insights / add-edit product / receipt / chat; root-level `action_global_open_receipt` for deep links. `AgentFeedFragment` uses these action IDs (avoids `IllegalArgumentException` when navigating from agent feed to peers). |
| **MainActivity** | Receipt intent handling: `post { }` + `runCatching` so `NavHostFragment` is ready. |
| **BiasharaApp** | `modelRegistry.bootstrap()`, `skillRegistry.bootstrap()`, `skillPackManager.bootstrap()` each wrapped in `runCatching` + log (uncaught coroutine failures no longer kill the process). |
| **PosFragment** | Barcode return path observes `currentBackStackEntry?.savedStateHandle` instead of `getBackStackEntry(R.id.posFragment)`. |
| **ChatFragment** | Activity-result callbacks use `_binding?` (mic / speech / skill manifest snackbar) after view teardown. |
| **Models** | `ModelDownloadManager.purgeLegacyModels`: keep all catalogue `.litertlm` files so Gemma 4 + FunctionGemma can coexist. |
| **HF downloads** | Clearer HTTP **403** user message (gated FunctionGemma: accept Gemma license + token scope). |
| **Backup / security** | `backup_rules.xml` + `data_extraction_rules.xml`: exclude `sharedpref` **`huggingface_auth.xml`** from cloud/full backup (HF read token). |
| **Build** | `:app:compileDebugKotlin` succeeds on the feature line. |

### Voice layer — Prompt **V9** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V9:** `VoiceSettingsFragment` / `VoiceSettingsViewModel` / `fragment_voice_settings.xml`; Settings hub card + `action_settingsFragment_to_voiceSettingsFragment`; Whisper + TTS controls; `ACTION_INSTALL_TTS_DATA`; `VoiceSettingsViewModelTest`; dual-write voice opt-in (prefs + Room) |
| **Next Prompt** | **V10** (TBD) |

### Voice layer — Prompt **V8** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V8:** `VoiceNavigator.kt`; `ChatFragment` + `VoiceRouter`/`CommandHandler`/`AppSettingsDao`/`BiasharaTtsEngine`; `item_chat_message.xml` / `ChatAdapter` assistant read-aloud; `fragment_pos.xml` / `PosFragment` voice search (`btn_voice_search`); strings `chat_voice_*`, `pos_voice_search_desc` |
| **Next Prompt** | *(superseded by V9+)* |

### Voice layer — Prompt **V7** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V7:** `item_agent_action_card.xml` / `item_agent_weekly_review_card.xml` + `AgentActionCardAdapter` (`SpeakerButtonView`, `QUEUE_FLUSH` / `QUEUE_ADD`); `AgentFeedViewModel`/`AgentFeedUiState` TTS flags; `AgentFeedFragment` + `BiasharaTtsEngine` CRITICAL auto-read; `SpeakerButtonView` queue mode + `setEnabled`; `agent_feed_listen_review` strings |
| **Next Prompt** | *(superseded by V8+)* |

### Voice layer — Prompt **V6** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V6:** `MicrophoneButtonView.kt`, `TranscriptionBannerView.kt`, `SpeakerButtonView.kt`; `view_microphone_button.xml`, `view_transcription_banner.xml`, `view_speaker_button.xml`; `attrs_voice_ui.xml`; `voice_teal`; `VoiceUiTtsEntryPoint`; `voice_ui_*` strings (5 locales); Chat composer still uses stock mic until V8 wiring |
| **Next Prompt** | *(superseded by V7+)* |

### Voice layer — Prompt **V5** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V5:** `TtsLanguageMapper.kt`, `BiasharaTtsEngine.kt` (`speak` / `stop` / `isSpeaking`, currency + markdown sanitisation), `TtsLanguageMapperTest.kt`; `BiasharaApp` calls `initialize()` |
| **Next Prompt** | *(superseded by V6+)* |

### Voice layer — Prompt **V4** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V4:** `VoiceIntent.kt`, `VoiceScreenContext.kt`, `VoiceNavigationTarget.kt`, `CommandHandler.kt`, `VoiceRouter.kt`, `VoiceRouterTest.kt`; POS phrases → `OpenPOS`; STT → router → `NavController` still to wire |
| **Next Prompt** | *(superseded by V5+)* |

### Voice layer — Prompt **V3** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V3:** `VoiceInputEvents.kt` (`VoiceInputEvent`, `VoiceSttEngine`), `VoiceInputProcessor` (`startListening`, `transcribeWithAi`, `usesOnDeviceAi`); `ChatFragment` + `TranscribeVoiceSkill` updates |
| **Next Prompt** | *(superseded by V4+)* |

### Voice layer — Prompt **V2** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V2:** `voice/WhisperTranscriber.kt`, `voice/WhisperModelManager.kt`, `voice/TranscriptionResult.kt`, `voice/PcmAudioUtil.kt`; `WhisperModelManagerTest` |
| **Next Prompt** | *(superseded by V3+)* |
| **Key note** | App `TranscriptionResult` lives in `com.biasharaai.voice` (SDK has its own `com.argmaxinc.whisperkit.TranscriptionResult`). |

### Voice layer — Prompt **V1** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V1:** `AudioChunk`, `SilenceDetector`, `AudioCaptureHelper` **class** (`startRecording()` `Flow`, `recordForDuration()`, `stopRecording()`), Hilt + `AppSettingsDao` for `silenceTimeoutMs`; `SilenceDetectorTest` |
| **Next Prompt** | *(superseded by V2+)* |
| **Files created** | `ai/AudioChunk.kt`, `ai/SilenceDetector.kt`, `test/.../SilenceDetectorTest.kt` |
| **Files modified** | `ai/AudioCaptureHelper.kt` (object → `@Singleton` class), `HANDOFF.md` |

### Voice layer — Prompt **V0** (complete on `feat/voice-layer-stt-tts`)

| Field | Value |
|-------|-------|
| **Last Completed** | **V0:** `com.argmaxinc:whisperkit`, Qualcomm QNN via **`com.qualcomm.qti`** (`qnn-runtime`, `qnn-litert-delegate`), `packaging.jniLibs.useLegacyPackaging`, `MODIFY_AUDIO_SETTINGS`, **`MIGRATION_20_21`**, `AppSettings` voice/TTS fields, Room **21** |
| **Next Prompt** | *(superseded by V1+)* |
| **Files modified** | `app/build.gradle.kts`, `AndroidManifest.xml`, `AppDatabase.kt`, `AppSettings.kt`, `DatabaseMigrations.kt`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |

| Handoff (H) | |
|---------|---|
| **Last Completed** | Prompt **K0**: Phase 7 setup — `MIGRATION_25_26`, `EmbeddingEngine.kt`, `VectorMath.kt`, `litert:1.2.0`, `assets/models/` |
| **Next Prompt** | Phase 7 **K1** — Knowledge content creation (~400 `.md` files across 7 language directories) |
| **Files created (K0)** | `ai/EmbeddingEngine.kt`, `ai/VectorMath.kt`, `assets/models/.gitkeep` |
| **Files modified (K0)** | `build.gradle.kts` (litert dep), `AppDatabase.kt` (v26), `DatabaseMigrations.kt` (MIGRATION_25_26), `AppDatabaseMigrationTest.kt` (v25→v26 test), `HANDOFF.md` |
| **Last Completed (X12)** | Prompt **X12**: Phase 6 testing suite — model swap, pack security, acceptance criteria |
| **Files created (X12)** | `ai/ModelSwapTest.kt`, `skills/packs/PackSecurityAcceptanceTest.kt`, `agent/Phase6AcceptanceCriteriaTest.kt` |
| **Files created (X11)** | `skills/packs/SkillPackManifest.kt`, `SkillPackVerifier.kt`, `SkillPackManager.kt`, `PackBridgedSkill.kt`, `assets/skill_pack_trust/signing_public.pem`, `SkillPackVerifierTest.kt`, `SkillPackManagerTest.kt`, `SkillPackTestSupport.kt` |
| **Files modified (X11)** | `SkillPackRecordDao.kt`, `SkillDescriptorDao.kt`, `SkillRegistry.kt`, `BiasharaApp.kt`, `HANDOFF.md` |
| **Files created (X10)** | `FunctionGemmaRouter.kt`, `FunctionGemmaRouterTest.kt` |
| **Files modified (X10)** | `models_catalogue.json`, `ModelRegistry.kt`, `ActiveModelStore.kt`, `AgentLoopRunner.kt`, `ModelSettingsViewModel.kt`, `AgentLoopRunnerTest.kt`, `HANDOFF.md` |
| **Files created (X9)** | `AgentSystemPrompts.kt`, `AgentAnomalySkillMapper.kt`, `AgentAnomalySkillMapperTest.kt` |
| **Files modified (X9)** | `AgentLoopRunner.kt`, all Phase 4 `*Worker.kt`, `LossAlertWorker.kt`, `LossAlertWorkerFactory.kt`, `AgentLoopRunnerTest.kt`, `HANDOFF.md` |
| **Files created (X9)** | `AgentSystemPrompts.kt`, `AgentAnomalySkillMapper.kt`, `AgentAnomalySkillMapperTest.kt` |
| **Files modified (X9)** | `AgentLoopRunner.kt`, all Phase 4 `*Worker.kt`, `LossAlertWorker.kt`, `LossAlertWorkerFactory.kt`, `AgentLoopRunnerTest.kt`, `HANDOFF.md` |
| **Files created (X8)** | `AgentLoopRunner.kt`, `AgentLoopResult.kt`, `AgentToolCallRecord.kt`, `BiasharaSkillOpenApiTool.kt`, `SkillToolFactory.kt`, `AgentLoopRunnerTest.kt`, `BiasharaSkillOpenApiToolTest.kt`, `SkillToolFactoryTest.kt` |
| **Files modified (X8)** | `ActiveModelStore.kt`, `ModelLoader.kt`, `HANDOFF.md` |
| **Files created (X8)** | `AgentLoopRunner.kt`, `AgentLoopResult.kt`, `AgentToolCallRecord.kt`, `BiasharaSkillOpenApiTool.kt`, `SkillToolFactory.kt`, `AgentLoopRunnerTest.kt`, `BiasharaSkillOpenApiToolTest.kt`, `SkillToolFactoryTest.kt` |
| **Files modified (X8)** | `ActiveModelStore.kt`, `ModelLoader.kt`, `HANDOFF.md` |
| **Files created (X7)** | `builtin/ExtractReceiptDataSkill.kt`, `TranscribeVoiceSkill.kt`, `DetectAnomalySkill.kt`, `FindCopurchasePairsSkill.kt`, four matching `*Test.kt` |
| **Files modified (X7)** | `skills_catalogue.json`, `SkillModule.kt`, `HANDOFF.md` |
| **Files created (X7)** | `builtin/ExtractReceiptDataSkill.kt`, `TranscribeVoiceSkill.kt`, `DetectAnomalySkill.kt`, `FindCopurchasePairsSkill.kt`, four matching `*Test.kt` |
| **Files modified (X7)** | `skills_catalogue.json`, `SkillModule.kt`, `HANDOFF.md` |
| **Files created (X6)** | `SkillSalesHistory.kt`, `builtin/ForecastDemandSkill.kt`, `SuggestPriceSkill.kt`, `DraftMessageSkill.kt`, `UpdateDataSkill.kt`, `ForecastDemandSkillTest.kt`, `SuggestPriceSkillTest.kt`, `DraftMessageSkillTest.kt`, `UpdateDataSkillTest.kt` |
| **Files modified (X6)** | `skills_catalogue.json`, `SkillArgs.kt`, `SkillModule.kt`, `HANDOFF.md` |
| **Files created (X5)** | `SkillArgs.kt`, `builtin/QuerySalesSkill.kt`, `QueryInventorySkill.kt`, `CalculateProfitSkill.kt`, `QueryCustomersSkill.kt`, `QuerySalesSkillTest.kt` |
| **Files modified (X5)** | `SkillModule.kt`, `SaleLineItemDao.kt`, `ProductDao.kt`, `SkillExecutorTest.kt`, `HANDOFF.md` |
| **Files created (X4)** | `skills_catalogue.json`, `BiasharaSkill.kt`, `SkillResult.kt`, `SkillCatalogue.kt`, `SkillRegistry.kt`, `SkillExecutor.kt`, `builtin/PingSkill.kt`, `di/SkillModule.kt`, `SkillCatalogueTest.kt`, `SkillExecutorTest.kt` |
| **Files modified (X4)** | `SkillDescriptorDao.kt`, `BiasharaApp.kt`, `HANDOFF.md` |
| **Files created (X3)** | `ModelSettingsFragment.kt`, `ModelSettingsViewModel.kt`, `BenchmarkRunner.kt`, `fragment_model_settings.xml`, `item_model_card.xml` |
| **Files modified (X3)** | `ActiveModelStore.kt` (`isForcedCpu()`), `ModelDescriptorDao.kt` (benchmark queries), `SettingsFragment.kt` (nav button), `fragment_settings.xml`, `nav_graph.xml`, `strings.xml` (all 5 locales), `HANDOFF.md` |
| **Files created (X2)** | `assets/models_catalogue.json`, `ModelCatalogue.kt`, `ModelRegistry.kt`, `ModelCatalogueTest.kt` |
| **Files modified (X2)** | `ModelDownloadManager.kt`, `ModelDescriptorDao.kt`, `BiasharaApp.kt`, `SettingsViewModel.kt`, `SettingsFragment.kt`, `HANDOFF.md` |
| **Files created (X0)** | `ModelDescriptor.kt`, `SkillDescriptor.kt`, `SkillPackRecord.kt`, `ModelDescriptorDao.kt`, `SkillDescriptorDao.kt`, `SkillPackRecordDao.kt` |
| **Files modified (X0)** | `AppDatabase.kt`, `DatabaseMigrations.kt`, `AppModule.kt`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Files created (X1)** | `ModelCapability.kt`, `ModelLoader.kt`, `BiasharaConversation.kt`, `ActiveModelStore.kt` |
| **Files modified (X1)** | `GemmaService.kt`, `AppModule.kt`, `AgentMutex.kt`, `LossAlertWorker.kt`, `LossAlertWorkerFactory.kt`, agent workers (`WeeklyReviewWorker`, `PricingAgentWorker`, `CashFlowSentinelWorker`, `CustomerRelationWorker`, `OpportunitySpotterWorker`), `HANDOFF.md` |
| **Last Completed (Phase 4 track)** | Prompt A10: Battery constraints, quiet hours, RunLog UI |
| **Next Prompt (Phase 4 track)** | Prompt A11 — Testing suite *(parallel maintenance; Phase 6 branch may merge A11 later)* |
| **Files modified (A10)** | `AgentOrchestrator.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `AppModule.kt`, `AgentRunLogDao.kt`, `BiasharaApp.kt`, `MainActivity.kt`, `AndroidManifest.xml`, `SettingsFragment.kt`, `fragment_settings.xml`, `nav_graph.xml`, `strings.xml` (all locales), `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Files created (A10)** | `NotificationScheduler.kt`, `PendingNotification.kt`, `PendingNotificationDao.kt`, `AgentSettingsFragment.kt`, `AgentSettingsViewModel.kt`, `AgentSettingsAdapter.kt`, `fragment_agent_settings.xml`, `item_agent_settings_row.xml` |
| **Files modified (A9)** | `AgentActionExecutor.kt`, `AgentFeedViewModel.kt`, `AgentFeedFragment.kt`, `strings.xml`, `HANDOFF.md` |
| **Files created (A9)** | — (executor expanded in place) |
| **Files modified (A7)** | `AgentSetting.kt` (kdoc), `TransactionDao.kt`, `CustomerDao.kt`, `DebtDao.kt`, `SaleLineItemDao.kt`, `AgentActionBuilder.kt`, `AgentOrchestrator.kt`, `LossAlertWorkerFactory.kt`, `AgentActionCardAdapter.kt`, `AgentFeedFragment.kt`, `strings.xml`, `HANDOFF.md` |
| **Files created (A7)** | `WeeklyReviewBuilder.kt`, `WeeklyReviewWorker.kt`, `CoPurchaseAnalyser.kt`, `OpportunitySpotterWorker.kt`, `item_agent_weekly_review_card.xml` |
| **Files modified (A6)** | `DebtDao.kt`, `DebtRepository.kt`, `TransactionDao.kt`, `AgentActionBuilder.kt`, `AgentOrchestrator.kt`, `LossAlertWorkerFactory.kt`, `strings.xml`, `HANDOFF.md` |
| **Files created (A6)** | `CustomerPatternAnalyser.kt`, `CustomerRelationWorker.kt` (real implementation) |
| **Files modified (A5)** | `TransactionDao.kt`, `AgentActionBuilder.kt`, `AgentOrchestrator.kt`, `LossAlertWorkerFactory.kt`, `HANDOFF.md` |
| **Files created (A5)** | `CashFlowSentinelWorker.kt`, `PricingRuleEngine.kt` (reimplemented `PricingAgentWorker.kt`; removed `CashFlowAgentWorker.kt`) |
| **Files created (A4)** | `AgentFeedFragment.kt`, `AgentFeedViewModel.kt`, `AgentActionCardAdapter.kt`, `AgentActionExecutor.kt`, `fragment_agent_feed.xml`, `item_agent_action_card.xml` |
| **Files removed (A4)** | `HomeFragment.kt`, `HomeViewModel.kt`, `fragment_home.xml` (replaced by agent feed) |
| **Files created (A2–A3)** | **A2:** `AgentMutex`, `AgentOrchestrator`, `AgentDecisionEngine`, `AgentActionBuilder`, `AgentTypes`; stub `*Worker` classes. **A3:** `StockGuardianRepository.kt`, `FraudRuleEngine.kt`; real `StockGuardianWorker`, `FraudSentinelWorker`. |
| **Final DB version (Room)** | **21** (`AppDatabase.kt`; **20→21** voice/TTS columns on `app_settings`) |
| **Phase 2 entities (Kotlin, representative)** | `Customer`, `Debt`, `Alert`, chat session/message entities, `AiBusinessMemory`, etc. (see Room section) |
| **Prompt vs. this repo** | **A2** [AgentOrchestrator] schedules workers. **A3** Stock Guardian + Fraud Sentinel. **A4** [AgentFeedFragment]. **A5** [CashFlowSentinelWorker] daily ~[AgentSetting.dailySummaryHour] (±30m flex) + [PricingAgentWorker] daily (staggered +4h); [PricingRuleEngine]; PARTIAL_AI+ uses [ActiveModelStore] inside [AgentMutex]. **A6** [CustomerPatternAnalyser] + [CustomerRelationWorker]: overdue debt SMS (priority) and visit-gap “we miss you” SMS; daily (+2h vs summary hour, ±30m flex); `SEND_SMS` payload (`phone`, `draftMessage`, optional `debtId`); PARTIAL_AI+ Gemma (debt prompt aligned with Phase 2 [DebtReminderViewModel]). **A7** [WeeklyReviewBuilder] + [WeeklyReviewWorker]: last completed ISO week stats → Gemma long prompt → feed card `AUTO_EXECUTE` / `SHOW_REVIEW` + stat chips JSON; [CoPurchaseAnalyser] + [OpportunitySpotterWorker]: top co-purchase pairs (≥3×) → Gemma bundle/shelf ideas; **FULL_AI + model only**; WorkManager **weekly** on [AgentSetting.weeklyReviewDayOfWeek] at [dailySummaryHour] while charging; opportunity run **+30 min** stagger; [AgentOrchestrator] injects [CapabilityTier] to cancel/skip schedules off FULL_AI. **A9** [AgentActionExecutor]: `SEND_SMS` → `smsto:` + `sms_body` (main thread, never auto-send); `UPDATE_PRICE` / payload-bearing `REVIEW_PRICE` → [ProductDao.updateProduct]; `OPEN_SCREEN` → [ExecutionResult.RequiresNavigation] + fragment completes navigation then `EXECUTED`; `SHOW_REVIEW` + `REVIEW_*` acknowledge → `EXECUTED`; [ExecutionResult] surfaces errors / unknown verbs to [AgentFeedViewModel]. **A10** [NotificationScheduler]: quiet hours → [PendingNotification] queue + flush on app resume; WorkManager constraints per agent (battery / charging / `NetworkType.NOT_REQUIRED`); [AgentSettingsFragment] shows last run + expandable 5-run history from [AgentRunLogDao]. **X0** (Phase 6): Room tables `model_descriptors`, `skill_descriptors`, `skill_pack_records` (**19→20**; external handbook “v9→v10” = same migration intent). **X1**: `ModelLoader` + `ActiveModelStore` + deprecated `GemmaService` façade; workers + loss alerts use `sendPrompt`. **X2**: bundled catalogue → Room `model_descriptors`; `ModelRegistry.bootstrap()` on launch; downloads keyed by `modelId`. |

## Phase 4 — Autonomous agent handbook (A0–A11)

Eleven delivery prompts (**A1–A11**) plus **A0** (tracker/setup) build the autonomous agent system on top of the shipped POS + Phase 2 stack. **Phase 4a (A1–A4)** is designed for **no Gemma dependency** so **RULES_BASED** devices get value immediately; **4b+** layers in `GemmaService` where the handbook calls for it.

### Pre-requisite checklist — confirm before Prompt A1

| # | Handbook item | This repository |
|---|----------------|-----------------|
| 1 | AppDatabase at version 7 (POS complete) | **Shipped:** Room **18**. **A1** added **16→17** (agent tables) + **17→18** (`last_stock_check_at`, stock index). External “v7” labels refer to an old baseline, not `AppDatabase.version`. |
| 2 | `SaleLineItem`, `AppSettings` entities exist | **Yes** — plus Phase 2 entities (`Customer`, `Debt`, `Alert`, chat, `AiBusinessMemory`, …). |
| 3 | `SaleRepository.commitSale()` working and `@Transaction` | Use **`SaleRepository.commitPosSale`** (atomic POS sale); **`commitReturn`** for returns. Both use `@Transaction` where applicable. |
| 4 | `HANDOFF.md` shows last completed **P10** | **Yes** — **Current Project State** lists **P10** as last feature prompt; handoff table lists **A0** when the tracker-only A0 pass is done. |
| 5 | `GemmaService` singleton working (for Phase 4b+) | **Yes** for tiers with on-device model; **4a** agents must not **require** Gemma. |

*External handbooks that still say “DB v7” refer to an older POS-era baseline label, not today’s Room `version` field.*

### Phase 4 prompt map

| Prompt | Phase | Feature | Gemma? | New DB? |
|--------|-------|---------|--------|---------|
| **A0** | Setup | `HANDOFF.md` + tracker / dependencies as specified | No | No |
| **A1** | 4a — Data | DB migrations + agent entities / DAOs (**done** — `16→17→18`) | No | **Yes** — `agent_actions`, `agent_settings`, `agent_run_log`; `products.last_stock_check_at` |
| **A2** | 4a — Core | `AgentOrchestrator` + `AgentDecisionEngine` + `AgentActionBuilder` + `AgentMutex` + WorkManager stubs (**done**) | No | No |
| **A3** | 4a — Agents | `StockGuardianAgent` + `FraudSentinel` (real worker logic) (**done**) | No | No |
| **A4** | 4a — UI | `AgentFeedFragment` + agent action cards (**done**) | No | No |
| **A5** | 4b — AI | `CashFlowSentinel` + `PricingAgent` (**done**) | Yes | No |
| **A6** | 4b — AI | `CustomerRelationAgent` (**done** — pattern analyser + worker + `SEND_SMS` actions) | Yes | No |
| **A7** | 4c — AI | `WeeklyReviewAgent` + `OpportunitySpotter` (**done** — FULL_AI-only, charging, mutex) | Yes | No |
| **A8** | 4c — Settings | `AgentSettingsFragment` + per-agent controls | No | No |
| **A9** | 4c — Actions | Action queue execution (**done** — [AgentActionExecutor] verb routing) | No | No |
| **A10** | 4d — Polish | Quiet hours, battery constraints, run log | No | **Yes** — `pending_notifications` |
| **A11** | 4d — Testing | Full test suite + acceptance criteria | No | No |

### Phase 6 — Model registry & skills handbook (X0–X12)

Phase 6 adds a **Model Registry** (swappable LiteRT-LM models), **Skills Engine** (tool-use / `BiasharaSkill`), multi-step **agentic loop**, optional **FunctionGemma** fast path, and **Skill Packs** (signed OTA). It **migrates inference** from the legacy `GemmaService` / LlmInference-style usage to the **Engine / Conversation** API (**X1**). Canonical Room version after **X0** was **20**; the **voice** branch adds **21** (**20→21**: `app_settings` voice/TTS columns).

| Prompt | Focus | Main deliverables |
|--------|-------|-------------------|
| **X0** | Foundation | `model_descriptors`, `skill_descriptors`, `skill_pack_records` + migration **19→20** (**done** on `feat/phase6-model-skills`) |
| **X1** | Engine API | `ModelLoader`, `ActiveModelStore`, `BiasharaConversation`; `GemmaService` deprecated façade; **agents + loss worker** use `ActiveModelStore.sendPrompt` (**done** on `feat/phase6-model-skills`) |
| **X2** | Model registry | `ModelRegistry`, `ModelDownloadManager` v2, `models_catalogue.json` / `ModelCatalogue` (**done** on `feat/phase6-model-skills`) |
| **X3** | Model settings UI | `ModelSettingsFragment`, benchmarks, per-capability assignment (**done** on `feat/phase6-model-skills`) |
| **X4** | Skills core | `BiasharaSkill`, `SkillRegistry`, `SkillExecutor`, `SkillResult` (**done** on `feat/phase6-model-skills`) |
| **X5** | Data skills | `query_sales`, `query_inventory`, `calculate_profit`, `query_customers` (**done** on `feat/phase6-model-skills`) |
| **X6** | AI + action skills | `forecast_demand`, `suggest_price`, `draft_message`, `update_data` (**done**) |
| **X7** | Multimodal skills | `extract_receipt_data`, `transcribe_voice`, `detect_anomaly`, `find_copurchase_pairs` (**done**) |
| **X8** | Tool use loop | `AgentLoopRunner`, `SkillToolFactory`, `BiasharaSkillOpenApiTool`, `ActiveModelStore.runAgentLoop` (**done**) |
| **X9** | Worker upgrade | All 7 Phase 4 workers use agentic loop + system prompts (**done**) |
| **X10** | FunctionGemma | `FunctionGemmaRouter`, latency routing, ephemeral FUNCTION_CALLING engine (**done**) |
| **X11** | Skill packs | `SkillPackManager`, `SkillPackVerifier`, OTA (Enterprise) (**done**) |
| **X12** | Testing | Model swap, skills, loop, pack security, acceptance criteria (**done**) |

### Phase 2 closure (Prompt U10)

- **Offline (manual):** With airplane mode on, confirm pricing (F1), receipt OCR + manual rows (F2), POS customer memory chips (F3), loss alerts / WorkManager (F5), debt / credit tab (F6), and on-device chat/inventory flows remain usable. **F7:** expect **“You are offline…”** when there is no default network, then exit without crash.
- **Tier (manual):** **RULES_BASED:** F4 blocked via existing negotiation dialog; F7 blocked via toast/dialog. **F1:** margin-style rules path without Gemma (`PricingAdvisor`). **F2:** OCR + Gemma unavailable → manual receipt review. **F3:** repeat-purchase chips without Gemma subtitle (`CustomerSuggestionEngine` returns null narrative off FULL_AI).
- **Performance (automated + device):** `Phase2PerformanceAuditTest` encodes budgets for CI; on a physical device, re-run receipt **capture → OCR → Gemma → review** and aim for **under ~15s** total when the model is warm.

### Phase 3 candidates (not scheduled; Phase 4 A-track is active)

- **Note:** Product work is on **Phase 4 — Autonomous Business Agent** (see **Current Project State**). Items below remain **ideas**, not committed prompts.

- **Federated / crowd price intelligence** — privacy-preserving aggregates, opt-in, regional benchmarks.
- **Gemma 3n product photo description** — shelf SKU photo → structured fields + safety filters.
- **Shelf audit by camera** — planogram / gap detection (likely hybrid rules + vision).
- **Micro-loan readiness score** — optional financial-health signal from cashflow + debt history (policy-heavy).

### Git branches (do not merge blindly)

- **Branch map and commands:** [docs/BRANCHES.md](docs/BRANCHES.md) — which branch to checkout, naming rules, and PR-oriented workflow.
- **Stable git line:** remote branch **`release/biashara-phase4`** — long-lived branch for Phase 2 **U**-prompt work and related features; **not** required to merge to `main`. Parent line: **`feat/pos-module`**.
- **Open a PR from this line** (optional, e.g. into another branch): https://github.com/Donaldcho/biashara/pull/new/release/biashara-phase4

### Prior phases (reference)
- **Phase 2 — Intelligence Layer Upgrade:** Features such as loss prevention alerts (U5), receipt OCR (U4), conversational chat layer, and related Room fields shipped under the upgrade track; prefer **additive** follow-ups only.
- **POS / Phase 1–3:** `PosFragment`, cart, payments, returns, end-of-day AI, etc. — treat as **confirmed working** unless a prompt explicitly revisits them.
- **Build / test stack:** `AppDatabaseMigrationTest` and **`MigrationTest`** (Prompt U9) validate **v3→v21** and focused Phase-2 chains; **`CustomerDaoTest`**, **`DebtDaoTest`**, **`AlertDaoTest`**, **`LossAlertEngineTest`**, **`Phase2PerformanceAuditTest`** (instrumented); **`ReceiptParserTest`**, **`OrderParserViewModelTest`** (JVM). **`fallbackToDestructiveMigration()`** is **not** used in `AppModule` — migrations are explicit. Run `.\scripts\preflight-build.ps1` before Gradle builds (see **Prerequisites** below).
- **Libraries (snapshot):** ML Kit (OCR, image labeling for Ask Image), WorkManager, Gson, Room testing, Turbine — see sections below for feature mapping.

## Prerequisites Before Any Gradle / IDE Build
1. Run `.\scripts\preflight-build.ps1` from the repo root. It writes `build-environment.log` (gitignored).
2. If the script reports **PowerShell HTTPS OK** but **Java PKIX / SSLHandshakeException** to `dl.google.com`, Gradle cannot resolve the Android Gradle Plugin until **TLS trust is fixed**.
3. Gradle wrapper uses **8.14.3**. `gradlew.bat` prefers Android Studio **JBR** unless `BIASHARAAI_USE_SYSTEM_JAVA=1`.

## Localization (Prompt 2b — done)
- **Locales:** `values` (English), `values-sw`, `values-ha`, `values-yo`, `values-am`.
- **First launch:** `LanguageSelectionFragment` → choose locale → `agentFeedFragment` (Today tab).
- **Bottom navigation:** Hidden on `languageSelectionFragment`, `barcodeScannerFragment`, `addEditProductFragment`, `paymentDialogFragment`, `supplierNegotiationFragment`, `negotiationGuideFragment`.

## Room Database (Prompt 3 + 9 — done; Prompt P1 — v7 + migrations; chat through v16; Phase 4 agents + Phase 6 X0 — v20)
- **Version 20** — **19→20**: `model_descriptors`, `skill_descriptors`, `skill_pack_records` (Prompt **X0** — Model Registry + Skills Engine persistence). **18→19** (`pending_notifications` + index on `fire_at`); **17→18** (`products.last_stock_check_at`, `index_products_stock_quantity` on `stock_quantity`); **16→17** (`agent_actions`, `agent_settings` singleton row, `agent_run_log`); **15→16** (`chat_session_messages.feedback_vote`); **14→15** (chat sessions/messages); plus earlier POS / Phase 2 chain (**no** `fallbackToDestructiveMigration()` in `AppModule`).
- **`Product` entity**: `id`, `name`, `description?`, `price`, `cost`, `stockQuantity`, `category?`, `barcodeValue?`, `imageUrl?`, **`lastStockCheckAt`** (Prompt A1 — stock guardian clock).
- **Phase 4a — Prompt A1:** `AgentAction`, `AgentSetting`, `AgentRunLog` + `AgentActionDao`, `AgentSettingDao`, `AgentRunLogDao` (Hilt-provided). `transactions` already indexed on `date` and `type`; A1 did **not** add duplicate transaction indexes.
- **Phase 4a — Prompt A2:** `AgentOrchestrator` (WorkManager `enqueueUniquePeriodicWork` / `cancelUniqueWork` per [AgentSetting]), `AgentMutex` (single `Mutex` for all `GemmaService` use from agents), `AgentDecisionEngine` (`isDuplicateAction`, `shouldSuppressNotification`, `buildRunLog`), `AgentActionBuilder` (typed `AgentAction` factories). Stub `CoroutineWorker` classes in `com.biasharaai.agent.workers` — **A3** replaces `doWork` bodies with real detection.
- **`Transaction` entity**: legacy fields `id`, `type`, `amount`, `description`, `date`; **POS (P1):** `paymentMethod`, `mobileMoneyNetwork`, `mobileMoneyRef`, `amountTendered`, `changeDue`, `receiptNumber`, `saleGroupId`, `taxRate`, `taxAmount`. **P8:** `TransactionType.RETURN`, `customerId` (credit sales), `relatedSaleTransactionId` (return rows). **`SaleLineItem`:** `sourceSaleLineItemId` links return lines to original sale lines.
- **`SaleLineItem`**, **`AppSettings`** (singleton row `id = 1`) — Prompt P1.
- **Phase 2 SQL tables (P1 migration 3→5):** `customers`, `debts`, `alerts` — now backed by Kotlin `@Entity` / DAOs (`Customer`, `Debt`, `Alert`); data preserved across upgrades.
- **`ProductDao`**: CRUD + Flow queries; POS helpers `searchProductsByNameOrBarcode`, `getProductsOrderedForPos`, `getProductByBarcode`; **U3:** `CategoryAverages` + `getCategoryAverages`, `sumUnitsSoldForProductInPeriod` (pricing advisor). **`Customer` / `CustomerDao`**: Phase 2 `customers` table (v8 entity). **`Debt` / `DebtDao` / `DebtRepository`**: Phase 2 `debts` table (v9 entity); outstanding sum per customer for POS credit tab. **`TransactionDao`**: `insertTransaction`, `getAllTransactions()`, `getTransactionsByPeriod(start, end)`.
- **`SaleLineItemDao`**, **`AppSettingsDao`** — Prompt P1 (Hilt-provided from `AppModule`).
- **`TransactionRepository`**: Hilt `@Singleton` wrapper around `TransactionDao` (includes `observeCompletedPosSales`, `observeTransactionById`). **`SaleRepository`**: atomic `commitReturn` (RETURN tx, lines, stock, optional `DebtDao.reduceAmount`).

## Inventory Management UI (Prompt 4 — done; Prompt U4 — receipt OCR)
- RecyclerView + **speed-dial** `ExtendedFloatingActionButton` (add manually, scan barcode, scan receipt) + toolbar scan + forecast badges.
- **U4:** `ReceiptScanFragment` (CameraX **ImageCapture** still → cache JPEG → bitmap), `ReceiptParser` (ML Kit Latin OCR + Gemma JSON array → `ReceiptLineItem`), `ReceiptReviewFragment` + `ProductDao.insertAll` bulk add; fallback single empty row + banner when OCR/Gemma/JSON fails.

## Barcode & QR Code Scanning (Prompt 4b — done)
- CameraX + ML Kit, fully offline. `ScanMode`: LOOKUP / ADD / RECORD_SALE.

## Product Entry and Editing (Prompt 5 — done; Prompt U3 — smart price; label scan + Gemma)
- OutlinedBox form with mic + scan end-icons, inline validation, SavedStateHandle.
- **U3:** **Suggest price** `TextButton` when cost is positive and category is non-blank → `PricingSuggestionBottomSheet` (`GemmaService.generateResponse` on **PARTIAL_AI** / **FULL_AI** when model available; else cost×1.3 rules string). Parsed `Suggested price: X` line fills the price field on **Use this price**.
- **Label scan:** `LabelScannerFragment` (ML Kit stable text) → **`LabelProductEnricher`** (Gemma JSON: `description` + `category` when model available) → `SavedStateHandle` keys `scanned_label_text`, `scanned_label_description`, `scanned_label_category`; `AddEditProductFragment` fills name, description, and category. Progress overlay while Gemma runs.

## Device Capability Check and Model Download (Prompt 6 — done)
- `DeviceCapabilityChecker` → `CapabilityTier` (FULL_AI / PARTIAL_AI / RULES_BASED).
- `ModelDownloadManager` → `getFilesDir()/models/gemma3-1b.litertlm`.
- Settings screen with download/delete/retry.

## LiteRT-LM Integration and Gemma Inference (Prompt 7 — done; runtime updated post–1.0)
- `GemmaService`: LiteRT-LM `Engine` + `Conversation`, single-thread executor + `Mutex`, streaming via `MessageCallback`.
- `DemandForecaster`: AI prediction with rules-based fallback.
- API: `com.google.ai.edge.litertlm` (`Engine`, `Conversation`, `SamplerConfig`, `Backend`).

## Local Language Voice Input (Prompt 8 — done)
- `AudioCaptureHelper`: PCM capture (16kHz, mono, 16-bit).
- `VoiceInputProcessor`: WhisperKit when initialised, future Gemma audio on **FULL_AI**, else `SpeechRecognizer`.
- `AddEditProductFragment`: Dual-path mic button, RECORD_AUDIO permission, recording indicator.

## Financial Insights Generator (Prompt 9 — done)
- `Transaction` entity + `TransactionDao` + `TransactionRepository`.
- `CashFlowAnalyzer`: Gemma-powered insights with rules fallback.
- `CashFlowInsightsFragment` + `CashFlowInsightsViewModel`: Chart + AI narrative; **U6:** same screen adds **Credit** tab (debts list, mark paid, SMS reminder draft).

## Testing Suite (Phase 1 baseline + Phase 2 U9/U10)
- **Test Dependencies Added** (`build.gradle.kts`):
  - `io.mockk:mockk:1.13.13` (unit test mocking)
  - `app.cash.turbine:turbine:1.2.0` (StateFlow testing)
  - `kotlinx-coroutines-test:1.9.0` (coroutine testing)
  - `androidx.room:room-testing:2.6.1` (in-memory Room DB)
  - `androidx.test:runner:1.6.2`, `androidx.test:rules:1.6.1`

### Unit Tests (JVM — `src/test/`)
- **`DemandForecasterTest.kt`** (10 tests):
  - AI response parsing: standard format, extra text, spaces around colons.
  - Insufficient data handling (< 7 data points).
  - Rules-based fallback when AI unavailable.
  - AI exception → rules fallback.
  - Unparseable response → raw text return.
- **`CashFlowAnalyzerTest.kt`** (7 tests):
  - Rules-based summary: profit, loss warning, top expense categories.
  - AI path: calls GemmaService, returns AI response.
  - AI exception → rules fallback.
  - Empty transaction list handling.
- **`InventoryListViewModelTest.kt`** (4 tests):
  - Products StateFlow emissions from DAO.
  - Empty list state.
  - Forecast generation for eligible products (stock ≥ threshold).
  - No forecast for zero-stock products.
- **`CashFlowInsightsViewModelTest.kt`** (5 tests):
  - Loading state → not loading after completion.
  - Income/expense/net calculation accuracy.
  - Insights text generation.
  - Period label set.
  - Refresh reloads insights.

### Instrumented Tests (Android — `src/androidTest/`)
- **`ProductDaoTest.kt`** (6 tests):
  - Insert and retrieve. Barcode lookup. Ordering (name ASC). Update. Delete.
  - **Performance**: 1,000 product query < 50ms acceptance criterion.
- **`TransactionDaoTest.kt`** (5 tests):
  - Insert and retrieve. Date ordering. Period filtering (inclusive). Empty range. Type storage.
- **`BarcodeAnalyzerInstrumentedTest.kt`** (2 tests):
  - ML Kit scanner latency < 500ms acceptance criterion.
  - BarcodeAnalyzer callback contract and reset.

### Test Results
- **Unit tests**: 26/26 passing ✅
- **Instrumented tests**: 13 tests, require connected Android device/emulator to run.
- **Build**: `assembleDebug` ✅ BUILD SUCCESSFUL.

### Testing Notes
- All unit tests mock `android.util.Log` via `mockkStatic()` since it's unavailable in JVM.
- ViewModel tests use `UnconfinedTestDispatcher` for `Dispatchers.Main` + `Thread.sleep(500)` to wait for `Dispatchers.IO` coroutines (ViewModels launch on IO for CPU-intensive AI work).
- Room DAO tests use `Room.inMemoryDatabaseBuilder()` with `allowMainThreadQueries()`.

## Hilt Dependency Graph (AppModule)
```
AppDatabase → ProductDao, TransactionDao, SaleLineItemDao, AppSettingsDao, CustomerDao, DebtDao, AlertDao, LossAlertDao
CartManager (singleton, constructor-injected)
CartRepository (singleton, deps: CartManager, AppSettingsDao)
DebtRepository (singleton, dep: DebtDao)
TransactionRepository (singleton, dep: TransactionDao)
SaleRepository (singleton, deps: AppDatabase, TransactionDao, SaleLineItemDao, ProductDao, DebtDao, CustomerDao)
CustomerRepository (singleton, dep: CustomerDao)
CapabilityResult → CapabilityTier
ModelDownloadManager (singleton)
GemmaService (singleton, deps: Context, CapabilityTier, ModelDownloadManager)
DemandForecaster (singleton, dep: GemmaService)
VoiceInputProcessor (singleton, deps: WhisperTranscriber, AudioCaptureHelper, AppSettingsDao, CapabilityTier)
CashFlowAnalyzer (singleton, dep: GemmaService)
PricingAdvisor (singleton, deps: GemmaService, ProductDao, CapabilityTier)
ReceiptParser (singleton, dep: GemmaService)
LabelProductEnricher (singleton, dep: GemmaService)
```

## Key Decisions / Architecture Notes
- Target platform: Android (Kotlin)
- Single-Activity, MVVM, Hilt, Room, Navigation Component
- On-device AI: Gemma 4 E2B via LiteRT-LM (`com.google.ai.edge.litertlm:litertlm-android:0.11.0`)
- Barcode: CameraX + ML Kit (on-device, fully offline)
- Navigation: `bundleOf()`, no Safe Args.
- **Model download**: Gemma 4 E2B (text-only, ~2.58GB) from HuggingFace litert-community (no auth). URL: `https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm`. File saved as `getFilesDir()/models/gemma-4-E2B-it.litertlm`. Min free storage: 3.5GB.
- **AI Chat**: `ChatFragment` in bottom nav (shared `ChatViewModel` with `ChatHistoryFragment`). Multi-session Room storage (`chat_sessions` / `chat_session_messages`), Gallery-style attach-image (ML Kit → text summary), optional Wikipedia augment, maps intent, URL-loaded skill chips. Injects live inventory + financial data into every AI prompt. Falls back to rules-based keyword matching when AI inference fails.
- **AudioModelOptions**: Commented out in `GemmaService.kt` — Gemma 4 E2B LiteRT is text-only.
- **VoiceInputProcessor**: routes **WhisperKit** (`WhisperTranscriber.isAvailable`) → future **Gemma audio** (stub) → **`SpeechRecognizer`**. `usesOnDeviceAi` is true when the resolved engine is not `SPEECH_RECOGNIZER`. `startListening()` emits `VoiceInputEvent` (no `AudioCaptureHelper` start for speech-only fallback).

### Model History (for next agent reference)
1. **Gemma 3n E2B** (multimodal, ~3.4GB) from Google Drive — Too slow on mobile devices.
2. **Gemma 4 E2B** (text-only, ~2.58GB) from HuggingFace — ✅ **Current active model**. Fastest for on-device inference (2.3B effective params via PLE architecture). Best for simple business Q&A tasks.
- **Google Drive download notes** (if reverting to Gemma 3n): Must use `drive.usercontent.google.com` (NOT `drive.google.com/uc`). File ID: `1YIEIATRoOKlnP72BR5y1ZWyPNASh54F3`.
- **To re-enable Gemma multimodal audio**: wire an `ModelCapability.AUDIO` LiteRT model into `ActiveModelStore`, then implement `VoiceInputProcessor.isGemmaAudioAvailable()` + `transcribePcmWithGemma()` (today they are stubs; STT uses WhisperKit or system recognizer).

## Final Handoff Documentation

### Performance Results
- **Room Database Queries**: Added `@Query` indices to `Product` (`category`) and `Transaction` (`date`, `type`). 1,000 product query executes in < 30ms.
- **Gemma Inference Times**: Time to First Token (TTFT) ~400ms on capable hardware. Decode speed ~15-20 tokens/sec.
- **Barcode Detection**: CameraX + ML Kit scanning latency consistently < 300ms.

### Offline Test Results
- **Pass**: All core user flows (Onboarding, Language Selection, Product Add/Edit, Barcode Scan, POS Sale, Voice Input fallback) validated successfully in Airplane Mode. On-device Gemma inference successfully runs 100% offline.

### Sync Strategy
- **Cloud Sync**: Optional Firebase Firestore (encrypted) cloud sync was intentionally **skipped** per user request.

### Final Notes & Limitations
- **Limitations**: `CapabilityTier.RULES_BASED` fallback gracefully disables on-device LLM when devices lack the 3.5GB required storage or RAM; **U3** still offers a rules-based suggested price. **PARTIAL_AI** / **FULL_AI** without a downloaded model also uses the same rules line for pricing.
- **Future Enhancements**: Implement the Firebase Sync strategy when a `google-services.json` config becomes available. The old `InsightsFragment.kt` is deprecated and can be safely deleted.

## Phase 2 — Prompt U0 (HANDOFF + Dependencies)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U0: HANDOFF.md Upgrade and Dependencies |
| **Next Prompt** | Prompt U1: Database Migrations |
| **Dependencies added** | `com.google.mlkit:text-recognition:16.0.1`, `androidx.work:work-runtime-ktx:2.9.0` |
| **Already satisfied (no duplicate lines)** | `androidx.room:room-testing` (instrumented tests, version aligned with `room` in `app/build.gradle.kts`, ≥ 2.6.1), `app.cash.turbine:turbine:1.2.0` (unit + instrumented, ≥ 1.1.0) |
| **Files modified** | `HANDOFF.md`, `app/build.gradle.kts` |

## Phase 2A — Prompt U1 (Database migrations — Customer, Debt, Alert)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U1: Database Migrations (additive on current schema) |
| **Next Prompt** | Prompt U2: Customer Memory and Suggestions |
| **DB version** | **11** (`MIGRATION_10_11`: `note_type` on `transactions`; **no** `fallbackToDestructiveMigration`) |
| **Entities / DAOs** | [Alert] + `AlertDao` (maps to `alerts` from **3→5**); `CustomerDao` / `DebtDao` extended per U1 API (`searchByName`, `getCustomerByIdFlow`, update/delete; `getUnpaidDebts`, `getDebtsByCustomer`, `getTotalOutstanding`, `markPaid`). |
| **Files created** | `Alert.kt`, `AlertDao.kt` |
| **Files modified** | `AppDatabase.kt`, `AppModule.kt`, `CustomerDao.kt`, `DebtDao.kt`, `Transaction.kt`, `DatabaseMigrations.kt`, `AppDatabaseMigrationTest.kt` |
| **Tests** | `AppDatabaseMigrationTest` — U1 era validated v3→v11 (`note_type`, `alerts`, etc.). **U2** extended the end-to-end test to **v12** and `customers.last_visit` (see U2 row). |

## Phase 2A — Prompt U2 (Customer memory and purchase suggestions)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U2: Customer Memory and Purchase Suggestions |
| **Next Prompt** | Prompt U3: Smart Pricing Engine |
| **DB version** | **12** (`MIGRATION_11_12`: `last_visit` on `customers` when missing) |
| **Behavior** | POS customer chip opens `CustomerSelectorBottomSheet` (search, last visit, new customer dialog). `CustomerSuggestionEngine` loads top repeat-purchase products (see `SaleLineItemDao.topProductIdsForCustomer`); on **FULL_AI** + model, optional Gemma one-liner in `text_suggestion_subtitle`. Suggestion chips add product at list price and hide when the product is already in cart. `SaleRepository` sets `Transaction.customerId` and `CustomerDao.updateLastVisit` after a successful sale. |
| **Files created** | `CustomerRepository.kt` (`data/local/db`) |
| **Files modified** | `CustomerSelectorBottomSheet.kt`, `fragment_customer_selector.xml`, `PosFragment.kt`, `fragment_pos.xml` (+ land / `sw600dp`), `PosViewModel.kt`, `Customer.kt`, `CustomerDao.kt`, `SaleLineItemDao.kt`, `CustomerSuggestionEngine.kt`, `SaleRepository.kt`, `DatabaseMigrations.kt`, `AppDatabase.kt`, `AppDatabaseMigrationTest.kt`, `strings.xml`, `HANDOFF.md` |
| **Tests** | `AppDatabaseMigrationTest.migrate3To21_preservesProductsAndTransactionsAndSeedsSettings` — v3→v21; `PRAGMA table_info(customers)` includes `last_visit`. |

## Phase 2A — Prompt U3 (Smart pricing engine)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U3: Smart Pricing Engine |
| **Next Prompt** | Prompt U4: Receipt and Invoice OCR |
| **DB version** | **12** (no new migration; read-only queries on `products` / `sale_line_items` / `transactions`) |
| **Behavior** | `PricingAdvisor.suggestPrice` uses `ProductDao.getCategoryAverages` + monthly units sold + `GemmaService.generateResponse` when tier is not **RULES_BASED** and the model is available; otherwise `Suggested price: {cost×1.3} (30% margin)`. `PricingSuggestionBottomSheet` parses the first line with `Suggested price:\\s*([\\d.]+)`; on failure, shows full text and disables **Use this price**. |
| **Files created** | `PricingAdvisor.kt`, `PricingSuggestionBottomSheet.kt`, `fragment_pricing_suggestion.xml` |
| **Files modified** | `AddEditProductFragment.kt`, `fragment_add_edit_product.xml`, `AddEditProductViewModel.kt`, `ProductDao.kt` (+ `CategoryAverages`), `strings.xml`, `HANDOFF.md` |

## Phase 2A — Prompt U5 (Loss prevention alerts)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U5: Loss Prevention Alerts |
| **Next Prompt** | Prompt U6: Debt and Credit Tracker |
| **DB version** | **13** (`MIGRATION_12_13`: `alert_type`, `product_id`, `dedupe_key`, `localized_message`, `related_transaction_id` on `alerts`) |
| **Behavior** | `LossAlertEngine` runs four Room-backed detectors (low stock without recent POS lines, 7-day-then-3-day-quiet sales gap, line `unit_price` &lt; 70% of list `price`, high expense day vs trailing 30-day average). `LossAlertWorker` (WorkManager **24h** unique periodic) inserts `Alert` rows with `dedupe_key` skip-if-active. **FULL_AI** + model + non-English locale: Gemma caches translation in `localized_message`. `HomeFragment` shows cards (`AlertCardAdapter`) with Review (inventory / edit product / receipt / insights) and Dismiss. `BiasharaApp` implements `Configuration.Provider` with `LossAlertWorkerFactory`; manifest removes default WorkManager initializer. |
| **Files created** | `LossAlertTypes.kt`, `LossAlertDao.kt`, `LossAlertEngine.kt`, `LossAlertWorker.kt`, `LossAlertWorkerFactory.kt`, `LossAlertScheduler.kt`, `HomeViewModel.kt`, `AlertCardAdapter.kt`, `item_alert_card.xml`, `ic_loss_alert.xml` |
| **Files modified** | `Alert.kt`, `AlertDao.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `AppModule.kt`, `BiasharaApp.kt`, `HomeFragment.kt`, `fragment_home.xml`, `AndroidManifest.xml`, `strings.xml`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Tests** | `AppDatabaseMigrationTest.migrate3To21_preservesProductsAndTransactionsAndSeedsSettings` — v3→v21; `PRAGMA table_info(alerts)` includes U5 columns. |

## Phase 2A — Prompt U6 (Debt and credit tracker)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U6: Debt and Credit Tracker |
| **Next Prompt** | Prompt U7: Supplier Negotiation Assistant |
| **DB version** | **15** (no new migration; uses existing `debts`, `transactions.note_type`) |
| **Behavior** | **POS:** `PaymentDialogFragment` — **Paid** commits cash/mobile/split as before; **Credit** tab blocks **Paid** and requires **On credit** → dialog (amount must match total, optional due date, optional note) → `commitOnCreditSale` → `SaleRepository` writes INCOME with `CREDIT_EXTENDED` + `Debt`. **Insights:** `CashFlowInsightsFragment` hosts `TabLayout` + `ViewPager2` — **Cash flow** (`InsightsOverviewFragment`, shared `CashFlowInsightsViewModel`) and **Credit** (`CreditFragment`: total outstanding, `DebtAdapter` rows with **Paid** / **Remind**). **Paid** → `DebtRepository.markDebtRepaid` (DAO `markPaid` + balancing INCOME `DEBT_REPAID`). **Remind** → `DebtReminderViewModel` + Gemma prompt (locale language name) → preview dialog → user **Send via SMS** (`ACTION_SENDTO` / `sms_body`; app never auto-sends). |
| **Files created** | `CreditFragment.kt`, `CreditViewModel.kt`, `DebtReminderViewModel.kt`, `DebtAdapter.kt`, `InsightsPagerAdapter.kt`, `InsightsOverviewFragment.kt`, `fragment_credit.xml`, `fragment_insights_overview.xml`, `item_debt_row.xml` |
| **Files modified** | `PaymentDialogFragment.kt`, `PaymentViewModel.kt`, `fragment_payment_dialog.xml`, `CashFlowInsightsFragment.kt`, `fragment_cash_flow_insights.xml`, `SaleRepository.kt`, `PaymentDraft.kt`, `DebtRepository.kt`, `DebtDao.kt`, `TransactionNoteTypes.kt`, `app/build.gradle.kts` (ViewPager2), `strings.xml`, `HANDOFF.md` |
| **Prompt vs. this repo** | Written U6: `SalesFragment` / `SalesViewModel` / `InsightsFragment` / `fragment_insights.xml`. **Shipped:** `PaymentDialogFragment` + `PaymentViewModel`; `CashFlowInsightsFragment` + `fragment_cash_flow_insights.xml` + `InsightsOverviewFragment` + `fragment_insights_overview.xml`. `nav_graph.xml` unchanged for U6 (Credit is an in-screen tab). `DebtRepository.kt` extended (original file from P5). |

## Phase 2A — Prompt U7 (Supplier negotiation assistant)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U7: Supplier Negotiation Assistant |
| **Next Prompt** | Prompt U8: WhatsApp Order Parser |
| **DB version** | **15** (no migration) |
| **Behavior** | **FULL_AI** only: `RULES_BASED` / `PARTIAL_AI` see a dialog (4GB+ RAM message) and do **not** navigate. Entry: **Prepare supplier visit** card on `HomeFragment` (below loss alerts) + overflow item on `InventoryListFragment`. `SupplierNegotiationFragment`: supplier name (optional), inventory multi-select + free-text lines, budget, city/country (country prefilled from device locale; currency from `AppSettings`). **Generate script** → `NegotiationViewModel` builds the spec prompt (city, country, language, items, budget, currency, supplier) and calls `GemmaService.generateResponse` on `Dispatchers.IO` with loading UI. `NegotiationGuideFragment`: parses OPENING / MAIN ASK / IF PUSHED BACK / CLOSING into colour‑coded cards; **Regenerate** re-runs Gemma; **Share** uses `ACTION_SEND` chooser with plain text. Activity-scoped `NegotiationViewModel` via `activityViewModels()`. |
| **Files created** | `SupplierNegotiationFragment.kt`, `NegotiationGuideFragment.kt`, `NegotiationViewModel.kt`, `NegotiationTierGate.kt`, `fragment_supplier_negotiation.xml`, `fragment_negotiation_guide.xml`, `item_negotiation_section.xml` |
| **Files modified** | `HomeFragment.kt`, `fragment_home.xml`, `InventoryListFragment.kt`, `fragment_inventory_list.xml`, `menu_inventory_list.xml`, `nav_graph.xml`, `MainActivity.kt`, `strings.xml`, `HANDOFF.md` |

## Phase 2A — Prompt U4 (Receipt and invoice OCR)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt U4: Receipt and Invoice OCR |
| **Next Prompt** | Prompt U5: Loss Prevention Alerts |
| **DB version** | **12** (no migration; `ProductDao.insertAll` for bulk inserts) |
| **Behavior** | Inventory speed dial → **Scan receipt** → `ReceiptScanFragment` (16:9 document frame + corners, **Capture receipt**). `ReceiptParser`: ML Kit `TextRecognizer` → full OCR string → `GemmaService.generateResponse` (JSON array only) → `Gson` → `List<ReceiptLineItem>`; on failure → `ReceiptReviewFragment` with **fallback** banner + one empty row. Review screen: editable cards, amber stroke until row valid, **Add N items** → `insertAll`, then `popBackStack` to `inventoryListFragment`. |
| **Files created** | `ReceiptScanFragment.kt`, `ReceiptParser.kt`, `ReceiptReviewFragment.kt`, `ReceiptReviewViewModel.kt`, `ReceiptReviewAdapter.kt`, `ReceiptDraftLine.kt`, `fragment_receipt_scan.xml`, `fragment_receipt_review.xml`, `item_receipt_review_line.xml`, receipt corner / frame drawables, `ic_receipt.xml` |
| **Files modified** | `InventoryListFragment.kt`, `fragment_inventory_list.xml`, `nav_graph.xml`, `MainActivity.kt` (hide bottom nav on receipt scan/review + label scanner), `ProductDao.kt`, `app/build.gradle.kts` (Gson), `strings.xml`, `HANDOFF.md` |

## POS Module — Prompt P0 (HANDOFF + ESC/POS + JitPack + Bluetooth)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P0: HANDOFF.md update + ESC/POS printer library + JitPack + Bluetooth permissions |
| **Next Prompt** | Prompt P3: POS Main Screen |
| **Dependencies added** | `com.github.DantSu:ESCPOS-ThermalPrinter-Android:3.3.0` (JitPack) |
| **Repositories** | `maven("https://jitpack.io")` in `settings.gradle.kts` (`dependencyResolutionManagement`) |
| **Manifest** | `BLUETOOTH` / `BLUETOOTH_ADMIN` (maxSdkVersion 30), `BLUETOOTH_CONNECT`, `BLUETOOTH_SCAN` (neverForLocation) |
| **Files modified** | `HANDOFF.md`, `app/build.gradle.kts`, `settings.gradle.kts`, `app/src/main/AndroidManifest.xml` |

## POS Module — Prompt P1 (Database migrations v5→v7)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P1: Database migrations v5→v7 (+ v3→v5 bridge for shipped installs) |
| **Next Prompt** | Prompt P2: Cart Data Layer |
| **DB version** | 7 |
| **New entities** | `SaleLineItem`, `AppSettings` (+ SQL tables `customers`, `debts`, `alerts` on migration 3→5) |
| **Files created** | `SaleLineItem.kt`, `SaleLineItemDao.kt`, `AppSettings.kt`, `AppSettingsDao.kt`, `DatabaseMigrations.kt`, `AppDatabaseMigrationTest.kt` |
| **Files modified** | `AppDatabase.kt`, `Transaction.kt`, `AppModule.kt`, `HANDOFF.md` |
| **Tests** | `AppDatabaseMigrationTest` (instrumented) — v3→v7 and v5→v7 validation |

## POS Module — Prompt P2 (Cart data layer)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P2: Cart Data Layer |
| **Next Prompt** | Prompt P3: POS Main Screen |
| **Files created** | `CartItem.kt`, `CartManager.kt`, `CartRepository.kt` under `com.biasharaai.pos.cart` |
| **Key note** | `CartManager` is `@Singleton` — holds `StateFlow` of cart lines; `CartRepository` combines with `AppSettingsDao` for tax / totals. No Room writes for cart until sale commit. |

## POS Module — Prompt P3 (POS main screen)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P3: POS Main Screen |
| **Next Prompt** | Prompt P4: Cart Panel and Totals |
| **DB version** | 8 (`Customer` entity + `MIGRATION_7_8`) |
| **Files created** | `PosFragment.kt`, `PosViewModel.kt`, `fragment_pos.xml` (+ `layout-land`, `layout-sw600dp`), `ProductGridAdapter.kt`, `item_product_grid.xml`, `item_product_search_result.xml`, `CustomerSelectorBottomSheet.kt`, `fragment_customer_selector.xml`, `item_customer_row.xml`, `colors_pos.xml` |
| **Files modified** | `nav_graph.xml` (`posFragment`, scanner `return_barcode_to_pos` arg), `bottom_nav_menu.xml` (Sales tab → `posFragment`), `BarcodeScannerFragment.kt`, `strings.xml`, `dimens.xml`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Removed** | `SalesFragment.kt` (placeholder) |
| **Key note** | Bottom-nav **Sales** opens POS. Scanner: `SCAN_TO_ADD` + `return_barcode_to_pos=true` posts barcode to `PosFragment` SavedStateHandle and pops. Landscape / `sw600dp`: 55% products / 45% product vs cart split. |

## POS Module — Prompt P4 (Cart panel and totals)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P4: Cart Panel and Totals |
| **Next Prompt** | Prompt P5: Payment Flows |
| **Files created** | `CartAdapter.kt`, `CartBottomSheetFragment.kt`, `fragment_cart_bottom_sheet.xml`, `item_cart_line.xml`, `view_totals_bar.xml`, `TotalsBarView.kt`, `CartLinePriceOverrideDialog.kt`, `PaymentDialogFragment.kt` |
| **Files modified** | `fragment_pos.xml` (+ land / `sw600dp`), `PosFragment.kt`, `nav_graph.xml` (`paymentDialogFragment` dialog + action from `posFragment`), `MainActivity.kt` (hide bottom nav on payment dialog), `strings.xml`, `colors_pos.xml`, `ic_remove.xml`, `bg_cart_badge.xml` |
| **Removed** | `item_pos_cart_line.xml` (replaced by `item_cart_line.xml`) |
| **Key note** | Phone: summary bar opens `CartBottomSheetFragment`; Pay → `PaymentDialogFragment` (full payment UI in P5). Tablet/land: `CartAdapter` + `TotalsBarView` + Pay in `posFragment`. Price override respects `AppSettings.allowPriceOverride`; deep-discount Gemma warning lives in `PosViewModel.applyLinePriceOverride` (Prompt P9). |

## POS Module — Prompt P5 (Payment flows)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P5: Payment Flows |
| **Next Prompt** | Prompt P6: Sale Commit and Receipt |
| **DB version** | 9 (`Debt` entity + `MIGRATION_8_9`) |
| **Files created** | `PaymentViewModel.kt`, `fragment_payment_dialog.xml` (rewrite `PaymentDialogFragment.kt` full-screen), `Debt.kt`, `DebtDao.kt`, `DebtRepository.kt` |
| **Files modified** | `AppDatabase.kt`, `DatabaseMigrations.kt`, `AppModule.kt`, `themes.xml`, `strings.xml`, `nav_graph.xml`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Key note** | `PaymentViewModel` holds tab state, tender / mobile / credit due date, split lines, and builds `PaymentDraft` for P6 — **no** `Transaction` / stock writes here. FULL_AI + `GemmaService.isAvailable`: paste-SMS extracts ref (Gemma + regex). Credit tab uses `CartRepository.selectedCustomer` + `DebtRepository` outstanding sum. |

## POS Module — Prompt P8 (Returns and refunds)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P8: Returns and Refunds |
| **Next Prompt** | Prompt P9: AI Features in POS |
| **Files created** | `ReturnFragment.kt`, `ReturnViewModel.kt`, `ReturnLineAdapter.kt`, `fragment_return.xml` |
| **Files modified** | `SaleRepository.kt` (`commitReturn` method), `ProductDao.kt` (`incrementStock`), `nav_graph.xml` |

## POS Module — Prompt P9 (AI features in POS)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P9: AI Features in POS |
| **Next Prompt** | Prompt P10 — **complete** (see P10 row below) |
| **Files created** | `PosAiAdvisor.kt`, `EndOfDayViewModel.kt`, `EndOfDayFragment.kt`, `fragment_end_of_day.xml`, `CustomerSuggestionEngine.kt` |
| **Key features** | Customer suggestion chips (`CustomerSuggestionEngine` + `PosFragment`; U2 adds cart-aware chips + Gemma subtitle); price override warning via `PosViewModel` + Snackbar Undo when tier is not `RULES_BASED`, override &lt; 60% of catalog, and Gemma available; mobile-money “Paste SMS” ref extraction (FULL_AI + model) in `PaymentViewModel`; **Close day** → `EndOfDayFragment` with stats chips, Gemma narrative (`PosAiAdvisor`), Share chooser. |
| **Files modified** | `PosFragment.kt`, `PosViewModel.kt`, `CartBottomSheetFragment.kt`, `CartLinePriceOverrideDialog.kt`, `PaymentViewModel.kt`, DAOs / repositories as needed, `nav_graph.xml`, POS layouts, `strings.xml` |

## POS Module — Prompt P10 (POS module complete)

| Field | Value |
|-------|-------|
| **Last Completed** | Prompt P10 — POS Module Complete |
| **Next Prompt** | Prompt A3 — StockGuardianAgent + FraudSentinel |
| **Key note** | Tracker only for continuity; detailed P10 file list lives in the PR / branch that closed P10. Phase 4 work starts at **Current Project State** above. |

## Chat — Conversational Query Layer (owner Q&A on Room data)

| Field | Value |
|-------|-------|
| **Purpose** | Answer common business questions from **transactions, sale lines, products, customers, debts** without relying on Gemma for facts. Gemma (PARTIAL_AI / FULL_AI) only **polishes** the factual string when available. |
| **Entry point** | `ChatViewModel.sendMessage` → `tryStructuredAnswer` runs **first** (before offline fallback and before streaming Gemma). |
| **Core types** | `ConversationalQueryLayer` (`chat/query`), `StructuredQueryRoutes.kt` + `StructuredQueryContext`, `ConversationalQueryLayerHints.buildStructuredPolishHints`, `RulesBasedReplyVariator` (RULES_BASED lead-in variety), `GemmaAnswerFormatter` (strict polish + digit-mass safeguard + `Log` telemetry), `PosSaleLineFact` + `SaleLineItemDao.posSaleLineFactsSince`. |
| **Lookback** | ~400 days of POS line facts + same-window transactions for aggregates (tune `LONG_LOOKBACK_DAYS`). |
| **Covered (examples)** | Today/week/month/year revenue & counts; best/worst day; hour-of-day / weekday peaks; payment mix (cash / mobile-ish / credit heuristic); top/worst products; low stock; category revenue; gross profit from lines; below-cost sales; expenses vs income (month); customer totals / named lookup; debt totals; “cost of [product]” from catalog; pointers to Home loss / suggest-price where schema lacks an answer. |
| **Explicit gaps** | Per-device / per-cashier / offline-sync counts / deep fraud analytics are **not** in Room as modeled — layer returns a clear “not stored” or “check Home” message instead of inventing data. |
| **Extension** | Add ordered branches in `tryStructuredAnswer` + optional new DAO queries; keep matchers **deterministic** for RULES_BASED devices. |
| **Catalog coverage** | Broad keyword routes for all 12 owner categories: timeboxed revenue/expenses, POS lines (profit, payment mix, top products, category share), customers/debts (including overdue heuristics), planning pointers, and honest fallbacks where targets, elasticity, device sync, or repayment timelines are not persisted. |
| **Gemma memory + sessions (DB v20)** | `ai_business_memory` + `ChatMemoryRepository`: owner phrases like “remember that …” / “note that …” persist and prefix structured + Gemma prompts (transcript rows removed from this repository — only long-term memory). **`chat_session_messages`**: each user/assistant reply is scoped to a **session**; **first** Gemma prompt after cold start, **New chat** (new session), or opening a session from history includes a capped transcript snapshot built from that session (`injectTranscriptIntoNextGemmaPrompt`). **New chat** creates a new session row and clears the on-screen thread without deleting other sessions. |
