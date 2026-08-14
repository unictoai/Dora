package app.dora.localai.data

import android.content.Context
import app.dora.localai.domain.LocalModel
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

class ModelDownloadManager(context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }

    data class DownloadManifest(
        val model: LocalModel,
        val url: String,
        val expectedSha256: String,
        val expectedBytes: Long? = null,
    )

    suspend fun download(
        manifest: DownloadManifest,
        onProgress: suspend (bytes: Long, total: Long?) -> Unit,
    ): Result<File> = runCatching {
        require(manifest.url.startsWith("https://")) { "Dora only accepts HTTPS model sources." }
        val finalFile = File(modelDirectory, "${manifest.model.id}.gguf")
        val partialFile = File(modelDirectory, "${manifest.model.id}.part")
        val existingBytes = if (partialFile.exists()) partialFile.length() else 0L

        val connection = (URL(manifest.url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/octet-stream")
            if (existingBytes > 0) setRequestProperty("Range", "bytes=$existingBytes-")
        }

        connection.connect()
        try {
            val response = connection.responseCode
            val append = existingBytes > 0 && response == HttpURLConnection.HTTP_PARTIAL
            if (response !in 200..299 && !append) error("Model download failed with HTTP $response")
            if (!append && existingBytes > 0) partialFile.delete()

            val startingBytes = if (append) existingBytes else 0L
            val total = connection.getHeaderFieldLong("Content-Length", -1L).let { length ->
                if (length <= 0) manifest.expectedBytes else length + startingBytes
            }
            connection.inputStream.use { input ->
                FileOutputStream(partialFile, append).buffered(DEFAULT_BUFFER).use { output ->
                    copyWithProgress(input, output, startingBytes, total, onProgress)
                }
            }
        } finally {
            connection.disconnect()
        }

        require(partialFile.exists() && partialFile.length() > 0L) { "Downloaded model is empty." }
        manifest.expectedBytes?.let { require(partialFile.length() == it) { "Downloaded model size does not match the manifest." } }
        val actualHash = sha256(partialFile)
        require(actualHash.equals(manifest.expectedSha256, ignoreCase = true)) { "Model checksum mismatch." }
        if (finalFile.exists()) finalFile.delete()
        check(partialFile.renameTo(finalFile)) { "Could not finalize the downloaded model." }
        finalFile
    }

    private suspend fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        initial: Long,
        total: Long?,
        onProgress: suspend (Long, Long?) -> Unit,
    ) {
        val buffer = ByteArray(DEFAULT_BUFFER)
        var copied = initial
        var lastReport = 0L
        while (true) {
            coroutineContext.ensureActive()
            val count = input.read(buffer)
            if (count <= 0) break
            output.write(buffer, 0, count)
            copied += count
            if (copied - lastReport >= REPORT_INTERVAL) {
                onProgress(copied, total)
                lastReport = copied
            }
        }
        output.flush()
        onProgress(copied, total)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(DEFAULT_BUFFER).use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val DEFAULT_BUFFER = 1024 * 1024
        const val REPORT_INTERVAL = 1024 * 1024 * 4L
    }
}
