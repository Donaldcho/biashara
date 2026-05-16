package com.biasharaai.skills

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkillCatalogueTest {

    private val gson = Gson()

    @Test
    fun parseCatalogue_includesPingAndDataSkills() {
        val json = """
            {
              "catalogueVersion": 1,
              "skills": [
                {
                  "skillId": "ping",
                  "displayName": "Health check",
                  "schemaJson": "{}",
                  "defaultEnabled": true
                },
                {
                  "skillId": "query_sales",
                  "displayName": "Query sales",
                  "schemaJson": "{\"type\":\"object\"}",
                  "defaultEnabled": true
                }
              ]
            }
        """.trimIndent()

        val catalogue = gson.fromJson(json, SkillCatalogue::class.java)

        assertEquals(1, catalogue.catalogueVersion)
        assertEquals(2, catalogue.skills.size)
        assertEquals("ping", catalogue.skills.first().skillId)
        assertTrue(catalogue.skills.any { it.skillId == "query_sales" })
    }
}
