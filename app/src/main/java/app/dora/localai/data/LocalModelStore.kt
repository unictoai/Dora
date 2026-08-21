package app.dora.localai.data

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import app.dora.localai.domain.ModelMetadata
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class LocalModelStore(private val context: Context) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }

    data class ImportedArtifact(
        val id: String,
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val sha256: String,
        val metadata: ModelMetadata,
    )

    fun importGguf(uri: Uri): Result<ImportedArtifact> = runCatching {
        val displayName = queryDisplayName(uri) ?: "imported-model.gguf"
        require(displayName.lowercase().endsWith(".gguf")) { "Dora currently imports GGUF text models only." }

        val temporary = File(modelDirectory, "${System.nanoTime()}.part")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Dora could not open the selected file." }
            temporary.outputStream().buffered().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
        }

        require(temporary.length() >= MIN_GGUF_BYTES) { "The selected model is too small to be a valid GGUF file." }
        GgufValidator.validate(temporary).getOrElse { error ->
            throw IllegalArgumentException("The selected file is not a supported GGUF artifact: ${error.message}", error)
        }
        val metadata = GgufMetadataReader.read(temporary).getOrElse { error ->
            throw IllegalArgumentException("Dora could not read GGUF metadata safely: ${error.message}", error)
        }

        val hash = sha256(temporary)
        val id = "import-${hash.take(16)}"
        val finalFile = File(modelDirectory, "$id.gguf")
        if (finalFile.exists()) finalFile.delete()
        check(temporary.renameTo(finalFile)) { "Dora could not finalize the imported model." }

        ImportedArtifact(
            id = id,
            name = displayName,
            path = finalFile.absolutePath,
            sizeBytes = finalFile.length(),
            sha256 = hash,
            metadata = metadata,
        )
    }.onFailure {
        modelDirectory.listFiles { file -> file.extension == "part" }?.forEach { it.delete() }
    }

    fun delete(path: String?) {
        if (path.isNullOrBlank()) return
        val file = File(path).canonicalFile
        if (file.parentFile?.canonicalFile == modelDirectory.canonicalFile) file.delete()
    }

    fun clearPrivateArtifacts() {
        modelDirectory.listFiles().orEmpty().forEach { file ->
            val safe = runCatching { file.canonicalFile.parentFile?.canonicalFile == modelDirectory.canonicalFile }.getOrDefault(false)
            if (safe && file.extension.lowercase() in setOf("gguf", "part")) file.delete()
        }
    }

    fun deleteOrphanedFiles(knownPaths: Set<String>): Int {
        return modelDirectory.listFiles().orEmpty().count { file ->
            val safe = runCatching { file.canonicalFile.parentFile?.canonicalFile == modelDirectory.canonicalFile }.getOrDefault(false)
            val candidate = file.extension.lowercase() in setOf("gguf", "part") && file.absolutePath !in knownPaths
            safe && candidate && file.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getString(0)
        }
        return uri.lastPathSegment?.substringAfterLast('/')
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
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
        const val MIN_GGUF_BYTES = 1024L
    }
}
