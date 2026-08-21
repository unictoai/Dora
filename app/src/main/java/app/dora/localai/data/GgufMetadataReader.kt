package app.dora.localai.data

import app.dora.localai.domain.ModelMetadata
import java.io.File
import java.io.RandomAccessFile

/** Reads bounded scalar GGUF metadata for transparent local model details. */
object GgufMetadataReader {
    private const val HEADER_BYTES = 24L
    private const val MAX_STRING_BYTES = 1L * 1024L * 1024L
    private const val MAX_ARRAY_ITEMS = 100_000L

    fun read(file: File): Result<ModelMetadata> = runCatching {
        val header = GgufValidator.validate(file).getOrThrow()
        RandomAccessFile(file, "r").use { input ->
            input.seek(HEADER_BYTES)
            val values = linkedMapOf<String, Any>()
            repeat(header.metadataKeyValueCount.toInt()) {
                val key = readString(input)
                val type = readU32(input).toInt()
                readValue(input, type)?.let { values[key] = it }
            }
            ModelMetadata(
                architecture = values["general.architecture"] as? String,
                displayName = values["general.name"] as? String,
                quantization = (values["general.file_type"] as? Number)?.toLong()?.let(::quantizationLabel),
                contextLength = values.firstNumberEndingWith(".context_length"),
                parameterCount = values["general.parameter_count"].asLong(),
                blockCount = values.firstNumberEndingWith(".block_count"),
                embeddingLength = values.firstNumberEndingWith(".embedding_length"),
                vocabularySize = values.firstNumberEndingWith(".vocab_size"),
            )
        }
    }

    private fun readValue(input: RandomAccessFile, type: Int): Any? = when (type) {
        0 -> input.readUnsignedByte()
        1 -> input.readByte().toInt()
        2 -> readU16(input)
        3 -> readU16(input).toShort().toInt()
        4 -> readU32(input)
        5 -> readU32(input).toInt()
        6 -> Float.fromBits(readU32(input).toInt())
        7 -> input.readUnsignedByte() != 0
        8 -> readString(input)
        9 -> {
            val elementType = readU32(input).toInt()
            val count = readI64(input)
            require(count in 0L..MAX_ARRAY_ITEMS) { "GGUF metadata array is too large." }
            repeat(count.toInt()) { readValue(input, elementType) }
            null
        }
        10 -> readI64(input).toULong().toLong()
        11 -> readI64(input)
        12 -> Double.fromBits(readI64(input))
        else -> error("Unsupported GGUF metadata type: $type")
    }

    private fun readString(input: RandomAccessFile): String {
        val length = readI64(input)
        require(length in 0L..MAX_STRING_BYTES) { "GGUF metadata string is too large." }
        val bytes = ByteArray(length.toInt())
        input.readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun readU16(input: RandomAccessFile): Int =
        input.readUnsignedByte() or (input.readUnsignedByte() shl 8)

    private fun readU32(input: RandomAccessFile): Long =
        input.readUnsignedByte().toLong() or
            (input.readUnsignedByte().toLong() shl 8) or
            (input.readUnsignedByte().toLong() shl 16) or
            (input.readUnsignedByte().toLong() shl 24)

    private fun readI64(input: RandomAccessFile): Long {
        var value = 0L
        repeat(8) { index -> value = value or (input.readUnsignedByte().toLong() shl (index * 8)) }
        return value
    }

    private fun Map<String, Any>.firstNumberEndingWith(suffix: String): Long? =
        entries.firstOrNull { it.key.endsWith(suffix) }?.value.asLong()

    private fun Any?.asLong(): Long? = (this as? Number)?.toLong()

    private fun quantizationLabel(fileType: Long): String = when (fileType) {
        0L -> "F32"
        1L -> "F16"
        2L -> "Q4_0"
        3L -> "Q4_1"
        6L -> "Q5_0"
        7L -> "Q5_1"
        8L -> "Q8_0"
        9L -> "Q8_1"
        10L -> "Q2_K"
        11L -> "Q3_K_S"
        12L -> "Q3_K_M"
        13L -> "Q3_K_L"
        14L -> "Q4_K_S"
        15L -> "Q4_K_M"
        16L -> "Q5_K_S"
        17L -> "Q5_K_M"
        18L -> "Q6_K"
        19L -> "IQ2_XXS"
        20L -> "IQ2_XS"
        21L -> "IQ3_XXS"
        22L -> "IQ1_S"
        23L -> "IQ4_NL"
        24L -> "IQ3_S"
        25L -> "IQ2_S"
        26L -> "IQ4_XS"
        27L -> "IQ1_M"
        28L -> "BF16"
        else -> "GGML_TYPE_$fileType"
    }
}
