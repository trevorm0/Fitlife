package com.example.fitwell.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val FitLifeColorScheme = darkColorScheme(
    primary = FitGreen, onPrimary = Midnight,
    primaryContainer = CardElevated, onPrimaryContainer = TextPrimary,
    secondary = FitCyan, onSecondary = Midnight,
    tertiary = SleepPurple, onTertiary = Midnight,
    background = Midnight, onBackground = TextPrimary,
    surface = NavyDark, onSurface = TextPrimary,
    surfaceVariant = CardNavy, onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = Midnight, surfaceContainerLow = NavyDark,
    surfaceContainer = CardNavy, surfaceContainerHigh = CardElevated, surfaceContainerHighest = CardElevated,
    outline = DividerBlue, outlineVariant = DividerBlue,
    error = HeartCoral, onError = Midnight
)

/** Shared spacing and corner conventions. */
object FitLifeDimens {
    val screenPadding = 16.dp
    val cardPadding = 16.dp
    val cardShape = RoundedCornerShape(18.dp)       // main cards 16-22
    val metricShape = RoundedCornerShape(16.dp)     // metric cards 14-18
    val buttonShape = RoundedCornerShape(28.dp)     // buttons 24-32
    val searchShape = RoundedCornerShape(24.dp)
    val fieldShape = RoundedCornerShape(16.dp)
}

/** FitLife is always dark: fixed palette, no dynamic colour. */
@Composable
fun FitLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = FitLifeColorScheme,
        typography = FitLifeTypography,
        shapes = Shapes(
            extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(14.dp),
            medium = RoundedCornerShape(18.dp), large = RoundedCornerShape(22.dp), extraLarge = RoundedCornerShape(28.dp)
        ),
        content = content
    )
}
