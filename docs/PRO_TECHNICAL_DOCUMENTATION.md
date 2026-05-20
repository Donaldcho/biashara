# Biashara AI Pro Technical Documentation

**Version scope:** Pro v1.3.0, versionCode 20, package `com.biasharaai`  
**Documentation date:** 2026-05-20  
**Audience:** engineers, QA, release managers, support leads, and deployment partners

This document describes the Pro edition as implemented in the Android codebase. Pro is an offline-first Android application that unlocks services, staff, vouchers, warranty/service receipts, mixed product/service POS, service-aware agents, and deeper business intelligence while keeping Basic/Shop behavior intact.

## Scope And Product Boundary

Biashara AI has two independent licence dimensions:

| Dimension | Values | Purpose |
| --- | --- | --- |
| Product line | `SHOP`, `PRO` | Gates feature families. `PRO` unlocks services, vouchers, staff, service delivery, service agents, and service POS. |
| Edition | `PRIVATE`, `SME`, `ENTERPRISE` | Controls deployment tier. Enterprise adds central server/sync workflows, but those are outside the Pro-only user surface documented here. |

Pro in this document means `ProductLine.PRO` with non-Enterprise usage unless stated otherwise. Enterprise tables and workers may exist in the current workspace, but Pro remains a local-device product. Basic/Shop users keep the current product-only design.

Current code notes:

- `app/build.gradle.kts` sets `versionName = "1.3.0"` and `versionCode = 20`.
- `docs/RELEASE_PRO_v1.3.0.md` records the Pro stable release baseline as AppDatabase v33.
- The active workspace has later Enterprise and learning migrations, so `AppDatabase` currently reports version 40. Pro docs below include the Pro features plus the current learning/feedback tables that run on-device.

## Executive Summary

Biashara AI Pro is a single-activity native Android app built with Kotlin, XML views, Hilt, Room, WorkManager, ML Kit, LiteRT-LM, and on-device TFLite embeddings. Its core design is:

1. Store all operational business data locally in Room.
2. Execute POS writes atomically inside Room transactions.
3. Use deterministic Kotlin repositories as the source of truth.
4. Use Gemma on-device for narration, chat polishing, summaries, pricing language, and business advice when the device supports it.
5. Fall back to rule-based behavior when the model is unavailable.
6. Persist feedback, KPI snapshots, demand forecast calibration rows, and business memory so the AI does not repeat the same advice forever.

## Architecture

```text
Android Activity and Fragments
  MainActivity
  NavHostFragment
  Bottom navigation
  XML views + ViewBinding

ViewModels
  StateFlow/LiveData UI state
  Validation
  Navigation events
  User actions

Repositories and domain services
  SaleRepository
  ServiceRepository
  LedgerRepository
  BusinessProfileRepository
  ChatSessionRepository
  Agent orchestration
  AI/ML wrappers

Room database
  products
  transactions
  sale_line_items
  service_items
  service_deliveries
  service_vouchers
  customers
  debts
  ledger_entries
  chat sessions
  agent actions
  KPI/memory/feedback tables

Platform services
  WorkManager
  CameraX
  ML Kit barcode/OCR/image labels
  Android Speech/TTS
  Bluetooth printing
  Android share intents

On-device AI
  LiteRT-LM Gemma model
  TFLite MiniLM embedding model
  Deterministic fallback routes
```

Key properties:

- The app is offline-first. Sales, inventory, services, debts, ledger, chat history, and agent cards persist locally.
- The LLM is not the decision maker for money or stock mutations. Kotlin repositories decide and validate. Gemma may explain, polish, summarize, or recommend.
- The POS write path is designed around atomic persistence: sale transaction, line items, stock changes, debts, ledger entries, service deliveries, and vouchers commit together.
- Pro gates are enforced in repositories and UI so Basic/Shop users do not see or execute service workflows.

## Technology Stack

