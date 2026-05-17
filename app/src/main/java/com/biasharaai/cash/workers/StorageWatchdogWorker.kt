package com.biasharaai.cash.workers

import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.data.local.db.CashMovementEvidenceDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Phase C — Prompt C0: Runs once per day and enforces thumbnail storage limits.
 *
 * Rules (in order):
 * 1. If device free storage < 100 MB → clear ALL thumbnails immediately.
 * 2. If total thumbnail bytes > 10 MB → keep the 150 most-recent, delete the rest.
 */
class StorageWatchdogWorker(
    appContext: Context,
    params: WorkerParameters,
    private val evidenceDao: CashMovementEvidenceDao,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        runCatching {
            enforceThumbnailStorageCap()
        }.fold(
            onSuccess = { Result.success() },
            onFailure = { e ->
                Log.w(TAG, "StorageWatchdogWorker failed", e)
                Result.retry()
            },
        )
    }

    private suspend fun enforceThumbnailStorageCap() {
        val freeBytes = runCatching {
            StatFs(Environment.getDataDirectory().path).availableBytes
        }.getOrDefault(Long.MAX_VALUE)

        if (freeBytes < FREE_STORAGE_THRESHOLD_BYTES) {
            Log.i(TAG, "Storage low (${freeBytes / MB}MB free) — clearing all thumbnails")
            evidenceDao.clearAllThumbnails()
            return
        }

        val totalBytes = evidenceDao.getTotalThumbnailBytes()
        if (totalBytes > THUMBNAIL_CAP_BYTES) {
            Log.i(TAG, "Thumbnail cap exceeded (${totalBytes / MB}MB) — trimming to $KEEP_COUNT")
            evidenceDao.deleteOldestThumbnails(keepCount = KEEP_COUNT)
        }
    }

    companion object {
        private const val TAG = "StorageWatchdogWorker"
        const val UNIQUE_WORK = "storage_watchdog"

        private const val MB = 1_024L * 1_024L
        private const val FREE_STORAGE_THRESHOLD_BYTES = 100L * MB
        private const val THUMBNAIL_CAP_BYTES = 10L * MB
        private const val KEEP_COUNT = 150
    }
}
