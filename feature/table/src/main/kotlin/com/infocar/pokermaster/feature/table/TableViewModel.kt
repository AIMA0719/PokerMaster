package com.infocar.pokermaster.feature.table

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.controller.GameController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 테이블 화면 ViewModel. [GameController] 를 감싸 [StateFlow] 로 UI 에 노출.
 *
 * NPC 턴 처리:
 *  - state 변경 후 toAct 가 NPC 이고 pending 없으면 자동으로 [Dispatchers.Default] 에서 계산 → [Dispatchers.Main] 에서 state 반영.
 *  - 사람 차례 동안 백그라운드 선계산은 M4 에서 [PrecomputeQueue] 로 보강 (v1.1 §5.1).
 *
 * M3 MVP: 2인 헤즈업 홀덤. 로비에서 모드 전달 → NPC 기본 persona = PRO.
 */
class TableViewModel private constructor(
    private val controller: GameController,
    /** NPC 턴 간 UI 연출용 최소 대기(ms). 너무 빠르면 사용자가 NPC 액션을 놓침. */
    private val npcDelayMs: Long = 700L,
) : ViewModel() {

    private val _state = MutableStateFlow(controller.state)
    val state: StateFlow<GameState> = _state.asStateFlow()

    init {
        tickNpc()
    }

    /** 사람 플레이어 액션. */
    fun onHumanAction(action: Action) {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        val next = controller.humanAct(action)
        _state.value = next
        tickNpc()
    }

    /** 쇼다운 애니 후 UI 가 '다음 핸드'. */
    fun onNextHand() {
        if (_state.value.pendingShowdown != null) {
            controller.ackShowdown()
        }
        // alive 2명 미만이면 더 진행 불가 → 리셋은 M3 외 (파산 리셋 v1.1 §1.2.L).
        val active = controller.state.players.count { it.chips > 0 }
        if (active >= 2) {
            _state.value = controller.nextHand()
            tickNpc()
        } else {
            _state.value = controller.state
        }
    }

    /** 핸드 항복 (인게임 메뉴). 현 스트릿에 즉시 폴드. */
    fun onSurrender() {
        val s = _state.value
        val seat = s.toActSeat ?: return
        val p = s.players.firstOrNull { it.seat == seat } ?: return
        if (!p.isHuman) return
        onHumanAction(Action(ActionType.FOLD))
    }

    private fun tickNpc() {
        viewModelScope.launch {
            while (true) {
                val s = _state.value
                val seat = s.toActSeat ?: return@launch
                if (s.pendingShowdown != null) return@launch
                val p = s.players.firstOrNull { it.seat == seat } ?: return@launch
                if (p.isHuman) return@launch
                // NPC 차례
                delay(npcDelayMs)
                val next = withContext(Dispatchers.Default) { controller.npcAct() }
                _state.value = next
            }
        }
    }

    companion object {
        /**
         * M3 MVP 기본 세팅: 2인 헤즈업 홀덤 (SB 25 / BB 50, 10k chips).
         * 다른 모드/좌석 수는 추후 factory 에서 분기.
         */
        fun createDefault(
            mode: GameMode = GameMode.HOLDEM_NL,
            humanNickname: String = "나",
            npcPersonaId: String = "PRO",
            startingChips: Long = 10_000L,
        ): TableViewModel {
            require(mode == GameMode.HOLDEM_NL) {
                "M3 MVP supports HOLDEM_NL only (mode=$mode)"
            }
            val config = TableConfig(mode = mode, seats = 2)
            val players = listOf(
                PlayerState(
                    seat = 0,
                    nickname = humanNickname,
                    isHuman = true,
                    personaId = null,
                    chips = startingChips,
                ),
                PlayerState(
                    seat = 1,
                    nickname = "프로",
                    isHuman = false,
                    personaId = npcPersonaId,
                    chips = startingChips,
                ),
            )
            val controller = GameController(config = config, initialPlayers = players)
            return TableViewModel(controller)
        }
    }
}
