package app.dora.localai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val DoraAccent = Color(0xFF4F46E5)
private val DoraInk = Color(0xFF171717)
private val DoraMuted = Color(0xFF737373)
private val DoraCanvas = Color(0xFFF7F7F8)
private val DoraSurface = Color(0xFFFFFFFF)
private val DoraDivider = Color(0xFFE5E5E7)

private val LightColors = lightColorScheme(
    primary = DoraAccent,
    onPrimary = Color.White,
    secondary = Color(0xFF52525B),
    onSecondary = Color.White,
    background = DoraCanvas,
    onBackground = DoraInk,
    surface = DoraSurface,
    onSurface = DoraInk,
    surfaceVariant = Color(0xFFF0F0F2),
    onSurfaceVariant = DoraMuted,
    outline = DoraDivider,
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFA5B4FC),
    onPrimary = Color(0xFF1E1B4B),
    secondary = Color(0xFFA1A1AA),
    onSecondary = Color(0xFF18181B),
    background = Color(0xFF101010),
    onBackground = Color(0xFFF5F5F5),
    surface = Color(0xFF191919),
    onSurface = Color(0xFFF5F5F5),
    surfaceVariant = Color(0xFF242424),
    onSurfaceVariant = Color(0xFFA3A3A3),
    outline = Color(0xFF3F3F46),
    error = Color(0xFFF97066),
)

private val DoraTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = bodyLarge.fontSize * 1.45f),
    )
}

@Composable
fun DoraTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        typography = DoraTypography,
        content = content,
    )
}
