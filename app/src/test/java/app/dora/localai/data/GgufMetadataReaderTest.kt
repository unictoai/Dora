package app.dora.localai.data

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufMetadataReaderTest {
    @Test
    fun readsSupportedScalarMetadata() {
        val entries = ByteArrayOutputStream().apply {
            stringEntry("general.architecture", "llama")
            stringEntry("general.name", "Dora Test")
            uint32Entry("general.file_type", 15)
            uint64Entry("llama.context_length", 4096)
            uint64Entry("general.parameter_count", 1_500_000_000)
            uint32Entry("llama.block_count", 24)
            uint32Entry("llama.embedding_length", 2048)
            uint32Entry("tokenizer.ggml.vocab_size", 32000)
        }
        val file = Files.createTempFile("dora-metadata", ".gguf").toFile()
        file.writeBytes(
            ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46554747)
                .putInt(3)
                .putLong(1)
                .putLong(8)
                .array() + entries.toByteArray(),
        )
        try {
            val metadata = GgufMetadataReader.read(file).getOrThrow()
            assertEquals("llama", metadata.architecture)
            assertEquals("Dora Test", metadata.displayName)
            assertEquals("Q4_K_M", metadata.quantization)
            assertEquals(4096L, metadata.contextLength)
            assertEquals(1_500_000_000L, metadata.parameterCount)
            assertEquals(24L, metadata.blockCount)
            assertEquals(2048L, metadata.embeddingLength)
            assertEquals(32000L, metadata.vocabularySize)
        } finally {
            assertTrue(file.delete())
        }
    }

    private fun ByteArrayOutputStream.stringEntry(key: String, value: String) {
        writeLong(key.length.toLong())
        write(key.toByteArray(Charsets.UTF_8))
        writeInt(8)
        writeLong(value.length.toLong())
        write(value.toByteArray(Charsets.UTF_8))
    }

    private fun ByteArrayOutputStream.uint32Entry(key: String, value: Int) {
        writeLong(key.length.toLong())
        write(key.toByteArray(Charsets.UTF_8))
        writeInt(4)
        writeInt(value)
    }

    private fun ByteArrayOutputStream.uint64Entry(key: String, value: Long) {
        writeLong(key.length.toLong())
        write(key.toByteArray(Charsets.UTF_8))
        writeInt(10)
        writeLong(value)
    }

    private fun ByteArrayOutputStream.writeInt(value: Int) = writeLittleEndian(value.toLong(), 4)

    private fun ByteArrayOutputStream.writeLong(value: Long) = writeLittleEndian(value, 8)

    private fun ByteArrayOutputStream.writeLittleEndian(value: Long, bytes: Int) {
        repeat(bytes) { index -> write((value ushr (index * 8)).toInt() and 0xff) }
    }
}
