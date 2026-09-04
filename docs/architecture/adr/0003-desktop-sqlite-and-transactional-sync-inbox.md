# ADR-0003: Desktop SQLite And Transactional Sync Inbox

- Status: Accepted and implemented
- Date: 2026-09-04
- Decision owners: Biashara AI engineering

## Context

The desktop application stored operational state in multiple TSV files. A sale or synchronization command could therefore update several in-memory aggregates and then partially rewrite files if the process, disk, or machine failed. Flat-file scans also do not provide the indexed access needed for a larger catalogue. Transport replay protection did not prevent the same business operation from being submitted in a newly signed request.

## Decision

Keep the existing `DesktopStore` application boundary and place a `DesktopStateRepository` port behind it. Use an embedded SQLite adapter for the Solo desktop deployment.

The adapter:

- Stores settings, products, services, customers, transactions, sale lines, service tickets, scan events, synchronization history, sync inbox receipts, and stock movements in indexed tables.
- Commits a complete desktop state snapshot and its sync inbox receipt in one SQLite transaction.
- Uses WAL mode, full synchronous writes, foreign-key enforcement, and a five-second busy timeout.
- Creates a pre-migration ZIP before importing legacy TSV data.
- Continues a best-effort TSV mirror after every successful database commit so the previous branch can read current data during rollback.
- Creates backup exports from a consistent SQLite `VACUUM INTO` snapshot.
- Rejects a database with a newer schema version instead of attempting a destructive downgrade.
- Serializes desktop and phone write commands while leaving AI streaming and read operations outside the write lock.

Protected mobile operations may include a stable `operationId`. The desktop stores its payload hash and original HTTP outcome. Repeating the same ID and business payload returns the original outcome. Reusing the ID with different data returns HTTP `409`. Session credentials are excluded from the business-payload hash so legitimate re-pairing does not invalidate a retry.

## Consequences

Positive:

- Multi-record persistence is atomic on the Solo desktop.
- A restart no longer loses the inbox record while keeping its associated stock or sale change.
- Barcode, catalogue, transaction, and inbox lookups have database indexes.
- Legacy data migrates automatically and has a clear rollback artifact.
- Mobile transaction and catalogue retries have stable business identities.

Costs and limitations:

- The compatibility facade still rewrites a complete database snapshot and TSV mirror; it is not the final incremental repository implementation.
- The in-memory state remains the current UI read model. Server-side catalogue pagination and image virtualization are still required.
- Android does not yet have a general durable desktop-sync outbox. Existing transaction memory, stock mutation IDs, and content-addressed catalogue IDs are transitional mechanisms.
- The stock movement table covers new desktop sales, mobile sales, phone stock reconciliation, and stock intake. Historical stock is not reconstructed during migration.
- SQLite remains a one-workstation Solo database. Medium-store cashier concurrency requires the Store profile and its authoritative server.

## Rejected Alternatives

- Continue coordinating multiple flat-file renames: complex recovery behavior with no useful query model.
- Store one opaque JSON document in SQLite: atomic but still unsuitable for indexed catalogue, ledger, and sync queries.
- Expose the SQLite file over a network share: unsafe concurrency and recovery behavior.
- Introduce PostgreSQL for the Solo release: adds installation and support cost before multiple terminals are supported.
