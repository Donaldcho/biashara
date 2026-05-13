package com.biasharaai.chat.skills

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gallery-style **skills from URL**: host a JSON array of suggestion chips that add a prompt prefix.
 *
 * Example JSON (`GET` returns UTF-8):
 * ```json
 * [
 *   {"id":"restock","label":"Restock tips","promptPrefix":"Give practical restocking advice for a small shop in East Africa for: "}
 * ]
 * ```
 */
@Singleton
class RemoteSkillManifestStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("chat_skill_manifest", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getManifestUrl(): String = prefs.getString(KEY_URL, "") ?: ""

    fun setManifestUrl(url: String) {
        prefs.edit().putString(KEY_URL, url.trim()).apply()
    }

    fun getCachedChips(): List<RemoteSkillChip> {
        val raw = prefs.getString(KEY_CACHE_JSON, null) ?: return emptyList()
        return parseChips(raw)
    }

    fun cacheChips(chips: List<RemoteSkillChip>) {
        prefs.edit().putString(KEY_CACHE_JSON, gson.toJson(chips)).apply()
    }

    fun parseChips(json: String): List<RemoteSkillChip> = try {
        val type = object : TypeToken<List<RemoteSkillChip>>() {}.type
        gson.fromJson<List<RemoteSkillChip>>(json, type) ?: emptyList()
    } catch (_: Exception) {
        emptyList()
    }

    suspend fun fetchFromUrl(url: String): Result<List<RemoteSkillChip>> = withContext(Dispatchers.IO) {
        runCatching {
            val u = URL(url)
            val conn = (u.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15_000
                readTimeout = 15_000
                setRequestProperty("User-Agent", "BiasharaAI/1.0")
            }
            try {
                if (conn.responseCode != 200) error("HTTP ${conn.responseCode}")
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val chips = parseChips(body).filter { it.label.isNotBlank() && it.promptPrefix.isNotBlank() }
                if (chips.isEmpty()) error("empty_manifest")
                cacheChips(chips)
                setManifestUrl(url)
                chips
            } finally {
                conn.disconnect()
            }
        }
    }

    companion object {
        private const val KEY_URL = "manifest_url"
        private const val KEY_CACHE_JSON = "manifest_cache_json"
    }
}

data class RemoteSkillChip(
    @SerializedName("id") val id: String = "",
    @SerializedName("label") val label: String = "",
    @SerializedName("promptPrefix") val promptPrefix: String = "",
)
