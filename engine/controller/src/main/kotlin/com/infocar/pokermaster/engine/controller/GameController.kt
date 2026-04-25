package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.rules.Rng

/**
 * 게임 컨트롤러. Reducer + AiDriver + Rng 를 묶어 UI 가 단일 진입점으로 쓸 수 있도록.
 *
 *  - 내부 상태는 var 로 보관 (pure reducer 가 새 [GameState] 반환).
 *  - coroutines 의존 없음. feature 레이어(ViewModel)가 [StateFlow] 로 감싸거나 일회성 호출.
 *  - thread-safety: 호출자가 직렬화 보장 (ViewModel actor/Mutex 등).
 *  - Rng 는 핸드마다 재생성 ([rngSupplier] 주입으로 테스트 가능).
 */
class GameController(
    private val config: TableConfig,
    initialPlayers: List<PlayerState>,
    private val aiDriver: AiDriver = AiDriver(),
    private val rngSupplier: (Long) -> Rng = { nonce -> Rng.create(nonce) },
    /**
     * Resume 복원 경로 — [state] 와 [rng] 를 주입받아 기존 핸드 중간 상태에서 재개.
     *  `null` 이면 새 핸드를 [HoldemReducer.startHand] 로 시작.
     */
    resumeFrom: ResumeSeed? = null,
) {
    private var currentRng: Rng
    private var _state: GameState

    init {
        // M7-BugFix: 복원 상태의 toActSeat 가 이미 all-in/folded seat 을 가리키는 drift 를
        // 감지하면 resume 무시하고 새 핸드로 폴백 (UI 는 snapshot 이 깨졌다는 사실을 직접 알 수 없으므로
        // 조용히 새 핸드로 복구).
        val validResume = resumeFrom?.takeIf { isResumeStateValid(it.state) }
        if (validResume != null) {
            currentRng = validResume.rng
            _state = validResume.state
        } else {
            currentRng = rngSupplier(INITIAL_HAND_INDEX)
            _state = startHand(
                players = initialPlayers,
                prevBtnSeat = null,
                rng = currentRng,
                handIndex = INITIAL_HAND_INDEX,
                startingVersion = GameState.INITIAL_VERSION,
            )
        }
    }

    /** 모드별 reducer 디스패치. SEVEN_STUD_HI_LO 는 v1.1 후속. */
    private fun startHand(
        players: List<PlayerState>,
        prevBtnSeat: Int?,
        rng: Rng,
        handIndex: Long,
        startingVersion: Long,
    ): GameState = when (config.mode) {
        GameMode.HOLDEM_NL -> HoldemReducer.startHand(config, players, prevBtnSeat, rng, handIndex, startingVersion)
        GameMode.SEVEN_STUD, GameMode.SEVEN_STUD_HI_LO ->
            StudReducer.startHand(config, players, prevBtnSeat, rng, handIndex, startingVersion)
    }

    private fun reduceAct(state: GameState, seat: Int, action: Action, rng: Rng): GameState =
        when (state.mode) {
            GameMode.HOLDEM_NL -> HoldemReducer.act(state, seat, action, rng)
            GameMode.SEVEN_STUD, GameMode.SEVEN_STUD_HI_LO -> StudReducer.act(state, seat, action, rng)
        }

    private fun reduceAckShowdown(state: GameState): GameState = when (state.mode) {
        GameMode.HOLDEM_NL -> HoldemReducer.ackShowdown(state)
        GameMode.SEVEN_STUD, GameMode.SEVEN_STUD_HI_LO -> StudReducer.ackShowdown(state)
    }

    /** toActSeat 이 존재하면 그 seat 이 active 인지 검증. null 이면 쇼다운 대기 — OK. */
    private fun isResumeStateValid(s: GameState): Boolean {
        val toAct = s.toActSeat ?: return s.pendingShowdown != null
        val p = s.players.firstOrNull { it.seat == toAct } ?: return false
        return p.active
    }

    val state: GameState get() = _state

    /** 현재 핸드의 Rng (snapshot 시 seed 기록용). */
    val rng: Rng get() = currentRng

    /**
     * 사람 플레이어의 액션 적용. M7-BugFix: toAct null / 불일치 상태에선 noop (UI race 방어).
     * 기존엔 `error("no current toActSeat")` 로 프로세스 크래시.
     */
    fun humanAct(action: Action): GameState {
        val seat = _state.toActSeat ?: return _state
        val p = _state.players.firstOrNull { it.seat == seat } ?: return _state
        if (!p.isHuman) return _state
        _state = reduceAct(_state, seat, action, currentRng)
        return _state
    }

    /**
     * NPC 차례 액션 계산+적용. 호출자는 반드시 비-UI 스레드 (Dispatchers.Default 권장) 에서 호출.
     * Monte Carlo equity 가 5~30ms 걸릴 수 있음.
     */
    fun npcAct(): GameState {
        val seat = _state.toActSeat ?: return _state
        val p = _state.players.firstOrNull { it.seat == seat } ?: return _state
        if (p.isHuman) return _state
        val persona = AiDriver.resolvePersona(p)
        val action = aiDriver.act(_state, seat, persona)
        _state = reduceAct(_state, seat, action, currentRng)
        return _state
    }

    /**
     * Phase5-II-A: LLM advisor 가 있으면 LLM 제안을 먼저 시도하고 실패 시 [aiDriver.act] 폴백.
     *
     * advisor 가 null 이면 [npcAct] 와 동일하게 동작 (단, suspend 시그니처). TableVM 이 withContext
     * 없이 직접 호출할 수 있다 (내부 Monte Carlo 는 여전히 blocking CPU 작업이므로 호출자가 적절한
     * dispatcher 에서 launch 해야 한다 — feature:table 의 startTicking 루프가 담당).
     */
    suspend fun npcActWithLlm(
        advisor: LlmAdvisor?,
        timeoutMs: Long = AiDriver.DEFAULT_LLM_TIMEOUT_MS,
    ): GameState = npcActAndLog(advisor, timeoutMs)?.state ?: _state

    /**
     * M5-B: NPC 액션을 적용하면서 실제로 선택된 [Action] 과 적용 직전 street 도 함께 반환.
     * 핸드 히스토리 로그 수집에 필요 (TableViewModel 이 ActionLogEntry 를 쌓을 수 있도록).
     */
    suspend fun npcActAndLog(
        advisor: LlmAdvisor?,
        timeoutMs: Long = AiDriver.DEFAULT_LLM_TIMEOUT_MS,
    ): NpcActResult? {
        // M7-BugFix: toAct null / 인간 차례 / 비-active seat → null 반환해 호출자 loop 조기 탈출.
        val seat = _state.toActSeat ?: return null
        val p = _state.players.firstOrNull { it.seat == seat } ?: return null
        if (p.isHuman || !p.active) return null
        val persona = AiDriver.resolvePersona(p)
        val streetBefore = _state.street
        val action = aiDriver.actWithLlm(_state, seat, persona, advisor, timeoutMs)
        _state = reduceAct(_state, seat, action, currentRng)
        return NpcActResult(state = _state, actorSeat = seat, action = action, streetBefore = streetBefore)
    }

    /** 쇼다운 UI 애니메이션 완료 후 호출 — pending 해제. pending 이 없으면 noop. */
    fun ackShowdown(): GameState {
        if (_state.pendingShowdown == null) return _state
        _state = reduceAckShowdown(_state)
        return _state
    }

    /** 다음 핸드 준비. 대부분의 경우 [ackShowdown] 직후 호출. */
    fun nextHand(): GameState {
        val s = _state
        val next = s.handIndex + 1
        currentRng = rngSupplier(next)
        _state = startHand(
            players = s.players,
            prevBtnSeat = s.btnSeat,
            rng = currentRng,
            handIndex = next,
            startingVersion = s.stateVersion,
        )
        return _state
    }

    /** 테스트/디버그용 — 외부에서 상태 강제 주입. */
    internal fun debugSetState(s: GameState) {
        _state = s
    }

    companion object {
        private const val INITIAL_HAND_INDEX: Long = 1L
    }
}

/** Resume 생성자 주입 시드. */
data class ResumeSeed(
    val state: GameState,
    val rng: Rng,
)

/**
 * M5-B: NPC 액션 적용 결과. [action] 은 실제로 reducer 에 투입된 Action ([state] 적용 후),
 * [streetBefore] 는 액션 직전 street (로그 정리용).
 */
data class NpcActResult(
    val state: GameState,
    val actorSeat: Int,
    val action: Action,
    val streetBefore: Street,
)
