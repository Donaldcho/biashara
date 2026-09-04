# Current System State

Documentation date: 2026-09-04

## Product Boundary

Biashara AI currently consists of an offline-first Android application and a standalone Windows desktop application. The initial market is a small store owner operating one phone, one desktop workstation, or both. Medium-store capabilities are a growth target, not a claim about the present deployment.

## Android Application

- Kotlin Android application in `app/`.
- Single-activity UI with fragments and ViewModels.
- Room database at schema version 41.
- Local product, service, customer, sale, ledger, chat, agent, and settings data.
- Camera and barcode workflows.
- On-device AI with deterministic fallbacks.
- Desktop Link screen and bridge client for pairing, scanning, catalogue transfer, transaction transfer, and reconciliation.

Android remains independently usable when the desktop is unavailable.

## Desktop Application

The desktop product has two runtime parts:

- `desktop-standalone/`: Java application server, local persistence, POS, phone bridge, LM Studio integration, agent tools, and bundled HTML/CSS/JavaScript UI.
- `desktop-shell-windows/`: Windows WebView2 shell that starts the Java server and presents it as a standalone application.

The local UI server binds to loopback ports starting at 8765. The phone bridge binds to LAN ports starting at 8865 and exposes a restricted set of mobile routes. Operational desktop records currently use TSV and properties files below `%USERPROFILE%/.biasharaai-desktop-pro`; product images are stored as files.

## Local AI And Agents

- Android uses its device model when installed and falls back to deterministic business rules.
- Desktop supports deterministic rules and an OpenAI-compatible LM Studio endpoint.
- Desktop agents use registered tool contracts, per-agent allow-lists, immutable business snapshots, bounded model turns, and append-only local run audit records.
- Current agent tools are read-only. They cannot alter inventory, ledger, services, customers, messages, or settings.

## Mobile/Desktop Synchronization

Implemented bridge capabilities include:

- One-time pairing code followed by a session key; the displayed code rotates after successful pairing.
- Pairing throttles for 30 seconds after five failed code attempts.
- Shared protocol `1.0` contract used by Android and desktop.
- HMAC-SHA256 request signatures over method, path, protocol version, request identity, timestamp, nonce, and body hash.
- Five-minute request validity window and in-memory replay rejection on the desktop.
- Session-scoped legacy compatibility: old sessions remain usable until an upgraded client sends a signed request; capable new pairings are signed-only immediately.
- Product and service catalogue exchange.
- Product images transferred to desktop file storage.
- Phone barcode scans sent to the desktop POS.
- Mobile transaction upload and desktop transaction reconciliation.
- Business settings exchange.
- Retry protection for selected transaction identifiers.

Current limitations:

- Protocol `1.0` authenticates requests but is not yet a complete operation log.
- Several records still use snapshot-style reconciliation.
- LAN pairing and request bodies still use HTTP and do not yet provide production-grade mutual device identity or transport confidentiality.
- Session keys are stored in application preferences/files rather than Android Keystore and the Windows credential vault.
- The desktop store rewrites flat files and returns the full application state to the UI.
- The catalogue UI is not yet server-paginated or virtualized.
- The desktop is suitable for one workstation, not concurrent cashier terminals.

## Build And Test

Android from the repository root:

```powershell
.\gradlew.bat testDebugUnitTest
.\gradlew.bat :app:assembleDebug
```

Desktop:

```powershell
.\gradlew.bat -p desktop-standalone clean test installDist --offline --no-daemon
```

Windows shell:

```powershell
dotnet publish desktop-shell-windows\BiasharaDesktopShell.csproj -c Release -o desktop-shell-windows\publish
```

## Deployment Classification

The current desktop release is a `Solo` deployment:

- One active desktop POS.
- Optional paired mobile device.
- Local-first operation.
- Local LM Studio is optional and never required for checkout.

Do not deploy the current flat-file desktop store as a shared network database. Do not run independent cashier databases and attempt to reconcile absolute stock totals.
