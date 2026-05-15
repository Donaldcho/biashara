package com.biasharaai.data.local.db

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Validates **v3 → v20** migrations preserve existing product and transaction rows
 * and produce the POS schema (`sale_line_items`, extended `transactions`, `app_settings`,
 * `note_type` on `transactions`, `last_visit` on `customers`, loss columns on `alerts`),
 * chat session tables, and Phase 4a agent tables (`agent_actions`, `agent_settings`, `agent_run_log`)
 * plus `products.last_stock_check_at`.
 *
 * Ships with **Migration(3, 5)** because the app historically shipped at DB v3; Prompt P1’s
 * **5 → 6** and **6 → 7** SQL matches the POS design once the DB reaches v5.
 */
@RunWith(AndroidJUnit4::class)
class AppDatabaseMigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().targetContext.deleteDatabase(TEST_DB)
    }

    @Test
    fun migrate3To20_preservesProductsAndTransactionsAndSeedsSettings() {
        helper.createDatabase(TEST_DB, 3).apply {
            // v3 schema (matches Room entities before POS — indices match Room naming)
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `price` REAL NOT NULL,
                    `cost` REAL NOT NULL,
                    `stock_quantity` INTEGER NOT NULL,
                    `category` TEXT,
                    `barcode_value` TEXT,
                    `image_url` TEXT
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode_value` ON `products` (`barcode_value`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_products_category` ON `products` (`category`)")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `description` TEXT NOT NULL,
                    `date` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")

            execSQL(
                "INSERT INTO products (name, price, cost, stock_quantity) VALUES ('Milo', 10.0, 5.0, 20)",
            )
            execSQL(
                "INSERT INTO transactions (type, amount, description, date) VALUES ('INCOME', 100.0, 'Morning sale', 1700000000000)",
            )
        }.close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            20,
            true,
            *DatabaseMigrations.ALL,
        )

        db.query("SELECT COUNT(*) FROM products").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT name, stock_quantity FROM products WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Milo", c.getString(0))
            assertEquals(20, c.getInt(1))
        }

        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query(
            "SELECT amount, description, payment_method, tax_rate, tax_amount, note_type FROM transactions WHERE id = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(100.0, c.getDouble(0), 0.001)
            assertEquals("Morning sale", c.getString(1))
            assertEquals("CASH", c.getString(2))
            assertEquals(0.0, c.getDouble(3), 0.001)
            assertEquals(0.0, c.getDouble(4), 0.001)
            assertEquals("STANDARD", c.getString(5))
        }

        db.query("SELECT COUNT(*) FROM sale_line_items").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.query("PRAGMA table_info(transactions)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("customer_id"))
            assertTrue(cols.contains("related_sale_transaction_id"))
            assertTrue(cols.contains("note_type"))
        }
        db.query("PRAGMA table_info(sale_line_items)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("source_sale_line_item_id"))
        }

        db.query("SELECT business_name, currency_code FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("My Business", c.getString(0))
            assertEquals("KES", c.getString(1))
        }

        db.query("SELECT COUNT(*) FROM customers").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("PRAGMA table_info(customers)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("last_visit"))
        }

        db.query("SELECT COUNT(*) FROM alerts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.query("PRAGMA table_info(alerts)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("alert_type"))
            assertTrue(cols.contains("product_id"))
            assertTrue(cols.contains("dedupe_key"))
            assertTrue(cols.contains("localized_message"))
            assertTrue(cols.contains("related_transaction_id"))
        }

        db.query("SELECT COUNT(*) FROM ai_business_memory").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='chat_transcript_turns'",
        ).use { c ->
            assertFalse(c.moveToFirst())
        }
        db.query("SELECT COUNT(*) FROM chat_sessions").use { c ->
            assertTrue(c.moveToFirst())
            assertTrue(c.getInt(0) >= 1)
        }
        db.query("SELECT COUNT(*) FROM chat_session_messages").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='agent_actions'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.query("SELECT master_switch FROM agent_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("PRAGMA table_info(products)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("last_stock_check_at"))
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='pending_notifications'").use { c ->
            assertTrue(c.moveToFirst())
        }

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='model_descriptors'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='skill_descriptors'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='skill_pack_records'").use { c ->
            assertTrue(c.moveToFirst())
        }

        db.close()
    }

    @Test
    fun migrate5To6Then6To7_fromHandoffBaseline_preservesData() {
        // Simulate DB already at v5 (Phase 2 tables present, no POS columns yet).
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `products` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `description` TEXT,
                    `price` REAL NOT NULL,
                    `cost` REAL NOT NULL,
                    `stock_quantity` INTEGER NOT NULL,
                    `category` TEXT,
                    `barcode_value` TEXT,
                    `image_url` TEXT
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_products_barcode_value` ON `products` (`barcode_value`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_products_category` ON `products` (`category`)")

            execSQL(
                """
                CREATE TABLE IF NOT EXISTS `transactions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL,
                    `amount` REAL NOT NULL,
                    `description` TEXT NOT NULL,
                    `date` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_date` ON `transactions` (`date`)")
            execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_type` ON `transactions` (`type`)")

            // Phase 2 auxiliary tables (same DDL as Migration 3→5)
            DatabaseMigrations.MIGRATION_3_5.migrate(this)

            execSQL("INSERT INTO products (name, price, cost, stock_quantity) VALUES ('Sugar', 5.0, 2.0, 100)")
            execSQL(
                "INSERT INTO transactions (type, amount, description, date) VALUES ('EXPENSE', 50.0, 'Stock', 1800000000000)",
            )
        }.close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            7,
            true,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
        )

        db.query("SELECT COUNT(*) FROM products").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        db.query("SELECT payment_method FROM transactions WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("CASH", c.getString(0))
        }
        db.query("SELECT id FROM app_settings WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1, c.getInt(0))
        }
        assertNotNull(db)
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test-biashara.db"
    }
}
