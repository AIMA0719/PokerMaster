package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.decision.ActionCandidate
import com.infocar.pokermaster.engine.decision.DecisionCore
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona
import kotlinx.coroutines.withTimeoutOrNull

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
        // 7스터드 3rd street monster (rolled-up) 안전장치. equity 노이즈로 약하게 평가될 수 있는 패턴.
        StudOpener.overrideOnThirdStreet(state, seat)?.let { return it }
        val ctx = buildContext(state, seat)
        val result = decisionCore.decide(ctx, persona)
        val top = result.candidates.firstOrNull() ?: return Action(ActionType.FOLD)
        return legalize(state, seat, top)
    }

    /**
     * Phase5-II-A: LLM advisor 를 선택적으로 적용하는 suspend 버전.
     *
     * 알고리즘:
     *  1. DecisionCore 로 기본 EV 후보군 계산 (LLM 실패 대비 베이스).
     *  2. [advisor] 가 non-null 이면 [withTimeoutOrNull] 로 제안 요청.
     *  3. 반환된 [com.infocar.pokermaster.engine.controller.llm.LlmDecision] 이 유효한 [ActionType]
     *     을 담고 있으면 그것을 [ActionCandidate] 로 변환해 [legalize] 에 투입.
     *  4. timeout / null / schema miss 는 모두 베이스 (candidates.first()) 로 폴백.
     *
     * 설계서 §5 의 LLM→Persona→MonteCarlo 폴백 5단계 중 1~2단계 해당. 후속 단계는 Phase5-II-B 의
     * TableVM / GameController 통합에서 persona heuristic 이 이미 DecisionCore 내부에 포함.
     */
    suspend fun actWithLlm(
        state: GameState,
        seat: Int,
        persona: Persona?,
        advisor: LlmAdvisor?,
        timeoutMs: Long = DEFAULT_LLM_TIMEOUT_MS,
    ): Action {
        // 7스터드 3rd street monster 우선 처리 — LLM 컨설트 비용/지연 회피.
        StudOpener.overrideOnThirdStreet(state, seat)?.let { return it }
        val ctx = buildContext(state, seat)
        val result = decisionCore.decide(ctx, persona)

        val llmCandidate: ActionCandidate? = advisor?.let { adv ->
            val decision = runCatching {
                withTimeoutOrNull(timeoutMs) { adv.suggest(ctx, result, persona) }
            }.getOrNull()
            decision?.actionTypeOrNull()?.let { at ->
                ActionCandidate(
                    action = at,
                    amount = decision.amount,
                    ev = 0.0,  // LLM 은 EV 반환 안 함 — legalize 단계에서 clamp.
                )
            }
        }

        val chosen = llmCandidate
            ?: result.candidates.firstOrNull()
            ?: return Action(ActionType.FOLD)
        return legalize(state, seat, chosen)
    }

    private fun buildContext(state: GameState, seat: Int): GameContext {
        val me = state.players.first { it.seat == seat }
        val opps = state.players.filter { it.seat != seat && !it.folded }
        // M7-BugFix: effStack 계산은 "실제 베팅 가능한 상대의 스택" 기반. all-in 상대(chips=0)를
        // 포함하면 effStack=0 으로 바뀌어 betSizingCandidates 가 비어 부적절한 fold 유도됨.
        val liveOpps = opps.filter { !it.allIn }
        val effStack = minOf(
            me.chips,
            liveOpps.maxOfOrNull { it.chips } ?: me.chips,
        )
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
            // M7-BugFix: equity 계산엔 all-in 상대도 포함 (쇼다운까지 합류).
            numActiveOpponents = opps.size,
            // fold equity 계산엔 fold 가능한 상대만 (all-in 은 절대 fold 못 함).
            numFoldableOpponents = liveOpps.size,
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
                val mayRaise = state.reopenAction || !me.actedThisStreet
                if (!mayRaise || me.chips == 0L) return callOrCheck()
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
        /** LLM 추론 timeout — 포커 UX 상 500ms 를 넘기면 NPC 턴이 지연된 느낌. */
        const val DEFAULT_LLM_TIMEOUT_MS: Long = 500L

        fun resolvePersona(player: PlayerState): Persona? =
            player.personaId?.let { id -> runCatching { Persona.valueOf(id) }.getOrNull() }
    }
}