| Layer | Technology |
| --- | --- |
| Language | Kotlin |
| Build | Gradle, Android Gradle Plugin 8.8.2, Kotlin 2.2.21, KSP 2.2.21-2.0.4 |
| Android target | minSdk 26, targetSdk 35, compileSdk 35 |
| Dependency injection | Dagger Hilt 2.57.2 |
| Persistence | Room 2.8.4, SQLite database `biashara.db` |
| Async | Kotlin coroutines 1.9.0, StateFlow, LiveData |
| Navigation | AndroidX Navigation 2.8.5 |
| UI | XML layouts, Material Components 1.12.0, ConstraintLayout, ViewBinding |
| Background jobs | WorkManager 2.9.0 |
| Camera/scanning | CameraX 1.4.1, ML Kit Barcode 17.3.0 |
| OCR/image | ML Kit Text Recognition 16.0.1, Image Labeling 17.0.9 |
| AI runtime | LiteRT-LM 0.11.0 for Gemma `.litertlm` models |
| Embeddings | LiteRT/TFLite 1.2.0 with MiniLM-L6-v2 style 384-dim embeddings |
| Voice | Android speech/TTS plus WhisperKit dependency |
| Networking | OkHttp 4.12.0 for optional cloud/server paths |
| Receipts | ESC/POS thermal printer library, Android print/share APIs |
| Tests | JUnit 4, MockK, Turbine, coroutines-test, Room testing |

## Main Source Layout

| Path | Responsibility |
| --- | --- |
| `app/src/main/java/com/biasharaai/MainActivity.kt` | Single-activity host, bottom navigation, splash, receipt deep link handling. |
| `app/src/main/java/com/biasharaai/BiasharaApp.kt` | Hilt app, WorkManager factory, startup bootstrap, agent/ledger scheduling. |
| `app/src/main/java/com/biasharaai/data/local/db/` | Room entities, DAOs, database, repositories for persisted business data. |
| `app/src/main/java/com/biasharaai/ui/` | Fragments and ViewModels for screens. |
| `app/src/main/java/com/biasharaai/pos/` | Cart/payment/receipt domain helpers. |
| `app/src/main/java/com/biasharaai/service/` | Pro service catalogue, tokens, receipt/voucher logic. |
| `app/src/main/java/com/biasharaai/agent/` | Agent types, scheduling, action building, decision/dedupe logic, workers. |
| `app/src/main/java/com/biasharaai/ai/` | Gemma service, device capability checks, demand forecast, embeddings. |
| `app/src/main/java/com/biasharaai/knowledge/` | Local RAG, knowledge chunks, business memory extraction. |
| `app/src/main/java/com/biasharaai/ledger/` | Ledger calculations, backfill, balance recompute, P&L export. |
| `app/src/main/res/navigation/nav_graph.xml` | App screen graph and global routes. |

## Licensing And Feature Gating

`ProductLineManager` reads the stored signed licence from `LicenceValidator`.

Important methods:

- `productLine()` returns `SHOP` by default.
- `isProEnabled()` returns true only for `ProductLine.PRO`.
- `isEnterprisePro()` returns true only for Pro plus Enterprise edition.

Pro gates appear in:

- `ServiceRepository.requirePro()`: prevents service, voucher, warranty, and service delivery writes without Pro.
- Inventory UI: services tab is only shown when Pro is enabled.
- POS UI: Products/Services/Both mode and service scanning are shown only when Pro is enabled.
- Staff settings: staff management button is hidden or blocked for non-Pro users.
- Agent settings/workers: utilisation, no-show, service-pricing, and voucher-expiry agents are Pro-only.
- Ledger repository: service sale, mixed sale, voucher sale/redemption, and warranty claim ledger entries require Pro.

## App Startup

Startup sequence:

1. `MainActivity` applies persisted locale before inflating UI.
2. Navigation starts at language selection on first run, otherwise Today/Agent Feed.
3. `BiasharaApp` ensures a default Shop licence if no key exists.
4. Room creates/seeds singleton rows for `app_settings`, `agent_settings`, and `business_profile`.
5. Model registry, skill registry, skill packs, and knowledge ingestor bootstrap in background.
6. Loss alerts, agents, ledger backfill, ledger balance recomputation, storage watchdog, and optional Enterprise sync workers are scheduled.
7. Fraud sentinel registers a Room invalidation observer against transactions/products.

For Pro, `ProOnboardingCardManager` can show onboarding guidance once the Pro licence is active.

## Navigation And Screens

Main bottom navigation:

