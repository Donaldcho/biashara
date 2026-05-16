package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** X5 — Product list with optional low-stock filter. */
@Singleton
class QueryInventorySkill @Inject constructor(
    private val productDao: ProductDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Query inventory"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"lowStockOnly":{"type":"boolean"}}}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val lowStockOnly = SkillArgsParser.boolArg(args, "lowStockOnly", default = false)
        val threshold = DEFAULT_LOW_STOCK_THRESHOLD

        val products = if (lowStockOnly) {
            productDao.getLowStockProducts(threshold, MAX_ROWS)
        } else {
            productDao.getProductsList().take(MAX_ROWS)
        }

        val rows = products.map { p ->
            mapOf(
                "id" to p.id,
                "name" to p.name,
                "stockQuantity" to p.stockQuantity,
                "price" to p.price,
                "cost" to p.cost,
                "category" to p.category,
            )
        }

        SkillResult.successMap(
            mapOf(
                "lowStockOnly" to lowStockOnly,
                "lowStockThreshold" to if (lowStockOnly) threshold else null,
                "count" to rows.size,
                "products" to rows,
            ),
            summary = if (lowStockOnly) {
                "${rows.size} product(s) below $threshold units"
            } else {
                "${rows.size} product(s) in inventory"
            },
        )
    }

    companion object {
        const val ID = "query_inventory"
        const val DEFAULT_LOW_STOCK_THRESHOLD = 5
        private const val MAX_ROWS = 100
    }
}
