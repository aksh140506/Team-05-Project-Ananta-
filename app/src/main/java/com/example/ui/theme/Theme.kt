package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = TerracottaOrange,
    onPrimary = WarmCreamSurface,
    primaryContainer = SoftApricotContainer,
    onPrimaryContainer = DeepApricotInk,
    secondary = SageMeadowGreen,
    onSecondary = WarmCreamSurface,
    secondaryContainer = MintSageContainer,
    onSecondaryContainer = DeepSageInk,
    tertiary = HarvestAmber,
    onTertiary = WarmCreamSurface,
    tertiaryContainer = SoftAmberContainer,
    onTertiaryContainer = DeepAmberInk,
    error = WarmCrimson,
    onError = WarmCreamSurface,
    errorContainer = SoftRoseContainer,
    onErrorContainer = DeepCrimsonInk,
    background = WarmLinenBg,
    onBackground = EspressoInk,
    surface = WarmCreamSurface,
    onSurface = EspressoInk,
    surfaceVariant = SandstoneVariant,
    onSurfaceVariant = WarmUmber,
    surfaceContainerLow = WarmLinenBg,
    surfaceContainer = SandstoneVariant,
    surfaceContainerHigh = OatContainer,
    outline = WarmOutline
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkOrangePrimary,
    onPrimary = DeepApricotInk,
    primaryContainer = DeepApricotInk,
    onPrimaryContainer = SoftApricotContainer,
    secondary = DarkSageSecondary,
    onSecondary = DeepSageInk,
    secondaryContainer = DeepSageInk,
    onSecondaryContainer = MintSageContainer,
    tertiary = DarkAmberTertiary,
    onTertiary = DeepAmberInk,
    tertiaryContainer = DeepAmberInk,
    onTertiaryContainer = SoftAmberContainer,
    error = WarmCrimson,
    onError = WarmCreamSurface,
    background = DarkEspressoBg,
    onBackground = DarkCreamInk,
    surface = DarkBarkSurface,
    onSurface = DarkCreamInk,
    surfaceVariant = DarkSandstoneVariant,
    onSurfaceVariant = DarkWarmMuted,
    outline = DarkWarmMuted
)

val GraminSetuShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun GraminSetuTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = GraminSetuTypography,
        shapes = GraminSetuShapes,
        content = content
    )
}
