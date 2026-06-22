package com.biasharaai.agent

import com.biasharaai.data.local.db.AgentAction
import java.security.MessageDigest
import java.util.Locale

/** Stable content hash for agent advice dedupe and owner-review suppression. */
object AgentActionHasher {
    private const val CONTENT_HASH_DETAIL_LIMIT = 360
    private const val CONTENT_HASH_BYTES = 12
    private val NORMALIZE_DIGITS = Regex("\\d+")
    private val NORMALIZE_WHITESPACE = Regex("[^a-z0-9#]+")

    fun contentHash(action: AgentAction): String {
        val raw = buildString {
            append(action.agentType).append('|')
            append(action.actionVerb.orEmpty()).append('|')
            append(action.relatedEntityType.orEmpty()).append('|')
            append(action.relatedEntityId?.toString().orEmpty()).append('|')
            append(action.headline).append('|')
            append(action.detail.take(CONTENT_HASH_DETAIL_LIMIT))
        }
        val normalized = NORMALIZE_WHITESPACE.replace(
            NORMALIZE_DIGITS.replace(raw.lowercase(Locale.US), "#"),
            " ",
        ).trim()
        val digest = MessageDigest.getInstance("SHA-256").digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.take(CONTENT_HASH_BYTES).joinToString("") { byte ->
            String.format(Locale.US, "%02x", byte.toInt() and 0xff)
        }
    }
}
