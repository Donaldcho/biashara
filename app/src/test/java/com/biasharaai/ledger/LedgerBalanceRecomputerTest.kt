package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerBalanceRecomputerTest {

    @Test
    fun recomputeAll_openingBalanceSetsAbsolute() = runTest {
        val dao = mockk<LedgerEntryDao>()
        val entries = listOf(
            entry(1, LedgerEntryType.OPENING_BALANCE, LedgerDirection.NEUTRAL, 5000.0, 0.0),
            entry(2, LedgerEntryType.SALE_PRODUCT, LedgerDirection.MONEY_IN, 200.0, 0.0),
        )
        coEvery { dao.getAllSortedByOccurredAt() } returns entries
        coEvery { dao.updateBalance(any(), any()) } returns Unit

        LedgerBalanceRecomputer(dao).recomputeAll()

        coVerify { dao.updateBalance(1, 5000.0) }
        coVerify { dao.updateBalance(2, 5200.0) }
    }

    @Test
    fun recomputeAll_updatesRunningBalances() = runTest {
        val dao = mockk<LedgerEntryDao>()
        val entries = listOf(
            entry(1, direction = LedgerDirection.MONEY_IN, amount = 100.0, balance = 0.0),
            entry(2, direction = LedgerDirection.MONEY_OUT, amount = 30.0, balance = 0.0),
            entry(3, direction = LedgerDirection.MONEY_IN, amount = 50.0, balance = 0.0),
        )
        coEvery { dao.getAllSortedByOccurredAt() } returns entries
        coEvery { dao.updateBalance(any(), any()) } returns Unit

        val count = LedgerBalanceRecomputer(dao).recomputeAll()

        assertEquals(3, count)
        coVerify { dao.updateBalance(1, 100.0) }
        coVerify { dao.updateBalance(2, 70.0) }
        coVerify { dao.updateBalance(3, 120.0) }
    }

    private fun entry(
        id: Long,
        type: LedgerEntryType = LedgerEntryType.SALE_PRODUCT,
        direction: LedgerDirection,
        amount: Double,
        balance: Double,
    ) = LedgerEntry(
        id = id,
        occurredAt = id,
        type = type,
        direction = direction,
        amount = amount,
        currency = "KES",
        description = "Test",
        runningBalance = balance,
        deviceId = "dev",
    )
}
