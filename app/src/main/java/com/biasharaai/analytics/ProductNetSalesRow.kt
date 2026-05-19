package com.biasharaai.analytics

import androidx.room.ColumnInfo

/** Net product sales in a period (sales minus returns). */
data class ProductNetSalesRow(
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    @ColumnInfo(name = "net_qty") val netQty: Int,
    @ColumnInfo(name = "net_revenue") val netRevenue: Double,
)
