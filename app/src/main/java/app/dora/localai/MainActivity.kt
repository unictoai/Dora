package app.dora.localai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dora.localai.data.DoraStorageSummary
import app.dora.localai.data.DoraUiState
import app.dora.localai.data.MainViewModel
import app.dora.localai.domain.CatalogFilter
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.Conversation
import app.dora.localai.domain.CuratedModelSuggestion
import app.dora.localai.domain.DeviceFitLevel
import app.dora.localai.domain.DownloadState
import app.dora.localai.domain.DoraJob
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.HuggingFaceFileCandidate
import app.dora.localai.domain.JobKind
import app.dora.localai.domain.JobState
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.MessageRole
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import app.dora.localai.ui.DoraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { DoraTheme { DoraApp() } }
    }
}

private data class DoraDestination(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

@Composable
fun DoraApp(vm: MainViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importGguf)
    }
    val documentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importDocument)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/markdown")) { uri ->
        uri?.let(vm::exportActiveConversation)
    }
    val destinations = listOf(
        DoraDestination("Chat", Icons.Default.ChatBubbleOutline),
        DoraDestination("Models", Icons.Default.Memory),
        DoraDestination("Settings", Icons.Default.Settings),
    )

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                destinations.forEachIndexed { index, destination ->
                    NavigationBarItem(
                        selected = state.selectedTab == index,
                        onClick = { vm.selectTab(index) },
                        icon = { Icon(destination.icon, contentDescription = destination.label) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state.selectedTab) {
                0 -> ChatScreen(
                    state,
                    vm,
                    onBrowseModels = vm::openModelDiscovery,
                    onImportModel = { importLauncher.launch(arrayOf("application/octet-stream", "application/gguf", "*/*")) },
                    onImportDocument = { documentLauncher.launch(arrayOf("text/plain", "text/markdown", "text/csv", "application/json", "application/xml")) },
                    onExport = {
                        val title = state.conversations.firstOrNull { it.id == state.activeConversationId }?.title.orEmpty()
                            .replace(Regex("[^A-Za-z0-9._-]+"), "_")
                            .trim('_')
                            .ifBlank { "dora-conversation" }
                        exportLauncher.launch("$title.md")
                    },
                )
                1 -> ModelsScreen(state, vm, onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/gguf", "*/*")) })
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun ModelSetupScreen(onImport: () -> Unit, onBrowse: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(app.dora.localai.R.drawable.dora_logo),
            contentDescription = "Dora logo",
            modifier = Modifier.size(86.dp).clip(RoundedCornerShape(26.dp)),
        )
        Spacer(Modifier.height(28.dp))
        Text("Set up Dora", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            "Bring a GGUF model to your phone. Dora validates it, keeps it private, and uses it locally for chat.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        SetupPoint("Private by default", "No account and no required cloud inference.")
        SetupPoint("Your model, your storage", "Dora stores imported files inside its private app space.")
        SetupPoint("Transparent runtime", "Dora tells you when native inference is active.")
        Spacer(Modifier.height(30.dp))
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Import a GGUF model")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Memory, contentDescription = null)
            Spacer(Modifier.width(10.dp))
            Text("Find a model on Hugging Face")
        }
        Spacer(Modifier.height(12.dp))
        Text("GGUF files only • model weights are not bundled", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SetupPoint(title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.Top) {
        Surface(modifier = Modifier.size(8.dp).padding(top = 6.dp), shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.primary) {}
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChatScreen(state: DoraUiState, vm: MainViewModel, onBrowseModels: () -> Unit, onImportModel: () -> Unit, onImportDocument: () -> Unit, onExport: () -> Unit) {
    val conversation = state.conversations.firstOrNull { it.id == state.activeConversationId } ?: state.conversations.first()
    val activeModel = state.models.firstOrNull { it.id == state.activeModelId && it.kind == ModelKind.TEXT && it.filePath != null && it.verified }
    var showSettings by remember { mutableStateOf(false) }
    var renameConversationId by remember { mutableStateOf<String?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dora", style = MaterialTheme.typography.headlineSmall)
                Text(activeModel?.name ?: "Explore local AI", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LocalStatus(isGenerating = state.isGenerating)
            IconButton(onClick = vm::toggleConversationList) {
                Icon(Icons.Default.ChatBubbleOutline, contentDescription = if (state.showConversationList) "Hide conversations" else "Show conversations")
            }
            IconButton(onClick = vm::toggleDocumentPanel) {
                Icon(Icons.Default.Description, contentDescription = if (state.showDocumentPanel) "Hide local documents" else "Show local documents")
            }
            IconButton(onClick = vm::createConversation, enabled = !state.isGenerating) {
                Icon(Icons.Default.Add, contentDescription = "New conversation")
            }
            IconButton(onClick = onExport, enabled = !state.isGenerating && conversation.messages.isNotEmpty()) {
                Icon(Icons.Default.FileDownload, contentDescription = "Export conversation")
            }
            IconButton(onClick = { showSettings = true }, enabled = !state.isGenerating) {
                Icon(Icons.Default.Tune, contentDescription = "Generation settings")
            }
        }
        if (state.showConversationList) {
            ConversationListPanel(
                conversations = state.conversations,
                activeId = state.activeConversationId,
                onSelect = vm::selectConversation,
                onRename = { renameConversationId = it },
                onDelete = vm::deleteConversation,
            )
            Spacer(Modifier.height(10.dp))
        }
        if (state.showDocumentPanel) {
            DocumentContextPanel(
                documents = state.documents,
                searchEnabled = state.documentSearchEnabled,
                onImport = onImportDocument,
                onToggleSearch = vm::toggleDocumentSearch,
                onDelete = vm::deleteDocument,
            )
            Spacer(Modifier.height(10.dp))
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        if (activeModel == null && conversation.messages.isEmpty()) {
            NoModelChatState(onBrowseModels = onBrowseModels, onImportModel = onImportModel, modifier = Modifier.weight(1f).fillMaxWidth())
        } else {
            ChatHistory(conversation.messages, modifier = Modifier.weight(1f).fillMaxWidth())
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            OutlinedTextField(
                value = state.composerText,
                onValueChange = vm::setComposerText,
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (activeModel == null) "Install a model to start" else "Message Dora") },
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
                enabled = !state.isGenerating && activeModel != null,
            )
            Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = if (activeModel != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                IconButton(onClick = { if (state.isGenerating) vm.stopGeneration() else vm.sendMessage() }, enabled = state.isGenerating || activeModel != null) {
                    Icon(if (state.isGenerating) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.Send, contentDescription = if (state.isGenerating) "Stop" else "Send", tint = if (activeModel != null || state.isGenerating) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (showSettings) {
        GenerationSettingsDialog(
            settings = state.activeChatSettingsForUi(),
            onDismiss = { showSettings = false },
            onSave = { vm.updateGenerationSettings(it); showSettings = false },
        )
    }
    renameConversationId?.let { id ->
        RenameConversationDialog(
            initialTitle = state.conversations.firstOrNull { it.id == id }?.title.orEmpty(),
            onDismiss = { renameConversationId = null },
            onSave = { vm.renameConversation(id, it); renameConversationId = null },
        )
    }
}

@Composable
private fun NoModelChatState(onBrowseModels: () -> Unit, onImportModel: () -> Unit, modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 18.dp)) {
            Surface(modifier = Modifier.size(76.dp), shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Default.Memory, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(20.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("Explore Dora", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            Text("Dora runs verified GGUF models on this device. Browse recommendations first, or import a model you already have.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onBrowseModels, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Memory, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Explore model recommendations")
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImportModel, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(15.dp)) {
                Icon(Icons.Default.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Import a local GGUF")
            }
            Spacer(Modifier.height(12.dp))
            Text("No cloud inference • No account required", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DocumentContextPanel(
    documents: List<app.dora.localai.domain.LocalDocument>,
    searchEnabled: Boolean,
    onImport: () -> Unit,
    onToggleSearch: (Boolean) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Local context", style = MaterialTheme.typography.titleSmall)
                    Text("Dora searches these private text files before local chat.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = searchEnabled, onCheckedChange = onToggleSearch)
            }
            if (documents.isEmpty()) {
                Text("No documents indexed yet. Supported: text, Markdown, CSV, JSON, and XML up to 20 MB each.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                documents.forEach { document ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(document.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${formatBytesForUi(document.sizeBytes)} • ${document.chunkCount} sections", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDelete(document.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remove ${document.name}") }
                    }
                }
            }
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Add a local document")
            }
        }
    }
}

@Composable
private fun ConversationListPanel(
    conversations: List<Conversation>,
    activeId: String,
    onSelect: (String) -> Unit,
    onRename: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        LazyColumn(modifier = Modifier.heightIn(max = 230.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp)) {
            items(conversations, key = { it.id }) { conversation ->
                Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (conversation.id == activeId) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f) else Color.Transparent).padding(start = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onSelect(conversation.id) }, modifier = Modifier.weight(1f)) {
                        Text(conversation.title, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Start, modifier = Modifier.fillMaxWidth())
                    }
                    IconButton(onClick = { onRename(conversation.id) }) { Icon(Icons.Default.Edit, contentDescription = "Rename conversation") }
                    IconButton(onClick = { onDelete(conversation.id) }, enabled = conversations.size > 1) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete conversation") }
                }
            }
        }
    }
}

private fun DoraUiState.activeChatSettingsForUi() = conversationSettings[activeConversationId] ?: app.dora.localai.domain.GenerationSettings()

@Composable
private fun RenameConversationDialog(initialTitle: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var title by remember(initialTitle) { mutableStateOf(initialTitle) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename conversation") },
        text = { OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true, label = { Text("Title") }) },
        confirmButton = { TextButton(onClick = { onSave(title) }, enabled = title.trim().isNotEmpty()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun GenerationSettingsDialog(
    settings: app.dora.localai.domain.GenerationSettings,
    onDismiss: () -> Unit,
    onSave: (app.dora.localai.domain.GenerationSettings) -> Unit,
) {
    var draft by remember(settings) { mutableStateOf(settings) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Local generation") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("These controls are applied to the on-device llama.cpp session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Profiles", style = MaterialTheme.typography.labelMedium)
                Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "Balanced" to app.dora.localai.domain.GenerationSettings(),
                        "Creative" to draft.copy(temperature = 1.0f, topP = 0.98f, topK = 60, maxTokens = 512),
                        "Focused" to draft.copy(temperature = 0.25f, topP = 0.85f, topK = 20, maxTokens = 384),
                    ).forEach { (label, profile) ->
                        FilterChip(selected = false, onClick = { draft = profile.normalized() }, label = { Text(label) })
                    }
                }
                OutlinedTextField(
                    value = draft.systemPrompt,
                    onValueChange = { draft = draft.copy(systemPrompt = it) },
                    label = { Text("System prompt") },
                    minLines = 3,
                    maxLines = 5,
                )
                Text("Output limit: ${draft.maxTokens} tokens", style = MaterialTheme.typography.labelMedium)
                Slider(value = draft.maxTokens.toFloat(), onValueChange = { draft = draft.copy(maxTokens = it.toInt()) }, valueRange = 16f..1024f, steps = 31)
                Text("CPU threads: ${draft.threads}", style = MaterialTheme.typography.labelMedium)
                Slider(value = draft.threads.toFloat(), onValueChange = { draft = draft.copy(threads = it.toInt().coerceAtLeast(1)) }, valueRange = 1f..16f, steps = 14)
                Text("Temperature: %.2f • Top-k: %d • Top-p: %.2f".format(draft.temperature, draft.topK, draft.topP), style = MaterialTheme.typography.labelMedium)
                Slider(value = draft.temperature, onValueChange = { draft = draft.copy(temperature = it) }, valueRange = 0.05f..1.5f, steps = 28)
                Slider(value = draft.topP, onValueChange = { draft = draft.copy(topP = it) }, valueRange = 0.1f..1.0f, steps = 17)
                Slider(value = draft.topK.toFloat(), onValueChange = { draft = draft.copy(topK = it.toInt().coerceAtLeast(1)) }, valueRange = 1f..128f, steps = 30)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(draft.normalized()) }) { Text("Apply") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun LocalStatus(isGenerating: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(modifier = Modifier.size(7.dp), shape = RoundedCornerShape(7.dp), color = if (isGenerating) MaterialTheme.colorScheme.primary else Color(0xFF34A853)) {}
        Text(if (isGenerating) "Working" else "On device", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ChatHistory(messages: List<ChatMessage>, modifier: Modifier) {
    if (messages.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("What can Dora help with?", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(8.dp))
                Text("Your conversation stays on this device.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }
    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(18.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 20.dp, bottom = 12.dp)) {
        items(messages, key = { it.id }) { message -> MessageBubble(message) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == MessageRole.USER
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(modifier = Modifier.fillMaxWidth(if (isUser) 0.84f else 0.94f)) {
            Text(if (isUser) "You" else "Dora", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            MarkdownContent(message.text.ifBlank { "…" })
            if (message.isPartial) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                message.metrics?.let { metrics ->
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "%.1f tok/s • %d ms first token • %d tokens • %d context tokens".format(metrics.tokensPerSecond, metrics.firstTokenLatencyMillis, metrics.tokensGenerated, metrics.contextTokenEstimate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun MarkdownContent(text: String) {
    val lines = text.replace("\r\n", "\n").split('\n')
    var inCode = false
    val codeBuffer = StringBuilder()
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        lines.forEach { line ->
            if (line.trimStart().startsWith("```")) {
                if (inCode) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        BasicText(codeBuffer.toString().trimEnd(), modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant))
                    }
                    codeBuffer.clear()
                }
                inCode = !inCode
            } else if (inCode) {
                codeBuffer.appendLine(line)
            } else {
                when {
                    line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> Row(verticalAlignment = Alignment.Top) {
                        Text("•", color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp))
                        InlineMarkdownText(line.trimStart().drop(2), Modifier.weight(1f))
                    }
                    line.isBlank() -> Spacer(Modifier.height(3.dp))
                    else -> InlineMarkdownText(line, Modifier.fillMaxWidth())
                }
            }
        }
        if (inCode && codeBuffer.isNotEmpty()) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                BasicText(codeBuffer.toString().trimEnd(), modifier = Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant))
            }
        }
    }
}

@Composable
private fun InlineMarkdownText(text: String, modifier: Modifier = Modifier) {
    val annotated = buildAnnotatedString {
        var cursor = 0
        val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`)")
        pattern.findAll(text).forEach { match ->
            append(text.substring(cursor, match.range.first))
            val value = match.value
            if (value.startsWith("**")) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(value.removeSurrounding("**")) }
            else withStyle(SpanStyle(fontFamily = FontFamily.Monospace, background = MaterialTheme.colorScheme.surfaceVariant)) { append(value.removeSurrounding("`")) }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor))
    }
    BasicText(annotated, modifier = modifier, style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun ModelsScreen(state: DoraUiState, vm: MainViewModel, onImport: () -> Unit) {
    val models = state.models.filter { it.kind == ModelKind.TEXT }
    val downloadJobs = state.jobs.filter { it.kind == JobKind.DOWNLOAD }
    val visibleCandidates = when (state.catalogFilter) {
        CatalogFilter.ALL -> state.huggingFaceCandidates
        CatalogFilter.RECOMMENDED -> state.huggingFaceCandidates.filter { candidate -> candidate.files.any { it.deviceFit.level == DeviceFitLevel.RECOMMENDED } }
        CatalogFilter.SMALLEST -> state.huggingFaceCandidates.sortedBy { it.files.minOfOrNull { file -> file.sizeBytes } ?: Long.MAX_VALUE }
        CatalogFilter.MOST_DOWNLOADED -> state.huggingFaceCandidates.sortedByDescending { it.downloads }
    }
    val sections = listOf(
        "ACTIVE" to downloadJobs.filter { it.download?.state in setOf(DownloadState.STARTING, DownloadState.DOWNLOADING, DownloadState.VERIFYING, DownloadState.VALIDATING, DownloadState.INSTALLING, DownloadState.RETRYING) },
        "QUEUED" to downloadJobs.filter { it.download?.state == DownloadState.QUEUED },
        "PAUSED" to downloadJobs.filter { it.download?.state == DownloadState.PAUSED },
        "FAILED" to downloadJobs.filter { it.download?.state == DownloadState.FAILED },
        "COMPLETED" to downloadJobs.filter { it.download?.state == DownloadState.COMPLETED || it.state == JobState.COMPLETE },
    )
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Models", "Discover, download, verify, and run private GGUF models.")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = state.huggingFaceQuery,
            onValueChange = vm::setHuggingFaceQuery,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            placeholder = { Text("Search public GGUF models") },
            trailingIcon = {
                IconButton(onClick = vm::browseHuggingFace, enabled = !state.isSearchingHuggingFace) {
                    if (state.isSearchingHuggingFace) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Memory, contentDescription = "Search Hugging Face")
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(16.dp)) {
            Icon(Icons.Default.Download, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Import a local GGUF")
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                CatalogFilter.ALL to "All",
                CatalogFilter.RECOMMENDED to "For this device",
                CatalogFilter.SMALLEST to "Smallest",
                CatalogFilter.MOST_DOWNLOADED to "Popular",
            ).forEach { (filter, label) ->
                FilterChip(selected = state.catalogFilter == filter, onClick = { vm.setCatalogFilter(filter) }, label = { Text(label, maxLines = 1) })
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 24.dp),
        ) {
            item { CuratedSuggestionsSection(state.curatedSuggestions, vm) }
            item { DownloadCenterSummary(state, downloadJobs) }
            sections.forEach { (title, jobs) ->
                if (jobs.isNotEmpty()) {
                    item { Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp)) }
                    items(jobs, key = { it.id }) { job ->
                        DownloadProgressCard(
                            job = job,
                            expanded = state.expandedDownloadId == job.id,
                            onToggleDetails = { vm.toggleDownloadDetails(job.id) },
                            onPause = { vm.pauseDownload(job.id) },
                            onResume = { vm.resumeDownload(job.id) },
                            onRetry = { vm.retryDownload(job.id) },
                            onCancel = { vm.cancelDownload(job.id) },
                            onDelete = { vm.deleteDownload(job.id) },
                        )
                    }
                }
            }
            item { Text("On this device", style = MaterialTheme.typography.titleMedium) }
            items(models, key = { it.id }) { model -> ModelCard(model, vm, model.id == state.activeModelId) }
            if (visibleCandidates.isNotEmpty()) {
                item { Text("Suggested for this device", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(visibleCandidates, key = { it.repoId }) { candidate -> HuggingFaceCandidateCard(candidate, vm, state.activeDownloadId) }
            } else {
                item {
                    Text("Search Hugging Face for public GGUF files. Dora ranks quantizations by measured RAM, storage, and ARM64 support.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CuratedSuggestionsSection(suggestions: List<CuratedModelSuggestion>, vm: MainViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Start here", style = MaterialTheme.typography.titleMedium)
                Text("Curated GGUF paths for your first local model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Hugging Face", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        suggestions.forEach { suggestion ->
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(suggestion.title, style = MaterialTheme.typography.titleSmall)
                            Text(suggestion.category, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                            Text(suggestion.fitHint, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
                        }
                    }
                    Text(suggestion.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    Text(suggestion.repoId, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Button(onClick = { vm.openCuratedSuggestion(suggestion) }, modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(12.dp)) {
                        Text("View compatible files")
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCenterSummary(state: DoraUiState, jobs: List<DoraJob>) {
    val active = jobs.count { it.download?.state in setOf(DownloadState.STARTING, DownloadState.DOWNLOADING, DownloadState.VERIFYING, DownloadState.VALIDATING, DownloadState.INSTALLING, DownloadState.RETRYING) }
    val paused = jobs.count { it.download?.state == DownloadState.PAUSED }
    val failed = jobs.count { it.download?.state == DownloadState.FAILED }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Download Center", style = MaterialTheme.typography.titleMedium)
            Text("$active active • ${jobs.count { it.download?.state == DownloadState.QUEUED }} queued • $paused paused • $failed failed", style = MaterialTheme.typography.bodySmall)
            Text("${formatBytesForUi(state.storageSummary.availableBytes)} available • ${formatBytesForUi(state.storageSummary.modelBytes)} in Dora models • ${state.storageSummary.orphanedFileCount} orphaned files", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DownloadProgressCard(
    job: DoraJob,
    expanded: Boolean,
    onToggleDetails: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onDelete: () -> Unit,
) {
    val download = job.download ?: return
    val percent = download.progressPercent?.coerceIn(0, 100)
    val progress = (percent ?: 0) / 100f
    val stateLabel = when (download.state) {
        DownloadState.QUEUED -> "Queued"
        DownloadState.STARTING -> "Starting"
        DownloadState.DOWNLOADING -> "Downloading"
        DownloadState.PAUSED -> "Paused"
        DownloadState.VERIFYING -> "Verifying"
        DownloadState.VALIDATING -> "Validating"
        DownloadState.INSTALLING -> "Installing"
        DownloadState.COMPLETED -> "Ready"
        DownloadState.CANCELLING -> "Cancelling"
        DownloadState.CANCELLED -> "Cancelled"
        DownloadState.FAILED -> "Failed"
        DownloadState.RETRYING -> "Retrying"
    }
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = if (download.state == DownloadState.FAILED) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(download.filename, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(stateLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                IconButton(onClick = onToggleDetails) {
                    Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, contentDescription = if (expanded) "Hide download details" else "Show download details")
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().semantics { contentDescription = "${download.filename}: ${percent?.let { "$it percent" } ?: "progress calculating"}, state $stateLabel" },
            )
            Text(
                buildString {
                    append(percent?.let { "$it%" } ?: "Calculating…")
                    append(" • ")
                    append(download.totalBytes?.let { "${formatBytesForUi(download.bytesDownloaded)} / ${formatBytesForUi(it)}" } ?: "${formatBytesForUi(download.bytesDownloaded)} downloaded")
                    download.downloadSpeedBytesPerSecond?.let { append(" • ${formatBytesForUi(it)}/s") }
                    download.estimatedRemainingTimeMillis?.let { append(" • ~${formatDurationForUi(it)} remaining") }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (!download.errorMessage.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(6.dp))
                    Text(download.errorMessage!!, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    download.state == DownloadState.DOWNLOADING || download.state == DownloadState.STARTING -> IconButton(onClick = onPause, enabled = download.isPausable) { Icon(Icons.Default.Pause, contentDescription = "Pause download") }
                    download.state == DownloadState.PAUSED -> IconButton(onClick = onResume) { Icon(Icons.Default.PlayArrow, contentDescription = "Resume download") }
                    download.state == DownloadState.FAILED -> IconButton(onClick = onRetry) { Icon(Icons.Default.Refresh, contentDescription = "Retry download") }
                }
                if (download.isCancellable) IconButton(onClick = onCancel) { Icon(Icons.Default.Close, contentDescription = "Cancel download") }
                if (download.state == DownloadState.FAILED || download.state == DownloadState.CANCELLED) IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, contentDescription = "Delete failed download") }
                TextButton(onClick = onToggleDetails) { Text(if (expanded) "Hide details" else "Details") }
            }
            if (expanded) {
                HorizontalDivider()
                DownloadDetailRow("Repository", download.repositoryId ?: "Not recorded")
                DownloadDetailRow("File", download.filename)
                DownloadDetailRow("Elapsed", formatDurationForUi(download.elapsedTimeMillis))
                DownloadDetailRow("Retries", download.retryCount.toString())
                DownloadDetailRow("Download ID", download.downloadId)
            }
        }
    }
}

@Composable
private fun DownloadDetailRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End, modifier = Modifier.padding(start = 12.dp))
    }
}

private fun formatDurationForUi(millis: Long): String {
    val seconds = (millis / 1000L).coerceAtLeast(0L)
    return if (seconds >= 60L) "${seconds / 60L} min" else "$seconds sec"
}

@Composable
private fun HuggingFaceCandidateCard(candidate: HuggingFaceCandidate, vm: MainViewModel, activeDownloadId: String?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(candidate.displayName, style = MaterialTheme.typography.titleMedium)
            Text(candidate.repoId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                candidate.pipelineTag?.let { CatalogMetadataPill(it) }
                candidate.library?.let { CatalogMetadataPill(it) }
                Text("${formatCountForUi(candidate.downloads)} downloads", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(candidate.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (candidate.tags.isNotEmpty()) {
                Text(candidate.tags.take(4).joinToString(" • "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (candidate.gated) {
                Text("Gated repository — request access on Hugging Face before downloading.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            candidate.files.take(3).forEach { file ->
                HuggingFaceFileRow(file, candidate, vm, activeDownloadId)
            }
        }
    }
}

@Composable
private fun CatalogMetadataPill(value: String) {
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatCountForUi(value: Long): String = when {
    value >= 1_000_000L -> "%.1fM".format(value / 1_000_000.0)
    value >= 1_000L -> "%.1fk".format(value / 1_000.0)
    else -> value.toString()
}

@Composable
private fun HuggingFaceFileRow(file: HuggingFaceFileCandidate, candidate: HuggingFaceCandidate, vm: MainViewModel, activeDownloadId: String?) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(12.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(file.filename, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${file.quantization} • ${formatBytesForUi(file.sizeBytes)}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
            FitLabel(file.deviceFit.level)
        }
        Text(file.deviceFit.explanation, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(
            onClick = { vm.downloadHuggingFace(file, candidate) },
            enabled = file.deviceFit.allowed && (activeDownloadId == null || activeDownloadId == "hf-${file.repoId}-${file.filename}".replace(Regex("[^A-Za-z0-9._-]"), "_")),
            modifier = Modifier.fillMaxWidth().height(42.dp),
            shape = RoundedCornerShape(12.dp),
        ) { Text(if (activeDownloadId == "hf-${file.repoId}-${file.filename}".replace(Regex("[^A-Za-z0-9._-]"), "_")) "Downloading…" else if (file.deviceFit.allowed) "Download to Dora" else "Not recommended") }
    }
}

@Composable
private fun FitLabel(level: DeviceFitLevel) {
    val (label, color) = when (level) {
        DeviceFitLevel.RECOMMENDED -> "Recommended" to Color(0xFF18864B)
        DeviceFitLevel.POSSIBLE -> "Possible" to Color(0xFF9A6700)
        DeviceFitLevel.TOO_HEAVY -> "Too heavy" to MaterialTheme.colorScheme.error
        DeviceFitLevel.UNSUPPORTED -> "Unsupported" to MaterialTheme.colorScheme.error
    }
    Surface(shape = RoundedCornerShape(20.dp), color = color.copy(alpha = 0.12f)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = color, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun formatBytesForUi(bytes: Long): String = when {
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    bytes >= 1024L * 1024L -> "%.0f MB".format(bytes / (1024.0 * 1024.0))
    else -> "%.0f KB".format(bytes / 1024.0)
}

@Composable
private fun ModelCard(model: LocalModel, vm: MainViewModel, isActive: Boolean) {
    val imported = model.installState == ModelInstallState.INSTALLED && model.filePath != null && model.verified
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (model.installState) {
                            ModelInstallState.INSTALLED -> if (imported) "Ready for local chat" else "Installed — verification pending"
                            ModelInstallState.INVALID -> "Needs attention — integrity or file check failed"
                            ModelInstallState.EXTERNAL -> "External model reference"
                            ModelInstallState.AVAILABLE -> "Not installed"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (model.installState == ModelInstallState.INVALID) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (imported) Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF34A853))
                if (model.installState == ModelInstallState.INVALID) Icon(Icons.Default.ErrorOutline, contentDescription = "Invalid model", tint = MaterialTheme.colorScheme.error)
            }
            Text("${model.publisher} • ${model.format} • ${model.sizeLabel}", style = MaterialTheme.typography.bodyMedium)
            Text(model.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text("Device fit: ${model.memoryLabel} • License: ${model.license}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (model.filePath != null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (isActive) "Active local model" else "Verified local model", style = MaterialTheme.typography.labelLarge, color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (imported && !isActive) OutlinedButton(onClick = { vm.selectActiveModel(model.id) }, shape = RoundedCornerShape(12.dp)) { Text("Use for chat") }
                    IconButton(onClick = { vm.deleteModel(model.id) }) { Icon(Icons.Default.DeleteOutline, contentDescription = "Remove model") }
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(state: DoraUiState, vm: MainViewModel) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Settings", "Simple controls for a private local app.")
        Spacer(Modifier.height(16.dp))
        SettingRow(Icons.Default.Lock, "Offline-only mode", "Inference never needs a cloud account or remote fallback.") {
            Switch(checked = state.isOfflineOnly, onCheckedChange = { vm.toggleOfflineOnly() })
        }
        HorizontalDivider()
        SettingRow(Icons.Default.Lock, "Incognito mode", "New chat turns stay in memory and are not written to Room. Existing history is not deleted.") {
            Switch(checked = state.privacyIncognito, onCheckedChange = { vm.toggleIncognito() })
        }
        HorizontalDivider()
        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Conversation retention", fontWeight = FontWeight.SemiBold)
            Text("Automatically remove saved conversations older than the selected window. Models and documents are not affected.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(modifier = Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0 to "Keep", 7 to "7 days", 30 to "30 days", 90 to "90 days").forEach { (days, label) ->
                    FilterChip(selected = state.retentionDays == days, onClick = { vm.setRetentionDays(days) }, label = { Text(label) })
                }
            }
        }
        HorizontalDivider()
        SettingRow(Icons.Default.Memory, "Device", state.deviceSummary)
        HorizontalDivider()
        SettingRow(
            Icons.Default.Storage,
            "Storage",
            "${formatBytesForUi(state.storageSummary.availableBytes)} available of ${formatBytesForUi(state.storageSummary.totalBytes)} • ${formatBytesForUi(state.storageSummary.modelBytes)} models • ${formatBytesForUi(state.storageSummary.temporaryDownloadBytes)} temporary downloads • ${state.storageSummary.orphanedFileCount} orphaned files",
        )
        HorizontalDivider()
        SettingRow(Icons.Default.Info, "Runtime", state.runtimeNotice)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = vm::clearAllLocalData, modifier = Modifier.fillMaxWidth()) { Text("Delete all local data") }
        Spacer(Modifier.height(12.dp))
        Text("Dora 0.8.0 pre-alpha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Column(modifier = Modifier.padding(top = 18.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(5.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 17.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
