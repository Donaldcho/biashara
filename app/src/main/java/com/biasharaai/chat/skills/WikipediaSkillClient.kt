package com.biasharaai.chat.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lightweight Wikipedia **search** context (requires network). Inspired by Gallery Agent Skills
 * fact-grounding — we only fetch titles + snippets, not full article HTML.
 */
@Singleton
class WikipediaSkillClient @Inject constructor() {

    suspend fun fetchSearchContext(query: String, max: Int = 3): String? = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.length < 2) return@withContext null
        val enc = URLEncoder.encode(q, Charsets.UTF_8.name())
        val url = URL(
            "https://en.wikipedia.org/w/api.php?action=query&format=json&list=search&srsearch=$enc&srlimit=$max",
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("User-Agent", "BiasharaAI/1.0 (https://github.com/)")
        }
        try {
            if (conn.responseCode != 200) return@withContext null
            val json = conn.inputStream.bufferedReader().use { it.readText() }
            val root = JSONObject(json)
            val search = root.optJSONObject("query")?.optJSONArray("search") ?: return@withContext null
            if (search.length() == 0) return@withContext null
            buildString {
                append("Wikipedia search snippets (may be incomplete; verify critical facts):\n")
                for (i in 0 until search.length()) {
                    val o = search.getJSONObject(i)
                    val title = o.optString("title")
                    val snippet = o.optString("snippet").replace(Regex("<[^>]+>"), "")
                    append("- ")
                    append(title)
                    append(": ")
                    append(snippet.take(240))
                    append("\n")
                }
            }
        } finally {
            conn.disconnect()
        }
    }
}