| Tab | Fragment | Purpose |
| --- | --- | --- |
| Today | `AgentFeedFragment` | AI/agent cards, daily brief, supplier visit shortcut, ledger shortcut. |
| Inventory | `InventoryListFragment` | Products for all editions; Services tab for Pro. |
| Chat | `ChatFragment` | Local business assistant with multi-session history. |
| Sales | `PosFragment` | Product/service/voucher POS. |
| Settings | `SettingsFragment` | Model, licence, currency, voice, staff, profile, ledger, integrations. |

Secondary Pro-relevant screens:

- `AddEditServiceFragment`
- `PaymentDialogFragment`
- `ReceiptFragment`
- `VoucherReceiptFragment`
- `ServiceReceiptScanFragment`
- `StaffSettingsFragment`
- `CashFlowInsightsFragment`
- `ManualLedgerEntryFragment`
- `CashCountFragment`
- `SupplierNegotiationFragment`
- `BusinessProfileEditFragment`
- `AgentSettingsFragment`
- `ChatHistoryFragment`

## Room Database

Database name: `biashara.db`.

Core operational tables:

| Table | Entity | Notes |
| --- | --- | --- |
| `products` | `Product` | Product catalogue, price, cost, stock, category, barcode, image, Enterprise sync metadata. |
| `transactions` | `Transaction` | Income, expense, return, payment fields, receipt number, tax, product/service subtotals, paid/balance fields. |
| `sale_line_items` | `SaleLineItem` | Product line snapshots for receipts, analytics, and returns. |
| `customers` | `Customer` | Customer identity, phone/email/notes, last visit. |
| `debts` | `Debt` | Customer credit balances and due dates. |
| `app_settings` | `AppSettings` | Business name, currency, tax, receipt footer, printer, voice, Pro onboarding, voucher signing key. |

Pro service tables:

| Table | Entity | Notes |
| --- | --- | --- |
| `service_items` | `ServiceItem` | Service catalogue. Includes name, description, base price, duration, category, `BSVC:` catalogue token, warranty days, kiosk visibility, Enterprise metadata. |
| `service_deliveries` | `ServiceDelivery` | One row per delivered service unit. Links service, transaction, customer, staff, receipt token, charged amount, warranty expiry. |
| `service_vouchers` | `ServiceVoucher` | Prepaid service vouchers. Tracks voucher ID, service, customer, source transaction, total/remaining uses, amount paid, expiry. |
| `staff_members` | `StaffMember` | Staff records with role, active flag, and optional PIN hash/salt. |
| `appointments` | `Appointment` | Appointment/no-show support for Pro service businesses. |

Ledger and finance tables:

| Table | Entity | Notes |
| --- | --- | --- |
| `ledger_entries` | `LedgerEntry` | Append-only money ledger. No update/delete write path. Corrections use adjustment entries. |
| `cash_counts` | `CashCount` | Daily physical cash count against expected ledger balance. |
| `ledger_context` | `LedgerContext` | Owner/agent explanations for anomalies. |
| `cash_movement_evidence` | `CashMovementEvidence` | Captured proof for scanned cash/SMS/bill entries. |

AI and learning tables:

| Table | Entity | Notes |
| --- | --- | --- |
| `agent_actions` | `AgentAction` | Pending/approved/dismissed cards shown on Today. |
| `agent_settings` | `AgentSetting` | Agent enable switches, schedule hour, thresholds, Pro service agent switches. |
| `agent_run_logs` | `AgentRunLog` | Worker run history. |
| `agent_advice_feedback` | `AgentAdviceFeedback` | Helpful/not-helpful votes on agent cards. Used to suppress repeated rejected advice. |
| `business_kpi_snapshots` | `BusinessKpiSnapshot` | Weekly KPI snapshots for longitudinal comparisons. |
| `forecast_calibrations` | `ForecastCalibration` | Predicted vs actual demand windows and bias ratio. |
| `business_memory_entries` | `BusinessMemoryEntry` | Goals, preferences, patterns, weekly KPI memories, optional embeddings. |
| `chat_sessions` | `ChatSessionEntity` | Chat threads. |
| `chat_session_messages` | `ChatSessionMessageEntity` | Chat turns, image paths, feedback, sources, confidence, action hint. |
| `knowledge_chunks` | `KnowledgeChunk` | Static app/business knowledge for RAG. |
| `teaching_events`, `lesson_completions`, `feature_mastery` | Teaching/learning tables | In-app help and skill education. |

