package com.biasharaai.ai

import android.util.Log
import com.biasharaai.data.local.db.ForecastCalibration
import com.biasharaai.data.local.db.ForecastCalibrationDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Demand forecasting service powered by the on-device Gemma model.
 *
 * Takes a product name and its recent sales history (daily sales counts)
 * and asks the model to predict the next 3 days of demand.
 *
 * When the [GemmaService] is not available (model not downloaded or
 * device below capability threshold), a simple rules-based fallback
 * is used instead (moving average).
 *
 * **Bias calibration (Step 2):** After each forecast the raw prediction is
 * stored in [ForecastCalibrationDao]. [ForecastCalibrationResolver] later
 * compares predictions to actual sales and writes a [biasRatio]. Before
 * the next forecast for the same product, the running average bias ratio
 * is applied to correct systematic over/under-prediction.
 */
@Singleton
class DemandForecaster @Inject constructor(
    private val gemmaService: GemmaService,
    private val calibrationDao: ForecastCalibrationDao,
) {
    companion object {
        private const val TAG = "DemandForecaster"

        /** Minimum number of daily data points needed for a meaningful forecast. */
        const val MIN_DATA_POINTS = 7

        /** Clamp bias correction to ±50% to avoid wild swings early in calibration. */
        private const val BIAS_MIN = 0.5f
        private const val BIAS_MAX = 1.5f
    }

    /**
     * Predict demand for the next 3 days.
     *
     * @param productId  Stable Room product ID — used to load/save calibration data.
     * @param productName Display name of the product.
     * @param salesHistory Daily sales counts (most-recent last), e.g. [5, 3, 7, 4, 6, 8, 5].
     * @return A human-readable forecast string, e.g. "Day 1: 6, Day 2: 5, Day 3: 7"
     *         or a rules-based fallback when AI is unavailable.
     */
    suspend fun predictDemand(
        productId: Long,
        productName: String,
        salesHistory: List<Int>,
    ): String {
        if (salesHistory.size < MIN_DATA_POINTS) {
            return ""
        }

        val rawForecast = if (gemmaService.isAvailable) {
            predictWithAi(productName, salesHistory)
        } else {
            predictWithRules(salesHistory)
        }

        if (rawForecast.isBlank()) return rawForecast

        val (d1, d2, d3) = parseToDayTriple(rawForecast)
        val biasRatio = loadBiasRatio(productId)
        val (cd1, cd2, cd3) = applyBias(d1, d2, d3, biasRatio)

        // Save this forecast window so it can be resolved against actuals in ~3 days
        val windowStart = System.currentTimeMillis()
        calibrationDao.insert(
            ForecastCalibration(
                productId = productId,
                productName = productName,
                windowStartMillis = windowStart,
                predictedDay1 = cd1,
                predictedDay2 = cd2,
                predictedDay3 = cd3,
            ),
        )

        val biasNote = if (biasRatio != null && biasRatio != 1f) {
            " (bias-corrected ×${"%.2f".format(biasRatio)})"
        } else ""
        return "Day1: $cd1, Day2: $cd2, Day3: $cd3$biasNote"
    }

    // ── private ───────────────────────────────────────────────────────────────

    private suspend fun loadBiasRatio(productId: Long): Float? =
        calibrationDao.avgBiasRatio(productId)?.takeIf { !it.isNaN() }
            ?.coerceIn(BIAS_MIN, BIAS_MAX)

    private fun applyBias(
        d1: Int, d2: Int, d3: Int,
        bias: Float?,
    ): Triple<Int, Int, Int> {
        if (bias == null || bias == 1f) return Triple(d1, d2, d3)
        return Triple(
            (d1 * bias).toInt().coerceAtLeast(0),
            (d2 * bias).toInt().coerceAtLeast(0),
            (d3 * bias).toInt().coerceAtLeast(0),
        )
    }

    private fun parseToDayTriple(forecast: String): Triple<Int, Int, Int> {
        val regex = Regex("""Day\s*1\s*:\s*(\d+).*?Day\s*2\s*:\s*(\d+).*?Day\s*3\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
        val m = regex.find(forecast)
        return if (m != null) {
            val (a, b, c) = m.destructured
            Triple(a.toIntOrNull() ?: 0, b.toIntOrNull() ?: 0, c.toIntOrNull() ?: 0)
        } else {
            Triple(0, 0, 0)
        }
    }

    private suspend fun predictWithAi(
        productName: String,
        salesHistory: List<Int>,
    ): String = withContext(Dispatchers.IO) {
        val historyStr = salesHistory.joinToString(", ")
        val innerPrompt = buildString {
            append("You are a retail demand forecasting assistant. ")
            append("Sales for \"$productName\" over the last ${salesHistory.size} days: ")
            append("[$historyStr]. ")
            append("Predict the next 3 days demand. ")
            append("Reply ONLY in the format: Day1: X, Day2: Y, Day3: Z. ")
            append("Disable thinking and speculative decoding.")
        }
        try {
            val raw = gemmaService.generateResponse(innerPrompt)
            Log.d(TAG, "AI forecast for '$productName': $raw")
            parseForecastResponse(raw)
        } catch (e: Exception) {
            Log.w(TAG, "AI forecast failed, falling back to rules", e)
            predictWithRules(salesHistory)
        }
    }

    private fun predictWithRules(salesHistory: List<Int>): String {
        val recent = salesHistory.takeLast(7)
        val avg = recent.average()
        val lastThree = recent.takeLast(3).average()
        val prevThree = recent.dropLast(3).takeLast(3).average()
        val trend = if (prevThree > 0) (lastThree - prevThree) / prevThree else 0.0
        val day1 = (avg * (1 + trend * 0.3)).coerceAtLeast(0.0).toInt()
        val day2 = (avg * (1 + trend * 0.5)).coerceAtLeast(0.0).toInt()
        val day3 = (avg * (1 + trend * 0.7)).coerceAtLeast(0.0).toInt()
        return "Day1: $day1, Day2: $day2, Day3: $day3"
    }

    private fun parseForecastResponse(raw: String): String {
        val regex = Regex("""Day\s*1\s*:\s*(\d+).*Day\s*2\s*:\s*(\d+).*Day\s*3\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)
        return if (match != null) {
            val (d1, d2, d3) = match.destructured
            "Day1: $d1, Day2: $d2, Day3: $d3"
        } else {
            raw.trim().take(60)
        }
    }
}
