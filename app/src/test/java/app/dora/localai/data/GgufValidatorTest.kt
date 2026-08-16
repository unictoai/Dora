package app.dora.localai.data

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GgufValidatorTest {
    @Test
    fun acceptsSupportedHeader() {
        val file = writeHeader(version = 3, tensorCount = 42, metadataCount = 7)
        try {
            val header = GgufValidator.validate(file).getOrThrow()
            assertEquals(3L, header.version)
            assertEquals(42L, header.tensorCount)
            assertEquals(7L, header.metadataKeyValueCount)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val file = writeHeader(version = 99, tensorCount = 1, metadataCount = 0)
        try {
            assertFalse(GgufValidator.validate(file).isSuccess)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsNonsensicalCounts() {
        val file = writeHeader(version = 3, tensorCount = 0, metadataCount = 0)
        try {
            assertFalse(GgufValidator.validate(file).isSuccess)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsTruncatedHeader() {
        val file = Files.createTempFile("dora-truncated", ".gguf").toFile()
        file.writeBytes(byteArrayOf('G'.code.toByte(), 'G'.code.toByte(), 'U'.code.toByte(), 'F'.code.toByte()))
        try {
            assertTrue(GgufValidator.validate(file).isFailure)
        } finally {
            file.delete()
        }
    }

    private fun writeHeader(version: Int, tensorCount: Long, metadataCount: Long) =
        Files.createTempFile("dora-gguf", ".gguf").toFile().also { file ->
            val bytes = ByteBuffer.allocate(24).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(0x46554747)
                .putInt(version)
                .putLong(tensorCount)
                .putLong(metadataCount)
                .array()
            file.writeBytes(bytes)
        }
}
