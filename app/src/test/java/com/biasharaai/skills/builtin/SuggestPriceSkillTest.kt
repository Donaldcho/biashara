package com.biasharaai.skills.builtin

import com.biasharaai.ai.PricingAdvisor
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SuggestPriceSkillTest {

    private lateinit var productDao: ProductDao
    private lateinit var pricingAdvisor: PricingAdvisor
    private lateinit var skill: SuggestPriceSkill

    @Before
    fun setUp() {
        productDao = mockk()
        pricingAdvisor = mockk()
        skill = SuggestPriceSkill(productDao, pricingAdvisor)
        coEvery { productDao.getProductByIdOnce(2L) } returns Product(
            id = 2L,
            name = "Oil",
            price = 200.0,
            cost = 150.0,
            stockQuantity = 3,
            category = "Groceries",
        )
        coEvery { pricingAdvisor.suggestPrice(any()) } returns "Suggested price: 220.00\nGood margin."
    }

    @Test
    fun execute_returnsSuccessWithParsedPrice() = runTest {
        val result = skill.execute("""{"productId":2}""")

        assertTrue(result is SkillResult.Success)
    }
}
