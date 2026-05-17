package com.biasharaai.cash

import android.util.Log
import com.biasharaai.data.local.db.CashMovementEvidenceDao
import com.biasharaai.data.local.db.LedgerEntryDao
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class CashAnomalySignal(
    val rule: String,
    val detail: String,
    val severity: Severity,
) {
    enum class Severity { LOW, MEDIUM, HIGH }
}

@Singleton
class CashEvidenceAnomalyDetector @Inject constructor(
    private val evidenceDao: CashMovementEvidenceDao,
    private val ledgerEntryDao: LedgerEntryDao,
) {

    suspend fun detectAll(): List<CashAnomalySignal> {
        val signals = mutableListOf<CashAnomalySignal>()
        val since30d = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)

        signals += checkLargeUnverifiedOutflows(since30d)
        signals += checkConsecutiveUnverifiedOutflows(since30d)

        return signals
    }

    private suspend fun checkLargeUnverifiedOutflows(since: Long): List<CashAnomalySignal> {
        val large = evidenceDao.getLargeUnverifiedOutflows(since, threshold = LARGE_OUTFLOW_THRESHOLD)
        return large.map { ev ->
            CashAnomalySignal(
                rule = "LARGE_UNVERIFIED_OUTFLOW",
                detail = "Unverified cash-out of ${ev.parsedAmount} with no transaction reference (evidenceId=${ev.id})",
                severity = CashAnomalySignal.Severity.HIGH,
            )
        }
    }

    private suspend fun checkConsecutiveUnverifiedOutflows(since: Long): List<CashAnomalySignal> {
        val count = evidenceDao.countUnverifiedOutflowsSince(since)
        if (count < CONSECUTIVE_UNVERIFIED_THRESHOLD) return emptyList()
        return listOf(
            CashAnomalySignal(
                rule = "REPEATED_UNVERIFIED_OUTFLOWS",
                detail = "$count unverified MONEY_OUT entries in the last 30 days with no reference",
                severity = CashAnomalySignal.Severity.MEDIUM,
            ),
        )
    }

    companion object {
        private const val TAG = "CashEvidenceAnomalyDetector"
        private const val LARGE_OUTFLOW_THRESHOLD = 5_000.0
        private const val CONSECUTIVE_UNVERIFIED_THRESHOLD = 3
    }
}
