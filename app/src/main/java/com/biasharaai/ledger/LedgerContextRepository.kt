package com.biasharaai.ledger

import com.biasharaai.data.local.db.LedgerContext
import com.biasharaai.data.local.db.LedgerContextDao
import com.biasharaai.data.local.db.LedgerContextSource
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LedgerContextRepository @Inject constructor(
    private val ledgerContextDao: LedgerContextDao,
) {

    suspend fun recordOwnerAnswer(
        relatedAnomalyId: String,
        contextType: String,
        prompt: String,
        ownerAnswer: String,
        appliesFromMillis: Long? = null,
        appliesToMillis: Long? = null,
    ): Long {
        val now = System.currentTimeMillis()
        ledgerContextDao.supersedeOwnerConfirmedForAnomaly(relatedAnomalyId, now)
        return ledgerContextDao.insert(
            LedgerContext(
                relatedAnomalyId = relatedAnomalyId,
                contextType = contextType,
                prompt = prompt,
                ownerAnswer = ownerAnswer,
                source = LedgerContextSource.OWNER_CONFIRMED.name,
                confidence = 1.0,
                appliesFromMillis = appliesFromMillis,
                appliesToMillis = appliesToMillis,
                createdAtMillis = now,
                resolvedAtMillis = now,
            ),
        )
    }

    suspend fun recordAgentInference(
        relatedAnomalyId: String,
        contextType: String,
        prompt: String,
        inference: String,
        confidence: Double? = null,
        appliesFromMillis: Long? = null,
        appliesToMillis: Long? = null,
    ): Long? {
        val existing = ledgerContextDao.getActiveForAnomaly(relatedAnomalyId)
        if (existing.any { it.source == LedgerContextSource.OWNER_CONFIRMED.name }) {
            return null
        }
        val now = System.currentTimeMillis()
        ledgerContextDao.supersedeAgentInferencesForAnomaly(relatedAnomalyId, now)
        return ledgerContextDao.insert(
            LedgerContext(
                relatedAnomalyId = relatedAnomalyId,
                contextType = contextType,
                prompt = prompt,
                ownerAnswer = inference,
                source = LedgerContextSource.AGENT_INFERRED.name,
                confidence = confidence,
                appliesFromMillis = appliesFromMillis,
                appliesToMillis = appliesToMillis,
                createdAtMillis = now,
            ),
        )
    }

    suspend fun getActiveForPeriod(fromMillis: Long, toMillis: Long): List<LedgerContext> =
        ledgerContextDao.getActiveForPeriod(fromMillis, toMillis)
}
