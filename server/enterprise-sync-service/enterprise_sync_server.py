#!/usr/bin/env python3
"""
Biashara AI Enterprise sync ingestion service.

The Android Enterprise client posts one sync envelope at a time. This server
accepts those envelopes, stores the raw event idempotently, and materializes the
central product catalog, service catalog, and stock movement ledger in SQLite.
It uses only Python standard-library modules so it can run on customer premises
without a package install step.
"""

from __future__ import annotations

import argparse
import hmac
import json
import os
import re
import socket
import sqlite3
import threading
import time
import uuid
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any
from urllib.parse import parse_qs, urlparse


MAX_JSON_BODY_BYTES = 32 * 1024 * 1024
MAX_SQLITE_UPLOAD_BYTES = 256 * 1024 * 1024
SYNC_PATHS = {"/", "/sync", "/enterprise/sync", "/v1/enterprise/sync"}
DISCOVERY_PROBE = b"BIASHARA_ENTERPRISE_DISCOVERY_V1"
DISCOVERY_SERVICE = "biashara-enterprise-sync"
DEFAULT_DISCOVERY_PORT = 8099
DEFAULT_SYNC_PATH = "/v1/enterprise/sync"


class ApiError(Exception):
    def __init__(self, status: HTTPStatus, message: str):
        super().__init__(message)
        self.status = status
        self.message = message


