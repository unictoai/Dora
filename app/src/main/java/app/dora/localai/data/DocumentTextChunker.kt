package app.dora.localai.data

object DocumentTextChunker {
    private const val CHUNK_LENGTH = 1_200
    private const val MIN_CHUNK_LENGTH = 80

    fun chunk(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n").trim()
        if (normalized.isBlank()) return emptyList()
        val chunks = mutableListOf<String>()
        var cursor = 0
        while (cursor < normalized.length) {
            val end = (cursor + CHUNK_LENGTH).coerceAtMost(normalized.length)
            val boundary = if (end == normalized.length) end else normalized.lastIndexOfAny(charArrayOf(' ', '\n', '.', ',', ';'), end).takeIf { it > cursor + MIN_CHUNK_LENGTH } ?: end
            chunks += normalized.substring(cursor, boundary).trim()
            cursor = boundary
            while (cursor < normalized.length && normalized[cursor].isWhitespace()) cursor++
        }
        return chunks.filter { it.length >= 20 }
    }

    fun normalizeForSearch(value: String): String = value.lowercase().replace(Regex("[^\\p{L}\\p{Nd}]+"), " ").trim()
}
