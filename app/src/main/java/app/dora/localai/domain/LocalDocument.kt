package app.dora.localai.domain

data class LocalDocument(
    val id: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val sha256: String,
    val chunkCount: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val enabled: Boolean = true,
    val errorMessage: String? = null,
)
