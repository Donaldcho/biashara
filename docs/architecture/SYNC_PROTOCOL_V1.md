# Phone Sync Protocol 1.0

Documentation date: 2026-09-04

## Scope

Protocol `1.0` protects Android-to-desktop bridge commands. It is implemented in the shared `sync-contract` module, Android `DesktopBridgeClient`, and desktop `PhoneRequestAuthenticator`.

Public discovery routes:

```text
GET  /api/phone/discovery
GET  /api/phone/capabilities
POST /api/phone/pair
```

Protected routes:

```text
POST /api/phone/scan
POST /api/phone/product-sync
POST /api/phone/transaction-sync
POST /api/phone/reconcile
POST /api/phone/stock-intake
```

## Pairing

The mobile pairing body includes the displayed code, device name, and supported protocol versions:

```json
{
  "token": "AB12CD34",
  "deviceName": "Stock phone",
  "supportedProtocolVersions": ["1.0"]
}
```

The desktop returns a new session secret, selected version, and authentication scheme:

```json
{
  "sessionKey": "generated-session-secret",
  "protocolVersion": "1.0",
  "authentication": "HMAC-SHA256"
}
```

After a successful response, the desktop rotates the displayed pairing code so it cannot be used for a second pairing. Five failed pairing-code attempts trigger a 30-second lockout and HTTP `429` with `Retry-After`.

The pairing response currently crosses the local network over HTTP. Treat the network as untrusted and do not describe this as production-grade secure pairing.

## Signed Requests

Every protected `1.0` request sends:

```text
X-Biashara-Session
X-Biashara-Protocol
X-Biashara-Request-Id
X-Biashara-Timestamp
X-Biashara-Nonce
X-Biashara-Signature
```

The canonical UTF-8 string is:

```text
UPPERCASE_HTTP_METHOD
REQUEST_PATH_WITHOUT_QUERY
PROTOCOL_VERSION
REQUEST_ID
TIMESTAMP_MILLISECONDS
NONCE
LOWERCASE_SHA256_HEX_OF_EXACT_BODY_BYTES
```

The signature is lowercase hexadecimal HMAC-SHA256 of that canonical string, keyed by the paired session secret.

The desktop validates the session, version, required headers, clock window, signature, and replay key before invoking a business handler. A repeated request ID and nonce receives HTTP `409`. Stale or invalid credentials receive `401`; unsupported versions or a forbidden legacy downgrade receive `426`.

## Compatibility

- A new mobile client advertises `1.0`; its new session is signed-only.
- A session stored before this update starts in legacy-compatible mode.
- When an upgraded client uses that old session and sends a valid signed request, the desktop persists a signed-only promotion.
- An old client that pairs without advertising `1.0` receives a legacy-compatible session for the migration period.

Legacy compatibility is temporary release support, not the target security model. Removal requires an announced minimum mobile version and migration telemetry.

## Idempotency

Transport replay rejection is not business idempotency. Sale, stock, ledger, and settings operations must also carry stable operation identifiers and be recorded in the planned transactional inbox before medium-store deployment.

## Next Protocol Work

1. Device public-key identity and explicit revocation.
2. Authenticated encrypted LAN transport.
3. Keystore and Windows credential-vault storage.
4. Transactional inbox/outbox and monotonic sync cursors.
5. Chunked, resumable media transfer.
6. Contract fixtures shared across Android and desktop integration tests.
