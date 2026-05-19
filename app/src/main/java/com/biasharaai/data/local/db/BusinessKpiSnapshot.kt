package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One row per completed ISO week — the persistent KPI baseline that enables
 * longitudinal trend comparisons ("third consecutive week services outpaced products").
 */
@Entity(
    tableName = "business_kpi_snapshots",
    indices = [Index(value = ["week_start_millis"], unique = true)],
)
data class BusinessKpiSnapshot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    /** Monday 00:00 local — deduplication key matching [WeeklyReviewBuilder.weekStartMillis]. */
    @ColumnInfo(name = "week_start_millis") val weekStartMillis: Long,
    @ColumnInfo(name = "week_revenue") val weekRevenue: Double,
    @ColumnInfo(name = "last_week_revenue") val lastWeekRevenue: Double,
    @ColumnInfo(name = "product_revenue") val productRevenue: Double,
    @ColumnInfo(name = "service_revenue") val serviceRevenue: Double,
    @ColumnInfo(name = "tx_count") val txCount: Long,
    @ColumnInfo(name = "new_customers") val newCustomers: Long,
    @ColumnInfo(name = "returning_customers") val returningCustomers: Long,
    @ColumnInfo(name = "top_product_name") val topProductName: String,
    @ColumnInfo(name = "top_product_revenue") val topProductRevenue: Double,
    @ColumnInfo(name = "top_service_name") val topServiceName: String? = null,
    @ColumnInfo(name = "service_sessions") val serviceSessions: Int = 0,
    @ColumnInfo(name = "best_day") val bestDay: String,
    @ColumnInfo(name = "best_hour") val bestHour: Int,
    @ColumnInfo(name = "credit_outstanding") val creditOutstanding: Double,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis(),
)
