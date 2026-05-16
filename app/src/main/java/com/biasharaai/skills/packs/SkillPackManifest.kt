package com.biasharaai.skills.packs

import com.google.gson.annotations.SerializedName

/**
 * Signed skill pack manifest (JSON). OTA delivers this document; [SkillPackManager] persists
 * metadata to Room and registers bridged skill implementations.
 */
data class SkillPackManifest(
    @SerializedName("packId") val packId: String,
    @SerializedName("packName") val packName: String,
    @SerializedName("version") val version: String,
    @SerializedName("catalogueVersion") val catalogueVersion: Int = 1,
    @SerializedName("skills") val skills: List<SkillPackSkillEntry> = emptyList(),
    @SerializedName("signatureAlgorithm") val signatureAlgorithm: String? = null,
    @SerializedName("signatureBase64") val signatureBase64: String? = null,
) {
    data class SkillPackSkillEntry(
        @SerializedName("skillId") val skillId: String,
        @SerializedName("displayName") val displayName: String,
        @SerializedName("schemaJson") val schemaJson: String,
        @SerializedName("defaultEnabled") val defaultEnabled: Boolean = true,
        /**
         * Optional built-in skill id to delegate execution to (e.g. `ping`).
         * Pack-only metadata without a delegate is registered for tool schemas but returns NOT_IMPLEMENTED.
         */
        @SerializedName("delegateTo") val delegateTo: String? = null,
    )
}
