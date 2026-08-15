package app.dora.localai.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.StatFs
import app.dora.localai.domain.DeviceFit
import app.dora.localai.domain.DeviceFitLevel

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

    fun fitForModel(modelBytes: Long): DeviceFit {
        val storageRequired = modelBytes + (512L * 1024L * 1024L)
        val memoryRequired = (modelBytes * 1.8).toLong()
        return when {
            !isArm64 -> DeviceFit(DeviceFitLevel.UNSUPPORTED, "ARM64 required", "This build runs native inference on arm64-v8a devices only.", false)
            availableStorageBytes < storageRequired -> DeviceFit(DeviceFitLevel.TOO_HEAVY, "Not enough storage", "Keep at least ${formatBytes(storageRequired - availableStorageBytes)} free for a safe download and model staging.", false)
            totalRamBytes < memoryRequired -> DeviceFit(DeviceFitLevel.TOO_HEAVY, "High memory risk", "This model may cause memory pressure; choose a smaller quantization.", false)
            totalRamBytes >= modelBytes * 2.4 && availableStorageBytes >= modelBytes * 2.5 -> DeviceFit(DeviceFitLevel.RECOMMENDED, "Recommended for this device", "Fits the measured RAM and storage budget with headroom.", true)
            else -> DeviceFit(DeviceFitLevel.POSSIBLE, "Possible with caution", "This model may work, but expect slower loading or thermal pressure.", true)
        }
    }

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
