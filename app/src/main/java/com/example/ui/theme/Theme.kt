package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import com.example.data.model.ThemeMode

private val LTDarkColorScheme = darkColorScheme(
    primary = ElectricGreen,
    onPrimary = OnElectricGreen,
    primaryContainer = ElectricGreenContainer,
    onPrimaryContainer = ElectricGreen,
    secondary = PremiumBlue,
    onSecondary = OnPremiumBlue,
    secondaryContainer = PremiumBlueContainer,
    onSecondaryContainer = PremiumBlueLight,
    tertiary = AmberGold,
    background = DeepBlack,
    onBackground = TextPrimaryDark,
    surface = DarkCharcoal,
    onSurface = TextPrimaryDark,
    surfaceVariant = ElevatedCharcoal,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainerLowest = DeepBlack,
    surfaceContainerLow = DarkCharcoal,
    surfaceContainer = ElevatedCharcoal,
    surfaceContainerHigh = CardCharcoal,
    outline = SubtleBorderDark,
    outlineVariant = SubtleBorderDark.copy(alpha = 0.6f),
    error = CoralError,
    onError = DeepBlack,
    errorContainer = CoralErrorContainer,
    onErrorContainer = CoralError
)

private val LTLightColorScheme = lightColorScheme(
    primary = ElectricGreenLightMode,
    onPrimary = LightSurface,
    primaryContainer = ElectricGreenContainerLight,
    onPrimaryContainer = OnElectricGreen,
    secondary = PremiumBlueLightMode,
    onSecondary = LightSurface,
    secondaryContainer = PremiumBlueContainerLight,
    onSecondaryContainer = PremiumBlueLightMode,
    tertiary = AmberGold,
    background = LightBackground,
    onBackground = TextPrimaryLight,
    surface = LightSurface,
    onSurface = TextPrimaryLight,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainerLowest = LightSurface,
    surfaceContainerLow = LightBackground,
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightSurfaceVariant,
    outline = LightBorder,
    outlineVariant = LightBorder.copy(alpha = 0.6f),
    error = CoralError,
    onError = LightSurface
)

val LTPlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LTPlayerTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit
) {
    val useDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    MaterialTheme(
        colorScheme = if (useDark) LTDarkColorScheme else LTLightColorScheme,
        typography = LTPlayerTypography,
        shapes = LTPlayerShapes,
        content = content
    )
}
