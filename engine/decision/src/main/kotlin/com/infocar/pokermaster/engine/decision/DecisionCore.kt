package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode

/**
 * 결정형 코어 — equity + pot odds + bet sizing → 액션 후보 EV 산출.
 *
 * 출력은 LLM 페르소나 레이어(M4) 가 받아 최종 액션을 선택하거나, LLM 미탑재 시 본 코어가 단독 사용.
 *
 * 페르소나가 주어지면 [PersonaBias] 로 편향 후 정렬.
 */
class DecisionCore(
    private val equityCalculator: EquityCalculator = EquityCalculator(),
    private val iterations: Int = 5_000,
) {

    fun decide(ctx: GameContext, persona: Persona? = null): DecisionResult {
        // 1) equity
        val equity = equityCalculator.equity(ctx, iterations)

        // 2) pot odds / SPR
        val potOdds = PostflopHeuristic.potOdds(ctx.pot, ctx.betToCall)
        val spr = PostflopHeuristic.spr(ctx.effectiveStack, ctx.pot)

        // 3) 후보 액션
        val candidates = mutableListOf<ActionCandidate>()

        if (ctx.betToCall == 0L) {
            // 체크 가능
            candidates += ActionCandidate(
                action = ActionType.CHECK,
                amount = 0L,
                ev = 0.0,
                equity = equity,
                potOdds = potOdds,
            )
        } else {
            // fold (sunk cost 무시 → ev = 0)
            candidates += ActionCandidate(
                action = ActionType.FOLD,
                amount = 0L,
                ev = 0.0,
                equity = equity,
                potOdds = potOdds,
            )
            // call (스택 부족하면 콜 amount 가 myStack 으로 클램프됨)
            val callAmount = ctx.betToCall.coerceAtMost(ctx.myStack)
            candidates += ActionCandidate(
                action = ActionType.CALL,
                amount = callAmount,
                ev = PostflopHeuristic.callEv(equity, ctx.pot, callAmount),
                equity = equity,
                potOdds = potOdds,
            )
        }

        // 4) bet/raise/all-in 후보 — 모드별 (홀덤 NL 만 다양한 사이즈, 7스터드는 단순 raise)
        if (ctx.mode == GameMode.HOLDEM_NL) {
            val sizings = PostflopHeuristic.betSizingCandidates(
                pot = ctx.pot,
                minRaise = ctx.minRaise.coerceAtLeast(1L),
                effectiveStack = ctx.myStack,
            )
            for ((type, amt) in sizings) {
                candidates += ActionCandidate(
                    action = type,
                    amount = amt,
                    ev = PostflopHeuristic.betEv(
                        equity = equity,
                        pot = ctx.pot,
                        amount = amt,
                        opponents = ctx.numActiveOpponents,
                    ),
                    equity = equity,
                )
            }
        } else {
            // 7스터드: fixed-limit 가정 — minRaise (또는 1 small bet) 한 단위만 raise 후보로
            val raiseAmount = ctx.minRaise.coerceAtMost(ctx.myStack)
            if (raiseAmount > 0 && raiseAmount > ctx.betToCall) {
                candidates += ActionCandidate(
                    action = ActionType.RAISE,
                    amount = raiseAmount,
                    ev = PostflopHeuristic.betEv(
                        equity = equity,
                        pot = ctx.pot,
                        amount = raiseAmount,
                        opponents = ctx.numActiveOpponents,
                    ),
                    equity = equity,
                )
            }
            if (ctx.myStack > 0 && ctx.myStack > ctx.betToCall) {
                candidates += ActionCandidate(
                    action = ActionType.ALL_IN,
                    amount = ctx.myStack,
                    ev = PostflopHeuristic.betEv(
                        equity = equity,
                        pot = ctx.pot,
                        amount = ctx.myStack,
                        opponents = ctx.numActiveOpponents,
                    ),
                    equity = equity,
                )
            }
        }

        // 5) 페르소나 편향 (선택) — pot-relative offset
        val final = if (persona != null) PersonaBias.apply(persona, candidates, ctx.pot) else candidates

        // 6) ev 내림차순 정렬 (1순위가 추천)
        return DecisionResult(
            candidates = final.sortedByDescending { it.ev },
            equity = equity,
            potOdds = potOdds,
            effectiveStack = ctx.effectiveStack,
            spr = spr,
        )
    }
}
