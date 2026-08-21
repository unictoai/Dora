package app.dora.localai.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

private val DoraAccent = Color(0xFF3857D6)
private val DoraTeal = Color(0xFF007F78)
private val DoraInk = Color(0xFF15161A)
private val DoraMuted = Color(0xFF69707D)
private val DoraCanvas = Color(0xFFF5F7FB)
private val DoraSurface = Color(0xFFFFFFFF)
private val DoraDivider = Color(0xFFDDE2EC)

private val LightColors = lightColorScheme(
    primary = DoraAccent,
    onPrimary = Color.White,
    secondary = DoraTeal,
    onSecondary = Color.White,
    tertiary = Color(0xFF8B4A00),
    onTertiary = Color.White,
    background = DoraCanvas,
    onBackground = DoraInk,
    surface = DoraSurface,
    onSurface = DoraInk,
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = DoraMuted,
    outline = DoraDivider,
    outlineVariant = Color(0xFFE9EDF5),
    error = Color(0xFFB42318),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB9C5FF),
    onPrimary = Color(0xFF17275F),
    secondary = Color(0xFF63D7CA),
    onSecondary = Color(0xFF003733),
    tertiary = Color(0xFFFFB873),
    onTertiary = Color(0xFF4D2600),
    background = Color(0xFF0D1118),
    onBackground = Color(0xFFE9EDF5),
    surface = Color(0xFF151A22),
    onSurface = Color(0xFFE9EDF5),
    surfaceVariant = Color(0xFF202834),
    onSurfaceVariant = Color(0xFFB2BBC9),
    outline = Color(0xFF3B4656),
    outlineVariant = Color(0xFF293342),
    error = Color(0xFFFFB4AB),
)

private val DoraTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold),
        titleLarge = titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = labelLarge.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = bodyLarge.copy(lineHeight = bodyLarge.fontSize * 1.45f),
        bodyMedium = bodyMedium.copy(lineHeight = bodyMedium.fontSize * 1.35f),
    )
}

@Composable
fun DoraTheme(themeMode: String = "SYSTEM", content: @Composable () -> Unit) {
    val dark = when (themeMode.uppercase()) {
        "DARK" -> true
        "LIGHT" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = DoraTypography,
        content = content,
    )
}
