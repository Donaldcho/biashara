package com.biasharaai.di

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.biasharaai.ai.CapabilityResult
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.DeviceCapabilityChecker
import com.biasharaai.ai.GemmaService
import com.biasharaai.ai.InferenceSettingsStore
import com.biasharaai.ai.ModelDownloadManager
import com.biasharaai.data.local.db.AppDatabase
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.LossAlertDao
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.ChatMemoryDao
import com.biasharaai.data.local.db.ChatSessionDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.data.local.db.DebtDao
import com.biasharaai.data.local.db.DatabaseMigrations
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
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
    fun provideModelDownloadManager(@ApplicationContext context: Context): ModelDownloadManager =
        ModelDownloadManager(context)

    @Provides
    @Singleton
    fun provideGemmaService(
        @ApplicationContext context: Context,
        modelDownloadManager: ModelDownloadManager,
        inferenceSettingsStore: InferenceSettingsStore,
    ): GemmaService = GemmaService(context, modelDownloadManager, inferenceSettingsStore)
}
