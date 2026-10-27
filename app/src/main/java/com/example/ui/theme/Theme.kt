package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val VaultDarkColorScheme = darkColorScheme(
    primary = VaultCyan,
    onPrimary = Color(0xFF00262C),
    primaryContainer = Color(0xFF083344),
    onPrimaryContainer = Color(0xFFA5F3FC),
    secondary = CalcAmber,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF431407),
    onSecondaryContainer = Color(0xFFFFEDD5),
    tertiary = AmethystNote,
    onTertiary = Color.White,
    background = ObsidianBackground,
    onBackground = TextPrimaryDark,
    surface = ObsidianSurface,
    onSurface = TextPrimaryDark,
    surfaceVariant = ObsidianSurfaceElevated,
    onSurfaceVariant = TextSecondaryDark,
    error = RubyDanger,
    onError = Color.White
)

private val DisguiseLightColorScheme = lightColorScheme(
    primary = CalcAmber,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFEDD5),
    onPrimaryContainer = Color(0xFF7C2D12),
    secondary = Color(0xFF0284C7),
    onSecondary = Color.White,
    background = LightCalcBackground,
    onBackground = Color(0xFF0F172A),
    surface = LightCalcSurface,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = LightCalcKeyAction,
    onSurfaceVariant = Color(0xFF475569),
    error = RubyDanger,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    useLightDisguise: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useLightDisguise) DisguiseLightColorScheme else VaultDarkColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
