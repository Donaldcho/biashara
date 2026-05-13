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
)
