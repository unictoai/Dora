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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.dora.localai.data.DoraUiState
import app.dora.localai.data.MainViewModel
import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.JobState
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

private data class DoraTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
fun DoraApp(vm: MainViewModel = viewModel()) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(state.toastMessage) {
        state.toastMessage?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearToast()
        }
    }

    val tabs = listOf(
        DoraTab("Home", Icons.Default.Home),
        DoraTab("Chat", Icons.Default.ChatBubbleOutline),
        DoraTab("Create", Icons.Default.AutoAwesome),
        DoraTab("Models", Icons.Default.Memory),
        DoraTab("Settings", Icons.Default.Settings),
    )

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = state.selectedTab == index,
                        onClick = { vm.selectTab(index) },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (state.selectedTab) {
                0 -> HomeScreen(state, onNavigate = vm::selectTab)
                1 -> ChatScreen(state, vm)
                2 -> ImageStudioScreen(state, vm)
                3 -> ModelsScreen(state, vm)
                else -> SettingsScreen(state, vm)
            }
        }
    }
}

@Composable
private fun PageColumn(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        subtitle?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HomeScreen(state: DoraUiState, onNavigate: (Int) -> Unit) {
    PageColumn {
        ScreenHeader("Good to see you.", "Dora keeps your AI work on your device.")
        PrivacyBanner(state.isOfflineOnly)
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text("Your local AI studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                }
                Text(
                    "Choose a verified model, see what your phone can handle, and generate without a required cloud account.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { onNavigate(3) }) { Text("Explore models") }
            }
        }
        Text("Start here", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            HomeActionCard("Chat locally", Icons.Default.ChatBubbleOutline, Modifier.weight(1f)) { onNavigate(1) }
            HomeActionCard("Create image", Icons.Default.Image, Modifier.weight(1f)) { onNavigate(2) }
        }
        RuntimeCard(state.runtimeNotice)
        Text("Privacy by design", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Dora’s inference path is local-first. Model downloads are separate, explicit actions, and no model weights are bundled into this starter build.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun HomeActionCard(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(label, fontWeight = FontWeight.SemiBold)
            Text("Private by default", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun PrivacyBanner(offlineOnly: Boolean) {
    Card(
        colors = CardDefaults.cardColors(containerColor = if (offlineOnly) Color(0xFFE4F7EF) else Color(0xFFFFF3D7)),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (offlineOnly) Icons.Default.Lock else Icons.Default.NetworkCheck, null, tint = if (offlineOnly) Color(0xFF1D7A52) else Color(0xFF9A6700))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(if (offlineOnly) "Offline-only mode" else "Model downloads can use the network", fontWeight = FontWeight.SemiBold)
                Text(if (offlineOnly) "Inference is designed to stay on this device." else "Dora will explain each download before it starts.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun RuntimeCard(notice: String) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.WarningAmber, null, tint = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("Runtime status", fontWeight = FontWeight.SemiBold)
                Text(notice, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ChatScreen(state: DoraUiState, vm: MainViewModel) {
    val conversation = state.conversations.firstOrNull { it.id == state.activeConversationId } ?: state.conversations.first()
    PageColumn {
        ScreenHeader("Local chat", "Stream a response from the active on-device model.")
        AssistChip(onClick = { vm.selectTab(3) }, label = { Text("Dora Starter • ${if (state.isGenerating) "generating" else "ready"}") }, leadingIcon = { Icon(Icons.Default.Memory, null) })
        ChatHistory(conversation.messages, modifier = Modifier.weight(1f, fill = true))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = state.composerText,
                onValueChange = vm::setComposerText,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Ask Dora something local…") },
                maxLines = 4,
            )
            IconButton(onClick = { if (state.isGenerating) vm.stopGeneration() else vm.sendMessage() }, modifier = Modifier.size(52.dp)) {
                Icon(if (state.isGenerating) Icons.Default.StopCircle else Icons.AutoMirrored.Filled.Send, contentDescription = if (state.isGenerating) "Stop" else "Send", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ChatHistory(messages: List<ChatMessage>, modifier: Modifier = Modifier) {
    if (messages.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text("Your private conversation will appear here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(messages, key = { it.id }) { message ->
            val isUser = message.role == MessageRole.USER
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                Surface(
                    color = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.94f),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(if (isUser) "You" else "Dora", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(message.text.ifBlank { "…" }, style = MaterialTheme.typography.bodyLarge)
                        if (message.isPartial) {
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageStudioScreen(state: DoraUiState, vm: MainViewModel) {
    PageColumn {
        ScreenHeader("Image studio", "A capability-aware local generation workflow.")
        RuntimeCard("${state.runtimeNotice}. Image runtime is not bundled yet.")
        OutlinedTextField(
            value = state.imagePrompt,
            onValueChange = vm::setImagePrompt,
            modifier = Modifier.fillMaxWidth(),
            minLines = 4,
            label = { Text("Prompt") },
            placeholder = { Text("Describe the image you want to create…") },
        )
        Text("Safe preset", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = true, onClick = {}, label = { Text("Fast draft") })
            FilterChip(selected = false, onClick = {}, label = { Text("512 × 512") })
        }
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Memory, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text("Dora will show memory, heat, and duration estimates once a runtime is verified on real Android devices.", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Button(onClick = vm::generateImage, modifier = Modifier.fillMaxWidth()) { Text("Start local image job") }
        state.jobs.filter { it.kind == app.dora.localai.domain.JobKind.IMAGE }.lastOrNull()?.let { job ->
            JobCard(job)
        }
    }
}

@Composable
private fun ModelsScreen(state: DoraUiState, vm: MainViewModel) {
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(vm::importGguf)
    }
    PageColumn {
        ScreenHeader("Models", "Install only what Dora can explain and validate.")
        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/gguf", "*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Download, null)
            Spacer(Modifier.width(8.dp))
            Text("Import a local model")
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            items(state.models, key = { it.id }) { model -> ModelCard(model, vm) }
        }
    }
}

@Composable
private fun ModelCard(model: LocalModel, vm: MainViewModel) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(model.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("${model.publisher} • ${model.kind.name.lowercase().replaceFirstChar { it.uppercase() }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (model.verified) Icon(Icons.Default.CheckCircle, "Verified", tint = Color(0xFF1D7A52))
            }
            Text(model.description, style = MaterialTheme.typography.bodyMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(onClick = {}, label = { Text(model.format) })
                AssistChip(onClick = {}, label = { Text(model.sizeLabel) })
            }
            Text(model.memoryLabel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("License: ${model.license}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (model.installState) {
                    ModelInstallState.INSTALLED -> {
                        Button(onClick = {}, modifier = Modifier.weight(1f)) { Text("Installed") }
                        IconButton(onClick = { vm.deleteModel(model.id) }) { Icon(Icons.Default.DeleteOutline, "Remove model") }
                    }
                    else -> Button(onClick = { vm.installModel(model.id) }, modifier = Modifier.fillMaxWidth()) { Text("Add to local registry") }
                }
            }
        }
    }
}

@Composable
private fun JobCard(job: app.dora.localai.domain.DoraJob) {
    Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(if (job.state == JobState.FAILED) Icons.Default.WarningAmber else Icons.Default.MoreHoriz, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text(job.label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(job.state.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.labelMedium)
            }
            if (job.state == JobState.RUNNING) LinearProgressIndicator(progress = { job.progress }, modifier = Modifier.fillMaxWidth())
            Text(job.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SettingsScreen(state: DoraUiState, vm: MainViewModel) {
    PageColumn {
        ScreenHeader("Settings & privacy", "Understand what Dora stores and when it can use the network.")
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Offline-only inference", fontWeight = FontWeight.SemiBold)
                    Text("No required cloud account or remote inference path.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = state.isOfflineOnly, onCheckedChange = { vm.toggleOfflineOnly() })
            }
        }
        SettingRow(Icons.Default.Storage, "Local storage", "Models, chats, images, and job metadata remain on this device.")
        SettingRow(Icons.Default.Memory, "Device fit", state.deviceSummary)
        SettingRow(Icons.Default.NetworkCheck, "Network policy", "Only explicit model acquisition may use the network in the planned production build.")
        Text("Runtime status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(state.runtimeNotice, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = vm::clearAllLocalData, modifier = Modifier.fillMaxWidth()) { Text("Delete all local data") }
    }
}

@Composable
private fun SettingRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, description: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
