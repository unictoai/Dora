package app.dora.localai.domain

import java.util.Locale

enum class DeviceFitLevel { RECOMMENDED, POSSIBLE, TOO_HEAVY, UNSUPPORTED }

data class DeviceFit(
    val level: DeviceFitLevel,
    val label: String,
    val explanation: String,
    val allowed: Boolean,
)

data class HuggingFaceFileCandidate(
    val id: String,
    val repoId: String,
    val filename: String,
    val revision: String,
    val sizeBytes: Long,
    val sha256: String?,
    val license: String,
    val downloadUrl: String,
    val deviceFit: DeviceFit,
) {
    val quantization: String
        get() = Regex("(?i)(IQ\\d+_[A-Z]+|Q\\d+_[A-Z0-9_]+|F16|BF16|Q8_0|Q4_0)")
            .find(filename)
            ?.value
            ?.uppercase(Locale.US)
            ?: "GGUF"
}

data class HuggingFaceCandidate(
    val repoId: String,
    val displayName: String,
    val author: String,
    val description: String,
    val license: String,
    val revision: String,
    val downloads: Long,
    val gated: Boolean,
    val files: List<HuggingFaceFileCandidate>,
) {
    val recommendedFile: HuggingFaceFileCandidate?
        get() = files.firstOrNull { it.deviceFit.level == DeviceFitLevel.RECOMMENDED }
            ?: files.firstOrNull { it.deviceFit.level == DeviceFitLevel.POSSIBLE }
            ?: files.firstOrNull()
}
