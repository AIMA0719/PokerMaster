package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import org.junit.jupiter.api.Test

class PostflopHeuristicTest {

    @Test fun pot_odds_zero_when_no_call_cost() {
        assertThat(PostflopHeuristic.potOdds(pot = 1000L, betToCall = 0L)).isEqualTo(0.0)
    }

    @Test fun pot_odds_third_when_call_is_quarter_of_total() {
        // call 100, pot 300 → 100/(300+100)=0.25
        assertThat(PostflopHeuristic.potOdds(pot = 300L, betToCall = 100L)).isWithin(1e-9).of(0.25)
    }

    @Test fun spr_capped_when_no_pot() {
        // pot=0 또는 effectiveStack/pot > 999 시 SPR_CAP 으로 클램프 (JSON 안전성)
        assertThat(PostflopHeuristic.spr(effectiveStack = 5000L, pot = 0L))
            .isEqualTo(DecisionResult.SPR_CAP)
    }

    @Test fun spr_basic() {
        assertThat(PostflopHeuristic.spr(effectiveStack = 1000L, pot = 200L))
            .isWithin(1e-9).of(5.0)
    }

    @Test fun call_ev_positive_when_equity_above_pot_odds() {
        // pot 200, call 100, equity 0.40 → potOdds 0.333, equity > odds → +EV
        val ev = PostflopHeuristic.callEv(equity = 0.40, pot = 200L, betToCall = 100L)
        assertThat(ev).isGreaterThan(0.0)
    }

    @Test fun call_ev_negative_when_equity_below_pot_odds() {
        // pot 200, call 100, equity 0.20 → potOdds 0.333, equity < odds → -EV
        val ev = PostflopHeuristic.callEv(equity = 0.20, pot = 200L, betToCall = 100L)
        assertThat(ev).isLessThan(0.0)
    }

    @Test fun bet_sizing_includes_all_in_always() {
        val candidates = PostflopHeuristic.betSizingCandidates(
            pot = 1000L, minRaise = 100L, effectiveStack = 5000L,
        )
        assertThat(candidates.any { it.first == ActionType.ALL_IN }).isTrue()
        assertThat(candidates.last().second).isEqualTo(5000L)
    }

    @Test fun bet_sizing_filters_below_min_raise() {
        // pot 100, min raise 200 → 1/3, 1/2 모두 < 200 → 클램프 후 단일 200, 200, 200, ... distinct
        val candidates = PostflopHeuristic.betSizingCandidates(
            pot = 100L, minRaise = 200L, effectiveStack = 5000L,
        )
        // raise 후보들 중 모두 ≥ 200
        candidates.filter { it.first == ActionType.RAISE }.forEach {
            assertThat(it.second).isAtLeast(200L)
        }
    }

    @Test fun fold_prob_increases_with_bet_size() {
        val small = PostflopHeuristic.estimatedFoldProb(amount = 30L, pot = 100L, opponents = 1)
        val large = PostflopHeuristic.estimatedFoldProb(amount = 200L, pot = 100L, opponents = 1)
        assertThat(large).isGreaterThan(small)
    }

    @Test fun fold_prob_drops_with_more_opponents() {
        val one = PostflopHeuristic.estimatedFoldProb(amount = 100L, pot = 100L, opponents = 1)
        val three = PostflopHeuristic.estimatedFoldProb(amount = 100L, pot = 100L, opponents = 3)
        assertThat(three).isLessThan(one)
    }
}
