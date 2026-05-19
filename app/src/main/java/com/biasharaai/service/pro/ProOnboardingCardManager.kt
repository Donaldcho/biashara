package com.biasharaai.service.pro

import com.biasharaai.agent.AgentTypes
import com.biasharaai.data.local.db.AgentAction
import com.biasharaai.data.local.db.AgentActionDao
import com.biasharaai.data.local.db.AppSettings
import com.biasharaai.data.local.db.AppSettingsDao
import android.content.Context
import com.biasharaai.locale.LanguagePreferences
import com.biasharaai.productline.ProductLineManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProOnboardingCardManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsDao: AppSettingsDao,
    private val agentActionDao: AgentActionDao,
    private val productLineManager: ProductLineManager,
) {
    suspend fun checkAndShowIfNeeded() {
        if (!productLineManager.isProEnabled()) return
        val settings = settingsDao.getSettingsSync() ?: AppSettings()
        if (settings.proOnboardingShown) return

        val language = LanguagePreferences.getPersistedLocaleTag(context)?.substringBefore('-') ?: "en"
        agentActionDao.insertAction(
            AgentAction(
                agentType = AgentTypes.PRO_ONBOARDING,
                urgency = "LOW",
                executionType = "REQUIRES_APPROVAL",
                headline = "Welcome to Biashara AI Pro! ✂️",
                detail = buildProWelcomeText(language),
                actionVerb = "EXPLORE_SERVICES",
                status = "PENDING",
                actionPayload = "{}",
                createdAt = System.currentTimeMillis(),
            ),
        )
        settingsDao.updateSettings(
            settings.copy(
                proOnboardingShown = true,
                proActivatedAt = System.currentTimeMillis(),
            ),
        )
    }

    private fun buildProWelcomeText(language: String): String = when (language) {
        "sw" -> "Karibu Pro! Sasa unaweza kuongeza huduma, kuuza vocha, na kufuatilia rekodi za huduma."
        else -> "Welcome to Pro! You can now add services, sell vouchers, and track service records."
    }
}
