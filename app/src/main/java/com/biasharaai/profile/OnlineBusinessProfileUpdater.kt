package com.biasharaai.profile

import com.biasharaai.data.local.db.BusinessProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

data class OnlineBusinessProfileUpdate(
    val sourceUrl: String,
    val detectedFields: Map<String, String>,
    val appliedFields: Map<String, String>,
)

@Singleton
class OnlineBusinessProfileUpdater @Inject constructor(
    private val businessProfileRepository: BusinessProfileRepository,
) {

    suspend fun preview(url: String): Result<OnlineBusinessProfileUpdate> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceUrl = validateUrl(url)
            val html = downloadHtml(sourceUrl)
            val detected = extractFields(html)
            OnlineBusinessProfileUpdate(
                sourceUrl = sourceUrl,
                detectedFields = detected,
                appliedFields = emptyMap(),
            )
        }
    }

    suspend fun apply(url: String): Result<OnlineBusinessProfileUpdate> = withContext(Dispatchers.IO) {
        runCatching {
            val sourceUrl = validateUrl(url)
            val html = downloadHtml(sourceUrl)
            val detected = extractFields(html)
            require(detected.isNotEmpty()) {
                "No supported business fields were found at that URL."
            }
            val current = businessProfileRepository.getOrCreate()
            val updated = detected.entries.fold(current) { profile, (field, value) ->
                profile.withField(field, value)
            }
            businessProfileRepository.upsert(updated)
            OnlineBusinessProfileUpdate(
                sourceUrl = sourceUrl,
                detectedFields = detected,
                appliedFields = detected,
            )
        }
    }

    private fun validateUrl(raw: String): String {
        val trimmed = raw.trim()
        require(trimmed.startsWith("https://", ignoreCase = true)) {
            "Use an https:// business page URL."
        }
        return trimmed
    }

    private fun downloadHtml(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", "BiasharaAI-Android")
        }
        try {
            require(conn.responseCode in 200..299) {
                "HTTP ${conn.responseCode} while reading business source."
            }
            return conn.inputStream.bufferedReader().use { it.readText() }.take(MAX_HTML_CHARS)
        } finally {
            conn.disconnect()
        }
    }

    private fun extractFields(html: String): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        val title = metaContent(html, "og:site_name")
            ?: jsonString(html, "name")
            ?: titleText(html)
        title?.cleanValue()?.takeIf { it.length >= 2 }?.let { fields["businessName"] = it }

        val description = metaContent(html, "description")
            ?: metaContent(html, "og:description")
            ?: jsonString(html, "description")
        description?.cleanValue()?.takeIf { it.length >= 8 }?.let { fields["description"] = it }

        val address = jsonObjectSlice(html, "address")
            ?.let { slice ->
                listOfNotNull(
                    jsonString(slice, "streetAddress"),
                    jsonString(slice, "addressLocality"),
                    jsonString(slice, "addressRegion"),
                    jsonString(slice, "addressCountry"),
                ).joinToString(", ").cleanValue()
            }
            ?.takeIf { it.isNotBlank() }
            ?: jsonString(html, "address")?.cleanValue()
        address?.takeIf { it.length >= 3 }?.let { fields["location"] = it }

        val openingHours = jsonArrayOrString(html, "openingHours")
            ?: jsonArrayOrString(html, "openingHoursSpecification")
        openingHours?.cleanValue()?.takeIf { it.length >= 3 }?.let { fields["openHours"] = it }

        val offer = jsonArrayOrString(html, "makesOffer")
            ?: jsonArrayOrString(html, "serviceType")
            ?: jsonArrayOrString(html, "servesCuisine")
        offer?.cleanValue()?.takeIf { it.length >= 3 }?.let { fields["primaryServices"] = it }

        return fields
    }

    private fun BusinessProfile.withField(field: String, value: String): BusinessProfile =
        when (field) {
            "businessName" -> copy(businessName = value)
            "description" -> copy(description = value)
            "location" -> copy(location = value)
            "openHours" -> copy(openHours = value)
            "primaryServices" -> copy(primaryServices = value)
            "primaryProducts" -> copy(primaryProducts = value)
            else -> this
        }

    private fun titleText(html: String): String? =
        Regex("<title[^>]*>(.*?)</title>", RegexOption.IGNORE_CASE)
            .find(html)
            ?.groupValues
            ?.getOrNull(1)

    private fun metaContent(html: String, name: String): String? {
        val escaped = Regex.escape(name)
        val patterns = listOf(
            Regex(
                """<meta[^>]+(?:name|property)=["']$escaped["'][^>]+content=["']([^"']+)["'][^>]*>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
            Regex(
                """<meta[^>]+content=["']([^"']+)["'][^>]+(?:name|property)=["']$escaped["'][^>]*>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
    }

    private fun jsonString(text: String, key: String): String? {
        val escaped = Regex.escape(key)
        return Regex(
            """"$escaped"\s*:\s*"([^"]+)"""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)?.groupValues?.getOrNull(1)
    }

    private fun jsonArrayOrString(text: String, key: String): String? {
        jsonString(text, key)?.let { return it }
        val escaped = Regex.escape(key)
        val array = Regex(
            """"$escaped"\s*:\s*\[(.*?)]""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)?.groupValues?.getOrNull(1) ?: return null
        return Regex(""""([^"]+)"""")
            .findAll(array)
            .map { it.groupValues[1] }
            .take(8)
            .joinToString(", ")
    }

    private fun jsonObjectSlice(text: String, key: String): String? {
        val escaped = Regex.escape(key)
        return Regex(
            """"$escaped"\s*:\s*\{(.*?)}""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)?.groupValues?.getOrNull(1)
    }

    private fun String.cleanValue(): String =
        replace(Regex("<[^>]+>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_FIELD_CHARS)

    private companion object {
        private const val MAX_HTML_CHARS = 500_000
        private const val MAX_FIELD_CHARS = 300
    }
}
