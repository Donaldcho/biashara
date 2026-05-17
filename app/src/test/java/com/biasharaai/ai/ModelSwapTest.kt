package com.biasharaai.ai

import android.content.Context
import com.biasharaai.data.local.db.ModelDescriptorDao
import io.mockk.every
import io.mockk.mockk
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * X12 — Phase 6 acceptance: model swap and per-capability routing.
 *
 * Tests [ModelRegistry] primary model switching, capability lookup, and per-capability
 * assignment — pure Kotlin logic exercised via [MemSharedPreferences].
 */
class ModelSwapTest {

    private lateinit var context: Context
    private lateinit var prefs: MemSharedPreferences
    private lateinit var catalogueLoader: ModelCatalogueLoader
    private lateinit var dao: ModelDescriptorDao
    private lateinit var registry: ModelRegistry
    private lateinit var tempDir: File

    private val twoModelCatalogue = ModelCatalogue(
        catalogueVersion = 1,
        defaultPrimaryModelId = "gemma-4-e2b-it",
        models = listOf(
            ModelCatalogueEntry(
                modelId = "gemma-4-e2b-it",
                displayName = "Gemma 4 E2B",
                huggingFaceRepo = "litert-community/gemma-4-E2B-it-litert-lm",
                fileName = "gemma-4-E2B-it.litertlm",
                sizeBytes = 2_705_326_080L,
                sha256 = "",
                capabilities = listOf("TEXT_GENERATION"),
                minTier = "PARTIAL_AI",
                isPrimaryCandidate = true,
            ),
            ModelCatalogueEntry(
                modelId = "functiongemma-270m-it",
                displayName = "FunctionGemma 270M",
                huggingFaceRepo = "google/functiongemma-270m-it-litert-lm",
                fileName = "functiongemma-270m-it.litertlm",
                sizeBytes = 500_000_000L,
                sha256 = "",
                capabilities = listOf("TEXT_GENERATION", "FUNCTION_CALLING"),
                minTier = "PARTIAL_AI",
            ),
        ),
    )

    @Before
    fun setUp() {
        tempDir = File.createTempFile("modelswap_test", "").apply { delete(); mkdirs() }
        prefs = MemSharedPreferences()
        context = mockk()
        every { context.getSharedPreferences(any(), any()) } returns prefs
        every { context.filesDir } returns tempDir
        catalogueLoader = mockk()
        every { catalogueLoader.load() } returns twoModelCatalogue
        dao = mockk(relaxed = true)
        registry = ModelRegistry(context, dao, catalogueLoader)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    // ── Primary model swap ─────────────────────────────────────────────────────

    @Test
    fun primaryModelId_noPreference_returnsDefaultFromCatalogue() {
        assertEquals("gemma-4-e2b-it", registry.primaryModelId())
    }

    @Test
    fun setPrimaryModelId_changesReturnedId() {
        registry.setPrimaryModelId("functiongemma-270m-it")
        assertEquals("functiongemma-270m-it", registry.primaryModelId())
    }

    @Test
    fun setPrimaryModelId_overwritePrevious_returnsLatest() {
        registry.setPrimaryModelId("functiongemma-270m-it")
        registry.setPrimaryModelId("gemma-4-e2b-it")
        assertEquals("gemma-4-e2b-it", registry.primaryModelId())
    }

    // ── Capability lookup ──────────────────────────────────────────────────────

    @Test
    fun capabilitiesForModel_textOnlyModel_containsTextGenerationOnly() {
        val caps = registry.capabilitiesForModel("gemma-4-e2b-it")
        assertTrue(caps.contains(ModelCapability.TEXT_GENERATION))
        assertFalse(caps.contains(ModelCapability.FUNCTION_CALLING))
    }

    @Test
    fun capabilitiesForModel_dualCapabilityModel_containsBoth() {
        val caps = registry.capabilitiesForModel("functiongemma-270m-it")
        assertTrue(caps.contains(ModelCapability.TEXT_GENERATION))
        assertTrue(caps.contains(ModelCapability.FUNCTION_CALLING))
    }

    @Test
    fun capabilitiesForModel_unknownModelId_returnsEmptySet() {
        assertTrue(registry.capabilitiesForModel("model-that-does-not-exist").isEmpty())
    }

    // ── Per-capability model assignment ───────────────────────────────────────

    @Test
    fun capabilityAssignment_setAndGet_roundTrip() {
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, "functiongemma-270m-it")
        assertEquals(
            "functiongemma-270m-it",
            registry.assignedModelForCapability(ModelCapability.FUNCTION_CALLING),
        )
    }

    @Test
    fun capabilityAssignment_clearWithNull_returnsNull() {
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, "functiongemma-270m-it")
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, null)
        assertNull(registry.assignedModelForCapability(ModelCapability.FUNCTION_CALLING))
    }

    @Test
    fun capabilityAssignment_clearWithBlankString_returnsNull() {
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, "functiongemma-270m-it")
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, "  ")
        assertNull(registry.assignedModelForCapability(ModelCapability.FUNCTION_CALLING))
    }

    @Test
    fun capabilityAssignment_distinctCapabilities_areStoredIndependently() {
        registry.setCapabilityModelId(ModelCapability.FUNCTION_CALLING, "functiongemma-270m-it")
        registry.setCapabilityModelId(ModelCapability.TEXT_GENERATION, "gemma-4-e2b-it")

        assertEquals("functiongemma-270m-it", registry.assignedModelForCapability(ModelCapability.FUNCTION_CALLING))
        assertEquals("gemma-4-e2b-it", registry.assignedModelForCapability(ModelCapability.TEXT_GENERATION))
    }

    @Test
    fun capabilityAssignment_unset_returnsNull() {
        assertNull(registry.assignedModelForCapability(ModelCapability.FUNCTION_CALLING))
    }

    // ── File / download state ──────────────────────────────────────────────────

    @Test
    fun isDownloaded_whenFileAbsent_returnsFalse() {
        assertFalse(registry.isDownloaded("gemma-4-e2b-it"))
    }

    @Test
    fun isDownloaded_whenFilePresent_returnsTrue() {
        val modelsDir = File(tempDir, ModelDownloadManager.MODELS_DIR).also { it.mkdirs() }
        File(modelsDir, "gemma-4-E2B-it.litertlm").writeText("placeholder")
        assertTrue(registry.isDownloaded("gemma-4-e2b-it"))
    }

    @Test
    fun resolveDownloadUrl_returnsHuggingFaceHttpsUrl() {
        val url = registry.resolveDownloadUrl("gemma-4-e2b-it")
        assertTrue(url.startsWith("https://huggingface.co/"))
        assertTrue(url.contains("gemma-4-E2B-it.litertlm"))
    }

    @Test
    fun resolveDownloadUrl_unknownModelId_throws() {
        try {
            registry.resolveDownloadUrl("does-not-exist")
            assertTrue("Expected exception", false)
        } catch (e: IllegalStateException) {
            assertTrue(e.message?.contains("does-not-exist") == true)
        }
    }
}
