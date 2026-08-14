package app.dora.localai.domain

import java.util.UUID

enum class ModelKind { TEXT, IMAGE }
en
enum class ModelInstallState { AVAILABLE, INSTALLED, EXTERNAL, INVALID }

data class LocalModel(
    val id: String,
    val name: String,
    val publisher: String,
    val kind: ModelKind,
    val format: String,
    val sizeLabel: String,
    val memoryLabel: String,
    val license: String,
    val description: String,
    val installState: ModelInstallState,
    val verified: Boolean,
    val recommended: Boolean,
)

enum class MessageRole { USER, ASSISTANT, SYSTEM }

data class ChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: MessageRole,
    val text: String,
    val isPartial: Boolean = false,
)

data class Conversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
)

enum class JobKind { DOWNLOAD, TEXT, IMAGE }

enum class JobState { IDLE, RUNNING, COMPLETE, CANCELED, FAILED }

data class DoraJob(
    val id: String = UUID.randomUUID().toString(),
    val kind: JobKind,
    val label: String,
    val state: JobState = JobState.IDLE,
    val progress: Float = 0f,
    val message: String = "",
)
