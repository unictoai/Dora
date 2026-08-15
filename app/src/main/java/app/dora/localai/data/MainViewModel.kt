package app.dora.localai.data

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.DeviceFitLevel
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.HuggingFaceFileCandidate
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
    val huggingFaceQuery: String = "",
    val huggingFaceCandidates: List<HuggingFaceCandidate> = emptyList(),
    val isSearchingHuggingFace: Boolean = false,
    val activeDownloadId: String? = null,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = LocalRegistry(application)
    private val modelStore = LocalModelStore(application)
    private val deviceProfile = DeviceProfile(application)
    private val huggingFaceClient = HuggingFaceClient(deviceProfile)
    private val modelDownloadManager = ModelDownloadManager(application)
    private val textEngine = DoraDemoTextEngine()
    private val nativeTextEngine = NativeLlamaTextEngine()
    private val imageEngine = DoraDemoImageEngine()
    private var generationJob: Job? = null

    private val _uiState = MutableStateFlow(initialState())
    val uiState: StateFlow<DoraUiState> = _uiState.asStateFlow()

    private fun initialState(): DoraUiState {
        val installed = registry.installedModelIds()
        val builtInIds = setOf(starterTextModel.id, imageModel.id)
        val builtInModels = listOf(starterTextModel, imageModel).map { model ->
            val artifact = registry.artifact(model.id)
            if (model.id in installed) model.copy(
                name = artifact?.name ?: model.name,
                publisher = artifact?.sourceRepo ?: model.publisher,
                license = artifact?.sourceLicense ?: model.license,
                installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                verified = model.kind == app.dora.localai.domain.ModelKind.TEXT,
                filePath = artifact?.path,
                sizeLabel = artifact?.sizeBytes?.let { formatBytes(it) } ?: model.sizeLabel,
            ) else model
        }
        val downloadedModels = registry.allArtifacts().filterNot { it.id in builtInIds }.map { artifact ->
            LocalModel(
                id = artifact.id,
                name = artifact.name,
                publisher = artifact.sourceRepo ?: "Local model",
                kind = app.dora.localai.domain.ModelKind.TEXT,
                format = "GGUF",
                sizeLabel = formatBytes(artifact.sizeBytes),
                memoryLabel = "Imported from Hugging Face",
                license = artifact.sourceLicense ?: "License recorded in provenance",
                description = artifact.sourceRevision?.let { "Hugging Face revision ${it.take(12)}" } ?: "Validated local model",
                installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                verified = true,
                recommended = false,
                filePath = artifact.path,
            )
        }
        return DoraUiState(
            models = builtInModels + downloadedModels,
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

    fun setHuggingFaceQuery(value: String) = _uiState.update { it.copy(huggingFaceQuery = value) }

    fun browseHuggingFace() {
        val query = _uiState.value.huggingFaceQuery.trim().ifBlank { "gguf" }
        _uiState.update { it.copy(isSearchingHuggingFace = true, toastMessage = null) }
        viewModelScope.launch {
            val result = huggingFaceClient.search(query)
            _uiState.update {
                result.fold(
                    onSuccess = { candidates -> it.copy(huggingFaceCandidates = candidates, isSearchingHuggingFace = false, toastMessage = if (candidates.isEmpty()) "No public GGUF repositories found" else null) },
                    onFailure = { error -> it.copy(isSearchingHuggingFace = false, toastMessage = error.message ?: "Hugging Face search failed") },
                )
            }
        }
    }

    fun downloadHuggingFace(file: HuggingFaceFileCandidate, candidate: HuggingFaceCandidate) {
        if (candidate.gated) {
            _uiState.update { it.copy(toastMessage = "This repository is gated. Open it on Hugging Face to request access.") }
            return
        }
        if (!file.deviceFit.allowed) {
            _uiState.update { it.copy(toastMessage = file.deviceFit.explanation) }
            return
        }
        if (_uiState.value.activeDownloadId != null) return
        val modelId = "hf-${file.repoId}-${file.filename}".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val model = LocalModel(
            id = modelId,
            name = file.filename,
            publisher = file.repoId,
            kind = app.dora.localai.domain.ModelKind.TEXT,
            format = "${file.quantization} GGUF",
            sizeLabel = formatBytes(file.sizeBytes),
            memoryLabel = file.deviceFit.label,
            license = file.license,
            description = "Downloaded from Hugging Face at revision ${file.revision.take(12)}.",
            installState = app.dora.localai.domain.ModelInstallState.AVAILABLE,
            verified = false,
            recommended = file.deviceFit.level == DeviceFitLevel.RECOMMENDED,
        )
        val job = DoraJob(kind = JobKind.DOWNLOAD, label = file.filename, state = JobState.RUNNING, message = "Starting secure download")
        _uiState.update { it.copy(jobs = it.jobs + job, activeDownloadId = modelId, toastMessage = null) }
        viewModelScope.launch {
            val result = modelDownloadManager.download(
                ModelDownloadManager.DownloadManifest(model, file.downloadUrl, file.sha256, file.sizeBytes),
            ) { bytes, total ->
                val progress = if (total != null && total > 0) (bytes.toFloat() / total).coerceIn(0f, 1f) else 0f
                updateDownloadJob(job.id, progress, "Downloading ${formatBytes(bytes)}")
            }
            val fileResult = result.getOrNull()
            val nativeValid = fileResult?.let { withContext(Dispatchers.Default) { NativeLlamaEngine.validateModel(it.absolutePath) } } == true
            if (!nativeValid) fileResult?.delete()
            val finalResult = if (nativeValid) result else Result.failure(IllegalStateException("Dora could not load this GGUF on this ARM64 device"))
            finalResult.fold(
                onSuccess = { downloaded ->
                    registry.setArtifact(LocalRegistry.StoredArtifact(modelId, file.filename, downloaded.absolutePath, downloaded.length(), file.sha256.orEmpty(), file.repoId, file.filename, file.revision, file.downloadUrl, file.license))
                    _uiState.update { state ->
                        val installed = model.copy(installState = app.dora.localai.domain.ModelInstallState.INSTALLED, verified = true, filePath = downloaded.absolutePath)
                        state.copy(models = (state.models.filterNot { it.id == modelId } + installed), activeDownloadId = null, toastMessage = "Model ready: ${file.filename}")
                    }
                    updateDownloadJob(job.id, 1f, "Validated and ready for local chat", JobState.COMPLETE)
                },
                onFailure = { error ->
                    updateDownloadJob(job.id, 0f, error.message ?: "Model download failed", JobState.FAILED)
                    _uiState.update { it.copy(activeDownloadId = null, toastMessage = error.message ?: "Model download failed") }
                },
            )
        }
    }

    private fun updateDownloadJob(id: String, progress: Float, message: String, state: JobState = JobState.RUNNING) {
        _uiState.update { current -> current.copy(jobs = current.jobs.map { job -> if (job.id == id) job.copy(progress = progress, message = message, state = state) else job }) }
    }

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
