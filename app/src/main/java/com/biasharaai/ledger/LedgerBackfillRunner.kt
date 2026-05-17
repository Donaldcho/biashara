package com.biasharaai.ledger

import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.CustomerDao
import com.biasharaai.ledger.LedgerDescriptionBuilder
import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntry
import com.biasharaai.data.local.db.LedgerEntryDao
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.Transaction
import com.biasharaai.data.local.db.TransactionDao
import com.biasharaai.data.local.db.TransactionNoteTypes
import com.biasharaai.data.local.db.TransactionType
import com.biasharaai.device.DeviceIdProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time replay of historical [transactions] into [ledger_entries] (Phase 9 L3).
 */
@Singleton
class LedgerBackfillRunner @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val customerDao: CustomerDao,
    private val settingsDao: AppSettingsDao,
    private val deviceIdProvider: DeviceIdProvider,
) {

    suspend fun runIfEmpty(): Boolean {
        if (ledgerEntryDao.count() > 0) return false
        val settings = settingsDao.getSettingsSync() ?: AppSettings()
        val deviceId = deviceIdProvider.get()
        val txs = transactionDao.getTransactionsList()
            .sortedWith(compareBy({ it.date }, { it.id }))
        var balance = 0.0
        val rows = mutableListOf<LedgerEntry>()
        for (tx in txs) {
            val event = mapTransaction(tx, settings.currencyCode, deviceId) ?: continue
            balance = applyBalance(balance, event.direction, event.amount)
            rows += event.copy(runningBalance = balance)
        }
        if (rows.isNotEmpty()) {
            ledgerEntryDao.insertAll(rows)
        }
        return true
    }

    private suspend fun mapTransaction(
        tx: Transaction,
        currency: String,
        deviceId: String,
    ): LedgerEntry? {
        return when (tx.type) {
            TransactionType.INCOME -> mapIncome(tx, currency, deviceId)
            TransactionType.EXPENSE -> mapExpense(tx, currency, deviceId)
            TransactionType.RETURN -> mapReturn(tx, currency, deviceId)
        }
    }

    private suspend fun mapIncome(tx: Transaction, currency: String, deviceId: String): LedgerEntry? {
        val amount = tx.amount
        if (amount <= 0) return null
        return when {
            tx.noteType == TransactionNoteTypes.CREDIT_EXTENDED || tx.paymentMethod == "CREDIT" -> {
                val name = tx.customerId?.let { customerDao.getCustomerById(it)?.name } ?: "Customer"
                baseEntry(
                    tx,
                    LedgerEntryType.CREDIT_EXTENDED,
                    LedgerDirection.MONEY_IN,
                    amount,
                    "Credit to $name — payment pending",
                    currency,
                    deviceId,
                )
            }
            tx.noteType == TransactionNoteTypes.DEBT_REPAID -> {
                val name = tx.customerId?.let { customerDao.getCustomerById(it)?.name } ?: "Customer"
                baseEntry(
                    tx,
                    LedgerEntryType.DEBT_REPAID,
                    LedgerDirection.MONEY_IN,
                    amount,
                    "$name repaid credit",
                    currency,
                    deviceId,
                )
            }
            else -> {
                val lines = saleLineItemDao.getLineItemsForTransactionOnce(tx.id)
                baseEntry(
                    tx,
                    LedgerEntryType.SALE_PRODUCT,
                    LedgerDirection.MONEY_IN,
                    amount,
                    LedgerDescriptionBuilder.productSale(lines, tx.receiptNumber),
                    currency,
                    deviceId,
                )
            }
        }
    }

    private fun mapExpense(tx: Transaction, currency: String, deviceId: String): LedgerEntry? {
        if (tx.amount <= 0) return null
        val desc = tx.description.lowercase()
        val type = if (desc.contains("stock") || desc.contains("inventory")) {
            LedgerEntryType.STOCK_PURCHASE
        } else {
            LedgerEntryType.EXPENSE
        }
        val label = if (type == LedgerEntryType.STOCK_PURCHASE) {
            "Stock purchased from supplier"
        } else {
            "Expense: ${tx.description}"
        }
        return baseEntry(tx, type, LedgerDirection.MONEY_OUT, tx.amount, label, currency, deviceId)
    }

    private fun mapReturn(tx: Transaction, currency: String, deviceId: String): LedgerEntry? {
        val refund = kotlin.math.abs(tx.amount)
        if (refund <= 0) return null
        return baseEntry(
            tx,
            LedgerEntryType.REFUND,
            LedgerDirection.MONEY_OUT,
            refund,
            "Refund — ${tx.description}",
            currency,
            deviceId,
        )
    }

    private fun baseEntry(
        tx: Transaction,
        type: LedgerEntryType,
        direction: LedgerDirection,
        amount: Double,
        description: String,
        currency: String,
        deviceId: String,
    ) = LedgerEntry(
        occurredAt = tx.date,
        type = type,
        direction = direction,
        amount = amount,
        currency = currency,
        description = description,
        runningBalance = 0.0,
        transactionId = tx.id,
        customerId = tx.customerId,
        deviceId = deviceId,
        isSynced = true,
    )

    private fun applyBalance(current: Double, direction: LedgerDirection, amount: Double): Double =
        when (direction) {
            LedgerDirection.MONEY_IN -> current + amount
            LedgerDirection.MONEY_OUT -> current - amount
            LedgerDirection.NEUTRAL -> current
        }
}
