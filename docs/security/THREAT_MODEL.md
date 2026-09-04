# Security Threat Model

Documentation date: 2026-09-04

## Scope

This threat model covers the Android application, Windows desktop application, local data, phone bridge, local AI, future voice input, backups, and the planned medium-store LAN server.

## Protected Assets

- Sales, payments, receipt numbers, stock movements, and ledger history.
- Customer names, phone numbers, credit, service history, and consent.
- Business settings, staff roles, device registrations, and licence data.
- WhatsApp and integration credentials.
- AI prompts, transcripts, attachments, tool results, and business memory.
- Backups and exported reports.

## Trust Boundaries

1. Android application process and private storage.
2. Desktop application, loopback UI server, and local filesystem.
3. Phone-to-desktop LAN connection.
4. Future cashier-to-store-server LAN connection.
5. LM Studio or another local model server.
6. External integrations including WhatsApp.
7. Backup media and exported files.

A shared Wi-Fi network is not a trusted boundary.

## Primary Threats

- Lost or stolen phone or laptop exposing customer and financial data.
- Unauthorised LAN client pairing with the desktop bridge.
- Replay of sale, stock, refund, or sync operations.
- Shared cashier credentials and untraceable privileged actions.
- Malicious or accidental modification of append-only financial records.
- Plaintext tokens in settings, logs, backups, or Git history.
- Prompt injection causing an AI agent to disclose data or execute an unsafe tool.
- Voice misrecognition creating a financially meaningful command.
- Corrupt, incomplete, or untested backups.
- Dependency or installer tampering.

## Current Controls

- Android private application storage and Room migrations.
- Desktop UI API bound to loopback.
- Restricted phone bridge route set.
- Pairing code and session key.
- One-time pairing-code rotation and failed-attempt throttling.
- Versioned phone sync requests signed with HMAC-SHA256.
- Timestamp, nonce, and request-ID validation with bounded replay rejection.
- Signed-only promotion prevents a capable paired session from downgrading to legacy bearer authentication.
- Idempotent identifiers for selected synchronization operations.
- Read-only desktop agent tools with explicit allow-lists and bounded execution.
- Local deterministic AI fallback.
- Git ignores signing material, model files, local environment files, and generated build output.

## Required Controls Before General Deployment

### Identity And Access

- Owner, manager, cashier, technician, and stock-controller roles.
- Unique user identities; shared privileged accounts are prohibited.
- Slow salted password/PIN hashing, retry throttling, and session timeout.
- Supervisor approval for refunds, voids, large discounts, settings, and stock corrections.

### Device And Transport

- QR pairing establishes a device public key, not only a reusable bearer value.
- Short-lived challenges prevent replay.
- Per-device credentials are scoped, rotatable, and revocable.
- Authenticate and encrypt LAN traffic for production deployments.
- Maintain a device activity and revocation screen.

### Data And Secrets

- Store long-lived integration secrets in Android Keystore or the operating-system credential vault.
- Encrypt portable backups and include integrity authentication.
- Minimise customer data in logs and agent audit output.
- Define retention and deletion policies for chats, transcripts, attachments, and exports.

### Transaction Integrity

- Use database transactions for sale, payment, stock, ledger, and receipt changes.
- Require idempotency keys for retried commands.
- Make financial, stock, authentication, and permission events append-only and attributable.
- Test concurrent checkout, power loss, migration failure, replay, and restore.

### AI And Voice

- Keep tools deny-by-default and schema validated.
- Read-only queries may run immediately.
- Write actions require role checks, deterministic validation, and explicit confirmation.
- Payment completion, refunds, stock destruction, and customer messaging must never rely solely on model interpretation.
- Do not retain raw audio by default.

## Security Release Gate

A release cannot be called production-ready until it has:

- Updated threat model and dependency inventory.
- Passing migration, authorization, replay, and backup-restore tests.
- No credentials or personal datasets in the repository or installer.
- Documented incident, device-revocation, backup, and recovery procedures.
