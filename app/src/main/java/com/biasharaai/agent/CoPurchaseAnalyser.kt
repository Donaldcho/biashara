package com.biasharaai.agent

import com.biasharaai.data.local.db.CoPurchasePair
import com.biasharaai.data.local.db.SaleLineItemDao
import javax.inject.Inject
import javax.inject.Singleton

/** A7 — Surfaces repeat basket-mates for [OpportunitySpotterWorker] (Room-backed SQL). */
@Singleton
class CoPurchaseAnalyser @Inject constructor(
    private val saleLineItemDao: SaleLineItemDao,
) {

    suspend fun topPairsSince(sinceMillis: Long, minCoCount: Int = 3): List<CoPurchasePair> =
        saleLineItemDao.getTopCoPurchasePairs(sinceMillis, minCoCount)
}
