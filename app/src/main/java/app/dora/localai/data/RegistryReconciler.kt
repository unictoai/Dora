package app.dora.localai.data

import android.content.Context
import java.io.File

/**
 * Reconciles the two metadata stores and the private model directory after
 * process death, app upgrade, external deletion, or an interrupted install.
 * It never deletes a user model automatically; invalid state is surfaced.
 */
class RegistryReconciler(
    context: Context,
    private val registry: LocalRegistry,
    private val dao: DoraDao,
) {
    private val modelDirectory = File(context.filesDir, "models").apply { mkdirs() }

    data class Report(
        val checkedArtifacts: Int,
        val invalidArtifacts: Int,
        val orphanFiles: Int,
        val partialFiles: Int,
        val messages: List<String>,
    )

    suspend fun reconcile(): Report {
        val messages = mutableListOf<String>()
        var invalid = 0
        var orphan = 0
        var partial = 0
        val registryArtifacts = registry.allArtifacts()
        val roomModels = dao.allModels().associateBy { it.id }
        val knownPaths = registryArtifacts.mapNotNull { it.path }.toSet() + roomModels.values.mapNotNull { it.path }

        for (artifact in registryArtifacts) {
            val file = File(artifact.path)
            val roomRecord = roomModels[artifact.id]
            val validPath = isPrivateModelFile(file)
            val sizeMatches = validPath && (artifact.sizeBytes <= 0L || file.length() == artifact.sizeBytes)
            if (!validPath || !sizeMatches) {
                registry.markArtifactInvalid(artifact.id)
                invalid += 1
                val reason = when {
                    !validPath -> "file is missing or outside Dora’s private model directory"
                    else -> "file size differs from recorded provenance"
                }
                messages += "${artifact.name}: $reason"
                if (roomRecord != null && roomRecord.verified) {
                    dao.upsertModel(roomRecord.copy(verified = false, updatedAt = System.currentTimeMillis()))
                }
            } else if (roomRecord == null) {
                dao.upsertModel(
                    ModelRecord(
                        id = artifact.id,
                        name = artifact.name,
                        kind = "TEXT",
                        format = "GGUF",
                        path = artifact.path,
                        sizeBytes = artifact.sizeBytes,
                        sha256 = artifact.sha256,
                        license = artifact.sourceLicense ?: "License recorded in provenance",
                        verified = false,
                        updatedAt = System.currentTimeMillis(),
                        sourceRepo = artifact.sourceRepo,
                        sourceFilename = artifact.sourceFilename,
                        sourceRevision = artifact.sourceRevision,
                        sourceUrl = artifact.sourceUrl,
                        sourceLicense = artifact.sourceLicense,
                    ),
                )
                messages += "${artifact.name}: restored missing database record"
            } else if (!roomRecord.verified) {
                dao.upsertModel(roomRecord.copy(verified = true, updatedAt = System.currentTimeMillis()))
                messages += "${artifact.name}: verification state repaired"
            }
        }

        for (record in roomModels.values) {
            val file = record.path?.let(::File)
            if (isPrivateModelFile(file) && file?.exists() == true && registry.artifact(record.id) == null) {
                registry.setArtifact(
                    LocalRegistry.StoredArtifact(
                        id = record.id,
                        name = record.name,
                        path = record.path,
                        sizeBytes = record.sizeBytes,
                        sha256 = record.sha256,
                        sourceRepo = record.sourceRepo,
                        sourceFilename = record.sourceFilename,
                        sourceRevision = record.sourceRevision,
                        sourceUrl = record.sourceUrl,
                        sourceLicense = record.sourceLicense,
                    ),
                )
                messages += "${record.name}: restored missing registry artifact"
            }
            if (record.path != null && (!isPrivateModelFile(file) || file?.exists() != true)) {
                registry.markArtifactInvalid(record.id)
                invalid += 1
                messages += "${record.name}: database points to a missing private file"
            }
        }

        modelDirectory.listFiles()?.forEach { file ->
            when {
                file.extension.equals("part", ignoreCase = true) -> {
                    partial += 1
                    messages += "Interrupted download retained for recovery: ${file.name}"
                }
                file.extension.equals("gguf", ignoreCase = true) && file.absolutePath !in knownPaths -> {
                    orphan += 1
                    messages += "Unregistered GGUF retained for review: ${file.name}"
                }
            }
        }

        return Report(registryArtifacts.size + roomModels.size, invalid, orphan, partial, messages.distinct())
    }

    private fun isPrivateModelFile(file: File?): Boolean {
        if (file == null) return false
        return runCatching {
            file.canonicalFile.parentFile?.canonicalFile == modelDirectory.canonicalFile && file.name.endsWith(".gguf", ignoreCase = true)
        }.getOrDefault(false)
    }
}