Enterprise-only tables also exist in the current workspace, but Pro user workflows do not depend on them.

## Product Model

`Product` fields:

- `id`
- `name`
- `description`
- `price`
- `cost`
- `stockQuantity`
- `category`
- `barcodeValue`
- `imageUrl`
- `lastStockCheckAt`
- Enterprise catalogue metadata fields

Indexes support barcode lookup, category filtering, and stock quantity queries.

Product stock is decremented only in `SaleRepository.commitPosSale()` after validation. Returns restore product stock through the return workflow.

## Service Model

`ServiceItem` is the Pro service catalogue row. Important fields:

- Name and description.
- Base price.
- Price mode.
- Duration in minutes.
- Category.
- Catalogue token with `BSVC:` prefix.
- Warranty days.
- Visibility flags.

`ServiceRepository.upsertService()` creates or updates service rows. On insert, the service is created first, then its final `BSVC:<id>` catalogue token is written.

`ServiceDelivery` represents a delivered service unit. If a cart sells quantity 3 of a service, the repository creates three delivery rows. Each row can carry:

- Staff name.
- Customer ID.
- Transaction ID.
- Warranty expiry.
- Charged amount.
- `BSRC:<deliveryId>` receipt token.

`ServiceVoucher` represents prepaid usage. The default extension treats services as voucherable and gives vouchers a 90-day validity window.

## Service Tokens

`ServiceTokenCodec` supports three service-related token families:

| Prefix | Meaning | Typical use |
| --- | --- | --- |
| `BSVC:` | Service catalogue token | Scan service QR to add the service to POS. |
| `BSRC:` | Service receipt token | Verify service delivery and warranty. |
| `BSVOU:` | Voucher token | Redeem prepaid service uses. |

`ServiceReceiptCodec` signs service receipt tokens with HMAC-SHA256 using the app-level voucher signing key. The receipt scanner can verify service receipt tokens and surface warranty status.

## POS Architecture

Main classes:

| Class | Responsibility |
| --- | --- |
| `PosFragment` | Product/service grid, search, mode chips, cart sheet/panel, scan actions, customer chip, payment navigation. |
| `PosViewModel` | Catalog state, cart mutation, customer selection, barcode/service token handling, price override warnings. |
| `CartManager` | In-memory active sale cart. Not persisted until payment commit. |
| `CartRepository` | Combines cart with settings/tax and selected customer into totals. |
| `PaymentDialogFragment` | Cash, mobile money, credit, voucher, split, mixed-payment UI. |
| `PaymentViewModel` | Payment validation, draft construction, voucher validation, SMS ref parsing, sale commit trigger. |
| `SaleRepository` | Atomic sale, debt, ledger, stock, service, and voucher persistence. |
| `MixedSaleAllocator` | Product/service/voucher subtotal allocation and paid/balance computation. |

Pro POS modes:

- Products
- Services
- Both

The cart can hold:

- Product lines.
- Service lines with optional staff name and override price.
- Voucher sale lines.

## Atomic Sale Commit

`SaleRepository.commitPosSale()` validates and writes the sale inside a Room transaction.

Validation includes:

- Cart must not be empty.
- Grand total must be positive.
- Service quantities must be positive.
- Product stock must still be available.
- Credit/deposit/balance sales require a customer.
- Voucher redemption must find a valid active voucher with remaining uses.

Commit writes:

1. One income `Transaction` with receipt number, payment fields, tax, product/service subtotals, amount paid, and balance due.
2. One `SaleLineItem` per product line.
3. Product stock decrement.
4. Ledger entry:
   - `SALE_PRODUCT`
   - `SALE_SERVICE`
   - `SALE_MIXED`
   - `VOUCHER_SALE`
   - `CREDIT_EXTENDED`
5. `Debt` row when balance is due.
6. `ServiceDelivery` rows for delivered services.
7. `ServiceVoucher` rows for voucher sale lines.
8. Voucher usage decrement for voucher payments.
9. Customer last-visit update.

The result returns the transaction ID and any issued voucher IDs so the receipt screen can show the full receipt and voucher QR.

## Payment Methods

`PaymentDraft` captures the payment intent before commit.

Supported primary tabs:

- Cash.
- Mobile money.
- Credit.
- Voucher.

Supported mixed-payment plans:

- `PAY_ALL`: everything paid now.
- `CREDIT_SERVICES`: product portion paid now, service portion on credit.
- `CREDIT_PRODUCTS`: service portion paid now, product portion on credit.
- `DEPOSIT`: partial payment now, remaining balance due later.

Split payment supports two methods and two amounts, usually cash plus mobile money.

## Receipts And Vouchers

`ReceiptFragment` renders the sale receipt using `ReceiptViewModel` and `PosReceiptAssembler`.

Receipt lines come from:

- `sale_line_items` for products.
- `service_deliveries` for service units.
- `service_vouchers` linked by `source_transaction_id` for prepaid vouchers.

Receipt details include:

- Business name.
- Receipt number.
- Date/time.
- Payment method and mobile money ref.
- Product/service/voucher lines.
- Subtotal, tax, grand total.
- Paid now and balance due when applicable.
- Footer.

`VoucherReceiptFragment` renders prepaid voucher QR/barcode cards. It supports sharing as an image and printing to PDF.

Return limitation:

- Current return flow is product-line based. Product returns restore stock and create refund ledger entries. Full service refund/credit-note workflow is not complete.

## Ledger Architecture

`LedgerRepository` is the sole write path for `ledger_entries`.

Design rules:

- Ledger entries are append-only.
- There is no general update/delete API.
- Corrections should be represented as `ADJUSTMENT` or other compensating entries.
- Each entry stores running balance at insert time.
- Daily recompute worker can recalculate balances from sorted entries.

Important ledger entry types:

| Type | Direction | Purpose |
| --- | --- | --- |
| `SALE_PRODUCT` | `MONEY_IN` | Product sale cash collected. |
| `SALE_SERVICE` | `MONEY_IN` | Service sale cash collected. |
| `SALE_MIXED` | `MONEY_IN` | Mixed product/service sale. |
| `VOUCHER_SALE` | `MONEY_IN` | Prepaid voucher sale. |
| `VOUCHER_REDEEMED` | `NEUTRAL` | Voucher usage event. |
| `CREDIT_EXTENDED` | `MONEY_IN` | Customer owes money. |
| `DEBT_REPAID` | `MONEY_IN` | Customer repayment. |
| `REFUND` | `MONEY_OUT` | Product return/refund. |
| `WARRANTY_CLAIM` | `MONEY_OUT` | Service warranty cost. |
| `STOCK_PURCHASE` | `MONEY_OUT` | Supplier stock purchase. |
| `EXPENSE` | `MONEY_OUT` | General expense. |
| `CASH_COUNT` | `NEUTRAL` | Physical cash check. |
| `OPENING_BALANCE` | `NEUTRAL` | Initial balance marker. |

Ledger UI is accessible from Today or Settings through Insights. Insights groups cash flow, credit, and ledger.

## P And L And Reporting

`LedgerPnLCalculator` combines ledger totals with transaction subtotals. Mixed sales use stored product/service subtotals so service revenue and product revenue can be reported separately.

`LedgerReportExporter` exports ledger reports and includes:

- Income breakdown.
- Product/service/voucher income.
- Other ledger type totals.
- Entries for the selected report period.

## Customers, Credit, And Debt

Customers are optional for normal cash sales and required for:

- Credit sales.
- Deposit/balance sales.
- Balance settlement.
- Voucher ownership when the user wants traceability.

`DebtRepository` and `SaleRepository` manage customer debts. Credit extension creates debt rows and ledger entries. Debt repayment records repayment and updates balances.

Open sale balances are tracked on `transactions.balance_due` and settlement uses `parent_transaction_id`.

## Chat Architecture

Main classes:

- `ChatFragment`
- `ChatViewModel`
- `ChatSessionRepository`
- `ConversationalQueryLayer`
- `GemmaAnswerFormatter`
- Built-in skills under `skills/builtin`

Chat behavior:

- Multi-session history is stored in `chat_sessions` and `chat_session_messages`.
- The active session ID is stored separately.
- Each session is capped at 400 messages.
- Recent transcript context can be injected into prompts.
- Structured query routes answer business questions from Room facts.
- Gemma polishes/phrases answers when available.
- The app falls back to deterministic answers when model support is missing.

