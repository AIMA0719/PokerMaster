package com.infocar.pokermaster.feature.table

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.infocar.pokermaster.feature.table.a11y.A11ySettings
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Declaration
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.engine.controller.StudReducer
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import kotlinx.coroutines.CoroutineScope
import com.infocar.pokermaster.feature.table.anim.pulseFloat
import com.infocar.pokermaster.feature.table.guide.GuideOverlay
import com.infocar.pokermaster.feature.table.guide.GuideSettings
import com.infocar.pokermaster.feature.table.guide.GuideStep
import com.infocar.pokermaster.feature.table.settings.LimitTriggerKind
import com.infocar.pokermaster.feature.table.settings.SettingsRepository
import com.infocar.pokermaster.feature.table.sfx.BgmManager
import com.infocar.pokermaster.feature.table.sfx.HapticManager
import com.infocar.pokermaster.feature.table.sfx.SfxKind
import com.infocar.pokermaster.feature.table.sfx.SfxPolicy
import com.infocar.pokermaster.feature.table.sfx.SoundManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 테이블 화면 최상위 Composable.
 *
 *  - [viewModel] 가 없으면(프리뷰) [stateOverride] 로 정적 렌더.
 *  - 하위 컴포넌트: [SeatLayout] / [ActionBar] / [HandEndSheet] / [CardCommunityRow] (Phase-B 병렬 에이전트 구현).
 */
