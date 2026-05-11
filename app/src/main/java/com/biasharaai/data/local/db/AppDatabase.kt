package com.biasharaai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class,
        Transaction::class,
        SaleLineItem::class,
        AppSettings::class,
    ],
    version = 7,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun saleLineItemDao(): SaleLineItemDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        const val NAME = "biashara.db"
    }
}
