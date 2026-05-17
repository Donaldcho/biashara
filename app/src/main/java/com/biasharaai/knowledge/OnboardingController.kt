package com.biasharaai.knowledge

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class OnboardingStep(
    val stepId: String,
    val title: String,
    val description: String,
    val featureId: String,
    val lessonId: String?,
    val isSkippable: Boolean = true,
)

@Singleton
class OnboardingController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val behaviourTracker: OwnerBehaviourTracker,
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    val isOnboardingComplete: Boolean
        get() = prefs.getBoolean(KEY_COMPLETE, false)

    val currentStepIndex: Int
        get() = prefs.getInt(KEY_STEP, 0)

    val steps: List<OnboardingStep> = listOf(
        OnboardingStep(
            stepId = "welcome",
            title = "Welcome to BiasharaAI",
            description = "Your AI-powered business assistant for East African traders. Let's set up your business in 5 minutes.",
            featureId = "settings",
            lessonId = "settings_basics",
            isSkippable = false,
        ),
        OnboardingStep(
            stepId = "add_first_product",
            title = "Add your first product",
            description = "Start by adding the products you sell. You can always add more later.",
            featureId = "add_product",
            lessonId = "add_product_basics",
        ),
        OnboardingStep(
            stepId = "make_first_sale",
            title = "Make your first sale",
            description = "Try the POS screen — it's how you record every sale.",
            featureId = "pos_sale",
            lessonId = "pos_sale_basics",
        ),
        OnboardingStep(
            stepId = "add_customer",
            title = "Add a customer",
            description = "Track who buys from you and manage credit sales.",
            featureId = "customers",
            lessonId = "customers_add",
        ),
        OnboardingStep(
            stepId = "meet_agent",
            title = "Meet your AI agent",
            description = "The AI agent monitors your business and sends you smart suggestions.",
            featureId = "agent_feed",
            lessonId = "agent_feed_intro",
        ),
    )

    fun currentStep(): OnboardingStep? = steps.getOrNull(currentStepIndex)

    fun advance() {
        val next = currentStepIndex + 1
        prefs.edit().putInt(KEY_STEP, next).apply()
        if (next >= steps.size) {
            complete()
        }
    }

    fun complete() {
        prefs.edit().putBoolean(KEY_COMPLETE, true).apply()
    }

    fun reset() {
        prefs.edit().putInt(KEY_STEP, 0).putBoolean(KEY_COMPLETE, false).apply()
    }

    companion object {
        private const val PREFS_NAME = "biashara_onboarding"
        private const val KEY_COMPLETE = "onboarding_complete"
        private const val KEY_STEP = "onboarding_step"
    }
}