@Composable
fun TableScreen(
    mode: GameMode,
    onExit: () -> Unit,
    /** 좌석 수 (인간 1 + AI N-1). 2~4 지원. */
    seats: Int = 2,
    /** 본인 buy-in. 0 이면 default(TABLE_STAKE) 적용. wallet 잔고 전체 권장. */
    humanBuyIn: Long = 0L,
    /** 본인 좌석 닉네임. 기본 "나" — Lobby 의 NicknameRepository 에서 주입. */
    humanNickname: String = "나",
    /** Phase5-II-B: LLM advisor. null 이면 DecisionCore-only 경로. */
    llmAdvisor: LlmAdvisor? = null,
    /** M5-B: 핸드 히스토리 Repository + Application scope. null 이면 저장 생략. */
    historyRepo: HandHistoryRepository? = null,
    historyScope: CoroutineScope? = null,
    /** M6-C: chip wallet. null 이면 buy-in/settle 스킵. */
    walletRepo: WalletRepository? = null,
    viewModel: TableViewModel = run {
        val ctx = LocalContext.current.applicationContext
        remember(mode, seats, humanBuyIn, humanNickname, llmAdvisor, historyRepo, historyScope, walletRepo) {
            TableViewModel.createDefault(
                context = ctx,
                mode = mode,
                seats = seats,
                humanNickname = humanNickname,
                humanBuyIn = humanBuyIn,
                llmAdvisor = llmAdvisor,
                historyRepo = historyRepo,
                historyScope = historyScope,
                walletRepo = walletRepo,
            )
        }
    },
) {
    val state by viewModel.state.collectAsState()
    val gameOver by viewModel.gameOver.collectAsState()
    val autoNextCountdown by viewModel.autoNextCountdown.collectAsState()
    val lastActions by viewModel.lastActions.collectAsState()
    val exitRequested by viewModel.exitRequested.collectAsState()
    val buyInRejected by viewModel.buyInRejected.collectAsState()
    val recoveryState by viewModel.recoveryState.collectAsState()
    val context = LocalContext.current

    // 카드 슬라이드인 동안 액션바/NPC tick 잠금 — UI 적응 시간 확보.
    var dealReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(1_200L)
        dealReady = true
    }
    val displayState = remember(state, dealReady) {
        if (dealReady) state
        else state.copy(
            players = state.players.map { it.copy(holeCards = emptyList()) },
            toActSeat = null,
        )
    }

    val exitScope = rememberCoroutineScope()
    var exitInProgress by remember { mutableStateOf(false) }
    // wallet 잔고 갱신을 보장한 뒤 로비 복귀하는 단일 wrapper. TableContent 의 X/메뉴 나가기,
    // LaunchedEffect(gameOver) 모두 이 함수만 호출하면 settled flag 로 중복 settle 방지됨.
    val onExitSettled: () -> Unit = {
        if (!exitInProgress) {
            exitInProgress = true
            exitScope.launch {
                viewModel.settleAndCloseAwait()
                onExit()
            }
        }
    }
    val onExitRequested: () -> Unit = {
        if (viewModel.requestExitAfterHand()) {
            onExitSettled()
        }
    }

    // 첫 백키: "이번 핸드 끝나고 나가기" 큐잉 (ExitQueuedBadge 노출).
    // 두 번째 백키 (이미 큐잉된 상태): 즉시 강제 종료.
    BackHandler {
        if (exitRequested) {
            onExitSettled()
        } else {
            onExitRequested()
        }
    }

    LaunchedEffect(exitRequested, state.pendingShowdown, gameOver) {
        if (exitRequested && (state.pendingShowdown != null || gameOver != null)) {
            delay(1_200L)
            onExitSettled()
        }
    }
    LaunchedEffect(buyInRejected) {
        val rejected = buyInRejected ?: return@LaunchedEffect
        android.widget.Toast.makeText(
            context,
            "잔고가 부족해 테이블에 입장할 수 없습니다 (필요 ${ChipFormat.format(rejected.required)}, 잔고 ${ChipFormat.format(rejected.balance)}).",
            android.widget.Toast.LENGTH_LONG,
        ).show()
        onExit()
    }

    // silent degrade / 안전망 알림 — VM이 emit한 1회성 메시지를 토스트로 노출.
    LaunchedEffect(viewModel) {
        viewModel.uiMessages.collect { msg ->
            android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // SFX/Haptic — DataStore 기반.
    val settingsRepo = remember(context) { SettingsRepository(context) }
    val scope = rememberCoroutineScope()
    val haptic = remember(context) { HapticManager(context) }
    val sound = remember(context) {
        SoundManager(context).apply {
            load(
                mapOf(
                    SfxKind.CardDeal to R.raw.sfx_card_deal,
                    SfxKind.ChipCommit to R.raw.sfx_chip_commit,
                    SfxKind.PotSweep to R.raw.sfx_pot_sweep,
                    SfxKind.Check to R.raw.sfx_check,
                    SfxKind.Fold to R.raw.sfx_fold,
                    SfxKind.AllIn to R.raw.sfx_allin,
                    SfxKind.HandWin to R.raw.sfx_hand_win,
                )
            )
        }
    }
    DisposableEffect(sound) { onDispose { sound.release() } }
    val sfxPolicy by settingsRepo.sfxPolicy.collectAsState(initial = SfxPolicy.Default)
    val selfLimit by settingsRepo.selfLimit.collectAsState(
        initial = com.infocar.pokermaster.feature.table.settings.SelfLimitSettings.Default,
    )

    // 책임 있는 게임: 일일 누적 핸드/시간 추적 (DataStore 영속 — 자정 넘으면 자동 리셋).
    val dailyUsage by settingsRepo.dailyUsage.collectAsState(
        initial = com.infocar.pokermaster.feature.table.settings.DailyUsage(
            date = "",
            hands = 0,
            playMinutes = 0,
        ),
    )
    // 한도 트리거 종류 — 한도별로 dismiss 상태를 분리해서 재트리거 방지.
    val limitTrigger = remember { mutableStateOf<LimitTriggerKind?>(null) }
    var handLimitDismissed by remember { mutableStateOf(false) }
    var timeLimitDismissed by remember { mutableStateOf(false) }

    // 한도 설정값 변경 시 dismiss 상태 리셋 — 사용자가 더 큰 값으로 재설정하면 다시 알림.
    LaunchedEffect(selfLimit.dailyHandLimit) { handLimitDismissed = false }
    LaunchedEffect(selfLimit.dailySessionMinutes) { timeLimitDismissed = false }

    // 핸드 변화 감지 → DataStore 에 delta 누적. handIndex 점프(예: 재시작) 안전.
    // UI 가 읽지 않는 holder — recomposition 트리거 회피용 단순 mutable 객체.
    val lastTrackedHandIndex = remember { object { var value: Long = state.handIndex } }
    LaunchedEffect(state.handIndex) {
        val delta = (state.handIndex - lastTrackedHandIndex.value).toInt()
        if (delta > 0) {
            settingsRepo.addDailyHands(delta)
            lastTrackedHandIndex.value = state.handIndex
        }
    }
    // 누적 시간 — 한도가 설정된 경우에만 측정 (idle write 회피).
    LaunchedEffect(selfLimit.dailySessionMinutes) {
        if (selfLimit.dailySessionMinutes <= 0) return@LaunchedEffect
        while (true) {
            delay(60_000L)
            settingsRepo.addDailyMinutes(1)
        }
    }

    LaunchedEffect(selfLimit.breakReminderMinutes) {
        val minutes = selfLimit.breakReminderMinutes
        if (minutes <= 0) return@LaunchedEffect
        while (true) {
            delay(minutes * 60_000L)
            // 핸드 종료/게임 오버 화면에 떠 있으면 알림 skip — 이미 사용자가 정리 중.
            if (state.pendingShowdown != null || gameOver != null) continue
            android.widget.Toast.makeText(
                context,
                "${minutes}분이 경과했어요. 잠시 쉬어가는 건 어떨까요? 🌿",
                android.widget.Toast.LENGTH_LONG,
            ).show()
        }
    }
    // 일일 핸드 한도 도달 — 영속 누적 기준. 사용자가 한 번 dismiss했으면 같은 한도에서 재트리거 X.
    LaunchedEffect(dailyUsage.hands, selfLimit.dailyHandLimit, handLimitDismissed) {
        if (handLimitDismissed) return@LaunchedEffect
        if (selfLimit.dailyHandLimit > 0 && dailyUsage.hands >= selfLimit.dailyHandLimit) {
            limitTrigger.value = LimitTriggerKind.Hand(
                limit = selfLimit.dailyHandLimit,
                current = dailyUsage.hands,
            )
        }
    }
    // 일일 시간 한도 도달 — 영속 누적 기준. dismiss 후 재트리거 X.
    LaunchedEffect(dailyUsage.playMinutes, selfLimit.dailySessionMinutes, timeLimitDismissed) {
        if (timeLimitDismissed) return@LaunchedEffect
        if (selfLimit.dailySessionMinutes > 0 && dailyUsage.playMinutes >= selfLimit.dailySessionMinutes) {
            limitTrigger.value = LimitTriggerKind.Time(
                limit = selfLimit.dailySessionMinutes,
                current = dailyUsage.playMinutes,
            )
        }
    }
    limitTrigger.value?.let { kind ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("휴식이 필요해요", fontWeight = FontWeight.Bold) },
            text = { Text(kind.message) },
            confirmButton = {
                Button(onClick = {
                    when (kind) {
                        is LimitTriggerKind.Hand -> handLimitDismissed = true
                        is LimitTriggerKind.Time -> timeLimitDismissed = true
                    }
                    limitTrigger.value = null
                    onExitRequested()
                }) {
                    Text("로비로 이동")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    when (kind) {
                        is LimitTriggerKind.Hand -> handLimitDismissed = true
                        is LimitTriggerKind.Time -> timeLimitDismissed = true
                    }
                    limitTrigger.value = null
                }) {
                    Text("계속 플레이")
                }
            },
        )
    }

    //BGM 매니저. 자산 (raw/bgm_table) 미설치 시 silent — 자동으로 안 재생.
    val bgm = remember(context) { BgmManager(context) }
    DisposableEffect(bgm) { onDispose { bgm.release() } }
    LaunchedEffect(sfxPolicy.bgmEnabled) {
        if (sfxPolicy.bgmEnabled) {
            val track = BgmManager.resolveTrack(context, "bgm_table")
            if (track != 0) bgm.play(track)
        } else {
            bgm.stop()
        }
    }

    //인간 액션 디스패처 — SFX/Haptic 은 viewModel.actionEvent collect 로 단일화.
    val onHumanActionWithSfx: OnAction = { action -> viewModel.onHumanAction(action) }

    val hapticEnabled = sfxPolicy.hapticEnabled
    val onSliderTick: () -> Unit = remember(haptic, hapticEnabled) {
        { if (hapticEnabled) haptic.onTick() }
    }
    val onSliderConfirmHaptic: () -> Unit = remember(haptic, hapticEnabled) {
        { if (hapticEnabled) haptic.onChipCommit() }
    }

    //인간/NPC 액션 통합 SFX/Haptic. NPC tick 도 동일 ActionEvent 흐름으로 처리되어
    // 무음 NPC 문제 해소. 햅틱은 사용자 손이 닿은 인간 액션에만 (UX 관행), 단 SFX 는 둘 다.
    LaunchedEffect(viewModel, sfxPolicy) {
        viewModel.actionEvent.collect { event ->
            if (sfxPolicy.hapticEnabled && event.isHuman) {
                when (event.type) {
                    ActionType.ALL_IN -> haptic.onDoubleClick()
                    ActionType.BET, ActionType.RAISE,
                    ActionType.COMPLETE, ActionType.BRING_IN -> haptic.onChipCommit()
                    ActionType.CHECK, ActionType.FOLD -> haptic.onTick()
                    ActionType.CALL, ActionType.DECLARE, ActionType.SAVE_LIFE -> haptic.onAction()
                }
            }
            if (sfxPolicy.soundEnabled) {
                val kind = when (event.type) {
                    ActionType.CHECK -> SfxKind.Check
                    ActionType.FOLD -> SfxKind.Fold
                    ActionType.ALL_IN -> SfxKind.AllIn
                    ActionType.DECLARE -> SfxKind.ChipCommit
                    else -> SfxKind.ChipCommit
                }
                // NPC 액션은 살짝 작게 — 본인 액션 강조.
                sound.play(kind, volume = if (event.isHuman) 0.85f else 0.7f)
            }
        }
    }

    //카드 딜링 stagger — 한 스트릿당 1회 → 좌석/카드 수 만큼 짧게 연속 재생.
    // FLOP=3장, TURN/RIVER=1장, PREFLOP/THIRD/FOURTH~SEVENTH = 활성 좌석 수 만큼.
    LaunchedEffect(state.street, dealReady) {
        if (!dealReady) return@LaunchedEffect
        if (!sfxPolicy.soundEnabled) return@LaunchedEffect
        val activeCount = state.players.count { !it.folded }
        val (count, gap) = when (state.street) {
            Street.PREFLOP, Street.THIRD -> activeCount to 80L
            Street.FLOP -> 3 to 100L
            Street.TURN, Street.RIVER -> 1 to 0L
            Street.FOURTH, Street.FIFTH, Street.SIXTH, Street.SEVENTH -> activeCount to 80L
            else -> 0 to 0L
        }
        repeat(count) { i ->
            sound.play(SfxKind.CardDeal, volume = 0.7f)
            if (i < count - 1 && gap > 0L) delay(gap)
        }
    }

    //본인 차례 30초 무응답 alert. 30초 경과 후 5초 간격 햅틱 TICK 최대 3회.
    // toAct 바뀌면 LaunchedEffect 재시작 → 이전 alert 자동 취소. pendingShowdown 시 skip.
    // 60초 시점에 자동 폴드(콜 봉착)/체크(콜 없음) → 영구 멈춤 방지.
    LaunchedEffect(state.toActSeat, state.pendingShowdown) {
        val toAct = state.toActSeat ?: return@LaunchedEffect
        if (state.pendingShowdown != null) return@LaunchedEffect
        val player = state.players.firstOrNull { it.seat == toAct } ?: return@LaunchedEffect
        if (!player.isHuman) return@LaunchedEffect
        delay(30_000L)
        repeat(3) {
            if (sfxPolicy.hapticEnabled) haptic.onAction()
            delay(5_000L)
        }
        // 자리비움 자동 정리 — 콜 봉착이면 폴드, 아니면 체크.
        viewModel.onTimeoutAutoFold()
    }

    //게임 오버 bust 햅틱 — 본인 패배(파산) 시 한 번 강하게.
    LaunchedEffect(gameOver, sfxPolicy) {
        val info = gameOver ?: return@LaunchedEffect
        if (sfxPolicy.hapticEnabled && !info.isHumanWinner) {
            haptic.onBust()
        }
    }

    //PotSweep + HandWin — 본인 승리 vs NPC 승리 차등 (volume + onWin 햅틱).
    val showdownActive = state.pendingShowdown != null
    LaunchedEffect(showdownActive) {
        if (!showdownActive) return@LaunchedEffect
        if (!sfxPolicy.soundEnabled) return@LaunchedEffect
        sound.play(SfxKind.PotSweep)
        delay(600L)
        if (!sfxPolicy.soundEnabled) return@LaunchedEffect
        val showdown = state.pendingShowdown ?: return@LaunchedEffect
        val humanSeat = state.players.firstOrNull { it.isHuman }?.seat
        val humanWon = humanSeat != null && (showdown.payouts[humanSeat] ?: 0L) > 0L
        sound.play(SfxKind.HandWin, volume = if (humanWon) 1.0f else 0.55f)
        if (humanWon && sfxPolicy.hapticEnabled) {
            haptic.onWin()
        }
    }

    // Guide overlay — DataStore 기반.
    val guideSettings by settingsRepo.guideSettings.collectAsState(initial = GuideSettings.Default)
    var currentGuideStep by remember { mutableStateOf<GuideStep?>(null) }
    // 최초 guideSettings 도달 시 한 번만 초기 step 결정 (이후 토글은 명시적으로 처리).
    LaunchedEffect(Unit) {
        //DataStore IO 예외(디스크 풀/파일 손상) 가 composable scope 로 전파되면
        // 앱이 죽음. runCatching + Default 폴백으로 격리.
        val first = runCatching { settingsRepo.guideSettings.first() }
            .getOrDefault(GuideSettings.Default)
        currentGuideStep = if (first.guideModeEnabled) first.initialStep() else null
    }
    val onToggleGuide: () -> Unit = {
        val next = if (guideSettings.guideModeEnabled) guideSettings.disable() else guideSettings.enable()
        scope.launch { settingsRepo.setGuideSettings(next) }
        currentGuideStep = if (next.guideModeEnabled) next.initialStep() else null
    }

    // A11y 설정 — 고대비 카드는 PlayingCard 가 LocalHighContrastCards 로 받는다.
    val a11ySettings by settingsRepo.a11ySettings.collectAsState(initial = A11ySettings.Default)
    // UI-Images: opt-in 이미지 카드 모드 (CC0 PNG). 기본 false → 기존 Canvas 경로.
    val useImageCards by settingsRepo.useImageCards.collectAsState(initial = false)

    // NPC tick 예외 회복 다이얼로그.
    recoveryState?.let { rec ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("AI 오류", fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(rec.message, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "원인: ${rec.cause}",
                        style = MaterialTheme.typography.bodySmall,
                        color = HangameColors.TextMuted,
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.recoverByDiscardingHand()
                }) {
                    Text("핸드 정리 후 계속")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    viewModel.dismissRecovery()
                    onExitRequested()
                }) {
                    Text("로비로")
                }
            },
        )
    }

    // 게임 오버 결과 다이얼로그.
    gameOver?.let { info ->
        val mePlayer = state.players.firstOrNull { it.isHuman }
        val finalChips = mePlayer?.chips ?: 0L
        val net = finalChips - humanBuyIn
        AlertDialog(
            onDismissRequest = {},
            title = {
                Text(
                    text = if (info.isHumanWinner) "🏆 게임 승리" else "게임 종료",
                    fontWeight = FontWeight.Black,
                    color = if (info.isHumanWinner) HangameColors.PotValue else HangameColors.TextPrimary,
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (info.isHumanWinner) "축하합니다! 모든 NPC를 파산시켰습니다."
                        else "${info.winnerNickname} 의 승리. 다시 도전해보세요.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = "최종 칩: ${ChipFormat.format(finalChips)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = HangameColors.TextChip,
                    )
                    if (humanBuyIn > 0L) {
                        val sign = if (net >= 0L) "+" else "-"
                        Text(
                            text = "내 결과: $sign${ChipFormat.format(kotlin.math.abs(net))}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (net >= 0L) HangameColors.TextLime else HangameColors.TextDanger,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = onExitSettled) {
                    Text(text = "로비로")
                }
            },
        )
    }

    CompositionLocalProvider(
        LocalHighContrastCards provides a11ySettings.highContrastCards,
        LocalReduceMotion provides a11ySettings.reduceMotion,
        LocalAnnounceActions provides a11ySettings.announceActionsAudibly,
        LocalUseImageCards provides useImageCards,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        TableContent(
            state = displayState,
            onAction = onHumanActionWithSfx,
            onDeclare = viewModel::onDeclare,
            onNextHand = viewModel::onNextHand,
            onSurrender = viewModel::onSurrender,
            onExit = onExitRequested,
            exitRequested = exitRequested,
            guideEnabled = guideSettings.guideModeEnabled,
            onToggleGuide = onToggleGuide,
            autoNextCountdown = autoNextCountdown,
            onPauseAutoNext = viewModel::pauseAutoNext,
            gameOver = gameOver,
            lastActions = lastActions,
            dealReady = dealReady,
            onSliderTick = onSliderTick,
            onSliderConfirmHaptic = onSliderConfirmHaptic,
        )
        AnimatedVisibility(
            visible = !dealReady,
            enter = fadeIn(tween(220)),
            exit = fadeOut(tween(360)),
            modifier = Modifier.align(Alignment.Center),
        ) {
            DealingPrepBadge()
        }
        currentGuideStep?.let { step ->
            GuideOverlay(
                step = step,
                onNext = {
                    currentGuideStep = when (step) {
                        is GuideStep.Welcome -> {
                            scope.launch { settingsRepo.setGuideSettings(guideSettings.markWelcomeSeen()) }
                            GuideStep.ActionHint(GuideSettings.DEFAULT_HINT)
                        }
                        is GuideStep.ActionHint -> null
                        is GuideStep.Closing -> null
                    }
                },
                onDismiss = {
                    scope.launch { settingsRepo.setGuideSettings(guideSettings.disable()) }
                    currentGuideStep = null
                },
            )
        }
    }
    } // CompositionLocalProvider
    // 이어하기 기능 제거 — ResumeDialog 호출 없음.
}

