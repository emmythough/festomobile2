package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Brand Nova Core Palette
val BrandNova = Color(0xFFC96F4A)
val BrandNovaSoft = Color(0x2AC96F4A)
val BrandNovaLine = Color(0x59C96F4A)
val BrandNovaHover = Color(0xFFD67D58)
val BrandNovaGlow = Color(0xFFFFE3BD)

// Light Theme Tokens
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceSubtle = Color(0xFFF7F7F8)
val LightSurfaceContainer = Color(0xFFEFEFED)
val LightSurfaceDialog = Color(0xFFFAFAF9)

val LightInkPrimary = Color(0xFF1E1E1E)
val LightInkSecondary = Color(0xFF4A4A4A)
val LightInkTertiary = Color(0xFF757575)
val LightInkDisabled = Color(0xFFA8A8A8)

val LightBorderHairline = Color(0xFFE6DCD0)
val LightBorderMedium = Color(0xFFD6C5B0)
val LightBorderStrong = Color(0xFFB8A186)

// Dark Theme Tokens
val DarkSurface = Color(0xFF0D0D0D)
val DarkSurfaceSubtle = Color(0xFF171717)
val DarkSurfaceContainer = Color(0xFF212121)
val DarkSurfaceDialog = Color(0xFF1C1C1C)

val DarkInkPrimary = Color(0xFFF5F5F5)
val DarkInkSecondary = Color(0xFFD4D4D4)
val DarkInkTertiary = Color(0xFF9E9E9E)
val DarkInkDisabled = Color(0xFF5E5E5E)

val DarkBorderHairline = Color(0xFF3D3128)
val DarkBorderMedium = Color(0xFF52422F)
val DarkBorderStrong = Color(0xFF7A6448)

// Syntax Highlighting Tokens (code blocks in chat + memory browser).
// Mid-saturation tones for light surfaces (#FAFAF9 dialog), brighter pastels
// for the dark dialog surface (#1C1C1C) -- both keep every token class legible
// without leaving the Festo hue family.
val LightSyntaxKeyword = Color(0xFF7B1FA2)
val LightSyntaxString = Color(0xFF2E7D32)
val LightSyntaxComment = Color(0xFF8A8A8A)
val LightSyntaxNumber = Color(0xFFB45309)
val LightSyntaxFunction = Color(0xFF1565C0)
val LightSyntaxType = Color(0xFF0E7490)
val LightSyntaxOperator = Color(0xFF52525B)
val LightSyntaxAnnotation = Color(0xFF9A3412)
val LightSyntaxTag = Color(0xFFBE123C)
val LightSyntaxAttribute = Color(0xFF445588)
val LightSyntaxProperty = Color(0xFF366CA8)

val DarkSyntaxKeyword = Color(0xFFC792EA)
val DarkSyntaxString = Color(0xFFA5D6A7)
val DarkSyntaxComment = Color(0xFF8A8A8A)
val DarkSyntaxNumber = Color(0xFFF5B77E)
val DarkSyntaxFunction = Color(0xFF82AAFF)
val DarkSyntaxType = Color(0xFF4EC9B0)
val DarkSyntaxOperator = Color(0xFFB0B6C0)
val DarkSyntaxAnnotation = Color(0xFFF5A97F)
val DarkSyntaxTag = Color(0xFFF07178)
val DarkSyntaxAttribute = Color(0xFFA8B8D8)
val DarkSyntaxProperty = Color(0xFF74B6E8)

// Semantic Accents
val AccentGreen = Color(0xFF2E7D32)
val AccentGreenSoft = Color(0x262E7D32)
val AccentBlue = Color(0xFF1565C0)
val AccentBlueSoft = Color(0x261565C0)
val AccentAmber = Color(0xFFD97706)
val AccentAmberSoft = Color(0x26D97706)
val AccentPurple = Color(0xFF7B1FA2)
val AccentPurpleSoft = Color(0x267B1FA2)
val AccentRed = Color(0xFFDC2626)
val AccentRedSoft = Color(0x26DC2626)

@Immutable
data class FestoExtendedColors(
    val brandNova: Color,
    val brandNovaSoft: Color,
    val brandNovaLine: Color,
    val brandNovaGlow: Color,
    val borderHairline: Color,
    val borderMedium: Color,
    val borderStrong: Color,
    val surfaceSubtle: Color,
    val surfaceContainer: Color,
    val surfaceDialog: Color,
    val inkTertiary: Color,
    val accentGreen: Color,
    val accentBlue: Color,
    val accentAmber: Color,
    val accentAmberSoft: Color,
    val accentPurple: Color,
    val isDark: Boolean
)

val LocalFestoColors = staticCompositionLocalOf {
    FestoExtendedColors(
        brandNova = BrandNova,
        brandNovaSoft = BrandNovaSoft,
        brandNovaLine = BrandNovaLine,
        brandNovaGlow = BrandNovaGlow,
        borderHairline = LightBorderHairline,
        borderMedium = LightBorderMedium,
        borderStrong = LightBorderStrong,
        surfaceSubtle = LightSurfaceSubtle,
        surfaceContainer = LightSurfaceContainer,
        surfaceDialog = LightSurfaceDialog,
        inkTertiary = LightInkTertiary,
        accentGreen = AccentGreen,
        accentBlue = AccentBlue,
        accentAmber = AccentAmber,
        accentAmberSoft = AccentAmberSoft,
        accentPurple = AccentPurple,
        isDark = false
    )
}
