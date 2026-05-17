package com.biasharaai.cash

import com.biasharaai.data.local.db.LedgerDirection
import com.biasharaai.data.local.db.LedgerEntryType
import com.biasharaai.data.local.db.ParserEngine
import com.biasharaai.data.local.db.ProofType

data class ParsedFields(
    val amount: Double? = null,
    val reference: String? = null,
    val counterparty: String? = null,
    val proofType: ProofType = ProofType.UNKNOWN,
    val parsedDate: Long? = null,
    val confidence: Float = 0f,
    val engine: ParserEngine = ParserEngine.MANUAL,
    val suggestedDirection: LedgerDirection? = null,
    val suggestedType: LedgerEntryType? = null,
)
