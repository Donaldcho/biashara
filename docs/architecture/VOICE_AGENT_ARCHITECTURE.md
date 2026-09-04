# Conversational Voice Agent Architecture

Status: Accepted direction; deliver in controlled phases.

## Product Experience

The user should speak naturally without choosing a technical agent first. A conversation coordinator determines whether the request concerns inventory, sales, ledger, services, credit, sync, or general business review, then invokes only the relevant registered tools.

Voice and text share the same conversation, permissions, agent definitions, business memory, and audit trail. Voice is an input/output adapter, not an independent source of business rules.

## Processing Pipeline

```text
Push to talk
  -> voice activity detection
  -> streaming speech-to-text adapter
  -> transcript preview/correction
  -> conversation coordinator
  -> permissioned agent and tools
  -> streamed response
  -> optional text-to-speech adapter
```

Speech engines must implement interfaces so Android system speech, an offline model, Windows speech, or a future server engine can be substituted without changing agent logic.

## Safety Classes

### Read-Only

Examples: sales totals, low stock, customer credit, service queue, and sync health. These may run after transcription and display their evidence.

### Drafting

Examples: prepare a marketing message, purchase list, or customer reminder. The agent creates a draft; the user reviews before it is saved or sent.

### Mutating

Examples: add stock, change a price, create a sale, issue a refund, complete a service, or send a message. The system must:

1. Show the interpreted structured command.
2. Read back quantities, money, customer, and consequences.
3. Require explicit confirmation.
4. Re-check the user's role and current data.
5. Execute through an idempotent application command.
6. Record an audit event.

High-risk operations may require a supervisor even after voice confirmation.

## Initial Voice Release

- Push-to-talk rather than an always-listening wake word.
- Streaming partial transcript with cancel and edit.
- Read-only questions across the existing desktop agent tools.
- Optional spoken response with replay and stop controls.
- Text remains available when speech quality is poor.
- Raw audio is discarded after transcription by default.
- Transcript history follows the same retention controls as chat.

## Later Releases

- Draft marketing and customer-service messages.
- Confirmed service booking and stock-intake commands.
- Multilingual speech packs and per-language quality benchmarks.
- Store-profile voice routing by staff role and active workstation.

Voice-controlled payment finalization, refunds, and destructive inventory changes are excluded until authorization, command confirmation, replay protection, and audit tests are complete.

## Acceptance Criteria

- A transcript can be edited before execution.
- Cancelling speech cancels transcription and pending model work.
- The selected agent cannot access a tool outside its allow-list.
- Money and quantity values are parsed and validated deterministically.
- No write occurs from an unconfirmed voice command.
- Audio is not retained unless the user explicitly enables retention.
- Checkout continues when speech or AI is unavailable.
