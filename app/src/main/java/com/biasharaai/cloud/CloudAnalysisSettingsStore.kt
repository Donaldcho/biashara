package com.biasharaai.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CloudAnalysisSettings(
    val enabled: Boolean,
    val endpointUrl: String,
    /** Non-null when a key was saved; actual secret is not exposed outside the store. */
    val hasApiKey: Boolean,
)

/**
 * Persists optional cloud analytics endpoint and API key.
 * Uses a private preferences file (same pattern as [com.biasharaai.ai.InferenceSettingsStore]).
 * For stricter at-rest encryption, migrate to `EncryptedSharedPreferences` when you add
 * `androidx.security:security-crypto` to the app module.
 */
@Singleton
class CloudAnalysisSettingsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val PREFS = "cloud_analysis_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_URL = "endpoint_url"
        private const val KEY_API_KEY = "api_key"
    }

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): CloudAnalysisSettings {
        val key = prefs.getString(KEY_API_KEY, null).orEmpty()
        return CloudAnalysisSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            endpointUrl = prefs.getString(KEY_URL, "").orEmpty(),
            hasApiKey = key.isNotEmpty(),
        )
    }

    /** Returns the bearer token for HTTPS uploads, or null if none configured. */
    fun apiKeyOrNull(): String? =
        prefs.getString(KEY_API_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Persists toggles and URL. When [newApiKeyIfNonBlank] is non-null and not blank, replaces the
     * stored API key; when null or blank, the previous key is left unchanged.
     */
    fun save(enabled: Boolean, endpointUrl: String, newApiKeyIfNonBlank: String?) {
        val ed = prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putString(KEY_URL, endpointUrl.trim())
        val trimmed = newApiKeyIfNonBlank?.trim()
        if (!trimmed.isNullOrEmpty()) {
            ed.putString(KEY_API_KEY, trimmed)
        }
        ed.apply()
    }
}
