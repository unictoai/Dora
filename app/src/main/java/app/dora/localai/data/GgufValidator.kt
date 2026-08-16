package app.dora.localai.data

import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Validates the GGUF container prefix without loading tensor data into memory. */
object GgufValidator {
    private const val HEADER_BYTES = 24
    private const val MAGIC = 0x46554747L // ASCII GGUF as a little-endian uint32.
    private const val MIN_VERSION = 1L
    private const val MAX_VERSION = 3L
    private const val MAX_TENSOR_COUNT = 10_000_000L
    private const val MAX_METADATA_COUNT = 100_000L

    data class Header(
        val version: Long,
        val tensorCount: Long,
        val metadataKeyValueCount: Long,
    )

    fun validate(file: File): Result<Header> = runCatching {
        require(file.isFile) { "GGUF artifact is not a regular file." }
        require(file.length() >= HEADER_BYTES) { "GGUF artifact is truncated before its complete header." }
        FileInputStream(file).use { input ->
            val bytes = ByteArray(HEADER_BYTES)
            readFully(input, bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            val magic = buffer.int.toLong() and 0xffffffffL
            require(magic == MAGIC) { "GGUF magic is invalid." }
            val version = buffer.int.toLong() and 0xffffffffL
            require(version in MIN_VERSION..MAX_VERSION) { "Unsupported GGUF version: $version." }
            val tensorCount = buffer.long
            val metadataCount = buffer.long
            require(tensorCount in 1..MAX_TENSOR_COUNT) { "GGUF tensor count is invalid: $tensorCount." }
            require(metadataCount in 0..MAX_METADATA_COUNT) { "GGUF metadata count is invalid: $metadataCount." }
            Header(version, tensorCount, metadataCount)
        }
    }

    private fun readFully(input: FileInputStream, destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = input.read(destination, offset, destination.size - offset)
            if (count < 0) throw IOException("GGUF header ended unexpectedly.")
            offset += count
        }
    }
}
