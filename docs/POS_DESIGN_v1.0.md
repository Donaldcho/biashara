# Biashara AI — Point of Sale (POS) Design Document

**Version:** 1.0
**Date:** May 2026
**Status:** Design — implementation tracked on branch `feat/pos-module`
**Scope:** Replace the `SalesFragment` placeholder with a production-grade POS
module integrated with the existing v2.0 codebase.

---

## 1. Overview

The current Biashara AI application has a `SalesFragment` placeholder but no
true Point of Sale system. This document specifies a complete, production-grade
POS module designed for African SME contexts:

- **Offline-first** — every flow works in Airplane Mode.
- **Local-language** — all UI strings localized (English, Swahili, Hausa,
  Yoruba, Amharic).
- **Multiple payment methods** — cash, mobile money (M-Pesa / MTN / Airtel),
  credit on account, split payments.
- **Barcode-accelerated product lookup** — reuses the existing CameraX
  `BarcodeAnalyzer`.
- **Digital receipts** — shareable text and optional Bluetooth thermal printing.

The POS module is the highest-traffic part of the app. A market vendor may
process **50–200 transactions per day**. Every interaction must be fast,
forgiving, and work flawlessly without internet.

> **Builds on existing work.** This module replaces the `SalesFragment`
> placeholder. It reuses: Room `Product` / `Transaction` / `Customer` entities,
> CameraX `BarcodeAnalyzer`, `GemmaService`, `DeviceCapabilityChecker`,
> `CustomerSuggestionEngine` (Phase 2), `Debt` entity (Phase 2). **No existing
> entity schemas change** — only additive columns and new entities.

---

## 2. POS user flows

### 2.1 Primary sale flow

1. Tap the **Sales** tab. The POS screen opens immediately — no loading state
   on a warm start.
2. Optionally select a customer from the customer chip (reuses
   `CustomerSelectorBottomSheet` from Phase 2).
3. Add products to the cart via one of three methods:
   1. **Scan barcode**
   2. **Search by name**
   3. **Browse the product grid**
4. The cart updates in real time — item count, line totals, subtotal, tax, and
   grand total.
5. Select payment method: **Cash**, **Mobile Money**, or **Credit (on
   account)**.
6. **Cash** — enter amount tendered → app shows change due → tap **Confirm
   sale**.
7. **Mobile Money** — app shows total and a reference field → tap **Confirm
   sale**.
8. **Credit** — requires a customer to be selected → confirms amount → creates
   a `Debt` record (Phase 2 entity).
9. Sale is committed to the Room database. `Transaction` and `SaleLineItem`
   records inserted **atomically**.
10. Digital receipt screen shown. User can share as text, print via Bluetooth
    thermal printer, or dismiss.

### 2.2 Quick Sale flow (speed-optimized for high-volume vendors)

For market vendors who need maximum speed, **Quick Sale** mode shows only a
numeric keypad and a product search field. No grid browsing. The flow is:

```
scan or type product name → quantity → scan or type next → Collect cash → Done
```

**Target:** complete a 3-item sale in **under 15 seconds**.

### 2.3 Return / refund flow

1. From the Transactions history screen, user finds the original sale.
2. Taps **Return / Refund**. Selects which line items are being returned and
   the quantity for each.
3. App creates a negative `Transaction` (`type = RETURN`) and restores stock
   via `ProductDao`.
4. If original payment was **Cash**, a cash refund is noted. If **Credit**,
   the `Debt` is reduced.

---

## 3. Screen designs

### 3.1 POS main screen layout

The POS screen uses a **two-panel** layout on tablets (product grid left, cart
right) and a **single-panel** layout on phones (cart is a collapsible bottom
sheet).

| Zone | Component | Behavior |
|---|---|---|
| **Top bar** | Customer chip + Mode toggle (Standard / Quick) | Tap customer chip to open `CustomerSelectorBottomSheet`. Mode persists per session. |
| **Search bar** | Product search field + scan icon | Type to filter. Scan icon opens `BarcodeScannerFragment` in `SCAN_TO_ADD` mode. Results appear as a dropdown list. |
| **Product grid** | `RecyclerView` grid — 2 cols phone, 3 cols tablet | Cards show name, price, stock badge. Tap to add 1 to cart. Long-press to set quantity. |
| **Cart panel** | LiveData `RecyclerView` of `CartLineItem` | Swipe to remove. Tap quantity to edit. Running total at bottom. |
| **Totals bar** | Subtotal, Tax (configurable %), Grand total | Tax rate set in Settings. Updates instantly. |
| **Payment bar** | Cash / Mobile Money / Credit chips + Confirm | Chips at bottom of screen. Tapping Cash opens the amount-tendered dialog. |

### 3.2 Cart line item

