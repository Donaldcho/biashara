package com.biasharaai.profile

import com.biasharaai.data.local.db.AppSettingsDao
import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.data.local.db.BusinessProfileDao
import com.biasharaai.money.MoneyFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BusinessProfileRepository @Inject constructor(
    private val businessProfileDao: BusinessProfileDao,
    private val appSettingsDao: AppSettingsDao,
    private val moneyFormatter: MoneyFormatter,
) {

    fun observeProfile(): Flow<BusinessProfile> =
        businessProfileDao.observe().map { it ?: BusinessProfile() }

    suspend fun getOrCreate(): BusinessProfile = withContext(Dispatchers.IO) {
        businessProfileDao.get() ?: BusinessProfile().also { businessProfileDao.upsert(it) }
    }

    suspend fun isOnboardingComplete(): Boolean = withContext(Dispatchers.IO) {
        businessProfileDao.isOnboardingComplete() == true
    }

    suspend fun buildAgentContextHeader(): String? = withContext(Dispatchers.IO) {
        val profile = businessProfileDao.get() ?: return@withContext null
        if (!profile.hasIdentity()) return@withContext null
        BusinessContextBuilder.build(profile, moneyFormatter)
    }

    suspend fun upsert(profile: BusinessProfile) = withContext(Dispatchers.IO) {
        val updated = profile.copy(lastUpdatedAt = System.currentTimeMillis())
        businessProfileDao.upsert(updated)
        if (updated.businessName.isNotBlank()) {
            val settings = appSettingsDao.getSettingsSync()
            if (settings != null && settings.businessName != updated.businessName) {
                appSettingsDao.updateSettings(settings.copy(businessName = updated.businessName))
            }
        }
    }

    suspend fun applyOnboardingAnswer(stepIndex: Int, rawAnswer: String): BusinessProfile =
        withContext(Dispatchers.IO) {
            val current = getOrCreate()
            val answer = rawAnswer.trim()
            val updated = BusinessOnboardingFieldApplier.apply(current, stepIndex, answer)
                .copy(
                    onboardingStep = stepIndex + 1,
                    lastUpdatedAt = System.currentTimeMillis(),
                )
            businessProfileDao.upsert(updated)
            if (updated.businessName.isNotBlank()) {
                val settings = appSettingsDao.getSettingsSync()
                if (settings != null) {
                    appSettingsDao.updateSettings(settings.copy(businessName = updated.businessName))
                }
            }
            updated
        }

    suspend fun completeOnboarding() = withContext(Dispatchers.IO) {
        val current = getOrCreate()
        businessProfileDao.upsert(
            current.copy(
                onboardingComplete = true,
                onboardingStep = BusinessOnboardingSteps.STEP_COUNT,
                lastUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun resetOnboarding() = withContext(Dispatchers.IO) {
        val current = getOrCreate()
        businessProfileDao.upsert(
            current.copy(
                onboardingComplete = false,
                onboardingStep = 0,
                lastUpdatedAt = System.currentTimeMillis(),
            ),
        )
    }
}
