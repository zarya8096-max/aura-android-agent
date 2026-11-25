package com.aura.agent.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.aura.agent.data.AppThemePreset

val GeminiDarkScheme = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF0842A0),
    onPrimaryContainer = Color(0xFFD3E3FD),
    secondary = Color(0xFFC58AF9),
    onSecondary = Color(0xFF2E1065),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1E1F20),
    onSurfaceVariant = Color(0xFF9AA0A6),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    outline = Color(0xFF333538),
    error = Color(0xFFF28B82)
)

val GeminiLightScheme = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF7C3AED),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF0F4F9),
    onSurfaceVariant = Color(0xFF5F6368),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    outline = Color(0xFFE0E3E7),
    error = Color(0xFFD93025)
)

private val ObsidianDarkScheme = darkColorScheme(
    primary = Color(0xFF7AB8FF),
    onPrimary = Color(0xFF071E3D),
    primaryContainer = Color(0xFF163253),
    onPrimaryContainer = Color(0xFFD6E9FF),
    secondary = Color(0xFFE2C290),
    onSecondary = Color(0xFF2E2210),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFECEEF2),
    surface = Color(0xFF0E0F12),
    onSurface = Color(0xFFECEEF2),
    surfaceVariant = Color(0xFF17191E),
    onSurfaceVariant = Color(0xFF98A0AD),
    surfaceContainer = Color(0xFF17191E),
    surfaceContainerHigh = Color(0xFF21242B),
    outline = Color(0xFF2B2F38),
    error = Color(0xFFEA8A8A)
)

private val SageDarkScheme = darkColorScheme(
    primary = Color(0xFF7EC4A0),
    onPrimary = Color(0xFF0B2619),
    primaryContainer = Color(0xFF183A29),
    onPrimaryContainer = Color(0xFFC8F0DA),
    secondary = Color(0xFFD4BE8D),
    onSecondary = Color(0xFF2A2314),
    background = Color(0xFF0F1311),
    onBackground = Color(0xFFE7EDE9),
    surface = Color(0xFF0F1311),
    onSurface = Color(0xFFE7EDE9),
    surfaceVariant = Color(0xFF181E1B),
    onSurfaceVariant = Color(0xFF92A199),
    surfaceContainer = Color(0xFF181E1B),
    surfaceContainerHigh = Color(0xFF222A25),
    outline = Color(0xFF2C3831),
    error = Color(0xFFE88B84)
)

private val AuraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 30.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
        letterSpacing = (-0.1).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 15.5.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.5.sp,
        lineHeight = 24.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp
    )
)

@Composable
fun AuraTheme(
    preset: AppThemePreset = AppThemePreset.GEMINI_MIDNIGHT,
    content: @Composable () -> Unit
) {
    val isDark = preset.isDark
    val scheme = when (preset) {
        AppThemePreset.WARM_ALABASTER -> GeminiLightScheme
        AppThemePreset.GEMINI_MIDNIGHT -> GeminiDarkScheme
        AppThemePreset.OBSIDIAN_GRAPHITE -> ObsidianDarkScheme
        AppThemePreset.SAGE_TERMINAL -> SageDarkScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }
        }
    }

    MaterialTheme(
        colorScheme = scheme,
        typography = AuraTypography,
        content = content
    )
}
