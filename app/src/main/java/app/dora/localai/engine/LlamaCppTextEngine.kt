package app.dora.localai.engine

import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.GenerationSettings
import app.dora.localai.domain.LocalModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Production adapter boundary for the future pinned llama.cpp/GGUF JNI bridge.
 *
 * The UI and domain layers depend only on [TextInferenceEngine]. This keeps
 * native pointers, ABI decisions, model loading, cancellation, and JNI errors
 * out of Compose code. The adapter intentionally fails loudly until the native
 * runtime is added and validated on real Android devices.
 */
class LlamaCppTextEngine : TextInferenceEngine {
    override val displayName: String = "llama.cpp / GGUF"
    override val isProductionReady: Boolean = false

    override fun streamReply(model: LocalModel, history: List<ChatMessage>, settings: GenerationSettings): Flow<String> = flow {
        throw UnsupportedOperationException(
            "The llama.cpp JNI bridge is not bundled yet. Run the Dora feasibility benchmark before enabling this adapter."
        )
    }
}
