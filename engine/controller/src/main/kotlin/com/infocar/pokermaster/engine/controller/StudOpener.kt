package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street

/**
 * 7스터드 3rd-street 오프너 휴리스틱.
 *
 * 한국식 7스터드는 시작 3장(2 down + 1 up) 강도로 대부분의 핸드 결정이 갈리는데,
 * Monte Carlo equity 는 3장 표본이 노이즈가 커서 monster/strong 핸드를 약체 평가할 수 있음.
 * 본 안전장치는 두 가지 동작:
 *
 *  - **Rolled-up trips** (3장 모두 같은 rank) → 무조건 RAISE/ALL_IN.
 *  - **Pair / 3-flush / 3-straight** → 브링인 수준 (≤ bringIn × 2) 콜에 한해 폴드 방지선만.
 *    (큰 봉착 액션엔 equity 가 결정 — 너무 공격적이면 AI 가 일관되게 손해)
 *
 * 그 외 시작핸드 → null (DecisionCore 의 equity 파이프라인에 위임).
 */
internal object StudOpener {

    private enum class Tier { RolledUp, Pair, ThreeFlush, ThreeStraight, None }

    fun overrideOnThirdStreet(state: GameState, seat: Int): Action? {
        if (state.mode != GameMode.SEVEN_STUD && state.mode != GameMode.SEVEN_STUD_HI_LO) return null
        if (state.street != Street.THIRD) return null
        val me = state.players.firstOrNull { it.seat == seat } ?: return null
        if (!me.active) return null
        val opening = me.holeCards + me.upCards
        if (opening.size != 3) return null

        val tier = classify(opening)
        return when (tier) {
            Tier.RolledUp -> raiseAction(state, me)
            Tier.Pair, Tier.ThreeFlush, Tier.ThreeStraight -> antiFold(state, me)
            Tier.None -> null
        }
    }

    private fun classify(opening: List<Card>): Tier {
        val ranks = opening.map { it.rank.value }
        val suits = opening.map { it.suit }
        val isRolledUp = ranks.distinct().size == 1
        if (isRolledUp) return Tier.RolledUp
        // Pair: 두 카드가 같은 rank (세 번째는 다름).
        val rankGroups = ranks.groupBy { it }.values
        val hasPair = rankGroups.any { it.size == 2 } && ranks.distinct().size == 2
        if (hasPair) return Tier.Pair
        // 3-flush: 세 장 같은 무늬.
        val isThreeFlush = suits.distinct().size == 1
        if (isThreeFlush) return Tier.ThreeFlush
        // 3-straight: 연속 3장 또는 wheel(A-2-3 = 14,2,3).
        if (isThreeStraight(ranks)) return Tier.ThreeStraight
        return Tier.None
    }

    private fun isThreeStraight(ranks: List<Int>): Boolean {
        if (ranks.toSet().size != 3) return false
        val asc = ranks.sorted()
        // 일반 연속 (예: 6,7,8 또는 J,Q,K).
        if (asc[2] - asc[0] == 2) return true
        // wheel-like: A-2-3 → asc = [2,3,14].
        if (asc == listOf(2, 3, 14)) return true
        // wheel-like: A-2-4 / A-3-4 등은 connected but not strict 3-straight; 제외.
        return false
    }

    /** Rolled-up: RAISE 또는 ALL_IN (콜만으로 all-in 인 케이스). */
    private fun raiseAction(state: GameState, me: PlayerState): Action {
        if (me.chips == 0L) return Action(ActionType.CHECK)
        val maxCommit = me.committedThisStreet + me.chips
        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        if (toCall >= me.chips) return Action(ActionType.ALL_IN, maxCommit)
        val target = state.minRaise
            .coerceAtLeast(state.betToCall + state.config.bringIn.coerceAtLeast(1L))
            .coerceAtMost(maxCommit)
        if (target <= state.betToCall) {
            // RAISE 타깃이 부적합 — 적어도 콜은 (만약 콜할게 있으면).
            return if (toCall > 0L) Action(ActionType.CALL) else Action(ActionType.CHECK)
        }
        return Action(ActionType.RAISE, target)
    }

    /**
     * 폴드 방지선: betToCall - committedThisStreet 가 bringIn × 2 이하면 CALL.
     * 그 이상의 큰 봉착 액션엔 equity 결정에 위임 (null 반환).
     */
    private fun antiFold(state: GameState, me: PlayerState): Action? {
        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        if (toCall == 0L) return null  // 봉착 없음 — equity 가 raise/check 결정
        val cheapThreshold = state.config.bringIn.coerceAtLeast(1L) * 2L
        if (toCall <= cheapThreshold) return Action(ActionType.CALL)
        return null  // 큰 raise 봉착 — equity 가 fold 여부 결정
    }
}
