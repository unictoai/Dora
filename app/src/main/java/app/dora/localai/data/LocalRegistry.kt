package app.dora.localai.data

import android.content.Context
import app.dora.localai.domain.GenerationSettings

class LocalRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("dora_registry", Context.MODE_PRIVATE)

    data class StoredArtifact(
        val id: String,
        val name: String,
        val path: String,
        val sizeBytes: Long,
        val sha256: String,
        val sourceRepo: String? = null,
        val sourceFilename: String? = null,
        val sourceRevision: String? = null,
        val sourceUrl: String? = null,
        val sourceLicense: String? = null,
    )

    fun isOfflineOnly(): Boolean = prefs.getBoolean(KEY_OFFLINE_ONLY, true)

    fun setOfflineOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_ONLY, value).apply()
    }

    fun isIncognito(): Boolean = prefs.getBoolean(KEY_INCOGNITO, false)

    fun setIncognito(value: Boolean) {
        prefs.edit().putBoolean(KEY_INCOGNITO, value).apply()
    }

    fun retentionDays(): Int = prefs.getInt(KEY_RETENTION_DAYS, 0).coerceIn(0, 365)

    fun themeMode(): String = prefs.getString(KEY_THEME_MODE, "SYSTEM")?.uppercase()?.takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM"

    fun setThemeMode(value: String) {
        prefs.edit().putString(KEY_THEME_MODE, value.uppercase().takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM").apply()
    }

    fun saveGenerationProfile(modelId: String, name: String, settings: GenerationSettings) {
        val cleanName = name.trim().take(40)
        if (modelId.isBlank() || cleanName.isBlank()) return
        val normalized = settings.normalized()
        val value = org.json.JSONObject()
            .put("systemPrompt", normalized.systemPrompt)
            .put("maxTokens", normalized.maxTokens)
            .put("threads", normalized.threads)
            .put("temperature", normalized.temperature.toDouble())
            .put("topK", normalized.topK)
            .put("topP", normalized.topP.toDouble())
            .toString()
        prefs.edit().putString(profileKey(modelId, cleanName), value).apply()
    }

    fun generationProfiles(modelId: String): Map<String, GenerationSettings> = prefs.all
        .filterKeys { it.startsWith(profilePrefix(modelId)) }
        .mapNotNull { (key, value) ->
            val name = key.removePrefix(profilePrefix(modelId))
            val encoded = value as? String ?: return@mapNotNull null
            runCatching {
                name to GenerationSettings(
                    systemPrompt = org.json.JSONObject(encoded).optString("systemPrompt"),
                    maxTokens = org.json.JSONObject(encoded).optInt("maxTokens", 256),
                    threads = org.json.JSONObject(encoded).optInt("threads", 4),
                    temperature = org.json.JSONObject(encoded).optDouble("temperature", 0.7).toFloat(),
                    topK = org.json.JSONObject(encoded).optInt("topK", 40),
                    topP = org.json.JSONObject(encoded).optDouble("topP", 0.95).toFloat(),
                ).normalized()
            }.getOrNull()
        }
        .toMap()

    fun setRetentionDays(value: Int) {
        prefs.edit().putInt(KEY_RETENTION_DAYS, value.coerceIn(0, 365)).apply()
    }

    fun activeModelId(): String? = prefs.getString(KEY_ACTIVE_MODEL, null)

    fun setActiveModelId(modelId: String?) {
        prefs.edit().apply {
            if (modelId.isNullOrBlank()) remove(KEY_ACTIVE_MODEL) else putString(KEY_ACTIVE_MODEL, modelId)
        }.apply()
    }

    fun installedModelIds(): Set<String> = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet()).orEmpty()

    fun setModelInstalled(modelId: String, installed: Boolean) {
        val ids = installedModelIds().toMutableSet()
        if (installed) ids.add(modelId) else ids.remove(modelId)
        prefs.edit().putStringSet(KEY_INSTALLED_MODELS, ids).apply()
        if (!installed) prefs.edit().remove(artifactKey(modelId, "path")).remove(artifactKey(modelId, "name")).remove(artifactKey(modelId, "size")).remove(artifactKey(modelId, "sha256")).remove(artifactKey(modelId, "sourceRepo")).remove(artifactKey(modelId, "sourceFilename")).remove(artifactKey(modelId, "sourceRevision")).remove(artifactKey(modelId, "sourceUrl")).remove(artifactKey(modelId, "sourceLicense")).apply()
    }

    fun setArtifact(artifact: StoredArtifact) {
        prefs.edit().putStringSet(KEY_INVALID_ARTIFACTS, invalidArtifactIds() - artifact.id).apply()
        prefs.edit()
            .putStringSet(KEY_INSTALLED_MODELS, installedModelIds() + artifact.id)
            .putString(artifactKey(artifact.id, "path"), artifact.path)
            .putString(artifactKey(artifact.id, "name"), artifact.name)
            .putLong(artifactKey(artifact.id, "size"), artifact.sizeBytes)
            .putString(artifactKey(artifact.id, "sha256"), artifact.sha256)
            .putString(artifactKey(artifact.id, "sourceRepo"), artifact.sourceRepo)
            .putString(artifactKey(artifact.id, "sourceFilename"), artifact.sourceFilename)
            .putString(artifactKey(artifact.id, "sourceRevision"), artifact.sourceRevision)
            .putString(artifactKey(artifact.id, "sourceUrl"), artifact.sourceUrl)
            .putString(artifactKey(artifact.id, "sourceLicense"), artifact.sourceLicense)
            .apply()
    }

    fun allArtifacts(): List<StoredArtifact> = installedModelIds().mapNotNull(::artifact)

    fun artifactForPath(path: String): StoredArtifact? = allArtifacts().firstOrNull { it.path == path }

    fun invalidArtifactIds(): Set<String> = prefs.getStringSet(KEY_INVALID_ARTIFACTS, emptySet()).orEmpty()

    fun markArtifactInvalid(id: String) {
        prefs.edit().putStringSet(KEY_INVALID_ARTIFACTS, invalidArtifactIds() + id).apply()
    }

    fun isArtifactInvalid(id: String): Boolean = id in invalidArtifactIds()

    fun artifact(id: String): StoredArtifact? {
        val path = prefs.getString(artifactKey(id, "path"), null) ?: return null
        return StoredArtifact(
            id = id,
            name = prefs.getString(artifactKey(id, "name"), id) ?: id,
            path = path,
            sizeBytes = prefs.getLong(artifactKey(id, "size"), 0L),
            sha256 = prefs.getString(artifactKey(id, "sha256"), "") ?: "",
            sourceRepo = prefs.getString(artifactKey(id, "sourceRepo"), null),
            sourceFilename = prefs.getString(artifactKey(id, "sourceFilename"), null),
            sourceRevision = prefs.getString(artifactKey(id, "sourceRevision"), null),
            sourceUrl = prefs.getString(artifactKey(id, "sourceUrl"), null),
            sourceLicense = prefs.getString(artifactKey(id, "sourceLicense"), null),
        )
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private fun artifactKey(id: String, field: String) = "artifact_${id}_$field"
    private fun profilePrefix(modelId: String) = "profile_${modelId}_"
    private fun profileKey(modelId: String, name: String) = profilePrefix(modelId) + name

    private companion object {
        const val KEY_OFFLINE_ONLY = "offline_only"
        const val KEY_INSTALLED_MODELS = "installed_models"
        const val KEY_ACTIVE_MODEL = "active_model"
        const val KEY_INVALID_ARTIFACTS = "invalid_artifacts"
        const val KEY_INCOGNITO = "incognito"
        const val KEY_RETENTION_DAYS = "retention_days"
        const val KEY_THEME_MODE = "theme_mode"
    }
}
