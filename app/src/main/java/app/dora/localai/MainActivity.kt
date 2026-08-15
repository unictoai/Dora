package app.dora.localai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dora.localai.data.DoraUiState
import app.dora.localai.data.MainViewModel
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.DeviceFitLevel
import app.dora.localai.domain.HuggingFaceCandidate
import app.dora.localai.domain.HuggingFaceFileCandidate
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.MessageRole
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import app.dora.localai.ui.DoraTheme
import kotlinx.coroutines.launch

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
    val scope = rememberCoroutineScope()
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importGguf)
    }
    val hasModel = state.models.any { it.kind == ModelKind.TEXT && it.filePath != null }
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

    if (!hasModel && state.selectedTab != 1) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            ModelSetupScreen(
                onImport = { importLauncher.launch(arrayOf("application/octet-stream", "application/gguf", "*/*")) },
                onBrowse = { vm.selectTab(1) },
            )
        }
        SnackbarHost(hostState = snackbarHostState)
        return
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
                0 -> ChatScreen(state, vm)
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
private fun ChatScreen(state: DoraUiState, vm: MainViewModel) {
    val conversation = state.conversations.firstOrNull { it.id == state.activeConversationId } ?: state.conversations.first()
    val activeModel = state.models.firstOrNull { it.kind == ModelKind.TEXT && it.filePath != null }
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Dora", style = MaterialTheme.typography.headlineSmall)
                Text(activeModel?.name ?: "Local chat", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            LocalStatus(isGenerating = state.isGenerating)
            IconButton(onClick = { vm.selectTab(2) }) {
                Icon(Icons.Default.MoreHoriz, contentDescription = "Settings")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.65f))
        ChatHistory(conversation.messages, modifier = Modifier.weight(1f).fillMaxWidth())
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
            OutlinedTextField(
                value = state.composerText,
                onValueChange = vm::setComposerText,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Message Dora") },
                maxLines = 5,
                shape = RoundedCornerShape(18.dp),
            )
            Surface(modifier = Modifier.size(52.dp), shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary) {
                IconButton(onClick = { if (state.isGenerating) vm.stopGeneration() else vm.sendMessage() }) {
                    Icon(if (state.isGenerating) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.Send, contentDescription = if (state.isGenerating) "Stop" else "Send", tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
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
            Text(message.text.ifBlank { "…" }, style = MaterialTheme.typography.bodyLarge)
            if (message.isPartial) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun ModelsScreen(state: DoraUiState, vm: MainViewModel, onImport: () -> Unit) {
    val models = state.models.filter { it.kind == ModelKind.TEXT }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).safeDrawingPadding().padding(horizontal = 20.dp)) {
        ScreenHeader("Models", "Import files or find a public GGUF on Hugging Face.")
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
        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 18.dp, bottom = 24.dp),
        ) {
            item { Text("On this device", style = MaterialTheme.typography.titleMedium) }
            items(models, key = { it.id }) { model -> ModelCard(model, vm) }
            if (state.huggingFaceCandidates.isNotEmpty()) {
                item { Text("Suggested for this device", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
                items(state.huggingFaceCandidates, key = { it.repoId }) { candidate -> HuggingFaceCandidateCard(candidate, vm, state.activeDownloadId) }
            } else {
                item {
                    Text("Search Hugging Face for public GGUF files. Dora ranks quantizations by your measured RAM, storage, and ARM64 support.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun HuggingFaceCandidateCard(candidate: HuggingFaceCandidate, vm: MainViewModel, activeDownloadId: String?) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(candidate.displayName, style = MaterialTheme.typography.titleMedium)
            Text(candidate.repoId, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(candidate.description, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
private fun ModelCard(model: LocalModel, vm: MainViewModel) {
    val imported = model.filePath != null
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium)
                    Text(if (imported) "Ready for local chat" else "No model file imported", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (imported) Icon(Icons.Default.CheckCircle, contentDescription = "Ready", tint = Color(0xFF34A853))
            }
            Text(if (imported) "${model.format} • ${model.sizeLabel}" else "Choose a .gguf file from your device to use this model.", style = MaterialTheme.typography.bodyMedium)
            if (imported) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Active local model", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
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
        SettingRow(Icons.Default.Memory, "Device", state.deviceSummary)
        HorizontalDivider()
        SettingRow(Icons.Default.Storage, "Storage", "Imported models stay in Dora’s private app storage.")
        HorizontalDivider()
        SettingRow(Icons.Default.Info, "Runtime", state.runtimeNotice)
        Spacer(Modifier.height(24.dp))
        TextButton(onClick = vm::clearAllLocalData, modifier = Modifier.fillMaxWidth()) { Text("Delete all local data") }
        Spacer(Modifier.height(12.dp))
        Text("Dora 0.3.0 pre-alpha", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
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
            Spacer(Modifier.height(3.dp))
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
    }
}
