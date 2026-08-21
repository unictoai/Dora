package app.dora.localai.data

import android.content.Context
import android.net.Uri
import app.dora.localai.domain.LocalDocument
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class LocalDocumentRepository(
    private val context: Context,
    private val dao: DoraDao,
) {
    suspend fun allDocuments(): List<LocalDocument> = withContext(Dispatchers.IO) {
        dao.allDocuments().map { it.toDomain() }
    }

    suspend fun allChunks(): List<DocumentChunkRecord> = withContext(Dispatchers.IO) {
        dao.allDocumentChunks()
    }

    suspend fun import(uri: Uri): Result<LocalDocument> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val mimeType = resolver.getType(uri).orEmpty().ifBlank { "text/plain" }
            require(isSupportedTextType(mimeType)) {
                "Dora currently indexes text, Markdown, CSV, and JSON files. PDF and binary document indexing is not enabled yet."
            }
            val staging = documentsDir().resolve(".${UUID.randomUUID()}.part")
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            resolver.openInputStream(uri)?.use { input ->
                staging.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        totalBytes += count
                        require(totalBytes <= MAX_DOCUMENT_BYTES) { "Document is larger than Dora’s ${MAX_DOCUMENT_BYTES / (1024 * 1024)} MB local indexing limit." }
                        digest.update(buffer, 0, count)
                        output.write(buffer, 0, count)
                    }
                }
            } ?: error("Dora could not read the selected document")
            require(totalBytes > 0L) { "The selected document is empty." }
            val sha256 = digest.digest().toHex()
            val id = "doc-${sha256.take(24)}"
            val finalFile = documentsDir().resolve("$id.txt")
            if (finalFile.exists()) staging.delete() else staging.renameTo(finalFile).also { renamed -> if (!renamed) error("Dora could not finalize the document copy") }
            val text = finalFile.readText(Charsets.UTF_8).replace("\u0000", " ")
            val chunks = DocumentTextChunker.chunk(text)
            require(chunks.isNotEmpty()) { "Dora could not extract readable text from the selected document." }
            dao.deleteDocumentChunks(id)
            dao.upsertDocumentChunks(chunks.mapIndexed { index, chunkText ->
                DocumentChunkRecord(
                    id = "$id-$index",
                    documentId = id,
                    ordinal = index,
                    text = chunkText,
                    searchableText = DocumentTextChunker.normalizeForSearch(chunkText),
                )
            })
            val now = System.currentTimeMillis()
            val record = DocumentRecord(id, displayName(uri), mimeType, totalBytes, sha256, chunks.size, now, now, true, null)
            dao.upsertDocument(record)
            record.toDomain()
        }.onFailure {
            context.filesDir.resolve("documents").listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        }
    }

    suspend fun delete(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            dao.deleteDocumentChunks(id)
            dao.deleteDocument(id)
            documentsDir().resolve("$id.txt").delete()
            Unit
        }
    }

    private fun documentsDir(): File = context.filesDir.resolve("documents").also { it.mkdirs() }

    private fun displayName(uri: Uri): String = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Local document"

    private fun isSupportedTextType(mimeType: String): Boolean = mimeType.startsWith("text/") || mimeType == "application/json" || mimeType == "application/xml"

    private fun DocumentRecord.toDomain() = LocalDocument(id, name, mimeType, sizeBytes, sha256, chunkCount, createdAt, updatedAt, enabled, errorMessage)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_DOCUMENT_BYTES = 20L * 1024L * 1024L
    }
}
