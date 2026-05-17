package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryDao
import javax.inject.Inject
import javax.inject.Singleton

/** Recomputes [running_balance] on every row (Phase 9 L9). */
@Singleton
class LedgerBalanceRecomputer @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
) {
    suspend fun recomputeAll(): Int {
        val entries = ledgerEntryDao.getAllSortedByOccurredAt()
        var balance = 0.0
        for (entry in entries) {
            balance = LedgerBalanceMath.nextBalance(
                balance,
                entry.type,
                entry.direction,
                entry.amount,
            )
            if (entry.runningBalance != balance) {
                ledgerEntryDao.updateBalance(entry.id, balance)
            }
        }
        return entries.size
    }
}
