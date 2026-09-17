package com.example.orbit.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = GrassGreen,
    onPrimary = Color.White,
    primaryContainer = GrassGreenContainer,
    onPrimaryContainer = OnGrassGreenContainer,

    secondary = DustyBlue,
    onSecondary = Color.White,
    secondaryContainer = DustyBlueContainer,
    onSecondaryContainer = OnDustyBlueContainer,

    tertiary = MustardAmber,
    // Belo na amberu pada na 2.10:1, zato tamno mastilo
    onTertiary = WarmCharcoal,
    tertiaryContainer = MustardAmberContainer,
    onTertiaryContainer = OnMustardAmberContainer,

    // Terakota je treci akcenat i istovremeno boja greske, pa sve odbijanje ide u brendu
    error = Terracotta,
    onError = Color.White,
    errorContainer = TerracottaContainer,
    onErrorContainer = OnTerracottaContainer,

    background = WarmCream,
    onBackground = WarmCharcoal,
    surface = SoftOffWhite,
    onSurface = WarmCharcoal,
    surfaceVariant = WarmSand,
    onSurfaceVariant = WarmStone,

    surfaceContainerLowest = Color.White,
    surfaceContainerLow = SoftOffWhite,
    surfaceContainer = WarmCream,
    surfaceContainerHigh = WarmSandHigh,
    surfaceContainerHighest = WarmSand,
    surfaceDim = WarmSandDim,
    surfaceBright = SoftOffWhite,

    outline = WarmOutline,
    outlineVariant = WarmOutlineVariant,

    inverseSurface = WarmCharcoal,
    inverseOnSurface = WarmCream,
    inversePrimary = GrassGreenDark,
    scrim = Color.Black,
)

private val DarkColorScheme = darkColorScheme(
    primary = GrassGreenDark,
    onPrimary = OnGrassGreenDark,
    primaryContainer = GrassGreenContainerDark,
    onPrimaryContainer = OnGrassGreenContainerDark,

    secondary = DustyBlueDark,
    onSecondary = OnDustyBlueDark,
    secondaryContainer = DustyBlueContainerDark,
    onSecondaryContainer = OnDustyBlueContainerDark,

    tertiary = MustardAmberDark,
    onTertiary = OnMustardAmberDark,
    tertiaryContainer = MustardAmberContainerDark,
    onTertiaryContainer = OnMustardAmberContainerDark,

    error = TerracottaDark,
    onError = OnTerracottaDark,
    errorContainer = TerracottaContainerDark,
    onErrorContainer = OnTerracottaContainerDark,

    background = WarmBackgroundDark,
    onBackground = WarmInkDark,
    surface = WarmSurfaceDark,
    onSurface = WarmInkDark,
    surfaceVariant = WarmSurfaceVariantDark,
    onSurfaceVariant = WarmStoneDark,

    surfaceContainerLowest = WarmSurfaceLowestDark,
    surfaceContainerLow = WarmSurfaceLowDark,
    surfaceContainer = WarmSurfaceContainerDark,
    surfaceContainerHigh = WarmSurfaceHighDark,
    surfaceContainerHighest = WarmSurfaceHighestDark,
    surfaceDim = WarmBackgroundDark,
    surfaceBright = WarmSurfaceHighestDark,

    outline = WarmOutlineDark,
    outlineVariant = WarmOutlineVariantDark,

    inverseSurface = WarmInkDark,
    inverseOnSurface = WarmCharcoal,
    inversePrimary = GrassGreen,
    scrim = Color.Black,
)

@Composable
fun OrbitTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    /**
     * Dinamicke boje uzimaju paletu iz tapeta i pregaze brend, zato su iskljucene.
     * Ostaje kao prekidac, da se na odbrani vidi razlika.
     */
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    CompositionLocalProvider(LocalOrbitAccents provides accentsFor(darkTheme)) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = OrbitShapes,
            content = content,
        )
    }
}
