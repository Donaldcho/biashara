package com.biasharaai.ai

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FunctionGemmaRouterTest {

    private lateinit var registry: ModelRegistry
    private lateinit var router: FunctionGemmaRouter

    @Before
    fun setUp() {
        registry = mockk()
        every { registry.primaryModelId() } returns "gemma-4-e2b-it"
        every { registry.capabilitiesForModel("gemma-4-e2b-it") } returns setOf(ModelCapability.TEXT_GENERATION)
        every { registry.capabilitiesForModel(FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID) } returns
            setOf(ModelCapability.FUNCTION_CALLING)
        router = FunctionGemmaRouter(registry)
    }

    @Test
    fun route_whenFunctionModelMissing_usesPrimary() = runTest {
        coEvery { registry.resolveModelForCapability(ModelCapability.FUNCTION_CALLING) } returns null

        val route = router.routeForToolLoop()

        assertEquals("gemma-4-e2b-it", route.modelId)
        assertTrue(!route.useFunctionFastPath)
    }

    @Test
    fun route_whenPrimaryLacksFunctionCalling_usesFastPath() = runTest {
        coEvery { registry.resolveModelForCapability(ModelCapability.FUNCTION_CALLING) } returns
            FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID
        coEvery { registry.bestTokensPerSec(FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID) } returns 100f
        coEvery { registry.bestTokensPerSec("gemma-4-e2b-it") } returns 10f

        val route = router.routeForToolLoop()

        assertEquals(FunctionGemmaRouter.FUNCTION_GEMMA_MODEL_ID, route.modelId)
        assertTrue(route.useFunctionFastPath)
        assertEquals("primary_lacks_function_calling", route.reason)
    }
}
