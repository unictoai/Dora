package app.dora.localai.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadTelemetryTest {
    @Test
    fun doesNotFabricateSpeedOrEtaBeforeElapsedSample() {
        var now = 1_000L
        val telemetry = DownloadTelemetry(1_000L, nowMillis = { now })
        val first = telemetry.update(100L)
        assertNull(first.speedBytesPerSecond)
        assertNull(first.estimatedRemainingTimeMillis)
        assertEquals(10, first.progressPercent)

        now += 1_000L
        val second = telemetry.update(200L)
        assertEquals(100L, second.speedBytesPerSecond)
        assertEquals(8_000L, second.estimatedRemainingTimeMillis)
    }

    @Test
    fun persistsAtTimeOrByteCheckpoint() {
        var now = 1_000L
        val telemetry = DownloadTelemetry(10_000_000L, nowMillis = { now })
        assertTrue(telemetry.update(0L).shouldPersist)
        assertTrue(!telemetry.update(1L).shouldPersist)
        now += 2_000L
        assertTrue(telemetry.update(2L).shouldPersist)
    }
}
