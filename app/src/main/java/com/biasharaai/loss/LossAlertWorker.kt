package com.biasharaai.loss

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biasharaai.ai.CapabilityTier
import com.biasharaai.ai.GemmaService
import com.biasharaai.data.local.db.AlertDao
import com.biasharaai.data.local.db.LossAlertEngine
import com.biasharaai.locale.LanguagePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId

class LossAlertWorker(
    appContext: Context,
    params: WorkerParameters,
    private val lossAlertEngine: LossAlertEngine,
    private val alertDao: AlertDao,
    private val gemmaService: GemmaService,
    private val capabilityTier: CapabilityTier,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val now = System.currentTimeMillis()
            val zone = ZoneId.systemDefault().rules.getOffset(Instant.ofEpochMilli(now))
            val candidates = lossAlertEngine.runAllDetections(nowMillis = now, zoneOffset = zone)
            val localeTag = LanguagePreferences.getPersistedLocaleTag(applicationContext) ?: "en"
            val languageName = languageDisplayNameForPrompt(localeTag)
            val translate =
                capabilityTier == CapabilityTier.FULL_AI &&
                    gemmaService.isAvailable &&
                    !localeTag.startsWith("en", ignoreCase = true)

            for (candidate in candidates) {
                val key = candidate.dedupeKey ?: continue
                if (alertDao.countActiveAlertsWithDedupeKey(key) > 0) continue

                val localized = if (translate) {
                    val prompt =
                        "Translate this alert to $languageName in plain, friendly language for a " +
                            "small business owner. Include a short headline on the first line, then " +
                            "one or two sentences of explanation. Reply with only the translated text, " +
                            "no quotes or preamble.\n\n" +
                            candidate.title + "\n" + candidate.message
                    try {
                        gemmaService.resetSession()
                        gemmaService.generateResponse(prompt).trim().take(800)
                            .ifBlank { null }
                    } catch (_: Exception) {
                        null
                    }
                } else {
                    null
                }

                val toInsert = candidate.copy(localizedMessage = localized)
                alertDao.insert(toInsert)
            }
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Loss alert scan failed", e)
            Result.success()
        }
    }

    private fun languageDisplayNameForPrompt(localeTag: String): String {
        val base = localeTag.substringBefore('-').lowercase()
        return when (base) {
            "sw" -> "Swahili"
            "ha" -> "Hausa"
            "yo" -> "Yoruba"
            "am" -> "Amharic"
            "en" -> "English"
            else -> localeTag
        }
    }

    companion object {
        private const val TAG = "LossAlertWorker"
    }
}
