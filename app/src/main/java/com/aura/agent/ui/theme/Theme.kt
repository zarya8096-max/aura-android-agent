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

val GeminiBlue = Color(0xFF4285F4)
val GeminiPurple = Color(0xFF9B72CB)
val GeminiCoral = Color(0xFFD96570)
val GeminiGradientColors = listOf(GeminiBlue, GeminiPurple, GeminiCoral)

private val AuraDarkColors = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    onPrimary = Color(0xFF062E6F),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = Color(0xFFC2E7FF),
    secondary = Color(0xFFC58AF9),
    onSecondary = Color(0xFF2B124C),
    background = Color(0xFF131314),
    onBackground = Color(0xFFE3E3E3),
    surface = Color(0xFF131314),
    onSurface = Color(0xFFE3E3E3),
    surfaceVariant = Color(0xFF1E1F20),
    onSurfaceVariant = Color(0xFFC4C7C5),
    surfaceContainer = Color(0xFF1E1F20),
    surfaceContainerHigh = Color(0xFF282A2C),
    outline = Color(0xFF3C4043),
    error = Color(0xFFF28B82)
)

private val AuraLightColors = lightColorScheme(
    primary = Color(0xFF1A73E8),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD3E3FD),
    onPrimaryContainer = Color(0xFF041E49),
    secondary = Color(0xFF7B1FA2),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF1F1F1F),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F1F1F),
    surfaceVariant = Color(0xFFF0F4F9),
    onSurfaceVariant = Color(0xFF444746),
    surfaceContainer = Color(0xFFF0F4F9),
    surfaceContainerHigh = Color(0xFFE9EEF6),
    outline = Color(0xFFDADCE0),
    error = Color(0xFFB3261E)
)

private val AuraTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 30.sp,
        lineHeight = 38.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 26.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 23.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp
    )
)

@Composable
fun AuraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) AuraDarkColors else AuraLightColors,
        typography = AuraTypography,
        content = content
    )
}
