package app.dora.localai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GenerationSettingsTest {
    @Test
    fun normalizedClampsRuntimeValues() {
        val normalized = GenerationSettings(
            systemPrompt = "  keep me  ",
            maxTokens = 100_000,
            threads = 0,
            temperature = -1f,
            topK = 0,
            topP = 2f,
        ).normalized()

        assertEquals("keep me", normalized.systemPrompt)
        assertEquals(4_096, normalized.maxTokens)
        assertEquals(1, normalized.threads)
        assertEquals(0.05f, normalized.temperature)
        assertEquals(1, normalized.topK)
        assertEquals(1.0f, normalized.topP)
    }

    @Test
    fun systemPromptIsBounded() {
        val normalized = GenerationSettings(systemPrompt = "x".repeat(10_000)).normalized()
        assertEquals(4_000, normalized.systemPrompt.length)
        assertTrue(normalized.systemPrompt.all { it == 'x' })
    }
}
