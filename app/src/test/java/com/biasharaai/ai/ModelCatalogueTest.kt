package com.biasharaai.ai

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelCatalogueTest {

  private val gson = Gson()

  @Test
  fun parseCatalogue_includesPrimaryGemmaModel() {
    val json = """
      {
        "catalogueVersion": 1,
        "defaultPrimaryModelId": "gemma-4-e2b-it",
        "models": [
          {
            "modelId": "gemma-4-e2b-it",
            "displayName": "Gemma 4 E2B (recommended)",
            "huggingFaceRepo": "litert-community/gemma-4-E2B-it-litert-lm",
            "fileName": "gemma-4-E2B-it.litertlm",
            "sizeBytes": 2705326080,
            "sha256": "",
            "capabilities": ["TEXT_GENERATION"],
            "minTier": "PARTIAL_AI",
            "isPrimaryCandidate": true
          }
        ]
      }
    """.trimIndent()

    val catalogue = gson.fromJson(json, ModelCatalogue::class.java)

    assertEquals(1, catalogue.catalogueVersion)
    assertEquals("gemma-4-e2b-it", catalogue.defaultPrimaryModelId)
    assertEquals(1, catalogue.models.size)
    val entry = catalogue.models.first()
    assertEquals("gemma-4-E2B-it.litertlm", entry.fileName)
    assertTrue(entry.huggingFaceResolveUrl().contains("huggingface.co"))
    assertTrue(entry.capabilitiesJson().contains("TEXT_GENERATION"))
  }
}
