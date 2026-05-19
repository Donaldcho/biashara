package com.biasharaai.pos.receipt

import com.biasharaai.data.local.db.SaleLineItem
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.data.local.db.ServiceDelivery
import com.biasharaai.data.local.db.ServiceDeliveryDao
import com.biasharaai.data.local.db.ServiceItemDao
import com.biasharaai.data.local.db.ServiceVoucher
import com.biasharaai.data.local.db.ServiceVoucherDao
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Builds printable receipt lines for a POS [transaction][com.biasharaai.data.local.db.Transaction].
 * Products live in [sale_line_items]; services in [service_deliveries]; prepaid vouchers in [service_vouchers].
 */
@Singleton
class PosReceiptAssembler @Inject constructor(
    private val saleLineItemDao: SaleLineItemDao,
    private val serviceDeliveryDao: ServiceDeliveryDao,
    private val serviceItemDao: ServiceItemDao,
    private val serviceVoucherDao: ServiceVoucherDao,
) {

    data class AssembledReceipt(
        val lines: List<PosReceiptLine>,
        val voucherIds: List<String>,
    )

    suspend fun assemble(
        transactionId: Long,
        extraVoucherIds: List<String> = emptyList(),
    ): AssembledReceipt {
        val productLines = saleLineItemDao.getLineItemsForTransactionOnce(transactionId)
            .filter { it.quantity != 0 }
            .map { it.toReceiptLine() }

        val deliveries = serviceDeliveryDao.getByTransactionOnce(transactionId)
        val serviceLines = groupServiceDeliveries(deliveries)

        val voucherIds = buildSet {
            addAll(extraVoucherIds)
            addAll(serviceVoucherDao.getVoucherIdsBySourceTransaction(transactionId))
        }
        val voucherLines = voucherIds.mapNotNull { id ->
            serviceVoucherDao.getByVoucherId(id)?.toReceiptLine(serviceItemDao)
        }

        val allVoucherIds = voucherIds.toList()
        return AssembledReceipt(
            lines = productLines + serviceLines + voucherLines,
            voucherIds = allVoucherIds,
        )
    }

    private suspend fun groupServiceDeliveries(deliveries: List<ServiceDelivery>): List<PosReceiptLine> {
        if (deliveries.isEmpty()) return emptyList()
        return deliveries
            .groupBy { d ->
                Triple(d.serviceItemId, d.chargedAmount, d.staffName?.trim()?.takeIf { it.isNotEmpty() })
            }
            .mapNotNull { (key, rows) ->
                val item = serviceItemDao.getById(key.first) ?: return@mapNotNull null
                val qty = rows.size
                val unit = key.second
                val displayName = buildString {
                    append(item.name)
                    key.third?.let { staff ->
                        append(" — ")
                        append(staff)
                    }
                }
                PosReceiptLine(
                    name = displayName,
                    quantity = qty,
                    unitPrice = unit,
                    lineTotal = unit * qty,
                    kind = PosReceiptLine.Kind.SERVICE,
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    private fun SaleLineItem.toReceiptLine(): PosReceiptLine {
        val qty = kotlin.math.abs(quantity)
        val unit = kotlin.math.abs(unitPrice)
        return PosReceiptLine(
            name = productName,
            quantity = qty,
            unitPrice = unit,
            lineTotal = kotlin.math.abs(lineTotal),
            kind = PosReceiptLine.Kind.PRODUCT,
        )
    }

    private suspend fun ServiceVoucher.toReceiptLine(serviceItemDao: ServiceItemDao): PosReceiptLine? {
        val item = serviceItemDao.getById(serviceItemId) ?: return null
        return PosReceiptLine(
            name = item.name,
            quantity = totalUses,
            unitPrice = amountPaid / totalUses.coerceAtLeast(1),
            lineTotal = amountPaid,
            kind = PosReceiptLine.Kind.VOUCHER,
        )
    }
}
