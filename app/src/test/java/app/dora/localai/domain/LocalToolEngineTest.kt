package app.dora.localai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalToolEngineTest {
    @Test
    fun calculatorHonorsPrecedenceAndParentheses() {
        assertEquals("Result: 14", LocalToolEngine.execute("/calc 2 * (3 + 4)"))
        assertEquals("Result: 5", LocalToolEngine.execute("/calc 10 / 2"))
    }

    @Test
    fun textCountIsBoundedAndDeterministic() {
        assertEquals("Words: 3\nCharacters: 16\nCharacters without spaces: 14", LocalToolEngine.execute("/count Dora stays local"))
    }

    @Test
    fun helpAndNowAreAvailableOffline() {
        assertTrue(LocalToolEngine.execute("/help")!!.contains("/calc"))
        assertTrue(LocalToolEngine.execute("/now")!!.startsWith("Local device time:"))
    }

    @Test
    fun arbitraryInputIsNotTreatedAsATool() {
        assertEquals(null, LocalToolEngine.execute("run shell command"))
        assertTrue(runCatching { LocalToolEngine.execute("/calc 2 ** 3") }.isFailure)
    }
}
