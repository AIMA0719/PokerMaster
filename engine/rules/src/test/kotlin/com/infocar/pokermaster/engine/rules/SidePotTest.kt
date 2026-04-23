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

    // =====================================================================
    // M3 Sprint2-F 검증 스위트 SP-01 ~ SP-08
    // 설계서 v1.1 §3.1 기준. 기존 테스트와 시나리오가 겹칠 수 있어
    // spec_ prefix 로 네임스페이스 분리.
    // =====================================================================

    // SP-01: 단순 올인 — 3인, A 작은 올인, B/C 더 큰 동일 콜 → main + side1(B/C) 자격자.
    @Test fun `SP-01 simple all-in creates main and side pot`() {
        val input = listOf(pc(0, 1_000L), pc(1, 4_000L), pc(2, 4_000L))
        val r = SidePotCalculator.compute(input)
        // L=1000: 1000*3=3000, eligible {0,1,2}
        // L=4000: 3000*2=6000, eligible {1,2}
        assertThat(r.pots).hasSize(2)
        assertThat(r.pots[0].amount).isEqualTo(3_000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2)
        assertThat(r.pots[1].amount).isEqualTo(6_000L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2)
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-02: 다중 올인 — 4인, 서로 다른 스택 A<B<C<D 전원 올인 → main + side1 + side2 + side3(uncalled).
    @Test fun `SP-02 multi all-in creates chain of side pots`() {
        val input = listOf(pc(0, 500L), pc(1, 1_200L), pc(2, 2_500L), pc(3, 4_000L))
        val r = SidePotCalculator.compute(input)
        // L=500:  500*4=2000, eligible {0,1,2,3}
        // L=1200: 700*3=2100, eligible {1,2,3}
        // L=2500: 1300*2=2600, eligible {2,3}
        // L=4000: 1500*1=1500, uncalled → seat 3
        assertThat(r.pots).hasSize(3)
        assertThat(r.pots[0].amount).isEqualTo(2_000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2, 3)
        assertThat(r.pots[1].amount).isEqualTo(2_100L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2, 3)
        assertThat(r.pots[2].amount).isEqualTo(2_600L)
        assertThat(r.pots[2].eligibleSeats).containsExactly(2, 3)
        assertThat(r.uncalledReturn).containsExactly(3, 1_500L)
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-03: 완전 일치 콜 — 올인 없이 전원 같은 금액 → 단일 메인팟만.
    @Test fun `SP-03 uniform call yields single main pot`() {
        val input = listOf(pc(0, 800L), pc(1, 800L), pc(2, 800L))
        val r = SidePotCalculator.compute(input)
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(2_400L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2)
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-04: 조기 폴드 — A 가 작게 커밋 후 폴드, 나머지 B/C/D 올인(금액 다름).
    // A 커밋은 main 팟에 포함되지만 eligible 에서 빠짐.
    @Test fun `SP-04 early fold contributes chips but not eligibility`() {
        val input = listOf(
            pc(0, 300L, folded = true),
            pc(1, 1_000L), pc(2, 2_500L), pc(3, 4_000L),
        )
        val r = SidePotCalculator.compute(input)
        // L=300:  300*4=1200, eligible {1,2,3} (A 폴드)
        // L=1000: 700*3=2100, eligible {1,2,3}
        // L=2500: 1500*2=3000, eligible {2,3}
        // L=4000: 1500*1=1500, uncalled → seat 3
        assertThat(r.pots).hasSize(3)
        assertThat(r.pots[0].amount).isEqualTo(1_200L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(1, 2, 3)
        assertThat(r.pots[1].amount).isEqualTo(2_100L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(1, 2, 3)
        assertThat(r.pots[2].amount).isEqualTo(3_000L)
        assertThat(r.pots[2].eligibleSeats).containsExactly(2, 3)
        assertThat(r.uncalledReturn).containsExactly(3, 1_500L)
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-05: 자기 혼자 적립 환급 — 마지막 레이저의 unmatched 부분이 uncalledReturn 으로 돌려짐.
    @Test fun `SP-05 last raiser unmatched layer is uncalled return`() {
        val input = listOf(pc(0, 600L), pc(1, 600L), pc(2, 2_000L))
        val r = SidePotCalculator.compute(input)
        // L=600:  600*3=1800, eligible {0,1,2}
        // L=2000: 1400*1=1400, uncalled → seat 2
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(1_800L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2)
        assertThat(r.uncalledReturn).containsExactly(2, 1_400L)
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-06: 자격자 0 팟 (dead money) — 어떤 layer 의 eligible 가 전부 폴드/자격 없음.
    // 폴드 A 가 최고 커밋 → 상위 layer 는 eligible 0 → dead money 로 적립.
    @Test fun `SP-06 layer with zero eligible becomes dead money`() {
        val input = listOf(
            pc(0, 3_000L, folded = true),
            pc(1, 1_000L), pc(2, 1_000L),
        )
        val r = SidePotCalculator.compute(input)
        // L=1000: 1000*3=3000, eligible {1,2}
        // L=3000: 2000*1=2000, 유일 기여자 seat 0 이지만 folded → eligible 공집합 → dead money
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(3_000L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(1, 2)
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(2_000L)
        assertConservation(input, r)
    }

    // SP-07: heads-up 올인 — 2인 heads-up, 큰 스택이 작은 쪽 올인에 콜 → main + uncalled 환급.
    @Test fun `SP-07 heads-up all-in with call produces uncalled return`() {
        val input = listOf(pc(0, 1_200L), pc(1, 5_000L))
        val r = SidePotCalculator.compute(input)
        // L=1200: 1200*2=2400, eligible {0,1}
        // L=5000: 3800*1=3800, uncalled → seat 1
        assertThat(r.pots).hasSize(1)
        assertThat(r.pots[0].amount).isEqualTo(2_400L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1)
        assertThat(r.uncalledReturn).containsExactly(1, 3_800L)
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }

    // SP-08: 앤티 + 7-stud bring-in 올인 — 앤티 1 ×3인 + bring-in 플레이어(seat2) 스택 한계 3으로 올인.
    // seat 0/1 은 부유 스택으로 이후 스트릿에서 추가 레이즈까지 진행 → main + side pot 발생.
    // v1.1 §3.1: committed = 앤티 + 모든 스트릿 베팅 합계. SidePotCalculator 는 모드 무관.
    // 커밋 내역: seat2 = 앤티1+bring-in2 = 3 (올인), seat0/1 = 앤티1 + 콜업 2 + 이후 레이즈 20 = 23.
    @Test fun `SP-08 seven-stud ante plus bring-in all-in`() {
        val input = listOf(
            pc(0, 23L),
            pc(1, 23L),
            pc(2, 3L), // bring-in 올인, 살아있음
        )
        val r = SidePotCalculator.compute(input)
        // L=3:  3*3 = 9,  eligible {0,1,2} (main pot — bring-in 플레이어 자격)
        // L=23: 20*2 = 40, eligible {0,1}   (side pot — 이후 스트릿 베팅)
        assertThat(r.pots).hasSize(2)
        assertThat(r.pots[0].amount).isEqualTo(9L)
        assertThat(r.pots[0].eligibleSeats).containsExactly(0, 1, 2)
        assertThat(r.pots[1].amount).isEqualTo(40L)
        assertThat(r.pots[1].eligibleSeats).containsExactly(0, 1)
        assertThat(r.uncalledReturn).isEmpty()
        assertThat(r.deadMoney).isEqualTo(0L)
        assertConservation(input, r)
    }
}
