# Biashara AI Enterprise Rearchitecture

## Decision

Enterprise must be a different architecture from Basic and Pro. Basic and Pro
remain local-first Android products. Enterprise becomes a server-owned system
with Android devices acting as branch terminals, offline caches, and event
producers.

The current Enterprise implementation adds sync, device registration, branch
metadata, audit events, catalog IDs, and stock movement upload to the single
device app. That is useful as a bridge, but it should not become the final
enterprise architecture because it still makes each phone behave like the
primary business database.

Enterprise source of truth moves to the Enterprise server.

## Product Line Boundary

| Product line | Data authority | Connectivity | Scope |
| --- | --- | --- | --- |
| Basic | Android Room database | Offline only | Single shop/device |
| Pro | Android Room database | Offline first | Single business, richer local features |
| Enterprise | Enterprise server | LAN/cloud with offline cache | Multi-device, multi-branch, governed data |

Enterprise code can reuse UI and domain components where that is pragmatic, but
it must not rely on the single-device Room database as the authority for shared
catalog, stock, staff, permissions, or analytics.

## Target Topology

```text
                 Cloud deployment or customer on-prem deployment

        +---------------------------------------------------------+
        |                 Enterprise Server                       |
        |---------------------------------------------------------|
        | API Gateway                                             |
        | Auth, tenants, devices, roles, audit                    |
        | Central product catalog                                 |
        | Central service catalog                                 |
        | Stock ledger and branch inventory                       |
        | Sales/event ingestion                                   |
        | AI memory, KPI snapshots, feedback loop                 |
        | WhatsApp integration adapter                            |
        +---------------------------+-----------------------------+
                                    |
                          PostgreSQL or SQLite
                          object storage/backups
                                    |
        +---------------------------+-----------------------------+
        |                           |                             |
 +-------------+             +-------------+              +-------------+
 | Branch POS  |             | Manager app |              | Admin web   |
 | Android     |             | Android     |              | console     |
 +-------------+             +-------------+              +-------------+
```

The same server image should run in both modes:

- On-premise: customer hosts Docker Compose or Kubernetes inside their network.
- Cloud: managed HTTPS endpoint with the same API contract and database schema.

Android only changes endpoint discovery and auth bootstrap between these modes.

## Server Owned Domains

| Domain | Enterprise authority | Android responsibility |
| --- | --- | --- |
| Product catalog | Server owns ID, version, status, price, barcode, category, deletion | Cache catalog, request edits, sell cached products |
| Service catalog | Server owns ID, version, duration, price mode, visibility | Cache services, request edits, use in POS |
| Stock | Server owns branch stock ledger and available quantities | Record movements locally when offline, push events |
| Sales | Server stores finalized sale events and line items | Capture sale, print receipt, queue if offline |
| Staff/roles | Server owns users, roles, permissions, device limits | Cache permissions and enforce local checks |
| Branches/devices | Server owns registrations and activation status | Discover server, register device, receive branch assignment |
| AI memory | Server owns durable KPI snapshots, advice feedback, repeated insight suppression | Show local answer, send feedback/events, cache latest insights |
| WhatsApp | Server owns integration credentials, webhooks, templates, audit | Android may trigger approved messages, not hold business WhatsApp secrets |

## Enterprise Client Model

Enterprise Android should be split from the local product behavior through
interfaces, not by scattering edition checks through every screen.

```text
Inventory UI
    |
    v
CatalogGateway
    |-- LocalCatalogGateway       Basic/Pro, direct Room authority
    |-- EnterpriseCatalogGateway  Enterprise, server authority plus local cache

POS UI
    |
    v
SalesGateway
    |-- LocalSalesGateway         Basic/Pro, direct Room writes
    |-- EnterpriseSalesGateway    Enterprise, local event queue plus server sync
```

Enterprise local Room tables become cache and queue tables:

- `enterprise_catalog_cache`
- `enterprise_service_cache`
- `enterprise_stock_cache`
- `enterprise_command_outbox`
- `enterprise_change_inbox`
- `enterprise_sync_cursors`
- `enterprise_conflicts`

Existing Product and ServiceItem tables can still be used by Basic/Pro. For
Enterprise, shared catalog fields should eventually come from cache tables or
read models produced from the server snapshot.

## Sync Protocol

The current server accepts upload envelopes. Enterprise needs bidirectional sync.

Minimum API v1:

```text
GET  /health
GET  /v1/enterprise/discovery
POST /v1/enterprise/devices/register
GET  /v1/enterprise/bootstrap?businessId=&deviceId=
GET  /v1/enterprise/changes?businessId=&deviceId=&cursor=
POST /v1/enterprise/commands
POST /v1/enterprise/events
GET  /v1/enterprise/catalog/products?businessId=&since=
GET  /v1/enterprise/catalog/services?businessId=&since=
GET  /v1/enterprise/stock/snapshot?businessId=&branchId=
GET  /v1/enterprise/ai/today?businessId=&branchId=
POST /v1/enterprise/ai/feedback
```

