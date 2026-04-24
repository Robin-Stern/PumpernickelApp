package com.pumpernickel.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Accent color presets (matches iOS ThemeManager) ──

data class AccentPreset(val key: String, val name: String, val color: Color)

val accentPresets = listOf(
    AccentPreset("green", "Grün", Color(0xFF66BB6A)),
    AccentPreset("blue", "Blau", Color(0xFF2196F3)),
    AccentPreset("indigo", "Indigo", Color(0xFF5C6BC0)),
    AccentPreset("purple", "Violett", Color(0xFF9C27B0)),
    AccentPreset("pink", "Pink", Color(0xFFE91E63)),
    AccentPreset("red", "Rot", Color(0xFFF44336)),
    AccentPreset("orange", "Orange", Color(0xFFFF9800)),
    AccentPreset("teal", "Teal", Color(0xFF009688)),
)

// ── Color scheme generation from accent color ──

private fun lightSchemeFrom(accent: Color): androidx.compose.material3.ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = accent.copy(alpha = 0.15f).compositeOver(Color.White),
    onPrimaryContainer = accent.darken(0.4f),
    secondary = accent.desaturate(0.3f),
    onSecondary = Color.White,
    secondaryContainer = accent.copy(alpha = 0.08f).compositeOver(Color.White),
    onSecondaryContainer = accent.darken(0.5f),
    tertiary = Color(0xFFFF9800),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFF3E0),
    onTertiaryContainer = Color(0xFFE65100),
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
    surface = Color.White,
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5F5F5),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = accent.lighten(0.3f),
    background = Color.White,
    onBackground = Color(0xFF1C1B1F),
)

private fun darkSchemeFrom(accent: Color): androidx.compose.material3.ColorScheme = darkColorScheme(
    primary = accent.lighten(0.2f),
    onPrimary = accent.darken(0.6f),
    primaryContainer = accent.darken(0.3f),
    onPrimaryContainer = accent.lighten(0.4f),
    secondary = accent.desaturate(0.3f).lighten(0.2f),
    onSecondary = accent.darken(0.5f),
    secondaryContainer = accent.desaturate(0.3f).darken(0.4f),
    onSecondaryContainer = accent.desaturate(0.3f).lighten(0.4f),
    tertiary = Color(0xFFFFB74D),
    onTertiary = Color(0xFF4E2600),
    tertiaryContainer = Color(0xFF6D3A00),
    onTertiaryContainer = Color(0xFFFFDDB3),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2B2930),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = accent,
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
)

// ── Color utility functions ──

private fun Color.compositeOver(background: Color): Color {
    val a = alpha
    return Color(
        red = red * a + background.red * (1f - a),
        green = green * a + background.green * (1f - a),
        blue = blue * a + background.blue * (1f - a),
        alpha = 1f
    )
}

private fun Color.lighten(factor: Float): Color = Color(
    red = (red + (1f - red) * factor).coerceIn(0f, 1f),
    green = (green + (1f - green) * factor).coerceIn(0f, 1f),
    blue = (blue + (1f - blue) * factor).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Color.darken(factor: Float): Color = Color(
    red = (red * (1f - factor)).coerceIn(0f, 1f),
    green = (green * (1f - factor)).coerceIn(0f, 1f),
    blue = (blue * (1f - factor)).coerceIn(0f, 1f),
    alpha = alpha
)

private fun Color.desaturate(factor: Float): Color {
    val luminance = 0.299f * red + 0.587f * green + 0.114f * blue
    return Color(
        red = (red + (luminance - red) * factor).coerceIn(0f, 1f),
        green = (green + (luminance - green) * factor).coerceIn(0f, 1f),
        blue = (blue + (luminance - blue) * factor).coerceIn(0f, 1f),
        alpha = alpha
    )
}

// ── Resolve accent color from key ──

fun resolveAccentColor(key: String): Color =
    accentPresets.firstOrNull { it.key == key }?.color ?: accentPresets.first().color

// ── Theme composable ──

@Composable
fun PumpernickelTheme(
    themeMode: String = "system",
    accentColorKey: String = "green",
    content: @Composable () -> Unit
) {
    val isDark = when (themeMode) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }

    val accent = resolveAccentColor(accentColorKey)
    val colorScheme = if (isDark) darkSchemeFrom(accent) else lightSchemeFrom(accent)

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
