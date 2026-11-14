package com.nova.designsystem.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw Nova palette. Values are taken 1:1 from the Nova design canvas. */
object NovaPalette {
    // Brand — cobalt (rider primary)
    val Cobalt = Color(0xFF3549D6)
    val CobaltContainer = Color(0xFFDFE0FF)
    val OnCobaltContainer = Color(0xFF0A1A7A)
    val Periwinkle = Color(0xFFE0E0F9)
    val OnPeriwinkle = Color(0xFF191A2C)

    // Coral (tertiary)
    val Coral = Color(0xFFA33B12)
    val CoralContainer = Color(0xFFFFDBCF)
    val OnCoralContainer = Color(0xFF3A0B00)
    val CoralBright = Color(0xFFFFB59E)

    // Volt (driver primary, also used as an accent in rider)
    val Volt = Color(0xFFCFF24A)
    val OnVolt = Color(0xFF1A2400)
    val Olive = Color(0xFF4A6400)

    // Success
    val Mint = Color(0xFFB7F1CC)
    val OnMint = Color(0xFF00210E)
    val Green = Color(0xFF1B6C3A)
    val GreenOnboarding = Color(0xFF0B7A4B)

    // Neutrals (M3 tonal surfaces)
    val Surface = Color(0xFFFBF8FF)
    val SurfaceContainerLow = Color(0xFFF5F2FC)
    val SurfaceContainer = Color(0xFFEFEDF7)
    val SurfaceContainerHigh = Color(0xFFE9E7F2)
    val SurfaceContainerHighest = Color(0xFFE3E1EC)
    val NavBar = Color(0xFFF2F0FA)
    val OnSurface = Color(0xFF1B1B21)
    val OnSurfaceVariant = Color(0xFF464652)
    val Outline = Color(0xFF767680)
    val OutlineVariant = Color(0xFFC6C5D0)
    val Ink = Color(0xFF1B1B21)

    // Error
    val Error = Color(0xFFBA1A1A)
    val ErrorContainer = Color(0xFFFFDAD6)
    val OnErrorContainer = Color(0xFF410002)

    // Map
    val MapLand = Color(0xFFEEF0F3)
    val MapWater = Color(0xFFCFE0F7)
    val MapPark = Color(0xFFD6EBD0)
    val MapRoad = Color(0xFFFFFFFF)

    // Plate
    val PlateYellow = Color(0xFFFFF6CC)
}

internal val RiderLightColorScheme: ColorScheme = lightColorScheme(
    primary = NovaPalette.Cobalt,
    onPrimary = Color.White,
    primaryContainer = NovaPalette.CobaltContainer,
    onPrimaryContainer = NovaPalette.OnCobaltContainer,
    secondary = Color(0xFF5B5D72),
    onSecondary = Color.White,
    secondaryContainer = NovaPalette.Periwinkle,
    onSecondaryContainer = NovaPalette.OnPeriwinkle,
    tertiary = NovaPalette.Coral,
    onTertiary = Color.White,
    tertiaryContainer = NovaPalette.CoralContainer,
    onTertiaryContainer = NovaPalette.OnCoralContainer,
    error = NovaPalette.Error,
    onError = Color.White,
    errorContainer = NovaPalette.ErrorContainer,
    onErrorContainer = NovaPalette.OnErrorContainer,
    background = NovaPalette.Surface,
    onBackground = NovaPalette.OnSurface,
    surface = NovaPalette.Surface,
    onSurface = NovaPalette.OnSurface,
    surfaceVariant = NovaPalette.SurfaceContainerHighest,
    onSurfaceVariant = NovaPalette.OnSurfaceVariant,
    outline = NovaPalette.Outline,
    outlineVariant = NovaPalette.OutlineVariant,
    inverseSurface = Color(0xFF313036),
    inverseOnSurface = Color(0xFFF3EFF7),
    inversePrimary = Color(0xFFB8C3FF),
    surfaceBright = NovaPalette.Surface,
    surfaceDim = Color(0xFFDBD9E3),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = NovaPalette.SurfaceContainerLow,
    surfaceContainer = NovaPalette.SurfaceContainer,
    surfaceContainerHigh = NovaPalette.SurfaceContainerHigh,
    surfaceContainerHighest = NovaPalette.SurfaceContainerHighest,
    scrim = Color.Black,
)

/** Brand colours that Material's [ColorScheme] has no role for. */
@Immutable
data class NovaExtendedColors(
    val success: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    val volt: Color,
    val onVolt: Color,
    val coralBright: Color,
    val navBar: Color,
    val plate: Color,
    val mapLand: Color,
    val mapWater: Color,
    val mapPark: Color,
    val mapRoad: Color,
    val onboardingGreen: Color,
)

internal val RiderExtendedColors = NovaExtendedColors(
    success = NovaPalette.Green,
    successContainer = NovaPalette.Mint,
    onSuccessContainer = NovaPalette.OnMint,
    volt = NovaPalette.Volt,
    onVolt = NovaPalette.OnVolt,
    coralBright = NovaPalette.CoralBright,
    navBar = NovaPalette.NavBar,
    plate = NovaPalette.PlateYellow,
    mapLand = NovaPalette.MapLand,
    mapWater = NovaPalette.MapWater,
    mapPark = NovaPalette.MapPark,
    mapRoad = NovaPalette.MapRoad,
    onboardingGreen = NovaPalette.GreenOnboarding,
)

val LocalNovaColors = staticCompositionLocalOf { RiderExtendedColors }
