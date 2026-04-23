package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.TableConfig
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
) {
    private var currentRng: Rng = rngSupplier(INITIAL_HAND_INDEX)

    private var _state: GameState = HoldemReducer.startHand(
        config = config,
        players = initialPlayers,
        prevBtnSeat = null,
        rng = currentRng,
        handIndex = INITIAL_HAND_INDEX,
        startingVersion = GameState.INITIAL_VERSION,
    )

    val state: GameState get() = _state

    /** 사람 플레이어의 액션 적용. toAct 가 인간이 아니면 실패. */
    fun humanAct(action: Action): GameState {
        val seat = _state.toActSeat ?: error("no current toActSeat")
        val p = _state.players.first { it.seat == seat }
        require(p.isHuman) { "current seat $seat is not human" }
        _state = HoldemReducer.act(_state, seat, action, currentRng)
        return _state
    }

    /**
     * NPC 차례 액션 계산+적용. 호출자는 반드시 비-UI 스레드 (Dispatchers.Default 권장) 에서 호출.
     * Monte Carlo equity 가 5~30ms 걸릴 수 있음.
     */
    fun npcAct(): GameState {
        val seat = _state.toActSeat ?: error("no current toActSeat")
        val p = _state.players.first { it.seat == seat }
        require(!p.isHuman) { "current seat $seat is human — call humanAct" }
        val persona = AiDriver.resolvePersona(p)
        val action = aiDriver.act(_state, seat, persona)
        _state = HoldemReducer.act(_state, seat, action, currentRng)
        return _state
    }

    /** 쇼다운 UI 애니메이션 완료 후 호출 — pending 해제. */
    fun ackShowdown(): GameState {
        _state = HoldemReducer.ackShowdown(_state)
        return _state
    }

    /** 다음 핸드 준비. 대부분의 경우 [ackShowdown] 직후 호출. */
    fun nextHand(): GameState {
        val s = _state
        val next = s.handIndex + 1
        currentRng = rngSupplier(next)
        _state = HoldemReducer.startHand(
            config = config,
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
