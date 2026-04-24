package com.infocar.pokermaster.feature.table

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.ActionLogEntry
import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.data.wallet.BuyInResult
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.ShowdownSummary
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.controller.GameController
import com.infocar.pokermaster.engine.controller.ResumeSeed
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.rules.Rng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 테이블 화면 ViewModel. [GameController] 를 감싸 [StateFlow] 로 UI 에 노출.
 *
 * 핵심 책임:
 *  - NPC 턴 자동 처리 (백그라운드 tick).
 *  - Resume 스냅샷 로드/저장 (v1.1 §1.2.D).
 *  - 사람 액션 디스패치 + 다음 핸드 / 항복 처리.
 *
 * M3 MVP: 2인 헤즈업 홀덤. 로비에서 모드 전달 → NPC 기본 persona = PRO.
 */
class TableViewModel private constructor(
    private val config: TableConfig,
    private val initialPlayers: List<PlayerState>,
    private val resumeRepo: ResumeRepository?,
    /** NPC 턴 간 UI 연출용 최소 대기(ms). 너무 빠르면 사용자가 NPC 액션을 놓침. */
    private val npcDelayMs: Long = 700L,
    /**
     * LLM advisor. null 이면 기존 DecisionCore/persona 결정만 사용 (모델 미로드/미지원 단말).
     * Hilt 주입은 [createDefault] 팩토리에서 옵션으로 받아 내려준다.
     */
    private val llmAdvisor: LlmAdvisor? = null,
    /**
     * M5-B: 핸드 히스토리 저장소. null 이면 기록 생략 (테스트/프리뷰). Application scope 에서
     * 저장해야 핸드 종료 직후 VM cleared 되어도 기록이 살아남음.
     */
    private val historyRepo: HandHistoryRepository? = null,
    private val historyScope: CoroutineScope? = null,
    /** M6-C: chip wallet. null 이면 buy-in/settle 스킵 (기존 호환). */
    private val walletRepo: WalletRepository? = null,
) : ViewModel() {

    private var controller: GameController = GameController(config, initialPlayers)

    private val _state = MutableStateFlow(controller.state)
    val state: StateFlow<GameState> = _state.asStateFlow()

    private val _resumePrompt = MutableStateFlow<ResumePrompt?>(null)
    val resumePrompt: StateFlow<ResumePrompt?> = _resumePrompt.asStateFlow()

    private var tickJob: Job? = null

    // M5-B: 핸드 히스토리 수집 버퍼. handIndex 가 바뀔 때마다 리셋.
    private var currentHandIndex: Long = controller.state.handIndex
    private var currentHandInitialState: GameState = controller.state
    private var currentHandActions: MutableList<ActionLogEntry> = mutableListOf()
    private val historyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // M6-C: 마지막 settle 중복 방지 플래그.
    private var settled: Boolean = false

    init {
        val snap = resumeRepo?.load()
        if (snap != null && snap.state.mode == config.mode) {
            // 복원 제안만 노출 — 사용자 결정 전까지 NPC tick 시작하지 않음.
            _resumePrompt.value = snap.toPrompt()
        } else {
            // 새 핸드부터 바로 진행.
            startTicking()
        }
        // M6-C: 테이블 세션 시작 시 wallet 에서 buy-in 차감 (LobbyVM 이 잔고 검증 후 진입).
        walletRepo?.let { repo ->
            viewModelScope.launch { repo.buyIn(WalletRepository.TABLE_STAKE) }
        }
    }

    /**
     * M6-C: 현재 인간 좌석의 최종 chips 를 wallet 에 적립. 중복 호출 방지 (settled flag).
     * 사용자가 onExit 하거나 ViewModel 이 cleared 될 때 호출.
     */
    fun settleAndClose() {
        if (settled) return
        settled = true
        val repo = walletRepo ?: return
        val scope = historyScope ?: return
        val finalChips = _state.value.players.firstOrNull { it.isHuman }?.chips ?: 0L
        scope.launch(kotlinx.coroutines.NonCancellable) {
            runCatching { repo.settle(finalChips) }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // onExit 에서 이미 settle 되지 않은 경우 방어선 — 앱 강제 종료 등.
        settleAndClose()
    }

    // ---------------------------------------------------------------- Actions

    fun onHumanAction(action: Action) {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        val streetBefore = s.street
        val next = controller.humanAct(action)
        // M5-B: human 액션 로그 + 핸드 종료 감지.
        logAction(seat = seat, action = action, streetOrdinal = streetBefore.ordinal)
        _state.value = next
        maybeRecordFinishedHand(next)
        persistSnapshot()
        startTicking()
    }

    fun onNextHand() {
        val s = _state.value
        if (s.pendingShowdown != null) {
            controller.ackShowdown()
        }
        val active = controller.state.players.count { it.chips > 0 }
        if (active >= 2) {
            _state.value = controller.nextHand()
            // M5-B: 다음 핸드 초기 상태/seed 새로 캡처 + 액션 로그 초기화.
            resetHistoryBufferFor(controller.state)
            persistSnapshot()
            startTicking()
        } else {
            _state.value = controller.state
            // 파산 리셋은 v1.1 §1.2.L — M3 범위 외.
        }
    }

    fun onSurrender() {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        onHumanAction(Action(ActionType.FOLD))
    }

    // ---------------------------------------------------------------- Resume

    /** 사용자가 "이어하기" 선택. */
    fun onResumeAccept() {
        val snap = resumeRepo?.load() ?: run {
            _resumePrompt.value = null
            startTicking()
            return
        }
        val rng = Rng.ofSeeds(
            serverSeed = snap.rngServerSeedHex.hexToBytes(),
            clientSeed = snap.rngClientSeedHex.hexToBytes(),
            nonce = snap.rngNonce,
        )
        controller = GameController(
            config = config,
            initialPlayers = initialPlayers,
            resumeFrom = ResumeSeed(state = snap.state, rng = rng),
        )
        _state.value = controller.state
        // M5-B: 재개된 핸드는 현재 상태를 "시작" 으로 간주 (이전 액션 로그는 소실).
        resetHistoryBufferFor(controller.state)
        _resumePrompt.value = null
        startTicking()
    }

    /** "포기하고 새 핸드" (discard=true) 또는 "취소=snapshot 유지 + 새 핸드" (discard=false). */
    fun onResumeDismiss(discard: Boolean) {
        if (discard) resumeRepo?.clear()
        _resumePrompt.value = null
        startTicking()
    }

    // ---------------------------------------------------------------- Internal

    private fun startTicking() {
        if (tickJob?.isActive == true) return
        tickJob = viewModelScope.launch {
            while (true) {
                val s = _state.value
                val seat = s.toActSeat ?: return@launch
                if (s.pendingShowdown != null) return@launch
                val p = s.players.firstOrNull { it.seat == seat } ?: return@launch
                if (p.isHuman) return@launch
                // M7-BugFix: toAct가 inactive seat(all-in/folded)을 가리키는 drift 상태라면
                // NPC act 호출 시 engine 이 require(active) 트립. 탈출시켜 freeze 방지.
                if (!p.active) {
                    android.util.Log.w(
                        "TableVM",
                        "tick: toAct seat=$seat is inactive (folded=${p.folded}, allIn=${p.allIn}) — aborting tick",
                    )
                    return@launch
                }
                delay(npcDelayMs)
                // Phase5-II-B: advisor 가 있으면 LLM → DecisionCore 폴백 경로, 없으면 pure
                // DecisionCore. M5-B: npcActAndLog 로 action 과 streetBefore 도 함께 수거.
                val result = try {
                    withContext(Dispatchers.Default) {
                        controller.npcActAndLog(llmAdvisor)
                    }
                } catch (ce: kotlinx.coroutines.CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    // M7-BugFix: engine에서 assertion/크래시가 나도 전체 앱 죽이지 않음.
                    // 다음 인간 액션이나 resume으로 복구될 수 있도록 tick만 중단.
                    android.util.Log.e("TableVM", "NPC tick failed — aborting loop", t)
                    return@launch
                }
                logAction(seat = result.actorSeat, action = result.action, streetOrdinal = result.streetBefore.ordinal)
                _state.value = result.state
                maybeRecordFinishedHand(result.state)
                persistSnapshot()
            }
        }
    }

    // ---------------------------------------------------------------- History

    /** 현재 버퍼에 action 추가. handIndex 바뀐 경우 먼저 reset. */
    private fun logAction(seat: Int, action: Action, streetOrdinal: Int) {
        // handIndex 가 바뀌었다면 onNextHand() 가 이미 resetHistoryBufferFor 를 호출했을 텐데
        // 안전망으로 한 번 더 체크.
        val currentIndex = controller.state.handIndex
        if (currentIndex != currentHandIndex) resetHistoryBufferFor(controller.state)
        currentHandActions.add(ActionLogEntry(seat = seat, action = action, streetIndex = streetOrdinal))
    }

    private fun resetHistoryBufferFor(state: GameState) {
        currentHandIndex = state.handIndex
        currentHandInitialState = state
        currentHandActions = mutableListOf()
    }

    /** 쇼다운 pending 이 막 생겼으면 히스토리 저장. 이미 저장했으면 중복 방지. */
    private var lastRecordedHandIndex: Long = -1L
    private fun maybeRecordFinishedHand(state: GameState) {
        if (state.pendingShowdown == null) return
        if (state.handIndex == lastRecordedHandIndex) return
        val repo = historyRepo ?: return
        val scope = historyScope ?: return
        lastRecordedHandIndex = state.handIndex

        val rng = controller.rng
        val record = HandHistoryRecord(
            id = 0,
            mode = state.mode.name,
            handIndex = state.handIndex,
            startedAt = nowMs() - estimateHandDurationMs(currentHandActions.size),
            endedAt = nowMs(),
            seedCommitHex = currentHandInitialState.rngCommitHex,
            serverSeedHex = rng.serverSeed.toHexLower(),
            clientSeedHex = rng.clientSeed.toHexLower(),
            nonce = rng.nonce,
            initialState = currentHandInitialState,
            actions = currentHandActions.toList(),
            resultJson = runCatching {
                historyJson.encodeToString(ShowdownSummary.serializer(), state.pendingShowdown!!)
            }.getOrElse { "{}" },
            winnerSeat = state.pendingShowdown?.payouts
                ?.maxByOrNull { it.value }?.key,
            potSize = state.players.sumOf { it.committedThisHand },
        )
        scope.launch(NonCancellable) {
            runCatching { repo.record(record) }
        }
    }

    private fun nowMs(): Long = System.currentTimeMillis()

    /** action 수당 대략 1초 가정 (UI delay 포함) — 정확한 시작시간은 aux 지표. */
    private fun estimateHandDurationMs(actionCount: Int): Long =
        (actionCount * 1000L).coerceAtLeast(1000L)

    // ---------------------------------------------------------------- Snapshot

    private fun persistSnapshot() {
        val repo = resumeRepo ?: return
        val s = _state.value
        // 쇼다운 상태는 보존 가치 낮음 — 다음 핸드 시작 후 저장.
        if (s.pendingShowdown != null) {
            repo.clear()
            return
        }
        val r = controller.rng
        repo.save(
            ResumeSnapshot(
                state = s,
                rngServerSeedHex = r.serverSeed.toHexLower(),
                rngClientSeedHex = r.clientSeed.toHexLower(),
                rngNonce = r.nonce,
            )
        )
    }

    companion object {
        /**
         * M3 MVP 기본 세팅: 2인 헤즈업 홀덤 (SB 25 / BB 50, 10k chips).
         * Context 를 받으면 ResumeRepository 가 활성화됨. null 이면 비활성.
         */
        fun createDefault(
            context: Context?,
            mode: GameMode = GameMode.HOLDEM_NL,
            humanNickname: String = "나",
            npcPersonaId: String = "PRO",
            startingChips: Long = WalletRepository.TABLE_STAKE,
            /** Phase5-II-B: LLM advisor. */
            llmAdvisor: LlmAdvisor? = null,
            /** M5-B: 핸드 히스토리 저장소 + scope. */
            historyRepo: HandHistoryRepository? = null,
            historyScope: CoroutineScope? = null,
            /** M6-C: 지갑. null 이면 buy-in/settle 스킵 (테스트/프리뷰 호환). */
            walletRepo: WalletRepository? = null,
        ): TableViewModel {
            require(mode == GameMode.HOLDEM_NL) {
                "M3 MVP supports HOLDEM_NL only (mode=$mode)"
            }
            val config = TableConfig(mode = mode, seats = 2)
            val players = listOf(
                PlayerState(
                    seat = 0, nickname = humanNickname, isHuman = true,
                    personaId = null, chips = startingChips,
                ),
                PlayerState(
                    seat = 1, nickname = "프로", isHuman = false,
                    personaId = npcPersonaId, chips = startingChips,
                ),
            )
            val repo = context?.let { ResumeRepository(it) }
            return TableViewModel(
                config = config,
                initialPlayers = players,
                resumeRepo = repo,
                llmAdvisor = llmAdvisor,
                historyRepo = historyRepo,
                historyScope = historyScope,
                walletRepo = walletRepo,
            )
        }
    }
}

// ---------------------------------------------------------------------------- Hex helpers

private fun ByteArray.toHexLower(): String =
    joinToString("") { "%02x".format(it) }

private fun String.hexToBytes(): ByteArray {
    require(length % 2 == 0) { "hex string must have even length" }
    return ByteArray(length / 2) { i ->
        val hi = Character.digit(this[i * 2], 16)
        val lo = Character.digit(this[i * 2 + 1], 16)
        require(hi >= 0 && lo >= 0) { "invalid hex char at index ${i * 2}" }
        ((hi shl 4) or lo).toByte()
    }
}
