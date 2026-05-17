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
            .split(Regex("\n{2,}|(?=##)"))
            .map { it.trim() }
            .filter { it.length >= 20 }

        val chunks = mutableListOf<ParsedChunk>()
        var chunkIndex = 0
        val buffer = StringBuilder()

        for (paragraph in paragraphs) {
            if (buffer.length + paragraph.length + 1 > MAX_CHUNK_CHARS && buffer.isNotEmpty()) {
                chunks.add(ParsedChunk(buffer.toString().trim(), sourcePath, languageCode, chunkIndex++))
                buffer.clear()
            }
            if (buffer.isNotEmpty()) buffer.append('\n')
            buffer.append(paragraph)
        }
        if (buffer.isNotEmpty()) {
            chunks.add(ParsedChunk(buffer.toString().trim(), sourcePath, languageCode, chunkIndex))
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
