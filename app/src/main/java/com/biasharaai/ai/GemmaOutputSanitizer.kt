package com.biasharaai.ai

/**
 * Removes Gemma / LiteRT-LM control text and common decode artifacts before showing or saving output.
 *
 * Even with LiteRT-LM applying chat templates internally, streamed tokens can still leak thought
 * channels, template markers, fake follow-up turns, or short repetition loops.
 */
object GemmaOutputSanitizer {

    private val thoughtBlocks = listOf(
        Regex("""(?is)<\|channel\>\s*thought\s*.*?<channel\|>"""),
        Regex("""(?is)<\|channel\|>\s*thought\s*.*?<\|end\|>"""),
        Regex("""(?is)<thinking>.*?</thinking>"""),
    )

    private val controlTokens = Regex(
        """(?is)<\|/?[a-z0-9_\- ]+\|>|<\|channel>|<channel\|>|</?start_of_turn>|<\|start\|>|<\|end\|>""",
    )
    private val legacyTurnTokens = Regex(
        """(?is)\b(end_of_turn|start_of_turn|eot|bos|eos)\b""",
    )
    private val leadingChannelLabel = Regex("""(?is)^\s*(final|assistant|model)\s*[:\n]\s*""")
    private val fakeFollowUpTurn = Regex(
        """(?is)\n\s*(user|assistant|model)\s*:\s*.*$""",
    )
    private val roleOnlyLine = Regex("""(?m)^\s*(user|assistant|model)\s*$""")
    private val repeatedNumberLoop = Regex("""(?m)^(\d+(?:\.\d+)?)\.\s*\1(?:\.\s*\1){2,}.*$""")
    private val repeatedPhraseLoop = Regex("""(?is)(\b[\w']{2,24}\b(?:\s+\1){3,})""")
    private val excessBlankLines = Regex("""\n{3,}""")

    fun finalAnswer(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw
        thoughtBlocks.forEach { pattern ->
            cleaned = cleaned.replace(pattern, "")
        }
        cleaned = cleaned.replace(controlTokens, "")
        cleaned = cleaned.replace(legacyTurnTokens, "")
        cleaned = cleaned.replace(leadingChannelLabel, "")
        cleaned = cleaned.replace(fakeFollowUpTurn, "")
        cleaned = cleaned.replace(roleOnlyLine, "\n")
        cleaned = collapseRepetitionLoops(cleaned)
        cleaned = cleaned.replace(excessBlankLines, "\n\n")
        return cleaned.trim()
    }

    /** Streaming-safe: strips control tokens without removing partial thought blocks mid-stream. */
    fun streamingPreview(raw: String): String {
        if (raw.isBlank()) return ""
        var cleaned = raw
        cleaned = cleaned.replace(controlTokens, "")
        cleaned = cleaned.replace(legacyTurnTokens, "")
        cleaned = cleaned.replace(leadingChannelLabel, "")
        cleaned = cleaned.replace(fakeFollowUpTurn, "")
        cleaned = cleaned.replace(roleOnlyLine, "\n")
        cleaned = collapseRepetitionLoops(cleaned)
        cleaned = cleaned.replace(excessBlankLines, "\n\n")
        return cleaned.trim()
    }

    private fun collapseRepetitionLoops(text: String): String {
        var out = text.replace(repeatedNumberLoop, "$1.")
        out = repeatedPhraseLoop.replace(out) { match ->
            match.groupValues[1].substringBefore(' ').ifBlank { match.value }
        }
        return out
    }
}
