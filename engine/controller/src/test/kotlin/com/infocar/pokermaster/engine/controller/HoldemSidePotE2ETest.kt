package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.engine.rules.HandCategory
import com.infocar.pokermaster.engine.rules.HandValue
import com.infocar.pokermaster.engine.rules.PlayerCommitment
import com.infocar.pokermaster.engine.rules.ShowdownResolver
import com.infocar.pokermaster.engine.rules.SidePotCalculator
import org.junit.jupiter.api.Test

/**
 * A2 — Hold'em 메인팟 동률 + 사이드팟 단독 승자 혼합 E2E.
 *
 * 시나리오: 3명 — 좌석 0(short stack, all-in), 좌석 1, 좌석 2 (큰 스택).
 *  - 좌석 0: 300칩 all-in
 *  - 좌석 1: 1000 commit
 *  - 좌석 2: 1000 commit
 *  - 메인팟 = 300*3 = 900, eligibleSeats = {0,1,2}
 *  - 사이드팟 = 700*2 = 1400, eligibleSeats = {1,2}
 *
 * 핸드:
 *  - 좌석 0 와 좌석 1 의 hi 동률 (예: 동률 TWO_PAIR) → 메인팟 분할.
 *  - 좌석 2 가 사이드팟에서 단독 승 (예: STRAIGHT > 좌석 1 의 TWO_PAIR; 좌석 0 은 사이드팟 자격 없음).
 *
 * 검증 포인트:
 *  - [SidePotCalculator] + [ShowdownResolver.resolveAll] 통합 경로가 자격 분리 + 분할/단독 동시 처리.
 *  - 칩 보존: Σ payouts == 메인팟 + 사이드팟.
 *
 * 실 reducer-deck 통합은 결정적 시드로 hi 카테고리 동률을 강제하는 게 어렵기 때문에 본 테스트는
 * SidePotCalculator + ShowdownResolver 직접 호출로 통합 경로를 e2e 검증한다.
 */
class HoldemSidePotE2ETest {

    private fun hv(category: HandCategory, vararg tiebreakers: Int) =
        HandValue(category, tiebreakers.toList(), emptyList())

    @Test
    fun side_pot_layer_decomposition_for_short_stack_all_in() {
        // 좌석 0(short all-in 300), 1(1000 commit), 2(1000 commit).
        // 메인팟 900 = {0,1,2} eligible. 사이드팟 1400 = {1,2} eligible.
        val commitments = listOf(
            PlayerCommitment(seat = 0, committed = 300L, folded = false),
            PlayerCommitment(seat = 1, committed = 1000L, folded = false),
            PlayerCommitment(seat = 2, committed = 1000L, folded = false),
        )
        val sideResult = SidePotCalculator.compute(commitments)
        assertThat(sideResult.pots).hasSize(2)
        val mainPot = sideResult.pots[0]
        assertThat(mainPot.amount).isEqualTo(900L)
        assertThat(mainPot.eligibleSeats).containsExactly(0, 1, 2)
        val sidePot = sideResult.pots[1]
        assertThat(sidePot.amount).isEqualTo(1400L)
        assertThat(sidePot.eligibleSeats).containsExactly(1, 2)
        assertThat(sideResult.uncalledReturn).isEmpty()
        assertThat(sideResult.deadMoney).isEqualTo(0L)
    }

    @Test
    fun main_pot_tie_split_with_side_pot_single_winner() {
        // 시나리오: 좌석 0 (short all-in 300) 과 좌석 2 가 hi 동률 → 메인팟 (=900) 절반씩.
        // 좌석 1 은 더 약 → 메인팟 zero. 사이드팟 1400 는 {1,2} eligible 인데 좌석 2 가 (1 보다) 강함.
        // → 좌석 0 = 450, 좌석 1 = 0, 좌석 2 = 450 + 1400 = 1850.
        // 단일 핸드값 모델에서 메인팟 동률 + 사이드팟 단독 승자 동시 발생을 만들 수 있는 유일한 구도다.
        val commitments = listOf(
            PlayerCommitment(seat = 0, committed = 300L, folded = false),
            PlayerCommitment(seat = 1, committed = 1000L, folded = false),
            PlayerCommitment(seat = 2, committed = 1000L, folded = false),
        )
        val sideResult = SidePotCalculator.compute(commitments)
        assertThat(sideResult.pots).hasSize(2)

        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),    // 메인팟 동률 (좌석 2 와)
            1 to hv(HandCategory.ONE_PAIR, 5),    // 둘 다 약함
            2 to hv(HandCategory.STRAIGHT, 9),    // 메인팟 동률 (좌석 0 와) + 사이드팟 단독 승
        )
        val payouts = ShowdownResolver.resolveAll(
            sideResult, hi,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = false,
        )

        // 메인팟 900 → 좌석 0 vs 좌석 2 동률 분할 = 450 each.
        // 사이드팟 1400 → 좌석 2 단독 승 (좌석 0 자격 없음, 좌석 1 hi 약).
        assertThat(payouts[0]).isEqualTo(450L)
        assertThat(payouts[1]).isNull()
        assertThat(payouts[2]).isEqualTo(450L + 1400L)
        // 칩 보존
        assertThat(payouts.values.sum()).isEqualTo(900L + 1400L)
    }

    @Test
    fun three_way_with_uncalled_bet_returns_to_short_stack_actor() {
        // 좌석 0 가 800 raise, 좌석 1 이 300 only (all-in), 좌석 2 fold.
        // 메인팟 = 300+300 (좌석 0 contrib 300 layer + 좌석 1 contrib 300 + 좌석 2 contrib 0) — 좌석 2
        // 베팅 안 함 가정. 좌석 0 의 800 중 500 = uncalled → 환급.
        val commitments = listOf(
            PlayerCommitment(seat = 0, committed = 800L, folded = false),
            PlayerCommitment(seat = 1, committed = 300L, folded = false),
            PlayerCommitment(seat = 2, committed = 0L, folded = true),
        )
        val sideResult = SidePotCalculator.compute(commitments)
        // 메인팟 = 600 (좌석 0,1 layer 300 each), 좌석 0 uncalled 500.
        assertThat(sideResult.pots).hasSize(1)
        assertThat(sideResult.pots[0].amount).isEqualTo(600L)
        assertThat(sideResult.pots[0].eligibleSeats).containsExactly(0, 1)
        assertThat(sideResult.uncalledReturn[0]).isEqualTo(500L)
    }
}
