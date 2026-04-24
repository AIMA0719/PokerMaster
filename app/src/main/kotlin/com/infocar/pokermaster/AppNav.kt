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
    const val TABLE = "table/{mode}"
    const val HISTORY = "history"
    const val HISTORY_DETAIL = "history/{id}"
    const val SETTINGS = "settings"
    const val STATS = "stats"
    fun table(mode: GameMode) = "table/${mode.name}"
    fun historyDetail(id: Long) = "history/$id"
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
                onSelectMode = { mode ->
                    // M3 MVP: HOLDEM_NL 만 지원 — 다른 모드는 로비에서 lock 예정
                    if (mode == GameMode.HOLDEM_NL) {
                        nav.navigate(Routes.table(mode))
                    }
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
            arguments = listOf(navArgument("mode") { type = NavType.StringType }),
        ) { entry ->
            val modeName = entry.arguments?.getString("mode") ?: GameMode.HOLDEM_NL.name
            val mode = runCatching { GameMode.valueOf(modeName) }.getOrDefault(GameMode.HOLDEM_NL)
            // Phase5-II-B / M5-B: Hilt EntryPoint 로 LlmAdvisor + HandHistoryRepository +
            // AppScope CoroutineScope 를 꺼내 TableScreen 에 주입. 엔진 미지원 단말에서도
            // advisor 는 non-null 이지만 suggest() 가 null 폴백 → DecisionCore 경로.
            val appCtx = LocalContext.current.applicationContext
            val entry = remember(appCtx) {
                EntryPointAccessors.fromApplication(appCtx, LlmAdvisorEntryPoint::class.java)
            }
            TableScreen(
                mode = mode,
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
