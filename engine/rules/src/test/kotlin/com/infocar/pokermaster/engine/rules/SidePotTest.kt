package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 사이드팟 알고리즘 테스트 (v1.1 §3.1).
 *
 * 검증 포인트:
 *  - 다중 올인 layer 분리
 *  - 폴드 칩이 자기 layer 까지만 기여
 *  - 자격자 = committed >= L AND NOT folded
 *  - 칩 보존: Σ pots + Σ uncalled + dead = Σ input.committed (strict)
 *  - 자기 혼자 적립 layer = uncalledReturn (즉시 환급)
 *  - 모두 폴드 layer = deadMoney (다음 핸드 이월 / v1 손실)
 */
class SidePotTest {

    private fun pc(seat: Int, committed: Long, folded: Boolean = false) =
        PlayerCommitment(seat, committed, folded)

    private fun assertConservation(input: List<PlayerCommitment>, r: SidePotResult) {
        val totalIn = input.sumOf { it.committed }
        val totalOut = r.pots.sumOf { it.amount } + r.uncalledReturn.values.sum() + r.deadMoney
        assertThat(totalOut).isEqualTo(totalIn)
    }

    // ---------- SP-01: 3-way 동시 올인 (1k/3k/5k) ----------
    @Test fun sp01_three_way_unequal_allin() {
        val input = listOf(pc(0, 1000L), pc(1, 3000L), pc(2, 5000L))
        val r = SidePotCalculator.compute(input)

        // main pot: 1000 × 3 = 3000, eligible {0,1,2}
        // side1: (3000-1000) × 2 = 4000, eligible {1,2}
        // side2: (5000-3000) × 1 = 2000, uncalled → seat 2 환급
        assertThat(r.pots).hasSize(2)
        assertThat(r.pots[0].amount).isEqualTo(3000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2)
        assertThat(r.pots[1].amount).isEqualTo(4000L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2)
        assertThat(r.uncalledReturn).containsExactly(2, 2000L)
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // ---------- SP-02: 4-way + 1명 폴드 ----------
    @Test fun sp02_fold_chips_contribute_only_to_own_layer() {
        val input = listOf(
            pc(0, 500L, folded = true),
            pc(1, 1500L), pc(2, 1500L), pc(3, 3000L),
        )
        val r = SidePotCalculator.compute(input)
        // L=500: 500*4=2000, eligible {1,2,3}
        // L=1500: 1000*3=3000, eligible {1,2,3}
        // L=3000: 1500*1=1500, uncalled → seat 3
        assertThat(r.pots).hasSize(2)
        assertThat(r.pots[0].amount).isEqualTo(2000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(1, 2, 3)
        assertThat(r.pots[1].amount).isEqualTo(3000L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2, 3)
        assertThat(r.uncalledReturn).containsExactly(3, 1500L)
        assertConservation(input, r)
    }

    // ---------- SP-06: 헤즈업 동시 올인 → 단일 팟 ----------
    @Test fun sp06_heads_up_equal_allin_single_pot() {
        val input = listOf(pc(0, 5000L), pc(1, 5000L))
        val r = SidePotCalculator.compute(input)
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(10_000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1)
        assertThat(r.uncalledReturn).isEmpty()
        assertConservation(input, r)
    }

    // ---------- SP-07: 5-way 다단 올인 ----------
    @Test fun sp07_five_way_multi_allin() {
        val input = listOf(
            pc(0, 500L), pc(1, 1500L), pc(2, 3000L), pc(3, 5000L), pc(4, 8000L)
        )
        val r = SidePotCalculator.compute(input)
        // 4개 contested pots + 1 uncalled (seat 4 의 마지막 layer)
        assertThat(r.pots).hasSize(4)
        assertThat(r.pots[0].amount).isEqualTo(2500L)   // 500*5
        assertThat(r.pots[1].amount).isEqualTo(4000L)   // 1000*4
        assertThat(r.pots[2].amount).isEqualTo(4500L)   // 1500*3
        assertThat(r.pots[3].amount).isEqualTo(4000L)   // 2000*2
        assertThat(r.uncalledReturn).containsExactly(4, 3000L)   // 3000*1
        assertConservation(input, r)
    }

    // ---------- SP-08: 폴드 후 다른 사람만 추가 베팅 ----------
    @Test fun sp08_fold_then_others_continue() {
        val input = listOf(
            pc(0, 1000L, folded = true), pc(1, 3000L), pc(2, 3000L),
        )
        val r = SidePotCalculator.compute(input)
        // L=1000: 1000*3=3000, eligible {1,2}
        // L=3000: 2000*2=4000, eligible {1,2}
        assertThat(r.pots).hasSize(2)
        assertThat(r.pots[0].amount).isEqualTo(3000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(1, 2)
        assertThat(r.pots[1].amount).isEqualTo(4000L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2)
        assertThat(r.uncalledReturn).isEmpty()
        assertConservation(input, r)
    }

    // ---------- 빈 입력 / 0 commit ----------
    @Test fun empty_input_returns_empty() {
        val r = SidePotCalculator.compute(emptyList())
        assertThat(r.pots).isEmpty()
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(0L)
    }

    @Test fun all_zero_commit_returns_empty() {
        val r = SidePotCalculator.compute(listOf(pc(0, 0L), pc(1, 0L)))
        assertThat(r.pots).isEmpty()
    }

    // ---------- 모두 동일 베팅 → 단일 팟 ----------
    @Test fun all_equal_commit_single_pot() {
        val input = listOf(pc(0, 100L), pc(1, 100L), pc(2, 100L), pc(3, 100L))
        val r = SidePotCalculator.compute(input)
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(400L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2, 3)
        assertConservation(input, r)
    }

    // ---------- 한 명만 적립 → uncalled return ----------
    @Test fun lone_committer_returns_to_self() {
        val input = listOf(pc(0, 500L), pc(1, 0L), pc(2, 0L))
        val r = SidePotCalculator.compute(input)
        assertThat(r.pots).isEmpty()
        assertThat(r.uncalledReturn).containsExactly(0, 500L)
        assertConservation(input, r)
    }

    // ---------- 모두 폴드 → dead money ----------
    @Test fun all_folded_becomes_dead_money() {
        val input = listOf(pc(0, 100L, folded = true), pc(1, 100L, folded = true))
        val r = SidePotCalculator.compute(input)
        assertThat(r.pots).isEmpty()
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(200L)
        assertConservation(input, r)
    }

    // ---------- 자기 layer 만 기여 ----------
    @Test fun fold_only_contributes_within_own_level() {
        val input = listOf(
            pc(0, 200L, folded = true), pc(1, 200L), pc(2, 1000L),
        )
        val r = SidePotCalculator.compute(input)
        // L=200: 600, eligible {1,2}
        // L=1000: 800, uncalled → seat 2
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(600L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(1, 2)
        assertThat(r.uncalledReturn).containsExactly(2, 800L)
        assertConservation(input, r)
    }

    // ---------- 보존성 fuzz: 모든 케이스에서 strict 보존 ----------
    @Test fun conservation_fuzz_1000_random_inputs() {
        val rnd = java.util.Random(42)
        repeat(1000) {
            val n = 2 + rnd.nextInt(7)
            val input = (0 until n).map { i ->
                pc(
                    seat = i,
                    committed = (rnd.nextLong() and 0x3FFFL),
                    folded = rnd.nextBoolean(),
                )
            }
            val r = SidePotCalculator.compute(input)
            assertConservation(input, r)
        }
    }
}
