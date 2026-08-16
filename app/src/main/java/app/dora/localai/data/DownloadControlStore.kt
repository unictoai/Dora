package app.dora.localai.data

import android.content.Context

/** Crosses the short race between a ViewModel control action and Worker cancellation. */
class DownloadControlStore(context: Context) {
    private val prefs = context.getSharedPreferences("dora_download_controls", Context.MODE_PRIVATE)

    fun requestPause(downloadId: String) = prefs.edit().putBoolean(key(downloadId, "pause"), true).putBoolean(key(downloadId, "cancel"), false).apply()

    fun requestCancel(downloadId: String) = prefs.edit().putBoolean(key(downloadId, "cancel"), true).putBoolean(key(downloadId, "pause"), false).apply()

    fun isPauseRequested(downloadId: String): Boolean = prefs.getBoolean(key(downloadId, "pause"), false)

    fun isCancelRequested(downloadId: String): Boolean = prefs.getBoolean(key(downloadId, "cancel"), false)

    fun clear(downloadId: String) = prefs.edit().remove(key(downloadId, "pause")).remove(key(downloadId, "cancel")).apply()

    private fun key(downloadId: String, suffix: String) = "${downloadId}_$suffix"
}
