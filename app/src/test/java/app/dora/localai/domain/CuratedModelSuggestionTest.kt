package app.dora.localai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CuratedModelSuggestionTest {
    @Test
    fun defaultSuggestionsAreUniqueRealRepositories() {
        val suggestions = defaultCuratedModelSuggestions
        assertEquals(suggestions.size, suggestions.map { it.repoId }.toSet().size)
        assertTrue(suggestions.size >= 4)
        assertTrue(suggestions.all { it.repoId.matches(Regex("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")) })
        assertTrue(suggestions.all { it.description.isNotBlank() && it.category.isNotBlank() })
        assertFalse(suggestions.any { it.repoId.contains("example", ignoreCase = true) })
    }
}
