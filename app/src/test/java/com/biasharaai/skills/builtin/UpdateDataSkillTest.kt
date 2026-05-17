package com.biasharaai.skills.builtin

import com.biasharaai.data.local.db.Product
import com.biasharaai.data.local.db.ProductDao
import com.biasharaai.skills.SkillResult
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdateDataSkillTest {

    private lateinit var productDao: ProductDao
    private lateinit var skill: UpdateDataSkill

    @Before
    fun setUp() {
        productDao = mockk(relaxed = true)
        skill = UpdateDataSkill(productDao)
    }

    @Test
    fun execute_updatesProductPrice() = runTest {
        val product = Product(
            id = 1L,
            name = "Sugar",
            price = 100.0,
            cost = 70.0,
            stockQuantity = 5,
            category = "Groceries",
        )
        coEvery { productDao.getProductByIdOnce(1L) } returns product

        val result = skill.execute("""{"entity":"product","entityId":1,"price":120}""")

        assertTrue(result is SkillResult.Success)
        coVerify {
            productDao.updateProduct(
                match { it.id == 1L && it.price == 120.0 && it.cost == 70.0 },
            )
        }
    }

    @Test
    fun execute_missingProduct_returnsNotFound() = runTest {
        coEvery { productDao.getProductByIdOnce(99L) } returns null

        val result = skill.execute("""{"entity":"product","entityId":99,"price":10}""")

        assertTrue(result is SkillResult.Failure)
        assertEquals("NOT_FOUND", (result as SkillResult.Failure).code)
    }
}
