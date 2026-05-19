package com.biasharaai.data.local.db

/** Values for [Transaction.noteType] (Prompt U1 / U6). */
object TransactionNoteTypes {
    const val STANDARD = "STANDARD"
    const val CREDIT_EXTENDED = "CREDIT_EXTENDED"
    const val DEBT_REPAID = "DEBT_REPAID"
    /** Prompt U8 — sale created from parsed WhatsApp / text order. */
    const val WHATSAPP_ORDER = "WHATSAPP_ORDER"

    /** Partial payment at drop-off; [Transaction.balanceDue] remains. */
    const val DEPOSIT_TAKEN = "DEPOSIT_TAKEN"

    /** Parts paid now, remainder on customer credit (mixed cart). */
    const val PARTIAL_CREDIT = "PARTIAL_CREDIT"

    /** Final collection against an open balance / deposit. */
    const val BALANCE_SETTLED = "BALANCE_SETTLED"
}