@Composable
internal fun TableContent(
    state: GameState,
    onAction: OnAction,
    onNextHand: () -> Unit,
    onSurrender: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    /** 7-Stud Hi-Lo declare 단계 사람 좌석 선언 콜백. 비활성 단계에선 미사용. */
    onDeclare: (Declaration) -> Unit = {},
    guideEnabled: Boolean = true,
    onToggleGuide: () -> Unit = {},
    autoNextCountdown: Int? = null,
    onPauseAutoNext: () -> Unit = {},
    gameOver: GameOverInfo? = null,
    lastActions: Map<Int, String> = emptyMap(),
    exitRequested: Boolean = false,
    /** 진입 직후 1.2초 딜러 준비 대기. false 면 액션바/Waiting 둘 다 숨긴다. */
    dealReady: Boolean = true,
    /** 베팅 슬라이더 drag tick 햅틱. 호출자가 SfxPolicy 게이트 후 실행. */
    onSliderTick: () -> Unit = {},
    /** 베팅 슬라이더 확정 햅틱 (onChipCommit). 호출자가 SfxPolicy 게이트 후 실행. */
    onSliderConfirmHaptic: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmAllIn by remember { mutableStateOf<Pair<ActionType, Long>?>(null) }
    val humanSeat = remember(state) { state.players.firstOrNull { it.isHuman }?.seat ?: 0 }
    val actionBarState = remember(state) { TableUiMapper.mapActionBar(state, humanSeat) }
    val handEndData = remember(state) { TableUiMapper.mapHandEnd(state) }
    val isShowdown = state.pendingShowdown != null || state.street == Street.SHOWDOWN
    val winnerSeats = remember(state.pendingShowdown) {
        val potWinners = state.pendingShowdown?.pots?.flatMap { it.winnerSeats }?.toSet().orEmpty()
        potWinners.ifEmpty {
            state.pendingShowdown?.payouts
                ?.filterValues { it > 0L }
                ?.keys
                .orEmpty()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .background(HangameColors.BackgroundBrush),  // 그라데이션 배경 = 풀스크린(상태바 포함)
        containerColor = Color.Transparent,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),  // 컨텐츠만 status/nav bar 안쪽으로
        ) {
            // 1) 펠트 — 타원 라디얼 그라데이션 (한게임 풍).
            HangameFelt(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 50.dp),
            )

            // 2) 명시 Column 분할 시트 레이아웃 — 헤즈업/3인/4인. 절대 안 겹침.
            MultiSeatLayout(
                state = state,
                humanSeat = humanSeat,
                isShowdown = isShowdown,
                winnerSeats = winnerSeats,
                lastActions = lastActions,
                centerContent = {
                    TableCenterContent(
                        state = state,
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 92.dp),
            )

            // 4) 우상단 헤더 — 블라인드 정보 + 햄버거 메뉴 + 나가기 (좌상단 제거).
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BlindInfoBadge(state = state)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Default.Menu, contentDescription = "메뉴", tint = Color.White)
                    }
                    val canSurrender = state.toActSeat == humanSeat &&
                        state.pendingShowdown == null &&
                        dealReady
                    InGameMenuDropdown(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onSurrender = onSurrender,
                        onExit = onExit,
                        exitRequested = exitRequested,
                        guideEnabled = guideEnabled,
                        onToggleGuide = onToggleGuide,
                        mode = state.mode,
                        canSurrender = canSurrender,
                    )
                }
                IconButton(
                    onClick = onExit,
                    enabled = !exitRequested,
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = if (exitRequested) {
                            stringResource(id = R.string.exit_queued)
                        } else {
                            stringResource(id = R.string.menu_exit)
                        },
                        tint = if (exitRequested) HangameColors.TextMuted else HangameColors.TextSecondary,
                    )
                }
            }

            if (exitRequested) {
                ExitQueuedBadge(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp),
                )
            }

            // 6) 하단 액션바 / declare 시트 분기 — 쇼다운 / 프리딜 대기 동안에는 둘 다 숨김.
            //   - DECLARE 단계 + 사람 차례: 일반 ActionBar 대신 DeclareSheet 만 노출.
            //   - 그 외: 기존 ActionBar 분기.
            val isHumanDeclareTurn = state.street == Street.DECLARE &&
                state.toActSeat == humanSeat &&
                state.pendingShowdown == null &&
                dealReady
            when {
                isHumanDeclareTurn -> {
                    DeclareSheet(
                        onDeclare = onDeclare,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 920.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
                actionBarState != null && state.pendingShowdown == null && dealReady -> {
                    ActionBar(
                        state = actionBarState,
                        onAction = onAction,
                        onRequestConfirm = { type, amount -> confirmAllIn = type to amount },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .widthIn(max = 920.dp)
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        onSliderTick = onSliderTick,
                        onSliderConfirmHaptic = onSliderConfirmHaptic,
                    )
                }
                state.pendingShowdown == null && gameOver == null && dealReady -> {
                    val toActPlayer = state.players.firstOrNull { it.seat == state.toActSeat }
                    WaitingForNpc(
                        nickname = toActPlayer?.nickname,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp),
                    )
                }
            }

            if (state.pendingShowdown != null && handEndData != null) {
                // 핸드 종료: WinnerBanner 단독 — 승자/지급액/핸드/베스트5장/다음 버튼 통합.
                WinnerBanner(
                    data = handEndData,
                    humanSeat = humanSeat,
                    autoNextCountdown = autoNextCountdown,
                    onNext = onNextHand,
                    onPauseAutoNext = onPauseAutoNext,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .widthIn(max = 460.dp)
                        .padding(horizontal = 16.dp),
                )
            }

            // 게임 오버 시 별도 오버레이/다이얼로그 X — 정산 애니가 끝난 뒤 LaunchedEffect 가
            // 자동으로 onExit() 호출 (사용자 룰).
        }

        // 베팅 2단계 확인 (Phase-C 구성)
        confirmAllIn?.let { (type, amount) ->
            val mePlayer = state.players.firstOrNull { it.seat == humanSeat }
            val delta = mePlayer?.let {
                (amount - it.committedThisStreet).coerceAtLeast(0L).coerceAtMost(it.chips)
            } ?: 0L
            BettingConfirmDialog(
                type = type,
                amount = amount,
                deltaChips = delta,
                onConfirm = {
                    confirmAllIn = null
                    onAction(Action(type, amount))
                },
                onCancel = { confirmAllIn = null },
            )
        }
    }
}

