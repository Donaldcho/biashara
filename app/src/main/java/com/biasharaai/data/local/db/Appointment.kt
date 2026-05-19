package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "appointments",
    indices = [
        Index(value = ["scheduled_at"]),
        Index(value = ["status"]),
        Index(value = ["customer_id"]),
    ],
)
data class Appointment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "customer_id") val customerId: Long? = null,
    @ColumnInfo(name = "customer_name") val customerName: String,
    @ColumnInfo(name = "service_item_id") val serviceItemId: Long,
    @ColumnInfo(name = "staff_member_id") val staffMemberId: Long? = null,
    @ColumnInfo(name = "scheduled_at") val scheduledAt: Long,
    @ColumnInfo(name = "duration_minutes") val durationMinutes: Int = 60,
    @ColumnInfo(defaultValue = "BOOKED") val status: String = STATUS_BOOKED,
    @ColumnInfo(name = "deposit_paid") val depositPaid: Double = 0.0,
    @ColumnInfo(name = "balance_due") val balanceDue: Double = 0.0,
    val notes: String? = null,
    @ColumnInfo(name = "transaction_id") val transactionId: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val STATUS_BOOKED = "BOOKED"
        const val STATUS_CONFIRMED = "CONFIRMED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
        const val STATUS_NO_SHOW = "NO_SHOW"
    }
}
