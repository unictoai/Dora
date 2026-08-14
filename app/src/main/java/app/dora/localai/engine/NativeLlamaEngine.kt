package app.dora.localai.engine

import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.LocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** JNI facade for the real llama.cpp Android library. */
object NativeLlamaEngine {
    private var loaded = false

    fun isAvailable(): Boolean = try {
        if (!loaded) {
            System.loadLibrary("dora_native")
            loaded = true
        }
        true
    } catch (_: UnsatisfiedLinkError) {
        false
    }

    fun version(): String = nativeVersion()

    fun validateModel(path: String): Boolean = nativeValidateModel(path)

    fun cancel() {
        if (isAvailable()) nativeCancel()
    }

    fun generate(path: String, prompt: String, maxTokens: Int = 256, threads: Int = 4): String =
        nativeGenerate(path, prompt, maxTokens, threads)

    private external fun nativeVersion(): String
    private external fun nativeValidateModel(path: String): Boolean
    private external fun nativeCancel()
    private external fun nativeGenerate(path: String, prompt: String, maxTokens: Int, threads: Int): String
}

class NativeStableDiffusionImageEngine : ImageInferenceEngine {
    override val displayName: String = "stable-diffusion.cpp isolated native"
    override val isProductionReady: Boolean
        get() = NativeLlamaEngine.isAvailable()

    override suspend fun generate(model: LocalModel, prompt: String): Result<String> =
        Result.failure(UnsupportedOperationException("An output path and validated image model bundle are required."))
}

class NativeLlamaTextEngine : TextInferenceEngine {
    override val displayName: String = "llama.cpp / GGUF native"
    override val isProductionReady: Boolean
        get() = NativeLlamaEngine.isAvailable()

    override fun streamReply(model: LocalModel, history: List<ChatMessage>): Flow<String> = flow<String> {
        val path = model.filePath ?: throw UnsupportedOperationException("A validated GGUF file path is required.")
        val prompt = history.joinToString("\\n") { message ->
            val role = if (message.role.name == "USER") "User" else "Assistant"
            "$role: ${message.text}"
        }
        emit(NativeLlamaEngine.generate(path = path, prompt = prompt))
    }.flowOn(Dispatchers.Default)
}
