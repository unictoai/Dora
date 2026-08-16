package app.dora.localai.data

import java.util.ArrayDeque

/** Computes download telemetry from real byte/time samples without fabricating speed or ETA. */
class DownloadTelemetry(
    private val totalBytes: Long?,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val windowMillis: Long = 10_000L,
    private val checkpointIntervalMillis: Long = 2_000L,
    private val checkpointBytes: Long = 4L * 1024L * 1024L,
) {
    data class Sample(
        val bytesDownloaded: Long,
        val totalBytes: Long?,
        val progressPercent: Int?,
        val speedBytesPerSecond: Long?,
        val elapsedTimeMillis: Long,
        val estimatedRemainingTimeMillis: Long?,
        val updatedAt: Long,
        val shouldPersist: Boolean,
    )

    private data class Point(val at: Long, val bytes: Long)

    private val points = ArrayDeque<Point>()
    private var startedAt: Long? = null
    private var lastPersistAt: Long? = null
    private var lastPersistBytes = 0L

    fun update(bytesDownloaded: Long): Sample {
        val now = nowMillis()
        if (startedAt == null) startedAt = now
        val safeBytes = bytesDownloaded.coerceAtLeast(0L)
        points.addLast(Point(now, safeBytes))
        while (points.size > 2 && now - points.first.at > windowMillis) points.removeFirst()

        val first = points.firstOrNull()
        val speed = first?.let { point ->
            val duration = now - point.at
            val bytes = safeBytes - point.bytes
            if (duration > 0L && bytes > 0L) (bytes * 1000L / duration).coerceAtLeast(1L) else null
        }
        val remaining = totalBytes?.minus(safeBytes)?.coerceAtLeast(0L)
        val eta = if (speed != null && remaining != null) remaining * 1000L / speed else null
        val percent = totalBytes?.takeIf { it > 0L }?.let { (safeBytes * 100L / it).toInt().coerceIn(0, 100) }
        val shouldPersist = lastPersistAt == null || now - lastPersistAt!! >= checkpointIntervalMillis || safeBytes - lastPersistBytes >= checkpointBytes
        if (shouldPersist) {
            lastPersistAt = now
            lastPersistBytes = safeBytes
        }
        return Sample(safeBytes, totalBytes, percent, speed, now - (startedAt ?: now), eta, now, shouldPersist)
    }
}
