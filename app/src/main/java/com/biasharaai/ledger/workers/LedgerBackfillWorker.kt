package com.biasharaai.ledger.workers

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.ledger.LedgerBackfillRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Phase 9 L3 — idempotent historical ledger replay. */
class LedgerBackfillWorker(
    appContext: Context,
    params: WorkerParameters,
    private val backfillRunner: LedgerBackfillRunner,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            backfillRunner.runIfEmpty()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { Result.retry() },
        )
    }

    companion object {
        const val UNIQUE_WORK = "ledger_backfill"
    }
}
