package com.biasharaai.data.local.db

import androidx.room.withTransaction
import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.payment.PaymentDraft
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
) {

    /**
     * Atomic POS sale: one INCOME [Transaction], line items, stock decrement, optional credit [Debt].
     *
     * @param cartCustomerId currently selected POS customer (walk-in = null); also used on non-credit tabs when set.
     * @return Room id of the new sale [Transaction].
     */
    suspend fun commitPosSale(
        lines: List<CartItem>,
        taxAmount: Double,
        grandTotal: Double,
        taxRatePercent: Double,
        draft: PaymentDraft,
        cartCustomerId: Long?,
    ): Long {
        require(lines.isNotEmpty()) { "Cart is empty" }
        require(grandTotal > 0) { "Grand total must be positive" }

        val receiptNumber = "RCP-${System.currentTimeMillis()}"
        val saleGroupId = UUID.randomUUID().toString()

        val customerId: Long? = when {
            draft.splitMode -> cartCustomerId
            draft.primaryTab == PrimaryPaymentTab.CREDIT ->
                draft.creditCustomerId ?: error("Credit sale requires a customer")
            else -> cartCustomerId
        }

        val (paymentMethod, amountTendered, changeDue, mobileNetwork, mobileRef) =
            paymentFieldsFromDraft(draft, grandTotal)

        return database.withTransaction {
            for (item in lines) {
                val row = productDao.getProductByIdOnce(item.product.id)
                    ?: error("Product no longer exists: ${item.product.name}")
                if (row.stockQuantity < item.quantity) {
                    error(
                        "Insufficient stock for ${item.product.name}: have ${row.stockQuantity}, need ${item.quantity}",
                    )
                }
            }

            val tx = Transaction(
                type = TransactionType.INCOME,
                amount = grandTotal,
                description = "POS sale",
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
            )
            val txId = transactionDao.insertTransaction(tx)

            for (item in lines) {
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
            }

            if (!draft.splitMode && draft.primaryTab == PrimaryPaymentTab.CREDIT) {
                val cid = checkNotNull(customerId)
                debtDao.insertDebt(
                    Debt(
                        customerId = cid,
                        amount = grandTotal,
                        description = "POS sale $receiptNumber",
                        dueDate = draft.creditDueDateMillis,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }

            if (customerId != null) {
                customerDao.updateLastVisit(customerId, System.currentTimeMillis())
            }

            txId
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

        return database.withTransaction {
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
            }

            if (original.paymentMethod == "CREDIT") {
                val cid = original.customerId
                if (cid != null && returnGrandTotal > 0) {
                    debtDao.reduceAmount(cid, returnGrandTotal)
                }
            }

            returnTxId
        }
    }
}
