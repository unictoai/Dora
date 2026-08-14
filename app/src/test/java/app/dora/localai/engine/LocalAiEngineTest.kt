package app.dora.localai.engine

import app.dora.localai.domain.ChatMessage
import app.dora.localai.domain.LocalModel
import app.dora.localai.domain.MessageRole
import app.dora.localai.domain.ModelInstallState
import app.dora.localai.domain.ModelKind
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalAiEngineTest {
    private val model = LocalModel(
        id = "test-model",
        name = "Test model",
        publisher = "Dora",
        kind = ModelKind.TEXT,
        format = "GGUF",
        sizeLabel = "1 MB",
        memoryLabel = "small",
        license = "test",
        description = "test",
        installState = ModelInstallState.INSTALLED,
        verified = true,
        recommended = true,
    )

    @Test
    fun demoEngineStreamsAnOfflineResponse() = runBlocking {
        val tokens = DoraDemoTextEngine().streamReply(
            model,
            listOf(ChatMessage(role = MessageRole.USER, text = "hello")),
        ).toList()

        assertTrue(tokens.isNotEmpty())
        assertTrue(tokens.joinToString("").contains("hello"))
    }
}
