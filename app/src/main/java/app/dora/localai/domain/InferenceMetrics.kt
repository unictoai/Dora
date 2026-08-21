package app.dora.localai.domain

data class InferenceMetrics(
    val firstTokenLatencyMillis: Long,
    val generationTimeMillis: Long,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val contextTokenEstimate: Int,
)
