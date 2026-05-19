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
 * - **16 → 17:** Phase 4a — Prompt A1: `agent_actions`, `agent_settings` (singleton row), `agent_run_log`.
 * - **17 → 18:** Phase 4a — Prompt A1: `products.last_stock_check_at` + `idx_products_stock` on `stock_quantity`.
 * - **20 → 21:** Voice V0 — STT/TTS preference columns on `app_settings`.
 * - **24 → 25:** Schema correction — renames mis-named `idx_ledger_*` indexes on `ledger_entries`
 *   and `ledger_context` to the names Room auto-generates from entity `@Index` annotations so that
 *   schema validation passes on devices that already migrated through the old 21→22 / 22→23 paths.
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

    /** Phase 4a — Prompt A1: autonomous agent queue + settings + run telemetry. */
    val MIGRATION_16_17 = object : Migration(16, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_actions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    agent_type TEXT NOT NULL,
                    urgency TEXT NOT NULL,
                    execution_type TEXT NOT NULL DEFAULT 'REQUIRES_APPROVAL',
                    headline TEXT NOT NULL,
                    detail TEXT NOT NULL,
                    action_payload TEXT,
                    action_verb TEXT,
                    status TEXT NOT NULL DEFAULT 'PENDING',
                    created_at INTEGER NOT NULL,
                    expires_at INTEGER,
                    related_entity_id INTEGER,
                    related_entity_type TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_settings (
                    id INTEGER PRIMARY KEY NOT NULL DEFAULT 1,
                    master_switch INTEGER NOT NULL DEFAULT 1,
                    stock_guardian_enabled INTEGER NOT NULL DEFAULT 1,
                    pricing_agent_enabled INTEGER NOT NULL DEFAULT 1,
                    cash_flow_enabled INTEGER NOT NULL DEFAULT 1,
                    customer_relation_enabled INTEGER NOT NULL DEFAULT 1,
                    fraud_sentinel_enabled INTEGER NOT NULL DEFAULT 1,
                    weekly_review_enabled INTEGER NOT NULL DEFAULT 1,
                    opportunity_spotter_enabled INTEGER NOT NULL DEFAULT 1,
                    stock_alert_threshold_days INTEGER NOT NULL DEFAULT 2,
                    daily_summary_hour INTEGER NOT NULL DEFAULT 20,
                    weekly_review_day_of_week INTEGER NOT NULL DEFAULT 1,
                    quiet_hours_start INTEGER NOT NULL DEFAULT 22,
                    quiet_hours_end INTEGER NOT NULL DEFAULT 7,
                    auto_approve_low_dismiss INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT OR IGNORE INTO agent_settings (id) VALUES (1)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_run_log (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    agent_type TEXT NOT NULL,
                    ran_at INTEGER NOT NULL,
                    duration_ms INTEGER NOT NULL,
                    actions_generated INTEGER NOT NULL DEFAULT 0,
                    outcome TEXT NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_agent_actions_status_created_at " +
                    "ON agent_actions (status, created_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_agent_actions_agent_type ON agent_actions (agent_type)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_agent_run_log_agent_type_ran_at " +
                    "ON agent_run_log (agent_type, ran_at)",
            )
        }
    }

    /**
     * Phase 4a — Prompt A1: stock-check timestamp on products + index for stock guardian.
     * `transactions` already has Room indices on `date` and `type` (see [Transaction]); no duplicate indexes.
     */
    val MIGRATION_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "products", "last_stock_check_at")) {
                db.execSQL(
                    "ALTER TABLE products ADD COLUMN last_stock_check_at INTEGER NOT NULL DEFAULT 0",
                )
            }
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_products_stock_quantity ON products(stock_quantity)",
            )
        }
    }

    /** Phase 4d — Prompt A10: deferred push notifications during quiet hours. */
    val MIGRATION_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS pending_notifications (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    body TEXT NOT NULL,
                    urgency TEXT NOT NULL,
                    fire_at INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_pending_notifications_fire_at " +
                    "ON pending_notifications (fire_at)",
            )
        }
    }

    /** Phase 6 — Prompt X0: Model Registry + Skills Engine tables (Room **19→20**; external handbook “v9→v10” refers to an older numbering). */
    val MIGRATION_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS model_descriptors (
                    modelId TEXT PRIMARY KEY NOT NULL,
                    displayName TEXT NOT NULL,
                    huggingFaceRepo TEXT NOT NULL,
                    fileName TEXT NOT NULL,
                    sizeBytes INTEGER NOT NULL,
                    sha256 TEXT NOT NULL,
                    capabilitiesJson TEXT NOT NULL,
                    minTier TEXT NOT NULL,
                    isDownloaded INTEGER NOT NULL DEFAULT 0,
                    downloadedAt INTEGER,
                    filePath TEXT,
                    tokensPerSecGpu REAL,
                    tokensPerSecCpu REAL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS skill_descriptors (
                    skillId TEXT PRIMARY KEY NOT NULL,
                    displayName TEXT NOT NULL,
                    schemaJson TEXT NOT NULL,
                    isEnabled INTEGER NOT NULL DEFAULT 1,
                    packId TEXT,
                    lastExecutedAt INTEGER,
                    executionCount INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS skill_pack_records (
                    packId TEXT PRIMARY KEY NOT NULL,
                    packName TEXT NOT NULL,
                    version TEXT NOT NULL,
                    installedAt INTEGER NOT NULL,
                    isActive INTEGER NOT NULL DEFAULT 1,
                    signatureValid INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
        }
    }

    /** Voice layer — Prompt V0: voice input + TTS preference columns on singleton `app_settings`. */
    val MIGRATION_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            if (!columnExists(db, "app_settings", "voice_input_enabled")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN voice_input_enabled INTEGER NOT NULL DEFAULT 1",
                )
            }
            if (!columnExists(db, "app_settings", "whisper_model_id")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN whisper_model_id TEXT NOT NULL DEFAULT 'whisper-tiny'",
                )
            }
            if (!columnExists(db, "app_settings", "silence_timeout_ms")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN silence_timeout_ms INTEGER NOT NULL DEFAULT 2500",
                )
            }
            if (!columnExists(db, "app_settings", "voice_language_mode")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN voice_language_mode TEXT NOT NULL DEFAULT 'AUTO'",
                )
            }
            if (!columnExists(db, "app_settings", "tts_enabled")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN tts_enabled INTEGER NOT NULL DEFAULT 1",
                )
            }
            if (!columnExists(db, "app_settings", "tts_speech_rate")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN tts_speech_rate REAL NOT NULL DEFAULT 0.9",
                )
            }
            if (!columnExists(db, "app_settings", "tts_pitch")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN tts_pitch REAL NOT NULL DEFAULT 1.0",
                )
            }
            if (!columnExists(db, "app_settings", "tts_auto_read_agent_alerts")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN tts_auto_read_agent_alerts INTEGER NOT NULL DEFAULT 0",
                )
            }
            if (!columnExists(db, "app_settings", "tts_auto_read_query_answers")) {
                db.execSQL(
                    "ALTER TABLE app_settings ADD COLUMN tts_auto_read_query_answers INTEGER NOT NULL DEFAULT 1",
                )
            }
        }
    }

    /** Phase 9 L0 — unified business ledger + daily cash counts (append-only ledger). */
    val MIGRATION_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ledger_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    occurred_at INTEGER NOT NULL,
                    recorded_at INTEGER NOT NULL,
                    type TEXT NOT NULL,
                    direction TEXT NOT NULL,
                    amount REAL NOT NULL,
                    currency TEXT NOT NULL,
                    description TEXT NOT NULL,
                    notes TEXT,
                    running_balance REAL NOT NULL,
                    transaction_id INTEGER,
                    service_delivery_id INTEGER,
                    voucher_id TEXT,
                    debt_id INTEGER,
                    customer_id INTEGER,
                    product_id INTEGER,
                    service_item_id INTEGER,
                    device_id TEXT NOT NULL,
                    staff_name TEXT,
                    sync_id TEXT NOT NULL,
                    is_synced INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            // Index names must match Room's auto-generated convention (index_<table>_<col>)
            // so the schema validator doesn't throw on startup.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_occurred_at` ON ledger_entries(occurred_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_direction` ON ledger_entries(direction)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_type` ON ledger_entries(type)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_customer_id` ON ledger_entries(customer_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_transaction_id` ON ledger_entries(transaction_id)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cash_counts (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    counted_at INTEGER NOT NULL,
                    expected_balance REAL NOT NULL,
                    actual_balance REAL NOT NULL,
                    difference REAL NOT NULL,
                    notes TEXT,
                    device_id TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }
    }

    /** Ledger Intelligence v2 — owner/agent financial context for anomalies. */
    val MIGRATION_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS ledger_context (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    related_ledger_entry_id INTEGER,
                    related_anomaly_id TEXT,
                    context_type TEXT NOT NULL,
                    prompt TEXT NOT NULL,
                    owner_answer TEXT,
                    source TEXT NOT NULL,
                    confidence REAL,
                    applies_from_millis INTEGER,
                    applies_to_millis INTEGER,
                    created_at_millis INTEGER NOT NULL,
                    resolved_at_millis INTEGER,
                    superseded_at_millis INTEGER
                )
                """.trimIndent(),
            )
            // Index names must match Room's auto-generated convention and entity @Index declarations.
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_related_ledger_entry_id` ON ledger_context(related_ledger_entry_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_related_anomaly_id` ON ledger_context(related_anomaly_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_created_at_millis` ON ledger_context(created_at_millis)",
            )
        }
    }

    /** Ledger Intelligence v2 efficiency indexes for aggregate-heavy agent queries. */
    val MIGRATION_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_direction_occurred_at " +
                    "ON ledger_entries(direction, occurred_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_type_occurred_at " +
                    "ON ledger_entries(type, occurred_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_is_synced ON ledger_entries(is_synced)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_sync_id ON ledger_entries(sync_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_cash_counts_counted_at ON cash_counts(counted_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_context_anomaly_active " +
                    "ON ledger_context(related_anomaly_id, superseded_at_millis)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS idx_ledger_context_active_period " +
                    "ON ledger_context(superseded_at_millis, applies_from_millis, applies_to_millis)",
            )
        }
    }

    /**
     * Schema correction — renames the mis-named indexes that [MIGRATION_21_22] and [MIGRATION_22_23]
     * created with shorthand `idx_*` names instead of Room's auto-generated `index_<table>_<col>`
     * convention. Devices that already have the DB at v24 (with wrong names) get the correct indexes
     * here; devices that migrated cleanly via the fixed 21→22 / 22→23 paths are unaffected because
     * all DROP / CREATE statements are guarded with IF EXISTS / IF NOT EXISTS.
     */
    val MIGRATION_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // ── ledger_entries: drop old shorthand names ──────────────────────────────────
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_occurred")
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_direction")
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_type")
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_customer")
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_transaction")
            // ── ledger_entries: create with Room-compatible names ─────────────────────────
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_occurred_at` ON ledger_entries(occurred_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_direction` ON ledger_entries(direction)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_type` ON ledger_entries(type)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_customer_id` ON ledger_entries(customer_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_entries_transaction_id` ON ledger_entries(transaction_id)",
            )
            // ── ledger_context: drop old shorthand names ──────────────────────────────────
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_context_anomaly")
            db.execSQL("DROP INDEX IF EXISTS idx_ledger_context_created")
            // ── ledger_context: create with Room-compatible names ─────────────────────────
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_related_ledger_entry_id` ON ledger_context(related_ledger_entry_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_related_anomaly_id` ON ledger_context(related_anomaly_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_ledger_context_created_at_millis` ON ledger_context(created_at_millis)",
            )
        }
    }

    /**
     * Phase 7 K0 — Knowledge Base & Teaching System infrastructure tables.
     * - **knowledge_chunks**: RAG text fragments with source path, language, and embedding BLOB.
     * - **teaching_events**: records of user interactions with features (for mastery tracking).
     * - **lesson_completions**: per-lesson completion records with score.
     * - **feature_mastery**: aggregated mastery level per feature, drives contextual help UX.
     */
    val MIGRATION_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS knowledge_chunks (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    content_text TEXT NOT NULL,
                    source_path TEXT NOT NULL,
                    language_code TEXT NOT NULL,
                    chunk_index INTEGER NOT NULL DEFAULT 0,
                    embedding_blob BLOB,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_source_path` ON knowledge_chunks(source_path)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_knowledge_chunks_language_code` ON knowledge_chunks(language_code)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS teaching_events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    event_type TEXT NOT NULL,
                    feature_id TEXT NOT NULL,
                    skill_invoked TEXT,
                    duration_ms INTEGER NOT NULL DEFAULT 0,
                    outcome TEXT NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_teaching_events_feature_id` ON teaching_events(feature_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_teaching_events_created_at` ON teaching_events(created_at)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS lesson_completions (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    lesson_id TEXT NOT NULL,
                    completed_at INTEGER NOT NULL,
                    score REAL NOT NULL DEFAULT 0.0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_lesson_completions_lesson_id` ON lesson_completions(lesson_id)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS feature_mastery (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    feature_id TEXT NOT NULL UNIQUE,
                    mastery_level TEXT NOT NULL DEFAULT 'UNDISCOVERED',
                    first_seen_at INTEGER NOT NULL,
                    last_practiced_at INTEGER NOT NULL,
                    practice_count INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_feature_mastery_mastery_level` ON feature_mastery(mastery_level)",
            )
        }
    }

    /** Phase C — Prompt C0: cash movement evidence table (capture proof, no images). */
    /** SV0 — staff, appointments, Pro onboarding + BSRC signing prefs on app_settings. */
    val MIGRATION_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS staff_members (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    phone TEXT,
                    role TEXT NOT NULL DEFAULT 'STAFF',
                    is_active INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS appointments (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    customer_id INTEGER,
                    customer_name TEXT NOT NULL,
                    service_item_id INTEGER NOT NULL,
                    staff_member_id INTEGER,
                    scheduled_at INTEGER NOT NULL,
                    duration_minutes INTEGER NOT NULL DEFAULT 60,
                    status TEXT NOT NULL DEFAULT 'BOOKED',
                    deposit_paid REAL NOT NULL DEFAULT 0.0,
                    balance_due REAL NOT NULL DEFAULT 0.0,
                    notes TEXT,
                    transaction_id INTEGER,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointments_scheduled_at` ON appointments(scheduled_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointments_status` ON appointments(status)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_appointments_customer_id` ON appointments(customer_id)",
            )
            addColumnIfMissing(db, "app_settings", "pro_onboarding_shown", "INTEGER NOT NULL DEFAULT 0")
            addColumnIfMissing(db, "app_settings", "pro_activated_at", "INTEGER")
            addColumnIfMissing(db, "app_settings", "voucher_signing_key", "TEXT")
        }
    }

    /** SV6 — Pro agent settings columns on agent_settings. */
    /** Mixed sales — transaction payment split + service delivery amounts for P&L. */
    val MIGRATION_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "transactions", "product_subtotal", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "transactions", "service_subtotal", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "transactions", "amount_paid", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "transactions", "balance_due", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "transactions", "settled_at", "INTEGER")
            addColumnIfMissing(db, "transactions", "parent_transaction_id", "INTEGER")
            addColumnIfMissing(db, "service_deliveries", "charged_amount", "REAL NOT NULL DEFAULT 0.0")
            addColumnIfMissing(db, "debts", "source_transaction_id", "INTEGER")
            db.execSQL(
                "UPDATE transactions SET amount_paid = amount WHERE amount_paid = 0.0 AND balance_due = 0.0",
            )
        }
    }

    /** Phase 10 — structured business profile for agent context and onboarding. */
    val MIGRATION_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS business_profile (
                    id INTEGER PRIMARY KEY NOT NULL,
                    business_name TEXT NOT NULL DEFAULT '',
                    owner_name TEXT NOT NULL DEFAULT '',
                    business_type TEXT NOT NULL DEFAULT 'mixed',
                    description TEXT,
                    primary_products TEXT,
                    primary_services TEXT,
                    specialisation TEXT,
                    target_customer TEXT,
                    location TEXT,
                    open_days TEXT,
                    open_hours TEXT,
                    staff_count INTEGER,
                    main_suppliers TEXT,
                    payment_methods TEXT,
                    monthly_revenue_target REAL,
                    business_goal TEXT,
                    agent_tone TEXT,
                    last_updated_at INTEGER NOT NULL DEFAULT 0,
                    onboarding_complete INTEGER NOT NULL DEFAULT 0,
                    onboarding_step INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT OR IGNORE INTO business_profile (id, business_name, last_updated_at)
                SELECT 1, COALESCE(
                    (SELECT business_name FROM app_settings WHERE id = 1 LIMIT 1),
                    'My Business'
                ), ${System.currentTimeMillis()}
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(db, "agent_settings", "utilisation_agent_enabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(
                db,
                "agent_settings",
                "utilisation_alert_threshold_pct",
                "INTEGER NOT NULL DEFAULT 60",
            )
            addColumnIfMissing(db, "agent_settings", "working_hours_per_day", "INTEGER NOT NULL DEFAULT 8")
            addColumnIfMissing(db, "agent_settings", "no_show_tracker_enabled", "INTEGER NOT NULL DEFAULT 1")
            addColumnIfMissing(
                db,
                "agent_settings",
                "service_pricing_agent_enabled",
                "INTEGER NOT NULL DEFAULT 1",
            )
        }
    }

    /** Pro service layer — service catalogue, vouchers, deliveries (empty in Shop). */
    val MIGRATION_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS service_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    name TEXT NOT NULL,
                    description TEXT,
                    base_price REAL NOT NULL,
                    price_mode TEXT NOT NULL DEFAULT 'FIXED',
                    duration_minutes INTEGER NOT NULL DEFAULT 0,
                    category TEXT,
                    catalogue_token TEXT NOT NULL,
                    warranty_days INTEGER NOT NULL DEFAULT 0,
                    visible_in_kiosk INTEGER NOT NULL DEFAULT 1,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_items_catalogue_token` " +
                    "ON service_items(catalogue_token)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_items_category` ON service_items(category)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS service_vouchers (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    voucher_id TEXT NOT NULL,
                    service_item_id INTEGER NOT NULL,
                    customer_id INTEGER,
                    total_uses INTEGER NOT NULL,
                    remaining_uses INTEGER NOT NULL,
                    amount_paid REAL NOT NULL,
                    purchased_at INTEGER NOT NULL,
                    expires_at INTEGER,
                    last_redeemed_at INTEGER,
                    FOREIGN KEY(service_item_id) REFERENCES service_items(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_vouchers_voucher_id` " +
                    "ON service_vouchers(voucher_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_vouchers_service_item_id` " +
                    "ON service_vouchers(service_item_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_vouchers_customer_id` " +
                    "ON service_vouchers(customer_id)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS service_deliveries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    service_item_id INTEGER NOT NULL,
                    transaction_id INTEGER,
                    customer_id INTEGER,
                    staff_name TEXT,
                    delivered_at INTEGER NOT NULL,
                    warranty_expires_at INTEGER,
                    receipt_token TEXT,
                    FOREIGN KEY(service_item_id) REFERENCES service_items(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_deliveries_service_item_id` " +
                    "ON service_deliveries(service_item_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_deliveries_transaction_id` " +
                    "ON service_deliveries(transaction_id)",
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS `index_service_deliveries_receipt_token` " +
                    "ON service_deliveries(receipt_token)",
            )
        }
    }

    val MIGRATION_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS cash_movement_evidence (
                    id                   INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    ledger_entry_id      INTEGER NOT NULL,
                    capture_method       TEXT NOT NULL,
                    proof_type           TEXT NOT NULL DEFAULT 'UNKNOWN',
                    raw_text             TEXT,
                    parsed_amount        REAL,
                    parsed_reference     TEXT,
                    parsed_counterparty  TEXT,
                    parsed_date          INTEGER,
                    parser_confidence    REAL NOT NULL DEFAULT 0.0,
                    parser_engine        TEXT NOT NULL DEFAULT 'MANUAL',
                    review_status        TEXT NOT NULL DEFAULT 'NEEDS_REVIEW',
                    thumbnail_bytes      BLOB,
                    thumbnail_size_bytes INTEGER NOT NULL DEFAULT 0,
                    created_at           INTEGER NOT NULL,
                    FOREIGN KEY(ledger_entry_id) REFERENCES ledger_entries(id) ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cash_movement_evidence_ledger_entry_id` " +
                    "ON cash_movement_evidence(ledger_entry_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cash_movement_evidence_parsed_reference` " +
                    "ON cash_movement_evidence(parsed_reference)",
            )
        }
    }

    /** Link prepaid vouchers to the POS sale that issued them (unified receipt). */
    val MIGRATION_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            addColumnIfMissing(
                db,
                "service_vouchers",
                "source_transaction_id",
                "INTEGER",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_service_vouchers_source_transaction_id` " +
                    "ON service_vouchers(source_transaction_id)",
            )
        }
    }

    /** Cameroon-first regional defaults: FCFA/XAF replaces the previous Kenya fallback. */
    val MIGRATION_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS app_settings_new (
                    id INTEGER NOT NULL PRIMARY KEY,
                    business_name TEXT NOT NULL DEFAULT 'My Business',
                    currency_code TEXT NOT NULL DEFAULT 'XAF',
                    currency_symbol TEXT NOT NULL DEFAULT 'FCFA',
                    tax_rate REAL NOT NULL DEFAULT 0.0,
                    tax_label TEXT NOT NULL DEFAULT 'Tax',
                    receipt_footer TEXT NOT NULL DEFAULT 'Thank you!',
                    quick_sale_mode INTEGER NOT NULL DEFAULT 0,
                    allow_price_override INTEGER NOT NULL DEFAULT 1,
                    bluetooth_printer_address TEXT,
                    printer_paper_width INTEGER NOT NULL DEFAULT 58,
                    voice_input_enabled INTEGER NOT NULL DEFAULT 1,
                    whisper_model_id TEXT NOT NULL DEFAULT 'whisper-tiny',
                    silence_timeout_ms INTEGER NOT NULL DEFAULT 2500,
                    voice_language_mode TEXT NOT NULL DEFAULT 'AUTO',
                    tts_enabled INTEGER NOT NULL DEFAULT 1,
                    tts_speech_rate REAL NOT NULL DEFAULT 0.9,
                    tts_pitch REAL NOT NULL DEFAULT 1.0,
                    tts_auto_read_agent_alerts INTEGER NOT NULL DEFAULT 0,
                    tts_auto_read_query_answers INTEGER NOT NULL DEFAULT 1,
                    pro_onboarding_shown INTEGER NOT NULL DEFAULT 0,
                    pro_activated_at INTEGER,
                    voucher_signing_key TEXT
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                INSERT INTO app_settings_new (
                    id, business_name, currency_code, currency_symbol, tax_rate, tax_label,
                    receipt_footer, quick_sale_mode, allow_price_override, bluetooth_printer_address,
                    printer_paper_width, voice_input_enabled, whisper_model_id, silence_timeout_ms,
                    voice_language_mode, tts_enabled, tts_speech_rate, tts_pitch,
                    tts_auto_read_agent_alerts, tts_auto_read_query_answers, pro_onboarding_shown,
                    pro_activated_at, voucher_signing_key
                )
                SELECT
                    id,
                    business_name,
                    CASE WHEN currency_code = 'KES' THEN 'XAF' ELSE currency_code END,
                    CASE WHEN currency_symbol = 'KSh' THEN 'FCFA' ELSE currency_symbol END,
                    tax_rate,
                    tax_label,
                    receipt_footer,
                    quick_sale_mode,
                    allow_price_override,
                    bluetooth_printer_address,
                    printer_paper_width,
                    voice_input_enabled,
                    whisper_model_id,
                    silence_timeout_ms,
                    voice_language_mode,
                    tts_enabled,
                    tts_speech_rate,
                    tts_pitch,
                    tts_auto_read_agent_alerts,
                    tts_auto_read_query_answers,
                    pro_onboarding_shown,
                    pro_activated_at,
                    voucher_signing_key
                FROM app_settings
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE app_settings")
            db.execSQL("ALTER TABLE app_settings_new RENAME TO app_settings")
            db.execSQL("INSERT OR IGNORE INTO app_settings (id) VALUES (1)")
            addColumnIfMissing(db, "chat_session_messages", "source_tags", "TEXT")
            addColumnIfMissing(db, "chat_session_messages", "confidence_label", "TEXT")
            addColumnIfMissing(db, "chat_session_messages", "action_hint", "TEXT")
        }
    }

    val MIGRATION_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS business_kpi_snapshots (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    week_start_millis INTEGER NOT NULL,
                    week_revenue REAL NOT NULL,
                    last_week_revenue REAL NOT NULL,
                    product_revenue REAL NOT NULL,
                    service_revenue REAL NOT NULL,
                    tx_count INTEGER NOT NULL,
                    new_customers INTEGER NOT NULL,
                    returning_customers INTEGER NOT NULL,
                    top_product_name TEXT NOT NULL,
                    top_product_revenue REAL NOT NULL,
                    top_service_name TEXT,
                    service_sessions INTEGER NOT NULL DEFAULT 0,
                    best_day TEXT NOT NULL,
                    best_hour INTEGER NOT NULL,
                    credit_outstanding REAL NOT NULL,
                    recorded_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_business_kpi_snapshots_week_start_millis ON business_kpi_snapshots(week_start_millis)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS forecast_calibrations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    product_id INTEGER NOT NULL,
                    product_name TEXT NOT NULL,
                    window_start_millis INTEGER NOT NULL,
                    predicted_day1 INTEGER NOT NULL,
                    predicted_day2 INTEGER NOT NULL,
                    predicted_day3 INTEGER NOT NULL,
                    actual_day1 INTEGER,
                    actual_day2 INTEGER,
                    actual_day3 INTEGER,
                    bias_ratio REAL,
                    recorded_at INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_forecast_calibrations_product_id ON forecast_calibrations(product_id)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_forecast_calibrations_window_start_millis ON forecast_calibrations(window_start_millis)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS business_memory_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    type TEXT NOT NULL,
                    content TEXT NOT NULL,
                    keywords TEXT,
                    embedding_blob BLOB,
                    source TEXT,
                    created_at INTEGER NOT NULL DEFAULT 0,
                    expires_at INTEGER
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS index_business_memory_entries_type ON business_memory_entries(type)")
            db.execSQL("CREATE INDEX IF NOT EXISTS index_business_memory_entries_created_at ON business_memory_entries(created_at)")
        }
    }

    val MIGRATION_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS agent_advice_feedback (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    agent_action_id INTEGER NOT NULL,
                    agent_type TEXT NOT NULL,
                    content_hash TEXT NOT NULL,
                    headline TEXT NOT NULL,
                    detail TEXT NOT NULL DEFAULT '',
                    vote INTEGER NOT NULL,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE UNIQUE INDEX IF NOT EXISTS index_agent_advice_feedback_agent_action_id ON agent_advice_feedback(agent_action_id)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_agent_advice_feedback_content_hash_created_at ON agent_advice_feedback(content_hash, created_at)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS index_agent_advice_feedback_vote_created_at ON agent_advice_feedback(vote, created_at)",
            )
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
        MIGRATION_16_17,
        MIGRATION_17_18,
        MIGRATION_18_19,
        MIGRATION_19_20,
        MIGRATION_20_21,
        MIGRATION_21_22,
        MIGRATION_22_23,
        MIGRATION_23_24,
        MIGRATION_24_25,
        MIGRATION_25_26,
        MIGRATION_26_27,
        MIGRATION_27_28,
        MIGRATION_28_29,
        MIGRATION_29_30,
        MIGRATION_30_31,
        MIGRATION_31_32,
        MIGRATION_32_33,
        MIGRATION_33_34,
        MIGRATION_34_35,
        MIGRATION_35_36,
    )

    private fun addColumnIfMissing(
        db: SupportSQLiteDatabase,
        table: String,
        column: String,
        definition: String,
    ) {
        val cursor = db.query("PRAGMA table_info(`$table`)")
        var exists = false
        while (cursor.moveToNext()) {
            if (cursor.getString(1) == column) {
                exists = true
                break
            }
        }
        cursor.close()
        if (!exists) {
            db.execSQL("ALTER TABLE `$table` ADD COLUMN `$column` $definition")
        }
    }
}
