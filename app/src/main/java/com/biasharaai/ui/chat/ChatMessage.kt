package com.biasharaai.ui.chat

/**
 * A single chat message in the conversation.
 *
 * @param stableId Room `chat_session_messages.id` when persisted; negative temp ids for in-flight UI rows.
 */
data class ChatMessage(
    val stableId: Long,
    val text: String,
    val isUser: Boolean,
    val imageUri: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    /** `1` user marked helpful, `-1` not helpful; `null` no vote. Persisted for assistant rows. */
    val feedbackVote: Int? = null,
    /** New-response UI metadata; older persisted messages leave this empty. */
    val sourceTags: List<String> = emptyList(),
    val confidenceLabel: String? = null,
    val actionHint: String? = null,
)
