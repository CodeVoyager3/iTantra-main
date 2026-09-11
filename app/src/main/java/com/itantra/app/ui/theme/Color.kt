package com.itantra.app.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// =========================================================================
// iTantra Clean Tactical Bright Design System (Matching Reference UI)
// Primary Accent: Electric Royal Blue #2563EB
// High-visibility Emergency SOS: Crimson Red #EF4444
// Background: Soft Bright Air #F8FAFC
// =========================================================================

// Primary Brand & Accents
val AccentBlue = Color(0xFF2563EB)
val AccentBlueHover = Color(0xFF1D4ED8)
val AccentBlueLight = Color(0xFF3B82F6)
val AccentBlueContainer = Color(0xFFEFF6FF)

// Emergency Red (SOS)
val SosRed = Color(0xFFEF4444)
val SosRedDark = Color(0xFFDC2626)
val SosRedLight = Color(0xFFF87171)
val SosRedRing1 = Color(0x33EF4444)
val SosRedRing2 = Color(0x1AEF4444)
val SosRedContainer = Color(0xFFFEE2E2)

// Status & Mesh Accents
val MeshGreen = Color(0xFF10B981)
val MeshGreenDark = Color(0xFF059669)
val MeshGreenContainer = Color(0xFFECFDF5)
val MeshGreenText = Color(0xFF047857)

val BadgeMintContainer = Color(0xFFDCFCE7)
val BadgeMintText = Color(0xFF15803D)

val BadgeIndigoContainer = Color(0xFFEEF2FF)
val BadgeIndigoText = Color(0xFF4338CA)

val RescueAmber = Color(0xFFF59E0B)
val RescueAmberContainer = Color(0xFFFEF3C7)
val RescueAmberText = Color(0xFFB45309)

// Light Theme Palette (Default & Reference)
val LightBackground = Color(0xFFF8FAFC)
val LightSurface = Color(0xFFFFFFFF)
val LightPrimaryText = Color(0xFF0F172A)
val LightSecondaryText = Color(0xFF64748B)
val LightOutline = Color(0xFFE2E8F0)
val LightOutlineFocused = Color(0xFF3B82F6)
val LightCardShadow = Color(0x080F172A)

// Dark Theme Palette (Fallback / Night Mode)
val DarkBackground = Color(0xFF090D16)
val DarkSurface = Color(0xFF131A29)
val DarkPrimaryText = Color(0xFFF1F5F9)
val DarkSecondaryText = Color(0xFF94A3B8)
val DarkOutline = Color(0xFF1E293B)
val DarkAccentContainer = Color(0xFF1E293B)

@Immutable
data class MinimalColors(
    val background: Color,
    val surface: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val outline: Color,
    val accent: Color,
    val accentContainer: Color,
    val error: Color,
    val errorContainer: Color,
    val cardSecondaryBg: Color,
    val badgeMintContainer: Color,
    val badgeMintText: Color,
    val badgeBlueContainer: Color,
    val badgeBlueText: Color,
    val badgePurpleContainer: Color,
    val badgePurpleText: Color,
    val badgeAmberContainer: Color,
    val badgeAmberText: Color,
    val isDark: Boolean
)

val LocalMinimalColors = staticCompositionLocalOf {
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

val MinimalColorsInstance: MinimalColors
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalMinimalColors.current

