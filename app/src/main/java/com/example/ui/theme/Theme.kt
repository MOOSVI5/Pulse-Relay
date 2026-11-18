package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val PulseDarkColorScheme = darkColorScheme(
    primary = PulseCrimson,
    onPrimary = Color.White,
    primaryContainer = PulseCrimsonContainer,
    onPrimaryContainer = PulseOnCrimsonContainer,
    secondary = TelemetryCyan,
    onSecondary = DarkBackground,
    secondaryContainer = TelemetryCyanContainer,
    onSecondaryContainer = TelemetryOnCyanContainer,
    tertiary = SignalEmerald,
    onTertiary = DarkBackground,
    tertiaryContainer = SignalEmeraldContainer,
    onTertiaryContainer = SignalOnEmeraldContainer,
    background = DarkBackground,
    onBackground = DarkTextPrimary,
    surface = DarkSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    surfaceContainer = DarkSurfaceVariant,
    surfaceContainerHigh = DarkSurfaceElevated,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = Color(0xFFFF5C70),
    onError = DarkBackground
)

private val PulseLightColorScheme = lightColorScheme(
    primary = LightCrimson,
    onPrimary = Color.White,
    primaryContainer = LightCrimsonContainer,
    onPrimaryContainer = LightOnCrimsonContainer,
    secondary = LightCyan,
    onSecondary = Color.White,
    secondaryContainer = LightCyanContainer,
    onSecondaryContainer = LightOnCyanContainer,
    tertiary = LightEmerald,
    onTertiary = Color.White,
    tertiaryContainer = LightEmeraldContainer,
    onTertiaryContainer = LightOnEmeraldContainer,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = Color(0xFFDC2626),
    onError = Color.White
)

val PulseShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun PulseRelayTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) PulseDarkColorScheme else PulseLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = PulseTypography,
        shapes = PulseShapes,
        content = content
    )
}
