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

/**
 * 앱 테마.
 *
 * 기본 진입점은 [themeMode] 파라미터이며, 호환을 위해 [darkTheme] boolean overload도 유지한다.
 * 기본값은 [ThemeMode.DEFAULT] = LIGHT — 시스템이 다크여도 앱은 라이트로 시작.
 */
@Composable
fun PokerMasterTheme(
    themeMode: ThemeMode = ThemeMode.DEFAULT,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = PokerTypography,
        content = content,
    )
}
