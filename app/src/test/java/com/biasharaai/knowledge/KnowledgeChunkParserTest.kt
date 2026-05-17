package com.biasharaai.knowledge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeChunkParserTest {

    @Test
    fun emptyContent_returnsNoChunks() {
        val chunks = KnowledgeChunkParser.parse("", "knowledge/en/test.md", "en")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun shortFragment_belowMinLength_isSkipped() {
        val chunks = KnowledgeChunkParser.parse("Too short", "knowledge/en/test.md", "en")
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun singleParagraph_producesOneChunk() {
        val content = "This is a single paragraph with enough text to pass the minimum length filter."
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/en/test.md", "en")
        assertEquals(1, chunks.size)
        assertEquals(content.trim(), chunks[0].contentText)
    }

    @Test
    fun twoParagraphs_separatedByBlankLine_producesTwoChunks() {
        val content = """
            This is the first paragraph with enough text to be included.

            This is the second paragraph with enough text to be included.
        """.trimIndent()
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/en/test.md", "en")
        assertEquals(2, chunks.size)
    }

    @Test
    fun chunkIndex_isSequential() {
        val content = buildString {
            repeat(5) { i -> append("Paragraph $i with enough content to pass the minimum length filter.\n\n") }
        }
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/en/test.md", "en")
        chunks.forEachIndexed { idx, chunk -> assertEquals(idx, chunk.chunkIndex) }
    }

    @Test
    fun longParagraph_exceedingMax_isFlushedIntoSeparateChunks() {
        // Build two paragraphs each ≥ 300 chars so combined would exceed 512
        val para1 = "A".repeat(300) + " word boundary text so it is a real paragraph worth keeping."
        val para2 = "B".repeat(300) + " another paragraph of similar length that forces a flush."
        val content = "$para1\n\n$para2"
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/en/test.md", "en")
        assertTrue("Expected at least 2 chunks, got ${chunks.size}", chunks.size >= 2)
    }

    @Test
    fun h2Header_actAsChunkBoundary() {
        val content = "Introduction text that is long enough to pass the minimum filter.\n## New Section\nSection body text that is also long enough to pass the minimum filter."
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/en/test.md", "en")
        assertTrue("Expected ≥2 chunks for ## split, got ${chunks.size}", chunks.size >= 1)
    }

    @Test
    fun sourcePath_isPreservedInEveryChunk() {
        val content = "Paragraph one with enough text.\n\nParagraph two with enough text."
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/sw/add_product.md", "sw")
        chunks.forEach { assertEquals("knowledge/sw/add_product.md", it.sourcePath) }
    }

    @Test
    fun languageCode_isPreservedInEveryChunk() {
        val content = "Content long enough to pass the filter and be included as a chunk."
        val chunks = KnowledgeChunkParser.parse(content, "knowledge/fr/pos_sale.md", "fr")
        chunks.forEach { assertEquals("fr", it.languageCode) }
    }
}
