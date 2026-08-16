package app.dora.localai.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import app.dora.localai.DoraApplication
import app.dora.localai.domain.DownloadState
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import app.dora.localai.engine.NativeLlamaEngine
import java.io.File
import java.util.UUID

class DoraModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val dao = (applicationContext as DoraApplication).database.dao()
    private val controls = DownloadControlStore(applicationContext)

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(0f, DownloadState.STARTING))
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: modelId
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val expectedSha256 = inputData.getString(KEY_SHA256).orEmpty()
        val expectedBytes = inputData.getLong(KEY_BYTES, -1L).takeIf { it > 0L }
        val sourceRepo = inputData.getString(KEY_SOURCE_REPO)
        val sourceFilename = inputData.getString(KEY_SOURCE_FILENAME)
        val sourceLicense = inputData.getString(KEY_SOURCE_LICENSE)
        val startedAt = System.currentTimeMillis()
        val safeId = modelId.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val temporaryPath = File(applicationContext.filesDir, "models/$safeId.part").absolutePath
        val record = JobRecord(
            id = jobId,
            kind = "DOWNLOAD",
            label = modelName,
            state = JobState.RUNNING.name,
            progress = 0f,
            message = "Starting secure download",
            updatedAt = startedAt,
            downloadId = jobId,
            modelId = modelId,
            repositoryId = sourceRepo,
            filename = sourceFilename ?: modelName,
            sourceRevision = inputData.getString(KEY_SOURCE_REVISION),
            sourceLicense = sourceLicense,
            url = url,
            expectedSha256 = expectedSha256.takeIf { it.isNotBlank() },
            downloadState = DownloadState.STARTING.name,
            totalBytes = expectedBytes,
            startedAt = startedAt,
            temporaryPath = temporaryPath,
        )
        dao.upsertJob(record)

        val model = LocalModel(
            id = modelId,
            name = modelName,
            publisher = sourceRepo ?: "Hugging Face",
            kind = ModelKind.TEXT,
            format = "GGUF",
            sizeLabel = expectedBytes?.toString() ?: "Unknown",
            memoryLabel = "Fit check required",
            license = sourceLicense ?: "See model card",
            description = "Downloaded model",
            installState = ModelInstallState.AVAILABLE,
            verified = false,
            recommended = false,
        )
        val telemetry = DownloadTelemetry(expectedBytes)
        val result = try {
            ModelDownloadManager(applicationContext).download(
                ModelDownloadManager.DownloadManifest(model, url, expectedSha256.takeIf { it.isNotBlank() }, expectedBytes),
            ) { bytes, total ->
                val sample = telemetry.update(bytes)
                val percent = sample.progressPercent ?: 0
                val state = if (percent >= 100) DownloadState.VERIFYING else if (bytes > 0L) DownloadState.DOWNLOADING else DownloadState.STARTING
                val progress = percent / 100f
                val progressData = Data.Builder()
                    .putDownloadProgress(bytes, progress)
                    .putLong(KEY_SPEED_BYTES_PER_SECOND, sample.speedBytesPerSecond ?: -1L)
                    .putLong(KEY_ETA_MILLIS, sample.estimatedRemainingTimeMillis ?: -1L)
                    .putString(KEY_DOWNLOAD_STATE, state.name)
                    .build()
                setProgress(progressData)
                setForeground(createForegroundInfo(progress, state))
                if (sample.shouldPersist || state == DownloadState.VERIFYING) {
                    dao.upsertJob(record.copy(
                        state = JobState.RUNNING.name,
                        progress = progress,
                        message = if (state == DownloadState.VERIFYING) "Download complete; verifying artifact" else buildProgressMessage(sample),
                        updatedAt = sample.updatedAt,
                        downloadState = state.name,
                        bytesDownloaded = sample.bytesDownloaded,
                        totalBytes = sample.totalBytes,
                        speedBytesPerSecond = sample.speedBytesPerSecond,
                        estimatedRemainingTimeMillis = sample.estimatedRemainingTimeMillis,
                        elapsedTimeMillis = sample.elapsedTimeMillis,
                    ))
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            val paused = controls.isPauseRequested(jobId)
            val userCancelled = controls.isCancelRequested(jobId)
            if (userCancelled) {
                File(temporaryPath).delete()
            }
            dao.upsertJob(record.copy(
                state = if (paused && !userCancelled) JobState.RUNNING.name else JobState.CANCELED.name,
                message = if (paused && !userCancelled) "Paused — partial download retained" else "Download cancelled",
                updatedAt = System.currentTimeMillis(),
                downloadState = if (paused && !userCancelled) DownloadState.PAUSED.name else DownloadState.CANCELLED.name,
                errorMessage = null,
            ))
            if (userCancelled) controls.clear(jobId)
            throw cancelled
        }

        return result.fold(
            onSuccess = { file ->
                val verifyingAt = System.currentTimeMillis()
                dao.upsertJob(record.copy(
                    state = JobState.RUNNING.name,
                    progress = 1f,
                    message = "Verifying checksum and GGUF header",
                    updatedAt = verifyingAt,
                    downloadState = DownloadState.VERIFYING.name,
                    bytesDownloaded = file.length(),
                    totalBytes = file.length(),
                    finalPath = file.absolutePath,
                ))
                dao.upsertJob(record.copy(
                    state = JobState.RUNNING.name,
                    progress = 1f,
                    message = "Validating with llama.cpp",
                    updatedAt = System.currentTimeMillis(),
                    downloadState = DownloadState.VALIDATING.name,
                    bytesDownloaded = file.length(),
                    totalBytes = file.length(),
                    finalPath = file.absolutePath,
                ))
                val nativeValid = NativeLlamaEngine.isAvailable() && NativeLlamaEngine.validateModel(file.absolutePath)
                if (!nativeValid) {
                    file.delete()
                    val message = "The file downloaded, but llama.cpp could not load it on this ARM64 device."
                    dao.upsertJob(record.copy(
                        state = JobState.FAILED.name,
                        message = message,
                        updatedAt = System.currentTimeMillis(),
                        downloadState = DownloadState.FAILED.name,
                        errorMessage = message,
                        finalPath = null,
                    ))
                    return@fold Result.failure(Data.Builder().putError(message).build())
                }
                dao.upsertJob(record.copy(
                    state = JobState.RUNNING.name,
                    progress = 1f,
                    message = "Installing model into Dora’s private library",
                    updatedAt = System.currentTimeMillis(),
                    downloadState = DownloadState.INSTALLING.name,
                    bytesDownloaded = file.length(),
                    totalBytes = file.length(),
                    finalPath = file.absolutePath,
                ))
                dao.upsertModel(
                    ModelRecord(
                        id = modelId,
                        name = modelName,
                        kind = "TEXT",
                        format = "GGUF",
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        sha256 = expectedSha256,
                        license = sourceLicense ?: "See model card",
                        verified = true,
                        updatedAt = System.currentTimeMillis(),
                        sourceRepo = sourceRepo,
                        sourceFilename = sourceFilename,
                        sourceRevision = inputData.getString(KEY_SOURCE_REVISION),
                        sourceUrl = url,
                        sourceLicense = sourceLicense,
                    ),
                )
                LocalRegistry(applicationContext).setArtifact(
                    LocalRegistry.StoredArtifact(
                        id = modelId,
                        name = modelName,
                        path = file.absolutePath,
                        sizeBytes = file.length(),
                        sha256 = expectedSha256,
                        sourceRepo = sourceRepo,
                        sourceFilename = sourceFilename,
                        sourceRevision = inputData.getString(KEY_SOURCE_REVISION),
                        sourceUrl = url,
                        sourceLicense = sourceLicense,
                    ),
                )
                dao.upsertJob(record.copy(
                    state = JobState.COMPLETE.name,
                    progress = 1f,
                    message = "Validated and installed",
                    updatedAt = System.currentTimeMillis(),
                    downloadState = DownloadState.COMPLETED.name,
                    bytesDownloaded = file.length(),
                    totalBytes = file.length(),
                    finalPath = file.absolutePath,
                    errorMessage = null,
                ))
                Result.success(Data.Builder().putString(KEY_PATH, file.absolutePath).build())
            },
            onFailure = { error ->
                val message = error.message ?: "Download failed"
                if (isRetryable(message)) {
                    dao.upsertJob(record.copy(
                        state = JobState.RUNNING.name,
                        message = "Network interrupted; Dora will retry automatically",
                        updatedAt = System.currentTimeMillis(),
                        downloadState = DownloadState.RETRYING.name,
                        retryCount = runAttemptCount + 1,
                        errorMessage = message,
                    ))
                    Result.retry()
                } else {
                    dao.upsertJob(record.copy(
                        state = JobState.FAILED.name,
                        message = message,
                        updatedAt = System.currentTimeMillis(),
                        downloadState = DownloadState.FAILED.name,
                        retryCount = runAttemptCount,
                        errorMessage = message,
                    ))
                    Result.failure(Data.Builder().putError(message).build())
                }
            },
        )
    }

    private fun isRetryable(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("timeout") || normalized.contains("connection") || normalized.contains("network") || normalized.contains("http 5") || normalized.contains("http 429") || normalized.contains("reset") || normalized.contains("temporarily")
    }

    private fun buildProgressMessage(sample: DownloadTelemetry.Sample): String {
        val size = sample.totalBytes?.let { "${formatBytes(sample.bytesDownloaded)} / ${formatBytes(it)}" } ?: "${formatBytes(sample.bytesDownloaded)} downloaded"
        val speed = sample.speedBytesPerSecond?.let { " • ${formatBytes(it)}/s" }.orEmpty()
        val eta = sample.estimatedRemainingTimeMillis?.let { " • ~${formatDuration(it)} remaining" }.orEmpty()
        return "Downloading $size$speed$eta"
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.0f KB".format(bytes / 1024.0)
    }

    private fun formatDuration(millis: Long): String {
        val seconds = (millis / 1000L).coerceAtLeast(0L)
        return if (seconds >= 60L) "${seconds / 60L} min" else "$seconds sec"
    }

    private fun createForegroundInfo(progress: Float, state: DownloadState): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Dora model download")
            .setContentText(when (state) {
                DownloadState.VERIFYING -> "Verifying model integrity"
                DownloadState.VALIDATING -> "Validating with llama.cpp"
                DownloadState.INSTALLING -> "Installing into Dora"
                else -> if (percent > 0) "$percent% — Dora is keeping the file private" else "Preparing secure download"
            })
            .setProgress(100, percent, percent == 0)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val CHANNEL_ID = "dora_model_downloads"
        private const val NOTIFICATION_ID = 4101
        const val KEY_JOB_ID = "job_id"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_BYTES = "expected_bytes"
        const val KEY_SOURCE_REPO = "source_repo"
        const val KEY_SOURCE_FILENAME = "source_filename"
        const val KEY_SOURCE_REVISION = "source_revision"
        const val KEY_SOURCE_LICENSE = "source_license"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_PROGRESS = "progress"
        const val KEY_SPEED_BYTES_PER_SECOND = "speed_bytes_per_second"
        const val KEY_ETA_MILLIS = "eta_millis"
        const val KEY_DOWNLOAD_STATE = "download_state"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"

        fun jobId(): String = UUID.randomUUID().toString()
    }
}

private fun Data.Builder.putDownloadProgress(bytes: Long, progress: Float): Data.Builder =
    putLong(DoraModelDownloadWorker.KEY_BYTES_DOWNLOADED, bytes).putFloat(DoraModelDownloadWorker.KEY_PROGRESS, progress)

private fun Data.Builder.putError(message: String): Data.Builder =
    putString(DoraModelDownloadWorker.KEY_ERROR, message)
