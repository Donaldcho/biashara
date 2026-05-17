package com.biasharaai.skills.builtin

import com.biasharaai.ai.DemandForecaster
import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.data.local.db.SaleLineItemDao
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ForecastDemandSkillTest {

    private lateinit var productDao: ProductDao
    private lateinit var saleLineItemDao: SaleLineItemDao
    private lateinit var demandForecaster: DemandForecaster
    private lateinit var skill: ForecastDemandSkill

    @Before
    fun setUp() {
        productDao = mockk()
        saleLineItemDao = mockk()
        demandForecaster = mockk()
        skill = ForecastDemandSkill(productDao, saleLineItemDao, demandForecaster)
        coEvery { productDao.getProductByIdOnce(1L) } returns Product(
            id = 1L,
            name = "Rice",
            price = 50.0,
            cost = 30.0,
            stockQuantity = 10,
            category = "Groceries",
        )
        coEvery { saleLineItemDao.posSaleLineFactsSince(any()) } returns emptyList()
    }

    @Test
    fun execute_insufficientHistory_returnsFailure() = runTest {
        val result = skill.execute("""{"productId":1,"historyDays":14}""")

        assertTrue(result is SkillResult.Failure)
        assertEquals("INSUFFICIENT_DATA", (result as SkillResult.Failure).code)
    }
}
