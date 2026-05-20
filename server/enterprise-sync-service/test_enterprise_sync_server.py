import unittest

from enterprise_sync_server import EnterpriseSyncStore


def envelope(payload_type, payload_entity_id, payload, queued_at=1000):
    return {
        "schemaVersion": 1,
        "exportKind": "enterprise_sync_outbox",
        "sentAtEpochMs": 2000,
        "businessId": "biz_1",
        "branchId": 1,
        "deviceId": "device_a",
        "destinationMode": "ON_PREMISE",
        "payloadType": payload_type,
        "payloadEntityId": payload_entity_id,
        "queuedAtEpochMs": queued_at,
        "payload": payload,
    }


class EnterpriseSyncStoreTest(unittest.TestCase):
    def setUp(self):
        self.store = EnterpriseSyncStore(":memory:")

    def tearDown(self):
        self.store.close()

    def test_product_upsert_materializes_catalog(self):
        result = self.store.ingest(
            envelope(
                "CATALOG_PRODUCT_UPSERTED",
                "prod-1",
                {
                    "centralId": "prod-1",
                    "localId": 12,
                    "version": 1,
                    "syncStatus": "PENDING",
                    "updatedAtEpochMs": 3000,
                    "name": "Sugar",
                    "description": None,
                    "price": 120.0,
                    "cost": 80.0,
                    "stockQuantity": 7,
                    "category": "Groceries",
                    "barcodeValue": "123",
                    "imageUrl": None,
                },
            )
        )

        self.assertTrue(result["accepted"])
        products = self.store.list_products("biz_1", include_deleted=False)
        self.assertEqual(1, len(products))
        self.assertEqual("Sugar", products[0]["name"])
        self.assertEqual(7, products[0]["stock_quantity"])

    def test_duplicate_event_is_idempotent(self):
        event = envelope(
            "STOCK_MOVEMENT_RECORDED",
            "1",
            {
                "movementId": 1,
                "localProductId": 12,
                "enterpriseProductId": "prod-1",
                "movementType": "SALE",
                "quantityDelta": -2,
                "stockAfter": 5,
                "sourceType": "POS_TRANSACTION",
                "sourceId": "44",
                "note": "RCP",
                "createdAtEpochMs": 3000,
            },
        )

        first = self.store.ingest(event)
        second = self.store.ingest(event)

        self.assertFalse(first["duplicate"])
        self.assertTrue(second["duplicate"])
        movements = self.store.list_stock_movements("biz_1", limit=10)
        self.assertEqual(1, len(movements))
        self.assertEqual(-2, movements[0]["quantity_delta"])

    def test_older_catalog_version_records_conflict(self):
        self.store.ingest(
            envelope(
                "CATALOG_PRODUCT_UPSERTED",
                "prod-1",
                {
                    "centralId": "prod-1",
                    "localId": 12,
                    "version": 3,
                    "syncStatus": "PENDING",
                    "updatedAtEpochMs": 3000,
                    "name": "Sugar",
                    "price": 130.0,
                    "cost": 80.0,
                    "stockQuantity": 7,
                },
                queued_at=1000,
            )
        )
        self.store.ingest(
            envelope(
                "CATALOG_PRODUCT_UPSERTED",
                "prod-1",
                {
                    "centralId": "prod-1",
                    "localId": 12,
                    "version": 2,
                    "syncStatus": "PENDING",
                    "updatedAtEpochMs": 2000,
                    "name": "Old Sugar",
                    "price": 90.0,
                    "cost": 70.0,
                    "stockQuantity": 1,
                },
                queued_at=1001,
            )
        )

        products = self.store.list_products("biz_1", include_deleted=False)
        self.assertEqual("Sugar", products[0]["name"])
        self.assertEqual(130.0, products[0]["price"])
        stats = self.store.stats("biz_1")
        self.assertEqual(1, stats["conflicts"])

    def test_business_analytics_json_upload_is_accepted(self):
        result = self.store.store_business_analytics_export(
            {
                "schemaVersion": 4,
                "exportKind": "biashara_business_snapshot",
                "exportedAtEpochMs": 123,
                "appVersionName": "test",
                "enterpriseRegisteredDevices": [
                    {"businessId": "biz_1", "deviceId": "device_a"},
                ],
            },
            raw_size_bytes=120,
        )

        self.assertTrue(result["accepted"])
        self.assertEqual("biz_1", result["businessId"])
        stats = self.store.stats("biz_1")
        self.assertEqual(1, stats["businessAnalyticsExports"])

    def test_sqlite_upload_is_accepted(self):
        result = self.store.store_sqlite_database_upload(
            raw=b"SQLite format 3\x00test",
            content_type="application/octet-stream",
        )

        self.assertTrue(result["accepted"])
        self.assertEqual("biashara_upload.db", result["filename"])
        stats = self.store.stats("unknown")
        self.assertEqual(1, stats["sqliteDatabaseUploads"])


if __name__ == "__main__":
    unittest.main()
