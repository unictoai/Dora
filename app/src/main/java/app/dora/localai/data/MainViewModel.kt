package app.dora.localai.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
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
import app.dora.localai.engine.NativeLlamaEngine
import app.dora.localai.engine.NativeLlamaTextEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val deviceSummary: String = "Device profile pending",
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = LocalRegistry(application)
    private val modelStore = LocalModelStore(application)
    private val deviceProfile = DeviceProfile(application)
    private val textEngine = DoraDemoTextEngine()
    private val nativeTextEngine = NativeLlamaTextEngine()
    private val imageEngine = DoraDemoImageEngine()
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<DoraUiState> = _uiState.asStateFlow()

    private fun initialState(): DoraUiState {
        val installed = registry.installedModelIds()
        return DoraUiState(
            models = listOf(starterTextModel, imageModel).map { model ->
                val artifact = registry.artifact(model.id)
                if (model.id in installed) model.copy(
                    name = artifact?.name ?: model.name,
                    installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                    verified = model.kind == app.dora.localai.domain.ModelKind.TEXT,
                    filePath = artifact?.path,
                    sizeLabel = artifact?.sizeBytes?.let { formatBytes(it) } ?: model.sizeLabel,
                ) else model
            },
            isOfflineOnly = registry.isOfflineOnly(),
            runtimeNotice = if (NativeLlamaEngine.isAvailable()) "Native llama.cpp bridge loaded — import a GGUF model to enable real inference" else "Native llama.cpp bridge unavailable in this build",
            deviceSummary = "${deviceProfile.primaryAbi} • ${formatBytes(deviceProfile.totalRamBytes)} RAM • ${formatBytes(deviceProfile.availableStorageBytes)} free",
        )
    }

    init {
        val conversation = _uiState.value.conversations.first()
        _uiState.update { it.copy(activeConversationId = conversation.id) }
    }

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab, toastMessage = null) }

    fun setComposerText(value: String) = _uiState.update { it.copy(composerText = value) }

    fun setImagePrompt(value: String) = _uiState.update { it.copy(imagePrompt = value) }

    fun toggleOfflineOnly() {
        val value = !_uiState.value.isOfflineOnly
        registry.setOfflineOnly(value)
        _uiState.update { it.copy(isOfflineOnly = value) }
    }

    fun clearAllLocalData() {
        registry.clearAll()
        val freshConversation = Conversation(title = "New local conversation")
        _uiState.value = DoraUiState(
            conversations = listOf(freshConversation),
            activeConversationId = freshConversation.id,
            toastMessage = "Local data deleted",
        )
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun importGguf(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { modelStore.importGguf(uri) }
            val artifact = result.getOrNull()
            if (artifact == null) {
                _uiState.update { it.copy(toastMessage = result.exceptionOrNull()?.message ?: "GGUF import failed") }
                return@launch
            }
            val nativeValid = withContext(Dispatchers.Default) {
                NativeLlamaEngine.isAvailable() && NativeLlamaEngine.validateModel(artifact.path)
            }
            if (!nativeValid) {
                modelStore.delete(artifact.path)
                _uiState.update { it.copy(toastMessage = "Dora could not load this GGUF on this ARM64 device") }
                return@launch
            }
            registry.setArtifact(LocalRegistry.StoredArtifact(starterTextModel.id, artifact.name, artifact.path, artifact.sizeBytes, artifact.sha256))
            _uiState.update { state ->
                state.copy(models = state.models.map { model ->
                    if (model.id == starterTextModel.id) model.copy(
                        name = artifact.name,
                        installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                        verified = true,
                        filePath = artifact.path,
                        sizeLabel = formatBytes(artifact.sizeBytes),
                    ) else model
                }, toastMessage = "Model ready: ${artifact.name}")
            }
        }
    }

    fun installModel(modelId: String) {
        registry.setModelInstalled(modelId, true)
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
        modelStore.delete(_uiState.value.models.firstOrNull { it.id == modelId }?.filePath)
        registry.setModelInstalled(modelId, false)
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
            try {
                val conversation = _uiState.value.conversations.first { it.id == conversationId }
                val activeModel = _uiState.value.models.firstOrNull { it.kind == app.dora.localai.domain.ModelKind.TEXT && it.filePath != null }
                val engine = if (activeModel != null && nativeTextEngine.isProductionReady) nativeTextEngine else textEngine
                var accumulated = ""
                engine.streamReply(activeModel ?: starterTextModel, conversation.messages).collect { token ->
                    accumulated += token
                    updateLastAssistant(conversationId, accumulated, true)
                }
                updateLastAssistant(conversationId, accumulated, false)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                updateLastAssistant(conversationId, "Dora could not complete this local generation: ${error.message ?: "native runtime error"}", false)
                _uiState.update { it.copy(toastMessage = "Local generation failed") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    fun stopGeneration() {
        NativeLlamaEngine.cancel()
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

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f KB".format(bytes / 1024.0)
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
