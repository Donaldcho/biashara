# ADR-0002: Versioned And Signed Phone Sync

- Status: Accepted and implemented
- Date: 2026-09-04
- Decision owners: Biashara AI engineering

## Context

The original local bridge authenticated protected requests with a reusable session value. It had no protocol negotiation, body integrity check, request expiry, or general replay rejection. Android and desktop also owned separate implicit knowledge of the wire contract, which made compatibility changes risky.

## Decision

Create a dependency-free `sync-contract` Java module and consume it from both Android and desktop.

Protocol `1.0` uses:

- An explicit protocol-version header.
- A unique request ID and nonce.
- A Unix timestamp in milliseconds.
- HMAC-SHA256 over a canonical request and SHA-256 body digest.
- Constant-time comparison for session credentials and signatures.
- A five-minute clock-skew window and bounded desktop replay cache.

The pairing request advertises client-supported protocol versions. A capable new pairing is signed-only. A session created by an older client may use legacy session authentication, but the first signed request permanently promotes that session to signed-only. This preserves installed clients without allowing a capable session to downgrade later.

## Consequences

Positive:

- Android and desktop compile against the same protocol constants and signing implementation.
- Modified, stale, and replayed signed requests are rejected before business handlers run.
- Unsupported protocol versions produce an upgrade response instead of undefined behavior.
- Existing paired installations keep a controlled migration path.

Costs and limitations:

- Phone and laptop clocks must be within five minutes.
- The replay cache is process-local; timestamp expiry still bounds replay after restart.
- HMAC does not encrypt data. The current LAN HTTP transport can expose business data and the pairing exchange to a network observer.
- Legacy sessions do not receive replay protection until upgraded and promoted.
- Device public-key identity, revocation, operating-system credential storage, and encrypted transport remain release-gate work.

## Rejected Alternatives

- Duplicate signing code in Android and desktop: likely to drift and fail interoperability.
- Immediate rejection of every legacy session: unacceptable breakage for installed small-store clients.
- Treat HMAC as a substitute for TLS: it provides integrity and authentication, not confidentiality.
- Introduce a remote identity service now: conflicts with offline-first Solo deployment and does not remove the need for local device identity.
