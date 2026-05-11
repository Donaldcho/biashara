package com.biasharaai.ai

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs

/**
 * Checks device hardware capabilities for on-device LLM inference.
 *
 * Evaluates RAM, API level, and available storage to determine
 * what AI tier the device can support.
 */
object DeviceCapabilityChecker {

    /** Minimum API level for reliable on-device LLM inference. */
    private const val MIN_API_LEVEL = 28

    /** Minimum free internal storage (bytes) for model download — 3.5 GB. */
    private const val MIN_FREE_STORAGE_BYTES = 3_500_000_000L

    /** RAM threshold for full AI (4 GB in bytes). */
    private const val FULL_AI_RAM_BYTES = 4L * 1024 * 1024 * 1024

    /** RAM threshold for partial AI (3 GB in bytes). */
    private const val PARTIAL_AI_RAM_BYTES = 3L * 1024 * 1024 * 1024

    /**
     * Evaluate the device and return the appropriate [CapabilityTier].
     *
     * @param modelPresentOnDisk When true, free-space is not required to *use* AI tiers — the
     * model is already installed (user may have less than [MIN_FREE_STORAGE_BYTES] free afterward).
     * Free space is still checked for [meetsStorageRequirement] so the UI can warn about downloads.
     */
    fun evaluate(context: Context, modelPresentOnDisk: Boolean = false): CapabilityResult {
        val apiLevel = Build.VERSION.SDK_INT
        val totalRam = getTotalRam(context)
        val freeStorage = getFreeInternalStorage()

        val apiOk = apiLevel >= MIN_API_LEVEL
        val storageOk = freeStorage >= MIN_FREE_STORAGE_BYTES
        val eligibleForAiTier = storageOk || modelPresentOnDisk

        val tier = when {
            !apiOk -> CapabilityTier.RULES_BASED
            totalRam >= FULL_AI_RAM_BYTES && eligibleForAiTier -> CapabilityTier.FULL_AI
            totalRam >= PARTIAL_AI_RAM_BYTES && eligibleForAiTier -> CapabilityTier.PARTIAL_AI
            else -> CapabilityTier.RULES_BASED
        }

        return CapabilityResult(
            tier = tier,
            apiLevel = apiLevel,
            totalRamMb = totalRam / (1024 * 1024),
            freeStorageMb = freeStorage / (1024 * 1024),
            meetsApiRequirement = apiOk,
            meetsStorageRequirement = storageOk,
        )
    }

    /** Total device RAM in bytes. */
    private fun getTotalRam(context: Context): Long {
        val activityManager =
            context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        return memInfo.totalMem
    }

    /** Free bytes on internal storage. */
    private fun getFreeInternalStorage(): Long {
        val stat = StatFs(Environment.getDataDirectory().path)
        return stat.availableBlocksLong * stat.blockSizeLong
    }
}

/**
 * AI capability tier determining what features are available.
 */
enum class CapabilityTier {
    /** 4GB+ RAM — full on-device LLM inference. */
    FULL_AI,

    /** 3GB RAM — on-device LLM with reduced context window. */
    PARTIAL_AI,

    /** Below threshold — fall back to rules-based logic only. */
    RULES_BASED,
}

/**
 * Detailed device capability evaluation result.
 */
data class CapabilityResult(
    val tier: CapabilityTier,
    val apiLevel: Int,
    val totalRamMb: Long,
    val freeStorageMb: Long,
    val meetsApiRequirement: Boolean,
    val meetsStorageRequirement: Boolean,
)
