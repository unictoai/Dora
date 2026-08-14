package app.dora.localai.data

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.Conversation
import app.dora.localai.domain.DoraJob
import app.dora.localai.domain.JobKind
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.MessageRole
import app.dora.localai.engine.DoraDemoImageEngine
import app.dora.localai.engine.DoraDemoTextEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val starterTextModel = LocalModel(
    id = "dora-starter-gguf",
    name = "Dora Starter 1.5B",
    publisher = "Dora verified catalog",
    kind = app.dora.localai.domain.ModelKind.TEXT,
    format = "GGUF / Q4_K_M",
    sizeLabel = "~1.1 GB",
    memoryLabel = "Recommended for 6 GB+ RAM",
    license = "Model license displayed before download",
    description = "A small starter model placeholder for the native llama.cpp integration.",
    installState = app.dora.localai.domain.ModelInstallState.AVAILABLE,
    verified = true,
    recommended = true,
)

private val imageModel = LocalModel(
    id = "dora-image-starter",
    name = "Dora Image Starter",
    publisher = "Dora verified catalog",
    kind = app.dora.localai.domain.ModelKind.IMAGE,
    format = "Runtime-specific diffusion bundle",
    sizeLabel = "Size shown after runtime selection",
    memoryLabel = "Use conservative presets",
    license = "Model license displayed before download",
    description = "Image generation integration point; no weights are bundled in this build.",
    installState = app.dora.localai.domain.ModelInstallState.AVAILABLE,
    verified = false,
    recommended = false,
)

data class DoraUiState(
    val selectedTab: Int = 0,
    val models: List<LocalModel> = listOf(starterTextModel, imageModel),
    val conversations: List<Conversation> = listOf(Conversation(title = "New local conversation")),
    val activeConversationId: String = "",
    val composerText: String = "",
    val imagePrompt: String = "",
    val jobs: List<DoraJob> = emptyList(),
    val isOfflineOnly: Boolean = true,
    val isGenerating: Boolean = false,
    val toastMessage: String? = null,
    val runtimeNotice: String = "Demo adapter active — native llama.cpp bridge is next",
)

class MainViewModel : ViewModel() {
    private val textEngine = DoraDemoTextEngine()
    private val imageEngine = DoraDemoImageEngine()
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(DoraUiState())
    val uiState: StateFlow<DoraUiState> = _uiState.asStateFlow()

    init {
        val conversation = _uiState.value.conversations.first()
        _uiState.update { it.copy(activeConversationId = conversation.id) }
    }

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab, toastMessage = null) }

    fun setComposerText(value: String) = _uiState.update { it.copy(composerText = value) }

    fun setImagePrompt(value: String) = _uiState.update { it.copy(imagePrompt = value) }

    fun toggleOfflineOnly() = _uiState.update { it.copy(isOfflineOnly = !it.isOfflineOnly) }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun installModel(modelId: String) {
        _uiState.update { state ->
            state.copy(models = state.models.map { model ->
                if (model.id == modelId) model.copy(
                    installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                    verified = model.kind == app.dora.localai.domain.ModelKind.TEXT,
                ) else model
            }, toastMessage = "Model added to Dora’s local registry")
        }
    }

    fun deleteModel(modelId: String) {
        _uiState.update { state ->
            state.copy(models = state.models.map { model ->
                if (model.id == modelId) model.copy(installState = app.dora.localai.domain.ModelInstallState.AVAILABLE) else model
            }, toastMessage = "Model removed from Dora")
        }
    }

    fun sendMessage() {
        val prompt = _uiState.value.composerText.trim()
        if (prompt.isBlank() || _uiState.value.isGenerating) return
        val conversationId = _uiState.value.activeConversationId
        val userMessage = ChatMessage(role = MessageRole.USER, text = prompt)
        val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, text = "", isPartial = true)
        updateConversation(conversationId) { conversation ->
            conversation.copy(messages = conversation.messages + userMessage + assistantMessage)
        }
        _uiState.update { it.copy(composerText = "", isGenerating = true, toastMessage = null) }

        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            val conversation = _uiState.value.conversations.first { it.id == conversationId }
            var accumulated = ""
            textEngine.streamReply(starterTextModel, conversation.messages).collect { token ->
                accumulated += token
                updateLastAssistant(conversationId, accumulated, true)
            }
            updateLastAssistant(conversationId, accumulated, false)
            _uiState.update { it.copy(isGenerating = false) }
        }
    }

    fun stopGeneration() {
        generationJob?.cancel()
        generationJob = null
        _uiState.update { it.copy(isGenerating = false, toastMessage = "Generation stopped") }
        val id = _uiState.value.activeConversationId
        val conversation = _uiState.value.conversations.firstOrNull { it.id == id } ?: return
        val last = conversation.messages.lastOrNull()
        if (last?.role == MessageRole.ASSISTANT) updateLastAssistant(id, last.text, false)
    }

    fun generateImage() {
        val prompt = _uiState.value.imagePrompt.trim()
        if (prompt.isBlank()) {
            _uiState.update { it.copy(toastMessage = "Add a prompt before starting an image job") }
            return
        }
        val job = DoraJob(kind = JobKind.IMAGE, label = prompt, state = JobState.RUNNING, progress = 0.15f, message = "Validating local image runtime")
        _uiState.update { it.copy(jobs = it.jobs + job, toastMessage = null) }
        viewModelScope.launch {
            val result = imageEngine.generate(imageModel, prompt)
            _uiState.update { state ->
                state.copy(
                    jobs = state.jobs.map { current ->
                        if (current.id == job.id) current.copy(
                            state = if (result.isSuccess) JobState.COMPLETE else JobState.FAILED,
                            progress = if (result.isSuccess) 1f else 0.15f,
                            message = result.exceptionOrNull()?.message ?: "Image ready",
                        ) else current
                    },
                    toastMessage = result.exceptionOrNull()?.message ?: "Image generated locally",
                )
            }
        }
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        _uiState.update { state ->
            state.copy(conversations = state.conversations.map { conversation ->
                if (conversation.id == id) transform(conversation) else conversation
            })
        }
    }

    private fun updateLastAssistant(id: String, text: String, partial: Boolean) {
        updateConversation(id) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
            if (index >= 0) messages[index] = messages[index].copy(text = text, isPartial = partial)
            conversation.copy(messages = messages)
        }
    }
}
