package app.dora.localai.data

import android.app.Application
import android.net.Uri
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.dora.localai.DoraApplication
import app.dora.localai.domain.CatalogFilter
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.CuratedModelSuggestion
import app.dora.localai.domain.defaultCuratedModelSuggestions
import app.dora.localai.domain.DownloadProgress
import app.dora.localai.domain.DownloadState
import app.dora.localai.domain.DeviceFitLevel
import app.dora.localai.domain.GenerationSettings
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.InferenceMetrics
import app.dora.localai.domain.HuggingFaceFileCandidate
import app.dora.localai.domain.Conversation
import app.dora.localai.domain.DoraJob
import app.dora.localai.domain.JobKind
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalDocument
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.util.concurrent.TimeUnit

private const val MAX_DOCUMENT_CONTEXT_CHUNKS = 4
private const val MAX_CONTEXT_CHARS_PER_CHUNK = 1_200

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

enum class ConversationExportFormat { MARKDOWN, JSON }

data class DoraUiState(
    val selectedTab: Int = 0,
    val models: List<LocalModel> = listOf(starterTextModel, imageModel),
    val activeModelId: String? = null,
    val conversations: List<Conversation> = listOf(Conversation(title = "New local conversation")),
    val activeConversationId: String = "",
    val composerText: String = "",
    val imagePrompt: String = "",
    val jobs: List<DoraJob> = emptyList(),
    val isOfflineOnly: Boolean = true,
    val themeMode: String = "SYSTEM",
    val isGenerating: Boolean = false,
    val isBenchmarking: Boolean = false,
    val benchmarkResult: String? = null,
    val toastMessage: String? = null,
    val runtimeNotice: String = "Demo adapter active — native llama.cpp bridge is next",
    val nativeRuntimeVersion: String = "Unavailable",
    val deviceSummary: String = "Device profile pending",
    val huggingFaceQuery: String = "",
    val huggingFaceCandidates: List<HuggingFaceCandidate> = emptyList(),
    val catalogFilter: CatalogFilter = CatalogFilter.ALL,
    val curatedSuggestions: List<CuratedModelSuggestion> = defaultCuratedModelSuggestions,
    val isSearchingHuggingFace: Boolean = false,
    val activeDownloadId: String? = null,
    val expandedDownloadId: String? = null,
    val showConversationList: Boolean = false,
    val documents: List<LocalDocument> = emptyList(),
    val documentSearchEnabled: Boolean = true,
    val showDocumentPanel: Boolean = false,
    val privacyIncognito: Boolean = false,
    val retentionDays: Int = 0,
    val conversationSettings: Map<String, GenerationSettings> = emptyMap(),
    val savedProfiles: Map<String, Map<String, GenerationSettings>> = emptyMap(),
    val storageSummary: DoraStorageSummary = DoraStorageSummary(),
)

private val DoraUiState.activeChatSettings: GenerationSettings
    get() = conversationSettings[activeConversationId] ?: GenerationSettings()

