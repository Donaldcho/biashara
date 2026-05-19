package com.biasharaai.pos.payment

/** Snapshot for POS sale commit (built on payment screen). */
data class PaymentDraft(
    val grandTotal: Double,
    val splitMode: Boolean,
    val primaryTab: PrimaryPaymentTab,
    val cashAmountTendered: Double?,
    val cashChangeDue: Double?,
    val mobileMoneyNetwork: String?,
    val mobileMoneyRef: String?,
    val creditCustomerId: Long?,
    val creditDueDateMillis: Long?,
    /** Optional owner note for informal credit (Prompt U6). */
    val creditNote: String? = null,
    val splitLine1Method: SplitLineMethod?,
    val splitLine1Amount: Double?,
    val splitLine2Method: SplitLineMethod?,
    val splitLine2Amount: Double?,
    /** Voucher ID when paying by service voucher. */
    val voucherId: String? = null,

    /** Mixed-cart payment plan (ignored when cart is not mixed and not deposit). */
    val mixedPaymentPlan: MixedPaymentPlan = MixedPaymentPlan.PAY_ALL,

    /** Used when [mixedPaymentPlan] is [MixedPaymentPlan.DEPOSIT]. */
    val depositAmount: Double? = null,
)

enum class PrimaryPaymentTab {
    CASH,
    MOBILE_MONEY,
    CREDIT,
    VOUCHER,
}

enum class SplitLineMethod {
    CASH,
    MOBILE_MONEY,
}
