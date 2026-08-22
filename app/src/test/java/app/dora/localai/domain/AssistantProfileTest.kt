package app.dora.localai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AssistantProfileTest {
    @Test
    fun normalizationBoundsProfileFieldsAndSettings() {
        val profile = AssistantProfile(
            name = " ",
            description = "d".repeat(300),
            systemPrompt = "p".repeat(5_000),
            modelId = "  model-1  ",
            greeting = "g".repeat(800),
            settings = GenerationSettings(maxTokens = 9_999, threads = 0, temperature = 9f, topK = 0, topP = -1f),
        ).normalized()

        assertEquals("Local assistant", profile.name)
        assertEquals(160, profile.description.length)
        assertEquals(4_000, profile.systemPrompt.length)
        assertEquals("model-1", profile.modelId)
        assertEquals(500, profile.greeting.length)
        assertEquals(4_096, profile.settings.maxTokens)
        assertEquals(1, profile.settings.threads)
        assertEquals(2.0f, profile.settings.temperature)
        assertEquals(1, profile.settings.topK)
        assertEquals(0.05f, profile.settings.topP)
    }

    @Test
    fun blankModelBindingBecomesNull() {
        assertNull(AssistantProfile(name = "A", systemPrompt = "P", modelId = " ").normalized().modelId)
    }
}
