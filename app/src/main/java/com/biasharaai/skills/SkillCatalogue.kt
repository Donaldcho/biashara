package com.biasharaai.skills

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class SkillCatalogue(
    @SerializedName("catalogueVersion") val catalogueVersion: Int,
    @SerializedName("skills") val skills: List<SkillCatalogueEntry>,
)

data class SkillCatalogueEntry(
    @SerializedName("skillId") val skillId: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("schemaJson") val schemaJson: String,
    @SerializedName("defaultEnabled") val defaultEnabled: Boolean = true,
    @SerializedName("packId") val packId: String? = null,
)

@Singleton
class SkillCatalogueLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    fun load(): SkillCatalogue {
        context.assets.open(CATALOGUE_ASSET).bufferedReader().use { reader ->
            return gson.fromJson(reader, SkillCatalogue::class.java)
                ?: error("skills_catalogue.json parsed to null")
        }
    }

    companion object {
        const val CATALOGUE_ASSET = "skills_catalogue.json"
    }
}
