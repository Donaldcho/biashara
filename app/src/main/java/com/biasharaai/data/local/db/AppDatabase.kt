package com.biasharaai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class,
        Transaction::class,
        SaleLineItem::class,
        AppSettings::class,
        Customer::class,
        Debt::class,
        Alert::class,
        AiBusinessMemory::class,
        ChatSessionEntity::class,
        ChatSessionMessageEntity::class,
        AgentAction::class,
        AgentSetting::class,
        AgentRunLog::class,
        PendingNotification::class,
        ModelDescriptor::class,
        SkillDescriptor::class,
        SkillPackRecord::class,
    ],
    version = 20,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun saleLineItemDao(): SaleLineItemDao
    abstract fun appSettingsDao(): AppSettingsDao
    abstract fun customerDao(): CustomerDao
    abstract fun debtDao(): DebtDao
    abstract fun alertDao(): AlertDao

    abstract fun lossAlertDao(): LossAlertDao

    abstract fun chatMemoryDao(): ChatMemoryDao

    abstract fun chatSessionDao(): ChatSessionDao

    abstract fun agentActionDao(): AgentActionDao

    abstract fun agentSettingDao(): AgentSettingDao

    abstract fun agentRunLogDao(): AgentRunLogDao

    abstract fun pendingNotificationDao(): PendingNotificationDao

    abstract fun modelDescriptorDao(): ModelDescriptorDao

    abstract fun skillDescriptorDao(): SkillDescriptorDao

    abstract fun skillPackRecordDao(): SkillPackRecordDao

    companion object {
        const val NAME = "biashara.db"
    }
}
