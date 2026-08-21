package app.dora.localai.engine

import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.GenerationSettings
import app.dora.localai.domain.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.concurrent.Semaphore

/** JNI facade for the real llama.cpp Android library. */
object NativeLlamaEngine {
    private val loadLock = Any()
    private val inferenceGate = Semaphore(1, true)

    @Volatile
    private var loaded = false

    fun isAvailable(): Boolean {
        if (loaded) return true
        synchronized(loadLock) {
            if (loaded) return true
            return try {
                System.loadLibrary("dora_native")
                loaded = true
                true
            } catch (_: UnsatisfiedLinkError) {
                false
            }
        }
    }

    fun version(): String {
        check(isAvailable()) { "The native llama.cpp library is unavailable on this device." }
        return nativeVersion()
    }

    fun validateModel(path: String): Boolean {
        if (path.isBlank() || !isAvailable()) return false
        if (!inferenceGate.tryAcquire()) return false
        return try {
            nativeValidateModel(path)
        } catch (_: IllegalArgumentException) {
            false
        } finally {
            inferenceGate.release()
        }
    }

    /** Requests cancellation without acquiring the generation gate. */
    fun cancel() {
        if (loaded) {
            try {
                nativeCancel()
            } catch (_: IllegalStateException) {
                // The native bridge may already be shutting down; cancellation is best effort.
            }
        }
    }

    fun generate(
        path: String,
        prompt: String,
        maxTokens: Int = 256,
        threads: Int = 4,
        temperature: Float = 0.7f,
        topK: Int = 40,
        topP: Float = 0.95f,
    ): String {
        check(path.isNotBlank()) { "A validated model path is required." }
        check(prompt.isNotBlank()) { "A non-empty prompt is required." }
        check(isAvailable()) { "The native llama.cpp library is unavailable on this device." }
        check(inferenceGate.tryAcquire()) { "Dora is already running one local inference job." }
        return try {
            nativeGenerate(
                path,
                prompt,
                maxTokens.coerceIn(16, 4096),
                threads.coerceIn(1, 16),
                temperature.coerceIn(0.05f, 2.0f),
                topK.coerceIn(1, 256),
                topP.coerceIn(0.05f, 1.0f),
            )
        } finally {
            inferenceGate.release()
        }
    }

    private external fun nativeVersion(): String
    private external fun nativeValidateModel(path: String): Boolean
    private external fun nativeCancel()
    private external fun nativeGenerate(path: String, prompt: String, maxTokens: Int, threads: Int, temperature: Float, topK: Int, topP: Float): String
}

class NativeStableDiffusionImageEngine : ImageInferenceEngine {
    override val displayName: String = "stable-diffusion.cpp isolated native"
    override val isProductionReady: Boolean = false

    override suspend fun generate(model: LocalModel, prompt: String): Result<String> =
        Result.failure(UnsupportedOperationException("An output path and validated image model bundle are required."))
}

class NativeLlamaTextEngine : TextInferenceEngine {
    override val displayName: String = "llama.cpp / GGUF native"
    override val isProductionReady: Boolean
        get() = NativeLlamaEngine.isAvailable()

    override fun streamReply(model: LocalModel, history: List<ChatMessage>, settings: GenerationSettings): Flow<String> = flow {
        val path = model.filePath ?: throw UnsupportedOperationException("A validated GGUF file path is required.")
        val normalized = settings.normalized()
        val prompt = buildString {
            if (normalized.systemPrompt.isNotBlank()) append("System: ${normalized.systemPrompt}\n")
            history.joinTo(this, separator = "\n") { message ->
                val role = when (message.role) {
                    app.dora.localai.domain.MessageRole.USER -> "User"
                    app.dora.localai.domain.MessageRole.ASSISTANT -> "Assistant"
                    app.dora.localai.domain.MessageRole.SYSTEM -> "System"
                }
                "$role: ${message.text}"
            }
        }
        emit(NativeLlamaEngine.generate(
            path = path,
            prompt = prompt,
            maxTokens = normalized.maxTokens,
            threads = normalized.threads,
            temperature = normalized.temperature,
            topK = normalized.topK,
            topP = normalized.topP,
        ))
    }.flowOn(Dispatchers.Default)
}
