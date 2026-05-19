package com.biasharaai.data.local.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ForecastCalibrationDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(calibration: ForecastCalibration): Long

    /**
     * Unresolved forecasts whose 3-day window has already passed.
     * Caller supplies [beforeMillis] = now − 3 days.
     */
    @Query(
        """
        SELECT * FROM forecast_calibrations
        WHERE bias_ratio IS NULL AND window_start_millis < :beforeMillis
        ORDER BY window_start_millis ASC
        """,
    )
    suspend fun getUnresolved(beforeMillis: Long): List<ForecastCalibration>

    /** Stamp actuals and the computed bias ratio onto a resolved calibration row. */
    @Query(
        """
        UPDATE forecast_calibrations
        SET actual_day1 = :a1, actual_day2 = :a2, actual_day3 = :a3, bias_ratio = :ratio
        WHERE id = :id
        """,
    )
    suspend fun resolveCalibration(id: Long, a1: Int, a2: Int, a3: Int, ratio: Float)

    /**
     * Rolling average bias ratio for a product across its last [limit] resolved windows.
     * Returns null if no resolved calibrations exist yet.
     */
    @Query(
        """
        SELECT AVG(bias_ratio) FROM (
            SELECT bias_ratio FROM forecast_calibrations
            WHERE product_id = :productId AND bias_ratio IS NOT NULL
            ORDER BY recorded_at DESC
            LIMIT :limit
        )
        """,
    )
    suspend fun avgBiasRatio(productId: Long, limit: Int = 10): Float?

    /** Prune calibrations older than [beforeMillis] to keep the table bounded. */
    @Query("DELETE FROM forecast_calibrations WHERE recorded_at < :beforeMillis")
    suspend fun pruneOlderThan(beforeMillis: Long)
}
