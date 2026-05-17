package com.biasharaai.pos

import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.Customer
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repeat-purchase suggestions from Room (all tiers) + optional Gemma one-liner (**FULL_AI**).
 */
@Singleton
class CustomerSuggestionEngine @Inject constructor(
    private val saleLineItemDao: SaleLineItemDao,
    private val productDao: ProductDao,
    private val capabilityTier: CapabilityTier,
    private val gemmaService: GemmaService,
) {

    suspend fun topProductsForCustomer(customerId: Long, limit: Int = 3): List<Product> =
        withContext(Dispatchers.IO) {
            val ids = saleLineItemDao.topProductIdsForCustomer(customerId, limit)
            ids.mapNotNull { productDao.getProductByIdOnce(it) }
        }

    /**
     * One sentence for under suggestion chips — **FULL_AI** + model only.
     */
    suspend fun gemmaRepeatPurchaseHint(customer: Customer, topProducts: List<Product>): String? =
        withContext(Dispatchers.IO) {
            if (capabilityTier != CapabilityTier.FULL_AI || !gemmaService.isAvailable) return@withContext null
            if (topProducts.isEmpty()) return@withContext null
            val language = Locale.getDefault().displayLanguage
            val days = daysSinceLastMeaningfulVisit(customer)
            val list = topProducts.joinToString { it.name }
            val prompt = """
                Customer ${customer.name} last visited $days days ago. Their most bought items are: $list.
                Write one sentence in $language noting any item they may need soon. Be specific and brief.
            """.trimIndent()
            runCatching { gemmaService.generateResponse(prompt).trim().takeIf { it.isNotBlank() } }.getOrNull()
        }

    private fun daysSinceLastMeaningfulVisit(customer: Customer): Long {
        val ref = when {
            customer.lastVisit > 0L -> customer.lastVisit
            customer.createdAt > 0L -> customer.createdAt
            else -> System.currentTimeMillis()
        }
        val diff = (System.currentTimeMillis() - ref).coerceAtLeast(0L)
        return TimeUnit.MILLISECONDS.toDays(diff).coerceAtLeast(0L)
    }
}
