package com.biasharaai.enterprise

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class EnterpriseSyncWorker(
    appContext: Context,
    params: WorkerParameters,
    private val enterpriseAuditRepository: EnterpriseAuditRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            enterpriseAuditRepository.flushPendingSync(limit = 100)
        }.fold(
            onSuccess = { result ->
                if (result.skippedReason != null) {
                    Log.d(TAG, "Enterprise sync skipped: ${result.skippedReason}")
                } else {
                    Log.d(TAG, "Enterprise sync flushed sent=${result.sent} failed=${result.failed}")
                }
                Result.success()
            },
            onFailure = { e ->
                Log.w(TAG, "Enterprise sync worker failed", e)
                Result.success()
            },
        )
    }

    companion object {
        private const val TAG = "EnterpriseSyncWorker"
        const val UNIQUE_PERIODIC_WORK = "enterprise_sync_periodic"
        const val UNIQUE_IMMEDIATE_WORK = "enterprise_sync_immediate"
    }
}
