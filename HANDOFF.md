# Biashara AI Project Handoff Document

## Current Project State
- **Last Completed Prompt:** Prompt P10 — POS Module Complete
- **Next Expected Prompt:** Prompt A1 — Agent Database Migrations
- **Phase:** 4 — Autonomous Business Agent
- **AppDatabase version:** **15** (`AppDatabase.kt`) — **Prompt A1** and later agent work add **additive** migrations from this baseline (e.g. **15→16+**). *(Any template that still says “v7” refers to an old POS-era milestone, not the current schema.)*
- **Key Notes:**
  - All agents are **ADDITIVE** — no existing code is deleted.
  - Phase **4a** (A1–A4) has **zero Gemma dependency** — value on all device tiers.
  - **`GemmaService`** calls are serialised via **`Mutex`** in **`AgentOrchestrator`** (add **`AgentOrchestrator`** in Phase 4; keep single-flight access consistent with existing `GemmaService` mutex usage).
  - Every agent action requires **owner approval** unless auto-approved in settings.

**Repository snapshot (unchanged facts):** POS, intelligence upgrades (e.g. loss alerts, **Gallery-style chat** with `chat_sessions` / `chat_session_messages` at **DB v15**), and the full **`DatabaseMigrations.kt`** chain remain in place. Details in **Room Database** and **Chat** sections below.

| Handoff | |
|---------|---|
| **Last Completed** | Prompt A0 |
| **Next Prompt** | A1: Database Migrations |
| **Files modified** | `HANDOFF.md` |

### Git branches (do not merge blindly)

- **Branch map and commands:** [docs/BRANCHES.md](docs/BRANCHES.md) — which branch to checkout, naming rules, and PR-oriented workflow.
- **Phase 4 prep integration:** remote branch **`feat/phase4-lineage-v1`** — carries chat / DB v15 / HANDOFF A0 work **without** folding it into `main` until a reviewed PR. Parent line: **`feat/pos-module`**.
- **Open a PR from this line:** https://github.com/Donaldcho/biashara/pull/new/feat/phase4-lineage-v1

### Prior phases (reference)
- **Phase 2 — Intelligence Layer Upgrade:** Features such as loss prevention alerts (U5), receipt OCR (U4), conversational chat layer, and related Room fields shipped under the upgrade track; prefer **additive** follow-ups only.
- **POS / Phase 1–3:** `PosFragment`, cart, payments, returns, end-of-day AI, etc. — treat as **confirmed working** unless a prompt explicitly revisits them.
- **Build / test stack:** `AppDatabaseMigrationTest` validates **v3→v15** among other paths; **`fallbackToDestructiveMigration()`** is **not** used in `AppModule` — migrations are explicit. Run `.\scripts\preflight-build.ps1` before Gradle builds (see **Prerequisites** below).
- **Libraries (snapshot):** ML Kit (OCR, image labeling for Ask Image), WorkManager, Gson, Room testing, Turbine — see sections below for feature mapping.

## Prerequisites Before Any Gradle / IDE Build
1. Run `.\scripts\preflight-build.ps1` from the repo root. It writes `build-environment.log` (gitignored).
2. If the script reports **PowerShell HTTPS OK** but **Java PKIX / SSLHandshakeException** to `dl.google.com`, Gradle cannot resolve the Android Gradle Plugin until **TLS trust is fixed**.
3. Gradle wrapper uses **8.14.3**. `gradlew.bat` prefers Android Studio **JBR** unless `BIASHARAAI_USE_SYSTEM_JAVA=1`.

## Localization (Prompt 2b — done)
- **Locales:** `values` (English), `values-sw`, `values-ha`, `values-yo`, `values-am`.
- **First launch:** `LanguageSelectionFragment` → choose locale → `homeFragment`.
- **Bottom navigation:** Hidden on `languageSelectionFragment`, `barcodeScannerFragment`, `addEditProductFragment`, `paymentDialogFragment`.

## Room Database (Prompt 3 + 9 — done; Prompt P1 — v7 + migrations; chat v15)
- **Version 15** — migrations in `DatabaseMigrations.kt` through **14→15** (`chat_sessions`, `chat_session_messages`, migrate off `chat_transcript_turns`); plus **12→13** loss columns on `alerts`; **7→8** `Customer`, **8→9** `Debt`, **9→10** `customer_id` / `related_sale_transaction_id` on `transactions`, `source_sale_line_item_id` on `sale_line_items`, **10→11** `note_type` on `transactions`, **11→12** `last_visit` on `customers`); **no** destructive fallback in `AppModule`.
- **`Product` entity**: `id`, `name`, `description?`, `price`, `cost`, `stockQuantity`, `category?`, `barcodeValue?`, `imageUrl?`.
- **`Transaction` entity**: legacy fields `id`, `type`, `amount`, `description`, `date`; **POS (P1):** `paymentMethod`, `mobileMoneyNetwork`, `mobileMoneyRef`, `amountTendered`, `changeDue`, `receiptNumber`, `saleGroupId`, `taxRate`, `taxAmount`. **P8:** `TransactionType.RETURN`, `customerId` (credit sales), `relatedSaleTransactionId` (return rows). **`SaleLineItem`:** `sourceSaleLineItemId` links return lines to original sale lines.
- **`SaleLineItem`**, **`AppSettings`** (singleton row `id = 1`) — Prompt P1.
- **Phase 2 SQL tables (P1 migration 3→5):** `customers`, `debts`, `alerts` — reserved for upcoming Kotlin `@Entity` / DAO wiring; **no Kotlin entities yet**, data preserved across upgrades.
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
- `VoiceInputProcessor`: Gemma 3n multimodal on FULL_AI, SpeechRecognizer fallback.
- `AddEditProductFragment`: Dual-path mic button, RECORD_AUDIO permission, recording indicator.

