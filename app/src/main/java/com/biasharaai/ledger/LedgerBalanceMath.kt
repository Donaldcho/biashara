package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType

/** Deterministic running-balance rules shared by [com.biasharaai.data.local.db.LedgerRepository] and [LedgerBalanceRecomputer]. */
object LedgerBalanceMath {

    fun nextBalance(
        previousBalance: Double,
        type: LedgerEntryType,
        direction: LedgerDirection,
        amount: Double,
    ): Double = when (type) {
        LedgerEntryType.OPENING_BALANCE -> amount
        else -> when (direction) {
            LedgerDirection.MONEY_IN -> previousBalance + amount
            LedgerDirection.MONEY_OUT -> previousBalance - amount
            LedgerDirection.NEUTRAL -> previousBalance
        }
    }
}
