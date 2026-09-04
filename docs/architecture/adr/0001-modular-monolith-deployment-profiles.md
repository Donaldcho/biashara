# ADR-0001: Modular Monolith With Deployment Profiles

- Status: Accepted
- Date: 2026-09-04
- Decision owners: Biashara AI engineering

## Context

Biashara AI must serve small stores now while allowing medium stores to add staff, terminals, and a local server. Separate Solo and Store products would duplicate business rules and make upgrades risky. Premature microservices would increase installation, observability, security, and support costs.

## Decision

Build one modular business core using ports and adapters.

- `Solo` runs the application and SQLite adapter locally.
- `Store` runs the same application services behind a LAN API with a PostgreSQL adapter.
- Platform code remains outside domain modules.
- Sync, AI, speech, WhatsApp, printers, and databases are adapters.
- Module boundaries are enforced in source and tests before any module becomes a separate process.

## Consequences

Positive:

- Small-store deployment stays simple.
- Core behavior is reused by Android and desktop.
- Database and model choices can change without rewriting business rules.
- A business can upgrade without changing its conceptual data model.
- Tests can run against domain services without UI or network dependencies.

Costs:

- Repository and application boundaries require disciplined code review.
- SQLite and PostgreSQL adapters need shared contract tests.
- Cross-module writes must be replaced with application commands.
- Deployment-profile compatibility must be tested continuously.

## Rejected Alternatives

- Separate codebases by edition: high duplication and migration risk.
- Peer-to-peer cashier databases: unsafe concurrent inventory and receipt ownership.
- Microservices now: operational complexity without current scale justification.
- Network-shared SQLite file: unsupported concurrency and corruption risk.
