package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.skills.BiasharaSkill
import com.biasharaai.skills.SkillArgsParser
import com.biasharaai.skills.SkillResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * X6 — Applies owner-approved field updates (product price / cost / stock for now).
 * Agent loop must obtain approval before calling with mutating args.
 */
@Singleton
class UpdateDataSkill @Inject constructor(
    private val productDao: ProductDao,
) : BiasharaSkill {
    override val id: String = ID
    override val displayName: String = "Update data"
    override val parameterSchemaJson: String =
        """{"type":"object","properties":{"entity":{"type":"string"},"entityId":{"type":"integer"},"price":{"type":"number"},"cost":{"type":"number"},"stockQuantity":{"type":"integer"}},"required":["entity","entityId"]}"""

    override suspend fun execute(argumentsJson: String): SkillResult = withContext(Dispatchers.IO) {
        val args = SkillArgsParser.parseObject(argumentsJson).getOrElse {
            return@withContext SkillResult.Failure("INVALID_ARGS", it.message ?: "Invalid JSON")
        }
        val entity = SkillArgsParser.stringArg(args, "entity")?.lowercase()
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "entity is required")
        val entityId = SkillArgsParser.longArg(args, "entityId")
            ?: return@withContext SkillResult.Failure("INVALID_ARGS", "entityId is required")

        when (entity) {
            "product" -> updateProduct(entityId, args)
            else -> SkillResult.Failure("UNSUPPORTED_ENTITY", "Unsupported entity: $entity")
        }
    }

    private suspend fun updateProduct(productId: Long, args: Map<String, Any?>): SkillResult {
        val product = productDao.getProductByIdOnce(productId)
            ?: return SkillResult.Failure("NOT_FOUND", "Product $productId not found")

        val newPrice = SkillArgsParser.doubleArg(args, "price")
        val newCost = SkillArgsParser.doubleArg(args, "cost")
        val newStock = SkillArgsParser.longArg(args, "stockQuantity")?.toInt()

        if (newPrice == null && newCost == null && newStock == null) {
            return SkillResult.Failure("INVALID_ARGS", "Provide at least one of: price, cost, stockQuantity")
        }

        if (newPrice != null && newPrice < 0) {
            return SkillResult.Failure("INVALID_ARGS", "price must be non-negative")
        }
        if (newCost != null && newCost < 0) {
            return SkillResult.Failure("INVALID_ARGS", "cost must be non-negative")
        }
        if (newStock != null && newStock < 0) {
            return SkillResult.Failure("INVALID_ARGS", "stockQuantity must be non-negative")
        }

        val updated = product.copy(
            price = newPrice ?: product.price,
            cost = newCost ?: product.cost,
            stockQuantity = newStock ?: product.stockQuantity,
        )
        productDao.updateProduct(updated)

        return SkillResult.successMap(
            mapOf(
                "entity" to "product",
                "entityId" to productId,
                "applied" to buildMap {
                    newPrice?.let { put("price", it) }
                    newCost?.let { put("cost", it) }
                    newStock?.let { put("stockQuantity", it) }
                },
            ),
            summary = "Updated product ${product.name}",
        )
    }

    companion object {
        const val ID = "update_data"
    }
}
