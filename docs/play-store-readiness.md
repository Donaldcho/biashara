# Play Store Readiness

This file tracks release preparation without changing the app's core positioning: offline-first SME business control with local AI, POS, Money Inbox, generated ledger, and optional cloud.

## Release Build

Create a private `keystore.properties` file from `keystore.properties.example`. Do not commit it.

```powershell
copy keystore.properties.example keystore.properties
```

Then set the real keystore path and passwords. Build the release bundle with:

```powershell
.\gradlew.bat :app:bundleRelease
```

If `keystore.properties` is missing, release builds remain unsigned and are not ready for Play upload.

## Network Policy

Release builds use `app/src/main/res/xml/network_security_config.xml`, which disables cleartext HTTP. Debug builds use a debug resource override that permits cleartext for local development and LAN testing.

For Play production, cloud AI gateways, enterprise upload endpoints, and payment-provider callbacks should use HTTPS.

## Store Listing Positioning

Use sovereignty-safe wording:

- "Offline-first POS, money inbox, ledger, stock, debt, and business assistant for SMEs."
- "Records and reconciles payments."
- "Optional cloud AI through an owner-controlled gateway."

Avoid claiming direct money transfer until licensed payment-provider integrations are approved and live.

## Release checklist (v1.4.0)

- [ ] Fresh install on Android 8+ (minSdk 26)
- [ ] Upgrade install from v1.3.0 / DB v40 → v41 (Money Inbox)
- [ ] Chat: no `<|channel|>`, `end_of_turn`, or fake `user:` turns in bubbles
- [ ] Agent feed: dismiss/approve suppresses repeat alerts for 21 days
- [ ] Money Inbox: scan/SMS/manual → draft → approve → ledger
- [ ] Optional cloud AI disabled by default; gateway HTTPS in production
- [ ] Release AAB signed with `keystore.properties`
- [ ] Privacy policy URL live in Settings


- Host `docs/privacy-policy.md` at the URL configured by `settings_privacy_policy_url`.
- Complete Data Safety for business records, customer contact data, financial records, camera, microphone, optional cloud AI, optional enterprise upload, diagnostics, and support reports.
- Complete AI-generated content declarations and point reviewers to the chat message "Report AI issue" action.
- Complete financial features declarations if the listing mentions payments, debts, expenses, or business finance.
- Verify Android 15+ 16 KB native page-size support for the final AAB because the app uses native AI/audio/ML libraries.
- Run closed testing on low-end Android phones and Android POS devices before production.
