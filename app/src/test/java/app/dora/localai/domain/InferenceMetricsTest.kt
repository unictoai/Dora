package app.dora.localai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InferenceMetricsTest {
    @Test
    fun normalizedClampsInvalidValues() {
        val normalized = InferenceMetrics(
            firstTokenLatencyMillis = -4L,
            generationTimeMillis = -2L,
            tokensGenerated = -1,
            tokensPerSecond = Float.NaN,
            contextTokenEstimate = -8,
        ).normalized()

        assertEquals(0L, normalized.firstTokenLatencyMillis)
        assertEquals(0L, normalized.generationTimeMillis)
        assertEquals(0, normalized.tokensGenerated)
        assertEquals(0f, normalized.tokensPerSecond, 0f)
        assertEquals(0, normalized.contextTokenEstimate)
    }

    @Test
    fun normalizedPreservesValidMeasurements() {
        val metrics = InferenceMetrics(120L, 900L, 42, 46.7f, 512).normalized()
        assertEquals(120L, metrics.firstTokenLatencyMillis)
        assertEquals(900L, metrics.generationTimeMillis)
        assertEquals(42, metrics.tokensGenerated)
        assertEquals(46.7f, metrics.tokensPerSecond, 0.001f)
        assertTrue(metrics.contextTokenEstimate == 512)
    }
}
