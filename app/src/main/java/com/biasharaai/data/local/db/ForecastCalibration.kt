package com.biasharaai.data.local.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Tracks one demand-forecast window per product so [ForecastCalibrationResolver]
 * can compare predicted vs actual quantities and compute a bias ratio.
 *
 * [biasRatio] = actual_avg / predicted_avg:
 *   < 1.0  → model over-predicted  (scale future forecasts down)
 *   > 1.0  → model under-predicted (scale future forecasts up)
 *   null   → window not yet resolved
 */
@Entity(
    tableName = "forecast_calibrations",
    indices = [
        Index(value = ["product_id"]),
        Index(value = ["window_start_millis"]),
    ],
)
data class ForecastCalibration(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "product_id") val productId: Long,
    @ColumnInfo(name = "product_name") val productName: String,
    /** Epoch ms of the first day being forecast (day 1 start). */
    @ColumnInfo(name = "window_start_millis") val windowStartMillis: Long,
    @ColumnInfo(name = "predicted_day1") val predictedDay1: Int,
    @ColumnInfo(name = "predicted_day2") val predictedDay2: Int,
    @ColumnInfo(name = "predicted_day3") val predictedDay3: Int,
    @ColumnInfo(name = "actual_day1") val actualDay1: Int? = null,
    @ColumnInfo(name = "actual_day2") val actualDay2: Int? = null,
    @ColumnInfo(name = "actual_day3") val actualDay3: Int? = null,
    @ColumnInfo(name = "bias_ratio") val biasRatio: Float? = null,
    @ColumnInfo(name = "recorded_at") val recordedAt: Long = System.currentTimeMillis(),
)
