package com.biasharaai.ui.chat

/**
 * A single chat message in the conversation.
 */
data class ChatMessage(
    val text: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis(),
)
