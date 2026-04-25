package com.infocar.pokermaster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
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
import com.infocar.pokermaster.di.LlmAdvisorEntryPoint
import com.infocar.pokermaster.feature.history.HandDetailScreen
import com.infocar.pokermaster.feature.history.HistoryListScreen
import com.infocar.pokermaster.feature.history.stats.StatsScreen
import com.infocar.pokermaster.feature.lobby.LobbyScreen
import com.infocar.pokermaster.feature.onboarding.OnboardingPrefs
import com.infocar.pokermaster.feature.onboarding.OnboardingScreen
import com.infocar.pokermaster.feature.table.TableScreen
import com.infocar.pokermaster.feature.table.settings.SettingsScreen
import com.infocar.pokermaster.model.ModelGateScreen
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay

private object Routes {
    const val SPLASH = "splash"
    const val MODEL_GATE = "modelGate"
    const val ONBOARDING = "onboarding"
    const val LOBBY = "lobby"
    const val TABLE = "table/{mode}/{seats}/{buyIn}"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    fun table(mode: GameMode, seats: Int, buyIn: Long) = "table/${mode.name}/$seats/$buyIn"
    fun historyDetail(id: Long) = "history/$id"
}

@Composable
fun AppNav() {
    val nav = rememberNavController()
    val ctx = LocalContext.current
    val prefs = remember {
        ctx.getSharedPreferences(OnboardingPrefs.PREFS_NAME, android.content.Context.MODE_PRIVATE)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.SPLASH,
        // safeDrawingPadding 제거 — status/navigation bar 영역까지 컨텐츠가 풀 그라데이션 배경으로
        // 차지. 각 화면에서 Scaffold 의 inner padding 또는 statusBarsPadding 으로 안전 영역 처리.
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(Routes.SPLASH) {
            SplashScreen(onReady = {
                nav.navigate(Routes.MODEL_GATE) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.MODEL_GATE) {
            ModelGateScreen(onReady = {
                val completed = prefs.getBoolean(OnboardingPrefs.KEY_COMPLETED, false)
                val dest = if (completed) Routes.LOBBY else Routes.ONBOARDING
                nav.navigate(dest) {
                    popUpTo(Routes.MODEL_GATE) { inclusive = true }
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
            LobbyScreen(
                onSelectMode = { mode, seats, buyIn ->
                    // 모든 정식 모드 지원 — HOLDEM_NL / SEVEN_STUD / SEVEN_STUD_HI_LO.
                    nav.navigate(Routes.table(mode, seats, buyIn))
                },
                // M5-C: 히스토리 진입점.
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                // M6-A: 설정 진입점.
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                // M6-B: 통계 진입점.
                onOpenStats = { nav.navigate(Routes.STATS) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { nav.popBackStack() },
                versionName = BuildConfig.VERSION_NAME,
            )
        }
        composable(Routes.STATS) {
            StatsScreen(onBack = { nav.popBackStack() })
        }
        composable(Routes.HISTORY) {
            HistoryListScreen(
                onBack = { nav.popBackStack() },
                onOpenDetail = { id -> nav.navigate(Routes.historyDetail(id)) },
            )
        }
        composable(
            route = Routes.HISTORY_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) {
            HandDetailScreen(onBack = { nav.popBackStack() })
        }
        composable(
            route = Routes.TABLE,
            arguments = listOf(
                navArgument("mode") { type = NavType.StringType },
                navArgument("seats") { type = NavType.IntType },
                navArgument("buyIn") { type = NavType.LongType },
            ),
        ) { entry ->
            val modeName = entry.arguments?.getString("mode") ?: GameMode.HOLDEM_NL.name
            val mode = runCatching { GameMode.valueOf(modeName) }.getOrDefault(GameMode.HOLDEM_NL)
            val seats = (entry.arguments?.getInt("seats") ?: 2).coerceIn(2, 4)
            val buyIn = entry.arguments?.getLong("buyIn") ?: 0L
            val appCtx = LocalContext.current.applicationContext
            val entry = remember(appCtx) {
                EntryPointAccessors.fromApplication(appCtx, LlmAdvisorEntryPoint::class.java)
            }
            TableScreen(
                mode = mode,
                seats = seats,
                humanBuyIn = buyIn,
                onExit = {
                    nav.navigate(Routes.LOBBY) {
                        popUpTo(Routes.LOBBY) { inclusive = true }
                    }
                },
                llmAdvisor = entry.llmAdvisor(),
                historyRepo = entry.historyRepo(),
                historyScope = entry.appScope(),
                walletRepo = entry.walletRepo(),
            )
        }
    }
}

@Composable
private fun SplashScreen(onReady: () -> Unit) {
    val ctx = LocalContext.current
    // v1.1 §1.2.O 단말 사양 핑거프린팅: Splash 에서 1회 측정 후 Mid 이하면 안내 Toast.
    LaunchedEffect(Unit) {
        val tier = DeviceFingerprint.classify(ctx)
        if (tier == DeviceTier.MID || tier == DeviceTier.LOW) {
            android.widget.Toast
                .makeText(ctx, DeviceFingerprint.label(tier), android.widget.Toast.LENGTH_LONG)
                .show()
        }
        delay(800L)
        onReady()
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(com.infocar.pokermaster.core.ui.theme.HangameColors.BackgroundBrush),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "♠♥♦♣",
                style = MaterialTheme.typography.displayMedium,
                color = com.infocar.pokermaster.core.ui.theme.HangameColors.TextSecondary,
            )
            Text(
                text = stringResource(id = R.string.splash_title),
                style = MaterialTheme.typography.displayLarge,
                color = com.infocar.pokermaster.core.ui.theme.HangameColors.TextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
            )
        }
    }
}
