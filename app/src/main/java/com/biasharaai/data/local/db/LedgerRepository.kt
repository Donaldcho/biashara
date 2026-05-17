package com.biasharaai.data.local.db

import com.biasharaai.device.DeviceIdProvider
import com.biasharaai.ledger.LedgerBalanceMath
import com.biasharaai.ledger.LedgerDescriptionBuilder
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.productline.ProductLineManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sole write path for [ledger_entries]. No update/delete APIs — corrections use [LedgerEntryType.ADJUSTMENT].
 *
 * Call from inside an existing [androidx.room.withTransaction] block so ledger rows commit atomically
 * with their source records.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val ledgerEntryDao: LedgerEntryDao,
    private val cashCountDao: CashCountDao,
    private val settingsDao: AppSettingsDao,
    private val deviceIdProvider: DeviceIdProvider,
    private val productLineManager: ProductLineManager,
    private val moneyFormatter: MoneyFormatter,
) {

    private suspend fun record(
        type: LedgerEntryType,
        direction: LedgerDirection,
        amount: Double,
        description: String,
        occurredAt: Long = System.currentTimeMillis(),
        transactionId: Long? = null,
        customerId: Long? = null,
        debtId: Long? = null,
        voucherId: String? = null,
        serviceDeliveryId: Long? = null,
        productId: Long? = null,
        serviceItemId: Long? = null,
        notes: String? = null,
        staffName: String? = null,
    ) {
        val settings = settingsDao.getSettingsSync() ?: AppSettings()
        val prevBal = ledgerEntryDao.getCurrentBalance() ?: 0.0
        val newBal = LedgerBalanceMath.nextBalance(prevBal, type, direction, amount)
        ledgerEntryDao.insert(
            LedgerEntry(
                occurredAt = occurredAt,
                type = type,
                direction = direction,
                amount = amount,
                currency = settings.currencyCode,
                description = description,
                runningBalance = newBal,
                transactionId = transactionId,
                customerId = customerId,
                debtId = debtId,
                voucherId = voucherId,
                serviceDeliveryId = serviceDeliveryId,
                productId = productId,
                serviceItemId = serviceItemId,
                notes = notes,
                deviceId = deviceIdProvider.get(),
                staffName = staffName,
            ),
        )
    }

    suspend fun recordProductSale(
        transaction: Transaction,
        lineItems: List<SaleLineItem>,
        customerId: Long?,
    ) = record(
        type = LedgerEntryType.SALE_PRODUCT,
        direction = LedgerDirection.MONEY_IN,
        amount = transaction.amount,
        description = LedgerDescriptionBuilder.productSale(lineItems, transaction.receiptNumber),
        occurredAt = transaction.date,
        transactionId = transaction.id,
        customerId = customerId,
    )

    suspend fun recordServiceSale(
        transaction: Transaction,
        serviceNames: List<String>,
        customerId: Long?,
    ) {
        check(productLineManager.isProEnabled()) { "Service sales require Pro" }
        record(
            type = LedgerEntryType.SALE_SERVICE,
            direction = LedgerDirection.MONEY_IN,
            amount = transaction.amount,
            description = LedgerDescriptionBuilder.serviceSale(serviceNames),
            occurredAt = transaction.date,
            transactionId = transaction.id,
            customerId = customerId,
        )
    }

    suspend fun recordMixedSale(
        transaction: Transaction,
        lineItems: List<SaleLineItem>,
        serviceNames: List<String>,
        customerId: Long?,
    ) {
        check(productLineManager.isProEnabled()) { "Mixed sales require Pro" }
        val productQty = lineItems.filter { it.quantity > 0 }.sumOf { it.quantity }
        record(
            type = LedgerEntryType.SALE_MIXED,
            direction = LedgerDirection.MONEY_IN,
            amount = transaction.amount,
            description = LedgerDescriptionBuilder.mixedSale(
                productQty,
                serviceNames.size,
                transaction.receiptNumber,
            ),
            occurredAt = transaction.date,
            transactionId = transaction.id,
            customerId = customerId,
        )
    }

    suspend fun recordExpense(transaction: Transaction, category: String) = record(
        type = LedgerEntryType.EXPENSE,
        direction = LedgerDirection.MONEY_OUT,
        amount = transaction.amount,
        description = "Expense: $category",
        occurredAt = transaction.date,
        transactionId = transaction.id,
    )

    suspend fun recordStockPurchase(transaction: Transaction) = record(
        type = LedgerEntryType.STOCK_PURCHASE,
        direction = LedgerDirection.MONEY_OUT,
        amount = transaction.amount,
        description = "Stock purchased from supplier",
        occurredAt = transaction.date,
        transactionId = transaction.id,
    )

    suspend fun recordCreditExtended(debt: Debt, customerName: String) = record(
        type = LedgerEntryType.CREDIT_EXTENDED,
        direction = LedgerDirection.MONEY_IN,
        amount = debt.amount,
        description = "Credit to $customerName — payment pending",
        occurredAt = debt.createdAt.takeIf { it > 0 } ?: System.currentTimeMillis(),
        customerId = debt.customerId,
        debtId = debt.id,
    )

    suspend fun recordDebtRepaid(debt: Debt, customerName: String, amount: Double) = record(
        type = LedgerEntryType.DEBT_REPAID,
        direction = LedgerDirection.MONEY_IN,
        amount = amount,
        description = "$customerName repaid credit",
        occurredAt = System.currentTimeMillis(),
        customerId = debt.customerId,
        debtId = debt.id,
    )

    suspend fun recordVoucherSale(
        voucherId: String,
        serviceName: String,
        totalUses: Int,
        amount: Double,
        customerId: Long?,
    ) {
        check(productLineManager.isProEnabled()) { "Vouchers require Pro" }
        record(
            type = LedgerEntryType.VOUCHER_SALE,
            direction = LedgerDirection.MONEY_IN,
            amount = amount,
            description = "Prepaid voucher: $serviceName x$totalUses",
            voucherId = voucherId,
            customerId = customerId,
        )
    }

    suspend fun recordVoucherRedemption(
        voucherId: String,
        serviceName: String,
        remainingUses: Int,
        customerId: Long?,
    ) {
        check(productLineManager.isProEnabled()) { "Vouchers require Pro" }
        record(
            type = LedgerEntryType.VOUCHER_REDEEMED,
            direction = LedgerDirection.NEUTRAL,
            amount = 0.0,
            description = "Voucher redeemed: $serviceName ($remainingUses uses left)",
            voucherId = voucherId,
            customerId = customerId,
        )
    }

    suspend fun recordReturn(original: Transaction, refundAmount: Double, returnTransactionId: Long?) = record(
        type = LedgerEntryType.REFUND,
        direction = LedgerDirection.MONEY_OUT,
        amount = refundAmount,
        description = "Refund — return on ${original.receiptNumber ?: "sale #${original.id}"}",
        occurredAt = System.currentTimeMillis(),
        transactionId = returnTransactionId ?: original.id,
        customerId = original.customerId,
    )

    suspend fun recordWarrantyClaim(
        serviceDeliveryId: Long,
        serviceName: String,
        labourCost: Double,
    ) {
        check(productLineManager.isProEnabled()) { "Warranty claims require Pro" }
        record(
            type = LedgerEntryType.WARRANTY_CLAIM,
            direction = LedgerDirection.MONEY_OUT,
            amount = labourCost,
            description = "Warranty claim: re-service on $serviceName",
            serviceDeliveryId = serviceDeliveryId,
        )
    }

    suspend fun recordManualEntry(
        direction: LedgerDirection,
        amount: Double,
        description: String,
        notes: String?,
        occurredAt: Long = System.currentTimeMillis(),
    ) = record(
        type = if (direction == LedgerDirection.MONEY_IN) {
            LedgerEntryType.OTHER_INCOME
        } else {
            LedgerEntryType.ADJUSTMENT
        },
        direction = direction,
        amount = amount,
        description = description,
        notes = notes,
        occurredAt = occurredAt,
    )

    suspend fun recordCashCount(
        expectedBalance: Double,
        actualBalance: Double,
        notes: String?,
    ) {
        val now = System.currentTimeMillis()
        record(
            type = LedgerEntryType.CASH_COUNT,
            direction = LedgerDirection.NEUTRAL,
            amount = 0.0,
            description = buildString {
                append("Cash count: Expected ")
                append(moneyFormatter.format(expectedBalance))
                append(" | Actual ")
                append(moneyFormatter.format(actualBalance))
                append(" | Diff ")
                append(moneyFormatter.format(actualBalance - expectedBalance))
            },
            notes = notes,
            occurredAt = now,
        )
        cashCountDao.insert(
            CashCount(
                countedAt = now,
                expectedBalance = expectedBalance,
                actualBalance = actualBalance,
                difference = actualBalance - expectedBalance,
                notes = notes,
                deviceId = deviceIdProvider.get(),
            ),
        )
    }

    suspend fun recordOpeningBalance(amount: Double) = record(
        type = LedgerEntryType.OPENING_BALANCE,
        direction = LedgerDirection.NEUTRAL,
        amount = amount,
        description = "Opening balance set by owner",
    )
}
