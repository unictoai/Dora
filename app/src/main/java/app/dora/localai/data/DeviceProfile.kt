package app.dora.localai.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs

class DeviceProfile(context: Context) {
    private val activityManager = context.getSystemService(ActivityManager::class.java)
    private val statFs = StatFs(context.filesDir.absolutePath)
    private val memoryInfo = ActivityManager.MemoryInfo().also { info -> activityManager?.getMemoryInfo(info) }

    val totalRamBytes: Long
        get() = memoryInfo.totalMem

    val availableStorageBytes: Long
        get() = statFs.availableBytes

    val primaryAbi: String
        get() = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"

    val isArm64: Boolean
        get() = primaryAbi == "arm64-v8a"

    fun textFit(requiredBytes: Long): FitResult {
        val available = availableStorageBytes
        val ram = totalRamBytes
        return when {
            !isArm64 -> FitResult("Unsupported ABI", "Dora’s native inference target requires ARM64.", false)
            available < requiredBytes -> FitResult("Not enough storage", "Free ${formatBytes(requiredBytes - available)} before installing this model.", false)
            ram < requiredBytes * 2 -> FitResult("High memory risk", "This model may trigger thermal or memory pressure.", false)
            else -> FitResult("Likely compatible", "ARM64 • ${formatBytes(ram)} RAM • ${formatBytes(available)} free", true)
        }
    }

    data class FitResult(val label: String, val explanation: String, val allowed: Boolean)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.0f KB".format(bytes / 1024.0)
    }
}
