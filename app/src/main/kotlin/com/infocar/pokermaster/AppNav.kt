package com.infocar.pokermaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.feature.lobby.LobbyScreen
import com.infocar.pokermaster.feature.onboarding.OnboardingPrefs
import com.infocar.pokermaster.feature.onboarding.OnboardingScreen
import com.infocar.pokermaster.feature.table.TableScreen
import kotlinx.coroutines.delay

private object Routes {
    const val SPLASH = "splash"
    const val ONBOARDING = "onboarding"
    const val LOBBY = "lobby"
    const val TABLE = "table/{mode}"
    fun table(mode: GameMode) = "table/${mode.name}"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val prefs = remember {
        ctx.getSharedPreferences(OnboardingPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    NavHost(navController = nav, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onReady = {
                val completed = prefs.getBoolean(OnboardingPrefs.KEY_COMPLETED, false)
                val dest = if (completed) Routes.LOBBY else Routes.ONBOARDING
                nav.navigate(dest) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onComplete = { result ->
                prefs.edit().apply {
                    putBoolean(OnboardingPrefs.KEY_COMPLETED, true)
                    putString(OnboardingPrefs.KEY_NICKNAME, result.nickname)
                    apply()
                }
                nav.navigate(Routes.LOBBY) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.LOBBY) {
            LobbyScreen(onSelectMode = { mode ->
                // M3 MVP: HOLDEM_NL 만 지원 — 다른 모드는 로비에서 lock 예정
                if (mode == GameMode.HOLDEM_NL) {
                    nav.navigate(Routes.table(mode))
                }
            })
        }
        composable(
            route = Routes.TABLE,
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val modeName = entry.arguments?.getString("mode") ?: GameMode.HOLDEM_NL.name
            val mode = runCatching { GameMode.valueOf(modeName) }.getOrDefault(GameMode.HOLDEM_NL)
            TableScreen(
                mode = mode,
                onExit = {
                    nav.navigate(Routes.LOBBY) {
                        popUpTo(Routes.LOBBY) { inclusive = true }
                    }
                },
            )
        }
    }
}

@Composable
private fun SplashScreen(onReady: () -> Unit) {
    // M0: 단순 800ms 지연. 추후 M4 에서 모델 워밍업 / RNG 자가검증 / DB 점검 추가.
    LaunchedEffect(Unit) {
        delay(800L)
        onReady()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(id = R.string.splash_title),
            style = MaterialTheme.typography.displayLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
    }
}