Command examples:

- `PRODUCT_CREATE_REQUESTED`
- `PRODUCT_UPDATE_REQUESTED`
- `SERVICE_CREATE_REQUESTED`
- `SERVICE_UPDATE_REQUESTED`
- `PRICE_CHANGE_REQUESTED`
- `STOCK_ADJUSTMENT_REQUESTED`
- `SALE_COMPLETED`
- `RETURN_COMPLETED`

Server responses must include:

- accepted/rejected status
- central IDs
- server version
- conflict reason when rejected
- next sync cursor

## Conflict Rules

The server is version authoritative.

Catalog writes:

1. Client sends `centralId`, `baseVersion`, and requested patch.
2. Server accepts only if `baseVersion` matches current server version or policy
   allows an automatic merge.
3. Server increments version and emits a change record.
4. Other devices pull the change.

Stock writes:

1. Client never edits stock quantity directly.
2. Client sends stock movement events.
3. Server appends to the ledger and calculates branch stock.
4. Negative stock is controlled by enterprise policy.

Offline sales:

1. Device can finalize sale locally if it has a valid catalog snapshot and
   policy permits offline operation.
2. Device queues sale event with idempotency key.
3. Server accepts once, updates ledger, and returns authoritative stock.
4. If conflict occurs, server records reconciliation task instead of dropping the
   sale.

## AI Architecture

Enterprise AI should not rely on each phone asking Gemma against only its local
Room state.

Server-owned AI data:

- `business_kpi_snapshots`
- `forecast_predictions`
- `forecast_errors`
- `ai_advice_feedback`
- `business_memory_entries`
- `insight_deduplication_log`
- successful Q&A history embeddings

Android Enterprise behavior:

- show the server generated Today insight feed when connected
- fall back to local deterministic summaries when offline
- send thumbs up/down, copy/edit/delete message actions, accepted advice signals
- suppress repeated advice using the server insight log

This keeps Gemma fixed but improves the context and feedback loop. The model
does not need fine-tuning to learn business patterns.

## WhatsApp Integration

WhatsApp belongs on the Enterprise server, not inside the Android APK.

Server responsibilities:

- hold WhatsApp Business API credentials
- receive webhook callbacks
- enforce template approval and consent
- store outbound/inbound message audit
- connect messages to customers, invoices, orders, and supplier visits

Android responsibilities:

- request a server-approved message
- show delivery status
- never store long-lived WhatsApp secrets

## Deployment Modes

Both deployment modes use the same logical product.

On-premise:

- Docker Compose first, Kubernetes later
- customer-owned database volume
- LAN discovery for Android bootstrap
- reverse proxy/TLS recommended for production
- backup export job included

Cloud:

- managed HTTPS endpoint
- managed database
- object storage for uploads/backups
- email/SMS/WhatsApp integrations enabled centrally
- tenant isolation by `business_id`

The APK should not care whether the server is cloud or on-prem after bootstrap.
It should only know endpoint, tenant, device identity, and permissions.

## Migration From Current Implementation

Keep what already works:

- current Enterprise sync outbox
- central product/service materialization on the server
- stock movement ingestion
- device registration
- branch registration
- LAN discovery
- on-prem/cloud endpoint setting

Change the ownership model in phases:

1. Add server read models and upload inspection APIs.
2. Add server bootstrap and pull changes endpoints.
3. Add Android Enterprise gateway interfaces.
4. Move Enterprise catalog screens to read server cache instead of Product DAO
   authority.
5. Move Enterprise POS sale completion to queued server events.
6. Add server AI Today feed, feedback, and repeated insight suppression.
7. Add admin console for catalog, stock, devices, branches, roles, and uploads.
8. Mark full SQLite upload as diagnostic/export only, not normal sync.

## First Implementation Slice

The first real engineering slice should be small and structural:

1. Extend the server with:
   - business summary endpoint
   - uploaded analytics/database listing endpoints
   - catalog snapshot endpoint
   - stock snapshot endpoint
   - sync cursor table
2. Add Android Enterprise client interfaces:
   - `CatalogGateway`
   - `StockGateway`
   - `SalesGateway`
   - `EnterpriseBootstrapRepository`
3. Wire only Enterprise edition to the new gateways.
4. Leave Basic and Pro screens backed by their existing repositories.
5. Add tests that prove Basic/Pro still use local repositories while Enterprise
   uses server/cache gateways.

This gives Enterprise its own spine without breaking the working local product.