data class DoraStorageSummary(
    val totalBytes: Long = 0L,
    val availableBytes: Long = 0L,
    val modelBytes: Long = 0L,
    val temporaryDownloadBytes: Long = 0L,
    val orphanedFileCount: Int = 0,
)

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val registry = LocalRegistry(application)
    private val dao = (application as DoraApplication).database.dao()
    private val modelStore = LocalModelStore(application)
    private val deviceProfile = DeviceProfile(application)
    private val huggingFaceClient = HuggingFaceClient(deviceProfile)
    private val workManager = WorkManager.getInstance(application)
    private val downloadControls = DownloadControlStore(application)
    private val reconciler = RegistryReconciler(application, registry, dao)
    private val documentRepository = LocalDocumentRepository(application, dao)
    private var documentChunks: List<DocumentChunkRecord> = emptyList()
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
                metadata = artifact?.path?.let { path -> GgufMetadataReader.read(File(path)).getOrNull() },
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
                metadata = GgufMetadataReader.read(File(artifact.path)).getOrNull(),
            )
        }
        val allModels = builtInModels + downloadedModels
        val restoredActiveModel = registry.activeModelId()?.takeIf { id -> allModels.any { it.id == id && it.kind == app.dora.localai.domain.ModelKind.TEXT && it.installState == app.dora.localai.domain.ModelInstallState.INSTALLED && it.verified && it.filePath != null } }
        val fallbackActiveModel = allModels.firstOrNull { it.kind == app.dora.localai.domain.ModelKind.TEXT && it.installState == app.dora.localai.domain.ModelInstallState.INSTALLED && it.verified && it.filePath != null }?.id
        return DoraUiState(
            models = allModels,
            activeModelId = restoredActiveModel ?: fallbackActiveModel,
            isOfflineOnly = registry.isOfflineOnly(),
            themeMode = registry.themeMode(),
            privacyIncognito = registry.isIncognito(),
            retentionDays = registry.retentionDays(),
            runtimeNotice = if (NativeLlamaEngine.isAvailable()) "Native llama.cpp bridge loaded — import a GGUF model to enable real inference" else "Native llama.cpp bridge unavailable in this build",
            nativeRuntimeVersion = runCatching { NativeLlamaEngine.version() }.getOrElse { "Unavailable" },
            deviceSummary = "${deviceProfile.primaryAbi} • ${formatBytes(deviceProfile.totalRamBytes)} RAM • ${formatBytes(deviceProfile.availableStorageBytes)} free",
            savedProfiles = allModels.associate { it.id to registry.generationProfiles(it.id) },
            storageSummary = storageSummary(),
        )
    }

    init {
        val conversation = _uiState.value.conversations.first()
        _uiState.update { it.copy(activeConversationId = conversation.id) }
        viewModelScope.launch(Dispatchers.IO) { loadConversations() }
        viewModelScope.launch(Dispatchers.IO) { loadDocuments() }
        viewModelScope.launch {
            dao.observeJobs().collect { records ->
                _uiState.update { state -> state.copy(jobs = records.map(::toDoraJob)) }
            }
        }
        viewModelScope.launch {
            val report = withContext(Dispatchers.IO) { reconciler.reconcile() }
            if (report.messages.isNotEmpty()) {
                _uiState.update { state ->
                    val repairedModels = state.models.map { model ->
                        val artifact = registry.artifact(model.id)
                        val valid = artifact != null && !registry.isArtifactInvalid(model.id) && File(artifact.path).exists()
                        if (artifact == null) model else model.copy(
                            installState = if (valid) app.dora.localai.domain.ModelInstallState.INSTALLED else app.dora.localai.domain.ModelInstallState.INVALID,
                            verified = valid,
                            filePath = artifact.path,
                        )
                    }
                    state.copy(models = repairedModels, toastMessage = report.messages.first())
                }
            }
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) { recoverInterruptedDownloads() }
        }
    }

    private suspend fun loadDocuments() {
        val documents = documentRepository.allDocuments()
        documentChunks = documentRepository.allChunks()
        _uiState.update { it.copy(documents = documents) }
    }

    private suspend fun recoverInterruptedDownloads() {
        val interruptedStates = setOf(
            DownloadState.STARTING,
            DownloadState.DOWNLOADING,
            DownloadState.VERIFYING,
            DownloadState.VALIDATING,
            DownloadState.INSTALLING,
            DownloadState.RETRYING,
        )
        dao.allDownloadJobs().filter { record ->
            runCatching { DownloadState.valueOf(record.downloadState ?: "") }.getOrNull() in interruptedStates
        }.forEach { record ->
            val modelId = record.modelId ?: return@forEach
            val url = record.url ?: return@forEach
            val work = runCatching { workManager.getWorkInfosForUniqueWork("download-$modelId").get() }.getOrDefault(emptyList())
            val active = work.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.BLOCKED }
            if (!active) {
                downloadControls.clear(record.downloadId ?: record.id)
                val retrying = record.copy(
                    state = JobState.RUNNING.name,
                    message = "Recovering interrupted download",
                    downloadState = DownloadState.RETRYING.name,
                    retryCount = record.retryCount + 1,
                    errorMessage = "Recovered after process interruption",
                    updatedAt = System.currentTimeMillis(),
                )
                dao.upsertJob(retrying)
                workManager.enqueueUniqueWork("download-$modelId", ExistingWorkPolicy.REPLACE, downloadRequest(retrying, url))
            }
        }
    }

    private fun toDoraJob(record: JobRecord): DoraJob {
        val kind = runCatching { JobKind.valueOf(record.kind) }.getOrDefault(JobKind.DOWNLOAD)
        val jobState = runCatching { JobState.valueOf(record.state) }.getOrDefault(JobState.FAILED)
        val download = if (kind == JobKind.DOWNLOAD) {
            val state = runCatching { DownloadState.valueOf(record.downloadState ?: "") }
                .getOrDefault(if (jobState == JobState.COMPLETE) DownloadState.COMPLETED else if (jobState == JobState.FAILED) DownloadState.FAILED else DownloadState.QUEUED)
            DownloadProgress(
                downloadId = record.downloadId ?: record.id,
                modelId = record.modelId ?: record.id,
                repositoryId = record.repositoryId,
                filename = record.filename ?: record.label,
                state = state,
                bytesDownloaded = record.bytesDownloaded,
                totalBytes = record.totalBytes,
                progressPercent = record.totalBytes?.takeIf { it > 0L }?.let { (record.bytesDownloaded * 100L / it).toInt().coerceIn(0, 100) },
                downloadSpeedBytesPerSecond = record.speedBytesPerSecond,
                elapsedTimeMillis = record.elapsedTimeMillis,
                estimatedRemainingTimeMillis = record.estimatedRemainingTimeMillis,
                startedAt = record.startedAt,
                updatedAt = record.updatedAt,
                retryCount = record.retryCount,
                errorMessage = record.errorMessage,
                isResumable = state == DownloadState.PAUSED || state == DownloadState.FAILED || state == DownloadState.RETRYING,
                isPausable = state == DownloadState.DOWNLOADING,
                isCancellable = state in setOf(DownloadState.QUEUED, DownloadState.STARTING, DownloadState.DOWNLOADING, DownloadState.PAUSED, DownloadState.RETRYING),
            )
        } else null
        return DoraJob(
            id = record.id,
            kind = kind,
            label = record.label,
            state = jobState,
            progress = record.progress,
            message = record.message,
            download = download,
        )
    }

    private suspend fun loadConversations() {
        val retentionDays = registry.retentionDays()
        if (retentionDays > 0) {
            val cutoff = System.currentTimeMillis() - retentionDays * 24L * 60L * 60L * 1_000L
            dao.deleteMessagesOlderThan(cutoff)
            dao.deleteConversationsOlderThan(cutoff)
        }
        val records = dao.allConversations()
        val restored = records.map { record ->
            Conversation(
                id = record.id,
                title = record.title,
                messages = dao.messagesForConversation(record.id).map { message ->
                    ChatMessage(
                        id = message.id,
                        role = runCatching { MessageRole.valueOf(message.role) }.getOrDefault(MessageRole.ASSISTANT),
                        text = message.text,
                        metrics = if (message.firstTokenLatencyMillis != null && message.generationTimeMillis != null && message.tokensGenerated != null && message.tokensPerSecond != null && message.contextTokenEstimate != null) InferenceMetrics(
                            firstTokenLatencyMillis = message.firstTokenLatencyMillis,
                            generationTimeMillis = message.generationTimeMillis,
                            tokensGenerated = message.tokensGenerated,
                            tokensPerSecond = message.tokensPerSecond,
                            contextTokenEstimate = message.contextTokenEstimate,
                        ).normalized() else null,
                    )
                },
            )
        }
        if (restored.isEmpty()) {
            val fresh = _uiState.value.conversations.first()
            persistConversation(fresh, GenerationSettings())
            return
        }
        val settings = records.associate { record ->
            record.id to GenerationSettings(
                systemPrompt = record.systemPrompt,
                maxTokens = record.maxTokens,
                threads = record.threads,
                temperature = record.temperature,
                topK = record.topK,
                topP = record.topP,
            ).normalized()
        }
        _uiState.update { state ->
            state.copy(
                conversations = restored,
                activeConversationId = restored.first().id,
                conversationSettings = settings,
            )
        }
    }

    private fun persistConversation(conversation: Conversation, settings: GenerationSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            if (registry.isIncognito()) return@launch
            val now = System.currentTimeMillis()
            val normalized = settings.normalized()
            dao.upsertConversation(
                ConversationRecord(
                    id = conversation.id,
                    title = conversation.title,
                    createdAt = now,
                    updatedAt = now,
                    systemPrompt = normalized.systemPrompt,
                    maxTokens = normalized.maxTokens,
                    threads = normalized.threads,
                    temperature = normalized.temperature,
                    topK = normalized.topK,
                    topP = normalized.topP,
                ),
            )
        }
    }

    fun toggleConversationList() = _uiState.update { it.copy(showConversationList = !it.showConversationList) }

    fun toggleDocumentPanel() = _uiState.update { it.copy(showDocumentPanel = !it.showDocumentPanel) }

    fun toggleDocumentSearch(enabled: Boolean) = _uiState.update { it.copy(documentSearchEnabled = enabled) }

    fun importDocument(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { documentRepository.import(uri) }
            if (result.isSuccess) {
                withContext(Dispatchers.IO) { loadDocuments() }
                _uiState.update { it.copy(toastMessage = "Document indexed locally") }
            } else {
                _uiState.update { it.copy(toastMessage = result.exceptionOrNull()?.message ?: "Document indexing failed") }
            }
        }
    }

    fun deleteDocument(id: String) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { documentRepository.delete(id) }
            if (result.isSuccess) {
                withContext(Dispatchers.IO) { loadDocuments() }
                _uiState.update { it.copy(toastMessage = "Document removed from local context") }
            } else {
                _uiState.update { it.copy(toastMessage = result.exceptionOrNull()?.message ?: "Document removal failed") }
            }
        }
    }

    fun selectConversation(id: String) {
        if (_uiState.value.conversations.any { it.id == id }) {
            _uiState.update { it.copy(activeConversationId = id, showConversationList = false) }
        }
    }

    fun createConversation() {
        val conversation = Conversation(title = "New local conversation")
        val settings = GenerationSettings()
        _uiState.update { state ->
            state.copy(
                conversations = listOf(conversation) + state.conversations,
                activeConversationId = conversation.id,
                conversationSettings = state.conversationSettings + (conversation.id to settings),
                showConversationList = false,
                toastMessage = "New private conversation",
            )
        }
        persistConversation(conversation, settings)
    }

    fun renameConversation(id: String, title: String) {
        val cleanTitle = title.trim().take(80).ifBlank { "Untitled conversation" }
        _uiState.update { state ->
            state.copy(conversations = state.conversations.map { if (it.id == id) it.copy(title = cleanTitle) else it })
        }
        val conversation = _uiState.value.conversations.firstOrNull { it.id == id } ?: return
        persistConversation(conversation, _uiState.value.conversationSettings[id] ?: GenerationSettings())
    }

    fun deleteConversation(id: String) {
        if (_uiState.value.conversations.size <= 1) {
            _uiState.update { it.copy(toastMessage = "Keep one conversation available for local chat") }
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteMessagesForConversation(id)
            dao.deleteConversation(id)
        }
        _uiState.update { state ->
            val remaining = state.conversations.filterNot { it.id == id }
            state.copy(
                conversations = remaining,
                activeConversationId = if (state.activeConversationId == id) remaining.first().id else state.activeConversationId,
                conversationSettings = state.conversationSettings - id,
                showConversationList = false,
                toastMessage = "Conversation deleted from this device",
            )
        }
    }

    fun saveActiveModelProfile(name: String, settings: GenerationSettings) {
        val modelId = _uiState.value.activeModelId ?: return
        val cleanName = name.trim().take(40)
        if (cleanName.isBlank()) return
        val normalized = settings.normalized()
        registry.saveGenerationProfile(modelId, cleanName, normalized)
        _uiState.update { state ->
            val profiles = state.savedProfiles[modelId].orEmpty() + (cleanName to normalized)
            state.copy(savedProfiles = state.savedProfiles + (modelId to profiles), toastMessage = "Saved profile: $cleanName")
        }
    }

    fun updateGenerationSettings(settings: GenerationSettings) {
        val id = _uiState.value.activeConversationId
        val normalized = settings.normalized()
        _uiState.update { state -> state.copy(conversationSettings = state.conversationSettings + (id to normalized)) }
        val conversation = _uiState.value.conversations.firstOrNull { it.id == id } ?: return
        persistConversation(conversation, normalized)
    }

    fun selectTab(tab: Int) = _uiState.update { it.copy(selectedTab = tab, toastMessage = null) }

    fun openModelDiscovery() {
        _uiState.update { it.copy(selectedTab = 1, toastMessage = null) }
    }

    fun openCuratedSuggestion(suggestion: CuratedModelSuggestion) {
        _uiState.update { it.copy(selectedTab = 1, huggingFaceQuery = suggestion.repoId, toastMessage = null) }
        browseHuggingFace()
    }

    fun toggleDownloadDetails(downloadId: String) = _uiState.update { state ->
        state.copy(expandedDownloadId = if (state.expandedDownloadId == downloadId) null else downloadId)
    }

    private fun storageSummary(): DoraStorageSummary {
        val modelsDirectory = File(getApplication<Application>().filesDir, "models")
        val files = modelsDirectory.listFiles().orEmpty()
        val modelBytes = files.filter { it.extension.equals("gguf", ignoreCase = true) }.sumOf { it.length() }
        val temporaryBytes = files.filter { it.extension.equals("part", ignoreCase = true) }.sumOf { it.length() }
        val orphanCount = files.count { it.extension.equals("gguf", ignoreCase = true) && registry.artifactForPath(it.absolutePath) == null }
        val stat = android.os.StatFs(getApplication<Application>().filesDir.path)
        return DoraStorageSummary(
            totalBytes = stat.totalBytes,
            availableBytes = stat.availableBytes,
            modelBytes = modelBytes,
            temporaryDownloadBytes = temporaryBytes,
            orphanedFileCount = orphanCount,
        )
    }

    fun setComposerText(value: String) = _uiState.update { it.copy(composerText = value) }

    fun selectActiveModel(modelId: String) {
        if (_uiState.value.isGenerating || _uiState.value.isBenchmarking) {
            _uiState.update { it.copy(toastMessage = "Stop local work before switching models") }
            return
        }
        val model = _uiState.value.models.firstOrNull { it.id == modelId }
        if (model?.kind != app.dora.localai.domain.ModelKind.TEXT || model.filePath.isNullOrBlank() || !model.verified) {
            _uiState.update { it.copy(toastMessage = "Only verified local text models can be active") }
            return
        }
        registry.setActiveModelId(modelId)
        _uiState.update { it.copy(activeModelId = modelId, toastMessage = "Active model: ${model.name}") }
    }

    fun setImagePrompt(value: String) = _uiState.update { it.copy(imagePrompt = value) }

    fun setHuggingFaceQuery(value: String) = _uiState.update { it.copy(huggingFaceQuery = value) }

    fun setCatalogFilter(filter: CatalogFilter) = _uiState.update { it.copy(catalogFilter = filter) }

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
        val requiredBytes = file.sizeBytes + maxOf(512L * 1024L * 1024L, file.sizeBytes / 10L)
        if (deviceProfile.availableStorageBytes < requiredBytes) {
            _uiState.update { it.copy(toastMessage = "Not enough private storage. Required ${formatBytes(requiredBytes)}; available ${formatBytes(deviceProfile.availableStorageBytes)}.") }
            return
        }
        if (_uiState.value.activeDownloadId != null) return
        val modelId = "hf-${file.repoId}-${file.filename}".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val existingArtifact = registry.artifact(modelId)
        if (existingArtifact != null && File(existingArtifact.path).isFile && !registry.isArtifactInvalid(modelId)) {
            registry.setActiveModelId(modelId)
            _uiState.update { it.copy(activeModelId = modelId, toastMessage = "Already installed: ${existingArtifact.name}") }
            return
        }
        if (_uiState.value.jobs.any { it.download?.modelId == modelId && it.download?.state in setOf(DownloadState.QUEUED, DownloadState.STARTING, DownloadState.DOWNLOADING, DownloadState.PAUSED, DownloadState.RETRYING, DownloadState.VERIFYING, DownloadState.VALIDATING, DownloadState.INSTALLING) }) {
            _uiState.update { it.copy(toastMessage = "This model is already in Dora’s download center.") }
            return
        }
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
        val jobId = DoraModelDownloadWorker.jobId()
        val job = DoraJob(
            id = jobId,
            kind = JobKind.DOWNLOAD,
            label = file.filename,
            state = JobState.RUNNING,
            message = "Queued for secure download",
            download = DownloadProgress(
                downloadId = jobId,
                modelId = modelId,
                repositoryId = file.repoId,
                filename = file.filename,
                state = DownloadState.QUEUED,
                bytesDownloaded = 0L,
                totalBytes = file.sizeBytes,
                progressPercent = 0,
                downloadSpeedBytesPerSecond = null,
                elapsedTimeMillis = 0L,
                estimatedRemainingTimeMillis = null,
                startedAt = null,
                updatedAt = System.currentTimeMillis(),
                retryCount = 0,
                errorMessage = null,
                isResumable = false,
                isPausable = false,
                isCancellable = true,
            ),
        )
        _uiState.update { it.copy(jobs = it.jobs + job, activeDownloadId = modelId, toastMessage = null) }
        val request = OneTimeWorkRequestBuilder<DoraModelDownloadWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
            .setInputData(
                Data.Builder()
                    .putString(DoraModelDownloadWorker.KEY_JOB_ID, job.id)
                    .putString(DoraModelDownloadWorker.KEY_MODEL_ID, modelId)
                    .putString(DoraModelDownloadWorker.KEY_MODEL_NAME, file.filename)
                    .putString(DoraModelDownloadWorker.KEY_URL, file.downloadUrl)
                    .putString(DoraModelDownloadWorker.KEY_SHA256, file.sha256.orEmpty())
                    .putLong(DoraModelDownloadWorker.KEY_BYTES, file.sizeBytes)
                    .putString(DoraModelDownloadWorker.KEY_SOURCE_REPO, file.repoId)
                    .putString(DoraModelDownloadWorker.KEY_SOURCE_FILENAME, file.filename)
                    .putString(DoraModelDownloadWorker.KEY_SOURCE_REVISION, file.revision)
                    .putString(DoraModelDownloadWorker.KEY_SOURCE_LICENSE, file.license)
                    .build(),
            )
            .build()
        workManager.enqueueUniqueWork("download-$modelId", ExistingWorkPolicy.REPLACE, request)
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(request.id).collect { info ->
                info ?: return@collect
                val progress = info.progress.getFloat(DoraModelDownloadWorker.KEY_PROGRESS, 0f)
                val bytes = info.progress.getLong(DoraModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L)
                val speed = info.progress.getLong(DoraModelDownloadWorker.KEY_SPEED_BYTES_PER_SECOND, -1L).takeIf { it > 0L }
                val eta = info.progress.getLong(DoraModelDownloadWorker.KEY_ETA_MILLIS, -1L).takeIf { it > 0L }
                val liveState = info.progress.getString(DoraModelDownloadWorker.KEY_DOWNLOAD_STATE)?.let { runCatching { DownloadState.valueOf(it) }.getOrNull() }
                val message = bytes.takeIf { it > 0L }?.let { "Downloading ${formatBytes(it)}" } ?: "Queued for secure download"
                when {
                    info.state == WorkInfo.State.ENQUEUED -> updateDownloadJob(job.id, progress, "Queued or waiting for network", downloadState = DownloadState.QUEUED, bytes = bytes, speed = speed, eta = eta)
                    info.state == WorkInfo.State.RUNNING -> updateDownloadJob(job.id, progress, message, downloadState = liveState, bytes = bytes, speed = speed, eta = eta)
                    info.state == WorkInfo.State.SUCCEEDED -> {
                        val artifact = registry.artifact(modelId)
                        val installed = artifact?.let { model.copy(installState = app.dora.localai.domain.ModelInstallState.INSTALLED, verified = true, filePath = it.path) }
                        if (installed != null) {
                            val metadata = withContext(Dispatchers.IO) { GgufMetadataReader.read(File(artifact.path)).getOrNull() }
                            val installedWithMetadata = installed.copy(metadata = metadata)
                            persistModelRecord(installedWithMetadata, artifact)
                            if (_uiState.value.activeModelId == null) registry.setActiveModelId(modelId)
                            _uiState.update { state -> state.copy(models = state.models.filterNot { it.id == modelId } + installedWithMetadata, activeModelId = state.activeModelId ?: modelId, activeDownloadId = null, toastMessage = "Model ready: ${file.filename}") }
                            updateDownloadJob(job.id, 1f, "Validated and ready for local chat", JobState.COMPLETE, DownloadState.COMPLETED, bytes = artifact?.sizeBytes ?: 0L)
                        } else {
                            _uiState.update { it.copy(activeDownloadId = null, toastMessage = "Download completed but Dora could not restore the model record") }
                            updateDownloadJob(job.id, 0f, "Model record missing after download", JobState.FAILED)
                        }
                    }
                    info.state.isFinished -> {
                        val error = info.outputData.getString(DoraModelDownloadWorker.KEY_ERROR) ?: "Model download failed"
                        updateDownloadJob(job.id, progress, error, JobState.FAILED, DownloadState.FAILED, bytes = bytes)
                        _uiState.update { it.copy(activeDownloadId = null, toastMessage = error) }
                    }
                }
            }
        }
    }

    private fun updateDownloadJob(
        id: String,
        progress: Float,
        message: String,
        state: JobState = JobState.RUNNING,
        downloadState: DownloadState? = null,
        bytes: Long? = null,
        speed: Long? = null,
        eta: Long? = null,
    ) {
        _uiState.update { current ->
            current.copy(jobs = current.jobs.map { job ->
                if (job.id != id) job else job.copy(
                    progress = progress,
                    message = message,
                    state = state,
                    download = job.download?.copy(
                        state = downloadState ?: job.download.state,
                        bytesDownloaded = bytes ?: job.download.bytesDownloaded,
                        progressPercent = (progress * 100f).toInt().coerceIn(0, 100),
                        downloadSpeedBytesPerSecond = speed ?: job.download.downloadSpeedBytesPerSecond,
                        estimatedRemainingTimeMillis = eta ?: job.download.estimatedRemainingTimeMillis,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
            })
        }
    }

    fun pauseDownload(downloadId: String) {
        val job = _uiState.value.jobs.firstOrNull { it.id == downloadId } ?: return
        val download = job.download ?: return
        if (!download.isPausable) return
        downloadControls.requestPause(download.downloadId)
        workManager.cancelUniqueWork("download-${download.modelId}")
        _uiState.update { state -> state.copy(toastMessage = "Pausing ${download.filename}…") }
    }

    fun cancelDownload(downloadId: String) {
        val job = _uiState.value.jobs.firstOrNull { it.id == downloadId } ?: return
        val download = job.download ?: return
        if (!download.isCancellable) return
        downloadControls.requestCancel(download.downloadId)
        workManager.cancelUniqueWork("download-${download.modelId}")
        viewModelScope.launch(Dispatchers.IO) {
            dao.findJob(download.downloadId)?.let { record ->
                dao.upsertJob(record.copy(
                    state = JobState.CANCELED.name,
                    message = "Download cancelled",
                    downloadState = DownloadState.CANCELLED.name,
                    errorMessage = null,
                    updatedAt = System.currentTimeMillis(),
                ))
            }
        }
    }

    fun resumeDownload(downloadId: String) = requeueDownload(downloadId, "Resuming download")

    fun retryDownload(downloadId: String) = requeueDownload(downloadId, "Retrying download")

    fun deleteDownload(downloadId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val record = dao.findJob(downloadId) ?: return@launch
            downloadControls.requestCancel(record.downloadId ?: record.id)
            record.modelId?.let { workManager.cancelUniqueWork("download-$it") }
            record.temporaryPath?.let(::deletePrivateDownloadFile)
            dao.deleteJob(record.id)
        }
    }

    private fun requeueDownload(downloadId: String, message: String) {
        viewModelScope.launch {
            val record = withContext(Dispatchers.IO) { dao.findJob(downloadId) } ?: return@launch
            val modelId = record.modelId ?: return@launch
            val url = record.url ?: return@launch
            downloadControls.clear(record.downloadId ?: record.id)
            val queued = record.copy(
                state = JobState.RUNNING.name,
                message = message,
                downloadState = DownloadState.QUEUED.name,
                errorMessage = null,
                updatedAt = System.currentTimeMillis(),
            )
            withContext(Dispatchers.IO) { dao.upsertJob(queued) }
            workManager.enqueueUniqueWork("download-$modelId", ExistingWorkPolicy.REPLACE, downloadRequest(queued, url))
        }
    }

    private fun downloadRequest(record: JobRecord, url: String): androidx.work.OneTimeWorkRequest = OneTimeWorkRequestBuilder<DoraModelDownloadWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 15L, TimeUnit.SECONDS)
        .setInputData(
            Data.Builder()
                .putString(DoraModelDownloadWorker.KEY_JOB_ID, record.downloadId ?: record.id)
                .putString(DoraModelDownloadWorker.KEY_MODEL_ID, record.modelId ?: record.id)
                .putString(DoraModelDownloadWorker.KEY_MODEL_NAME, record.label)
                .putString(DoraModelDownloadWorker.KEY_URL, url)
                .putString(DoraModelDownloadWorker.KEY_SHA256, record.expectedSha256.orEmpty())
                .putLong(DoraModelDownloadWorker.KEY_BYTES, record.totalBytes ?: -1L)
                .putString(DoraModelDownloadWorker.KEY_SOURCE_REPO, record.repositoryId)
                .putString(DoraModelDownloadWorker.KEY_SOURCE_FILENAME, record.filename)
                .putString(DoraModelDownloadWorker.KEY_SOURCE_REVISION, record.sourceRevision)
                .putString(DoraModelDownloadWorker.KEY_SOURCE_LICENSE, record.sourceLicense)
                .build(),
        )
        .build()

    private fun deletePrivateDownloadFile(path: String) {
        runCatching {
            val file = File(path).canonicalFile
            val directory = File(getApplication<Application>().filesDir, "models").canonicalFile
            if (file.parentFile?.canonicalFile == directory && file.extension.equals("part", ignoreCase = true)) file.delete()
        }
    }

    fun runLocalBenchmark() {
        if (_uiState.value.isGenerating || _uiState.value.isBenchmarking) return
        val model = _uiState.value.models.firstOrNull { it.id == _uiState.value.activeModelId && it.kind == app.dora.localai.domain.ModelKind.TEXT && it.filePath != null && it.verified }
        if (model == null || !nativeTextEngine.isProductionReady) {
            _uiState.update { it.copy(toastMessage = "Select a verified model and load native llama.cpp before benchmarking") }
            return
        }
        _uiState.update { it.copy(isBenchmarking = true, benchmarkResult = null, toastMessage = null) }
        viewModelScope.launch {
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val settings = (_uiState.value.conversationSettings[_uiState.value.activeConversationId] ?: GenerationSettings()).normalized().copy(maxTokens = 32)
                    val startedAt = System.nanoTime()
                    val output = NativeLlamaEngine.generate(
                        path = model.filePath!!,
                        prompt = "Reply with one short sentence confirming that this is a local benchmark.",
                        maxTokens = settings.maxTokens,
                        threads = settings.threads,
                        temperature = settings.temperature,
                        topK = settings.topK,
                        topP = settings.topP,
                    )
                    val elapsed = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(1L)
                    val tokens = estimateTokenCount(output)
                    "${tokens} estimated tokens • ${elapsed} ms end-to-end • %.1f estimated tok/s".format(tokens * 1_000f / elapsed)
                }
            }
            _uiState.update { state -> state.copy(isBenchmarking = false, benchmarkResult = result.getOrNull(), toastMessage = result.exceptionOrNull()?.message ?: "Local benchmark complete") }
        }
    }

    fun cleanupOrphanedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            val knownPaths = registry.allArtifacts().map { it.path }.toSet()
            val removed = modelStore.deleteOrphanedFiles(knownPaths)
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(storageSummary = storageSummary(), toastMessage = if (removed == 0) "No orphaned model files found" else "Removed $removed orphaned private file${if (removed == 1) "" else "s"}") }
            }
        }
    }

    fun setThemeMode(mode: String) {
        val normalized = mode.uppercase().takeIf { it in setOf("SYSTEM", "LIGHT", "DARK") } ?: "SYSTEM"
        registry.setThemeMode(normalized)
        _uiState.update { it.copy(themeMode = normalized) }
    }

    fun toggleOfflineOnly() {
        val value = !_uiState.value.isOfflineOnly
        registry.setOfflineOnly(value)
        _uiState.update { it.copy(isOfflineOnly = value) }
    }

    fun toggleIncognito() {
        val value = !_uiState.value.privacyIncognito
        registry.setIncognito(value)
        _uiState.update { it.copy(privacyIncognito = value, toastMessage = if (value) "Incognito on: new chat turns stay in memory only" else "Incognito off: new chat turns can be saved") }
    }

    fun setRetentionDays(days: Int) {
        val value = days.takeIf { it in setOf(0, 7, 30, 90) } ?: 0
        registry.setRetentionDays(value)
        _uiState.update { it.copy(retentionDays = value) }
        viewModelScope.launch(Dispatchers.IO) {
            if (value > 0) {
                val cutoff = System.currentTimeMillis() - value * 24L * 60L * 60L * 1_000L
                dao.deleteMessagesOlderThan(cutoff)
                dao.deleteConversationsOlderThan(cutoff)
            }
        }
    }

    fun clearAllLocalData() {
        registry.clearAll()
        viewModelScope.launch(Dispatchers.IO) {
            dao.allDownloadJobs().forEach { record ->
                record.modelId?.let { workManager.cancelUniqueWork("download-$it") }
                record.downloadId?.let(downloadControls::requestCancel)
            }
            modelStore.clearPrivateArtifacts()
            dao.deleteAllMessages()
            dao.deleteAllConversations()
            dao.deleteAllModels()
            dao.deleteAllJobs()
            dao.deleteAllDocumentChunks()
            dao.deleteAllDocuments()
            withContext(Dispatchers.Main) {
                _uiState.value = initialState().copy(toastMessage = "All local Dora data deleted")
            }
        }
        _uiState.update { it.copy(isGenerating = false, activeDownloadId = null, toastMessage = "Deleting local Dora data…") }
        NativeLlamaEngine.cancel()
        generationJob?.cancel()
        generationJob = null
    }

    fun clearToast() = _uiState.update { it.copy(toastMessage = null) }

    fun importConversation(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val input = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Dora could not open the selected conversation")
                    val bytes = input.use { stream -> readBoundedBytes(stream, MAX_IMPORT_BYTES + 1) }
                    require(bytes.size <= MAX_IMPORT_BYTES) { "Conversation import is limited to 5 MB." }
                    val root = org.json.JSONObject(bytes.toString(Charsets.UTF_8))
                    val title = root.optString("title", "Imported conversation").trim().take(80).ifBlank { "Imported conversation" }
                    val messagesJson = root.optJSONArray("messages") ?: error("The selected file has no messages array")
                    require(messagesJson.length() in 1..MAX_IMPORTED_MESSAGES) { "The conversation contains an unsupported number of messages." }
                    val messages = buildList {
                        for (index in 0 until messagesJson.length()) {
                            val item = messagesJson.optJSONObject(index) ?: error("Message $index is invalid")
                            val role = runCatching { MessageRole.valueOf(item.optString("role").uppercase()) }.getOrElse { error("Message $index has an invalid role") }
                            val text = item.optString("text").take(MAX_IMPORTED_MESSAGE_CHARS)
                            add(ChatMessage(role = role, text = text, metrics = item.optJSONObject("metrics")?.let { metrics ->
                                InferenceMetrics(
                                    firstTokenLatencyMillis = metrics.optLong("firstTokenLatencyMillis"),
                                    generationTimeMillis = metrics.optLong("generationTimeMillis"),
                                    tokensGenerated = metrics.optInt("tokensGenerated"),
                                    tokensPerSecond = metrics.optDouble("tokensPerSecond").toFloat(),
                                    contextTokenEstimate = metrics.optInt("contextTokenEstimate"),
                                ).normalized()
                            }))
                        }
                    }
                    Conversation(title = title, messages = messages)
                }
            }
            result.onSuccess { imported ->
                val settings = GenerationSettings()
                _uiState.update { state ->
                    state.copy(
                        conversations = listOf(imported) + state.conversations,
                        activeConversationId = imported.id,
                        conversationSettings = state.conversationSettings + (imported.id to settings),
                        showConversationList = false,
                        toastMessage = "Conversation imported locally",
                    )
                }
                persistConversation(imported, settings)
                imported.messages.forEachIndexed { index, message -> persistMessage(imported.id, message, index.toLong()) }
            }.onFailure { error ->
                _uiState.update { it.copy(toastMessage = "Import failed: ${error.message ?: "invalid conversation file"}") }
            }
        }
    }

    fun exportActiveConversation(uri: Uri, format: ConversationExportFormat) {
        val state = _uiState.value
        val conversation = state.conversations.firstOrNull { it.id == state.activeConversationId } ?: return
        val modelName = state.models.firstOrNull { it.id == state.activeModelId }?.name ?: "No verified model selected"
        viewModelScope.launch(Dispatchers.IO) {
            val result = runCatching {
                val output = getApplication<Application>().contentResolver.openOutputStream(uri)
                    ?: error("Dora could not open the selected destination")
                output.bufferedWriter().use { writer ->
                    when (format) {
                        ConversationExportFormat.MARKDOWN -> {
                            writer.appendLine("# ${conversation.title}")
                            writer.appendLine()
                            writer.appendLine("- Model: $modelName")
                            writer.appendLine("- Exported from Dora private local chat")
                            writer.appendLine()
                            conversation.messages.forEach { message ->
                                val role = when (message.role) {
                                    MessageRole.USER -> "You"
                                    MessageRole.ASSISTANT -> "Dora"
                                    MessageRole.SYSTEM -> "System"
                                }
                                writer.appendLine("## $role")
                                writer.appendLine()
                                writer.appendLine(message.text)
                                message.metrics?.let { metrics ->
                                    writer.appendLine()
                                    writer.appendLine("_Inference: %.1f tokens/s; %d ms first token; %d tokens generated; %d context tokens._".format(metrics.tokensPerSecond, metrics.firstTokenLatencyMillis, metrics.tokensGenerated, metrics.contextTokenEstimate))
                                }
                                writer.appendLine()
                            }
                        }
                        ConversationExportFormat.JSON -> {
                            writer.append("{\"title\":").append(jsonEscape(conversation.title))
                            writer.append(",\"model\":").append(jsonEscape(modelName))
                            writer.append(",\"exportedFrom\":\"Dora private local chat\",\"messages\":[")
                            conversation.messages.forEachIndexed { index, message ->
                                if (index > 0) writer.append(',')
                                writer.append("{\"role\":").append(jsonEscape(message.role.name.lowercase()))
                                writer.append(",\"text\":").append(jsonEscape(message.text))
                                message.metrics?.let { metrics ->
                                    writer.append(",\"metrics\":{\"firstTokenLatencyMillis\":${metrics.firstTokenLatencyMillis},\"generationTimeMillis\":${metrics.generationTimeMillis},\"tokensGenerated\":${metrics.tokensGenerated},\"tokensPerSecond\":${metrics.tokensPerSecond},\"contextTokenEstimate\":${metrics.contextTokenEstimate}}")
                                }
                                writer.append('}')
                            }
                            writer.append("]}")
                        }
                    }
                }
            }
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(toastMessage = result.fold({ "Conversation exported" }, { "Export failed: ${it.message ?: "could not write file"}" })) }
            }
        }
    }

    fun importGguf(uri: Uri) {
        if (_uiState.value.isGenerating || _uiState.value.isBenchmarking) {
            _uiState.update { it.copy(toastMessage = "Stop local work before importing a model") }
            return
        }
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
            registry.setArtifact(LocalRegistry.StoredArtifact(artifact.id, artifact.name, artifact.path, artifact.sizeBytes, artifact.sha256))
            registry.setActiveModelId(artifact.id)
            val importedModel = LocalModel(
                id = artifact.id,
                name = artifact.metadata.displayName?.takeIf { it.isNotBlank() } ?: artifact.name,
                publisher = "Local import",
                kind = app.dora.localai.domain.ModelKind.TEXT,
                format = "${artifact.metadata.quantization ?: "GGUF"} GGUF",
                sizeLabel = formatBytes(artifact.sizeBytes),
                memoryLabel = "Imported local model",
                license = "User-provided file",
                description = "Imported and validated from private storage.",
                installState = app.dora.localai.domain.ModelInstallState.INSTALLED,
                verified = true,
                recommended = false,
                filePath = artifact.path,
                metadata = artifact.metadata,
            )
            persistModelRecord(importedModel, LocalRegistry.StoredArtifact(artifact.id, importedModel.name, artifact.path, artifact.sizeBytes, artifact.sha256))
            _uiState.update { state ->
                state.copy(models = state.models.filterNot { it.id == artifact.id } + importedModel, activeModelId = artifact.id, toastMessage = "Model ready: ${importedModel.name}")
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
        if (_uiState.value.isGenerating || _uiState.value.isBenchmarking) {
            _uiState.update { it.copy(toastMessage = "Stop local work before removing a model") }
            return
        }
        modelStore.delete(_uiState.value.models.firstOrNull { it.id == modelId }?.filePath)
        if (registry.activeModelId() == modelId) registry.setActiveModelId(null)
        registry.setModelInstalled(modelId, false)
        viewModelScope.launch(Dispatchers.IO) { dao.deleteModel(modelId) }
        _uiState.update { state ->
                            state.copy(models = state.models.map { model ->
                    if (model.id == modelId) model.copy(installState = app.dora.localai.domain.ModelInstallState.AVAILABLE, filePath = null, verified = false) else model
                }, activeModelId = if (state.activeModelId == modelId) null else state.activeModelId, toastMessage = "Model removed from Dora")

        }
    }

    fun sendMessage() {
        val prompt = _uiState.value.composerText.trim()
        if (prompt.isBlank() || _uiState.value.isGenerating) return
        val state = _uiState.value
        val conversationId = state.activeConversationId
        val currentConversation = state.conversations.firstOrNull { it.id == conversationId } ?: return
        val activeModel = state.models.firstOrNull { it.id == state.activeModelId && it.kind == app.dora.localai.domain.ModelKind.TEXT && it.filePath != null && it.verified }
        if (activeModel == null) {
            _uiState.update { it.copy(toastMessage = "Select a verified local GGUF model before chatting") }
            return
        }
        if (!nativeTextEngine.isProductionReady) {
            _uiState.update { it.copy(toastMessage = "Native llama.cpp is unavailable; Dora will not substitute demo output") }
            return
        }
        val userMessage = ChatMessage(role = MessageRole.USER, text = prompt)
        val assistantMessage = ChatMessage(role = MessageRole.ASSISTANT, text = "", isPartial = true)
        if (currentConversation.messages.isEmpty()) {
            val title = prompt.replace(Regex("\\s+"), " ").take(42).ifBlank { "New local conversation" }
            updateConversation(conversationId) { it.copy(title = title) }
        }
        updateConversation(conversationId) { conversation -> conversation.copy(messages = conversation.messages + userMessage + assistantMessage) }
        persistMessage(conversationId, userMessage, currentConversation.messages.size.toLong())
        persistMessage(conversationId, assistantMessage, currentConversation.messages.size.toLong() + 1L)
        _uiState.update { it.copy(composerText = "", isGenerating = true, toastMessage = null) }
        startGeneration(conversationId, prompt)
    }

    fun regenerateLastAssistant() {
        if (_uiState.value.isGenerating) return
        val conversationId = _uiState.value.activeConversationId
        val conversation = _uiState.value.conversations.firstOrNull { it.id == conversationId } ?: return
        val assistantIndex = conversation.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        val userIndex = assistantIndex - 1
        if (assistantIndex < 0 || userIndex < 0 || conversation.messages[userIndex].role != MessageRole.USER) return
        val activeModel = _uiState.value.models.firstOrNull { it.id == _uiState.value.activeModelId && it.kind == app.dora.localai.domain.ModelKind.TEXT && it.filePath != null && it.verified }
        if (activeModel == null || !nativeTextEngine.isProductionReady) {
            _uiState.update { it.copy(toastMessage = "Native llama.cpp is unavailable; regeneration was not started") }
            return
        }
        val replacement = conversation.messages[assistantIndex].copy(text = "", isPartial = true, metrics = null)
        updateConversation(conversationId) { current -> current.copy(messages = current.messages.toMutableList().also { it[assistantIndex] = replacement }) }
        persistMessage(conversationId, replacement, assistantIndex.toLong())
        _uiState.update { it.copy(isGenerating = true, toastMessage = null) }
        startGeneration(conversationId, conversation.messages[userIndex].text)
    }

    fun editLastUserMessageAndRegenerate(text: String) {
        val revised = text.trim()
        if (revised.isBlank() || _uiState.value.isGenerating) return
        val conversationId = _uiState.value.activeConversationId
        val conversation = _uiState.value.conversations.firstOrNull { it.id == conversationId } ?: return
        val assistantIndex = conversation.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        val userIndex = assistantIndex - 1
        if (assistantIndex < 0 || userIndex < 0 || conversation.messages[userIndex].role != MessageRole.USER) return
        val revisedUser = conversation.messages[userIndex].copy(text = revised)
        val replacementAssistant = conversation.messages[assistantIndex].copy(text = "", isPartial = true, metrics = null)
        updateConversation(conversationId) { current -> current.copy(messages = current.messages.toMutableList().also { it[userIndex] = revisedUser; it[assistantIndex] = replacementAssistant }) }
        persistMessage(conversationId, revisedUser, userIndex.toLong())
        persistMessage(conversationId, replacementAssistant, assistantIndex.toLong())
        _uiState.update { it.copy(isGenerating = true, toastMessage = null) }
        startGeneration(conversationId, revised)
    }

    private fun startGeneration(conversationId: String, prompt: String) {
        val settings = _uiState.value.conversationSettings[conversationId]?.normalized() ?: GenerationSettings()
        generationJob?.cancel()
        generationJob = viewModelScope.launch {
            try {
                val conversation = _uiState.value.conversations.first { it.id == conversationId }
                val activeModel = _uiState.value.models.firstOrNull { it.id == _uiState.value.activeModelId && it.kind == app.dora.localai.domain.ModelKind.TEXT && it.filePath != null && it.verified }
                    ?: error("No verified local model is active")
                val documentPrompt = if (_uiState.value.documentSearchEnabled) documentContext(prompt) else ""
                val systemPrompt = listOf(settings.systemPrompt.takeIf { it.isNotBlank() }, documentPrompt.takeIf { it.isNotBlank() }).filterNotNull().joinToString("\n\n")
                val history = if (systemPrompt.isBlank()) conversation.messages else listOf(ChatMessage(role = MessageRole.SYSTEM, text = systemPrompt)) + conversation.messages
                var accumulated = ""
                var tokenCount = 0
                var firstTokenAtNanos: Long? = null
                val startedAtNanos = System.nanoTime()
                nativeTextEngine.streamReply(activeModel, history, settings).collect { token ->
                    if (firstTokenAtNanos == null) firstTokenAtNanos = System.nanoTime()
                    tokenCount += estimateTokenCount(token)
                    accumulated += token
                    updateLastAssistant(conversationId, accumulated, true)
                }
                val finishedAtNanos = System.nanoTime()
                val elapsedMillis = ((finishedAtNanos - startedAtNanos) / 1_000_000L).coerceAtLeast(1L)
                val metrics = InferenceMetrics(
                    firstTokenLatencyMillis = firstTokenAtNanos?.let { (it - startedAtNanos) / 1_000_000L } ?: elapsedMillis,
                    generationTimeMillis = elapsedMillis,
                    tokensGenerated = tokenCount.coerceAtLeast(estimateTokenCount(accumulated)),
                    tokensPerSecond = tokenCount.coerceAtLeast(estimateTokenCount(accumulated)) * 1_000f / elapsedMillis,
                    contextTokenEstimate = history.sumOf { estimateTokenCount(it.text) },
                ).normalized()
                updateLastAssistant(conversationId, accumulated, false, metrics)
                persistLastAssistant(conversationId)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                updateLastAssistant(conversationId, "Dora could not complete this local generation: ${error.message ?: "native runtime error"}", false)
                persistLastAssistant(conversationId)
                _uiState.update { it.copy(toastMessage = "Local generation failed") }
            } finally {
                _uiState.update { it.copy(isGenerating = false) }
            }
        }
    }

    private fun documentContext(query: String): String {
        val terms = query.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").split(" ").filter { it.length >= 3 }.toSet()
        if (terms.isEmpty() || documentChunks.isEmpty()) return ""
        val documentsById = _uiState.value.documents.associateBy { it.id }
        val matches = documentChunks.asSequence()
            .filter { chunk -> documentsById[chunk.documentId]?.enabled == true }
            .map { chunk -> chunk to terms.count { term -> " $term " in " ${chunk.searchableText} " } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(MAX_DOCUMENT_CONTEXT_CHUNKS)
            .toList()
        if (matches.isEmpty()) return ""
        return buildString {
            append("Use these excerpts from the user’s local documents when relevant. If they do not answer the question, say so. Do not invent citations.\n")
            matches.forEach { (chunk, _) ->
                append("\n[${documentsById[chunk.documentId]?.name ?: "Local document"}, section ${chunk.ordinal + 1}]\n")
                append(chunk.text.take(MAX_CONTEXT_CHARS_PER_CHUNK))
                append('\n')
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
        if (last?.role == MessageRole.ASSISTANT) {
            updateLastAssistant(id, last.text, false)
            persistLastAssistant(id)
        }
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

    private fun persistMessage(conversationId: String, message: ChatMessage, ordinal: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            if (registry.isIncognito()) return@launch
            dao.upsertMessage(
                MessageRecord(
                    id = message.id,
                    conversationId = conversationId,
                    role = message.role.name,
                    text = message.text,
                    ordinal = ordinal,
                    createdAt = System.currentTimeMillis(),
                    firstTokenLatencyMillis = message.metrics?.firstTokenLatencyMillis,
                    generationTimeMillis = message.metrics?.generationTimeMillis,
                    tokensGenerated = message.metrics?.tokensGenerated,
                    tokensPerSecond = message.metrics?.tokensPerSecond,
                    contextTokenEstimate = message.metrics?.contextTokenEstimate,
                ),
            )
        }
    }

    private fun persistLastAssistant(conversationId: String) {
        val conversation = _uiState.value.conversations.firstOrNull { it.id == conversationId } ?: return
        val index = conversation.messages.indexOfLast { it.role == MessageRole.ASSISTANT }
        val message = conversation.messages.getOrNull(index) ?: return
        persistMessage(conversationId, message, index.toLong())
        persistConversation(conversation, _uiState.value.conversationSettings[conversationId] ?: GenerationSettings())
    }

    private fun updateConversation(id: String, transform: (Conversation) -> Conversation) {
        _uiState.update { state ->
            state.copy(conversations = state.conversations.map { conversation ->
                if (conversation.id == id) transform(conversation) else conversation
            })
        }
    }

    private fun estimateTokenCount(text: String): Int = text.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0

    private fun jsonEscape(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private fun readBoundedBytes(input: InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(limit)
        val buffer = ByteArray(8 * 1024)
        while (output.size() < limit) {
            val count = input.read(buffer, 0, minOf(buffer.size, limit - output.size()))
            if (count <= 0) break
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private companion object {
        const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
        const val MAX_IMPORTED_MESSAGES = 10_000
        const val MAX_IMPORTED_MESSAGE_CHARS = 1_000_000
    }

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1024 * 1024 * 1024 -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f KB".format(bytes / 1024.0)
    }

    private fun persistModelRecord(model: LocalModel, artifact: LocalRegistry.StoredArtifact) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.upsertModel(
                ModelRecord(
                    id = model.id,
                    name = model.name,
                    kind = model.kind.name,
                    format = model.format,
                    path = artifact.path,
                    sizeBytes = artifact.sizeBytes,
                    sha256 = artifact.sha256,
                    license = artifact.sourceLicense ?: model.license,
                    verified = model.verified,
                    updatedAt = System.currentTimeMillis(),
                    sourceRepo = artifact.sourceRepo,
                    sourceFilename = artifact.sourceFilename,
                    sourceRevision = artifact.sourceRevision,
                    sourceUrl = artifact.sourceUrl,
                    sourceLicense = artifact.sourceLicense,
                ),
            )
        }
    }

    private fun updateLastAssistant(id: String, text: String, partial: Boolean, metrics: InferenceMetrics? = null) {
        updateConversation(id) { conversation ->
            val messages = conversation.messages.toMutableList()
            val index = messages.indexOfLast { it.role == MessageRole.ASSISTANT }
            if (index >= 0) messages[index] = messages[index].copy(text = text, isPartial = partial, metrics = metrics ?: messages[index].metrics)
            conversation.copy(messages = messages)
        }
    }
}