Current chat message controls:

- Copy message.
- Edit message.
- Delete message.
- Delete chat session.
- Helpful/not helpful feedback on assistant rows.
- Optional image attachment path.

Chat message edits update `chat_session_messages.body`. Deletions remove only that message row. Session deletion cascades messages through the Room foreign key.

## AI And Inference

Gemma is loaded on-device through LiteRT-LM. The model is stored in the app private files directory after download.

Device capability tiers:

| Tier | Behavior |
| --- | --- |
| `FULL_AI` | Full model-assisted chat, weekly review, opportunity spotting, richer agents. |
| `PARTIAL_AI` | Shorter or limited model usage, more deterministic fallbacks. |
| `RULES_BASED` | No LLM dependency; Room/rule-based answers and alerts continue. |

Important AI responsibilities:

- Chat answer phrasing.
- Weekly business review narrative.
- Opportunity spotting.
- Cash-flow narrative.
- Demand forecast prompt path.
- Price override warnings.
- Supplier negotiation script.
- Mobile money SMS/reference extraction when available.

Important non-AI responsibilities:

- POS validation.
- Stock decrement.
- Payment commit.
- Debt creation.
- Ledger entries.
- Voucher usage.
- Service delivery.
- Returns.

## Learning And Feedback Loop

The current Pro code has an on-device learning loop based on persisted context, not model fine-tuning.

### KPI snapshots

`WeeklyReviewWorker` persists `BusinessKpiSnapshot` rows with:

- Week start.
- Week revenue.
- Last week revenue.
- Product revenue.
- Service revenue.
- Transaction count.
- New and returning customers.
- Top product and revenue.
- Top service and service session count.
- Best day/hour.
- Credit outstanding.

Today uses recent snapshots to build a daily brief, for example service-led revenue or top product focus.

### Demand forecast calibration

`DemandForecaster` stores each 3-day forecast in `forecast_calibrations`. `ForecastCalibrationResolver` later compares predictions to actual sold quantities and writes `biasRatio`.

Future forecasts load average bias per product and clamp correction to 0.5x to 1.5x. This gives product-specific calibration without changing model weights.

### Agent advice feedback

Agent cards support helpful/not-helpful votes. `AgentFeedViewModel` stores feedback in `agent_advice_feedback`.

If an owner marks a card not helpful:

- That card is dismissed.
- Similar content hashes are suppressed for a 21-day window.
- Feedback is retained for 180 days.

This prevents repeated reporting of the same unhelpful information.

### Business memory

`BusinessMemoryExtractor` writes `business_memory_entries` from:

- Weekly KPI memory.
- Recent owner chat messages that look like goals or preferences.

`AgentPromptComposer` prepends relevant business profile and business memory to agent prompts. `KnowledgeRetriever` can retrieve both static knowledge chunks and business memory entries, using embeddings when available and keyword fallback otherwise.

## RAG And Knowledge

Static knowledge pipeline:

1. Markdown assets are parsed into chunks.
2. Chunks are stored in `knowledge_chunks`.
3. If `EmbeddingEngine` is loaded, embedding blobs are stored.
4. Retrieval returns top chunks by cosine similarity or keyword fallback.

Business memory entries are converted to virtual chunks for retrieval. This lets the assistant use both app knowledge and this business's own history.

Embedding implementation note:

- `EmbeddingEngine` returns 384-dimensional vectors.
- It expects `assets/models/minilm-l6-v2.tflite`.
- Current tokenizer is a simple hash-based approximation, so retrieval must keep keyword fallback.

## Agent System

Agent types:

- `STOCK_GUARDIAN`
- `PRICING_AGENT`
- `CASH_FLOW`
- `CUSTOMER_RELATION`
- `FRAUD_SENTINEL`
- `WEEKLY_REVIEW`
- `OPPORTUNITY_SPOTTER`
- `LEDGER_ANOMALY`
- `VOUCHER_EXPIRY`
- `PRO_ONBOARDING`
- `UTILISATION_AGENT`
- `NO_SHOW_TRACKER`
- `SERVICE_PRICING_AGENT`

Scheduler:

- `AgentOrchestrator.scheduleAll()` reads `agent_settings`.
- Periodic work is registered through WorkManager.
- Pull-to-refresh calls `AgentOrchestrator.runAllNow()`.
- Heavy weekly AI workers require `FULL_AI` and active model availability.

