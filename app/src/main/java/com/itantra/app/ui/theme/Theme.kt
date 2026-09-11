package com.itantra.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

private val MinimalLightColorScheme = lightColorScheme(
    primary = AccentBlue,
    onPrimary = Color.White,
    primaryContainer = AccentBlueContainer,
    onPrimaryContainer = AccentBlueHover,
    secondary = AccentBlueLight,
    onSecondary = Color.White,
    secondaryContainer = AccentBlueContainer,
    onSecondaryContainer = AccentBlue,
    tertiary = MeshGreen,
    onTertiary = Color.White,
    background = LightBackground,
    onBackground = LightPrimaryText,
    surface = LightSurface,
    onSurface = LightPrimaryText,
    surfaceVariant = LightSurface,
    onSurfaceVariant = LightSecondaryText,
    surfaceContainer = Color.White,
    surfaceContainerHigh = Color.White,
    surfaceContainerHighest = Color.White,
    surfaceContainerLow = Color(0xFFF8FAFC),
    surfaceContainerLowest = Color.White,
    surfaceTint = AccentBlue,
    outline = LightOutline,
    outlineVariant = LightOutline,
    error = SosRed,
    onError = Color.White,
    errorContainer = SosRedContainer,
    onErrorContainer = SosRedDark
)

private val MinimalDarkColorScheme = darkColorScheme(
    primary = AccentBlueLight,
    onPrimary = Color.White,
    primaryContainer = DarkAccentContainer,
    onPrimaryContainer = DarkPrimaryText,
    secondary = AccentBlueLight,
    onSecondary = Color.White,
    secondaryContainer = DarkAccentContainer,
    onSecondaryContainer = DarkPrimaryText,
    tertiary = MeshGreen,
    onTertiary = Color.White,
    background = DarkBackground,
    onBackground = DarkPrimaryText,
    surface = DarkSurface,
    onSurface = DarkPrimaryText,
    surfaceVariant = DarkSurface,
    onSurfaceVariant = DarkSecondaryText,
    surfaceContainer = Color(0xFF131A29),
    surfaceContainerHigh = Color(0xFF1A2234),
    surfaceContainerHighest = Color(0xFF222C42),
    surfaceContainerLow = Color(0xFF0F1522),
    surfaceContainerLowest = Color(0xFF090D16),
    surfaceTint = AccentBlueLight,
    outline = DarkOutline,
    outlineVariant = DarkOutline,
    error = SosRed,
    onError = Color.White,
    errorContainer = Color(0xFF451A1A),
    onErrorContainer = SosRed
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Default to bright theme per user directive
    content: @Composable () -> Unit
) {
    val minimalColors = if (darkTheme) {
        MinimalColors(
            background = DarkBackground,
            surface = DarkSurface,
            textPrimary = DarkPrimaryText,
            textSecondary = DarkSecondaryText,
            outline = DarkOutline,
            accent = AccentBlueLight,
            accentContainer = DarkAccentContainer,
            error = SosRed,
            errorContainer = Color(0xFF451A1A),
            cardSecondaryBg = Color(0xFF1E293B),
            badgeMintContainer = Color(0xFF064E3B),
            badgeMintText = Color(0xFF6EE7B7),
            badgeBlueContainer = Color(0xFF1E3A8A),
            badgeBlueText = Color(0xFF93C5FD),
            badgePurpleContainer = Color(0xFF4C1D95),
            badgePurpleText = Color(0xFFD8B4FE),
            badgeAmberContainer = Color(0xFF78350F),
            badgeAmberText = Color(0xFFFCD34D),
            isDark = true
        )
    } else {
        MinimalColors(
            background = LightBackground,
            surface = LightSurface,
            textPrimary = LightPrimaryText,
            textSecondary = LightSecondaryText,
            outline = LightOutline,
            accent = AccentBlue,
            accentContainer = AccentBlueContainer,
            error = SosRed,
            errorContainer = SosRedContainer,
            cardSecondaryBg = Color(0xFFF1F5F9),
            badgeMintContainer = Color(0xFFDCFCE7),
            badgeMintText = Color(0xFF15803D),
            badgeBlueContainer = Color(0xFFEFF6FF),
            badgeBlueText = Color(0xFF1D4ED8),
            badgePurpleContainer = Color(0xFFF3E8FF),
            badgePurpleText = Color(0xFF7E22CE),
            badgeAmberContainer = Color(0xFFFEF3C7),
            badgeAmberText = Color(0xFFB45309),
            isDark = false
        )
    }

    val colorScheme = if (darkTheme) MinimalDarkColorScheme else MinimalLightColorScheme

    CompositionLocalProvider(
        LocalMinimalColors provides minimalColors
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}

val MaterialTheme.minimalColors: MinimalColors
    @Composable
    @ReadOnlyComposable
    get() = LocalMinimalColors.current
