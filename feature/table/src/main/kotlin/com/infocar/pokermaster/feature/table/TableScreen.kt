package com.infocar.pokermaster.feature.table

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.engine.controller.StudReducer
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import kotlinx.coroutines.CoroutineScope
import com.infocar.pokermaster.feature.table.anim.pulseFloat
import com.infocar.pokermaster.feature.table.guide.GuideOverlay
import com.infocar.pokermaster.feature.table.guide.GuideSettings
import com.infocar.pokermaster.feature.table.guide.GuideStep
import com.infocar.pokermaster.feature.table.settings.SettingsRepository
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
    /** Phase5-II-B: LLM advisor. null 이면 DecisionCore-only 경로. */
    llmAdvisor: LlmAdvisor? = null,
    /** M5-B: 핸드 히스토리 Repository + Application scope. null 이면 저장 생략. */
    historyRepo: HandHistoryRepository? = null,
    historyScope: CoroutineScope? = null,
    /** M6-C: chip wallet. null 이면 buy-in/settle 스킵. */
    walletRepo: WalletRepository? = null,
    viewModel: TableViewModel = run {
        val ctx = LocalContext.current.applicationContext
        remember(mode, seats, humanBuyIn, llmAdvisor, historyRepo, historyScope, walletRepo) {
            TableViewModel.createDefault(
                context = ctx,
                mode = mode,
                seats = seats,
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

    // 3초 프리딜 — 카드 / 액션바 / NPC tick 까지 함께 막아 준비 시간 확보.
    var dealReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(3_000L)
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
    // wallet 잔고 갱신을 보장한 뒤 로비 복귀하는 단일 wrapper. TableContent 의 X/메뉴 나가기,
    // LaunchedEffect(gameOver) 모두 이 함수만 호출하면 settled flag 로 중복 settle 방지됨.
    val onExitSettled: () -> Unit = {
        exitScope.launch {
            viewModel.settleAndCloseAwait()
            onExit()
        }
    }

    // 게임 오버 시 정산 애니 2초 → settle 동기 await → 로비 복귀.
    LaunchedEffect(gameOver) {
        if (gameOver != null) {
            delay(2_000L)
            onExitSettled()
        }
    }

    // SFX/Haptic — Sprint2-G Phase 3 + Sprint3-A DataStore.
    val context = LocalContext.current
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

    val onHumanActionWithSfx: OnAction = { action ->
        if (sfxPolicy.hapticEnabled) {
            when (action.type) {
                ActionType.RAISE, ActionType.ALL_IN, ActionType.BET,
                ActionType.COMPLETE, ActionType.BRING_IN -> haptic.onChipCommit()
                else -> haptic.onAction()
            }
        }
        if (sfxPolicy.soundEnabled) {
            val kind = when (action.type) {
                ActionType.CHECK -> SfxKind.Check
                ActionType.FOLD -> SfxKind.Fold
                ActionType.ALL_IN -> SfxKind.AllIn
                else -> SfxKind.ChipCommit
            }
            sound.play(kind)
        }
        viewModel.onHumanAction(action)
    }

    // Guide overlay — Sprint2-G Phase 4 + Sprint3-A DataStore.
    val guideSettings by settingsRepo.guideSettings.collectAsState(initial = GuideSettings.Default)
    var currentGuideStep by remember { mutableStateOf<GuideStep?>(null) }
    // 최초 guideSettings 도달 시 한 번만 초기 step 결정 (이후 토글은 명시적으로 처리).
    LaunchedEffect(Unit) {
        // M7-BugFix: DataStore IO 예외(디스크 풀/파일 손상) 가 composable scope 로 전파되면
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

    CompositionLocalProvider(
        LocalHighContrastCards provides a11ySettings.highContrastCards,
        LocalReduceMotion provides a11ySettings.reduceMotion,
    ) {
    Box(modifier = Modifier.fillMaxSize()) {
        TableContent(
            state = displayState,
            onAction = onHumanActionWithSfx,
            onNextHand = viewModel::onNextHand,
            onSurrender = viewModel::onSurrender,
            onExit = onExitSettled,
            guideEnabled = guideSettings.guideModeEnabled,
            onToggleGuide = onToggleGuide,
            autoNextCountdown = autoNextCountdown,
            gameOver = gameOver,
            lastActions = lastActions,
            dealReady = dealReady,
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
    guideEnabled: Boolean = true,
    onToggleGuide: () -> Unit = {},
    autoNextCountdown: Int? = null,
    gameOver: GameOverInfo? = null,
    lastActions: Map<Int, String> = emptyMap(),
    /** 진입 직후 3초 딜러 준비 대기. false 면 액션바/Waiting 둘 다 숨긴다. */
    dealReady: Boolean = true,
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmAllIn by remember { mutableStateOf<Pair<ActionType, Long>?>(null) }
    val humanSeat = remember(state) { state.players.firstOrNull { it.isHuman }?.seat ?: 0 }
    val actionBarState = remember(state) { TableUiMapper.mapActionBar(state, humanSeat) }
    val handEndData = remember(state) { TableUiMapper.mapHandEnd(state) }
    val isShowdown = state.pendingShowdown != null || state.street == Street.SHOWDOWN
    val winnerSeats = remember(state.pendingShowdown) {
        state.pendingShowdown?.pots?.flatMap { it.winnerSeats }?.toSet() ?: emptySet()
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
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CenterPotDisplay(pot = TableUiMapper.totalPot(state))
                        // 7스터드는 커뮤니티 카드 없음 — community row 대신 스트릿 표기.
                        if (state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO) {
                            StreetLabel(state.street)
                        } else {
                            CardCommunityRow(community = state.community)
                        }
                        // 정산 표시는 시트 우측 PayoutBadge (펄스 골드) 로 충분 —
                        // 가운데 영역 좁아 추가 텍스트는 짤림.
                    }
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 48.dp)
                    .padding(top = 8.dp, bottom = 72.dp),
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
                    InGameMenuDropdown(
                        expanded = menuOpen,
                        onDismiss = { menuOpen = false },
                        onSurrender = onSurrender,
                        onExit = onExit,
                        guideEnabled = guideEnabled,
                        onToggleGuide = onToggleGuide,
                    )
                }
                IconButton(onClick = onExit) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "나가기",
                        tint = HangameColors.TextSecondary,
                    )
                }
            }

            // 6) 하단 액션바 — 쇼다운 / 프리딜 대기 동안에는 액션바/Waiting 토스트 모두 숨김.
            if (actionBarState != null && state.pendingShowdown == null && dealReady) {
                ActionBar(
                    state = actionBarState,
                    onAction = onAction,
                    onRequestConfirm = { type, amount -> confirmAllIn = type to amount },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .widthIn(max = 920.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            } else if (state.pendingShowdown == null && gameOver == null && dealReady) {
                WaitingForNpc(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }

            if (state.pendingShowdown != null && handEndData != null) {
                WinnerBanner(
                    data = handEndData,
                    humanSeat = humanSeat,
                    autoNextCountdown = autoNextCountdown,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 64.dp),
                )
            }

            // 게임 오버 시 별도 오버레이/다이얼로그 X — 정산 애니가 끝난 뒤 LaunchedEffect 가
            // 자동으로 onExit() 호출 (사용자 룰).
        }

        // 베팅 2단계 확인 (Phase-C 구성)
        confirmAllIn?.let { (type, amount) ->
            BettingConfirmDialog(
                type = type,
                amount = amount,
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
 */
@Composable
private fun HangameFelt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(180.dp))
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        HangameColors.FeltInner,
                        HangameColors.FeltMid,
                        HangameColors.FeltOuter,
                    ),
                ),
            )
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
        PlayerSeat(
            player = player,
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
            extraBadgeLabel = seatBadges[player.seat],
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
                    Modifier.fillMaxWidth().weight(0.32f),
                    contentAlignment = Alignment.Center,
                ) { seat(npcs[0]) }
                Box(
                    Modifier.fillMaxWidth().weight(0.36f),
                    contentAlignment = Alignment.Center,
                ) { centerContent() }
                Box(
                    Modifier.fillMaxWidth().weight(0.32f),
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
                    Modifier.fillMaxWidth().weight(0.32f),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
                        seat(npcs[0])
                        seat(npcs[1])
                    }
                }
                Box(
                    Modifier.fillMaxWidth().weight(0.36f),
                    contentAlignment = Alignment.Center,
                ) { centerContent() }
                Box(
                    Modifier.fillMaxWidth().weight(0.32f),
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
                    Modifier.fillMaxWidth().weight(0.28f),
                    contentAlignment = Alignment.Center,
                ) { seat(npcs[1]) }
                Box(
                    Modifier.fillMaxWidth().weight(0.40f),
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
            SeatLayout(
                players = state.players,
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
            text = "Total",
            fontSize = 10.sp,
            color = HangameColors.PotLabel,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(text = "🪙", fontSize = 14.sp)
            Text(
                text = ChipFormat.format(pot),
                fontSize = 18.sp,
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
 * 7스터드 스트릿 라벨 — community row 자리에 "3rd street" 등 표기.
 */
@Composable
private fun StreetLabel(street: Street) {
    val text = when (street) {
        Street.THIRD -> "3rd street"
        Street.FOURTH -> "4th street"
        Street.FIFTH -> "5th street"
        Street.SIXTH -> "6th street"
        Street.SEVENTH -> "7th street"
        Street.SHOWDOWN -> "Showdown"
        else -> "—"
    }
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.6f),
        border = BorderStroke(0.5.dp, HangameColors.SeatBorder),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            fontSize = 12.sp,
            color = HangameColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * 우상단 블라인드/베팅 정보 표시.
 *  - 홀덤: "SB 50 | BB 100"
 *  - 7스터드/HiLo: "앤티 10 | 브링인 25"
 */
@Composable
private fun BlindInfoBadge(state: GameState) {
    val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
    val left = if (isStud) "앤티 ${ChipFormat.format(state.config.ante)}"
    else "SB ${ChipFormat.format(state.config.smallBlind)}"
    val right = if (isStud) "브링인 ${ChipFormat.format(state.config.bringIn)}"
    else "BB ${ChipFormat.format(state.config.bigBlind)}"
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.8f),
        border = BorderStroke(0.5.dp, HangameColors.SeatBorder),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = left,
                fontSize = 11.sp,
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
            Text(
                text = "|",
                fontSize = 11.sp,
                color = HangameColors.TextMuted,
            )
            Text(
                text = right,
                fontSize = 11.sp,
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

@Composable
private fun WaitingForNpc(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = stringResource(id = R.string.waiting_for_npc),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
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
    modifier: Modifier = Modifier,
) {
    val firstPot = data.pots.firstOrNull() ?: return
    val firstWinnerSeat = firstPot.winnerSeats.firstOrNull() ?: return
    val winnerName = data.nicknameBySeat[firstWinnerSeat] ?: "-"
    val winnerCategory = data.handInfos[firstWinnerSeat] ?: ""
    val winnerPayout = data.payoutsBySeat[firstWinnerSeat] ?: 0L
    val isHumanWinner = firstWinnerSeat == humanSeat
    val others = (firstPot.winnerSeats - firstWinnerSeat)
        .mapNotNull { data.nicknameBySeat[it] }
        .joinToString(", ")
        .ifEmpty { null }

    val glow = pulseFloat(initial = 0.55f, target = 1f, periodMs = 850, label = "winner-glow")

    // visible 을 false→true 로 토글해야 AnimatedVisibility 의 enter 트랜지션이 실제로 발생.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(420)) +
            scaleIn(tween(480, easing = FastOutSlowInEasing), initialScale = 0.55f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.85f),
        modifier = modifier,
    ) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = HangameColors.PotBg.copy(alpha = 0.95f),
            border = BorderStroke(2.5.dp, HangameColors.PotValue.copy(alpha = glow)),
            shadowElevation = 16.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(text = "🏆", fontSize = 26.sp)
                    Text(
                        text = if (isHumanWinner) {
                            stringResource(id = R.string.winner_you_win)
                        } else {
                            stringResource(id = R.string.winner_name_wins, winnerName)
                        },
                        fontSize = 22.sp,
                        color = HangameColors.PotValue,
                        fontWeight = FontWeight.Black,
                    )
                }
                if (isHumanWinner) {
                    Text(
                        text = winnerName,
                        fontSize = 13.sp,
                        color = HangameColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                if (winnerCategory.isNotEmpty()) {
                    Text(
                        text = winnerCategory,
                        fontSize = 16.sp,
                        color = HangameColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (winnerPayout > 0L) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(text = "🪙", fontSize = 16.sp)
                        Text(
                            text = stringResource(
                                id = R.string.winner_payout,
                                ChipFormat.format(winnerPayout),
                            ),
                            fontSize = 18.sp,
                            color = if (isHumanWinner) HangameColors.TextLime else HangameColors.PotValue,
                            fontWeight = FontWeight.ExtraBold,
                        )
                    }
                }
                if (others != null) {
                    Text(
                        text = stringResource(id = R.string.winner_split_with, others),
                        fontSize = 11.sp,
                        color = HangameColors.TextMuted,
                    )
                }
                if (autoNextCountdown != null) {
                    Text(
                        text = stringResource(id = R.string.auto_next_in, autoNextCountdown),
                        fontSize = 11.sp,
                        color = HangameColors.PotLabel,
                        fontWeight = FontWeight.Medium,
                    )
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
