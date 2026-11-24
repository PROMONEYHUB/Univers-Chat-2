package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val IosDarkColorScheme = darkColorScheme(
    primary = IosDarkBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueContainerDark,
    onPrimaryContainer = IosOnBlueContainerDark,
    secondary = IosDarkRed,
    onSecondary = Color.White,
    secondaryContainer = IosRedContainerDark,
    onSecondaryContainer = IosOnRedContainerDark,
    tertiary = IosDarkGreen,
    onTertiary = Color.White,
    tertiaryContainer = IosGreenContainerDark,
    onTertiaryContainer = IosOnGreenContainerDark,
    background = IosDarkBackground,
    onBackground = IosDarkLabel,
    surface = IosDarkCardSurface,
    onSurface = IosDarkLabel,
    surfaceVariant = IosDarkFill,
    onSurfaceVariant = IosDarkSecondaryLabel,
    outline = IosDarkSeparator,
    outlineVariant = IosDarkHairline,
    error = IosDarkRed,
    onError = Color.White,
    errorContainer = ErrorContainerDark,
    onErrorContainer = IosOnRedContainerDark
)

private val IosLightColorScheme = lightColorScheme(
    primary = IosBlue,
    onPrimary = Color.White,
    primaryContainer = IosBlueContainerLight,
    onPrimaryContainer = IosOnBlueContainerLight,
    secondary = IosRed,
    onSecondary = Color.White,
    secondaryContainer = IosRedContainerLight,
    onSecondaryContainer = IosOnRedContainerLight,
    tertiary = IosGreen,
    onTertiary = Color.White,
    tertiaryContainer = IosGreenContainerLight,
    onTertiaryContainer = IosOnGreenContainerLight,
    background = IosLightGroupedBackground,
    onBackground = IosLightLabel,
    surface = IosLightCardSurface,
    onSurface = IosLightLabel,
    surfaceVariant = IosLightFill,
    onSurfaceVariant = IosLightSecondaryLabel,
    outline = IosLightSeparator,
    outlineVariant = IosLightHairline,
    error = IosRed,
    onError = Color.White,
    errorContainer = ErrorContainerLight,
    onErrorContainer = IosOnRedContainerLight
)

val PulseShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun PulseChatTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) IosDarkColorScheme else IosLightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = PulseShapes,
        content = content
    )
}
