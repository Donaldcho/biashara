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
 * Validates **v3 → v25** migrations preserve existing product and transaction rows
 * and produce the POS schema (`sale_line_items`, extended `transactions`, `app_settings`,
 * `note_type` on `transactions`, `last_visit` on `customers`, loss columns on `alerts`),
 * chat session tables, Phase 4a agent tables (`agent_actions`, `agent_settings`, `agent_run_log`)
 * plus `products.last_stock_check_at`, Phase 6 model/skill tables, Voice V0 columns on `app_settings`,
 * ledger intelligence tables (`ledger_context`), composite indexes (v23→v24), and schema-correction
 * index renames (v24→v25) that align `ledger_entries` / `ledger_context` index names with Room's
 * auto-generated naming convention.
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
    fun migrate3To25_preservesProductsAndTransactionsAndSeedsSettings() {
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
            25,
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

        db.query(
            """
            SELECT business_name, currency_code, voice_input_enabled, whisper_model_id,
                   silence_timeout_ms, voice_language_mode, tts_enabled, tts_speech_rate, tts_pitch,
                   tts_auto_read_agent_alerts, tts_auto_read_query_answers
            FROM app_settings WHERE id = 1
            """.trimIndent(),
        ).use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("My Business", c.getString(0))
            assertEquals("KES", c.getString(1))
            assertEquals(1, c.getInt(2))
            assertEquals("whisper-tiny", c.getString(3))
            assertEquals(2500, c.getInt(4))
            assertEquals("AUTO", c.getString(5))
            assertEquals(1, c.getInt(6))
            assertEquals(0.9, c.getDouble(7), 0.001)
            assertEquals(1.0, c.getDouble(8), 0.001)
            assertEquals(0, c.getInt(9))
            assertEquals(1, c.getInt(10))
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

        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='ledger_entries'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.query("SELECT COUNT(*) FROM ledger_entries").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND tbl_name='ledger_entries'",
        ).use { c ->
            var indexCount = 0
            while (c.moveToNext()) indexCount++
            // 21→22 creates 5 indexes; 23→24 adds 4 composite indexes = 9 total.
            assertEquals(9, indexCount)
        }
        db.query("PRAGMA table_info(ledger_entries)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("occurred_at"))
            assertTrue(cols.contains("running_balance"))
            assertTrue(cols.contains("sync_id"))
            assertTrue(cols.contains("is_synced"))
        }
        db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cash_counts'").use { c ->
            assertTrue(c.moveToFirst())
        }
        db.query("SELECT COUNT(*) FROM cash_counts").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // 22 → 23: ledger_context table
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='ledger_context'",
        ).use { c -> assertTrue("ledger_context table missing after 22→23", c.moveToFirst()) }
        db.query("SELECT COUNT(*) FROM ledger_context").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }
        db.query("PRAGMA table_info(ledger_context)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("context_type"))
            assertTrue(cols.contains("prompt"))
            assertTrue(cols.contains("created_at_millis"))
            assertTrue(cols.contains("superseded_at_millis"))
        }

        // 23 → 24: composite indexes on ledger_entries and cash_counts
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_direction_occurred_at'",
        ).use { c -> assertTrue("idx_ledger_direction_occurred_at missing after 23→24", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_type_occurred_at'",
        ).use { c -> assertTrue("idx_ledger_type_occurred_at missing after 23→24", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_is_synced'",
        ).use { c -> assertTrue("idx_ledger_is_synced missing after 23→24", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_cash_counts_counted_at'",
        ).use { c -> assertTrue("idx_cash_counts_counted_at missing after 23→24", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_context_anomaly_active'",
        ).use { c -> assertTrue("idx_ledger_context_anomaly_active missing after 23→24", c.moveToFirst()) }

        // 24 → 25: Room-compatible index names on ledger_entries and ledger_context
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ledger_entries_occurred_at'",
        ).use { c -> assertTrue("index_ledger_entries_occurred_at missing after 24→25", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ledger_entries_direction'",
        ).use { c -> assertTrue("index_ledger_entries_direction missing after 24→25", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ledger_context_related_anomaly_id'",
        ).use { c -> assertTrue("index_ledger_context_related_anomaly_id missing after 24→25", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ledger_context_related_ledger_entry_id'",
        ).use { c -> assertTrue("index_ledger_context_related_ledger_entry_id missing after 24→25", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_ledger_context_created_at_millis'",
        ).use { c -> assertTrue("index_ledger_context_created_at_millis missing after 24→25", c.moveToFirst()) }
        // Old wrong-named indexes must be gone
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_occurred'",
        ).use { c -> assertFalse("idx_ledger_occurred should be dropped by 24→25", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='idx_ledger_context_anomaly'",
        ).use { c -> assertFalse("idx_ledger_context_anomaly should be dropped by 24→25", c.moveToFirst()) }

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

    // ── Phase 7 K0: v25 → v26 ─────────────────────────────────────────────────

    @Test
    fun migrate25To26_createsKnowledgeBaseAndTeachingTables() {
        helper.createDatabase(TEST_DB, 25).apply {
            // v25 baseline — just needs the DB to exist at that version.
        }.close()

        val db = helper.runMigrationsAndValidate(
            TEST_DB,
            26,
            true,
            DatabaseMigrations.MIGRATION_25_26,
        )

        // knowledge_chunks table exists with expected columns
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='knowledge_chunks'",
        ).use { c -> assertTrue("knowledge_chunks table missing", c.moveToFirst()) }
        db.query("PRAGMA table_info(knowledge_chunks)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("content_text"))
            assertTrue(cols.contains("source_path"))
            assertTrue(cols.contains("language_code"))
            assertTrue(cols.contains("chunk_index"))
            assertTrue(cols.contains("embedding_blob"))
            assertTrue(cols.contains("created_at"))
        }
        db.query("SELECT COUNT(*) FROM knowledge_chunks").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(0, c.getInt(0))
        }

        // teaching_events table exists with expected columns
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='teaching_events'",
        ).use { c -> assertTrue("teaching_events table missing", c.moveToFirst()) }
        db.query("PRAGMA table_info(teaching_events)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("event_type"))
            assertTrue(cols.contains("feature_id"))
            assertTrue(cols.contains("skill_invoked"))
            assertTrue(cols.contains("duration_ms"))
            assertTrue(cols.contains("outcome"))
            assertTrue(cols.contains("created_at"))
        }

        // lesson_completions table exists with expected columns
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='lesson_completions'",
        ).use { c -> assertTrue("lesson_completions table missing", c.moveToFirst()) }
        db.query("PRAGMA table_info(lesson_completions)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("lesson_id"))
            assertTrue(cols.contains("completed_at"))
            assertTrue(cols.contains("score"))
        }

        // feature_mastery table exists with expected columns and defaults
        db.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name='feature_mastery'",
        ).use { c -> assertTrue("feature_mastery table missing", c.moveToFirst()) }
        db.query("PRAGMA table_info(feature_mastery)").use { c ->
            val cols = mutableListOf<String>()
            while (c.moveToNext()) cols.add(c.getString(1))
            assertTrue(cols.contains("feature_id"))
            assertTrue(cols.contains("mastery_level"))
            assertTrue(cols.contains("first_seen_at"))
            assertTrue(cols.contains("last_practiced_at"))
            assertTrue(cols.contains("practice_count"))
        }

        // Indexes present
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_knowledge_chunks_source_path'",
        ).use { c -> assertTrue("index_knowledge_chunks_source_path missing", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_teaching_events_feature_id'",
        ).use { c -> assertTrue("index_teaching_events_feature_id missing", c.moveToFirst()) }
        db.query(
            "SELECT name FROM sqlite_master WHERE type='index' AND name='index_feature_mastery_mastery_level'",
        ).use { c -> assertTrue("index_feature_mastery_mastery_level missing", c.moveToFirst()) }

        // feature_mastery default mastery_level is UNDISCOVERED
        db.execSQL(
            "INSERT INTO feature_mastery (feature_id, first_seen_at, last_practiced_at) VALUES ('add_product', 0, 0)",
        )
        db.query("SELECT mastery_level FROM feature_mastery WHERE feature_id='add_product'").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("UNDISCOVERED", c.getString(0))
        }

        db.close()
    }

    companion object {
        private const val TEST_DB = "migration-test-biashara.db"
    }
}