| Field | Display | Editable |
|---|---|---|
| Product name | Full name, truncated at 28 chars | No |
| Unit price | Formatted with currency symbol | **Yes** — tap to override for this sale only |
| Quantity | Stepper control (− / count / +) | **Yes** |
| Line total | `price × qty`, auto-calculated | No |
| Remove | Swipe left reveals red delete | Gesture |

### 3.3 Payment dialogs

| Method | Dialog content | On confirm |
|---|---|---|
| **Cash** | Amount tendered field. Change due shown in large green text. Confirm button. | Insert `Transaction(SALE, CASH)`. Show receipt. |
| **Mobile Money** | Reference field (optional). Network selector (M-Pesa / MTN / Airtel / Other). Amount shown. | Insert `Transaction(SALE, MOBILE_MONEY)`. Show receipt. |
| **Credit / On Account** | Requires customer. Shows customer name + outstanding balance. Optional due date. | Insert `Transaction(SALE, CREDIT)`. Insert `Debt`. Show receipt. |
| **Split payment** | Two-method split — e.g. part cash, part mobile money. Each amount editable. | Insert two `Transaction` records linked by a `saleGroupId`. |

### 3.4 Digital receipt screen

Shown after every successful sale. Contains:

- Business name (from Settings) and date/time
- Line items list with quantities and prices
- Subtotal, tax, total, amount tendered, change due
- Payment method and reference (if Mobile Money)
- Customer name (if sale was linked to a customer)
- A receipt number, formatted: `BA-YYYYMMDD-NNNN`

**Action buttons:** _Share as text_ (Android Share sheet), _Print via Bluetooth_
(opens printer selector), _New sale_ (returns to empty POS), _Done_ (navigates
to Home).

---

## 4. Data model

### 4.1 New entity — `SaleLineItem`

| Field | Type | Notes |
|---|---|---|
| `id` | `Long` | Primary key, auto-generated |
| `transactionId` | `Long` | FK → `Transaction`, `onDelete = CASCADE` |
| `productId` | `Long` | FK → `Product` |
| `productName` | `String` | Snapshot at time of sale (product may be renamed later) |
| `unitPrice` | `Double` | Snapshot at time of sale |
| `quantity` | `Int` | Quantity sold |
| `lineTotal` | `Double` | `unitPrice × quantity`, stored for fast receipt rendering |

### 4.2 In-memory only — `CartItem` (not persisted)

| Field | Type | Notes |
|---|---|---|
| `product` | `Product` | Full `Product` object from Room |
| `quantity` | `Int` | Default 1, user-adjustable |
| `overridePrice` | `Double?` | Set if user manually changed unit price for this sale |

### 4.3 Additive columns on existing `Transaction`

| New field | Type | Notes |
|---|---|---|
| `paymentMethod` | `String` | `CASH` \| `MOBILE_MONEY` \| `CREDIT` \| `SPLIT` |
| `mobileMoneyNetwork` | `String?` | `M-PESA` \| `MTN` \| `AIRTEL` \| `OTHER` — null for non-MoMo sales |
| `mobileMoneyRef` | `String?` | Reference number entered by user |
| `amountTendered` | `Double?` | For cash sales — enables change calculation in receipt |
| `changeDue` | `Double?` | `amountTendered - total` |
| `receiptNumber` | `String` | Auto-generated: `BA-YYYYMMDD-NNNN` |
| `saleGroupId` | `String?` | UUID — links two `Transaction` rows for a split payment |
| `taxRate` | `Double` | Tax rate applied, snapshotted from Settings at sale time |
| `taxAmount` | `Double` | Calculated tax amount stored for receipt accuracy |

### 4.4 Database migrations

`AppDatabase` migrates from **v5** (end of Phase 2) to **v7** for the POS
module:

| Migration | Changes |
|---|---|
| **v5 → v6** | Create `sale_line_items` table. Add `paymentMethod`, `mobileMoneyNetwork`, `mobileMoneyRef`, `amountTendered`, `changeDue`, `receiptNumber`, `saleGroupId`, `taxRate`, `taxAmount` columns to `transactions` table. Back-fill existing rows: `paymentMethod = 'CASH'`, `receiptNumber = 'BA-LEGACY-<id>'`, `taxRate = 0.0`. |
| **v6 → v7** | Create new single-row `app_settings` table with `tax_rate` (Double, default 0.0) and `currency_code` (String, default `KES`). |

Migrations are exercised by `androidx.room:room-testing` instrumented tests
covering forward migration **and** schema validation.

---

## 5. AI integration in POS

| Feature | How AI helps | Device tier | Trigger |
|---|---|---|---|
| **Product suggestion chips** | `CustomerSuggestionEngine` shows top 3 past purchases as quick-add chips | All tiers (rule-based) | Customer selected |
| **Price override warning** | If user sets price > 40% below normal, Gemma asks _"Is this a discount or an error?"_ in the user's language | `PARTIAL_AI`+ | User edits unit price |
| **End-of-day summary** | Gemma generates a plain-language daily summary: top seller, total revenue, busiest hour, notable anomalies | `FULL_AI` | Tapping _Close day_ in Settings |
| **Mobile money network detection** | Gemma parses a pasted USSD / SMS confirmation message and extracts amount + reference number automatically | `FULL_AI` | User pastes text into reference field |

