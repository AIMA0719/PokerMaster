package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.engine.decision.ActionCandidate
import com.infocar.pokermaster.engine.decision.DecisionCore
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona

/**
 * NPC 차례에 결정형 코어(M2)에 게임 상태를 변환해 주고, 반환된 후보를 적법 액션으로 강제 클램프.
 *
 * 핵심 책임:
 *  1. [GameState] → [GameContext] 변환 (시점: 해당 seat 본인 관점)
 *  2. [DecisionCore.decide] 호출
 *  3. 1순위 후보를 [Action] 으로 적법화 (체크 가능 시 fold 대신 check, raise 불가 시 call 강등 등)
 *
 * v1.1 §5.2 LLM 응답 검증은 M4 에서 [AiDriver] 를 감싸는 LLM 레이어가 담당. 여기선 결정형 코어만.
 */
class AiDriver(
    private val decisionCore: DecisionCore = DecisionCore(),
) {

    fun act(state: GameState, seat: Int, persona: Persona?): Action {
        val ctx = buildContext(state, seat)
        val result = decisionCore.decide(ctx, persona)
        val top = result.candidates.firstOrNull() ?: return Action(ActionType.FOLD)
        return legalize(state, seat, top)
    }

    private fun buildContext(state: GameState, seat: Int): GameContext {
        val me = state.players.first { it.seat == seat }
        val opps = state.players.filter { it.seat != seat && !it.folded }
        val effStack = minOf(me.chips, opps.maxOfOrNull { it.chips } ?: me.chips)
        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        return GameContext(
            mode = state.mode,
            seat = seat,
            opponentSeats = opps.map { it.seat },
            hole = me.holeCards,
            upCards = me.upCards,
            community = state.community,
            knownOpponentUpCards = state.players
                .filter { it.seat != seat }
                .associate { it.seat to it.upCards },
            pot = totalPot(state),
            betToCall = toCall,
            minRaise = state.minRaise,
            myStack = me.chips,
            effectiveStack = effStack,
            numActiveOpponents = opps.count { !it.allIn },
        )
    }

    private fun totalPot(state: GameState): Long =
        state.players.sumOf { it.committedThisHand }

    private fun legalize(state: GameState, seat: Int, cand: ActionCandidate): Action {
        val me = state.players.first { it.seat == seat }
        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        val myMaxCommit = me.committedThisStreet + me.chips

        fun callOrCheck(): Action =
            if (toCall > 0L) Action(ActionType.CALL) else Action(ActionType.CHECK)

        return when (cand.action) {
            ActionType.FOLD -> if (toCall == 0L) Action(ActionType.CHECK) else Action(ActionType.FOLD)
            ActionType.CHECK -> if (toCall > 0L) Action(ActionType.CALL) else Action(ActionType.CHECK)
            ActionType.CALL -> callOrCheck()
            ActionType.BET, ActionType.RAISE -> {
                if (!state.reopenAction || me.chips == 0L) return callOrCheck()
                val desired = cand.amount.coerceAtLeast(state.minRaise).coerceAtMost(myMaxCommit)
                when {
                    desired >= myMaxCommit -> Action(ActionType.ALL_IN, myMaxCommit)
                    desired <= state.betToCall -> callOrCheck()
                    else -> Action(ActionType.RAISE, desired)
                }
            }
            ActionType.ALL_IN -> Action(ActionType.ALL_IN, myMaxCommit)
            else -> callOrCheck()
        }
    }

    /** Persona resolver 헬퍼 — PlayerState.personaId(문자열) → [Persona] 매핑. 실패 시 null. */
    companion object {
        fun resolvePersona(player: PlayerState): Persona? =
            player.personaId?.let { id -> runCatching { Persona.valueOf(id) }.getOrNull() }
    }
}
