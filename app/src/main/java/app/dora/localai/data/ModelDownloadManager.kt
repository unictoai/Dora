package app.dora.localai.data

import android.content.Context
import app.dora.localai.domain.LocalModel
import kotlinx.coroutines.ensureActive
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import kotlin.coroutines.coroutineContext

class ModelDownloadManager(context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }

    data class DownloadManifest(
        val model: LocalModel,
        val url: String,
        val expectedSha256: String? = null,
        val expectedBytes: Long? = null,
    )

    suspend fun download(
        manifest: DownloadManifest,
        onProgress: suspend (bytes: Long, total: Long?) -> Unit,
    ): Result<File> {
        return try {
            Result.success(downloadInternal(manifest, onProgress))
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private suspend fun downloadInternal(
        manifest: DownloadManifest,
        onProgress: suspend (bytes: Long, total: Long?) -> Unit,
    ): File {
        require(manifest.url.startsWith("https://")) { "Dora only accepts HTTPS model sources." }
        val safeId = manifest.model.id.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val finalFile = File(modelDirectory, "$safeId.gguf")
        val partialFile = File(modelDirectory, "$safeId.part")
        val existingBytes = if (partialFile.exists()) partialFile.length() else 0L
        val connection = openDownloadConnection(manifest.url, existingBytes)
        try {
            val response = connection.responseCode
            val append = existingBytes > 0 && response == HttpURLConnection.HTTP_PARTIAL
            if (response !in 200..299 && !append) error("Model download failed with HTTP $response")
            if (!append && existingBytes > 0) partialFile.delete()

            val startingBytes = if (append) existingBytes else 0L
            val total = parseContentRangeTotal(connection.getHeaderField("Content-Range"))
                ?: connection.getHeaderFieldLong("Content-Length", -1L).let { length ->
                    when {
                        length <= 0L -> manifest.expectedBytes
                        append -> length + startingBytes
                        else -> length
                    }
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
        manifest.expectedBytes?.let { expected ->
            require(partialFile.length() == expected) {
                "Downloaded model size does not match Hugging Face metadata (${partialFile.length()} of $expected bytes)."
            }
        }
        val actualHash = sha256(partialFile)
        manifest.expectedSha256?.takeIf { it.isNotBlank() }?.let { expected ->
            require(actualHash.equals(expected, ignoreCase = true)) { "Model checksum mismatch." }
        }
        GgufValidator.validate(partialFile).getOrElse { error ->
            throw IllegalArgumentException("The downloaded file is not a supported GGUF artifact: ${error.message}", error)
        }
        if (finalFile.exists()) finalFile.delete()
        check(partialFile.renameTo(finalFile)) { "Could not finalize the downloaded model." }
        return finalFile
    }

    private fun openDownloadConnection(sourceUrl: String, rangeStart: Long): HttpURLConnection {
        var currentUrl = sourceUrl
        repeat(MAX_REDIRECTS + 1) { redirectCount ->
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("Accept", "application/octet-stream")
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("User-Agent", "Dora-Android/0.3")
                if (rangeStart > 0L) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            connection.connect()
            val response = connection.responseCode
            if (response !in REDIRECT_MIN..REDIRECT_MAX) return connection
            val location = connection.getHeaderField("Location") ?: run {
                connection.disconnect()
                error("Hugging Face returned a redirect without a destination.")
            }
            connection.disconnect()
            require(redirectCount < MAX_REDIRECTS) { "Too many redirects while opening the Hugging Face file." }
            currentUrl = URL(URL(currentUrl), location).toString()
            require(currentUrl.startsWith("https://")) { "Dora rejected a non-HTTPS model redirect." }
        }
        error("Could not open the Hugging Face model URL.")
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
        var lastReport = initial
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

    private fun parseContentRangeTotal(header: String?): Long? = header
        ?.substringAfter('/')
        ?.toLongOrNull()
        ?.takeIf { it > 0L }

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
        return digest.digest().joinToString("") { byte -> "%02x".format(Locale.US, byte) }
    }

    private companion object {
        const val DEFAULT_BUFFER = 1024 * 1024
        const val REPORT_INTERVAL = 1024 * 1024 * 4L
        const val MAX_REDIRECTS = 5
        const val REDIRECT_MIN = 300
        const val REDIRECT_MAX = 399
    }
}