All AI calls go through the existing `GemmaService.generateStreaming(...)` so
they inherit the warm-up, cancellation, mutex, and capability-tier behavior
already shipped in v1.1.2.

---

## 6. Bluetooth receipt printing

Biashara AI supports optional printing to common low-cost Bluetooth thermal
printers (58mm and 80mm roll width) used widely across African markets —
typically priced under $30 USD.

### 6.1 Supported printer protocol

**ESC/POS** command protocol — the de-facto standard for thermal receipt
printers. A lightweight ESC/POS Kotlin library handles formatting. No
printer-specific SDK required.

### 6.2 Printer setup flow

1. User goes to **Settings → Receipt Printer → Pair Printer**.
2. App scans for paired Bluetooth devices and filters by device class
   (`Imaging / Printer`).
3. User selects their printer. A test print is sent.
4. Paper width (58mm or 80mm) is selected. This setting is saved.
5. From that point, the **Print** button on the receipt screen sends directly
   to the paired printer.

### 6.3 Receipt format (ESC/POS)

The receipt includes:

- Centered business name (bold, large)
- Divider lines
- Left-aligned line items with right-aligned prices
- Totals section
- Payment method, receipt number
- Thank-you message in the user's language

Formatted using ESC/POS text justification and bold commands — **no bitmap
image required**, so printing is fast even on cheap printers.

---

## 7. Settings required

| Setting | Location | Default |
|---|---|---|
| Business name | Settings → Business Profile | "My Business" |
| Currency code | Settings → Business Profile | `KES` |
| Tax rate (%) | Settings → Sales | `0%` (set to local VAT rate) |
| Tax label | Settings → Sales | "Tax" (localized) |
| Receipt footer message | Settings → Receipts | "Thank you!" (in user's language) |
| Bluetooth printer | Settings → Receipt Printer | None paired |
| Quick Sale mode | Settings → Sales | Off |
| Price override allowed | Settings → Sales | On |

---

## 8. Performance requirements

| Metric | Target | Why it matters |
|---|---|---|
| POS screen cold start | **< 400 ms** | Vendors cannot wait — they have a queue of customers |
| Product search response | **< 100 ms** for 10,000 products | Instant feel is non-negotiable at the counter |
| Barcode scan → cart add | **< 800 ms** end-to-end | Faster than manually finding a product |
| Sale commit (Room insert) | **< 200 ms** | User should see receipt appear immediately |
| Receipt print (Bluetooth) | **< 3 s** | Standard expectation for thermal printers |
| Cart with 50 line items | **No jank — 60 fps RecyclerView** | Edge case for bulk orders |

Benchmarks live next to feature code:

- Cart RecyclerView frame time — `app/src/androidTest/.../pos/CartPerfTest.kt`
- Room insert latency — `app/src/androidTest/.../pos/SaleCommitPerfTest.kt`

---

## 9. Implementation phases

Tracked as separate commits on `feat/pos-module`.

| Phase | Deliverable |
|---|---|
| **P1 — Data layer** | `SaleLineItem` entity, DAO, Migration v5→v6 and v6→v7, instrumented migration tests. |
| **P2 — Core UI** | New `PosFragment` replacing the placeholder, cart `RecyclerView`, product grid, customer chip. |
| **P3 — Payment flows** | Cash / Mobile Money / Credit dialogs, split-payment flow, atomic commit through `TransactionRepository.commitSale(...)`. |
| **P4 — Receipt** | Digital receipt screen, Android Share Sheet, receipt number generation, snapshotted line items. |
| **P5 — Quick Sale** | Numeric-keypad layout, single-screen flow, persisted mode toggle. |
| **P6 — Returns** | `RETURN` transaction type, stock restoration, Debt reduction. |
| **P7 — Bluetooth printing** | ESC/POS formatter, printer pairing screen, test print. |
| **P8 — AI hooks** | Price-override warning, end-of-day summary, USSD-text parsing. |
| **P9 — Polish + telemetry** | Performance instrumentation against §8 targets, localized strings for all five locales, accessibility pass. |

---

## 10. References

- ESC/POS command reference — <https://reference.epson-biz.com/modules/ref_escpos/>
- Android Bluetooth Classic (non-BLE) for printing — <https://developer.android.com/develop/connectivity/bluetooth/connect-bluetooth-devices>
- Room atomic transactions — <https://developer.android.com/training/data-storage/room/async-queries#transaction>
- Biashara AI Development Handbook v2.0 — existing architecture this module builds on (`Biashara_AI_Development_Handbook_v2.pdf` in repo root)
- Biashara AI Upgrade Design Document v3.0 — Phase 2 entities reused by this module