/**
 * 한게임 풍 펠트 — 타원 라디얼 그라데이션 + 외곽 보더.
 *
 * UI-Images TODO: opt-in 이미지 모드(LocalUseImageCards=true) 가 켜졌을 때 이 자리 위에
 * `Modifier.paint(painterResource(R.drawable.felt_bg), contentScale = ContentScale.Crop)` 로
 * CC0 felt 텍스처를 덧입히는 옵션을 추가할 것. 현재는 검증 가능한 CC0 felt 텍스처를
 * 확보하지 못해 (Kenney boardgame-pack 미포함) 그라데이션 폴백만 유지한다.
 * `feature/table/src/main/res/drawable-nodpi/felt_bg.png` 파일을 드롭한 뒤 활성화.
 */
@Composable
private fun HangameFelt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(180.dp))
            .background(HangameColors.feltBrush())
            .border(
                BorderStroke(2.dp, HangameColors.SeatBorder),
                RoundedCornerShape(180.dp),
            ),
    )
}

/**
 * 명시적 Column/Row 분할 시트 레이아웃 — 헤즈업/3인/4인 모두 영역 명시 분할로 절대 안 겹침.
 *
 *  - N=2 (헤즈업): NPC 위쪽 / 가운데 / 본인 아래쪽
 *  - N=3 (3인): NPC1·NPC2 위쪽 가로 분리 / 가운데 / 본인 아래쪽
 *  - N=4 (4인): NPC2 위쪽 / NPC1·가운데·NPC3 가로 / 본인 아래쪽
 *  - N>=5: 기존 angle 기반 [SeatLayout] 폴백
 */
