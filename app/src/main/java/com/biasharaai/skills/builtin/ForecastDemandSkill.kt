package com.biasharaai.skills.builtin

import com.biasharaai.ai.DemandForecaster
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import com.biasharaai.skills.SkillSalesHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** X6 — Next 3-day demand forecast via [DemandForecaster] + real POS line history. */
@Singleton
class ForecastDemandSkill @Inject constructor(
    private val productDao: ProductDao,
    private val saleLineItemDao: SaleLineItemDao,
    private val demandForecaster: DemandForecaster,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Forecast demand"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"productId":{"type":"integer"},"historyDays":{"type":"integer","minimum":7,"maximum":90}},"required":["productId"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val productId = SkillArgsParser.longArg(args, "productId")
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "productId is required")
        val historyDays = SkillArgsParser.intArg(args, "historyDays", default = 14, min = 7, max = 90)

        val product = productDao.getProductByIdOnce(productId)
            ?: return@withContext SkillResult.Failure("NOT_FOUND", "Product $productId not found")

        val since = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(historyDays.toLong())
        val facts = saleLineItemDao.posSaleLineFactsSince(since)
            .filter { it.productId == productId }
        val history = SkillSalesHistory.dailyTotalsFromFacts(facts, historyDays)

        val nonZeroDays = history.count { it > 0 }
        if (nonZeroDays < DemandForecaster.MIN_DATA_POINTS) {
            return@withContext SkillResult.Failure(
                "INSUFFICIENT_DATA",
                "Need at least ${DemandForecaster.MIN_DATA_POINTS} days of sales; have $nonZeroDays non-zero day(s).",
            )
        }

        val forecast = demandForecaster.predictDemand(product.name, history)
        if (forecast.isBlank()) {
            return@withContext SkillResult.Failure("FORECAST_EMPTY", "Could not produce a forecast.")
        }

        SkillResult.successMap(
            mapOf(
                "productId" to productId,
                "productName" to product.name,
                "historyDays" to historyDays,
                "dailyHistory" to history,
                "forecast" to forecast,
            ),
            summary = forecast,
        )
    }

    companion object {
        const val ID = "forecast_demand"
    }
}
