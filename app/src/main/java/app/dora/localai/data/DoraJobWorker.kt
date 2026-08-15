package app.dora.localai.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.Data
import androidx.work.WorkerParameters
import app.dora.localai.DoraApplication
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import app.dora.localai.engine.NativeLlamaEngine
import java.util.UUID

class DoraModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val dao = (applicationContext as DoraApplication).database.dao()

    override suspend fun doWork(): Result {
        setForeground(createForegroundInfo(0f))
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: modelId
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val expectedSha256 = inputData.getString(KEY_SHA256).orEmpty()
        val expectedBytes = inputData.getLong(KEY_BYTES, -1L).takeIf { it > 0L }
        val sourceRepo = inputData.getString(KEY_SOURCE_REPO)
        val sourceFilename = inputData.getString(KEY_SOURCE_FILENAME)
        val sourceRevision = inputData.getString(KEY_SOURCE_REVISION)
        val sourceLicense = inputData.getString(KEY_SOURCE_LICENSE)
        val record = JobRecord(jobId, "DOWNLOAD", modelName, JobState.RUNNING.name, 0f, "Starting secure download", System.currentTimeMillis())
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
        val result = ModelDownloadManager(applicationContext).download(
            ModelDownloadManager.DownloadManifest(model, url, expectedSha256.takeIf { it.isNotBlank() }, expectedBytes),
        ) { bytes, total ->
            val progress = if (total != null && total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            setProgress(Data.Builder().putDownloadProgress(bytes, progress).build())
            setForeground(createForegroundInfo(progress))
            dao.upsertJob(record.copy(progress = progress, message = "Downloading ${bytes / 1024 / 1024} MB", updatedAt = System.currentTimeMillis()))
        }

        return result.fold(
            onSuccess = { file ->
                val nativeValid = NativeLlamaEngine.isAvailable() && NativeLlamaEngine.validateModel(file.absolutePath)
                if (!nativeValid) {
                    file.delete()
                    val message = "The file downloaded, but llama.cpp could not load it on this ARM64 device."
                    dao.upsertJob(record.copy(state = JobState.FAILED.name, message = message, updatedAt = System.currentTimeMillis()))
                    return@fold Result.failure(Data.Builder().putError(message).build())
                }
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
                        sourceRevision = sourceRevision,
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
                        sourceRevision = sourceRevision,
                        sourceUrl = url,
                        sourceLicense = sourceLicense,
                    ),
                )
                dao.upsertJob(record.copy(state = JobState.COMPLETE.name, progress = 1f, message = "Validated and installed", updatedAt = System.currentTimeMillis()))
                Result.success(Data.Builder().putString(KEY_PATH, file.absolutePath).build())
            },
            onFailure = { error ->
                val message = error.message ?: "Download failed"
                if (isRetryable(message)) {
                    dao.upsertJob(record.copy(state = JobState.RUNNING.name, message = "Network interrupted; Dora will retry automatically", updatedAt = System.currentTimeMillis()))
                    Result.retry()
                } else {
                    dao.upsertJob(record.copy(state = JobState.FAILED.name, message = message, updatedAt = System.currentTimeMillis()))
                    Result.failure(Data.Builder().putError(message).build())
                }
            },
        )
    }

    private fun isRetryable(message: String): Boolean {
        val normalized = message.lowercase()
        return normalized.contains("timeout") || normalized.contains("connection") || normalized.contains("network") || normalized.contains("http 5") || normalized.contains("reset") || normalized.contains("temporarily")
    }

    private fun createForegroundInfo(progress: Float): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager?.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Model downloads", NotificationManager.IMPORTANCE_LOW))
        }
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("Downloading model")
            .setContentText(if (percent > 0) "$percent% — Dora is keeping the file private" else "Preparing secure download")
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
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"

        fun jobId(): String = UUID.randomUUID().toString()
    }
}

private fun Data.Builder.putDownloadProgress(bytes: Long, progress: Float): Data.Builder =
    putLong(DoraModelDownloadWorker.KEY_BYTES_DOWNLOADED, bytes).putFloat(DoraModelDownloadWorker.KEY_PROGRESS, progress)

private fun Data.Builder.putError(message: String): Data.Builder =
    putString(DoraModelDownloadWorker.KEY_ERROR, message)
