package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = BrandNova,
    onPrimary = Color.White,
    primaryContainer = BrandNovaSoft,
    onPrimaryContainer = BrandNovaGlow,
    secondary = BrandNovaHover,
    onSecondary = Color.White,
    secondaryContainer = DarkSurfaceContainer,
    onSecondaryContainer = DarkInkPrimary,
    tertiary = AccentAmber,
    onTertiary = Color.Black,
    background = DarkSurface,
    onBackground = DarkInkPrimary,
    surface = DarkSurface,
    onSurface = DarkInkPrimary,
    surfaceVariant = DarkSurfaceSubtle,
    onSurfaceVariant = DarkInkSecondary,
    surfaceContainer = DarkSurfaceContainer,
    outline = DarkBorderMedium,
    outlineVariant = DarkBorderHairline,
    error = AccentRed,
    onError = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BrandNova,
    onPrimary = Color.White,
    primaryContainer = BrandNovaSoft,
    onPrimaryContainer = BrandNova,
    secondary = BrandNovaHover,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaceContainer,
    onSecondaryContainer = LightInkPrimary,
    tertiary = AccentAmber,
    onTertiary = Color.Black,
    background = LightSurface,
    onBackground = LightInkPrimary,
    surface = LightSurface,
    onSurface = LightInkPrimary,
    surfaceVariant = LightSurfaceSubtle,
    onSurfaceVariant = LightInkSecondary,
    surfaceContainer = LightSurfaceContainer,
    outline = LightBorderMedium,
    outlineVariant = LightBorderHairline,
    error = AccentRed,
    onError = Color.White
)

object FestoTheme {
    val colors: FestoExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFestoColors.current

    @Composable
    @ReadOnlyComposable
    fun novaRadialBrush(): Brush {
        return Brush.radialGradient(
            colors = listOf(
                BrandNovaGlow,
                BrandNova,
                BrandNovaHover
            )
        )
    }

    @Composable
    @ReadOnlyComposable
    fun voiceOrbBrush(): Brush {
        val isDark = LocalFestoColors.current.isDark
        return if (isDark) {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF1DB),
                    BrandNova,
                    Color(0xFF8F4225),
                    Color(0xFF381A0E)
                )
            )
        } else {
            Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFF3E0),
                    BrandNova,
                    Color(0xFFA8512E)
                )
            )
        }
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val extendedColors = if (darkTheme) {
        FestoExtendedColors(
            brandNova = BrandNova,
            brandNovaSoft = BrandNovaSoft,
            brandNovaLine = BrandNovaLine,
            brandNovaGlow = BrandNovaGlow,
            borderHairline = DarkBorderHairline,
            borderMedium = DarkBorderMedium,
            borderStrong = DarkBorderStrong,
            surfaceSubtle = DarkSurfaceSubtle,
            surfaceContainer = DarkSurfaceContainer,
            surfaceDialog = DarkSurfaceDialog,
            inkTertiary = DarkInkTertiary,
            accentGreen = AccentGreen,
            accentBlue = AccentBlue,
            accentAmber = AccentAmber,
            accentAmberSoft = AccentAmberSoft,
            accentPurple = AccentPurple,
            isDark = true
        )
    } else {
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

    CompositionLocalProvider(LocalFestoColors provides extendedColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
