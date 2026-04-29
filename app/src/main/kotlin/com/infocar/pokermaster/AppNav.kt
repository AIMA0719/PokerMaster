package com.infocar.pokermaster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
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
import com.infocar.pokermaster.feature.table.settings.LimitTriggerKind
import com.infocar.pokermaster.feature.table.settings.SettingsRepository
import com.infocar.pokermaster.feature.table.settings.SettingsScreen
import com.infocar.pokermaster.model.DefaultModels
import com.infocar.pokermaster.model.ModelGateScreen
import com.infocar.pokermaster.model.ModelStore
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NAV_TRANSITION_MS = 280

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
                val agreedVersion = prefs.getInt(OnboardingPrefs.KEY_TERMS_VERSION, 0)
                // 완료한 적 있어도 약관 버전이 올랐으면 재동의 유도.
                val needsReconsent = agreedVersion < OnboardingPrefs.TERMS_VERSION
                val dest = if (completed && !needsReconsent) Routes.LOBBY else Routes.ONBOARDING
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
                    // 약관 동의 메타데이터 — 컴플라이언스 근거. 시점 + 버전 보존.
                    putBoolean(OnboardingPrefs.KEY_AGE_CONFIRMED, result.ageConfirmed)
                    putBoolean(OnboardingPrefs.KEY_TERMS_ACCEPTED, result.termsAccepted)
                    putBoolean(OnboardingPrefs.KEY_PRIVACY_ACCEPTED, result.privacyAccepted)
                    putLong(OnboardingPrefs.KEY_ACCEPTED_AT_MS, result.acceptedAtMs)
                    putInt(OnboardingPrefs.KEY_TERMS_VERSION, result.termsVersion)
                    apply()
                }
                nav.navigate(Routes.LOBBY) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(
            Routes.LOBBY,
            enterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 4 } +
                    fadeIn(animationSpec = tween(NAV_TRANSITION_MS))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 4 } +
                    fadeOut(animationSpec = tween(NAV_TRANSITION_MS))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 4 } +
                    fadeIn(animationSpec = tween(NAV_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { -it / 4 } +
                    fadeOut(animationSpec = tween(NAV_TRANSITION_MS))
            },
        ) {
            val lobbyCtx = LocalContext.current.applicationContext
            val settingsRepo = remember(lobbyCtx) { SettingsRepository(lobbyCtx) }
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            var limitBlocker by remember { mutableStateOf<String?>(null) }

            LobbyScreen(
                onSelectMode = { mode, seats, buyIn ->
                    scope.launch {
                        val (limit, usage) = combine(
                            settingsRepo.selfLimit,
                            settingsRepo.dailyUsage,
                        ) { l, u -> l to u }.first()
                        val kind = limit.evaluate(usage)
                        if (kind == null) {
                            nav.navigate(Routes.table(mode, seats, buyIn))
                        } else {
                            // 진입 게이트 메시지는 누적 도달 알림(TableScreen)보다 강한 톤 — 자정 이후 재시작 안내.
                            val unit = if (kind is LimitTriggerKind.Hand) "핸드" else "분 누적 플레이"
                            limitBlocker = "오늘 ${kind.limit}$unit 한도에 도달했어요 " +
                                "(현재 ${kind.current}${if (kind is LimitTriggerKind.Hand) "핸드" else "분"}). " +
                                "자정 이후 다시 시작할 수 있습니다."
                        }
                    }
                },
                onOpenHistory = { nav.navigate(Routes.HISTORY) },
                onOpenSettings = { nav.navigate(Routes.SETTINGS) },
                onOpenStats = { nav.navigate(Routes.STATS) },
            )

            limitBlocker?.let { msg ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { limitBlocker = null },
                    title = { Text("오늘 한도 도달") },
                    text = { Text(msg) },
                    confirmButton = {
                        androidx.compose.material3.Button(onClick = { limitBlocker = null }) {
                            Text("확인")
                        }
                    },
                )
            }
        }
        composable(Routes.SETTINGS) {
            val appCtx = LocalContext.current.applicationContext
            SettingsScreen(
                onBack = { nav.popBackStack() },
                versionName = BuildConfig.VERSION_NAME,
                onVerifyModel = {
                    withContext(Dispatchers.IO) {
                        val store = ModelStore(appCtx)
                        val entry = DefaultModels.default
                        when (val r = store.verify(entry)) {
                            ModelStore.VerifyResult.Valid -> "✓ 모델 검증 성공 (SHA-256 일치)"
                            ModelStore.VerifyResult.Missing ->
                                "✗ 모델 파일 없음 — 다음 진입 시 자동 다운로드됩니다."
                            is ModelStore.VerifyResult.SizeMismatch ->
                                "✗ 크기 불일치 (예상 ${r.expected}, 실제 ${r.actual})"
                            is ModelStore.VerifyResult.HashMismatch ->
                                "✗ 해시 불일치 — 손상된 파일입니다. 삭제 후 재다운로드 권장."
                        }
                    }
                },
                onDeleteModel = {
                    withContext(Dispatchers.IO) {
                        val store = ModelStore(appCtx)
                        val entry = DefaultModels.default
                        store.deletePart(entry)
                        store.deleteInstalled(entry)
                    }
                },
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
            enterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it } +
                    fadeIn(animationSpec = tween(NAV_TRANSITION_MS))
            },
            exitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it } +
                    fadeOut(animationSpec = tween(NAV_TRANSITION_MS))
            },
            popEnterTransition = {
                slideInHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it } +
                    fadeIn(animationSpec = tween(NAV_TRANSITION_MS))
            },
            popExitTransition = {
                slideOutHorizontally(animationSpec = tween(NAV_TRANSITION_MS)) { it } +
                    fadeOut(animationSpec = tween(NAV_TRANSITION_MS))
            },
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
                humanNickname = entry.nicknameRepo().current(),
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
    // Mid/Low 티어 단말은 LLM 모드 한계가 있어 안내 배너로 사용자 기대치 정렬.
    var tierLabel by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        val tier = DeviceFingerprint.classify(ctx)
        if (tier == DeviceTier.MID || tier == DeviceTier.LOW) {
            tierLabel = DeviceFingerprint.label(tier)
        }
        delay(1_400L)
        onReady()
    }
    // 잔여9-3: Splash cinematic — 카드 슈트 scaleIn 0.55→1.0 (700ms), 타이틀 200ms 후 fadeIn.
    val cardScale = remember { Animatable(0.55f) }
    val cardAlpha = remember { Animatable(0f) }
    val titleAlpha = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        cardAlpha.animateTo(1f, tween(durationMillis = 380))
    }
    LaunchedEffect(Unit) {
        cardScale.animateTo(1f, tween(durationMillis = 700, easing = FastOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        delay(220L)
        titleAlpha.animateTo(1f, tween(durationMillis = 520, easing = FastOutSlowInEasing))
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
                style = MaterialTheme.typography.displayLarge,
                color = com.infocar.pokermaster.core.ui.theme.HangameColors.TextSecondary,
                letterSpacing = 8.sp,
                modifier = Modifier
                    .alpha(cardAlpha.value)
                    .scale(cardScale.value),
            )
            androidx.compose.foundation.layout.Spacer(
                modifier = Modifier.height(20.dp),
            )
            Text(
                text = stringResource(id = R.string.splash_title),
                style = MaterialTheme.typography.displayLarge,
                color = com.infocar.pokermaster.core.ui.theme.HangameColors.TextPrimary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                modifier = Modifier.alpha(titleAlpha.value),
            )
            tierLabel?.let { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = com.infocar.pokermaster.core.ui.theme.HangameColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp,
                    modifier = Modifier
                        .padding(top = 24.dp, start = 24.dp, end = 24.dp)
                        .alpha(titleAlpha.value),
                )
            }
        }
    }
}
