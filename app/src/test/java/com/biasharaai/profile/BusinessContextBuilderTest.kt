package com.biasharaai.profile

import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessContextBuilderTest {

    @Test
    fun build_includesBusinessNameAndGoal() {
        val cartRepository = mockk<CartRepository>()
        every { cartRepository.activeSettings } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        val formatter = MoneyFormatter(cartRepository)
        val profile = BusinessProfile(
            businessName = "Amina's Hair Studio",
            ownerName = "Amina",
            businessType = "salon",
            primaryServices = "braiding, twists",
            targetCustomer = "young professionals",
            monthlyRevenueTarget = 30_000.0,
            businessGoal = "Add nails section",
        )
        val block = BusinessContextBuilder.build(profile, formatter)
        assertTrue(block.contains("Amina's Hair Studio"))
        assertTrue(block.contains("Amina"))
        assertTrue(block.contains("Add nails section"))
        assertTrue(block.contains("braiding"))
    }
}
