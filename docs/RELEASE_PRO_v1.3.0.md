# Biashara AI Pro — Stable Release v1.3.0

**Branch:** `release/pro-stable-v1.3.0`  
**Version:** `1.3.0` (versionCode **20**)  
**AppDatabase:** **33**  
**Package:** `com.biasharaai`  
**Target:** Android 8.0+ (API 26), compile/target SDK 35

This release line ships **Biashara AI Pro**: services POS, mixed payments, business intelligence profile, net-of-returns analytics, and unified receipts for product + service + voucher sales.

---

## Highlights

### Pro POS — Services & vouchers
- **Services catalog** on Sales tab (Products / Services / Both modes).
- **Unified cart** shows products, services (with staff subtitle), and prepaid voucher lines.
- **Mixed payment plans:** pay all, deposit + balance, credit products only, credit services only.
- **Voucher issue & redeem** (CODE-128), staff picker, service price override.
- **Collect balance** on open sales with `balance_due`.

### Receipts (fixed in this release)
- **`PosReceiptAssembler`** builds one receipt from:
  - `sale_line_items` (products)
  - `service_deliveries` (services)
  - `service_vouchers` (prepaid, linked via `source_transaction_id`)
- Payment flow always opens the **full sale receipt**; voucher QR via **View prepaid voucher QR** button.
- Deposit sales show **Paid now** and **Balance due** on receipt.

### Business intelligence profile (Phase 10)
- `business_profile` table (migration **31→32**).
- Conversational onboarding + settings edit UI.
- `QueryBusinessProfileSkill`, `BusinessContextBuilder`, `AgentPromptComposer` wired into agent workers.
- Knowledge chunks: `assets/knowledge/{en,sw}/business_profile.md`.

### Sales intelligence (net of returns)
- **`SalesIntelligenceRepository`** — single source for net revenue and product ranks.
- Returns no longer inflate “top seller” / weekly review / chat / cash-flow figures.
- `ProductDao.sumUnitsSoldForProductInPeriod` nets INCOME + RETURN lines.

### Pro agent workers
- Utilisation, no-show, service pricing, voucher expiry agents (Pro licence gate).
- `QueryServicesSkill`, service blocks in weekly review and cash-flow sentinel.

### Schema migrations (27 → 33)
| Migration | Change |
|-----------|--------|
| 28→29 | Services: `service_items`, `service_deliveries`, `service_vouchers`, staff |
| 29→30 | Appointments |
| 30→31 | Mixed sale columns on `transactions` |
| 31→32 | `business_profile` |
| 32→33 | `service_vouchers.source_transaction_id` (receipt linkage) |

---

## Build & install

```bash
./gradlew assembleRelease
```

Signed APK (debug keystore for pilot installs):

```
app/build/outputs/apk/release/biasharaai-v1.3.0-release.apk
```

Configure a **release keystore** in `keystore.properties` before Play Store submission.

---

## Verification checklist

- [ ] Sell **product + service** in one cart → receipt lists **both** lines; subtotal matches total.
- [ ] Sell product → **return** → weekly review / chat do not praise returned SKU as top seller.
- [ ] Pro licence enabled → Services tab visible; agents report service revenue.
- [ ] Issue prepaid voucher with product sale → receipt shows voucher line + QR button.
- [ ] Fresh install migrates from v27+ without crash (or clean install on v33).

---

## Documentation

| Doc | Purpose |
|-----|---------|
| `HANDOFF.md` | Engineering handoff, schema, branch map |
| `USER_MANUAL.md` | End-user guide (source for PDF) |
| `docs/PRO_TECHNICAL_DOCUMENTATION.md` | Deep Pro architecture, stack, data model, agents, AI, ledger, and QA reference |
| `docs/PRO_USER_MANUAL.md` | Full Pro end-user manual for product/service sales, vouchers, ledger, chat, and daily workflows |
| `docs/generate_user_manual_pdf.py` | Regenerate `USER_MANUAL.pdf` |

---

## Known limitations

- Release APK in CI/local builds may be signed with **debug** keystore until `signingConfigs` is configured.
- `ConversationalQueryLayer` structured routes still use some gross POS line facts (net sales use `SalesIntelligenceRepository` elsewhere).
- Service **returns** are product-line-item based; full service refund flows may need a follow-up release.

---

## Git

```bash
git checkout release/pro-stable-v1.3.0
git pull origin release/pro-stable-v1.3.0
```

Previous stable line: `release/biashara-phase4` (pre-Pro services stack).
