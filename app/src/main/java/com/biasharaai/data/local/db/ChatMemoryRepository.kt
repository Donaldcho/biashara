package com.biasharaai.data.local.db

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists owner “remember” notes from chat. Session-scoped messages live in [ChatSessionRepository].
 */
@Singleton
class ChatMemoryRepository @Inject constructor(
    private val chatMemoryDao: ChatMemoryDao,
) {

    suspend fun captureMemoryFromUserMessage(raw: String) {
        val extracted = extractMemoryText(raw) ?: return
        chatMemoryDao.insertMemory(AiBusinessMemory(text = extracted))
        trimMemoriesIfNeeded()
    }

    suspend fun formatMemoryPrefixForFacts(limit: Int = 15): String {
        val rows = chatMemoryDao.getRecentMemories(limit)
        if (rows.isEmpty()) return ""
        return rows.joinToString(
            prefix = "Owner preferences (remember from chat; if this conflicts with database facts below, trust the database for numbers):\n",
            separator = "\n",
        ) { "- ${it.text.trim()}" } + "\n\n"
    }

    suspend fun buildMemoryBlockForPrompt(maxNotes: Int = 14, maxChars: Int = 1200): String {
        val rows = chatMemoryDao.getRecentMemories(maxNotes)
        if (rows.isEmpty()) return ""
        val header = "Long-term notes the owner asked to remember (preferences; database facts win on figures):\n"
        val sb = StringBuilder(header)
        var budget = maxChars - header.length
        for (row in rows.asReversed()) {
            val line = "- ${row.text.trim()}\n"
            if (line.length > budget) break
            sb.append(line)
            budget -= line.length
        }
        return sb.toString().trimEnd() + "\n\n"
    }

    private suspend fun trimMemoriesIfNeeded() {
        val n = chatMemoryDao.countMemories()
        val excess = n - MAX_MEMORIES
        if (excess > 0) chatMemoryDao.deleteOldestMemories(excess)
    }

    companion object {
        private const val MAX_MEMORIES = 40
        private const val MAX_MEMORY_CHARS = 500

        private val memoryPatterns = listOf(
            Regex("(?i)remember\\s+that\\s*[:.]?\\s*(.+)"),
            Regex("(?i)please\\s+remember\\s*[:.]?\\s*(.+)"),
            Regex("(?i)don't\\s+forget\\s*[:.]?\\s*(.+)"),
            Regex("(?i)do\\s+not\\s+forget\\s*[:.]?\\s*(.+)"),
            Regex("(?i)note\\s+to\\s+self\\s*[:.]?\\s*(.+)"),
            Regex("(?i)note\\s+that\\s*[:.]?\\s*(.+)"),
            Regex("(?i)for\\s+future\\s+reference\\s*[:.]?\\s*(.+)"),
        )

        private fun extractMemoryText(raw: String): String? {
            val trimmed = raw.trim()
            if (trimmed.length < 12) return null
            for (p in memoryPatterns) {
                val m = p.find(trimmed) ?: continue
                val cap = m.groupValues.getOrNull(1)?.trim() ?: continue
                if (cap.length < 8) continue
                return cap.take(MAX_MEMORY_CHARS)
            }
            return null
        }
    }
}
