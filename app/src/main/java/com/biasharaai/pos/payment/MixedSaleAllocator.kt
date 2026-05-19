package com.biasharaai.pos.payment

import com.biasharaai.pos.cart.CartItem
import com.biasharaai.pos.cart.VoucherCartItem
import com.biasharaai.service.ServiceCartLine
import kotlin.math.max

/**
 * Splits mixed-cart totals for partial credit, deposits, and P&L reporting.
 * Tax is allocated proportionally to product vs service subtotals.
 */
object MixedSaleAllocator {

    data class Breakdown(
        val productSubtotal: Double,
        val serviceSubtotal: Double,
        val voucherSubtotal: Double,
        val taxAmount: Double,
        val grandTotal: Double,
    ) {
        val preTaxSubtotal: Double get() = productSubtotal + serviceSubtotal + voucherSubtotal
    }

    data class PaymentSplit(
        val paidNow: Double,
        val balanceDue: Double,
        val taxOnPaidPortion: Double,
        val taxOnCreditPortion: Double,
    )

    fun fromCart(
        productLines: List<CartItem>,
        serviceLines: List<ServiceCartLine>,
        voucherLines: List<VoucherCartItem>,
        taxRatePercent: Double,
    ): Breakdown {
        val productSubtotal = productLines.sumOf { it.lineTotal }
        val serviceSubtotal = serviceLines.sumOf { it.lineTotal }
        val voucherSubtotal = voucherLines.sumOf { it.totalAmount }
        val preTax = productSubtotal + serviceSubtotal + voucherSubtotal
        val taxAmount = preTax * (taxRatePercent / 100.0)
        return Breakdown(
            productSubtotal = productSubtotal,
            serviceSubtotal = serviceSubtotal,
            voucherSubtotal = voucherSubtotal,
            taxAmount = taxAmount,
            grandTotal = preTax + taxAmount,
        )
    }

    fun paymentSplit(
        breakdown: Breakdown,
        plan: MixedPaymentPlan,
        depositAmount: Double? = null,
    ): PaymentSplit {
        val preTax = breakdown.preTaxSubtotal
        if (preTax <= 0.0) {
            return PaymentSplit(
                paidNow = breakdown.grandTotal,
                balanceDue = 0.0,
                taxOnPaidPortion = breakdown.taxAmount,
                taxOnCreditPortion = 0.0,
            )
        }
        val productShare = breakdown.productSubtotal / preTax
        val serviceShare = (breakdown.serviceSubtotal + breakdown.voucherSubtotal) / preTax

        return when (plan) {
            MixedPaymentPlan.PAY_ALL -> PaymentSplit(
                paidNow = breakdown.grandTotal,
                balanceDue = 0.0,
                taxOnPaidPortion = breakdown.taxAmount,
                taxOnCreditPortion = 0.0,
            )
            MixedPaymentPlan.CREDIT_SERVICES -> {
                val creditPreTax = breakdown.serviceSubtotal + breakdown.voucherSubtotal
                val paidPreTax = breakdown.productSubtotal
                val taxCredit = breakdown.taxAmount * serviceShare
                val taxPaid = breakdown.taxAmount - taxCredit
                PaymentSplit(
                    paidNow = paidPreTax + taxPaid,
                    balanceDue = creditPreTax + taxCredit,
                    taxOnPaidPortion = taxPaid,
                    taxOnCreditPortion = taxCredit,
                )
            }
            MixedPaymentPlan.CREDIT_PRODUCTS -> {
                val creditPreTax = breakdown.productSubtotal
                val paidPreTax = breakdown.serviceSubtotal + breakdown.voucherSubtotal
                val taxCredit = breakdown.taxAmount * productShare
                val taxPaid = breakdown.taxAmount - taxCredit
                PaymentSplit(
                    paidNow = paidPreTax + taxPaid,
                    balanceDue = creditPreTax + taxCredit,
                    taxOnPaidPortion = taxPaid,
                    taxOnCreditPortion = taxCredit,
                )
            }
            MixedPaymentPlan.DEPOSIT -> {
                val deposit = depositAmount?.coerceIn(0.0, breakdown.grandTotal) ?: 0.0
                PaymentSplit(
                    paidNow = deposit,
                    balanceDue = max(0.0, breakdown.grandTotal - deposit),
                    taxOnPaidPortion = if (breakdown.grandTotal > 0) {
                        breakdown.taxAmount * (deposit / breakdown.grandTotal)
                    } else {
                        0.0
                    },
                    taxOnCreditPortion = if (breakdown.grandTotal > 0) {
                        breakdown.taxAmount * (1.0 - deposit / breakdown.grandTotal)
                    } else {
                        0.0
                    },
                )
            }
        }
    }

    fun isMixedCart(
        productLines: List<CartItem>,
        serviceLines: List<ServiceCartLine>,
    ): Boolean = productLines.isNotEmpty() && serviceLines.isNotEmpty()
}
