package com.nova.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.unit.dp

internal val NovaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
    largeIncreased = RoundedCornerShape(24.dp),
    extraLargeIncreased = RoundedCornerShape(32.dp),
    extraExtraLarge = RoundedCornerShape(48.dp),
)

/**
 * Nova rider theme: Material 3 Expressive with the brand colour scheme (dynamic colour is off
 * on purpose so Nova's cobalt/coral/volt identity is consistent on every phone), Roboto Flex
 * type, Nova shapes and the expressive spring-based [MotionScheme].
 */
@Composable
fun NovaTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalNovaColors provides RiderExtendedColors) {
        MaterialExpressiveTheme(
            colorScheme = RiderLightColorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = NovaShapes,
            typography = NovaTypography,
            content = content,
        )
    }
}

/** Access Nova's extra brand colours: `NovaTheme.colors.successContainer`. */
object NovaTheme {
    val colors: NovaExtendedColors
        @Composable @ReadOnlyComposable get() = LocalNovaColors.current
}

/** Short alias for the Material colour scheme inside Nova screens. */
val scheme
    @Composable @ReadOnlyComposable get() = MaterialTheme.colorScheme
