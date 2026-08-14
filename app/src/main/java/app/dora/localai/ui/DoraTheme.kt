package app.dora.localai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DoraIndigo = Color(0xFF5B5CE2)
private val DoraTeal = Color(0xFF0F766E)
private val DoraInk = Color(0xFF202133)
private val DoraCanvas = Color(0xFFF7F7FB)
private val DoraSurface = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = DoraIndigo,
    onPrimary = Color.White,
    secondary = DoraTeal,
    background = DoraCanvas,
    surface = DoraSurface,
    onBackground = DoraInk,
    onSurface = DoraInk,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9B8FF),
    secondary = Color(0xFF73D7C8),
    background = Color(0xFF11121A),
    surface = Color(0xFF1A1B25),
    onBackground = Color(0xFFE8E8F0),
    onSurface = Color(0xFFE8E8F0),
)

@Composable
fun DoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
