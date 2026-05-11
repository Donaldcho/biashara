package com.biasharaai.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Product::class, Transaction::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        const val NAME = "biashara.db"
    }
}