Default schedule highlights:

| Agent | Schedule | Pro gate |
| --- | --- | --- |
| Stock guardian | Every 4 hours | No |
| Pricing agent | Daily | No |
| Cash flow | Daily | No |
| Customer relation | Daily | No |
| Fraud sentinel | Reactive | No |
| Weekly review | Weekly, charging and full AI | No, but includes Pro service blocks when Pro |
| Opportunity spotter | Weekly, charging and full AI | No |
| Voucher expiry | Daily | Yes |
| Utilisation agent | Daily | Yes |
| No-show tracker | Daily around 21:00 | Yes |
| Service pricing agent | Weekly | Yes |
| Ledger anomaly | Daily | No |

Agent actions are stored in `agent_actions`. Today sorts by urgency, collapses duplicates, hides rejected repeat hashes, and shows at most 20 rows.

## Today Screen

Today is powered by `AgentFeedViewModel`.

It combines:

- Pending agent actions.
- App settings.
- Current executing action.
- Recent agent advice feedback.
- Recent KPI snapshots.

UI state includes:

- Greeting.
- Date.
- Attention label.
- Today brief.
- Action cards.
- TTS flags.

Feedback suppression and KPI brief construction happen in the ViewModel, not in the LLM. This makes repeated-advice reduction deterministic.

## Supplier Visit

The supplier negotiation flow is reachable from Today and Inventory. It builds a negotiation guide from selected products and business data. Rich language generation needs Full AI/model availability; deterministic stock facts still come from Room.

## Business Profile

`business_profile` stores structured business identity:

- Business name.
- Owner name.
- Business type.
- Products/services.
- Specialisation.
- Target customer.
- Location.
- Opening days/hours.
- Staff count.
- Suppliers.
- Payment methods.
- Monthly revenue target.
- Business goal.
- Agent tone.
- Onboarding state.

`BusinessProfileRepository` syncs the business name back into `app_settings.business_name` and builds prompt context for agents.

## Settings

Settings controls:

- Device capability tier display.
- Model download/delete/benchmark.
- Inference configurations.
- Currency.
- Business profile.
- Voice settings.
- Agent settings/activity.
- Staff settings for Pro.
- Ledger shortcut.
- Licence key.
- WhatsApp helper and order parser.
- Optional cloud/Enterprise controls, hidden unless Enterprise edition is active.

`AppSettings` stores:

- Business name.
- Currency code/symbol.
- Tax rate/label.
- Receipt footer.
- Quick sale mode.
- Price override permission.
- Bluetooth printer address and paper width.
- Voice input/TTS preferences.
- Whisper model ID.
- Pro onboarding state.
- Voucher signing key.

## WhatsApp And Order Parser

The current app supports WhatsApp-adjacent workflows through Android intents and clipboard:

- `WhatsAppIntegration` can open WhatsApp if installed.
- Settings includes help for sharing orders into the app.
- `OrderParserActivity` can receive shared text or clipboard text.
- The parser tier may block advanced parsing if device capability is insufficient.

This is not a server-side WhatsApp Business API integration. It is a local Android integration pattern suitable for offline-first Pro.

## Scanning And OCR

Scanning features:

- Product barcode scan through CameraX and ML Kit Barcode.
- Service tokens through scanner/router for `BSVC:`, `BSRC:`, `BSVOU:`.
- Product label scan through ML Kit text recognition.
- Supplier receipt scan/review for inventory intake.
- Cash evidence scan and SMS import for ledger entries.

`BarcodeScanRouter` recognizes service prefixes and routes them to POS/service verification paths.

## Voice And TTS

Voice features include:

- Voice input toggle.
- Whisper model ID setting.
- Silence timeout.
- Language mode.
- TTS enable/disable.
- Speech rate and pitch.
- Auto-read agent alerts.
- Auto-read query answers.

The app still works when voice is disabled or unavailable.

## Security And Privacy

Default Pro privacy model:

- Business data is stored locally in Room.
- Gemma inference runs on-device.
- No cloud API is required for Pro operation.
- Optional sharing/export actions are user initiated.
- Licence key is verified with HMAC and stored in app preferences.
- Staff PINs are stored as hash/salt, not plain text.
- Service receipt verification uses HMAC-signed tokens.