@Composable
private fun MultiSeatLayout(
    state: GameState,
    humanSeat: Int,
    isShowdown: Boolean,
    winnerSeats: Set<Int>,
    lastActions: Map<Int, String>,
    centerContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 자주 변하는 players reference 마다 sort/filter/blinds 재계산되지 않게 메모.
    val sortedBySeat = remember(state.players) { state.players.sortedBy { it.seat } }
    val activeBySeat = remember(sortedBySeat) { sortedBySeat.filter { it.chips > 0 } }
    val blinds = remember(activeBySeat, state.btnSeat) { computeBlinds(activeBySeat, state.btnSeat) }
    val (sbSeat, bbSeat) = blinds
    val human = remember(sortedBySeat, humanSeat) {
        sortedBySeat.firstOrNull { it.seat == humanSeat }
    }
    val npcs = remember(sortedBySeat, humanSeat) {
        sortedBySeat.filter { it.seat != humanSeat }
    }

    val totalActiveSeats = sortedBySeat.size.coerceAtLeast(1)

    // 7스터드 시트 라벨: 3rd 의 브링인 좌석, 4th 의 오픈 페어 좌석.
    val seatBadges: Map<Int, String> = remember(state.mode, state.street, state.lastAggressorSeat, state.players) {
        buildStudSeatBadges(state)
    }

    @Composable
    fun seat(player: PlayerState) {
        val payout = state.pendingShowdown?.payouts?.get(player.seat)?.takeIf { it > 0L }
        val viewerPlayer = TableUiMapper.mapPlayerForViewer(player, humanSeat, state.street)
        val declared = state.declarations[player.seat]
        // DECLARE 단계 중 상대 좌석 인디케이터 — 실 선언이 들어왔으면 "선언 완료", 아직이면 "결정 중".
        val declareIndicator: String? = if (state.street == Street.DECLARE && player.seat != humanSeat) {
            if (declared != null) "선언 완료" else "결정 중"
        } else null
        // SHOWDOWN/이후: 본인 선언이거나 마스크 해제된 좌석은 한국어 라벨로 노출.
        val declarationBadge: String? = when {
            declareIndicator != null -> declareIndicator
            else -> when (declared) {
                Declaration.HIGH -> "하이"
                Declaration.LOW -> "로우"
                Declaration.SWING -> "스윙"
                null -> null
            }
        }
        // extraBadge 와 declarationBadge 를 한 줄에 합치되 — extraBadge 가 우선이면 둘 다 표시.
        val mergedBadge: String? = when {
            seatBadges[player.seat] != null && declarationBadge != null ->
                "${seatBadges[player.seat]} · $declarationBadge"
            seatBadges[player.seat] != null -> seatBadges[player.seat]
            declarationBadge != null -> declarationBadge
            else -> null
        }
        PlayerSeat(
            player = viewerPlayer,
            isBtn = player.seat == state.btnSeat,
            isSb = sbSeat != null && player.seat == sbSeat,
            isBb = bbSeat != null && player.seat == bbSeat,
            isToAct = state.toActSeat != null && player.seat == state.toActSeat,
            isHuman = player.seat == humanSeat,
            isShowdown = isShowdown,
            isWinner = player.seat in winnerSeats,
            lastActionLabel = lastActions[player.seat],
            winnerPayout = payout,
            dealOrderIndex = sortedBySeat.indexOfFirst { it.seat == player.seat }.coerceAtLeast(0),
            totalActiveSeats = totalActiveSeats,
            extraBadgeLabel = mergedBadge,
        )
    }

    when {
        human == null -> Box(modifier)
        npcs.isEmpty() -> Box(modifier, contentAlignment = Alignment.Center) {
            seat(human)
        }
        npcs.size == 1 -> {
            // 헤즈업: NPC 위 / 가운데 / 본인 아래
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().weight(0.30f),
                    contentAlignment = Alignment.Center,
                ) { seat(npcs[0]) }
                Box(
                    Modifier.fillMaxWidth().weight(0.40f),
                    contentAlignment = Alignment.Center,
                ) { centerContent() }
                Box(
                    Modifier.fillMaxWidth().weight(0.30f),
                    contentAlignment = Alignment.Center,
                ) { seat(human) }
            }
        }
        npcs.size == 2 -> {
            // 3인: 위쪽 NPC 둘 가로 분리 / 가운데 / 본인 아래
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().weight(0.28f),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                        seat(npcs[0])
                        seat(npcs[1])
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(0.42f),
                    contentAlignment = Alignment.Center,
                ) { centerContent() }
                Box(
                    Modifier.fillMaxWidth().weight(0.30f),
                    contentAlignment = Alignment.Center,
                ) { seat(human) }
            }
        }
        npcs.size == 3 -> {
            // 4인: NPC2 위 가운데 / NPC1·가운데·NPC3 가로 / 본인 아래
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.fillMaxWidth().weight(0.26f),
                    contentAlignment = Alignment.Center,
                ) { seat(npcs[1]) }
                Box(
                    Modifier.fillMaxWidth().weight(0.42f),
                ) {
                    Row(
                        Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            seat(npcs[0])
                        }
                        Box(Modifier.weight(1.4f), contentAlignment = Alignment.Center) {
                            centerContent()
                        }
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            seat(npcs[2])
                        }
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(0.32f),
                    contentAlignment = Alignment.Center,
                ) { seat(human) }
            }
        }
        else -> {
            // 5+ 인 — 기존 angle 기반 폴백 (centerContent 는 별도 처리 필요).
            // HiLo UI: DECLARE 단계 동안 viewer 가 아닌 좌석의 declaration 을 마스킹.
            val maskedPlayers = state.players.map {
                TableUiMapper.mapPlayerForViewer(it, humanSeat, state.street)
            }
            SeatLayout(
                players = maskedPlayers,
                btnSeat = state.btnSeat,
                toActSeat = state.toActSeat,
                humanSeat = humanSeat,
                isShowdown = isShowdown,
                winnerSeats = winnerSeats,
                lastActionBySeat = lastActions,
                seatBadges = seatBadges,
                modifier = modifier,
            )
        }
    }
}

