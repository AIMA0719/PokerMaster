package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
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
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import kotlinx.coroutines.CoroutineScope
import com.infocar.pokermaster.feature.table.guide.GuideOverlay
import com.infocar.pokermaster.feature.table.guide.GuideSettings
import com.infocar.pokermaster.feature.table.guide.GuideStep
import com.infocar.pokermaster.feature.table.settings.SettingsRepository
import com.infocar.pokermaster.feature.table.sfx.HapticManager
import com.infocar.pokermaster.feature.table.sfx.SfxKind
import com.infocar.pokermaster.feature.table.sfx.SfxPolicy
import com.infocar.pokermaster.feature.table.sfx.SoundManager
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
    /** Phase5-II-B: LLM advisor. null 이면 DecisionCore-only 경로. */
    llmAdvisor: LlmAdvisor? = null,
    /** M5-B: 핸드 히스토리 Repository + Application scope. null 이면 저장 생략. */
    historyRepo: HandHistoryRepository? = null,
    historyScope: CoroutineScope? = null,
    /** M6-C: chip wallet. null 이면 buy-in/settle 스킵. */
    walletRepo: WalletRepository? = null,
    viewModel: TableViewModel = run {
        val ctx = LocalContext.current.applicationContext
        remember(mode, llmAdvisor, historyRepo, historyScope, walletRepo) {
            TableViewModel.createDefault(
                context = ctx,
                mode = mode,
                llmAdvisor = llmAdvisor,
                historyRepo = historyRepo,
                historyScope = historyScope,
                walletRepo = walletRepo,
            )
        }
    },
) {
    val state by viewModel.state.collectAsState()
    val resumePrompt by viewModel.resumePrompt.collectAsState()

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
        val first = settingsRepo.guideSettings.first()
        currentGuideStep = if (first.guideModeEnabled) first.initialStep() else null
    }
    val onToggleGuide: () -> Unit = {
        val next = if (guideSettings.guideModeEnabled) guideSettings.disable() else guideSettings.enable()
        scope.launch { settingsRepo.setGuideSettings(next) }
        currentGuideStep = if (next.guideModeEnabled) next.initialStep() else null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        TableContent(
            state = state,
            onAction = onHumanActionWithSfx,
            onNextHand = viewModel::onNextHand,
            onSurrender = viewModel::onSurrender,
            onExit = onExit,
            guideEnabled = guideSettings.guideModeEnabled,
            onToggleGuide = onToggleGuide,
        )
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
    resumePrompt?.let { prompt ->
        ResumeDialog(
            prompt = prompt,
            onResume = viewModel::onResumeAccept,
            onDiscard = { viewModel.onResumeDismiss(discard = true) },
            onCancel = { viewModel.onResumeDismiss(discard = false) },
        )
    }
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
) {
    var menuOpen by remember { mutableStateOf(false) }
    var confirmAllIn by remember { mutableStateOf<Pair<ActionType, Long>?>(null) }
    val humanSeat = remember(state) { state.players.firstOrNull { it.isHuman }?.seat ?: 0 }
    val actionBarState = remember(state) { TableUiMapper.mapActionBar(state, humanSeat) }
    val handEndData = remember(state) { TableUiMapper.mapHandEnd(state) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .background(MaterialTheme.colorScheme.background),
        ) {
            // 1) 상단 바 (메뉴 + 폴백 배지 자리)
            TopBar(
                onOpenMenu = { menuOpen = true },
                potLabel = ChipFormat.format(TableUiMapper.totalPot(state)),
                street = state.street,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
            )

            // 2) 중앙 — 좌석 레이아웃 + 커뮤니티 카드 (SeatLayout / CardCommunityRow: Agent A·C)
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxSize()
                    .padding(top = 48.dp, bottom = 220.dp)
            ) {
                SeatLayout(
                    players = state.players,
                    btnSeat = state.btnSeat,
                    toActSeat = state.toActSeat,
                    humanSeat = humanSeat,
                    modifier = Modifier.fillMaxSize(),
                )
                CardCommunityRow(
                    community = state.community,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            // 3) 하단 — 액션바 (Agent B)
            if (actionBarState != null) {
                ActionBar(
                    state = actionBarState,
                    onAction = onAction,
                    onRequestConfirm = { type, amount -> confirmAllIn = type to amount },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                )
            } else if (state.pendingShowdown == null) {
                WaitingForNpc(modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }

            // 4) 핸드 종료 오버레이
            if (handEndData != null) {
                HandEndSheet(
                    data = handEndData,
                    onNext = onNextHand,
                    onInsights = { /* M5 사고 과정 해설 */ },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }

        // 인게임 메뉴 바텀시트 (Phase-C 구성)
        if (menuOpen) {
            InGameMenuSheet(
                onDismiss = { menuOpen = false },
                onSurrender = { menuOpen = false; onSurrender() },
                onExit = { menuOpen = false; onExit() },
                guideEnabled = guideEnabled,
                onToggleGuide = { menuOpen = false; onToggleGuide() },
            )
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

@Composable
private fun TopBar(
    onOpenMenu: () -> Unit,
    potLabel: String,
    street: Street,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onOpenMenu) {
            Icon(Icons.Default.Menu, contentDescription = "메뉴")
        }
        Spacer(Modifier.width(8.dp))
        // 폴백 모드 배지 (Phase-C에서 조건부)
        FallbackModeBadge()
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = stringResource(id = R.string.pot_label, potLabel),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = street.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Spacer(Modifier.width(12.dp))
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
