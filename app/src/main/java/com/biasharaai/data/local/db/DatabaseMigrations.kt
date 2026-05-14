package com.biasharaai.data.local.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Explicit Room migrations — no destructive fallback.
 *
 * - **3 → 5:** Brings installs that shipped with `AppDatabase` v3 (Product +
 *   Transaction only) up to the **Phase 2 baseline** expected before POS:
 *   auxiliary tables `customers`, `debts`, `alerts` (empty until those
 *   features land). Product and transaction rows are untouched.
 * - **5 → 6:** POS — `sale_line_items` + payment / receipt / tax columns on
 *   `transactions` (see Prompt P1 spec).
 * - **6 → 7:** POS — single-row `app_settings` for business / tax / printer prefs.
 * - **9 → 10:** P8 returns — `transactions.customer_id`, `related_sale_transaction_id`;
 *   `sale_line_items.source_sale_line_item_id`.
 * - **10 → 11:** Phase 2A / Prompt U1 — `transactions.note_type` (`STANDARD` default); [Alert] entity
 *   maps to existing `alerts` table from **3→5** (no table recreate).
 * - **11 → 12:** Prompt U2 — `customers.last_visit` for POS customer memory.
 * - **12 → 13:** Prompt U5 — loss alerts: `alert_type`, `product_id`, `dedupe_key`,
 *   `localized_message`, `related_transaction_id` on `alerts`.
 * - **13 → 14:** Chat — `ai_business_memory` (owner “remember” notes) and `chat_transcript_turns`
 *   (persisted turns for Gemma prompt continuity).
 * - **14 → 15:** Chat sessions — `chat_sessions` + `chat_session_messages`; legacy `chat_transcript_turns`
 *   migrated into session `id=1` then dropped (Gallery-style history per thread).
 * - **15 → 16:** `chat_session_messages.feedback_vote` for lightweight assistant reply feedback (experiment).
 */
object DatabaseMigrations {

    internal fun columnExists(db: SupportSQLiteDatabase, table: String, column: String): Boolean {
        db.query("PRAGMA table_info(`$table`)").use { c ->
            val idx = c.getColumnIndex("name")
            while (c.moveToNext()) {
                val name = c.getString(idx) ?: continue
                if (name.equals(column, ignoreCase = true)) return true
            }
        }
        return false
    }

