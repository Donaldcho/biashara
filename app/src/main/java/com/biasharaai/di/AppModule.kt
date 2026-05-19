package com.biasharaai.di

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biasharaai.ai.ActiveModelStore
import com.biasharaai.ai.CapabilityResult
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DeviceCapabilityChecker
import com.biasharaai.ai.EmbeddingEngine
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AgentAdviceFeedbackDao
import com.biasharaai.data.local.db.AgentRunLogDao
import com.biasharaai.data.local.db.ModelDescriptorDao
import com.biasharaai.data.local.db.PendingNotificationDao
import com.biasharaai.data.local.db.SkillDescriptorDao
import com.biasharaai.data.local.db.SkillPackRecordDao
import com.biasharaai.data.local.db.AgentSettingDao
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.LossAlertDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CashCountDao
import com.biasharaai.data.local.db.LedgerContextDao
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.ChatMemoryDao
import com.biasharaai.data.local.db.ChatSessionDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.DatabaseMigrations
import com.biasharaai.data.local.db.FeatureMasteryDao
import com.biasharaai.data.local.db.KnowledgeChunkDao
import com.biasharaai.data.local.db.LessonCompletionDao
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.AppointmentDao
import com.biasharaai.data.local.db.BusinessProfileDao
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucherDao
import com.biasharaai.data.local.db.StaffMemberDao
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.TeachingEventDao
import com.biasharaai.data.local.db.TransactionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // ── Database ────────────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .addMigrations(*DatabaseMigrations.ALL)
            .addCallback(
                object : RoomDatabase.Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        // Fresh install: Room creates `app_settings` empty; seed the singleton row.
                        db.execSQL("INSERT OR IGNORE INTO app_settings (id) VALUES (1)")
                        db.execSQL("INSERT OR IGNORE INTO agent_settings (id) VALUES (1)")
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO business_profile (id, business_name, last_updated_at)
                            SELECT 1, COALESCE(
                                (SELECT business_name FROM app_settings WHERE id = 1),
                                'My Business'
                            ), ${System.currentTimeMillis()}
                            """.trimIndent(),
                        )
                    }
                },
            )
            .build()

    @Provides
    fun provideProductDao(database: AppDatabase): ProductDao = database.productDao()

    @Provides
    fun provideTransactionDao(database: AppDatabase): TransactionDao = database.transactionDao()

    @Provides
    fun provideSaleLineItemDao(database: AppDatabase): SaleLineItemDao = database.saleLineItemDao()

    @Provides
    fun provideAppSettingsDao(database: AppDatabase): AppSettingsDao = database.appSettingsDao()

    @Provides
    fun provideCustomerDao(database: AppDatabase): CustomerDao = database.customerDao()

    @Provides
    fun provideDebtDao(database: AppDatabase): DebtDao = database.debtDao()

    @Provides
    fun provideAlertDao(database: AppDatabase): AlertDao = database.alertDao()

    @Provides
    fun provideLossAlertDao(database: AppDatabase): LossAlertDao = database.lossAlertDao()

    @Provides
    fun provideChatMemoryDao(database: AppDatabase): ChatMemoryDao = database.chatMemoryDao()

    @Provides
    fun provideChatSessionDao(database: AppDatabase): ChatSessionDao = database.chatSessionDao()

    @Provides
    fun provideAgentActionDao(database: AppDatabase): AgentActionDao = database.agentActionDao()

    @Provides
    fun provideAgentAdviceFeedbackDao(database: AppDatabase): AgentAdviceFeedbackDao =
        database.agentAdviceFeedbackDao()

    @Provides
    fun provideAgentSettingDao(database: AppDatabase): AgentSettingDao = database.agentSettingDao()

    @Provides
    fun provideAgentRunLogDao(database: AppDatabase): AgentRunLogDao = database.agentRunLogDao()

    @Provides
    fun providePendingNotificationDao(database: AppDatabase): PendingNotificationDao =
        database.pendingNotificationDao()

    @Provides
    fun provideModelDescriptorDao(database: AppDatabase): ModelDescriptorDao = database.modelDescriptorDao()

    @Provides
    fun provideSkillDescriptorDao(database: AppDatabase): SkillDescriptorDao = database.skillDescriptorDao()

    @Provides
    fun provideSkillPackRecordDao(database: AppDatabase): SkillPackRecordDao = database.skillPackRecordDao()

    @Provides
    fun provideLedgerEntryDao(database: AppDatabase): LedgerEntryDao = database.ledgerEntryDao()

    @Provides
    fun provideCashCountDao(database: AppDatabase): CashCountDao = database.cashCountDao()

    @Provides
    fun provideLedgerContextDao(database: AppDatabase): LedgerContextDao = database.ledgerContextDao()

    @Provides
    fun provideKnowledgeChunkDao(database: AppDatabase): com.biasharaai.data.local.db.KnowledgeChunkDao = database.knowledgeChunkDao()

    @Provides
    fun provideTeachingEventDao(database: AppDatabase): com.biasharaai.data.local.db.TeachingEventDao = database.teachingEventDao()

    @Provides
    fun provideLessonCompletionDao(database: AppDatabase): com.biasharaai.data.local.db.LessonCompletionDao = database.lessonCompletionDao()

    @Provides
    fun provideFeatureMasteryDao(database: AppDatabase): com.biasharaai.data.local.db.FeatureMasteryDao = database.featureMasteryDao()

    @Provides
    fun provideCashMovementEvidenceDao(database: AppDatabase): com.biasharaai.data.local.db.CashMovementEvidenceDao = database.cashMovementEvidenceDao()

    @Provides
    fun provideServiceItemDao(database: AppDatabase): ServiceItemDao = database.serviceItemDao()

    @Provides
    fun provideServiceVoucherDao(database: AppDatabase): ServiceVoucherDao = database.serviceVoucherDao()

    @Provides
    fun provideServiceDeliveryDao(database: AppDatabase): ServiceDeliveryDao = database.serviceDeliveryDao()

    @Provides
    fun provideStaffMemberDao(database: AppDatabase): StaffMemberDao = database.staffMemberDao()

    @Provides
    fun provideAppointmentDao(database: AppDatabase): AppointmentDao = database.appointmentDao()

    @Provides
    fun provideBusinessProfileDao(database: AppDatabase): BusinessProfileDao = database.businessProfileDao()

    @Provides
    fun provideBusinessKpiSnapshotDao(database: AppDatabase): com.biasharaai.data.local.db.BusinessKpiSnapshotDao = database.businessKpiSnapshotDao()

    @Provides
    fun provideForecastCalibrationDao(database: AppDatabase): com.biasharaai.data.local.db.ForecastCalibrationDao = database.forecastCalibrationDao()

    @Provides
    fun provideBusinessMemoryEntryDao(database: AppDatabase): com.biasharaai.data.local.db.BusinessMemoryEntryDao = database.businessMemoryEntryDao()

    @Provides
    @Singleton
    fun provideNotificationManager(@ApplicationContext context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Prompt U5: 24h periodic loss scan is scheduled from [BiasharaApp] via [com.biasharaai.loss.LossAlertScheduler].

    // ── AI / Device Capability ──────────────────────────────────────────

    @Provides
    @Singleton
    fun provideCapabilityResult(
        @ApplicationContext context: Context,
        modelDownloadManager: ModelDownloadManager,
    ): CapabilityResult =
        DeviceCapabilityChecker.evaluate(
            context,
            modelPresentOnDisk = modelDownloadManager.isModelDownloaded,
        )

    @Provides
    @Singleton
    fun provideCapabilityTier(result: CapabilityResult): CapabilityTier = result.tier

    @Provides
    @Singleton
    fun provideGemmaService(activeModelStore: ActiveModelStore): GemmaService =
        GemmaService(activeModelStore)

    @Provides
    @Singleton
    fun provideEmbeddingEngine(): EmbeddingEngine = EmbeddingEngine()
}
