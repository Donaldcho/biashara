package com.biasharaai.ai

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bundled catalogue of downloadable LiteRT-LM models (Phase 6 X2).
 * Parsed from [CATALOGUE_ASSET]; synced into Room by [ModelRegistry].
 */
data class ModelCatalogue(
    @SerializedName("catalogueVersion") val catalogueVersion: Int,
    @SerializedName("defaultPrimaryModelId") val defaultPrimaryModelId: String,
    @SerializedName("models") val models: List<ModelCatalogueEntry>,
)

data class ModelCatalogueEntry(
    @SerializedName("modelId") val modelId: String,
    @SerializedName("displayName") val displayName: String,
    @SerializedName("huggingFaceRepo") val huggingFaceRepo: String,
    @SerializedName("fileName") val fileName: String,
    @SerializedName("sizeBytes") val sizeBytes: Long,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("capabilities") val capabilities: List<String>,
    @SerializedName("minTier") val minTier: String,
    @SerializedName("isPrimaryCandidate") val isPrimaryCandidate: Boolean = false,
    /** When true, Hugging Face requires login + license acceptance; app needs [HuggingFaceTokenStore]. */
    @SerializedName("requiresHfAccess") val requiresHfAccess: Boolean = false,
) {
    fun huggingFaceResolveUrl(): String =
        "https://huggingface.co/$huggingFaceRepo/resolve/main/$fileName"

    fun capabilitiesJson(): String = Gson().toJson(capabilities)
}

@Singleton
class ModelCatalogueLoader @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val gson = Gson()

    fun load(): ModelCatalogue {
        context.assets.open(CATALOGUE_ASSET).bufferedReader().use { reader ->
            return gson.fromJson(reader, ModelCatalogue::class.java)
                ?: error("models_catalogue.json parsed to null")
        }
    }

    companion object {
        const val CATALOGUE_ASSET = "models_catalogue.json"
    }
}
