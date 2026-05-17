package com.biasharaai.cash

import com.biasharaai.data.local.db.ProofType

object ProofTypeDetector {

    fun detect(text: String): ProofType {
        val upper = text.uppercase()
        return when {
            isMpesaSms(upper) -> ProofType.MPESA_SMS
            isKplc(upper) -> ProofType.UTILITY_BILL
            isAirtel(upper) || isMtnMomo(upper) -> ProofType.MPESA_SMS
            isTillSlip(upper) -> ProofType.TILL_SLIP
            isSupplierBill(upper) -> ProofType.SUPPLIER_BILL
            isInvoice(upper) -> ProofType.INVOICE
            isBankSlip(upper) -> ProofType.BANK_SLIP
            isReceipt(upper) -> ProofType.RECEIPT
            else -> ProofType.UNKNOWN
        }
    }

    private fun isMpesaSms(u: String) =
        u.contains("M-PESA") || u.contains("MPESA") ||
        (u.contains("CONFIRMED") && u.contains("KSH"))

    private fun isAirtel(u: String) =
        u.contains("AIRTEL MONEY") || u.contains("AIRTELMONEY") ||
        (u.contains("AIRTEL") && u.contains("TRANSACTION ID"))

    private fun isMtnMomo(u: String) =
        u.contains("MTN MOMO") || u.contains("MTN MOBILE MONEY") ||
        (u.contains("MTN") && u.contains("TXN ID"))

    private fun isKplc(u: String) =
        u.contains("KENYA POWER") || u.contains("KPLC") ||
        (u.contains("TOKENS") && u.contains("KWH"))

    private fun isTillSlip(u: String) =
        u.contains("TILL NO") || u.contains("TILL NUMBER") ||
        (u.contains("CASHIER") && u.contains("TOTAL"))

    private fun isSupplierBill(u: String) =
        u.contains("DELIVERY NOTE") || u.contains("PROFORMA") ||
        u.contains("SUPPLIER") || u.contains("LPO")

    private fun isInvoice(u: String) =
        u.contains("INVOICE") || u.contains("INV NO") || u.contains("INV#")

    private fun isBankSlip(u: String) =
        u.contains("DEPOSIT SLIP") || u.contains("BANK TRANSFER") ||
        u.contains("RTGS") || u.contains("EFT CREDIT")

    private fun isReceipt(u: String) =
        u.contains("RECEIPT") || u.contains("RCPT") || u.contains("OFFICIAL RECEIPT")
}
