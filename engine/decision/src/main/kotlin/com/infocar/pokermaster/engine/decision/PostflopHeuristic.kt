package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.ActionType

/**
 * 포스트플롭 EV 휴리스틱.
 *
 *  단순화 모델 (v1):
 *   - call EV  = equity * (pot + betToCall) - (1 - equity) * betToCall
 *               = equity * pot + (2 * equity - 1) * betToCall
 *   - bet EV (반응 fold% f) = f * pot + (1 - f) * (2 * equity - 1) * (pot + amount)
 *     상대 fold 확률은 베팅 사이즈/equity 함수로 단순 추정
 *   - check EV = 0 (현 상황 유지)
 *   - fold EV = 0 (커밋한 칩 손실은 sunk cost, 의사결정에서 무시)
 */
object PostflopHeuristic {

    /** call EV. betToCall == 0 이면 의미 없음 — 호출자가 check 로 처리. */
    fun callEv(equity: Double, pot: Long, betToCall: Long): Double {
        val potD = pot.toDouble()
        val callD = betToCall.toDouble()
        return equity * potD + (2 * equity - 1) * callD
    }

    /**
     * bet/raise EV 추정. 상대 fold 확률 [foldProb] 가 주어지면 그것을 사용,
     * 없으면 베팅 사이즈에 대한 단순 fold 함수 사용.
     */
    fun betEv(
        equity: Double,
        pot: Long,
        amount: Long,
        opponents: Int,
        foldProb: Double = estimatedFoldProb(amount, pot, opponents),
    ): Double {
        val potD = pot.toDouble()
        val amtD = amount.toDouble()
        val foldEv = foldProb * potD
        val seenEv = (1 - foldProb) * ((2 * equity - 1) * (potD + amtD))
        return foldEv + seenEv
    }

    /**
     * 베팅 사이즈 → 단일 상대 fold 확률 단순 모델.
     *  - 1/3 pot ~ 25% / 1/2 pot ~ 35% / 2/3 pot ~ 42% / pot ~ 50% / 2x pot ~ 60%
     * 다중 상대일 경우 fold 확률은 (single)^opp 로 누적.
     */
    fun estimatedFoldProb(amount: Long, pot: Long, opponents: Int): Double {
        if (pot <= 0 || amount <= 0) return 0.0
        val ratio = amount.toDouble() / pot.toDouble()
        val singleFold = when {
            ratio <= 0.34 -> 0.25
            ratio <= 0.51 -> 0.35
            ratio <= 0.68 -> 0.42
            ratio <= 1.05 -> 0.50
            ratio <= 2.05 -> 0.60
            else -> 0.70
        }
        // 다중 상대: 모두 fold 해야 베터 승. p^N
        return Math.pow(singleFold, opponents.coerceAtLeast(1).toDouble())
    }

    /** pot odds = call 비용 / (pot + call 비용). 0 이면 콜 비용 무료. */
    fun potOdds(pot: Long, betToCall: Long): Double {
        if (betToCall <= 0) return 0.0
        return betToCall.toDouble() / (pot + betToCall).toDouble()
    }

    /** SPR = effectiveStack / pot. pot 0 이면 SPR_CAP (=999.0). */
    fun spr(effectiveStack: Long, pot: Long): Double {
        if (pot <= 0) return DecisionResult.SPR_CAP
        val raw = effectiveStack.toDouble() / pot.toDouble()
        return raw.coerceAtMost(DecisionResult.SPR_CAP)
    }

    /**
     * 표준 베팅 사이즈 후보 5종 (1/3, 1/2, 2/3, pot, all-in).
     * minRaise 미만 또는 effectiveStack 초과는 제외 / all-in 으로 클램프.
     */
    fun betSizingCandidates(
        pot: Long,
        minRaise: Long,
        effectiveStack: Long,
    ): List<Pair<ActionType, Long>> {
        if (effectiveStack <= 0) return emptyList()
        val raw = listOf(
            pot / 3,
            pot / 2,
            pot * 2 / 3,
            pot,
        ).filter { it > 0 }

        val out = mutableListOf<Pair<ActionType, Long>>()
        for (size in raw) {
            val clamped = size.coerceAtLeast(minRaise).coerceAtMost(effectiveStack)
            if (clamped < minRaise && effectiveStack >= minRaise) continue
            // all-in 미만 + minRaise 충족 시만 RAISE/BET
            if (clamped < effectiveStack) {
                out += ActionType.RAISE to clamped
            }
        }
        // all-in 항상 후보
        out += ActionType.ALL_IN to effectiveStack
        return out.distinctBy { it.second }
    }
}