/**
 * 7스터드/HiLo 전용 시트 라벨 생성:
 *  - 3rd street: bring-in 좌석 ("브링인")
 *  - 4th street: 오픈 페어 좌석들 ("오픈 페어")
 *  - 그 외 스트릿/모드: 빈 맵.
 */
private fun buildStudSeatBadges(state: GameState): Map<Int, String> {
    val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
    if (!isStud) return emptyMap()
    return when (state.street) {
        Street.THIRD -> state.lastAggressorSeat?.let { mapOf(it to "브링인") } ?: emptyMap()
        Street.FOURTH -> StudReducer.openPairsOnFourthStreet(state).mapValues { "오픈 페어" }
        else -> emptyMap()
    }
}

@Composable
private fun TableCenterContent(state: GameState, modifier: Modifier = Modifier) {
    val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
    if (isStud) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CenterPotDisplay(
                pot = TableUiMapper.totalPot(state),
                modifier = Modifier.widthIn(max = 150.dp),
            )
            Spacer(Modifier.width(10.dp))
            StreetLabel(state.street)
        }
    } else {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CenterPotDisplay(pot = TableUiMapper.totalPot(state))
            CardCommunityRow(community = state.community)
        }
    }
}

/**
 * 펠트 중앙 Total Pot 표시 — 한게임 풍. POKER 로고 자리에 Total 만 prominent.
 *
 *  - 상단: "Total" 작은 라벨 (옅은 하늘색)
 *  - 하단: 칩 아이콘 + 금액 (큰 골드)
 *  - pot 값이 변할 때마다 살짝 부풀었다가 원복 — 베팅 시각 신호.
 */