Risks and production notes:

- The current development licence signing secret is embedded in app code and marked dev-only. Production should sign licences server-side and avoid shipping the signing secret.
- Room database is not encrypted by default in the inspected code. If threat model requires device-at-rest protection beyond Android sandboxing, add SQLCipher or Android Keystore-backed encryption.
- Enterprise/cloud endpoints should stay hidden for non-Enterprise users and require HTTPS plus bearer token when enabled.

## Performance And Stability Design

Stability choices:

- Kotlin repositories validate before writes.
- POS commits use Room transactions.
- AI is optional and guarded by capability tier/model availability.
- WorkManager constraints avoid heavy AI work on low battery or without charging where appropriate.
- Agent duplicate checks prevent repeated rows.
- Feedback suppression reduces repeated user-visible reports.
- Ledger balance recompute worker repairs running balance drift.
- Storage watchdog runs daily.
- Chat sessions are trimmed to a maximum message count.

Known performance-sensitive areas:

- First model download is about 2.5 GB.
- Full AI weekly review needs charging and sufficient RAM/storage.
- Embedding model availability depends on bundled asset presence.
- Room migrations should be tested on real upgrade paths before public release.

## Build And Release

Current Android config:

- `applicationId`: `com.biasharaai`
- `namespace`: `com.biasharaai`
- `minSdk`: 26
- `targetSdk`: 35
- `compileSdk`: 35
- Java/Kotlin target: 17
- Release minification: disabled in current build file.

Recommended release steps:

1. Build from the Pro release branch or a clean release candidate branch.
2. Confirm Pro licence activation on a fresh install.
3. Confirm Basic/Shop install still hides services, staff, service POS, and Enterprise controls.
4. Run Room migration tests for supported upgrade versions.
5. Run POS manual QA with product-only, service-only, mixed, credit, deposit, split, voucher, and return flows.
6. Verify model unavailable path on a low-tier device.
7. Verify model available path on a Full AI device.
8. Replace debug signing with production signing before store distribution.

## Test Strategy

Unit tests should cover:

- Product/service repository validation.
- `MixedSaleAllocator`.
- `SaleRepository` sale commit edge cases.
- Voucher redemption/expiry.
- Service token parsing.
- Ledger running balance math.
- Demand forecast parsing/calibration.
- Agent dedupe and feedback suppression.
- Chat message edit/delete/copy state updates.
- Licence parser and product-line gating.

Instrumented tests should cover:

- Room migrations.
- DAO joins and transaction queries.
- POS full flow on database.
- Barcode/scan routing where possible.
- Settings visibility by licence.

Manual QA checklist:

- Fresh install as Shop: no Services tab, no Pro service POS.
- Apply Pro licence: Services tab appears.
- Add product, scan barcode, sell product, print/share receipt.
- Add service, print service QR, scan service token into POS.
- Sell product plus service in one cart.
- Sell prepaid voucher, view voucher QR, redeem voucher.
- Sell deposit sale, settle balance later.
- Sell credit sale, verify debt and ledger entries.
- Return product from receipt, verify stock restored and refund ledger row.
- Mark agent card not helpful, refresh, verify similar report is hidden.
- Edit/copy/delete a chat message.
- Run Today refresh with model unavailable and model available.

## Known Limitations

- Service returns/refunds are not yet a full first-class workflow.
- Current release config has minification disabled.
- Production licence signing needs a server-side signing process.
- Database encryption is not enabled by default.
- Embedded Enterprise code exists in the workspace but should stay hidden/no-op for non-Enterprise users.
- Embedding tokenizer is still approximate; keyword fallback is important.
- LLM output should not be treated as a source of truth for financial writes.

## Operational Support Notes

When diagnosing user issues, collect:

- App version and build.
- Licence product line and edition shown in Settings.
- Device capability tier.
- Whether model is downloaded.
- Android version and free storage.
- Whether the issue happens offline.
- Transaction receipt number or ledger entry date/time.
- For service issues: service token, voucher ID, or receipt token.
- For repeated AI advice: whether the owner used helpful/not-helpful feedback.

Do not ask users to clear app data unless export/backup has been handled, because all operational business data is local.
