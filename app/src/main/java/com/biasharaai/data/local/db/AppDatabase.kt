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
    ],
    version = 15,
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

    companion object {
        const val NAME = "biashara.db"
    }
}
