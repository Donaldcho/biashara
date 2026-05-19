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
        LedgerEntry::class,
        CashCount::class,
        LedgerContext::class,
        KnowledgeChunk::class,
        TeachingEvent::class,
        LessonCompletion::class,
        FeatureMastery::class,
        CashMovementEvidence::class,
        ServiceItem::class,
        ServiceVoucher::class,
        ServiceDelivery::class,
        StaffMember::class,
        Appointment::class,
        BusinessProfile::class,
    ],
    version = 33,
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

    abstract fun ledgerEntryDao(): LedgerEntryDao

    abstract fun cashCountDao(): CashCountDao

    abstract fun ledgerContextDao(): LedgerContextDao

    abstract fun knowledgeChunkDao(): KnowledgeChunkDao

    abstract fun teachingEventDao(): TeachingEventDao

    abstract fun lessonCompletionDao(): LessonCompletionDao

    abstract fun featureMasteryDao(): FeatureMasteryDao

    abstract fun cashMovementEvidenceDao(): CashMovementEvidenceDao

    abstract fun serviceItemDao(): ServiceItemDao

    abstract fun serviceVoucherDao(): ServiceVoucherDao

    abstract fun serviceDeliveryDao(): ServiceDeliveryDao

    abstract fun staffMemberDao(): StaffMemberDao

    abstract fun appointmentDao(): AppointmentDao

    abstract fun businessProfileDao(): BusinessProfileDao

    companion object {
        const val NAME = "biashara.db"
    }
}
