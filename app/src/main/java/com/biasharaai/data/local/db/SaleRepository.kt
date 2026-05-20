package com.biasharaai.data.local.db

import androidx.room.withTransaction
import com.biasharaai.enterprise.EnterpriseCatalogRepository
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.payment.MixedPaymentPlan
import com.biasharaai.pos.payment.MixedSaleAllocator
import com.biasharaai.pos.payment.PaymentDraft
import com.biasharaai.pos.cart.VoucherCartItem
import com.biasharaai.service.ServiceCartLine
import com.biasharaai.service.ServiceRepository
import com.biasharaai.service.isVoucherable
import com.biasharaai.service.voucherValidDays
import com.biasharaai.pos.payment.PrimaryPaymentTab
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

data class ReturnLineCommit(
    val originalSaleLineItemId: Long,
    val productId: Long,
    val productName: String,
    val unitPrice: Double,
    val returnQty: Int,
)

private data class PendingEnterpriseStockMovement(
    val product: Product,
    val quantityDelta: Int,
    val movementType: String,
    val sourceType: String,
    val sourceId: String,
    val note: String,
)

/**
 * Atomic POS return: RETURN transaction, negative line items, stock restore, optional debt reduction.
 */
@Singleton
class SaleRepository @Inject constructor(
    private val database: AppDatabase,
    private val transactionDao: TransactionDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val productDao: ProductDao,
    private val debtDao: DebtDao,
    private val customerDao: CustomerDao,
    private val ledgerRepository: LedgerRepository,
    private val serviceRepository: ServiceRepository,
    private val serviceVoucherDao: ServiceVoucherDao,
    private val enterpriseCatalogRepository: EnterpriseCatalogRepository,
) {

    companion object {
        private const val EPSILON = 0.01
    }

    /**
     * Atomic POS sale: one INCOME [Transaction], line items, stock decrement, optional credit [Debt].
     *
     * @param cartCustomerId currently selected POS customer (walk-in = null); also used on non-credit tabs when set.
     * @return Room id of the new sale [Transaction].
     */
    suspend fun commitPosSale(
        lines: List<CartItem>,
        serviceLines: List<ServiceCartLine> = emptyList(),
        voucherLines: List<VoucherCartItem> = emptyList(),
        taxAmount: Double,
        grandTotal: Double,
        taxRatePercent: Double,
        draft: PaymentDraft,
        cartCustomerId: Long?,
        transactionDescription: String = "POS sale",
        transactionNoteTypeOverride: String? = null,
    ): PosSaleCommitResult {
        require(lines.isNotEmpty() || serviceLines.isNotEmpty() || voucherLines.isNotEmpty()) {
            "Cart is empty"
        }
        require(serviceLines.isEmpty() || serviceLines.all { it.quantity > 0 }) { "Invalid service qty" }
        require(grandTotal > 0) { "Grand total must be positive" }

        val receiptNumber = "RCP-${System.currentTimeMillis()}"
        val saleGroupId = UUID.randomUUID().toString()

        val breakdown = MixedSaleAllocator.fromCart(
            productLines = lines,
            serviceLines = serviceLines,
            voucherLines = voucherLines,
            taxRatePercent = taxRatePercent,
        )
        val paymentPlan = when {
            !draft.splitMode && draft.primaryTab == PrimaryPaymentTab.CREDIT -> MixedPaymentPlan.PAY_ALL
            else -> draft.mixedPaymentPlan
        }
        val paymentSplit = if (!draft.splitMode && draft.primaryTab == PrimaryPaymentTab.CREDIT) {
            MixedSaleAllocator.PaymentSplit(
                paidNow = 0.0,
                balanceDue = grandTotal,
                taxOnPaidPortion = 0.0,
                taxOnCreditPortion = breakdown.taxAmount,
            )
        } else {
            MixedSaleAllocator.paymentSplit(breakdown, paymentPlan, draft.depositAmount)
        }
        if (paymentSplit.balanceDue > EPSILON && paymentPlan != MixedPaymentPlan.PAY_ALL) {
            require(cartCustomerId != null || draft.creditCustomerId != null) {
                "Select a customer for balance due / deposit sales"
            }
        }

        val customerId: Long? = when {
            draft.splitMode -> cartCustomerId
            draft.primaryTab == PrimaryPaymentTab.CREDIT ->
                draft.creditCustomerId ?: error("Credit sale requires a customer")
            paymentSplit.balanceDue > EPSILON -> cartCustomerId ?: draft.creditCustomerId
            else -> cartCustomerId
        }

        val amountDueNow = paymentSplit.paidNow
        val (paymentMethod, amountTendered, changeDue, mobileNetwork, mobileRef) =
            paymentFieldsFromDraft(draft, amountDueNow)

        val issuedVoucherIds = mutableListOf<String>()
        val enterpriseStockMovements = mutableListOf<PendingEnterpriseStockMovement>()
        val txId = database.withTransaction {
            for (item in lines) {
                val row = productDao.getProductByIdOnce(item.product.id)
                    ?: error("Product no longer exists: ${item.product.name}")
                if (row.stockQuantity < item.quantity) {
                    error(
                        "Insufficient stock for ${item.product.name}: have ${row.stockQuantity}, need ${item.quantity}",
                    )
                }
            }

            val noteType = transactionNoteTypeOverride ?: when {
                draft.splitMode -> TransactionNoteTypes.STANDARD
                draft.primaryTab == PrimaryPaymentTab.CREDIT -> TransactionNoteTypes.CREDIT_EXTENDED
                paymentPlan == MixedPaymentPlan.DEPOSIT && paymentSplit.balanceDue > EPSILON ->
                    TransactionNoteTypes.DEPOSIT_TAKEN
                paymentPlan == MixedPaymentPlan.CREDIT_SERVICES ||
                    paymentPlan == MixedPaymentPlan.CREDIT_PRODUCTS ->
                    TransactionNoteTypes.PARTIAL_CREDIT
                else -> TransactionNoteTypes.STANDARD
            }

            val tx = Transaction(
                type = TransactionType.INCOME,
                amount = grandTotal,
                description = transactionDescription,
                date = System.currentTimeMillis(),
                paymentMethod = paymentMethod,
                mobileMoneyNetwork = mobileNetwork,
                mobileMoneyRef = mobileRef,
                amountTendered = amountTendered,
                changeDue = changeDue,
                receiptNumber = receiptNumber,
                saleGroupId = saleGroupId,
                taxRate = taxRatePercent,
                taxAmount = taxAmount,
                customerId = customerId,
                relatedSaleTransactionId = null,
                noteType = noteType,
                productSubtotal = breakdown.productSubtotal,
                serviceSubtotal = breakdown.serviceSubtotal + breakdown.voucherSubtotal,
                amountPaid = paymentSplit.paidNow,
                balanceDue = paymentSplit.balanceDue,
            )
            val txId = transactionDao.insertTransaction(tx)

            for (item in lines) {
                val row = productDao.getProductByIdOnce(item.product.id)
                    ?: error("Product no longer exists: ${item.product.name}")
                saleLineItemDao.insertLineItem(
                    SaleLineItem(
                        transactionId = txId,
                        productId = item.product.id,
                        productName = item.product.name,
                        unitPrice = item.effectivePrice,
                        quantity = item.quantity,
                        lineTotal = item.lineTotal,
                        sourceSaleLineItemId = null,
                    ),
                )
                productDao.incrementStock(item.product.id, -item.quantity)
                val stockAfter = row.stockQuantity - item.quantity
                enterpriseStockMovements += PendingEnterpriseStockMovement(
                    product = row.copy(stockQuantity = stockAfter),
                    quantityDelta = -item.quantity,
                    movementType = EnterpriseStockMovement.TYPE_SALE,
                    sourceType = EnterpriseStockMovement.SOURCE_POS_TRANSACTION,
                    sourceId = txId.toString(),
                    note = receiptNumber,
                )
            }

            val committedTx = tx.copy(id = txId)
            val lineItems = saleLineItemDao.getLineItemsForTransactionOnce(txId)
            val serviceNames = serviceLines.flatMap { line ->
                List(line.quantity) { line.service.name }
            }
            val hasProducts = lines.isNotEmpty()
            val hasServices = serviceLines.isNotEmpty()

            if (paymentSplit.paidNow > EPSILON) {
                val cashLedgerTx = committedTx.copy(
                    amount = paymentSplit.paidNow,
                    amountPaid = paymentSplit.paidNow,
                )
                when {
                    hasProducts && hasServices ->
                        ledgerRepository.recordMixedSale(
                            cashLedgerTx,
                            lineItems,
                            serviceNames,
                            customerId,
                            cashAmount = paymentSplit.paidNow,
                        )
                    hasServices ->
                        ledgerRepository.recordServiceSale(
                            cashLedgerTx,
                            serviceNames,
                            customerId,
                            cashAmount = paymentSplit.paidNow,
                        )
                    hasProducts ->
                        ledgerRepository.recordProductSale(
                            cashLedgerTx,
                            lineItems,
                            customerId,
                            cashAmount = paymentSplit.paidNow,
                        )
                    voucherLines.isEmpty() ->
                        ledgerRepository.recordProductSale(
                            cashLedgerTx,
                            lineItems,
                            customerId,
                            cashAmount = paymentSplit.paidNow,
                        )
                }
            }

            if (paymentSplit.balanceDue > EPSILON) {
                val cid = checkNotNull(customerId)
                val debtDescription = buildString {
                    append("Balance: ")
                    append(receiptNumber)
                    when (paymentPlan) {
                        MixedPaymentPlan.DEPOSIT -> append(" (deposit sale)")
                        MixedPaymentPlan.CREDIT_SERVICES -> append(" (service/labour)")
                        MixedPaymentPlan.CREDIT_PRODUCTS -> append(" (parts/products)")
                        else -> append(" (credit)")
                    }
                    val n = draft.creditNote?.trim().orEmpty()
                    if (n.isNotEmpty()) {
                        append(" — ")
                        append(n)
                    }
                }
                val debtId = debtDao.insertDebt(
                    Debt(
                        customerId = cid,
                        amount = paymentSplit.balanceDue,
                        description = debtDescription,
                        dueDate = draft.creditDueDateMillis,
                        createdAt = System.currentTimeMillis(),
                        sourceTransactionId = txId,
                    ),
                )
                val customerName = customerDao.getCustomerById(cid)?.name ?: "Customer"
                ledgerRepository.recordCreditExtended(
                    Debt(
                        id = debtId,
                        customerId = cid,
                        amount = paymentSplit.balanceDue,
                        description = debtDescription,
                        dueDate = draft.creditDueDateMillis,
                        createdAt = System.currentTimeMillis(),
                        sourceTransactionId = txId,
                    ),
                    customerName,
                )
            }

            if (serviceLines.isNotEmpty()) {
                serviceRepository.recordDeliveriesForSale(
                    services = serviceLines,
                    transactionId = txId,
                    customerId = customerId,
                )
            }

            for (voucherItem in voucherLines) {
                require(voucherItem.serviceItem.isVoucherable) {
                    "Service is not voucherable: ${voucherItem.serviceItem.name}"
                }
                val expiresAt = System.currentTimeMillis() +
                    voucherItem.serviceItem.voucherValidDays * 86_400_000L
                val voucher = serviceRepository.sellVoucher(
                    serviceItemId = voucherItem.serviceItem.id,
                    totalUses = voucherItem.uses,
                    amountPaid = voucherItem.totalAmount,
                    customerId = voucherItem.customerId ?: customerId,
                    expiresAt = expiresAt,
                    sourceTransactionId = txId,
                )
                issuedVoucherIds += voucher.voucherId
            }

            if (!draft.splitMode && draft.primaryTab == PrimaryPaymentTab.VOUCHER) {
                val vid = checkNotNull(draft.voucherId) { "Voucher payment missing voucher ID" }
                val voucher = serviceVoucherDao.getByVoucherId(vid)
                    ?: error("Voucher not found: $vid")
                require(voucher.remainingUses > 0) { "Voucher has no remaining uses" }
                serviceVoucherDao.update(
                    voucher.copy(
                        remainingUses = voucher.remainingUses - 1,
                        lastRedeemedAt = System.currentTimeMillis(),
                    ),
                )
            }

            if (customerId != null) {
                customerDao.updateLastVisit(customerId, System.currentTimeMillis())
            }

            txId
        }
        enterpriseStockMovements.forEach { movement ->
            enterpriseCatalogRepository.recordProductStockMovement(
                product = movement.product,
                quantityDelta = movement.quantityDelta,
                movementType = movement.movementType,
                sourceType = movement.sourceType,
                sourceId = movement.sourceId,
                note = movement.note,
            )
        }
        return PosSaleCommitResult(transactionId = txId, issuedVoucherIds = issuedVoucherIds)
    }

    /**
     * Scenario 3 — collect remaining balance on a sale with [Transaction.balanceDue].
     */
    suspend fun commitBalanceSettlement(
        originalTransactionId: Long,
        draft: PaymentDraft,
    ): Long {
        val original = transactionDao.getTransactionById(originalTransactionId)
            ?: error("Sale not found")
        require(original.balanceDue > EPSILON) { "No balance due on this sale" }
        require(original.settledAt == null) { "Sale already settled" }
        val balance = original.balanceDue
        val customerId = original.customerId ?: error("Sale has no customer profile")
        val (paymentMethod, amountTendered, changeDue, mobileNetwork, mobileRef) =
            paymentFieldsFromDraft(draft, balance)

        return database.withTransaction {
            val now = System.currentTimeMillis()
            val settlementTx = Transaction(
                type = TransactionType.INCOME,
                amount = balance,
                description = "Balance: ${original.receiptNumber ?: original.id}",
                date = now,
                paymentMethod = paymentMethod,
                mobileMoneyNetwork = mobileNetwork,
                mobileMoneyRef = mobileRef,
                amountTendered = amountTendered,
                changeDue = changeDue,
                receiptNumber = "RCP-${now}",
                saleGroupId = original.saleGroupId,
                taxRate = 0.0,
                taxAmount = 0.0,
                customerId = customerId,
                relatedSaleTransactionId = originalTransactionId,
                noteType = TransactionNoteTypes.BALANCE_SETTLED,
                productSubtotal = 0.0,
                serviceSubtotal = 0.0,
                amountPaid = balance,
                balanceDue = 0.0,
                parentTransactionId = originalTransactionId,
            )
            val settlementId = transactionDao.insertTransaction(settlementTx)
            transactionDao.updateBalanceSettlement(originalTransactionId, 0.0, now)

            val lineItems = saleLineItemDao.getLineItemsForTransactionOnce(originalTransactionId)
            val hasProducts = lineItems.any { it.quantity > 0 }
            val hasServices = original.serviceSubtotal > EPSILON
            val committed = settlementTx.copy(id = settlementId)
            when {
                hasProducts && hasServices ->
                    ledgerRepository.recordMixedSale(
                        committed,
                        lineItems,
                        emptyList(),
                        customerId,
                        cashAmount = balance,
                    )
                hasServices ->
                    ledgerRepository.recordServiceSale(committed, emptyList(), customerId, cashAmount = balance)
                else ->
                    ledgerRepository.recordProductSale(committed, lineItems, customerId, cashAmount = balance)
            }

            debtDao.getOpenDebtForTransaction(originalTransactionId)?.let { debt ->
                debtDao.markPaid(debt.id)
                val customerName = customerDao.getCustomerById(customerId)?.name ?: "Customer"
                ledgerRepository.recordDebtRepaid(debt, customerName, balance)
            }

            customerDao.updateLastVisit(customerId, now)
            settlementId
        }
    }

    private fun paymentFieldsFromDraft(
        draft: PaymentDraft,
        grandTotal: Double,
    ): PaymentFields {
        if (draft.splitMode) {
            return PaymentFields(
                paymentMethod = "SPLIT",
                amountTendered = null,
                changeDue = null,
                mobileNetwork = null,
                mobileRef = null,
            )
        }
        return when (draft.primaryTab) {
            PrimaryPaymentTab.CASH -> PaymentFields(
                paymentMethod = "CASH",
                amountTendered = draft.cashAmountTendered,
                changeDue = draft.cashChangeDue?.takeIf { it >= 0.0 },
                mobileNetwork = null,
                mobileRef = null,
            )
            PrimaryPaymentTab.MOBILE_MONEY -> PaymentFields(
                paymentMethod = "MOBILE_MONEY",
                amountTendered = null,
                changeDue = null,
                mobileNetwork = draft.mobileMoneyNetwork,
                mobileRef = draft.mobileMoneyRef,
            )
            PrimaryPaymentTab.CREDIT -> PaymentFields(
                paymentMethod = "CREDIT",
                amountTendered = null,
                changeDue = null,
                mobileNetwork = null,
                mobileRef = null,
            )
            PrimaryPaymentTab.VOUCHER -> PaymentFields(
                paymentMethod = "VOUCHER",
                amountTendered = null,
                changeDue = null,
                mobileNetwork = null,
                mobileRef = draft.voucherId,
            )
        }
    }

    private data class PaymentFields(
        val paymentMethod: String,
        val amountTendered: Double?,
        val changeDue: Double?,
        val mobileNetwork: String?,
        val mobileRef: String?,
    )

    /**
     * @return Room id of the new RETURN [Transaction].
     */
    suspend fun commitReturn(
        originalTransactionId: Long,
        lines: List<ReturnLineCommit>,
    ): Long {
        require(lines.isNotEmpty()) { "No return lines" }
        val original = transactionDao.getTransactionById(originalTransactionId)
            ?: error("Original transaction not found")
        require(original.type == TransactionType.INCOME) { "Not a sale transaction" }

        var returnGrandTotal = 0.0
        for (l in lines) {
            require(l.returnQty > 0) { "Invalid return qty" }
            val already = saleLineItemDao.sumReturnedQuantityForOriginalLine(l.originalSaleLineItemId)
            val origLine = saleLineItemDao.getLineItemsForTransactionOnce(originalTransactionId)
                .firstOrNull { it.id == l.originalSaleLineItemId }
                ?: error("Line not in sale")
            require(origLine.quantity > 0) { "Invalid original line" }
            val maxLeft = origLine.quantity - already
            require(l.returnQty <= maxLeft) { "Return qty exceeds remaining" }
            returnGrandTotal += l.unitPrice * l.returnQty
        }

        val enterpriseStockMovements = mutableListOf<PendingEnterpriseStockMovement>()
        val returnTxId = database.withTransaction {
            val returnTx = Transaction(
                type = TransactionType.RETURN,
                amount = -returnGrandTotal,
                description = "Return for sale #${original.receiptNumber ?: original.id}",
                date = System.currentTimeMillis(),
                paymentMethod = "RETURN",
                mobileMoneyNetwork = null,
                mobileMoneyRef = null,
                amountTendered = null,
                changeDue = null,
                receiptNumber = null,
                saleGroupId = original.saleGroupId,
                taxRate = original.taxRate,
                taxAmount = 0.0,
                customerId = original.customerId,
                relatedSaleTransactionId = originalTransactionId,
            )
            val returnTxId = transactionDao.insertTransaction(returnTx)

            for (l in lines) {
                val lineTotal = l.unitPrice * l.returnQty
                saleLineItemDao.insertLineItem(
                    SaleLineItem(
                        transactionId = returnTxId,
                        productId = l.productId,
                        productName = l.productName,
                        unitPrice = l.unitPrice,
                        quantity = -l.returnQty,
                        lineTotal = -lineTotal,
                        sourceSaleLineItemId = l.originalSaleLineItemId,
                    ),
                )
                productDao.incrementStock(l.productId, l.returnQty)
                val product = productDao.getProductByIdOnce(l.productId)
                if (product != null) {
                    enterpriseStockMovements += PendingEnterpriseStockMovement(
                        product = product,
                        quantityDelta = l.returnQty,
                        movementType = EnterpriseStockMovement.TYPE_RETURN,
                        sourceType = EnterpriseStockMovement.SOURCE_POS_TRANSACTION,
                        sourceId = returnTxId.toString(),
                        note = "Return for sale #${original.receiptNumber ?: original.id}",
                    )
                }
            }

            if (original.paymentMethod == "CREDIT") {
                val cid = original.customerId
                if (cid != null && returnGrandTotal > 0) {
                    debtDao.reduceAmount(cid, returnGrandTotal)
                }
            }

            ledgerRepository.recordReturn(original, returnGrandTotal, returnTxId)

            returnTxId
        }
        enterpriseStockMovements.forEach { movement ->
            enterpriseCatalogRepository.recordProductStockMovement(
                product = movement.product,
                quantityDelta = movement.quantityDelta,
                movementType = movement.movementType,
                sourceType = movement.sourceType,
                sourceId = movement.sourceId,
                note = movement.note,
            )
        }
        return returnTxId
    }
}
