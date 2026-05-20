package com.biasharaai.knowledge

object KnowledgeChunkParser {

    private const val MAX_CHUNK_CHARS = 512

    /**
     * Splits [markdownContent] into semantically meaningful chunks suitable for embedding.
     *
     * Rules:
     * - Split on blank lines (two or more newlines) or at `##` header boundaries.
     * - Each chunk is at most [MAX_CHUNK_CHARS] characters; paragraphs that would overflow
     *   the buffer are flushed first.
     * - Fragments shorter than 20 characters are skipped (likely headings or whitespace).
     *
     * @param markdownContent Raw content of a `.md` knowledge file.
     * @param sourcePath      Asset path used to identify the file (e.g. `knowledge/en/basics.md`).
     * @param languageCode    BCP-47 language code (e.g. `"en"`, `"sw"`).
     * @return Ordered list of [ParsedChunk] with sequential [ParsedChunk.chunkIndex] values.
     */
    fun parse(markdownContent: String, sourcePath: String, languageCode: String): List<ParsedChunk> {
        val paragraphs = markdownContent
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("""(?:\n\s*){2,}|(?=^##\s)""", RegexOption.MULTILINE))
            .map { it.trim() }
            .filter { it.length >= 20 }

        val chunks = mutableListOf<ParsedChunk>()
        var chunkIndex = 0

        for (paragraph in paragraphs) {
            for (chunkText in splitParagraph(paragraph)) {
                if (chunkText.length >= 20) {
                    chunks.add(ParsedChunk(chunkText, sourcePath, languageCode, chunkIndex++))
                }
            }
        }

        return chunks
    }

    private fun splitParagraph(paragraph: String): List<String> {
        if (paragraph.length <= MAX_CHUNK_CHARS) return listOf(paragraph)

        val chunks = mutableListOf<String>()
        var remaining = paragraph.trim()
        while (remaining.length > MAX_CHUNK_CHARS) {
            val splitAt = remaining.lastIndexOf(' ', startIndex = MAX_CHUNK_CHARS)
                .takeIf { it >= 20 }
                ?: MAX_CHUNK_CHARS
            chunks.add(remaining.substring(0, splitAt).trim())
            remaining = remaining.substring(splitAt).trim()
        }
        if (remaining.isNotEmpty()) {
            chunks.add(remaining)
        }
        return chunks
    }
}

data class ParsedChunk(
    val contentText: String,
    val sourcePath: String,
    val languageCode: String,
    val chunkIndex: Int,
)
