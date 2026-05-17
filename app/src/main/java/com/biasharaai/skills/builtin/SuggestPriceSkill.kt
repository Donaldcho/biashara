package com.biasharaai.skills.builtin

import com.biasharaai.ai.PricingAdvisor
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X6 — Selling price suggestion via [PricingAdvisor] (Gemma or rules). */
@Singleton
class SuggestPriceSkill @Inject constructor(
    private val productDao: ProductDao,
    private val pricingAdvisor: PricingAdvisor,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Suggest price"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"productId":{"type":"integer"}},"required":["productId"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val productId = SkillArgsParser.longArg(args, "productId")
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "productId is required")

        val product = productDao.getProductByIdOnce(productId)
            ?: return@withContext SkillResult.Failure("NOT_FOUND", "Product $productId not found")

        val category = product.category?.trim().orEmpty()
        if (category.isEmpty()) {
            return@withContext SkillResult.Failure(
                "INVALID_PRODUCT",
                "Product must have a category before suggesting a price.",
            )
        }

        val response = pricingAdvisor.suggestPrice(product)
        val numeric = PricingAdvisor.parseSuggestedNumericPrice(response)

        SkillResult.successMap(
            mapOf(
                "productId" to productId,
                "suggestedPrice" to numeric,
                "fullText" to response,
                "rationale" to PricingAdvisor.rationaleText(response),
            ),
            summary = numeric?.let { "Suggested price: $it" } ?: response.take(80),
        )
    }

    companion object {
        const val ID = "suggest_price"
    }
}
