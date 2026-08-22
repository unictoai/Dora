package app.dora.localai.data

import android.content.Context
import android.net.Uri
import android.os.StatFs
import android.provider.DocumentsContract
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

    data class FolderImportSummary(
        val imported: List<ImportedArtifact>,
        val skipped: Int,
        val errors: List<String>,
    )

    fun importGguf(uri: Uri): Result<ImportedArtifact> {
        var temporary: File? = null
        return runCatching {
        val displayName = queryDisplayName(uri) ?: "imported-model.gguf"
        require(displayName.lowercase().endsWith(".gguf")) { "Dora currently imports GGUF text models only." }

        temporary = File(modelDirectory, "${System.nanoTime()}.part")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Dora could not open the selected file." }
            temporary!!.outputStream().buffered().use { output -> input.copyTo(output, DEFAULT_BUFFER) }
        }

        require(temporary!!.length() >= MIN_GGUF_BYTES) { "The selected model is too small to be a valid GGUF file." }
        GgufValidator.validate(temporary!!).getOrElse { error ->
            throw IllegalArgumentException("The selected file is not a supported GGUF artifact: ${error.message}", error)
        }
        val metadata = GgufMetadataReader.read(temporary!!).getOrElse { error ->
            throw IllegalArgumentException("Dora could not read GGUF metadata safely: ${error.message}", error)
        }

        val hash = sha256(temporary!!)
        val id = "import-${hash.take(16)}"
        val finalFile = File(modelDirectory, "$id.gguf")
        if (finalFile.exists()) finalFile.delete()
        check(temporary!!.renameTo(finalFile)) { "Dora could not finalize the imported model." }
        temporary = null

        ImportedArtifact(
            id = id,
            name = displayName,
            path = finalFile.absolutePath,
            sizeBytes = finalFile.length(),
            sha256 = hash,
            metadata = metadata,
        )
    }.onFailure {
        temporary?.delete()
    }
    }

    fun importGgufFolder(treeUri: Uri): Result<FolderImportSummary> = runCatching {
        val rootDocumentId = DocumentsContract.getTreeDocumentId(treeUri)
        val candidates = mutableListOf<FolderCandidate>()
        val visited = mutableSetOf<String>()

        fun walk(parentDocumentId: String, depth: Int) {
            require(depth <= MAX_FOLDER_DEPTH) { "The selected folder is nested too deeply for safe import." }
            if (!visited.add(parentDocumentId)) return
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
            val projection = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_SIZE,
            )
            context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE)
                val sizeIndex = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_SIZE)
                while (cursor.moveToNext()) {
                    val documentId = cursor.getString(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty()
                    val mime = cursor.getString(mimeIndex).orEmpty()
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        walk(documentId, depth + 1)
                    } else if (name.lowercase().endsWith(".gguf")) {
                        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else -1L
                        candidates += FolderCandidate(
                            uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                            name = name,
                            sizeBytes = size,
                        )
                        require(candidates.size <= MAX_FOLDER_FILES) { "A single folder import is limited to $MAX_FOLDER_FILES GGUF files." }
                    }
                }
            }
        }

        walk(rootDocumentId, 0)
        require(candidates.isNotEmpty()) { "No GGUF files were found in the selected folder." }
        val knownBytes = candidates.map { it.sizeBytes }.filter { it > 0L }.sum()
        require(knownBytes <= MAX_FOLDER_BYTES) { "The selected folder exceeds Dora’s aggregate import limit." }
        if (knownBytes > 0L) {
            val available = StatFs(modelDirectory.path).availableBytes
            require(available >= knownBytes + STORAGE_HEADROOM_BYTES) { "Not enough private storage for this folder import." }
        }

        val imported = mutableListOf<ImportedArtifact>()
        val errors = mutableListOf<String>()
        candidates.forEach { candidate ->
            importGguf(candidate.uri).onSuccess(imported::add).onFailure { error ->
                errors += "${candidate.name}: ${error.message ?: "invalid GGUF"}"
            }
        }
        require(imported.isNotEmpty()) { errors.firstOrNull() ?: "No valid GGUF files could be imported." }
        FolderImportSummary(imported = imported, skipped = candidates.size - imported.size, errors = errors)
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

    private data class FolderCandidate(val uri: Uri, val name: String, val sizeBytes: Long)

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
        const val MAX_FOLDER_FILES = 20
        const val MAX_FOLDER_DEPTH = 4
        const val MAX_FOLDER_BYTES = 8L * 1024L * 1024L * 1024L
        const val STORAGE_HEADROOM_BYTES = 512L * 1024L * 1024L
    }
}
