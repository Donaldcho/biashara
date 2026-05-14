package com.biasharaai.data.local.db

import androidx.room.migration.Migration
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Prompt U9 — focused migration validation.
 *
 * This app never shipped **MIGRATION_1_2** / **MIGRATION_4_5**; the Phase 2 baseline path is
 * **v3** (products + transactions only) → **MIGRATION_3_5** … **MIGRATION_10_11** (auxiliary
 * tables + POS + `note_type` default `STANDARD`).
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    @After
    fun tearDown() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.deleteDatabase(TEST_DB)
        ctx.deleteDatabase(TEST_DB_LARGE)
    }

    @Test
    fun migrateV3ThroughPhase2Core_preservesProductsAndTransactionsAndNoteType() {
        helper.createDatabase(TEST_DB, 3).apply {
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
                "INSERT INTO products (name, price, cost, stock_quantity) VALUES ('Sample A', 10.0, 5.0, 12)",
            )
            execSQL(
                "INSERT INTO products (name, price, cost, stock_quantity) VALUES ('Sample B', 20.0, 8.0, 3)",
            )
            execSQL(
                "INSERT INTO transactions (type, amount, description, date) VALUES ('INCOME', 55.5, 'Cash sale', 1700000000000)",
            )
            execSQL(
                "INSERT INTO transactions (type, amount, description, date) VALUES ('EXPENSE', 12.0, 'Transport', 1700003600000)",
            )
        }.close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            11,
            true,
            *PHASE2_THROUGH_NOTE_TYPE,
        )

        db.query("SELECT COUNT(*) FROM products").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query("SELECT name, stock_quantity FROM products WHERE id = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Sample A", c.getString(0))
            assertEquals(12, c.getInt(1))
        }

        db.query("SELECT COUNT(*) FROM transactions").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(2, c.getInt(0))
        }
        db.query(
            "SELECT amount, description, note_type FROM transactions WHERE id = 1",
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(55.5, c.getDouble(0), 0.001)
            assertEquals("Cash sale", c.getString(1))
            assertEquals("STANDARD", c.getString(2))
        }

        db.query("SELECT COUNT(*) FROM customers").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM debts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM alerts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('customers','debts','alerts') ORDER BY name",
        ).use { c ->
            val names = mutableListOf<String>()
            while (c.moveToNext()) names.add(c.getString(0))
            assertEquals(listOf("alerts", "customers", "debts"), names)
        }

        db.close()
    }

    /**
     * Acceptance: zero data loss on a 1,000-row product table. CI/emulators may exceed 500ms;
     * 500ms is the on-device target from the Phase 2 checklist.
     */
    @Test
    fun migrateV3To16_preservesThousandProducts_underReasonableTime() {
        helper.createDatabase(TEST_DB_LARGE, 3).apply {
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

            for (i in 1..1000) {
                execSQL(
                    "INSERT INTO products (name, price, cost, stock_quantity) VALUES ('SKU-$i', 1.0, 0.5, 1)",
                )
            }
            execSQL(
                "INSERT INTO transactions (type, amount, description, date) VALUES ('INCOME', 1.0, 'seed', 1)",
            )
        }.close()

        val t0 = System.nanoTime()
        val db = helper.runMigrationsAndValidate(
            TEST_DB_LARGE,
            16,
            true,
            *DatabaseMigrations.ALL,
        )
        val elapsedMs = (System.nanoTime() - t0) / 1_000_000

        db.query("SELECT COUNT(*) FROM products").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(1000, c.getInt(0))
        }
        db.query("SELECT name FROM products WHERE id = 500").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("SKU-500", c.getString(0))
        }

        // Flake guard: physical devices often &lt; 500ms; emulators/CI vary widely.
        assertTrue(
            "Migrations took ${elapsedMs}ms (acceptance checklist: <500ms on device)",
            elapsedMs < 60_000,
        )
        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test-u9.db"
        private const val TEST_DB_LARGE = "migration-test-u9-large.db"

        private val PHASE2_THROUGH_NOTE_TYPE: Array<Migration> = arrayOf(
            DatabaseMigrations.MIGRATION_3_5,
            DatabaseMigrations.MIGRATION_5_6,
            DatabaseMigrations.MIGRATION_6_7,
            DatabaseMigrations.MIGRATION_7_8,
            DatabaseMigrations.MIGRATION_8_9,
            DatabaseMigrations.MIGRATION_9_10,
            DatabaseMigrations.MIGRATION_10_11,
        )
    }
}
