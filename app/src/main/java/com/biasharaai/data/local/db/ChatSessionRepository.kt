package com.biasharaai.data.local.db

import com.biasharaai.data.ChatActiveSessionStore
import com.biasharaai.ui.chat.ChatMessage
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Multi-session chat persistence (Google AI Edge Gallery–style threads).
 * Replaces the legacy flat `chat_transcript_turns` table (migrated in **14→15**).
 */
@Singleton
class ChatSessionRepository @Inject constructor(
    private val chatSessionDao: ChatSessionDao,
    private val activeSessionStore: ChatActiveSessionStore,
) {

    fun observeSessions(): Flow<List<ChatSessionEntity>> = chatSessionDao.observeSessions()

    suspend fun listSessions(): List<ChatSessionEntity> = chatSessionDao.listSessions()

    suspend fun getSession(id: Long): ChatSessionEntity? = chatSessionDao.getSession(id)

    /**
     * Ensures [ChatActiveSessionStore] points at a valid session; creates one if needed.
     */
    suspend fun ensureActiveSession(defaultTitle: String): Long {
        val current = activeSessionStore.getActiveSessionId()
        if (current != ChatActiveSessionStore.NO_SESSION) {
            val row = chatSessionDao.getSession(current)
            if (row != null) return current
        }
        val sessions = chatSessionDao.listSessions()
        val pick = sessions.firstOrNull()?.id ?: createSession(defaultTitle)
        activeSessionStore.setActiveSessionId(pick)
        return pick
    }

    fun getActiveSessionIdFromStore(): Long = activeSessionStore.getActiveSessionId()

    suspend fun setActiveSession(id: Long) {
        if (chatSessionDao.getSession(id) != null) {
            activeSessionStore.setActiveSessionId(id)
        }
    }

    suspend fun createSession(title: String): Long {
        val now = System.currentTimeMillis()
        val id = chatSessionDao.insertSession(
            ChatSessionEntity(title = title, createdAt = now, updatedAt = now),
        )
        activeSessionStore.setActiveSessionId(id)
        return id
    }

    suspend fun deleteSession(id: Long) {
        chatSessionDao.deleteSession(id)
        if (activeSessionStore.getActiveSessionId() == id) {
            activeSessionStore.setActiveSessionId(ChatActiveSessionStore.NO_SESSION)
        }
    }

    suspend fun loadMessagesForUi(sessionId: Long): List<ChatMessage> =
        chatSessionDao.messagesForSession(sessionId).map { it.toUiMessage() }

    /**
     * Gallery-style transcript for Gemma — call **before** appending the current user turn.
     */
    suspend fun buildTranscriptBlockForPrompt(sessionId: Long, maxChars: Int = 2400): String {
        val turns = chatSessionDao.messagesForSession(sessionId)
        if (turns.isEmpty()) return ""
        val header = "Recent conversation (continuity only; business facts below override for stock/sales):\n"
        val sb = StringBuilder(header)
        var budget = maxChars - header.length
        val lines = ArrayDeque<String>()
        for (turn in turns.asReversed()) {
            val body = turn.body.trim()
            if (body.isBlank()) continue
            val block = if (turn.role == ROLE_USER) {
                "User:\n$body\n"
            } else {
                "Model:\n$body\n"
            }
            if (block.length > budget) break
            lines.addFirst(block)
            budget -= block.length
        }
        for (line in lines) sb.append(line).append("\n")
        return sb.toString().trimEnd() + "\n\n"
    }

    suspend fun appendUserMessage(sessionId: Long, text: String, imagePath: String? = null): Long {
        val t = text.trim()
        if (t.isBlank() && imagePath.isNullOrBlank()) return -1L
        val body = t.ifBlank { " " }
        maybeTrimSession(sessionId)
        val id = chatSessionDao.insertMessage(
            ChatSessionMessageEntity(
                sessionId = sessionId,
                role = ROLE_USER,
                body = body,
                imagePath = imagePath,
            ),
        )
        chatSessionDao.touchSession(sessionId)
        return id
    }

    suspend fun setMessageFeedback(messageId: Long, vote: Int?) {
        chatSessionDao.updateMessageFeedback(messageId, vote)
    }

    suspend fun appendAssistantMessage(sessionId: Long, text: String): Long {
        val t = text.trim()
        if (t.isBlank()) return -1L
        maybeTrimSession(sessionId)
        val id = chatSessionDao.insertMessage(
            ChatSessionMessageEntity(
                sessionId = sessionId,
                role = ROLE_ASSISTANT,
                body = t,
                imagePath = null,
            ),
        )
        chatSessionDao.touchSession(sessionId)
        return id
    }

    /** Sets list title from the first user line (only when this is the first message in the session). */
    suspend fun updateTitleFromFirstUserLine(sessionId: Long, candidate: String) {
        if (chatSessionDao.countMessages(sessionId) != 1) return
        val line = candidate.trim().lines().firstOrNull()?.take(TITLE_MAX) ?: return
        if (line.length < 4) return
        chatSessionDao.updateSessionTitle(sessionId, line)
    }

    private suspend fun maybeTrimSession(sessionId: Long) {
        val n = chatSessionDao.countMessages(sessionId)
        val excess = n - MAX_MESSAGES_PER_SESSION
        if (excess > 0) chatSessionDao.deleteOldestMessages(sessionId, excess)
    }

    private fun ChatSessionMessageEntity.toUiMessage(): ChatMessage =
        ChatMessage(
            stableId = id,
            text = body.trim(),
            isUser = role == ROLE_USER,
            imageUri = imagePath,
            timestamp = createdAt,
            feedbackVote = feedbackVote,
        )

    companion object {
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
        private const val MAX_MESSAGES_PER_SESSION = 400
        private const val TITLE_MAX = 48
    }
}
