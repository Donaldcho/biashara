package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "enterprise_stock_movements",
    indices = [
        Index(value = ["product_id", "created_at"]),
        Index(value = ["enterprise_product_id"]),
        Index(value = ["movement_type", "created_at"]),
        Index(value = ["source_type", "source_id"]),
    ],
)
data class EnterpriseStockMovement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "enterprise_product_id") val enterpriseProductId: String? = null,
    @ColumnInfo(name = "movement_type") val movementType: String,
    @ColumnInfo(name = "quantity_delta") val quantityDelta: Int,
    @ColumnInfo(name = "stock_after") val stockAfter: Int? = null,
    @ColumnInfo(name = "source_type") val sourceType: String? = null,
    @ColumnInfo(name = "source_id") val sourceId: String? = null,
    val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val TYPE_INITIAL_STOCK = "INITIAL_STOCK"
        const val TYPE_ADJUSTMENT = "ADJUSTMENT"
        const val TYPE_SALE = "SALE"
        const val TYPE_RETURN = "RETURN"
        const val TYPE_STOCK_RECEIPT = "STOCK_RECEIPT"

        const val SOURCE_CATALOG_SAVE = "CATALOG_SAVE"
        const val SOURCE_POS_TRANSACTION = "POS_TRANSACTION"
        const val SOURCE_INVENTORY_ADJUSTMENT = "INVENTORY_ADJUSTMENT"
        const val SOURCE_RECEIPT_IMPORT = "RECEIPT_IMPORT"
    }
}
