# Architecture Documentation

Documentation date: 2026-09-04

This directory is the engineering entry point for Biashara AI architecture. Documents distinguish the current implementation from accepted future direction so deployment teams do not mistake a roadmap for a delivered control.

## Start Here

- [Current state](CURRENT_STATE.md): software that exists in this repository today, how it runs, and known limitations.
- [Growth architecture](GROWTH_ARCHITECTURE.md): target design for small stores first and medium stores later.
- [Voice agent architecture](VOICE_AGENT_ARCHITECTURE.md): safe natural conversation over the existing agent and tool platform.
- [Phone sync protocol v1](SYNC_PROTOCOL_V1.md): implemented request contract, authentication, compatibility, and limits.
- [ADR-0001](adr/0001-modular-monolith-deployment-profiles.md): decision to use one modular product with multiple deployment profiles.
- [ADR-0002](adr/0002-versioned-signed-phone-sync.md): decision to share and sign the phone-desktop protocol.
- [Security threat model](../security/THREAT_MODEL.md): assets, boundaries, threats, current controls, and required controls.

## Documentation Rules

1. Code and automated tests are the implementation truth.
2. A roadmap item must be labelled `Planned` until its acceptance tests pass.
3. Architectural changes require an Architecture Decision Record (ADR).
4. Sync and API changes require a versioned contract and backward-compatibility notes.
5. Security-sensitive changes require a threat-model update.
6. Every schema change requires migration and rollback tests.
7. User-facing behavior must also be reflected in the user manual or release notes.

## Historical Documents

Documents elsewhere under `docs/` contain valuable product and release history. Some refer to earlier database versions or proposed dependencies. Use this directory for the current architecture baseline and verify historical implementation claims against source before relying on them.
