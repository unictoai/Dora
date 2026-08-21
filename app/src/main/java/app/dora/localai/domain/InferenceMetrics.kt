package app.dora.localai.domain

data class InferenceMetrics(
    val firstTokenLatencyMillis: Long,
    val generationTimeMillis: Long,
    val tokensGenerated: Int,
    val tokensPerSecond: Float,
    val contextTokenEstimate: Int,
) {
    fun normalized(): InferenceMetrics = copy(
        firstTokenLatencyMillis = firstTokenLatencyMillis.coerceAtLeast(0L),
        generationTimeMillis = generationTimeMillis.coerceAtLeast(0L),
        tokensGenerated = tokensGenerated.coerceAtLeast(0),
        tokensPerSecond = tokensPerSecond.takeIf { it.isFinite() }?.coerceAtLeast(0f) ?: 0f,
        contextTokenEstimate = contextTokenEstimate.coerceAtLeast(0),
    )
}
