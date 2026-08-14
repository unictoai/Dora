package app.dora.localai.data

import android.content.Context

class LocalRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("dora_registry", Context.MODE_PRIVATE)

    data class StoredArtifact(
        val id: String,
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val sha256: String,
    )

    fun isOfflineOnly(): Boolean = prefs.getBoolean(KEY_OFFLINE_ONLY, true)

    fun setOfflineOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_ONLY, value).apply()
    }

    fun installedModelIds(): Set<String> = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet()).orEmpty()

    fun setModelInstalled(modelId: String, installed: Boolean) {
        val ids = installedModelIds().toMutableSet()
        if (installed) ids.add(modelId) else ids.remove(modelId)
        prefs.edit().putStringSet(KEY_INSTALLED_MODELS, ids).apply()
        if (!installed) prefs.edit().remove(artifactKey(modelId, "path")).remove(artifactKey(modelId, "name")).remove(artifactKey(modelId, "size")).remove(artifactKey(modelId, "sha256")).apply()
    }

    fun setArtifact(artifact: StoredArtifact) {
        prefs.edit()
            .putStringSet(KEY_INSTALLED_MODELS, installedModelIds() + artifact.id)
            .putString(artifactKey(artifact.id, "path"), artifact.path)
            .putString(artifactKey(artifact.id, "name"), artifact.name)
            .putLong(artifactKey(artifact.id, "size"), artifact.sizeBytes)
            .putString(artifactKey(artifact.id, "sha256"), artifact.sha256)
            .apply()
    }

    fun artifact(id: String): StoredArtifact? {
        val path = prefs.getString(artifactKey(id, "path"), null) ?: return null
        return StoredArtifact(
            id = id,
            name = prefs.getString(artifactKey(id, "name"), id) ?: id,
            path = path,
            sizeBytes = prefs.getLong(artifactKey(id, "size"), 0L),
            sha256 = prefs.getString(artifactKey(id, "sha256"), "") ?: "",
        )
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun artifactKey(id: String, field: String) = "artifact_${id}_$field"

    private companion object {
        const val KEY_OFFLINE_ONLY = "offline_only"
        const val KEY_INSTALLED_MODELS = "installed_models"
    }
}
