package com.biasharaai.cloud

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class CloudAiAugmentationSettings(
    val enabled: Boolean,
    val internetResearchEnabled: Boolean,
    val sendBusinessContext: Boolean,
    val useForGeneralChat: Boolean,
    val gatewayUrl: String,
    val providerLabel: String,
    /** Non-null when a gateway token was saved; the secret stays inside the store. */
    val hasGatewayToken: Boolean,
)

/**
 * Opt-in cloud AI settings. This is intentionally separate from Enterprise upload settings:
 * augmentation is for chat/research, while Enterprise upload is for data export/sync.
 */
@Singleton
class CloudAiAugmentationSettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): CloudAiAugmentationSettings {
        val token = prefs.getString(KEY_GATEWAY_TOKEN, null).orEmpty()
        return CloudAiAugmentationSettings(
            enabled = prefs.getBoolean(KEY_ENABLED, false),
            internetResearchEnabled = prefs.getBoolean(KEY_INTERNET_RESEARCH, false),
            sendBusinessContext = prefs.getBoolean(KEY_SEND_BUSINESS_CONTEXT, false),
            useForGeneralChat = prefs.getBoolean(KEY_USE_FOR_GENERAL_CHAT, false),
            gatewayUrl = prefs.getString(KEY_GATEWAY_URL, "").orEmpty(),
            providerLabel = prefs.getString(KEY_PROVIDER_LABEL, DEFAULT_PROVIDER).orEmpty()
                .ifBlank { DEFAULT_PROVIDER },
            hasGatewayToken = token.isNotBlank(),
        )
    }

    fun gatewayTokenOrNull(): String? =
        prefs.getString(KEY_GATEWAY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun save(
        enabled: Boolean,
        internetResearchEnabled: Boolean,
        sendBusinessContext: Boolean,
        useForGeneralChat: Boolean,
        gatewayUrl: String,
        providerLabel: String = DEFAULT_PROVIDER,
        newGatewayTokenIfNonBlank: String?,
    ) {
        val ed = prefs.edit()
            .putBoolean(KEY_ENABLED, enabled)
            .putBoolean(KEY_INTERNET_RESEARCH, internetResearchEnabled)
            .putBoolean(KEY_SEND_BUSINESS_CONTEXT, sendBusinessContext)
            .putBoolean(KEY_USE_FOR_GENERAL_CHAT, useForGeneralChat)
            .putString(KEY_GATEWAY_URL, gatewayUrl.trim())
            .putString(KEY_PROVIDER_LABEL, providerLabel.trim().ifBlank { DEFAULT_PROVIDER })
        val token = newGatewayTokenIfNonBlank?.trim()
        if (!token.isNullOrEmpty()) {
            ed.putString(KEY_GATEWAY_TOKEN, token)
        }
        ed.apply()
    }

    companion object {
        const val PREFS = "cloud_ai_augmentation_prefs"
        const val DEFAULT_PROVIDER = "Claude"

        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERNET_RESEARCH = "internet_research_enabled"
        private const val KEY_SEND_BUSINESS_CONTEXT = "send_business_context"
        private const val KEY_USE_FOR_GENERAL_CHAT = "use_for_general_chat"
        private const val KEY_GATEWAY_URL = "gateway_url"
        private const val KEY_GATEWAY_TOKEN = "gateway_token"
        private const val KEY_PROVIDER_LABEL = "provider_label"
    }
}
