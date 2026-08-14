package app.dora.localai.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import app.dora.localai.DoraApplication
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import java.util.UUID

class DoraModelDownloadWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    private val dao = (applicationContext as DoraApplication).database.dao()

    override suspend fun doWork(): Result {
        val jobId = inputData.getString(KEY_JOB_ID) ?: return Result.failure()
        val modelId = inputData.getString(KEY_MODEL_ID) ?: return Result.failure()
        val modelName = inputData.getString(KEY_MODEL_NAME) ?: modelId
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val expectedSha256 = inputData.getString(KEY_SHA256) ?: return Result.failure()
        val expectedBytes = inputData.getLong(KEY_BYTES, -1L).takeIf { it > 0L }
        val record = JobRecord(jobId, "DOWNLOAD", modelName, JobState.RUNNING.name, 0f, "Starting secure download", System.currentTimeMillis())
        dao.upsertJob(record)

        val model = LocalModel(
            id = modelId,
            name = modelName,
            publisher = "Dora verified catalog",
            kind = ModelKind.TEXT,
            format = "GGUF",
            sizeLabel = expectedBytes?.toString() ?: "Unknown",
            memoryLabel = "Fit check required",
            license = "See manifest",
            description = "Downloaded model",
            installState = ModelInstallState.AVAILABLE,
            verified = false,
            recommended = false,
        )
        val result = ModelDownloadManager(applicationContext).download(
            ModelDownloadManager.DownloadManifest(model, url, expectedSha256, expectedBytes),
        ) { bytes, total ->
            val progress = if (total != null && total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
            setProgress(Data.Builder().putLong(KEY_BYTES_DOWNLOADED, bytes).putFloat(KEY_PROGRESS, progress).build())
            dao.upsertJob(record.copy(progress = progress, message = "Downloading ${bytes / 1024 / 1024} MB", updatedAt = System.currentTimeMillis()))
        }

        return result.fold(
            onSuccess = { file ->
                dao.upsertModel(ModelRecord(modelId, modelName, "TEXT", "GGUF", file.absolutePath, file.length(), expectedSha256, "See manifest", true, System.currentTimeMillis()))
                dao.upsertJob(record.copy(state = JobState.COMPLETE.name, progress = 1f, message = "Validated and installed", updatedAt = System.currentTimeMillis()))
                Result.success(Data.Builder().putString(KEY_PATH, file.absolutePath).build())
            },
            onFailure = { error ->
                dao.upsertJob(record.copy(state = JobState.FAILED.name, message = error.message ?: "Download failed", updatedAt = System.currentTimeMillis()))
                Result.failure(Data.Builder().putString(KEY_ERROR, error.message ?: "Download failed").build())
            },
        )
    }

    companion object {
        const val KEY_JOB_ID = "job_id"
        const val KEY_MODEL_ID = "model_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_URL = "url"
        const val KEY_SHA256 = "sha256"
        const val KEY_BYTES = "expected_bytes"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_PROGRESS = "progress"
        const val KEY_PATH = "path"
        const val KEY_ERROR = "error"

        fun jobId(): String = UUID.randomUUID().toString()
    }
}
