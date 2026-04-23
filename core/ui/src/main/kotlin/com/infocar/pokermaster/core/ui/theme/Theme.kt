package com.infocar.pokermaster.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightScheme = lightColorScheme(
    primary = PokerColors.Primary,
    onPrimary = PokerColors.OnDark,
    secondary = PokerColors.Accent,
    onSecondary = PokerColors.OnLight,
    error = PokerColors.Danger,
    background = PokerColors.BackgroundLight,
    onBackground = PokerColors.OnLight,
    surface = PokerColors.SurfaceLight,
    onSurface = PokerColors.OnLight,
)

private val DarkScheme = darkColorScheme(
    primary = PokerColors.Primary,
    onPrimary = PokerColors.OnDark,
    secondary = PokerColors.Accent,
    onSecondary = PokerColors.OnLight,
    error = PokerColors.Danger,
    background = PokerColors.BackgroundDark,
    onBackground = PokerColors.OnDark,
    surface = PokerColors.SurfaceDark,
    onSurface = PokerColors.OnDark,
)

@Composable
fun PokerMasterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = PokerTypography,
        content = content,
    )
}
