package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * EquityCalculator (Monte Carlo) 테스트.
 *
 * 알려진 equity 케이스 (러프 ±3%):
 *  - AA vs random: ~85%
 *  - AKs vs random: ~67%
 *  - 72o vs random: ~35%
 *  - AA vs KK preflop: ~81%
 *  - 시드 고정 → 결정론
 */
class EquityCalculatorTest {

    private fun calc(seed: Long = 42L) = EquityCalculator(seed)

    private fun within(actual: Double, expected: Double, delta: Double) {
        assertThat(actual).isAtLeast(expected - delta)
        assertThat(actual).isAtMost(expected + delta)
    }

    @Test fun aces_vs_random_around_85_percent() {
        val eq = calc().holdemEquity(hole = hand("AS", "AH"), opponents = 1, iterations = 3_000)
        within(eq, 0.85, 0.04)
    }

    @Test fun ak_suited_vs_random_around_67_percent() {
        val eq = calc().holdemEquity(hole = hand("AS", "KS"), opponents = 1, iterations = 3_000)
        within(eq, 0.67, 0.04)
    }

    @Test fun seventwo_offsuit_vs_random_around_35_percent() {
        val eq = calc().holdemEquity(hole = hand("7S", "2H"), opponents = 1, iterations = 3_000)
        within(eq, 0.35, 0.05)
    }

    @Test fun aces_vs_5_random_opponents_drops_to_around_55_percent() {
        // AA vs 5 random ~ 55%
        val eq = calc().holdemEquity(hole = hand("AS", "AH"), opponents = 5, iterations = 2_000)
        within(eq, 0.55, 0.07)
    }

    @Test fun deterministic_for_same_seed() {
        val a = calc(seed = 7L).holdemEquity(hand("KS", "KH"), opponents = 2, iterations = 500)
        val b = calc(seed = 7L).holdemEquity(hand("KS", "KH"), opponents = 2, iterations = 500)
        assertThat(a).isEqualTo(b)
    }

    @Test fun board_known_river_set_over_set_almost_certain_win() {
        // Hero AA, board AKQ52 모두 다른 무늬 → 본인 풀하우스 가능 X, 그러나 셋 트리플
        // 단순화: AA + 보드 KKK → 풀하우스 (AA + KKK → KKKAA Full house Aces full of Kings)
        val board = hand("KS", "KH", "KD", "5C", "2H")
        val eq = calc().holdemEquity(hand("AS", "AC"), board = board, opponents = 1, iterations = 1_000)
        // 상대가 K 가지면 quads, 그 외엔 본인 우세 — 약 95%+
        within(eq, 0.97, 0.05)
    }

    @Test fun rejects_invalid_hole_size() {
        val ex = runCatching {
            calc().holdemEquity(hand("AS"), opponents = 1)
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test fun rejects_duplicate_cards() {
        val ex = runCatching {
            calc().holdemEquity(hand("AS", "AS"), opponents = 1)
        }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---------- 7-Stud (단순 회귀) ----------
    @Test fun seven_stud_equity_is_in_zero_one_range() {
        val mySeven = hand("AS", "AH", "AD", "KC", "QH", "JS", "TD")  // 트리플 A + broadway
        val opp = listOf(hand("2C", "5D", "7H"))                      // 약한 업카드 3장
        val eq = calc().sevenStudEquity(mySeven, opp, iterations = 500)
        assertThat(eq).isAtLeast(0.0)
        assertThat(eq).isAtMost(1.0)
        // 트리플 A 는 매우 강함
        assertThat(eq).isAtLeast(0.7)
    }

    @Test fun seven_stud_hi_lo_split_returns_in_range() {
        val mySeven = hand("AS", "2D", "3C", "4H", "5S", "KH", "QD")  // 백스트레이트 + 휠
        val opp = listOf(hand("KC", "QH", "JS"))
        val eq = calc().sevenStudEquity(mySeven, opp, iterations = 500, hiLoSplit = true)
        assertThat(eq).isAtLeast(0.0)
        assertThat(eq).isAtMost(1.0)
        // 휠 hi+lo 동시 강 → scoop 가능성 ↑ → equity ≥ 0.6 기대
        assertThat(eq).isAtLeast(0.5)
    }
}
