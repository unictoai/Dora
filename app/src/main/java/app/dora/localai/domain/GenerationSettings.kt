package app.dora.localai.domain

data class GenerationSettings(
    val systemPrompt: String = "You are Dora, a concise and helpful private assistant. Be clear about uncertainty.",
    val maxTokens: Int = 256,
    val threads: Int = 4,
    val temperature: Float = 0.7f,
    val topK: Int = 40,
    val topP: Float = 0.95f,
) {
    fun normalized(): GenerationSettings = copy(
        systemPrompt = systemPrompt.trim().take(4_000),
        maxTokens = maxTokens.coerceIn(16, 4_096),
        threads = threads.coerceIn(1, 16),
        temperature = temperature.coerceIn(0.05f, 2.0f),
        topK = topK.coerceIn(1, 256),
        topP = topP.coerceIn(0.05f, 1.0f),
    )
}
