package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

private val IsaakingDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianVoid,
    primaryContainer = ElectricCyanDim,
    onPrimaryContainer = TextPrimary,
    secondary = CinemaAmber,
    onSecondary = ObsidianVoid,
    secondaryContainer = ElevatedCard,
    onSecondaryContainer = CinemaAmber,
    tertiary = NeonViolet,
    onTertiary = TextPrimary,
    tertiaryContainer = ElevatedCard,
    onTertiaryContainer = NeonViolet,
    background = ObsidianCanvas,
    onBackground = TextPrimary,
    surface = StudioSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedCard,
    onSurfaceVariant = TextSecondary,
    outline = StudioOutline,
    outlineVariant = StudioOutlineSubtle,
    error = CoralPulse,
    onError = TextPrimary
)

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun IsaakingTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = IsaakingDarkColorScheme,
        typography = Typography,
        shapes = StudioShapes,
        content = content
    )
}