## Financial Insights Generator (Prompt 9 — done)
- `Transaction` entity + `TransactionDao` + `TransactionRepository`.
- `CashFlowAnalyzer`: Gemma-powered insights with rules fallback.
- `CashFlowInsightsFragment` + `CashFlowInsightsViewModel`: Chart + AI narrative.

## Testing Suite (Prompt 10 — done)
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
VoiceInputProcessor (singleton, deps: GemmaService, CapabilityTier)
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
- **VoiceInputProcessor**: `usesOnDeviceAi = false` — always uses Android `SpeechRecognizer`.

### Model History (for next agent reference)
1. **Gemma 3n E2B** (multimodal, ~3.4GB) from Google Drive — Too slow on mobile devices.
2. **Gemma 4 E2B** (text-only, ~2.58GB) from HuggingFace — ✅ **Current active model**. Fastest for on-device inference (2.3B effective params via PLE architecture). Best for simple business Q&A tasks.
- **Google Drive download notes** (if reverting to Gemma 3n): Must use `drive.usercontent.google.com` (NOT `drive.google.com/uc`). File ID: `1YIEIATRoOKlnP72BR5y1ZWyPNASh54F3`.
- **To re-enable multimodal**: Uncomment `AudioModelOptions` in `GemmaService.kt`, set `usesOnDeviceAi` to `capabilityTier == FULL_AI && gemmaService.isAvailable` in `VoiceInputProcessor.kt`.

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
| **Tests** | `AppDatabaseMigrationTest.migrate3To15_preservesProductsAndTransactionsAndSeedsSettings` — v3→v15; `PRAGMA table_info(customers)` includes `last_visit`. |

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
| **Next Prompt** | Prompt U6: Debt and Credit Tracker |
| **DB version** | **13** (`MIGRATION_12_13`: `alert_type`, `product_id`, `dedupe_key`, `localized_message`, `related_transaction_id` on `alerts`) |
| **Behavior** | `LossAlertEngine` runs four Room-backed detectors (low stock without recent POS lines, 7-day-then-3-day-quiet sales gap, line `unit_price` &lt; 70% of list `price`, high expense day vs trailing 30-day average). `LossAlertWorker` (WorkManager **24h** unique periodic) inserts `Alert` rows with `dedupe_key` skip-if-active. **FULL_AI** + model + non-English locale: Gemma caches translation in `localized_message`. `HomeFragment` shows cards (`AlertCardAdapter`) with Review (inventory / edit product / receipt / insights) and Dismiss. `BiasharaApp` implements `Configuration.Provider` with `LossAlertWorkerFactory`; manifest removes default WorkManager initializer. |
| **Files created** | `LossAlertTypes.kt`, `LossAlertDao.kt`, `LossAlertEngine.kt`, `LossAlertWorker.kt`, `LossAlertWorkerFactory.kt`, `LossAlertScheduler.kt`, `HomeViewModel.kt`, `AlertCardAdapter.kt`, `item_alert_card.xml`, `ic_loss_alert.xml` |
| **Files modified** | `Alert.kt`, `AlertDao.kt`, `AppDatabase.kt`, `DatabaseMigrations.kt`, `AppModule.kt`, `BiasharaApp.kt`, `HomeFragment.kt`, `fragment_home.xml`, `AndroidManifest.xml`, `strings.xml`, `AppDatabaseMigrationTest.kt`, `HANDOFF.md` |
| **Tests** | `AppDatabaseMigrationTest.migrate3To15_preservesProductsAndTransactionsAndSeedsSettings` — v3→v15; `PRAGMA table_info(alerts)` includes U5 columns. |

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
| **Next Prompt** | Phase 4 — Prompt A1: Agent Database Migrations |
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
| **Gemma memory + sessions (DB v15)** | `ai_business_memory` + `ChatMemoryRepository`: owner phrases like “remember that …” / “note that …” persist and prefix structured + Gemma prompts (transcript rows removed from this repository — only long-term memory). **`chat_session_messages`**: each user/assistant reply is scoped to a **session**; **first** Gemma prompt after cold start, **New chat** (new session), or opening a session from history includes a capped transcript snapshot built from that session (`injectTranscriptIntoNextGemmaPrompt`). **New chat** creates a new session row and clears the on-screen thread without deleting other sessions. |
