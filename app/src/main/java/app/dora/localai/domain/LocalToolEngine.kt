package app.dora.localai.domain

import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object LocalToolEngine {
    fun execute(input: String): String? {
        val command = input.trim()
        return when {
            command.equals("/help", ignoreCase = true) -> helpText()
            command.equals("/now", ignoreCase = true) -> "Local device time: ${ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm z", Locale.getDefault()))}"
            command.startsWith("/count ", ignoreCase = true) -> countText(command.substringAfter(' ').take(MAX_INPUT_CHARS))
            command.startsWith("/calc ", ignoreCase = true) -> calculate(command.substringAfter(' ').take(MAX_INPUT_CHARS))
            else -> null
        }
    }

    private fun helpText(): String = """Dora local tools (offline)

/calc 12 * (3 + 4)
/count text to count words and characters
/now

These tools are deterministic, bounded, and run only on this device. They do not execute code or access the network."""

    private fun countText(text: String): String {
        val words = text.trim().takeIf { it.isNotEmpty() }?.split(Regex("\\s+"))?.size ?: 0
        return "Words: $words\nCharacters: ${text.length}\nCharacters without spaces: ${text.count { !it.isWhitespace() }}"
    }

    private fun calculate(expression: String): String {
        require(expression.isNotBlank()) { "Usage: /calc expression" }
        val value = Parser(expression).parse()
        require(value.isFinite()) { "The calculation result is not finite." }
        return "Result: ${formatNumber(value)}"
    }

    private fun formatNumber(value: Double): String {
        val rounded = if (kotlin.math.abs(value) < 1e12) "%.10f".format(Locale.US, value).trimEnd('0').trimEnd('.') else value.toString()
        return if (rounded == "-0") "0" else rounded
    }

    private class Parser(private val source: String) {
        private var index = 0

        fun parse(): Double {
            val result = parseExpression()
            skipWhitespace()
            require(index == source.length) { "Unexpected input near position $index." }
            return result
        }

        private fun parseExpression(): Double {
            var value = parseTerm()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('+') -> value + parseTerm()
                    consume('-') -> value - parseTerm()
                    else -> return value
                }
            }
        }

        private fun parseTerm(): Double {
            var value = parseFactor()
            while (true) {
                skipWhitespace()
                value = when {
                    consume('*') -> value * parseFactor()
                    consume('/') -> value / parseFactor().also { require(it != 0.0) { "Division by zero is not allowed." } }
                    else -> return value
                }
            }
        }

        private fun parseFactor(): Double {
            skipWhitespace()
            if (consume('+')) return parseFactor()
            if (consume('-')) return -parseFactor()
            if (consume('(')) {
                val value = parseExpression()
                skipWhitespace()
                require(consume(')')) { "Missing closing parenthesis." }
                return value
            }
            val start = index
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index++
            require(index > start) { "Expected a number near position $index." }
            return source.substring(start, index).toDoubleOrNull() ?: error("Invalid number.")
        }

        private fun consume(character: Char): Boolean {
            if (index < source.length && source[index] == character) {
                index++
                return true
            }
            return false
        }

        private fun skipWhitespace() {
            while (index < source.length && source[index].isWhitespace()) index++
        }
    }

    private const val MAX_INPUT_CHARS = 500
}
