# Biashara AI Enterprise Sync Service

This is the current server-side ingestion contract for Enterprise deployments.
The target architecture is documented in
[`docs/ENTERPRISE_REARCHITECTURE.md`](../../docs/ENTERPRISE_REARCHITECTURE.md).
Android
Enterprise devices post queued sync envelopes to this service. The service
stores every envelope idempotently and materializes:

- central product catalog
- central service catalog
- stock movement ledger
- registered devices
- branches
- audit events

It uses Python standard-library modules only and stores data in SQLite, so it can
run on-premise without a package install step. For cloud deployment, run the same
service behind HTTPS.

## Run Locally

### Docker Compose

```powershell
cd server\enterprise-sync-service
$env:BIASHARA_ENTERPRISE_SYNC_TOKEN = "biashara-enterprise-local-test-token"
docker compose up -d --build
```

Health check:

```powershell
curl http://127.0.0.1:8088/health
```

The SQLite database is stored in the Docker volume
`enterprise-sync-service_enterprise_sync_data`.

LAN auto-discovery listens on UDP `8099`. The Android Enterprise app broadcasts
a discovery probe and fills the endpoint from the server response.

### Direct Python

```powershell
$env:BIASHARA_ENTERPRISE_SYNC_TOKEN = "change-this-token"
python server\enterprise-sync-service\enterprise_sync_server.py --host 127.0.0.1 --port 8088 --db C:\tmp\biashara_enterprise_sync.db
```

For local development only:

```powershell
python server\enterprise-sync-service\enterprise_sync_server.py --dev-no-auth
```

## Android Endpoint

In the Enterprise app settings, set:

```text
https://your-domain.example/v1/enterprise/sync
```

For on-premise APK testing against a Docker server on your PC, select
`On-premise` and use your PC LAN address:

```text
http://192.168.x.x:8088/v1/enterprise/sync
```

The Android client allows `http://` only for Enterprise `ON_PREMISE` endpoints
whose host is `localhost`, `.local`, `127.x.x.x`, `10.x.x.x`, `172.16-31.x.x`,
or `192.168.x.x`. Cloud mode still requires `https://`.

The Android app already sends:

- `Authorization: Bearer <token>`
- `X-Biashara-Export-Type: enterprise-sync-outbox`
- `X-Biashara-Deployment-Mode: ON_PREMISE` or `CLOUD`
- `X-Biashara-Payload-Type: ...`

Use HTTPS in production. For on-premise production installs, put this service
behind Nginx, Caddy, Traefik, or a customer gateway with a trusted TLS
certificate.

The same endpoint accepts all Enterprise upload buttons:

- `enterprise-sync-outbox`: queued device sync events
- `business-analytics-json`: full business JSON snapshot
- `sqlite-database`: full Room SQLite database upload

## Read APIs

All read APIs require the same bearer token.

```text
GET /health
GET /v1/enterprise/stats?businessId=<id>
GET /v1/enterprise/catalog/products?businessId=<id>
GET /v1/enterprise/catalog/services?businessId=<id>
GET /v1/enterprise/stock/movements?businessId=<id>&limit=100
GET /v1/enterprise/events?businessId=<id>&limit=100
```

Append `includeDeleted=true` to catalog endpoints when an admin console needs
tombstones.

## Payloads Consumed

- `DEVICE_REGISTRATION`
- `BRANCH_UPDATED`
- `AUDIT_EVENT`
- `CATALOG_PRODUCT_UPSERTED`
- `CATALOG_PRODUCT_DELETED`
- `CATALOG_SERVICE_UPSERTED`
- `CATALOG_SERVICE_DELETED`
- `STOCK_MOVEMENT_RECORDED`

Catalog upserts are version-aware. If a device sends an older catalog version
than the central record, the server keeps the central row and records a conflict
in `catalog_conflicts`.

## Cloud vs On-Premise

Use the same binary and schema for both:

- **On-premise:** customer hosts the service and SQLite database inside their
  network, usually behind a reverse proxy with TLS.
- **Cloud:** managed deployment hosts the service behind a public HTTPS
  endpoint; use a managed disk/volume for the SQLite file or replace the store
  with PostgreSQL later without changing the Android envelope contract.
