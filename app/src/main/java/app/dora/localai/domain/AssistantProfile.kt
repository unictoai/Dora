package app.dora.localai.domain

import java.util.UUID

data class AssistantProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val systemPrompt: String,
    val modelId: String? = null,
    val greeting: String = "",
    val settings: GenerationSettings = GenerationSettings(),
) {
    fun normalized(): AssistantProfile = copy(
        name = name.trim().take(60).ifBlank { "Local assistant" },
        description = description.trim().take(160),
        systemPrompt = systemPrompt.trim().take(4_000),
        modelId = modelId?.trim()?.takeIf { it.isNotBlank() },
        greeting = greeting.trim().take(500),
        settings = settings.normalized(),
    )
}
