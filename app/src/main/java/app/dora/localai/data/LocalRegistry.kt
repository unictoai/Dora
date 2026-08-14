package app.dora.localai.data

import android.content.Context

class LocalRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("dora_registry", Context.MODE_PRIVATE)

    fun isOfflineOnly(): Boolean = prefs.getBoolean(KEY_OFFLINE_ONLY, true)

    fun setOfflineOnly(value: Boolean) {
        prefs.edit().putBoolean(KEY_OFFLINE_ONLY, value).apply()
    }

    fun installedModelIds(): Set<String> = prefs.getStringSet(KEY_INSTALLED_MODELS, emptySet()).orEmpty()

    fun setModelInstalled(modelId: String, installed: Boolean) {
        val ids = installedModelIds().toMutableSet()
        if (installed) ids.add(modelId) else ids.remove(modelId)
        prefs.edit().putStringSet(KEY_INSTALLED_MODELS, ids).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_OFFLINE_ONLY = "offline_only"
        const val KEY_INSTALLED_MODELS = "installed_models"
    }
}
