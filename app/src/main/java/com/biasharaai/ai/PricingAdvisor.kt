package com.biasharaai.ai

import com.biasharaai.data.local.db.CategoryAverages
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device or rules-based selling price hint for the product form — Prompt U3.
 */
@Singleton
class PricingAdvisor @Inject constructor(
    private val gemmaService: GemmaService,
    private val productDao: ProductDao,
    private val capabilityTier: CapabilityTier,
) {

    /**
     * Returns the full suggestion text (either Gemma reply or rules string).
     * [product.category] must be non-blank; [product.cost] should reflect the form.
     */
    suspend fun suggestPrice(product: Product): String {
        val category = product.category?.trim().orEmpty()
        require(category.isNotEmpty()) { "Category is required for pricing suggestions" }

        if (capabilityTier == CapabilityTier.RULES_BASED) {
            return rulesSuggestedPriceLine(product.cost)
        }

        if (!gemmaService.isAvailable) {
            return rulesSuggestedPriceLine(product.cost)
        }

        val categoryAvg: CategoryAverages =
            productDao.getCategoryAverages(category, product.id).first()

        val (monthStart, monthEndExclusive) = currentMonthBoundsMillis()
        val monthlySales = productDao.sumUnitsSoldForProductInPeriod(
            product.id,
            monthStart,
            monthEndExclusive,
        ).toInt()

        val language = Locale.getDefault().displayLanguage
        val avgPrice = categoryAvg.avgPrice ?: 0.0
        val avgCost = categoryAvg.avgCost ?: 0.0

        val prompt = """
You are a pricing advisor for a small African business.
Respond in $language. Reply in under 80 words.
Product: ${product.name}. Category: $category.
Cost price: ${product.cost}. Current price: ${product.price}.
Category average price: $avgPrice.
Category average cost: $avgCost.
Units sold this month: $monthlySales.
Suggest one selling price. State the price clearly on the first line
as 'Suggested price: X'. Then explain why in 1-2 sentences.
        """.trimIndent()

        return runCatching { gemmaService.generateResponse(prompt).trim() }
            .getOrElse { rulesSuggestedPriceLine(product.cost) }
    }

    private fun rulesSuggestedPriceLine(cost: Double): String {
        val suggested = cost * 1.3
        return "Suggested price: ${"%.2f".format(Locale.US, suggested)} (30% margin)"
    }

    private fun currentMonthBoundsMillis(): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        val endExclusive = cal.timeInMillis
        return start to endExclusive
    }

    companion object {
        private val SUGGESTED_PRICE_REGEX =
            Regex("Suggested price:\\s*([\\d.]+)", RegexOption.IGNORE_CASE)

        /**
         * Parses the numeric price from the first line of [response], per Prompt U3.
         */
        fun parseSuggestedNumericPrice(response: String): Double? {
            val firstLine = response.lineSequence().firstOrNull().orEmpty()
            return SUGGESTED_PRICE_REGEX.find(firstLine)?.groupValues?.get(1)?.toDoubleOrNull()
        }

        fun rationaleText(response: String): String {
            val lines = response.lines()
            if (lines.size <= 1) return response.trim()
            return lines.drop(1).joinToString("\n").trim().ifBlank { response.trim() }
        }
    }
}
