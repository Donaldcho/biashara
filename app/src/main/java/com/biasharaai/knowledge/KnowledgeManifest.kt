package com.biasharaai.knowledge

import com.google.gson.annotations.SerializedName

data class KnowledgeManifest(
    @SerializedName("version") val version: Int,
    @SerializedName("releaseDate") val releaseDate: String,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("sha256") val sha256: String,
    @SerializedName("sizeBytes") val sizeBytes: Long,
    @SerializedName("languagesIncluded") val languagesIncluded: List<String>,
    @SerializedName("changesSummary") val changesSummary: String = "",
)
