package com.biasharaai.ledger.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.ledger.LedgerBalanceRecomputer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 9 L9 — nightly running-balance integrity pass. */
class LedgerBalanceRecomputeWorker(
    appContext: Context,
    params: WorkerParameters,
    private val balanceRecomputer: LedgerBalanceRecomputer,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching { balanceRecomputer.recomputeAll() }
            .fold(
                onSuccess = { Result.success() },
                onFailure = { Result.retry() },
            )
    }

    companion object {
        const val UNIQUE_WORK = "ledger_balance_recompute"
    }
}
