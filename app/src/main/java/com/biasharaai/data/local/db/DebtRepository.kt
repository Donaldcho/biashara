package com.biasharaai.data.local.db

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** One row for the Credit tab list (Prompt U6). */
data class UnpaidDebtRow(
    val debt: Debt,
    val customerName: String,
    val daysOutstanding: Int,
)

@Singleton
class DebtRepository @Inject constructor(
    private val database: AppDatabase,
    private val debtDao: DebtDao,
    private val customerDao: CustomerDao,
    private val transactionDao: TransactionDao,
) {
    fun observeTotalOutstandingForCustomer(customerId: Long): Flow<Double> =
        debtDao.observeTotalOutstandingForCustomer(customerId)

    fun observeUnpaidDebtsOldestFirst(): Flow<List<UnpaidDebtRow>> =
        debtDao.getUnpaidDebtsOldestFirst().flatMapLatest { debts ->
            flow { emit(enrichDebts(debts)) }
        }

    fun observeTotalOutstanding(): Flow<Double> = debtDao.getTotalOutstanding()

    /**
     * Settles the debt row and records matching income so cash-flow totals stay consistent (Prompt U6).
     */
    suspend fun markDebtRepaid(debtId: Long) {
        val debt = debtDao.getDebtById(debtId) ?: return
        if (debt.amount <= 0.0) return
        val amount = debt.amount
        database.withTransaction {
            debtDao.markPaid(debtId)
            transactionDao.insertTransaction(
                Transaction(
                    type = TransactionType.INCOME,
                    amount = amount,
                    description = "Debt repaid",
                    date = System.currentTimeMillis(),
                    customerId = debt.customerId,
                    noteType = TransactionNoteTypes.DEBT_REPAID,
                ),
            )
        }
    }

    private suspend fun enrichDebts(debts: List<Debt>): List<UnpaidDebtRow> =
        debts.map { debt ->
            val customer = customerDao.getCustomerById(debt.customerId)
            UnpaidDebtRow(
                debt = debt,
                customerName = customer?.name ?: "—",
                daysOutstanding = daysBetween(debt.createdAt, System.currentTimeMillis()),
            )
        }

    private fun daysBetween(startMs: Long, endMs: Long): Int {
        if (startMs <= 0L) return 0
        val diff = (endMs - startMs).coerceAtLeast(0L)
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }
}