class EnterpriseSyncStore:
    def __init__(self, db_path: str):
        self.db_path = db_path
        self.upload_dir: str | None = None
        if db_path != ":memory:":
            parent = os.path.dirname(os.path.abspath(db_path))
            if parent:
                os.makedirs(parent, exist_ok=True)
                self.upload_dir = os.path.join(parent, "uploads")
                os.makedirs(self.upload_dir, exist_ok=True)
        self.db = sqlite3.connect(db_path, check_same_thread=False)
        self.db.row_factory = sqlite3.Row
        if db_path != ":memory:":
            self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA foreign_keys=ON")
        self.migrate()

    def migrate(self) -> None:
        with self.db:
            self.db.executescript(
                """
                CREATE TABLE IF NOT EXISTS sync_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    event_id TEXT NOT NULL UNIQUE,
                    received_at_epoch_ms INTEGER NOT NULL,
                    business_id TEXT NOT NULL,
                    branch_id INTEGER,
                    device_id TEXT NOT NULL,
                    destination_mode TEXT NOT NULL,
                    payload_type TEXT NOT NULL,
                    payload_entity_id TEXT NOT NULL,
                    queued_at_epoch_ms INTEGER NOT NULL,
                    envelope_json TEXT NOT NULL,
                    payload_json TEXT NOT NULL,
                    UNIQUE (
                        business_id,
                        device_id,
                        payload_type,
                        payload_entity_id,
                        queued_at_epoch_ms
                    )
                );

                CREATE INDEX IF NOT EXISTS index_sync_events_business_received
                    ON sync_events (business_id, received_at_epoch_ms);
                CREATE INDEX IF NOT EXISTS index_sync_events_payload
                    ON sync_events (payload_type, payload_entity_id);

                CREATE TABLE IF NOT EXISTS catalog_products (
                    business_id TEXT NOT NULL,
                    central_id TEXT NOT NULL,
                    local_id INTEGER NOT NULL,
                    version INTEGER NOT NULL,
                    sync_status TEXT NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    price REAL NOT NULL,
                    cost REAL NOT NULL,
                    stock_quantity INTEGER NOT NULL,
                    category TEXT,
                    barcode_value TEXT,
                    image_url TEXT,
                    deleted_at_epoch_ms INTEGER,
                    last_event_id TEXT NOT NULL,
                    PRIMARY KEY (business_id, central_id)
                );
                CREATE INDEX IF NOT EXISTS index_catalog_products_business_name
                    ON catalog_products (business_id, name COLLATE NOCASE);
                CREATE INDEX IF NOT EXISTS index_catalog_products_business_barcode
                    ON catalog_products (business_id, barcode_value);

                CREATE TABLE IF NOT EXISTS catalog_services (
                    business_id TEXT NOT NULL,
                    central_id TEXT NOT NULL,
                    local_id INTEGER NOT NULL,
                    version INTEGER NOT NULL,
                    sync_status TEXT NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    base_price REAL NOT NULL,
                    price_mode TEXT NOT NULL,
                    duration_minutes INTEGER NOT NULL,
                    category TEXT,
                    catalogue_token TEXT NOT NULL,
                    warranty_days INTEGER NOT NULL,
                    visible_in_kiosk INTEGER NOT NULL,
                    deleted_at_epoch_ms INTEGER,
                    last_event_id TEXT NOT NULL,
                    PRIMARY KEY (business_id, central_id)
                );
                CREATE INDEX IF NOT EXISTS index_catalog_services_business_name
                    ON catalog_services (business_id, name COLLATE NOCASE);
                CREATE INDEX IF NOT EXISTS index_catalog_services_business_token
                    ON catalog_services (business_id, catalogue_token);

                CREATE TABLE IF NOT EXISTS stock_movements (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    business_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    movement_id INTEGER NOT NULL,
                    local_product_id INTEGER NOT NULL,
                    enterprise_product_id TEXT,
                    movement_type TEXT NOT NULL,
                    quantity_delta INTEGER NOT NULL,
                    stock_after INTEGER,
                    source_type TEXT,
                    source_id TEXT,
                    note TEXT,
                    created_at_epoch_ms INTEGER NOT NULL,
                    received_event_id TEXT NOT NULL,
                    UNIQUE (business_id, device_id, movement_id)
                );
                CREATE INDEX IF NOT EXISTS index_stock_movements_product_created
                    ON stock_movements (business_id, enterprise_product_id, created_at_epoch_ms);

                CREATE TABLE IF NOT EXISTS registered_devices (
                    business_id TEXT NOT NULL,
                    device_id TEXT NOT NULL,
                    display_name TEXT,
                    deployment_mode TEXT,
                    app_version_name TEXT,
                    max_devices_snapshot INTEGER,
                    first_seen_at_epoch_ms INTEGER,
                    last_seen_at_epoch_ms INTEGER,
                    is_active INTEGER NOT NULL DEFAULT 1,
                    payload_json TEXT NOT NULL,
                    last_event_id TEXT NOT NULL,
                    PRIMARY KEY (business_id, device_id)
                );

                CREATE TABLE IF NOT EXISTS branches (
                    business_id TEXT NOT NULL,
                    branch_id INTEGER NOT NULL,
                    code TEXT NOT NULL,
                    name TEXT NOT NULL,
                    location TEXT,
                    is_default INTEGER NOT NULL,
                    is_active INTEGER NOT NULL,
                    updated_at_epoch_ms INTEGER NOT NULL,
                    payload_json TEXT NOT NULL,
                    last_event_id TEXT NOT NULL,
                    PRIMARY KEY (business_id, branch_id)
                );

                CREATE TABLE IF NOT EXISTS audit_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    business_id TEXT NOT NULL,
                    device_id TEXT,
                    action TEXT,
                    entity_type TEXT,
                    entity_id TEXT,
                    summary TEXT,
                    created_at_epoch_ms INTEGER,
                    payload_json TEXT NOT NULL,
                    received_event_id TEXT NOT NULL UNIQUE
                );
                CREATE INDEX IF NOT EXISTS index_audit_events_business_created
                    ON audit_events (business_id, created_at_epoch_ms);

                CREATE TABLE IF NOT EXISTS catalog_conflicts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    received_event_id TEXT NOT NULL,
                    business_id TEXT NOT NULL,
                    catalog_type TEXT NOT NULL,
                    central_id TEXT NOT NULL,
                    current_version INTEGER NOT NULL,
                    incoming_version INTEGER NOT NULL,
                    reason TEXT NOT NULL,
                    incoming_json TEXT NOT NULL,
                    created_at_epoch_ms INTEGER NOT NULL
                );

                CREATE TABLE IF NOT EXISTS business_analytics_exports (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    export_id TEXT NOT NULL UNIQUE,
                    received_at_epoch_ms INTEGER NOT NULL,
                    business_id TEXT NOT NULL,
                    app_version_name TEXT,
                    exported_at_epoch_ms INTEGER,
                    raw_size_bytes INTEGER NOT NULL,
                    payload_json TEXT NOT NULL
                );
                CREATE INDEX IF NOT EXISTS index_business_analytics_exports_business_received
                    ON business_analytics_exports (business_id, received_at_epoch_ms);

                CREATE TABLE IF NOT EXISTS database_uploads (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    upload_id TEXT NOT NULL UNIQUE,
                    received_at_epoch_ms INTEGER NOT NULL,
                    business_id TEXT NOT NULL,
                    filename TEXT NOT NULL,
                    content_type TEXT NOT NULL,
                    raw_size_bytes INTEGER NOT NULL,
                    stored_path TEXT,
                    payload_blob BLOB
                );
                CREATE INDEX IF NOT EXISTS index_database_uploads_business_received
                    ON database_uploads (business_id, received_at_epoch_ms);
                """
            )

    def close(self) -> None:
        self.db.close()

    def ingest(self, envelope: dict[str, Any]) -> dict[str, Any]:
        self._validate_envelope(envelope)
        payload = envelope["payload"]
        event_id = str(uuid.uuid4())
        received_at = now_ms()
        envelope_json = stable_json(envelope)
        payload_json = stable_json(payload)

        with self.db:
            cursor = self.db.execute(
                """
                INSERT OR IGNORE INTO sync_events (
                    event_id,
                    received_at_epoch_ms,
                    business_id,
                    branch_id,
                    device_id,
                    destination_mode,
                    payload_type,
                    payload_entity_id,
                    queued_at_epoch_ms,
                    envelope_json,
                    payload_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    event_id,
                    received_at,
                    envelope["businessId"],
                    envelope.get("branchId"),
                    envelope["deviceId"],
                    envelope["destinationMode"],
                    envelope["payloadType"],
                    envelope["payloadEntityId"],
                    int(envelope.get("queuedAtEpochMs") or 0),
                    envelope_json,
                    payload_json,
                ),
            )
            if cursor.rowcount == 0:
                existing = self._find_existing_event(envelope)
                return {
                    "accepted": True,
                    "duplicate": True,
                    "eventId": existing["event_id"] if existing else None,
                    "payloadType": envelope["payloadType"],
                }

            self._apply_payload(envelope, event_id)

        return {
            "accepted": True,
            "duplicate": False,
            "eventId": event_id,
            "payloadType": envelope["payloadType"],
        }

    def store_business_analytics_export(self, payload: dict[str, Any], raw_size_bytes: int) -> dict[str, Any]:
        if not isinstance(payload, dict):
            raise ApiError(HTTPStatus.BAD_REQUEST, "Business analytics JSON body must be an object")
        export_id = str(uuid.uuid4())
        business_id = infer_business_id_from_export(payload)
        with self.db:
            self.db.execute(
                """
                INSERT INTO business_analytics_exports (
                    export_id,
                    received_at_epoch_ms,
                    business_id,
                    app_version_name,
                    exported_at_epoch_ms,
                    raw_size_bytes,
                    payload_json
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    export_id,
                    now_ms(),
                    business_id,
                    payload.get("appVersionName"),
                    nullable_int(payload.get("exportedAtEpochMs")),
                    raw_size_bytes,
                    stable_json(payload),
                ),
            )
        return {
            "accepted": True,
            "exportId": export_id,
            "businessId": business_id,
            "exportKind": payload.get("exportKind", "biashara_business_snapshot"),
        }

    def store_sqlite_database_upload(self, raw: bytes, content_type: str) -> dict[str, Any]:
        upload_id = str(uuid.uuid4())
        filename = "biashara_upload.db"
        database_bytes = raw
        if content_type.lower().startswith("multipart/form-data"):
            parsed = parse_multipart_database_part(raw, content_type)
            if parsed is not None:
                filename, database_bytes = parsed
        safe_name = safe_filename(filename)
        stored_path: str | None = None
        if self.upload_dir:
            stored_path = os.path.join(self.upload_dir, f"{upload_id}_{safe_name}")
            with open(stored_path, "wb") as out:
                out.write(database_bytes)
            payload_blob = None
        else:
            payload_blob = database_bytes
        with self.db:
            self.db.execute(
                """
                INSERT INTO database_uploads (
                    upload_id,
                    received_at_epoch_ms,
                    business_id,
                    filename,
                    content_type,
                    raw_size_bytes,
                    stored_path,
                    payload_blob
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                (
                    upload_id,
                    now_ms(),
                    "unknown",
                    safe_name,
                    content_type,
                    len(database_bytes),
                    stored_path,
                    payload_blob,
                ),
            )
        return {
            "accepted": True,
            "uploadId": upload_id,
            "filename": safe_name,
            "bytes": len(database_bytes),
        }

    def list_products(self, business_id: str, include_deleted: bool) -> list[dict[str, Any]]:
        where = "business_id = ?"
        args: list[Any] = [business_id]
        if not include_deleted:
            where += " AND deleted_at_epoch_ms IS NULL"
        return self._rows(
            f"SELECT * FROM catalog_products WHERE {where} ORDER BY name COLLATE NOCASE",
            args,
        )

    def list_services(self, business_id: str, include_deleted: bool) -> list[dict[str, Any]]:
        where = "business_id = ?"
        args: list[Any] = [business_id]
        if not include_deleted:
            where += " AND deleted_at_epoch_ms IS NULL"
        return self._rows(
            f"SELECT * FROM catalog_services WHERE {where} ORDER BY name COLLATE NOCASE",
            args,
        )

    def list_stock_movements(self, business_id: str, limit: int) -> list[dict[str, Any]]:
        return self._rows(
            """
            SELECT * FROM stock_movements
            WHERE business_id = ?
            ORDER BY created_at_epoch_ms DESC, id DESC
            LIMIT ?
            """,
            [business_id, clamp_limit(limit)],
        )

    def list_events(self, business_id: str, limit: int) -> list[dict[str, Any]]:
        return self._rows(
            """
            SELECT event_id, received_at_epoch_ms, business_id, branch_id, device_id,
                   destination_mode, payload_type, payload_entity_id, queued_at_epoch_ms
            FROM sync_events
            WHERE business_id = ?
            ORDER BY received_at_epoch_ms DESC, id DESC
            LIMIT ?
            """,
            [business_id, clamp_limit(limit)],
        )

    def stats(self, business_id: str) -> dict[str, Any]:
        def count(table: str, extra: str = "") -> int:
            row = self.db.execute(
                f"SELECT COUNT(*) AS n FROM {table} WHERE business_id = ? {extra}",
                (business_id,),
            ).fetchone()
            return int(row["n"])

        return {
            "businessId": business_id,
            "events": count("sync_events"),
            "activeProducts": count("catalog_products", "AND deleted_at_epoch_ms IS NULL"),
            "activeServices": count("catalog_services", "AND deleted_at_epoch_ms IS NULL"),
            "stockMovements": count("stock_movements"),
            "registeredDevices": count("registered_devices", "AND is_active = 1"),
            "branches": count("branches", "AND is_active = 1"),
            "conflicts": count("catalog_conflicts"),
            "businessAnalyticsExports": count("business_analytics_exports"),
            "sqliteDatabaseUploads": count("database_uploads"),
        }

    def _validate_envelope(self, envelope: dict[str, Any]) -> None:
        if not isinstance(envelope, dict):
            raise ApiError(HTTPStatus.BAD_REQUEST, "JSON body must be an object")
        for key in (
            "schemaVersion",
            "exportKind",
            "businessId",
            "deviceId",
            "destinationMode",
            "payloadType",
            "payloadEntityId",
            "payload",
        ):
            if key not in envelope:
                raise ApiError(HTTPStatus.BAD_REQUEST, f"Missing field: {key}")
        if envelope["exportKind"] != "enterprise_sync_outbox":
            raise ApiError(HTTPStatus.BAD_REQUEST, "Unsupported exportKind")
        if not isinstance(envelope["payload"], dict):
            raise ApiError(HTTPStatus.BAD_REQUEST, "payload must be an object")
        for key in ("businessId", "deviceId", "destinationMode", "payloadType", "payloadEntityId"):
            if not str(envelope[key]).strip():
                raise ApiError(HTTPStatus.BAD_REQUEST, f"{key} must not be blank")

    def _find_existing_event(self, envelope: dict[str, Any]) -> sqlite3.Row | None:
        return self.db.execute(
            """
            SELECT event_id FROM sync_events
            WHERE business_id = ?
              AND device_id = ?
              AND payload_type = ?
              AND payload_entity_id = ?
              AND queued_at_epoch_ms = ?
            LIMIT 1
            """,
            (
                envelope["businessId"],
                envelope["deviceId"],
                envelope["payloadType"],
                envelope["payloadEntityId"],
                int(envelope.get("queuedAtEpochMs") or 0),
            ),
        ).fetchone()

    def _apply_payload(self, envelope: dict[str, Any], event_id: str) -> None:
        payload_type = str(envelope["payloadType"]).upper()
        if payload_type == "CATALOG_PRODUCT_UPSERTED":
            self._upsert_product(envelope, event_id)
        elif payload_type == "CATALOG_SERVICE_UPSERTED":
            self._upsert_service(envelope, event_id)
        elif payload_type == "CATALOG_PRODUCT_DELETED":
            self._delete_product(envelope, event_id)
        elif payload_type == "CATALOG_SERVICE_DELETED":
            self._delete_service(envelope, event_id)
        elif payload_type == "STOCK_MOVEMENT_RECORDED":
            self._insert_stock_movement(envelope, event_id)
        elif payload_type == "DEVICE_REGISTRATION":
            self._upsert_device(envelope, event_id)
        elif payload_type == "BRANCH_UPDATED":
            self._upsert_branch(envelope, event_id)
        elif payload_type == "AUDIT_EVENT":
            self._insert_audit_event(envelope, event_id)

    def _upsert_product(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        business_id = envelope["businessId"]
        central_id = central_id_for(payload, envelope, "prod")
        version = int(payload.get("version") or 0)
        existing = self.db.execute(
            "SELECT version FROM catalog_products WHERE business_id = ? AND central_id = ?",
            (business_id, central_id),
        ).fetchone()
        if existing and version < int(existing["version"]):
            self._record_conflict(envelope, event_id, "PRODUCT", central_id, int(existing["version"]), version)
            return
        self.db.execute(
            """
            INSERT INTO catalog_products (
                business_id, central_id, local_id, version, sync_status, updated_at_epoch_ms,
                name, description, price, cost, stock_quantity, category, barcode_value, image_url,
                deleted_at_epoch_ms, last_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
            ON CONFLICT (business_id, central_id) DO UPDATE SET
                local_id = excluded.local_id,
                version = excluded.version,
                sync_status = excluded.sync_status,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                name = excluded.name,
                description = excluded.description,
                price = excluded.price,
                cost = excluded.cost,
                stock_quantity = excluded.stock_quantity,
                category = excluded.category,
                barcode_value = excluded.barcode_value,
                image_url = excluded.image_url,
                deleted_at_epoch_ms = NULL,
                last_event_id = excluded.last_event_id
            """,
            (
                business_id,
                central_id,
                int(payload.get("localId") or 0),
                version,
                str(payload.get("syncStatus") or "PENDING"),
                int(payload.get("updatedAtEpochMs") or now_ms()),
                str(payload.get("name") or ""),
                payload.get("description"),
                float(payload.get("price") or 0.0),
                float(payload.get("cost") or 0.0),
                int(payload.get("stockQuantity") or 0),
                payload.get("category"),
                payload.get("barcodeValue"),
                payload.get("imageUrl"),
                event_id,
            ),
        )

    def _upsert_service(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        business_id = envelope["businessId"]
        central_id = central_id_for(payload, envelope, "svc")
        version = int(payload.get("version") or 0)
        existing = self.db.execute(
            "SELECT version FROM catalog_services WHERE business_id = ? AND central_id = ?",
            (business_id, central_id),
        ).fetchone()
        if existing and version < int(existing["version"]):
            self._record_conflict(envelope, event_id, "SERVICE", central_id, int(existing["version"]), version)
            return
        self.db.execute(
            """
            INSERT INTO catalog_services (
                business_id, central_id, local_id, version, sync_status, updated_at_epoch_ms,
                name, description, base_price, price_mode, duration_minutes, category,
                catalogue_token, warranty_days, visible_in_kiosk, deleted_at_epoch_ms, last_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NULL, ?)
            ON CONFLICT (business_id, central_id) DO UPDATE SET
                local_id = excluded.local_id,
                version = excluded.version,
                sync_status = excluded.sync_status,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                name = excluded.name,
                description = excluded.description,
                base_price = excluded.base_price,
                price_mode = excluded.price_mode,
                duration_minutes = excluded.duration_minutes,
                category = excluded.category,
                catalogue_token = excluded.catalogue_token,
                warranty_days = excluded.warranty_days,
                visible_in_kiosk = excluded.visible_in_kiosk,
                deleted_at_epoch_ms = NULL,
                last_event_id = excluded.last_event_id
            """,
            (
                business_id,
                central_id,
                int(payload.get("localId") or 0),
                version,
                str(payload.get("syncStatus") or "PENDING"),
                int(payload.get("updatedAtEpochMs") or now_ms()),
                str(payload.get("name") or ""),
                payload.get("description"),
                float(payload.get("basePrice") or 0.0),
                str(payload.get("priceMode") or "FIXED"),
                int(payload.get("durationMinutes") or 0),
                payload.get("category"),
                str(payload.get("catalogueToken") or ""),
                int(payload.get("warrantyDays") or 0),
                1 if bool(payload.get("visibleInKiosk", True)) else 0,
                event_id,
            ),
        )

    def _delete_product(self, envelope: dict[str, Any], event_id: str) -> None:
        self._mark_deleted("catalog_products", "prod", envelope, event_id)

    def _delete_service(self, envelope: dict[str, Any], event_id: str) -> None:
        self._mark_deleted("catalog_services", "svc", envelope, event_id)

    def _mark_deleted(self, table: str, prefix: str, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        business_id = envelope["businessId"]
        central_id = central_id_for(payload, envelope, prefix)
        deleted_at = int(payload.get("deletedAtEpochMs") or now_ms())
        self.db.execute(
            f"""
            UPDATE {table}
            SET deleted_at_epoch_ms = ?, last_event_id = ?
            WHERE business_id = ? AND central_id = ?
            """,
            (deleted_at, event_id, business_id, central_id),
        )

    def _insert_stock_movement(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        self.db.execute(
            """
            INSERT OR IGNORE INTO stock_movements (
                business_id, device_id, movement_id, local_product_id, enterprise_product_id,
                movement_type, quantity_delta, stock_after, source_type, source_id, note,
                created_at_epoch_ms, received_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                envelope["businessId"],
                envelope["deviceId"],
                int(payload.get("movementId") or 0),
                int(payload.get("localProductId") or 0),
                payload.get("enterpriseProductId"),
                str(payload.get("movementType") or "UNKNOWN"),
                int(payload.get("quantityDelta") or 0),
                nullable_int(payload.get("stockAfter")),
                payload.get("sourceType"),
                payload.get("sourceId"),
                payload.get("note"),
                int(payload.get("createdAtEpochMs") or now_ms()),
                event_id,
            ),
        )

    def _upsert_device(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        business_id = str(payload.get("businessId") or envelope["businessId"])
        device_id = str(payload.get("deviceId") or envelope["deviceId"])
        self.db.execute(
            """
            INSERT INTO registered_devices (
                business_id, device_id, display_name, deployment_mode, app_version_name,
                max_devices_snapshot, first_seen_at_epoch_ms, last_seen_at_epoch_ms,
                is_active, payload_json, last_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (business_id, device_id) DO UPDATE SET
                display_name = excluded.display_name,
                deployment_mode = excluded.deployment_mode,
                app_version_name = excluded.app_version_name,
                max_devices_snapshot = excluded.max_devices_snapshot,
                last_seen_at_epoch_ms = excluded.last_seen_at_epoch_ms,
                is_active = excluded.is_active,
                payload_json = excluded.payload_json,
                last_event_id = excluded.last_event_id
            """,
            (
                business_id,
                device_id,
                payload.get("displayName"),
                payload.get("deploymentMode"),
                payload.get("appVersionName"),
                nullable_int(payload.get("maxDevicesSnapshot")),
                nullable_int(payload.get("firstSeenAt")),
                nullable_int(payload.get("lastSeenAt")),
                1 if bool(payload.get("isActive", True)) else 0,
                stable_json(payload),
                event_id,
            ),
        )

    def _upsert_branch(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        business_id = str(payload.get("businessId") or envelope["businessId"])
        branch_id = int(payload.get("id") or envelope.get("branchId") or 0)
        self.db.execute(
            """
            INSERT INTO branches (
                business_id, branch_id, code, name, location, is_default, is_active,
                updated_at_epoch_ms, payload_json, last_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (business_id, branch_id) DO UPDATE SET
                code = excluded.code,
                name = excluded.name,
                location = excluded.location,
                is_default = excluded.is_default,
                is_active = excluded.is_active,
                updated_at_epoch_ms = excluded.updated_at_epoch_ms,
                payload_json = excluded.payload_json,
                last_event_id = excluded.last_event_id
            """,
            (
                business_id,
                branch_id,
                str(payload.get("code") or "MAIN"),
                str(payload.get("name") or "Main branch"),
                payload.get("location"),
                1 if bool(payload.get("isDefault", False)) else 0,
                1 if bool(payload.get("isActive", True)) else 0,
                int(payload.get("updatedAt") or now_ms()),
                stable_json(payload),
                event_id,
            ),
        )

    def _insert_audit_event(self, envelope: dict[str, Any], event_id: str) -> None:
        payload = envelope["payload"]
        self.db.execute(
            """
            INSERT OR IGNORE INTO audit_events (
                business_id, device_id, action, entity_type, entity_id, summary,
                created_at_epoch_ms, payload_json, received_event_id
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                str(payload.get("businessId") or envelope["businessId"]),
                payload.get("deviceId"),
                payload.get("action"),
                payload.get("entityType"),
                payload.get("entityId"),
                payload.get("summary"),
                nullable_int(payload.get("createdAt")),
                stable_json(payload),
                event_id,
            ),
        )

    def _record_conflict(
        self,
        envelope: dict[str, Any],
        event_id: str,
        catalog_type: str,
        central_id: str,
        current_version: int,
        incoming_version: int,
    ) -> None:
        self.db.execute(
            """
            INSERT INTO catalog_conflicts (
                received_event_id, business_id, catalog_type, central_id, current_version,
                incoming_version, reason, incoming_json, created_at_epoch_ms
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            (
                event_id,
                envelope["businessId"],
                catalog_type,
                central_id,
                current_version,
                incoming_version,
                "incoming_version_older_than_central_version",
                stable_json(envelope["payload"]),
                now_ms(),
            ),
        )

    def _rows(self, sql: str, args: list[Any]) -> list[dict[str, Any]]:
        return [dict(row) for row in self.db.execute(sql, args).fetchall()]


class EnterpriseSyncHandler(BaseHTTPRequestHandler):
    server_version = "BiasharaEnterpriseSync/1.0"

    def do_GET(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path == "/health":
                self._json(HTTPStatus.OK, {"ok": True, "service": "enterprise-sync"})
                return
            self._require_auth()
            qs = parse_qs(parsed.query)
            business_id = single(qs, "businessId")
            if not business_id:
                raise ApiError(HTTPStatus.BAD_REQUEST, "businessId query parameter is required")
            include_deleted = single(qs, "includeDeleted") == "true"
            limit = int(single(qs, "limit") or "100")

            if parsed.path == "/v1/enterprise/catalog/products":
                body = {"products": self.server.store.list_products(business_id, include_deleted)}
            elif parsed.path == "/v1/enterprise/catalog/services":
                body = {"services": self.server.store.list_services(business_id, include_deleted)}
            elif parsed.path == "/v1/enterprise/stock/movements":
                body = {"movements": self.server.store.list_stock_movements(business_id, limit)}
            elif parsed.path == "/v1/enterprise/events":
                body = {"events": self.server.store.list_events(business_id, limit)}
            elif parsed.path == "/v1/enterprise/stats":
                body = self.server.store.stats(business_id)
            else:
                raise ApiError(HTTPStatus.NOT_FOUND, "Unknown endpoint")
            self._json(HTTPStatus.OK, body)
        except ApiError as exc:
            self._json(exc.status, {"ok": False, "error": exc.message})
        except Exception as exc:  # pragma: no cover - defensive server boundary
            self._json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(exc)})

    def do_POST(self) -> None:
        try:
            parsed = urlparse(self.path)
            if parsed.path not in SYNC_PATHS:
                raise ApiError(HTTPStatus.NOT_FOUND, "Unknown endpoint")
            self._require_auth()
            export_type = self.headers.get("X-Biashara-Export-Type", "")
            if export_type not in {"enterprise-sync-outbox", "business-analytics-json", "sqlite-database"}:
                raise ApiError(
                    HTTPStatus.BAD_REQUEST,
                    "X-Biashara-Export-Type must be enterprise-sync-outbox, business-analytics-json, or sqlite-database",
                )
            length = int(self.headers.get("Content-Length") or "0")
            if length <= 0:
                raise ApiError(HTTPStatus.BAD_REQUEST, "Request body is required")
            limit = MAX_SQLITE_UPLOAD_BYTES if export_type == "sqlite-database" else MAX_JSON_BODY_BYTES
            if length > limit:
                raise ApiError(HTTPStatus.REQUEST_ENTITY_TOO_LARGE, "Request body is too large")
            raw = self.rfile.read(length)
            if export_type == "sqlite-database":
                result = self.server.store.store_sqlite_database_upload(
                    raw = raw,
                    content_type = self.headers.get("Content-Type", "application/octet-stream"),
                )
            else:
                try:
                    payload = json.loads(raw.decode("utf-8"))
                except json.JSONDecodeError as exc:
                    raise ApiError(HTTPStatus.BAD_REQUEST, f"Invalid JSON: {exc.msg}") from exc
                if export_type == "enterprise-sync-outbox":
                    result = self.server.store.ingest(payload)
                else:
                    result = self.server.store.store_business_analytics_export(payload, len(raw))
            self._json(HTTPStatus.ACCEPTED, result)
        except ApiError as exc:
            self._json(exc.status, {"ok": False, "error": exc.message})
        except Exception as exc:  # pragma: no cover - defensive server boundary
            self._json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(exc)})

    def log_message(self, format: str, *args: Any) -> None:
        if not self.server.quiet:
            super().log_message(format, *args)

    def _require_auth(self) -> None:
        if self.server.allow_unauthenticated:
            return
        auth = self.headers.get("Authorization", "")
        expected = f"Bearer {self.server.token}"
        if not hmac.compare_digest(auth, expected):
            raise ApiError(HTTPStatus.UNAUTHORIZED, "Invalid bearer token")

    def _json(self, status: HTTPStatus, body: dict[str, Any]) -> None:
        data = stable_json(body).encode("utf-8")
        self.send_response(status.value)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


class EnterpriseSyncHttpServer(ThreadingHTTPServer):
    def __init__(
        self,
        server_address: tuple[str, int],
        store: EnterpriseSyncStore,
        token: str,
        allow_unauthenticated: bool,
        quiet: bool,
    ):
        super().__init__(server_address, EnterpriseSyncHandler)
        self.store = store
        self.token = token
        self.allow_unauthenticated = allow_unauthenticated
        self.quiet = quiet


def run_server(
    host: str,
    port: int,
    db_path: str,
    token: str,
    allow_unauthenticated: bool,
    quiet: bool,
    discovery_port: int,
    public_host: str | None,
) -> None:
    store = EnterpriseSyncStore(db_path)
    if discovery_port > 0:
        start_discovery_responder(
            discovery_port = discovery_port,
            http_port = port,
            public_host = public_host,
            quiet = quiet,
        )
    httpd = EnterpriseSyncHttpServer((host, port), store, token, allow_unauthenticated, quiet)
    print(f"Biashara Enterprise sync service listening on http://{host}:{port}")
    if discovery_port > 0:
        print(f"LAN discovery responder listening on UDP {discovery_port}")
    print(f"SQLite store: {db_path}")
    if allow_unauthenticated:
        print("WARNING: authentication disabled; use only for local development")
    httpd.serve_forever()


def main() -> None:
    parser = argparse.ArgumentParser(description="Biashara AI Enterprise sync ingestion service")
    parser.add_argument("--host", default=os.environ.get("BIASHARA_ENTERPRISE_HOST", "127.0.0.1"))
    parser.add_argument("--port", type=int, default=int(os.environ.get("BIASHARA_ENTERPRISE_PORT", "8088")))
    parser.add_argument("--db", default=os.environ.get("BIASHARA_ENTERPRISE_DB", "enterprise_sync.db"))
    parser.add_argument("--token", default=os.environ.get("BIASHARA_ENTERPRISE_SYNC_TOKEN", ""))
    parser.add_argument(
        "--discovery-port",
        type=int,
        default=int(os.environ.get("BIASHARA_ENTERPRISE_DISCOVERY_PORT", str(DEFAULT_DISCOVERY_PORT))),
        help="UDP port for LAN auto-discovery; set 0 to disable",
    )
    parser.add_argument(
        "--public-host",
        default=os.environ.get("BIASHARA_ENTERPRISE_PUBLIC_HOST"),
        help="Optional host/IP to advertise in discovery responses",
    )
    parser.add_argument("--dev-no-auth", action="store_true", help="Disable bearer auth for local development only")
    parser.add_argument("--quiet", action="store_true")
    args = parser.parse_args()

    if not args.dev_no_auth and not args.token:
        raise SystemExit(
            "Set BIASHARA_ENTERPRISE_SYNC_TOKEN or pass --token. "
            "Use --dev-no-auth only for localhost development."
        )
    run_server(
        host=args.host,
        port=args.port,
        db_path=args.db,
        token=args.token,
        allow_unauthenticated=args.dev_no_auth,
        quiet=args.quiet,
        discovery_port=args.discovery_port,
        public_host=args.public_host,
    )


def start_discovery_responder(
    discovery_port: int,
    http_port: int,
    public_host: str | None,
    quiet: bool,
) -> None:
    def loop() -> None:
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        sock.bind(("0.0.0.0", discovery_port))
        response = {
            "service": DISCOVERY_SERVICE,
            "version": 1,
            "httpPort": http_port,
            "path": DEFAULT_SYNC_PATH,
            "deploymentMode": "ON_PREMISE",
        }
        if public_host:
            response["host"] = public_host
        data = stable_json(response).encode("utf-8")
        while True:
            try:
                raw, address = sock.recvfrom(2048)
                if DISCOVERY_PROBE not in raw and DISCOVERY_SERVICE.encode("utf-8") not in raw:
                    continue
                sock.sendto(data, address)
            except Exception as exc:  # pragma: no cover - long-running server loop
                if not quiet:
                    print(f"Discovery responder error: {exc}")

    thread = threading.Thread(target=loop, name="enterprise-discovery", daemon=True)
    thread.start()


def central_id_for(payload: dict[str, Any], envelope: dict[str, Any], prefix: str) -> str:
    central_id = str(payload.get("centralId") or "").strip()
    if central_id:
        return central_id
    local_id = payload.get("localId") or payload.get("localProductId") or envelope["payloadEntityId"]
    return f"{prefix}-{envelope['businessId']}-{envelope['deviceId']}-{local_id}"


def infer_business_id_from_export(payload: dict[str, Any]) -> str:
    devices = payload.get("enterpriseRegisteredDevices")
    if isinstance(devices, list):
        for device in devices:
            if isinstance(device, dict) and str(device.get("businessId") or "").strip():
                return str(device["businessId"]).strip()
    audit_events = payload.get("enterpriseAuditEvents")
    if isinstance(audit_events, list):
        for event in audit_events:
            if isinstance(event, dict) and str(event.get("businessId") or "").strip():
                return str(event["businessId"]).strip()
    branches = payload.get("enterpriseBranches")
    if isinstance(branches, list):
        for branch in branches:
            if isinstance(branch, dict) and str(branch.get("businessId") or "").strip():
                return str(branch["businessId"]).strip()
    return "unknown"


def parse_multipart_database_part(raw: bytes, content_type: str) -> tuple[str, bytes] | None:
    match = re.search(r"boundary=(?P<boundary>[^;]+)", content_type, re.IGNORECASE)
    if not match:
        return None
    boundary = match.group("boundary").strip().strip('"')
    delimiter = ("--" + boundary).encode("utf-8")
    for part in raw.split(delimiter):
        part = part.strip(b"\r\n")
        if not part or part == b"--" or b"\r\n\r\n" not in part:
            continue
        header_bytes, body = part.split(b"\r\n\r\n", 1)
        headers = header_bytes.decode("utf-8", errors="replace")
        if 'name="database"' not in headers:
            continue
        filename_match = re.search(r'filename="(?P<filename>[^"]+)"', headers)
        filename = filename_match.group("filename") if filename_match else "biashara_upload.db"
        return filename, body.rstrip(b"\r\n")
    return None


def safe_filename(filename: str) -> str:
    base = os.path.basename(filename.strip()) or "biashara_upload.db"
    cleaned = re.sub(r"[^A-Za-z0-9._-]+", "_", base)
    return cleaned[:120] or "biashara_upload.db"


def stable_json(value: Any) -> str:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":"), sort_keys=True)


def now_ms() -> int:
    return int(time.time() * 1000)


def nullable_int(value: Any) -> int | None:
    if value is None:
        return None
    return int(value)


def single(query: dict[str, list[str]], key: str) -> str | None:
    values = query.get(key)
    if not values:
        return None
    return values[0]


def clamp_limit(value: int) -> int:
    return max(1, min(value, 1000))


if __name__ == "__main__":
    main()
