package com.biasharaai.data.local.db

/** Result of [SaleRepository.commitPosSale]. */
data class PosSaleCommitResult(
    val transactionId: Long,
    val issuedVoucherIds: List<String> = emptyList(),
)
