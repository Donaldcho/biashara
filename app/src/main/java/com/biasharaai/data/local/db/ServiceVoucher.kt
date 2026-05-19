package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "service_vouchers",
    foreignKeys = [
        ForeignKey(
            entity = ServiceItem::class,
            parentColumns = ["id"],
            childColumns = ["service_item_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["voucher_id"], unique = true),
        Index(value = ["service_item_id"]),
        Index(value = ["customer_id"]),
        Index(value = ["source_transaction_id"]),
    ],
)
data class ServiceVoucher(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "voucher_id") val voucherId: String,
    @ColumnInfo(name = "service_item_id") val serviceItemId: Long,
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    @ColumnInfo(name = "source_transaction_id") val sourceTransactionId: Long? = null,
    @ColumnInfo(name = "total_uses") val totalUses: Int,
    @ColumnInfo(name = "remaining_uses") val remainingUses: Int,
    @ColumnInfo(name = "amount_paid") val amountPaid: Double,
    @ColumnInfo(name = "purchased_at") val purchasedAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "expires_at") val expiresAt: Long? = null,
    @ColumnInfo(name = "last_redeemed_at") val lastRedeemedAt: Long? = null,
)
