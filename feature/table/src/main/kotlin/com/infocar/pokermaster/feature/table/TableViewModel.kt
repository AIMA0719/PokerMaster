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
    private var autoNextJob: Job? = null

    /** 게임 오버 상태: 승자 닉네임 + 최종 칩. null 이면 게임 진행 중. */
    private val _gameOver = MutableStateFlow<GameOverInfo?>(null)
    val gameOver: StateFlow<GameOverInfo?> = _gameOver.asStateFlow()

    /** 쇼다운 후 자동 다음 핸드 카운트다운 (초). null 이면 카운트다운 없음. */
    private val _autoNextCountdown = MutableStateFlow<Int?>(null)
    val autoNextCountdown: StateFlow<Int?> = _autoNextCountdown.asStateFlow()

    /** 좌석별 마지막 액션 라벨 — 2초 후 자동 소멸. */
    private val _lastActions = MutableStateFlow<Map<Int, String>>(emptyMap())
    val lastActions: StateFlow<Map<Int, String>> = _lastActions.asStateFlow()
    private var clearActionJob: Job? = null

    // M5-B: 핸드 히스토리 수집 버퍼. handIndex 가 바뀔 때마다 리셋.
    private var currentHandIndex: Long = controller.state.handIndex
    private var currentHandInitialState: GameState = controller.state
    private var currentHandActions: MutableList<ActionLogEntry> = mutableListOf()
    private val historyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // M6-C: 마지막 settle 중복 방지 플래그.
    private var settled: Boolean = false

    /** 쇼다운 후 자동 다음 핸드까지 대기 시간(초). 사용자 룰: "한 판 끝나고 다음 판까지 3초". */
    private val autoNextDelaySeconds = 3

    /**
     * 테이블 진입 직후 NPC tick 시작 전 휴식(ms).
     * 사용자 룰: "홀덤 누르면 3초 대기하고 시작" + 카드 딜링 애니(약 1.5~2s) 마무리까지 여유.
     */
    private val initialRestMs = 4_500L

    init {
        val snap = resumeRepo?.load()
        if (snap != null && snap.state.mode == config.mode) {
            _resumePrompt.value = snap.toPrompt()
        } else {
            // 진입 직후 2초 휴식 — 카드 딜링 애니 + 사용자 화면 적응 시간. 별도 카운트다운
            // 없이 NPC tick 만 지연.
            viewModelScope.launch {
                delay(initialRestMs)
                startTicking()
            }
        }
        // 테이블 세션 시작 시 wallet 에서 본인 buy-in 만큼 차감 (사용자 룰: 본인 chips=wallet 전체).
        walletRepo?.let { repo ->
            val humanStartChips = initialPlayers.firstOrNull { it.isHuman }?.chips ?: 0L
            if (humanStartChips > 0L) {
                viewModelScope.launch { repo.buyIn(humanStartChips) }
            }
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
                .onFailure { android.util.Log.w("TableVM", "settle async failed", it) }
        }
    }

    /**
     * settle 동기 await — 호출자가 wallet 잔고 갱신을 보장받은 후 다음 액션(예: 로비 nav).
     * settled flag 로 중복 settle 방지 (이후 onCleared 호출되어도 noop).
     */
    suspend fun settleAndCloseAwait() {
        if (settled) return
        settled = true
        val repo = walletRepo ?: return
        val finalChips = _state.value.players.firstOrNull { it.isHuman }?.chips ?: 0L
        runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                repo.settle(finalChips)
            }
        }.onFailure { android.util.Log.w("TableVM", "settle await failed", it) }
    }

    override fun onCleared() {
        super.onCleared()
        // onExit 에서 이미 settle 되지 않은 경우 방어선 — 앱 강제 종료 등.
        settleAndClose()
    }

    // ---------------------------------------------------------------- Actions

    fun onHumanAction(action: Action) {
        val s = _state.value
        val seat = s.toActSeat
        if (seat == null) {
            android.util.Log.w("TableVM", "onHumanAction dropped: toActSeat=null")
            return
        }
        val p = s.players.firstOrNull { it.seat == seat }
        if (p == null) {
            android.util.Log.w("TableVM", "onHumanAction dropped: no player at seat=$seat")
            return
        }
        if (!p.isHuman) {
            android.util.Log.w("TableVM", "onHumanAction dropped: seat=$seat is NPC (isHuman=false)")
            return
        }
        val streetBefore = s.street
        val next = controller.humanAct(action)
        showLastAction(seat, action)
        // M5-B: human 액션 로그 + 핸드 종료 감지.
        logAction(seat = seat, action = action, streetOrdinal = streetBefore.ordinal)
        _state.value = next
        maybeRecordFinishedHand(next)
        persistSnapshot()
        startTicking()
    }

    fun onNextHand() {
        cancelAutoNext()
        _lastActions.value = emptyMap()
        val s = _state.value
        if (s.pendingShowdown != null) {
            controller.ackShowdown()
        }
        val active = controller.state.players.count { it.chips > 0 }
        // 본인이 파산(chips=0) 했으면 NPC 끼리 계속 가는 건 의미 없음 — 즉시 게임 오버.
        val human = controller.state.players.firstOrNull { it.isHuman }
        val humanBust = human != null && human.chips == 0L
        if (active >= 2 && !humanBust) {
            _state.value = controller.nextHand()
            // M5-B: 다음 핸드 초기 상태/seed 새로 캡처 + 액션 로그 초기화.
            resetHistoryBufferFor(controller.state)
            persistSnapshot()
            startTicking()
        } else {
            _state.value = controller.state
            // 게임 오버: 승자 정보 설정.
            val winner = controller.state.players.maxByOrNull { it.chips }
            _gameOver.value = GameOverInfo(
                winnerNickname = winner?.nickname ?: "-",
                winnerSeat = winner?.seat ?: -1,
                isHumanWinner = winner?.isHuman == true,
                finalChips = winner?.chips ?: 0L,
            )
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

    /** 사용자가 "이어하기" 선택. M7-BugFix: 손상된 snapshot 에서도 crash 없이 새 핸드로 폴백. */
    fun onResumeAccept() {
        val snap = resumeRepo?.load() ?: run {
            _resumePrompt.value = null
            startTicking()
            return
        }
        // M7-BugFix: 파일 손상/수동 편집으로 hex 필드가 깨져 있어도 앱이 죽지 않도록 runCatching.
        val rng = runCatching {
            Rng.ofSeeds(
                serverSeed = snap.rngServerSeedHex.hexToBytes(),
                clientSeed = snap.rngClientSeedHex.hexToBytes(),
                nonce = snap.rngNonce,
            )
        }.getOrElse {
            android.util.Log.w("TableVM", "resume snapshot has invalid hex — discarding", it)
            resumeRepo?.clear()
            _resumePrompt.value = null
            startTicking()
            return
        }
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

    // ---------------------------------------------------------------- Action Labels

    private fun showLastAction(seat: Int, action: Action) {
        // HiLo UI: DECLARE_HI/LO/BOTH 액션은 옆에서는 비공개 — Street.DECLARE 단계 동안
        // 다른 좌석의 라벨이 노출되면 비공개 룰(상대 선언 마스킹)을 깨버린다. 본인 좌석 외에는
        // 단순히 "선언" 만 표시. 본인은 상세 라벨 노출.
        val isOpponentDeclare = action.type in DECLARE_TYPES &&
            _state.value.players.firstOrNull { it.seat == seat }?.isHuman != true
        val label = when {
            isOpponentDeclare -> "선언"
            action.type == ActionType.FOLD -> "폴드"
            action.type == ActionType.CHECK -> "체크"
            action.type == ActionType.CALL -> "콜"
            action.type == ActionType.BET -> "벳 ${ChipFormat.format(action.amount)}"
            action.type == ActionType.RAISE -> "레이즈 ${ChipFormat.format(action.amount)}"
            action.type == ActionType.ALL_IN -> "올인"
            action.type == ActionType.DECLARE_HI -> "하이 선언"
            action.type == ActionType.DECLARE_LO -> "로우 선언"
            action.type == ActionType.DECLARE_BOTH -> "양방향 선언"
            else -> action.type.name
        }
        _lastActions.value = _lastActions.value + (seat to label)
        clearActionJob?.cancel()
        clearActionJob = viewModelScope.launch {
            delay(2000L)
            _lastActions.value = emptyMap()
        }
    }

    // ---------------------------------------------------------------- Auto-Next

    /** 쇼다운 후 자동 다음 핸드 카운트다운 시작. */
    private fun startAutoNextCountdown() {
        cancelAutoNext()
        autoNextJob = viewModelScope.launch {
            for (remaining in autoNextDelaySeconds downTo 1) {
                _autoNextCountdown.value = remaining
                delay(1000L)
            }
            _autoNextCountdown.value = null
            onNextHand()
        }
    }

    private fun cancelAutoNext() {
        autoNextJob?.cancel()
        autoNextJob = null
        _autoNextCountdown.value = null
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
                } ?: run {
                    // M7-BugFix: GameController 가 null 반환 (toAct drift / 인간 차례) → 루프 종료.
                    android.util.Log.w("TableVM", "npcActAndLog returned null — tick skipped")
                    return@launch
                }
                showLastAction(result.actorSeat, result.action)
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

    /** 쇼다운 pending 이 막 생겼으면 히스토리 저장 + 자동 다음 핸드 카운트다운 시작. */
    private var lastRecordedHandIndex: Long = -1L
    private fun maybeRecordFinishedHand(state: GameState) {
        if (state.pendingShowdown == null) return
        if (state.handIndex == lastRecordedHandIndex) return
        lastRecordedHandIndex = state.handIndex

        // 자동 다음 핸드 카운트다운 시작.
        startAutoNextCountdown()

        val repo = historyRepo ?: return
        val scope = historyScope ?: return

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
        /** AI 시트 buy-in (사용자 룰: 5만 고정). */
        const val NPC_BUY_IN: Long = 50_000L

        fun createDefault(
            context: Context?,
            mode: GameMode = GameMode.HOLDEM_NL,
            /** 좌석 수 (인간 1 + AI N-1). 2~4 지원. */
            seats: Int = 2,
            humanNickname: String = "나",
            /** 본인 buy-in (=wallet 잔고 전체). 0 이면 default(TABLE_STAKE) 폴백 (테스트/프리뷰). */
            humanBuyIn: Long = 0L,
            /** Phase5-II-B: LLM advisor. */
            llmAdvisor: LlmAdvisor? = null,
            /** M5-B: 핸드 히스토리 저장소 + scope. */
            historyRepo: HandHistoryRepository? = null,
            historyScope: CoroutineScope? = null,
            /** M6-C: 지갑. null 이면 buy-in/settle 스킵 (테스트/프리뷰 호환). */
            walletRepo: WalletRepository? = null,
        ): TableViewModel {
            // 모든 정식 지원 모드: HOLDEM_NL / SEVEN_STUD / SEVEN_STUD_HI_LO.
            val effectiveMode = mode
            val effectiveSeats = seats.coerceIn(2, 4)
            // 7스터드/HiLo 디폴트 베팅 구조: ante 10 / bring-in 25 (NL 베팅, raise cap 3).
            val config = when (effectiveMode) {
                GameMode.SEVEN_STUD, GameMode.SEVEN_STUD_HI_LO -> TableConfig(
                    mode = effectiveMode,
                    seats = effectiveSeats,
                    smallBlind = 0L,
                    bigBlind = 0L,
                    ante = 10L,
                    bringIn = 25L,
                )
                GameMode.HOLDEM_NL -> TableConfig(mode = effectiveMode, seats = effectiveSeats)
            }

            // NPC 페르소나 분배 — engine/decision 의 PersonaPool 사용. PRO 가 첫 슬롯,
            // 나머지는 seed=0 결정적 셔플로 다양화. seat=1 위치는 항상 동일 인물 (사용자 학습용).
            val npcCount = effectiveSeats - 1
            val npcPersonas = if (npcCount > 0) {
                com.infocar.pokermaster.engine.decision.PersonaPool.pickFor(npcCount = npcCount, seed = 0L)
            } else emptyList()
            val npcPersonaIds = npcPersonas.map { it.name }
            val npcNicknames = npcPersonas.map { it.displayName }

            // Buy-in 분기 (사용자 룰):
            //  - 인간: humanBuyIn (wallet 잔고 전체). 0 이면 default(TABLE_STAKE).
            //  - NPC: 5만 고정.
            val effectiveHumanChips =
                if (humanBuyIn > 0L) humanBuyIn else WalletRepository.TABLE_STAKE
            val players = buildList {
                add(
                    PlayerState(
                        seat = 0, nickname = humanNickname, isHuman = true,
                        personaId = null, chips = effectiveHumanChips,
                    )
                )
                for (i in 0 until npcCount) {
                    add(
                        PlayerState(
                            seat = i + 1,
                            nickname = npcNicknames.getOrElse(i) { "AI${i + 1}" },
                            isHuman = false,
                            personaId = npcPersonaIds.getOrElse(i) { "PRO" },
                            chips = NPC_BUY_IN,
                        )
                    )
                }
            }
            // 이어하기 기능 제거 (사용자 요청). resumeRepo=null 이면 init {} 에서
            // resumeRepo?.load() 가 null 이라 즉시 startTicking() 으로 진입, persistSnapshot()
            // 도 노op. ResumePrompt UI 도 항상 null 이라 ResumeDialog 안 뜸.
            val repo: ResumeRepository? = null
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

/** HiLo UI: 좌석 라벨 마스킹용 — DECLARE_* 모음. */
private val DECLARE_TYPES: Set<ActionType> = setOf(
    ActionType.DECLARE_HI,
    ActionType.DECLARE_LO,
    ActionType.DECLARE_BOTH,
)

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
