package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_items",
    indices = [
        Index(value = ["catalogue_token"], unique = true),
        Index(value = ["category"]),
    ],
)
data class ServiceItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String? = null,
    @ColumnInfo(name = "base_price") val basePrice: Double,
    @ColumnInfo(name = "price_mode") val priceMode: String = ServicePriceMode.FIXED.name,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 0,
    val category: String? = null,
    /** BSVC-prefixed QR token for POS scan. */
    @ColumnInfo(name = "catalogue_token") val catalogueToken: String,
    @ColumnInfo(name = "warranty_days") val warrantyDays: Int = 0,
    @ColumnInfo(name = "visible_in_kiosk") val visibleInKiosk: Boolean = true,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "enterprise_catalog_id") val enterpriseCatalogId: String? = null,
    @ColumnInfo(name = "enterprise_catalog_version") val enterpriseCatalogVersion: Long = 0L,
    @ColumnInfo(name = "enterprise_catalog_updated_at") val enterpriseCatalogUpdatedAt: Long = 0L,
    @ColumnInfo(name = "enterprise_sync_status") val enterpriseSyncStatus: String = SYNC_LOCAL,
) {
    fun priceModeEnum(): ServicePriceMode = ServicePriceMode.parse(priceMode)

    companion object {
        const val SYNC_LOCAL = "LOCAL"
        const val SYNC_PENDING = "PENDING"
        const val SYNC_SYNCED = "SYNCED"
        const val SYNC_CONFLICT = "CONFLICT"
    }
}
