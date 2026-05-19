package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.BusinessProfile
import com.biasharaai.money.MoneyFormatter
import com.biasharaai.pos.cart.CartRepository
import com.biasharaai.profile.BusinessProfileRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueryBusinessProfileSkillTest {

    @Test
    fun execute_returnsProfileWhenSet() = runTest {
        val repo = mockk<BusinessProfileRepository>()
        coEvery { repo.getOrCreate() } returns BusinessProfile(
            businessName = "Test Shop",
            ownerName = "Jane",
        )
        val cartRepository = mockk<CartRepository>()
        every { cartRepository.activeSettings } returns kotlinx.coroutines.flow.MutableStateFlow(null)
        val skill = QueryBusinessProfileSkill(repo, MoneyFormatter(cartRepository))
        val result = skill.execute("""{"includeContextBlock":false}""")
        assertTrue(result is com.biasharaai.skills.SkillResult.Success)
        val success = result as com.biasharaai.skills.SkillResult.Success
        val mapType = object : TypeToken<Map<String, Any?>>() {}.type
        val data: Map<String, Any?> = Gson().fromJson(success.outputJson, mapType)
        assertEquals(true, data["found"])
        assertEquals("Test Shop", data["businessName"])
    }
}