@Composable
private fun CenterPotDisplay(pot: Long, modifier: Modifier = Modifier) {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(pot) {
        if (pot > 0) {
            scale.snapTo(1f)
            scale.animateTo(1.15f, tween(durationMillis = 140))
            scale.animateTo(1f, tween(durationMillis = 240))
        }
    }
    Column(
        modifier = modifier.scale(scale.value),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        Text(
            text = "총 팟",
            fontSize = 12.sp,
            color = HangameColors.PotLabel,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(text = "🪙", fontSize = 16.sp)
            Text(
                text = ChipFormat.format(pot),
                fontSize = 22.sp,
                color = HangameColors.PotValue,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * 7스터드 스트릿 라벨 — community row 자리에 한국식 "3구/4구" 표기.
 */
@Composable
private fun StreetLabel(street: Street) {
    val ko = when (street) {
        Street.THIRD -> "3구"
        Street.FOURTH -> "4구"
        Street.FIFTH -> "5구"
        Street.SIXTH -> "6구"
        Street.SEVENTH -> "7구"
        Street.DECLARE -> "선언"
        Street.SHOWDOWN -> "쇼다운"
        else -> "—"
    }
    val en = when (street) {
        Street.THIRD -> "3rd"
        Street.FOURTH -> "4th"
        Street.FIFTH -> "5th"
        Street.SIXTH -> "6th"
        Street.SEVENTH -> "7th"
        else -> null
    }
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.7f),
        border = BorderStroke(0.5.dp, HangameColors.StudAccent.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = ko,
                fontSize = 15.sp,
                color = HangameColors.StudAccent,
                fontWeight = FontWeight.Black,
            )
            if (en != null) {
                Text(
                    text = en,
                    fontSize = 11.sp,
                    color = HangameColors.TextMuted,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/**
 * 우상단 블라인드/베팅 정보 표시.
 *  - 홀덤: "SB 50 | BB 100"
 *  - 7스터드/HiLo: "7포커 · 앤티 10 | 브링인 25"
 */
@Composable
private fun BlindInfoBadge(state: GameState) {
    val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
    val modePrefix = when (state.mode) {
        GameMode.SEVEN_STUD -> "7포커"
        GameMode.SEVEN_STUD_HI_LO -> "하이로우"
        else -> null
    }
    val left = if (isStud) "앤티 ${ChipFormat.format(state.config.ante)}"
    else "SB ${ChipFormat.format(state.config.smallBlind)}"
    val right = if (isStud) "브링인 ${ChipFormat.format(state.config.bringIn)}"
    else "BB ${ChipFormat.format(state.config.bigBlind)}"
    val borderColor = if (isStud) HangameColors.StudAccent.copy(alpha = 0.55f) else HangameColors.SeatBorder
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.85f),
        border = BorderStroke(if (isStud) 1.dp else 0.5.dp, borderColor),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (modePrefix != null) {
                Text(
                    text = modePrefix,
                    fontSize = 12.sp,
                    color = HangameColors.StudAccent,
                    fontWeight = FontWeight.Black,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = "·",
                    fontSize = 12.sp,
                    color = HangameColors.TextMuted,
                )
            }
            Text(
                text = left,
                fontSize = 12.sp,
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "|",
                fontSize = 12.sp,
                color = HangameColors.TextMuted,
            )
            Text(
                text = right,
                fontSize = 12.sp,
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun WaitingForNpc(nickname: String? = null, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = if (nickname.isNullOrBlank()) stringResource(id = R.string.waiting_for_npc)
            else "${nickname} 가 생각 중...",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExitQueuedBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HangameColors.PotValue.copy(alpha = 0.55f)),
        shadowElevation = 8.dp,
    ) {
        Text(
            text = stringResource(id = R.string.exit_queued),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
            fontSize = 12.sp,
            color = HangameColors.PotValue,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DealingPrepBadge(modifier: Modifier = Modifier) {
    val pulse = pulseFloat(initial = 0.65f, target = 1f, periodMs = 700, label = "deal-prep")
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        color = HangameColors.PotBg.copy(alpha = 0.88f),
        border = BorderStroke(1.5.dp, HangameColors.SeatBorder.copy(alpha = pulse)),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = "🃏", fontSize = 22.sp)
            Text(
                text = stringResource(id = R.string.prep_dealing),
                fontSize = 16.sp,
                color = HangameColors.TextPrimary.copy(alpha = pulse),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * 핸드 종료 시 펠트 상단 중앙에 등장하는 승자 배너.
 *
 *  - 🏆 + 승자 닉네임 + (본인 승리 시 "YOU WIN" 강조)
 *  - 한국어 핸드 카테고리 ("풀하우스", "투 페어" 등)
 *  - +지급 칩 (본인이면 라임, 그 외엔 골드)
 *  - 다음 핸드 카운트다운
 *
 * scaleIn + fadeIn 으로 등장, 골드 보더는 펄스. PayoutBadge 와 별개로 *누가* 이겼는지 즉시 인지.
 */
@Composable
private fun WinnerBanner(
    data: HandEndViewData,
    humanSeat: Int,
    autoNextCountdown: Int?,
    onNext: () -> Unit,
    onPauseAutoNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val allWinnerSeats = data.pots
        .flatMap { it.winnerSeats }
        .toSet()
        .ifEmpty { data.payoutsBySeat.filterValues { it > 0L }.keys }
    if (allWinnerSeats.isEmpty()) return

    val humanPayout = data.payoutsBySeat[humanSeat] ?: 0L
    val primaryWinnerSeat = if (humanPayout > 0L) humanSeat else allWinnerSeats.first()
    val winnerName = data.nicknameBySeat[primaryWinnerSeat] ?: "-"
    val winnerCategory = data.handInfos[primaryWinnerSeat] ?: ""
    val winnerPayout = data.payoutsBySeat[primaryWinnerSeat] ?: 0L
    val isHumanWinner = primaryWinnerSeat == humanSeat
    val winnerBest5 = data.bestFiveBySeat[primaryWinnerSeat].orEmpty()
    val splitPot = data.pots.firstOrNull()?.takeIf {
        data.mode == GameMode.SEVEN_STUD_HI_LO &&
            it.hiWinnerSeats.isNotEmpty() &&
            it.loWinnerSeats.isNotEmpty() &&
            it.hiWinnerSeats != it.loWinnerSeats
    }
    val isHiLoSplit = splitPot != null
    val multiWinners = (allWinnerSeats - primaryWinnerSeat)
        .mapNotNull { seat ->
            val nick = data.nicknameBySeat[seat] ?: return@mapNotNull null
            val payout = data.payoutsBySeat[seat] ?: 0L
            nick to payout
        }
        .takeIf { it.isNotEmpty() }

    val reduceMotion = LocalReduceMotion.current
    val glow = pulseFloat(
        initial = if (isHumanWinner) 0.4f else 0.55f,
        target = 1f,
        periodMs = if (isHumanWinner) 600 else 850,
        label = "winner-glow",
    )

    val shake = remember { Animatable(0f) }
    LaunchedEffect(isHumanWinner, reduceMotion) {
        if (isHumanWinner && !reduceMotion) {
            val keyframes = listOf(0f, 1f, -1f, 0.7f, -0.5f, 0f)
            val stepMs = 90
            for (k in keyframes) {
                shake.animateTo(k, tween(durationMillis = stepMs, easing = FastOutSlowInEasing))
            }
        }
    }

    // PotSweep 사운드(0ms) + 좌석 칩 카운트업(700ms) + 짧은 buffer 후 등장.
    // 사용자가 잔고 변화/팟 sweep 을 먼저 인지하고, 그 후 배너로 결과 정리.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(900L)
        visible = true
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) +
            scaleIn(tween(480, easing = FastOutSlowInEasing), initialScale = 0.55f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.85f),
        modifier = modifier.offset { IntOffset(x = (shake.value * 4.dp.toPx()).toInt(), y = 0) },
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = HangameColors.PotBg.copy(alpha = 0.97f),
            border = BorderStroke(2.5.dp, HangameColors.PotValue.copy(alpha = glow)),
            shadowElevation = 20.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 1) 큰 트로피
                Text(text = "🏆", fontSize = 48.sp)

                // 2) 승자 헤드라인 — "YOU WIN" / "{닉네임} 승" / "하이/로우 분할"
                Text(
                    text = when {
                        isHiLoSplit -> "하이 / 로우 분할"
                        isHumanWinner -> stringResource(id = R.string.winner_you_win)
                        else -> stringResource(id = R.string.winner_name_wins, winnerName)
                    },
                    fontSize = 30.sp,
                    color = HangameColors.PotValue,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )

                // 3) 본인 승리시 닉네임 부제, NPC 승리시 본인의 net 결과 미니 라벨
                if (isHumanWinner) {
                    Text(
                        text = winnerName,
                        fontSize = 14.sp,
                        color = HangameColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                // 4) 한국어 핸드 카테고리 — 큰 강조
                if (winnerCategory.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = HangameColors.HeaderBgRight.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = winnerCategory,
                            modifier = Modifier.padding(horizontal = 18.dp, vertical = 6.dp),
                            fontSize = 22.sp,
                            color = HangameColors.TextPrimary,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                // 5) HiLo 분할 시 hi/lo 승자 표시
                if (splitPot != null) {
                    val hiNames = splitPot.hiWinnerSeats
                        .mapNotNull { data.nicknameBySeat[it] }
                        .joinToString(", ")
                    val loNames = splitPot.loWinnerSeats
                        .mapNotNull { data.nicknameBySeat[it] }
                        .joinToString(", ")
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(
                            text = "Hi · $hiNames",
                            fontSize = 13.sp,
                            color = HangameColors.HiLoHiBadge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "Lo · $loNames",
                            fontSize = 13.sp,
                            color = HangameColors.HiLoLoBadge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }

                // 6) 베스트 5장 카드 — 승자가 어떤 패로 이겼는지 시각화
                if (winnerBest5.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        winnerBest5.take(5).forEach { card ->
                            PlayingCard(
                                card = card,
                                faceDown = false,
                                width = 38.dp,
                                height = 54.dp,
                            )
                        }
                    }
                }

                // 7) 지급 칩 — 가장 큰 강조
                if (winnerPayout > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(text = "🪙", fontSize = 24.sp)
                        Text(
                            text = "+${ChipFormat.format(winnerPayout)}",
                            fontSize = 32.sp,
                            color = if (isHumanWinner) HangameColors.TextLime else HangameColors.PotValue,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }

                // 8) 다중 승자(사이드팟) — 작은 라인
                if (multiWinners != null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        multiWinners.forEach { (nick, amt) ->
                            Text(
                                text = "$nick +${ChipFormat.format(amt)}",
                                fontSize = 12.sp,
                                color = HangameColors.TextMuted,
                            )
                        }
                    }
                }

                // 9) Uncalled 환급 (있으면)
                if (data.uncalledBySeat.isNotEmpty()) {
                    val uncalledLine = data.uncalledBySeat.entries
                        .sortedBy { it.key }
                        .joinToString("  ·  ") { (seat, chips) ->
                            val nick = data.nicknameBySeat[seat] ?: "#$seat"
                            "$nick 환급 +${ChipFormat.format(chips)}"
                        }
                    Text(
                        text = uncalledLine,
                        fontSize = 11.sp,
                        color = HangameColors.TextMuted,
                    )
                }

                // 10) 액션 — "다음 핸드" + 카운트다운 / "정지"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (autoNextCountdown != null) {
                        OutlinedButton(
                            onClick = onPauseAutoNext,
                            modifier = Modifier.height(48.dp),
                        ) { Text("정지", fontWeight = FontWeight.SemiBold) }
                    }
                    Button(
                        onClick = onNext,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HangameColors.BtnCall,
                            contentColor = Color.White,
                        ),
                    ) {
                        val btnText = if (autoNextCountdown != null) {
                            stringResource(id = R.string.hand_end_next) + " (${autoNextCountdown}s)"
                        } else {
                            stringResource(id = R.string.hand_end_next)
                        }
                        Text(text = btnText, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

// -------------------------------------------------------------------------
// Preview
// -------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 720, widthDp = 360)
@Composable
private fun TableScreenPreview() {
    val config = TableConfig(mode = GameMode.HOLDEM_NL, seats = 2)
    val state = GameState(
        mode = GameMode.HOLDEM_NL,
        config = config,
        stateVersion = 1L,
        handIndex = 1L,
        players = listOf(
            PlayerState(seat = 0, nickname = "나", isHuman = true, chips = 9_950L, committedThisHand = 50L, committedThisStreet = 50L),
            PlayerState(seat = 1, nickname = "프로", isHuman = false, personaId = "PRO", chips = 9_950L, committedThisHand = 50L, committedThisStreet = 50L),
        ),
        btnSeat = 0,
        toActSeat = 0,
        street = Street.PREFLOP,
        community = emptyList(),
        betToCall = 50L,
        minRaise = 100L,
    )
    PokerMasterTheme {
        TableContent(
            state = state,
            onAction = {},
            onNextHand = {},
            onSurrender = {},
            onExit = {},
        )
    }
}
