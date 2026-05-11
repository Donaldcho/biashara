package com.biasharaai.ai

import android.util.Log
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
 */
@Singleton
class DemandForecaster @Inject constructor(
    private val gemmaService: GemmaService,
) {
    companion object {
        private const val TAG = "DemandForecaster"

        /** Minimum number of daily data points needed for a meaningful forecast. */
        const val MIN_DATA_POINTS = 7
    }

    /**
     * Predict demand for the next 3 days.
     *
     * @param productName Display name of the product.
     * @param salesHistory Daily sales counts (most-recent last), e.g. [5, 3, 7, 4, 6, 8, 5].
     * @return A human-readable forecast string, e.g. "Day 1: 6, Day 2: 5, Day 3: 7"
     *         or a rules-based fallback when AI is unavailable.
     */
    suspend fun predictDemand(
        productName: String,
        salesHistory: List<Int>,
    ): String {
        if (salesHistory.size < MIN_DATA_POINTS) {
            return ""  // Not enough data
        }

        return if (gemmaService.isAvailable) {
            predictWithAi(productName, salesHistory)
        } else {
            predictWithRules(salesHistory)
        }
    }

    /**
     * AI-powered prediction using the Gemma model.
     */
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
        // LiteRT-LM applies the chat template internally; pass plain text only.
        try {
            val raw = gemmaService.generateResponse(innerPrompt)
            Log.d(TAG, "AI forecast for '$productName': $raw")
            parseForecastResponse(raw)
        } catch (e: Exception) {
            Log.w(TAG, "AI forecast failed, falling back to rules", e)
            predictWithRules(salesHistory)
        }
    }

    /**
     * Rules-based fallback: 3-day weighted moving average.
     *
     * Weights the most recent days more heavily for a simple trend estimate.
     */
    private fun predictWithRules(salesHistory: List<Int>): String {
        val recent = salesHistory.takeLast(7)
        val avg = recent.average()

        // Simple trend: compare last 3 vs previous 3
        val lastThree = recent.takeLast(3).average()
        val prevThree = recent.dropLast(3).takeLast(3).average()
        val trend = if (prevThree > 0) (lastThree - prevThree) / prevThree else 0.0

        val day1 = (avg * (1 + trend * 0.3)).coerceAtLeast(0.0).toInt()
        val day2 = (avg * (1 + trend * 0.5)).coerceAtLeast(0.0).toInt()
        val day3 = (avg * (1 + trend * 0.7)).coerceAtLeast(0.0).toInt()

        return "Day1: $day1, Day2: $day2, Day3: $day3"
    }

    /**
     * Parse the model's raw text output into a standardised forecast string.
     *
     * Expected format from the model: "Day1: X, Day2: Y, Day3: Z"
     * but the model may add extra text around it.
     */
    private fun parseForecastResponse(raw: String): String {
        // Try to extract Day1/Day2/Day3 pattern from the response
        val regex = Regex("""Day\s*1\s*:\s*(\d+).*Day\s*2\s*:\s*(\d+).*Day\s*3\s*:\s*(\d+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(raw)

        return if (match != null) {
            val (d1, d2, d3) = match.destructured
            "Day1: $d1, Day2: $d2, Day3: $d3"
        } else {
            // If we can't parse, return the trimmed raw output (capped)
            raw.trim().take(60)
        }
    }
}
