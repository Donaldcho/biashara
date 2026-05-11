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
 */
object DatabaseMigrations {

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

    val ALL: Array<Migration> = arrayOf(
        MIGRATION_3_5,
        MIGRATION_5_6,
        MIGRATION_6_7,
    )
}
