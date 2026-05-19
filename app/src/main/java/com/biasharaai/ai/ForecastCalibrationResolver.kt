package com.biasharaai.ai

import android.util.Log
import com.biasharaai.data.local.db.ForecastCalibrationDao
import com.biasharaai.data.local.db.SaleLineItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Closes out expired [com.biasharaai.data.local.db.ForecastCalibration] rows by comparing
 * the original 3-day predictions against actual POS sales quantities, then writing a
 * [biasRatio][com.biasharaai.data.local.db.ForecastCalibration.biasRatio] = actual_avg / predicted_avg.
 *
 * Called weekly from [com.biasharaai.agent.workers.WeeklyReviewWorker] before building
 * the next forecast so [DemandForecaster] always sees up-to-date calibration data.
 *
 * Bias interpretation:
 *   < 1.0 â†’ model consistently over-predicted â†’ scale future forecasts down
 *   > 1.0 â†’ model consistently under-predicted â†’ scale future forecasts up
 *   = 1.0 â†’ perfect calibration
 */
@Singleton
class ForecastCalibrationResolver @Inject constructor(
    private val calibrationDao: ForecastCalibrationDao,
    private val saleLineItemDao: SaleLineItemDao,
) {
    companion object {
        private const val TAG = "ForecastCalibration"
        private val DAY_MS = TimeUnit.DAYS.toMillis(1)
        private val PRUNE_AGE_MS = TimeUnit.DAYS.toMillis(90)
    }

    /**
     * Resolve all calibration windows that started more than 3 days ago and
     * have not yet been closed. Safe to call repeatedly â€” already-resolved
     * rows are not touched.
     */
    suspend fun resolveExpired() = withContext(Dispatchers.IO) {
        val cutoff = System.currentTimeMillis() - 3 * DAY_MS
        val unresolved = calibrationDao.getUnresolved(cutoff)
        if (unresolved.isEmpty()) return@withContext

        Log.d(TAG, "Resolving ${unresolved.size} expired forecast calibration(s)")

        for (cal in unresolved) {
            val d1Start = cal.windowStartMillis
            val d2Start = d1Start + DAY_MS
            val d3Start = d2Start + DAY_MS
            val d3End = d3Start + DAY_MS

            val a1 = saleLineItemDao.soldQuantityForProductBetween(cal.productId, d1Start, d2Start)
            val a2 = saleLineItemDao.soldQuantityForProductBetween(cal.productId, d2Start, d3Start)
            val a3 = saleLineItemDao.soldQuantityForProductBetween(cal.productId, d3Start, d3End)

            val predictedAvg = (cal.predictedDay1 + cal.predictedDay2 + cal.predictedDay3) / 3f
            val actualAvg = (a1 + a2 + a3) / 3f
            val ratio = if (predictedAvg > 0f) (actualAvg / predictedAvg).coerceIn(0.1f, 10f) else 1f

            calibrationDao.resolveCalibration(cal.id, a1, a2, a3, ratio)
            Log.d(TAG, "Resolved product=${cal.productId} predictedâ‰ˆ${predictedAvg.toInt()} actualâ‰ˆ${actualAvg.toInt()} bias=${"%.2f".format(ratio)}")
        }

        // Keep table bounded â€” prune rows older than 90 days
        calibrationDao.pruneOlderThan(System.currentTimeMillis() - PRUNE_AGE_MS)
    }
}

