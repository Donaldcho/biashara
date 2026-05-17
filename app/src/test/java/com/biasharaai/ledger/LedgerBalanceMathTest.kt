package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType
import org.junit.Assert.assertEquals
import org.junit.Test

class LedgerBalanceMathTest {

    @Test
    fun openingBalance_setsAbsoluteBalance() {
        val bal = LedgerBalanceMath.nextBalance(
            previousBalance = 500.0,
            type = LedgerEntryType.OPENING_BALANCE,
            direction = LedgerDirection.NEUTRAL,
            amount = 10_000.0,
        )
        assertEquals(10_000.0, bal, 0.001)
    }

    @Test
    fun moneyIn_addsToPrevious() {
        val bal = LedgerBalanceMath.nextBalance(
            previousBalance = 100.0,
            type = LedgerEntryType.SALE_PRODUCT,
            direction = LedgerDirection.MONEY_IN,
            amount = 50.0,
        )
        assertEquals(150.0, bal, 0.001)
    }

    @Test
    fun moneyOut_subtractsFromPrevious() {
        val bal = LedgerBalanceMath.nextBalance(
            previousBalance = 100.0,
            type = LedgerEntryType.EXPENSE,
            direction = LedgerDirection.MONEY_OUT,
            amount = 30.0,
        )
        assertEquals(70.0, bal, 0.001)
    }
}
