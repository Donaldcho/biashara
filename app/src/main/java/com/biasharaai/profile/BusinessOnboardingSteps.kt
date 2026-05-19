package com.biasharaai.profile

import com.biasharaai.R

object BusinessOnboardingSteps {
    const val STEP_COUNT = 8

    data class Step(val index: Int, val questionResId: Int)

    val steps: List<Step> = listOf(
        Step(0, R.string.business_onboarding_q_name),
        Step(1, R.string.business_onboarding_q_offer),
        Step(2, R.string.business_onboarding_q_customers),
        Step(3, R.string.business_onboarding_q_hours),
        Step(4, R.string.business_onboarding_q_location),
        Step(5, R.string.business_onboarding_q_owner),
        Step(6, R.string.business_onboarding_q_suppliers),
        Step(7, R.string.business_onboarding_q_goal),
    )

    fun stepForIndex(index: Int): Step? = steps.getOrNull(index)
}