    /**
     * Prompt U1 (Phase 2A) on current codebase: **note_type** on `transactions` (credit / note
     * classification). **Alert** `@Entity` maps to the existing `alerts` table from **3→5** — no DDL.
     */
    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "transactions", "note_type")) {
                db.execSQL(
                    "ALTER TABLE transactions ADD COLUMN note_type TEXT NOT NULL DEFAULT 'STANDARD'",
                )
            }
        }
    }

    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "customers", "last_visit")) {
                db.execSQL(
                    "ALTER TABLE customers ADD COLUMN last_visit INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }

    /** Prompt U5 — loss prevention alert metadata on `alerts`. */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "alerts", "alert_type")) {
                db.execSQL(
                    "ALTER TABLE alerts ADD COLUMN alert_type TEXT NOT NULL DEFAULT 'LEGACY'",
                )
            }
            if (!columnExists(db, "alerts", "product_id")) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN product_id INTEGER")
            }
            if (!columnExists(db, "alerts", "dedupe_key")) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN dedupe_key TEXT")
            }
            if (!columnExists(db, "alerts", "localized_message")) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN localized_message TEXT")
            }
            if (!columnExists(db, "alerts", "related_transaction_id")) {
                db.execSQL("ALTER TABLE alerts ADD COLUMN related_transaction_id INTEGER")
            }
        }
    }

    /**
     * v3 had only `products` and `transactions`. v5 adds Phase 2 tables used by
     * POS (Customer / Debt / Alert) without altering existing product or
     * transaction rows.
     */
    val MIGRATION_3_5 = object : Migration(3, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS customers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT,
                    email TEXT,
                    notes TEXT,
                    created_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS debts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    customer_id INTEGER NOT NULL,
                    amount REAL NOT NULL,
                    description TEXT NOT NULL,
                    due_date INTEGER,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    FOREIGN KEY(customer_id) REFERENCES customers(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_debts_customer_id ON debts(customer_id)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS alerts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    message TEXT NOT NULL,
                    severity TEXT NOT NULL DEFAULT 'INFO',
                    created_at INTEGER NOT NULL DEFAULT 0,
                    read INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS sale_line_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    transaction_id INTEGER NOT NULL,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    unit_price REAL NOT NULL,
                    quantity INTEGER NOT NULL,
                    line_total REAL NOT NULL,
                    FOREIGN KEY(transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_sale_line_items_transaction_id ON sale_line_items(transaction_id)",
            )
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN payment_method TEXT NOT NULL DEFAULT 'CASH'",
            )
            db.execSQL("ALTER TABLE transactions ADD COLUMN mobile_money_network TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN mobile_money_ref TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN amount_tendered REAL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN change_due REAL")
            db.execSQL("ALTER TABLE transactions ADD COLUMN receipt_number TEXT")
            db.execSQL("ALTER TABLE transactions ADD COLUMN sale_group_id TEXT")
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN tax_rate REAL NOT NULL DEFAULT 0.0",
            )
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN tax_amount REAL NOT NULL DEFAULT 0.0",
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_settings (
                    id INTEGER NOT NULL PRIMARY KEY,
                    business_name TEXT NOT NULL DEFAULT 'My Business',
                    currency_code TEXT NOT NULL DEFAULT 'KES',
                    currency_symbol TEXT NOT NULL DEFAULT 'KSh',
                    tax_rate REAL NOT NULL DEFAULT 0.0,
                    tax_label TEXT NOT NULL DEFAULT 'Tax',
                    receipt_footer TEXT NOT NULL DEFAULT 'Thank you!',
                    quick_sale_mode INTEGER NOT NULL DEFAULT 0,
                    allow_price_override INTEGER NOT NULL DEFAULT 1,
                    bluetooth_printer_address TEXT,
                    printer_paper_width INTEGER NOT NULL DEFAULT 58
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT OR IGNORE INTO app_settings (id) VALUES (1)")
        }
    }

    /**
     * Room **v8** adds the [Customer] `@Entity` mapping to the existing `customers` table
     * (created in 3→5). No DDL changes.
     */
    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Intentionally empty — table already exists from MIGRATION_3_5.
        }
    }

    /**
     * Room **v9** adds the [Debt] `@Entity` mapping to the existing `debts` table
     * (created in 3→5). No DDL changes.
     */
    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Intentionally empty — table already exists from MIGRATION_3_5.
        }
    }

    /**
     * Prompt P8 — returns: credit customer on sale, link return rows to original sale / line.
     */
    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE transactions ADD COLUMN customer_id INTEGER")
            db.execSQL(
                "ALTER TABLE transactions ADD COLUMN related_sale_transaction_id INTEGER",
            )
            db.execSQL(
                "ALTER TABLE sale_line_items ADD COLUMN source_sale_line_item_id INTEGER",
            )
        }
    }

    /** Chat memory: long-term owner notes + transcript for Gemma context after cold start. */
    val MIGRATION_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ai_business_memory` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `text` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_transcript_turns` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `role` TEXT NOT NULL,
                    `text` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** Chat: multi-session history + messages (replaces flat `chat_transcript_turns`). */
    val MIGRATION_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_sessions` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `title` TEXT NOT NULL,
                    `created_at` INTEGER NOT NULL,
                    `updated_at` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `chat_session_messages` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `session_id` INTEGER NOT NULL,
                    `role` TEXT NOT NULL,
                    `body` TEXT NOT NULL,
                    `image_path` TEXT,
                    `created_at` INTEGER NOT NULL,
                    FOREIGN KEY(`session_id`) REFERENCES `chat_sessions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_chat_session_messages_session_id` " +
                    "ON `chat_session_messages` (`session_id`)",
            )

            db.execSQL(
                "INSERT OR IGNORE INTO chat_sessions (id, title, created_at, updated_at) " +
                    "VALUES (1, 'Chat', 0, 0)",
            )

            if (columnExists(db, "chat_transcript_turns", "id")) {
                db.execSQL(
                    """
                    INSERT INTO chat_session_messages (session_id, role, body, image_path, created_at)
                    SELECT 1, role, text, NULL, created_at FROM chat_transcript_turns
                    ORDER BY id ASC
                    """.trimIndent(),
                )
                db.execSQL("DROP TABLE IF EXISTS chat_transcript_turns")
            }
        }
    }

    /** Experiment: optional thumbs feedback on persisted assistant chat bubbles. */
    val MIGRATION_15_16 = object : Migration(15, 16) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "chat_session_messages", "feedback_vote")) {
                db.execSQL("ALTER TABLE chat_session_messages ADD COLUMN feedback_vote INTEGER")
            }
        }
    }

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_3_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
        MIGRATION_7_8,
        MIGRATION_8_9,
        MIGRATION_9_10,
        MIGRATION_10_11,
        MIGRATION_11_12,
        MIGRATION_12_13,
        MIGRATION_13_14,
        MIGRATION_14_15,
        MIGRATION_15_16,
    )
}
