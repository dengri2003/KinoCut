package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StudioDarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    onPrimary = ObsidianBg,
    primaryContainer = DeepCyanContainer,
    onPrimaryContainer = ElectricCyan,
    secondary = AmberGold,
    onSecondary = ObsidianBg,
    secondaryContainer = DeepAmberContainer,
    onSecondaryContainer = AmberGold,
    tertiary = VioletKey,
    onTertiary = TextPrimary,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = ConsoleSurface,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedConsole,
    onSurfaceVariant = TextSecondary,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ConsoleSurface,
    surfaceContainer = ElevatedConsole,
    surfaceContainerHigh = CardConsole,
    outline = HairlineBorder,
    outlineVariant = ColorTokens.SubtleBorder,
    error = CoralCrimson,
    onError = TextPrimary
)

private object ColorTokens {
    val SubtleBorder = androidx.compose.ui.graphics.Color(0xFF1E222D)
}

val StudioShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(26.dp)
)

@Composable
fun KinoCutTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StudioDarkColorScheme,
        typography = KinoCutTypography,
        shapes = StudioShapes,
        content = content
    )
}
