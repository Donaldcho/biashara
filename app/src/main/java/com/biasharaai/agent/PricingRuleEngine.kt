package com.biasharaai.agent

import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

enum class PricingRuleKind {
    VELOCITY_SPIKE,
    DEAD_STOCK,
    MARGIN_ALERT,
}

data class PricingRuleHit(
    val product: Product,
    val kind: PricingRuleKind,
    /** Short facts for the owner + optional Gemma rationale prefix. */
    val factLine: String,
)

/**
 * Room-only rules for [PricingAgentWorker] (A5). At most one hit per product (priority: velocity,
 * then dead stock, then margin).
 */
@Singleton
class PricingRuleEngine @Inject constructor(
    private val productDao: ProductDao,
) {

    suspend fun detectHits(nowMillis: Long): List<PricingRuleHit> {
        val products = productDao.getProductsList()
        val msDay = 24L * 60 * 60 * 1000
        val endExclusive = nowMillis + 1
        val out = ArrayList<PricingRuleHit>(products.size)
        for (p in products) {
            val units24h = productDao.sumUnitsSoldForProductInPeriod(
                p.id,
                nowMillis - msDay,
                endExclusive,
            )
            val units7d = productDao.sumUnitsSoldForProductInPeriod(
                p.id,
                nowMillis - 7 * msDay,
                endExclusive,
            )
            val avgDaily = units7d / 7.0
            if (avgDaily > 0 && units24h.toDouble() > 2 * avgDaily) {
                out.add(
                    PricingRuleHit(
                        product = p,
                        kind = PricingRuleKind.VELOCITY_SPIKE,
                        factLine = "Sold $units24h units in 24h vs ~${String.format(Locale.US, "%.1f", avgDaily)} units/day average over the last 7 days (more than 2× pace).",
                    ),
                )
                continue
            }
            val units5d = productDao.sumUnitsSoldForProductInPeriod(
                p.id,
                nowMillis - 5 * msDay,
                endExclusive,
            )
            if (p.stockQuantity > 0 && units5d == 0L) {
                out.add(
                    PricingRuleHit(
                        product = p,
                        kind = PricingRuleKind.DEAD_STOCK,
                        factLine = "No sales in the last 5 days while stock is ${p.stockQuantity} units.",
                    ),
                )
                continue
            }
            if (p.price < p.cost * 1.1) {
                out.add(
                    PricingRuleHit(
                        product = p,
                        kind = PricingRuleKind.MARGIN_ALERT,
                        factLine = "List price ${p.price} is under 110% of cost (${p.cost}) — margin under ~10%.",
                    ),
                )
            }
        }
        return out
    }
}
