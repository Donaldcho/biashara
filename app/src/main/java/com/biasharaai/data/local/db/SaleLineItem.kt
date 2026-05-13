package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sale_line_items",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["transaction_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["transaction_id"])],
)
data class SaleLineItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    @ColumnInfo(name = "transaction_id")
    val transactionId: Long,
    @ColumnInfo(name = "product_id")
    val productId: Long,
    @ColumnInfo(name = "product_name")
    val productName: String,
    @ColumnInfo(name = "unit_price")
    val unitPrice: Double,
    val quantity: Int,
    @ColumnInfo(name = "line_total")
    val lineTotal: Double,

    /** Links a return line back to the original sale line (Prompt P8). */
    @ColumnInfo(name = "source_sale_line_item_id")
    val sourceSaleLineItemId: Long? = null,
)
