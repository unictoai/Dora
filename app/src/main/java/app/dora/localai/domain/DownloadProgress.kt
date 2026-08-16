package app.dora.localai.domain

/** Explicit lifecycle states for a model download and installation job. */
enum class DownloadState {
    QUEUED,
    STARTING,
    DOWNLOADING,
    PAUSED,
    VERIFYING,
    VALIDATING,
    INSTALLING,
    COMPLETED,
    CANCELLING,
    CANCELLED,
    FAILED,
    RETRYING,
}

/**
 * UI-facing progress owned by the download state layer rather than Compose.
 * Speed and ETA are nullable until enough real samples exist; Dora never fabricates them.
 */
data class DownloadProgress(
    val downloadId: String,
    val modelId: String,
    val repositoryId: String?,
    val filename: String,
    val state: DownloadState,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val progressPercent: Int?,
    val downloadSpeedBytesPerSecond: Long?,
    val elapsedTimeMillis: Long,
    val estimatedRemainingTimeMillis: Long?,
    val startedAt: Long?,
    val updatedAt: Long,
    val retryCount: Int,
    val errorMessage: String?,
    val isResumable: Boolean,
    val isPausable: Boolean,
    val isCancellable: Boolean,
) {
    val isFinished: Boolean
        get() = state == DownloadState.COMPLETED || state == DownloadState.CANCELLED || state == DownloadState.FAILED
}

/** Legal transitions are intentionally strict so a completed job cannot appear ready at 100%. */
object DownloadStateMachine {
    private val transitions = mapOf(
        DownloadState.QUEUED to setOf(DownloadState.STARTING, DownloadState.CANCELLING, DownloadState.CANCELLED),
        DownloadState.STARTING to setOf(DownloadState.DOWNLOADING, DownloadState.RETRYING, DownloadState.CANCELLING, DownloadState.FAILED),
        DownloadState.DOWNLOADING to setOf(DownloadState.PAUSED, DownloadState.VERIFYING, DownloadState.RETRYING, DownloadState.CANCELLING, DownloadState.FAILED),
        DownloadState.PAUSED to setOf(DownloadState.QUEUED, DownloadState.DOWNLOADING, DownloadState.CANCELLING, DownloadState.CANCELLED),
        DownloadState.VERIFYING to setOf(DownloadState.VALIDATING, DownloadState.RETRYING, DownloadState.FAILED, DownloadState.CANCELLING),
        DownloadState.VALIDATING to setOf(DownloadState.INSTALLING, DownloadState.RETRYING, DownloadState.FAILED, DownloadState.CANCELLING),
        DownloadState.INSTALLING to setOf(DownloadState.COMPLETED, DownloadState.RETRYING, DownloadState.FAILED, DownloadState.CANCELLING),
        DownloadState.RETRYING to setOf(DownloadState.STARTING, DownloadState.DOWNLOADING, DownloadState.FAILED, DownloadState.CANCELLING),
        DownloadState.CANCELLING to setOf(DownloadState.CANCELLED),
        DownloadState.FAILED to setOf(DownloadState.RETRYING, DownloadState.CANCELLING),
        DownloadState.COMPLETED to emptySet(),
        DownloadState.CANCELLED to emptySet(),
    )

    fun canTransition(from: DownloadState, to: DownloadState): Boolean = to in transitions.getValue(from)

    fun requireTransition(from: DownloadState, to: DownloadState) {
        require(canTransition(from, to)) { "Illegal download state transition: $from → $to" }
    }
}
