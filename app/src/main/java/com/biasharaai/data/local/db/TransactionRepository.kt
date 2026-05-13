package com.biasharaai.data.local.db

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository wrapper around [TransactionDao].
 *
 * Provides a clean API surface for the ViewModel / AI layers and
 * keeps the DAO as a Room-only concern.
 */
@Singleton
class TransactionRepository @Inject constructor(
    private val transactionDao: TransactionDao,
) {
    /** Insert a transaction and return its generated ID. */
    suspend fun insert(transaction: Transaction): Long =
        transactionDao.insertTransaction(transaction)

    /** All transactions ordered newest-first. */
    fun getAll(): Flow<List<Transaction>> =
        transactionDao.getAllTransactions()

    /** Transactions within [startDate]..[endDate] (epoch millis, inclusive). */
    fun getByPeriod(startDate: Long, endDate: Long): Flow<List<Transaction>> =
        transactionDao.getTransactionsByPeriod(startDate, endDate)

    /** POS sales ([TransactionType.INCOME] with positive sale lines), newest first. */
    fun observeCompletedPosSales(): Flow<List<Transaction>> =
        transactionDao.observeCompletedPosSales()

    suspend fun getTransactionById(id: Long): Transaction? =
        transactionDao.getTransactionById(id)

    fun observeTransactionById(id: Long): Flow<Transaction?> =
        transactionDao.observeTransactionById(id)

    suspend fun getTransactionsBetween(startDate: Long, endDate: Long): List<Transaction> =
        transactionDao.getTransactionsBetween(startDate, endDate)
}
