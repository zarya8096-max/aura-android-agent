package com.aura.agent.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.aura.agent.data.AppThemePreset

val ObsidianAccent = Color(0xFF7AB8FF)
val WarmSandAccent = Color(0xFFE2C290)
val SageAccent = Color(0xFF7EC4A0)
val GeminiGradientColors = listOf(Color(0xFF7AB8FF), Color(0xFFA5B4FC), Color(0xFFE2C290))

private val ObsidianGraphiteColors = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF071E3D),
    primaryContainer = Color(0xFF152942),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFE2C290),
    onSecondary = Color(0xFF2E2210),
    background = Color(0xFF0D0E10),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0D0E10),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF16181C),
    onSurfaceVariant = Color(0xFF9AA0AA),
    surfaceContainer = Color(0xFF14161A),
    surfaceContainerHigh = Color(0xFF1D2026),
    outline = Color(0xFF282C34),
    error = Color(0xFFEA8A8A)
)

private val GeminiMidnightColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF13335A),
    onPrimaryContainer = Color(0xFFD2E3FC),
    secondary = Color(0xFFA8C7FA),
    onSecondary = Color(0xFF0B2545),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1E1F20),
    onSurfaceVariant = Color(0xFFA8ABA8),
    surfaceContainer = Color(0xFF1B1C1E),
    surfaceContainerHigh = Color(0xFF26282B),
    outline = Color(0xFF33363A),
    error = Color(0xFFF28B82)
)

private val WarmAlabasterColors = lightColorScheme(
    primary = Color(0xFF1D4ED8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFF9A6B2F),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFAF9F6),
    onBackground = Color(0xFF18181B),
    surface = Color(0xFFFAF9F6),
    onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF2F0EA),
    onSurfaceVariant = Color(0xFF52525B),
    surfaceContainer = Color(0xFFF3F1EC),
    surfaceContainerHigh = Color(0xFFE8E5DD),
    outline = Color(0xFFDCD8CE),
    error = Color(0xFFB91C1C)
)

private val SageTerminalColors = darkColorScheme(
    primary = Color(0xFF7EC4A0),
    onPrimary = Color(0xFF0B2619),
    primaryContainer = Color(0xFF173526),
    onPrimaryContainer = Color(0xFFC8F0DA),
    secondary = Color(0xFFC9B486),
    onSecondary = Color(0xFF2A2314),
    background = Color(0xFF0E1210),
    onBackground = Color(0xFFE6ECE8),
    surface = Color(0xFF0E1210),
    onSurface = Color(0xFFE6ECE8),
    surfaceVariant = Color(0xFF161C19),
    onSurfaceVariant = Color(0xFF92A199),
    surfaceContainer = Color(0xFF141A17),
    surfaceContainerHigh = Color(0xFF1D2621),
    outline = Color(0xFF28352E),
    error = Color(0xFFE88B84)
)

private val AuraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 26.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.5.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.2.sp
    )
)

@Composable
fun AuraTheme(
    preset: AppThemePreset = AppThemePreset.OBSIDIAN_GRAPHITE,
    content: @Composable () -> Unit
) {
    val scheme = when (preset) {
        AppThemePreset.OBSIDIAN_GRAPHITE -> ObsidianGraphiteColors
        AppThemePreset.GEMINI_MIDNIGHT -> GeminiMidnightColors
        AppThemePreset.WARM_ALABASTER -> WarmAlabasterColors
        AppThemePreset.SAGE_TERMINAL -> SageTerminalColors
    }
    MaterialTheme(
        colorScheme = scheme,
        typography = AuraTypography,
        content = content
    )
}
