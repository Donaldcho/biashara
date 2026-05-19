package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_deliveries",
    foreignKeys = [
        ForeignKey(
            entity = ServiceItem::class,
            parentColumns = ["id"],
            childColumns = ["service_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["service_item_id"]),
        Index(value = ["transaction_id"]),
        Index(value = ["receipt_token"], unique = true),
    ],
)
data class ServiceDelivery(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "service_item_id") val serviceItemId: Long,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    @ColumnInfo(name = "staff_name") val staffName: String? = null,
    @ColumnInfo(name = "delivered_at") val deliveredAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "warranty_expires_at") val warrantyExpiresAt: Long? = null,
    @ColumnInfo(name = "receipt_token") val receiptToken: String? = null,
    /** Amount charged for this delivery (P&L service revenue). */
    @ColumnInfo(name = "charged_amount", defaultValue = "0.0")
    val chargedAmount: Double = 0.0,
)
