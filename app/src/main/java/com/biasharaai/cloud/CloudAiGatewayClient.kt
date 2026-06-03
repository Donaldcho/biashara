package com.biasharaai.cloud

import com.biasharaai.BuildConfig
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

private val CLOUD_AI_JSON = "application/json; charset=utf-8".toMediaType()

data class CloudAiGatewayRequest(
    val gatewayUrl: String,
    val bearerToken: String?,
    val userQuestion: String,
    val languageName: String,
    val allowInternetResearch: Boolean,
    val businessContext: String?,
    val visualSummary: String?,
    val providerLabel: String,
)

data class CloudAiGatewaySource(
    val title: String,
    val url: String,
    val snippet: String,
)

data class CloudAiGatewayResponse(
    val answer: String,
    val provider: String?,
    val model: String?,
    val usedInternet: Boolean,
    val sources: List<CloudAiGatewaySource>,
)

@Singleton
class CloudAiGatewayClient @Inject constructor() {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(35, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun ask(request: CloudAiGatewayRequest): Result<CloudAiGatewayResponse> =
        suspendCancellableCoroutine { cont ->
            val httpReq = runCatching {
                CloudAiGatewayPolicy.requireAllowed(request.gatewayUrl)
                val body = buildRequestJson(request).toString().toRequestBody(CLOUD_AI_JSON)
                Request.Builder()
                    .url(request.gatewayUrl)
                    .post(body)
                    .header("Accept", "application/json")
                    .header("User-Agent", "BiasharaAI-Android/${BuildConfig.VERSION_NAME}")
                    .header("X-Biashara-AI-Mode", "optional-cloud-augmentation")
                    .apply {
                        if (!request.bearerToken.isNullOrBlank()) {
                            header("Authorization", "Bearer ${request.bearerToken.trim()}")
                        }
                    }
                    .build()
            }.getOrElse {
                cont.resume(Result.failure(it))
                return@suspendCancellableCoroutine
            }

            val call = client.newCall(httpReq)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resume(Result.failure(e))
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val result = runCatching {
                            response.use { resp ->
                                val text = resp.readBodyLimited()
                                if (!resp.isSuccessful) {
                                    error("HTTP ${resp.code}: ${text.cleanGatewayText(500)}")
                                }
                                parseGatewayResponse(text)
                            }
                        }
                        if (cont.isActive) cont.resume(result)
                    }
                },
            )
    }

    private fun buildRequestJson(request: CloudAiGatewayRequest): JSONObject =
        JSONObject().apply {
            put("mode", "research_augment")
            put("provider", request.providerLabel)
            put("userQuestion", request.userQuestion.take(MAX_QUESTION_CHARS))
            put("language", request.languageName)
            put("allowInternetResearch", request.allowInternetResearch)
            put("app", "BiasharaAI")
            put(
                "sovereigntyPolicy",
                JSONObject().apply {
                    put("localFirst", true)
                    put("optionalCloud", true)
                    put("businessDataIncluded", !request.businessContext.isNullOrBlank())
                    put("targetUsers", "SMEs in Africa")
                    put("instruction", SYSTEM_INSTRUCTION)
                },
            )
            request.visualSummary?.takeIf { it.isNotBlank() }?.let {
                put("visualSummary", it.take(MAX_VISUAL_CHARS))
            }
            request.businessContext?.takeIf { it.isNotBlank() }?.let {
                put("businessContext", it.take(MAX_BUSINESS_CONTEXT_CHARS))
            }
        }

    private fun parseGatewayResponse(raw: String): CloudAiGatewayResponse {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("{")) {
            val answer = trimmed.cleanGatewayText(MAX_ANSWER_CHARS)
            require(answer.isNotBlank()) { "Cloud AI gateway returned no answer." }
            return CloudAiGatewayResponse(
                answer = answer,
                provider = null,
                model = null,
                usedInternet = false,
                sources = emptyList(),
            )
        }
        val root = JSONObject(trimmed)
        val payload = root.optJSONObject("data")
            ?: root.optJSONObject("result")
            ?: root.optJSONObject("response")
            ?: root
        val answer = firstNonBlank(
            payload.optString("answer"),
            payload.optString("final"),
            payload.optJSONObject("message")?.optString("content").orEmpty(),
            payload.optString("message"),
            payload.optString("text"),
            choicesText(payload.optJSONArray("choices")),
            contentArrayText(payload.optJSONArray("content")),
        ).cleanGatewayText(MAX_ANSWER_CHARS)
        require(answer.isNotBlank()) { "Cloud AI gateway returned no answer." }
        val sources = parseSources(
            payload.optJSONArray("sources")
                ?: payload.optJSONArray("citations")
                ?: payload.optJSONArray("references")
                ?: JSONArray(),
        )
        return CloudAiGatewayResponse(
            answer = answer,
            provider = payload.optString("provider").cleanGatewayText(MAX_SOURCE_FIELD_CHARS).ifBlank { null },
            model = payload.optString("model").cleanGatewayText(MAX_SOURCE_FIELD_CHARS).ifBlank { null },
            usedInternet = payload.optBoolean("usedInternet", sources.isNotEmpty()),
            sources = sources,
        )
    }

    private fun choicesText(choices: JSONArray?): String {
        if (choices == null) return ""
        val pieces = buildList {
            for (i in 0 until choices.length()) {
                val choice = choices.optJSONObject(i) ?: continue
                firstNonBlank(
                    choice.optJSONObject("message")?.optString("content").orEmpty(),
                    choice.optString("text"),
                    choice.optString("content"),
                ).takeIf { it.isNotBlank() }?.let(::add)
            }
        }
        return pieces.joinToString("\n").trim()
    }

    private fun contentArrayText(content: JSONArray?): String {
        if (content == null) return ""
        val pieces = buildList {
            for (i in 0 until content.length()) {
                val item = content.optJSONObject(i) ?: continue
                val type = item.optString("type")
                if (type.isBlank() || type == "text") {
                    item.optString("text").takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
        return pieces.joinToString("\n").trim()
    }

    private fun parseSources(arr: JSONArray): List<CloudAiGatewaySource> = buildList {
        for (i in 0 until arr.length()) {
            val raw = arr.opt(i)
            if (raw is String) {
                val clean = raw.cleanGatewayText(MAX_SOURCE_FIELD_CHARS)
                if (clean.isNotBlank()) {
                    add(CloudAiGatewaySource(title = clean, url = clean, snippet = ""))
                }
                continue
            }
            val item = raw as? JSONObject ?: continue
            val url = firstNonBlank(
                item.optString("url"),
                item.optString("link"),
                item.optString("sourceUrl"),
                item.optString("uri"),
            ).cleanGatewayText(MAX_SOURCE_FIELD_CHARS)
            val title = firstNonBlank(
                item.optString("title"),
                item.optString("name"),
                item.optString("source"),
                url,
            ).cleanGatewayText(MAX_SOURCE_FIELD_CHARS)
            if (url.isBlank() && title.isBlank()) continue
            add(
                CloudAiGatewaySource(
                    title = title,
                    url = url,
                    snippet = firstNonBlank(
                        item.optString("snippet"),
                        item.optString("excerpt"),
                        item.optString("text"),
                    ).cleanGatewayText(MAX_SOURCE_FIELD_CHARS),
                ),
            )
        }
    }.take(MAX_SOURCES)

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() }.orEmpty()

    private fun Response.readBodyLimited(): String {
        val responseBody = this.body ?: return ""
        val contentLength = responseBody.contentLength()
        require(contentLength <= MAX_RESPONSE_BYTES || contentLength == -1L) {
            "Cloud AI gateway response is too large."
        }
        return responseBody.charStream().use { reader ->
            val buffer = CharArray(RESPONSE_READ_BUFFER_CHARS)
            val sb = StringBuilder()
            while (true) {
                val read = reader.read(buffer)
                if (read <= 0) break
                val remaining = MAX_RESPONSE_CHARS + 1 - sb.length
                if (remaining <= 0) break
                sb.append(buffer, 0, minOf(read, remaining))
                if (sb.length > MAX_RESPONSE_CHARS) {
                    error("Cloud AI gateway response is too large.")
                }
            }
            sb.toString()
        }
    }

    private fun String.cleanGatewayText(maxChars: Int): String =
        filter { it == '\n' || it == '\t' || it >= ' ' }
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
            .take(maxChars)

    private companion object {
        private const val MAX_QUESTION_CHARS = 8_000
        private const val MAX_ANSWER_CHARS = 6_000
        private const val MAX_BUSINESS_CONTEXT_CHARS = 12_000
        private const val MAX_VISUAL_CHARS = 2_000
        private const val MAX_SOURCE_FIELD_CHARS = 500
        private const val MAX_SOURCES = 4
        private const val MAX_RESPONSE_CHARS = 80_000
        private const val MAX_RESPONSE_BYTES = 256_000L
        private const val RESPONSE_READ_BUFFER_CHARS = 4_096

        private const val SYSTEM_INSTRUCTION =
            "Answer for an African SME operator. Prefer current sourced facts when internet research is allowed. " +
                "Never invent local business records. If business context is absent, do not pretend to know the shop data. " +
                "Keep the answer practical, concise, and source-aware."
    }
}
