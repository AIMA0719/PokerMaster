package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.DeclareDirection
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.standardDeck
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.decision.ActionCandidate
import com.infocar.pokermaster.engine.decision.DecisionCore
import com.infocar.pokermaster.engine.decision.EquityCalculator
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
        // 한국식 Hi-Lo Declare: SEVENTH 베팅 종료 후 DECLARE 단계 — 베팅 후보 산출 우회.
        declareActionOrNull(state, seat)?.let { return it }
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
        // Declare 단계는 LLM 우회 — 결정론 + 즉시 종료 (선언은 EV 단순 비교).
        declareActionOrNull(state, seat)?.let { return it }
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

    /**
     * 한국식 Hi-Lo Declare 단계 자동 선언.
     *
     * SEVEN_STUD_HI_LO + Street.DECLARE 일 때만 발동. 선택 알고리즘:
     *  1. [EquityCalculator.declareEquity] 로 hi/lo/both 각각 EV 계산.
     *  2. 기대 팟 점유율: HI=hi*0.5, LO=lo*0.5, BOTH=both*1.0 (strict scoop).
     *  3. 단, BOTH 는 strict 룰로 실패 시 0 → 분산이 큼. 안전장치:
     *     [BOTH_DECLARE_THRESHOLD] (0.45) 이상일 때만 BOTH 후보로 인정.
     *     0.45 근거: hi 약 90% + lo 약 50% (양방 동시충족 strict) 시나리오의 해석적 추정.
     *     50/50 단방향 + ~25% scoop 같은 모호한 케이스에서 분산 ↑ 위험을 회피.
     *  4. 동률 시 HI > LO > BOTH 순서로 단방향 선호 (UI 친화 + 분산 ↓).
     *
     * non-Hi-Lo / 다른 street / 본인이 이미 폴드 / DECLARE 가 아닌 좌석이면 null.
     */
    private fun declareActionOrNull(state: GameState, seat: Int): Action? {
        if (state.mode != GameMode.SEVEN_STUD_HI_LO) return null
        if (state.street != Street.DECLARE) return null
        if (state.toActSeat != seat) return null
        val me = state.players.firstOrNull { it.seat == seat } ?: return null
        if (me.folded) return null
        // 이미 선언했으면 (재진입 방지) null — reducer 가 액션을 다시 요구하면 그대로 흐른다.
        if (me.declaration != null) return null

        val myCards: List<Card> = me.holeCards + me.upCards
        if (myCards.isEmpty()) return null

        val opponents: List<List<Card>> = state.players
            .filter { it.seat != seat && !it.folded }
            .map { it.upCards }
        if (opponents.isEmpty()) {
            // 상대 전원 폴드 — 어떤 선언이든 무관하지만 분쟁 회피로 BOTH 안정값.
            return Action(ActionType.DECLARE_BOTH)
        }

        val known = (myCards + opponents.flatten()).toSet()
        val deckRemaining = standardDeck() - known
        val de = EquityCalculator(seed = null).declareEquity(
            myCards = myCards,
            opponents = opponents,
            deckRemaining = deckRemaining,
            iterations = DECLARE_ITERATIONS,
        )

        val hiEv = de.hiEquity * 0.5
        val loEv = de.loEquity * 0.5
        val bothEv = de.bothEquity * 1.0
        val bothEligible = de.bothEquity >= BOTH_DECLARE_THRESHOLD

        // 1순위 후보 선택: BOTH 는 임계값 충족 시에만 경쟁.
        val best = listOfNotNull(
            DeclareDirection.HI to hiEv,
            DeclareDirection.LO to loEv,
            if (bothEligible) DeclareDirection.BOTH to bothEv else null,
        ).maxByOrNull { it.second }!!.first

        val type = when (best) {
            DeclareDirection.HI -> ActionType.DECLARE_HI
            DeclareDirection.LO -> ActionType.DECLARE_LO
            DeclareDirection.BOTH -> ActionType.DECLARE_BOTH
        }
        return Action(type, 0L)
    }

    /** Persona resolver 헬퍼 — PlayerState.personaId(문자열) → [Persona] 매핑. 실패 시 null. */
    companion object {
        /** LLM 추론 timeout — 포커 UX 상 500ms 를 넘기면 NPC 턴이 지연된 느낌. */
        const val DEFAULT_LLM_TIMEOUT_MS: Long = 500L

        /**
         * BOTH 선언 임계값. bothEquity 가 이 값 미만이면 BOTH 후보를 배제하고 단방향 선언만 고려.
         *
         * 근거:
         *  - STRICT BOTH 는 한 방향이라도 동률/패배 시 0 (전부 잃음).
         *  - hiEq=loEq=0.5, bothEq=0.25 같은 케이스 EV 비교: HI=0.25, LO=0.25, BOTH=0.25. 동률이지만
         *    BOTH 의 분산은 √(0.25·0.75) ≈ 0.43 으로 단방향 √(0.25·0.75) ≈ 0.43 동급이나,
         *    실제 한국식 게임에선 "둘 다 잃을 위험" 의 심리적 cost 가 큼 → 보수적 임계값 0.45.
         *  - bothEq ≥ 0.45 면 hi/lo 양방 모두 평균 70% 이상 우위인 경우가 대부분 (대략 √0.45 ≈ 0.67).
         *  - 임계값 미달 시 단방향이 항상 안전한 양의 EV 를 보장.
         */
        const val BOTH_DECLARE_THRESHOLD: Double = 0.45

        /** Declare 시 Monte Carlo iteration 수. 한 번만 평가 — 정확도 vs latency 트레이드오프. */
        const val DECLARE_ITERATIONS: Int = 1_000

        fun resolvePersona(player: PlayerState): Persona? =
            player.personaId?.let { id -> runCatching { Persona.valueOf(id) }.getOrNull() }
    }
}
