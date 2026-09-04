# Small-To-Medium Growth Architecture

Status: Accepted direction; implementation is incremental.

## Goals

1. Keep installation and daily operation simple for a small store owner.
2. Preserve offline sales, stock, services, credit, and receipts.
3. Allow an existing business to grow into multiple staff and cashier terminals without replacing the product.
4. Make security, schema evolution, sync compatibility, and recovery testable.
5. Keep AI optional and outside the checkout-critical path.

## Architecture Style

Use a modular monolith with ports and adapters. Business rules live in domain and application modules. Android, desktop UI, HTTP, databases, device discovery, LM Studio, speech, printers, and WhatsApp are adapters.

Do not introduce microservices, distributed queues, or Kubernetes for the small-store product. A process boundary is added only when the medium-store deployment needs a local authoritative server.

## Deployment Profiles

### Solo

- One phone, one desktop, or both.
- Embedded SQLite database on each application.
- Versioned operation synchronization between paired devices.
- Local image files with generated thumbnails.
- Optional local AI model.

### Store

- One local store server with PostgreSQL.
- Multiple cashier, manager, technician, and stock-taking clients over LAN.
- The store server is authoritative while clients keep bounded offline command queues.
- Real-time change notifications plus cursor-based catch-up.
- Optional LM Studio on the manager workstation or store server.

### Multi-Branch

This is not an initial-market requirement. The future profile adds a branch-local server and asynchronous replication to a central control plane. Checkout must remain available when the internet or central service is unavailable.

## Logical Modules

- Identity, roles, devices, and cashier shifts.
- Catalogue, price book, tax, promotions, and media.
- Inventory movements, receiving, counts, damage, and transfers.
- Sales, carts, payments, returns, receipts, and credit.
- Services, work tickets, staff assignment, warranty, and vouchers.
- Ledger and auditable accounting events.
- Customers, consent, messaging, and WhatsApp exports.
- Sync outbox, inbox, cursors, conflicts, and protocol compatibility.
- Agent runtime, business tools, memory, voice, and feedback.

Modules expose commands and queries. They do not modify another module's tables directly.

## Data Decisions

1. Replace desktop operational TSV storage with SQLite behind repository interfaces. Preserve an import path and a backup before migration.
2. Represent stock as append-only movements. Never reconcile concurrent devices by replacing an absolute quantity.
3. Commit a sale, payment, stock movement, ledger event, customer balance, and receipt number in one transaction.
4. Give commands globally unique IDs and idempotency keys.
5. Add `business_id`, `device_id`, `origin`, `revision`, and timestamps to synchronized records.
6. Store images as content-addressed files; keep metadata and hashes in the database.
7. Use indexed queries and paginated APIs instead of returning the full catalogue.

This is pragmatic command/query separation, not full event sourcing. Immutable financial and stock facts are retained, while ordinary catalogue metadata remains updateable with version checks.

## Sync Contract

Every client maintains:

- Transactional outbox of locally accepted operations.
- Deduplicating inbox of remotely accepted operations.
- Cursor per peer or server.
- Recorded conflicts requiring deterministic policy or human review.

The receiver acknowledges an operation only after committing it. Retrying the same operation returns the original outcome. Protocol versions are additive within a major version; unsupported major versions fail with an actionable upgrade message.

## Performance Targets

- 5,000 products without loading every card or image into the DOM.
- First catalogue page in 300 ms on supported hardware.
- Indexed barcode lookup in 150 ms or less.
- Checkout operations remain independent of AI availability.
- Initial image sync is resumable; normal sync transfers only changed records.

## Delivery Sequence

1. Freeze current behavior with tests, architecture documentation, and a recoverable branch checkpoint.
2. Introduce repository contracts and an embedded SQLite desktop adapter.
3. Add stock movements, idempotent sale commands, and migration tests.
4. Version the sync envelope and implement transactional outbox/inbox processing.
5. Add paginated catalogue queries, thumbnail generation, and virtualized rendering.
6. Add staff roles, cashier shifts, device revocation, and security audit events.
7. Deliver read-only conversational voice, then separately approved write commands.
8. Add the Store deployment with PostgreSQL and concurrent transaction tests.

Each step must preserve the Solo workflow and produce a reversible data migration.
