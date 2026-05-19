package com.biasharaai.profile

import com.biasharaai.data.local.db.BusinessProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class BusinessOnboardingFieldApplierTest {

    @Test
    fun apply_offer_detectsSalonServices() {
        val updated = BusinessOnboardingFieldApplier.apply(
            BusinessProfile(),
            1,
            "Natural hair braiding, twists, and locs",
        )
        assertEquals("salon", updated.businessType)
        assertEquals("Natural hair braiding, twists, and locs", updated.primaryServices)
    }

    @Test
    fun apply_goal_parsesRevenueTarget() {
        val updated = BusinessOnboardingFieldApplier.apply(
            BusinessProfile(),
            7,
            "Expand nails by December. Monthly target KSh 30,000",
        )
        assertNotNull(updated.monthlyRevenueTarget)
        assertEquals(30_000.0, updated.monthlyRevenueTarget!!, 0.01)
    }
}
