package app.dora.localai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentTextChunkerTest {
    @Test
    fun emptyAndWhitespaceInputProducesNoChunks() {
        assertTrue(DocumentTextChunker.chunk("  \n\t ").isEmpty())
    }

    @Test
    fun chunkerKeepsReadableBoundedSections() {
        val text = ("Dora keeps this sentence local and searchable. ").repeat(80)
        val chunks = DocumentTextChunker.chunk(text)
        assertTrue(chunks.size >= 2)
        assertTrue(chunks.all { it.length <= 1_200 })
        assertTrue(chunks.all { it.length >= 20 })
    }

    @Test
    fun searchNormalizationRemovesPunctuationAndFoldsCase() {
        assertEquals("dora local ai 2026", DocumentTextChunker.normalizeForSearch("Dora, Local-AI: 2026!"))
    }
}
