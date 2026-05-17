package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [
        Index(value = ["barcode_value"]),
        Index(value = ["category"]),
        Index(value = ["stock_quantity"]),
    ],
)
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String? = null,
    val price: Double,
    val cost: Double,
    @ColumnInfo(name = "stock_quantity") val stockQuantity: Int,
    val category: String? = null,
    @ColumnInfo(name = "barcode_value") val barcodeValue: String? = null,
    @ColumnInfo(name = "image_url") val imageUrl: String? = null,
    /** Phase 4a — Prompt A1: last stock check epoch ms for Stock Guardian (0 = never). */
    @ColumnInfo(name = "last_stock_check_at") val lastStockCheckAt: Long = 0L,
)
