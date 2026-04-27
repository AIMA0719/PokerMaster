package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 7-card stud / 7-stud Hi-Lo 룰 감사 — 엣지 케이스 회귀 테스트.
 *
 * 다루는 시나리오 (감사 스코프):
 *  - Wheel(A-2-3-4-5) 가 hi 와 lo 양쪽에서 어떻게 평가되는지 (Korean Variant)
 *  - 동률 lo 핸드의 quartering 분배 (홀수칩 흐름)
 *  - 모든 자격자가 동시에 hi/lo 동률인 극단 케이스
 *  - 다중 사이드팟 + Hi-Lo qualifier 가 사이드팟마다 다르게 적용
 *  - LowValue 의 compareTo 안정성 (기준 동률, 동일 객체)
 */
class AuditEdgeCasesTest {

    private fun hv(category: HandCategory, vararg tiebreakers: Int) =
        HandValue(category, tiebreakers.toList(), emptyList())

    private fun lv(vararg desc: Int) = LowValue(desc.toList(), emptyList())

    // ============================================================================================
    // Wheel 평가
    // ============================================================================================

    /** 휠(A-2-3-4-5) 7장 핸드를 한국식 변형으로 평가하면 hi=STRAIGHT_BACK, lo=가장 강한 lo. */
    @Test fun wheel_in_korean_stud_is_back_straight_and_strongest_low() {
        val seven = hand("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)!!
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(high.tiebreakers).isEqualTo(listOf(5))
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    /** 백스트레이트는 한국식 변형에서 일반 스트레이트보다 약 — 그러나 트리플보다는 강 (strength=5 > 4). */
    @Test fun wheel_high_loses_to_six_high_straight_but_beats_trips() {
        val wheel = HandEvaluatorHiLo.evaluateHigh(
            hand("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        )
        val sixStraight = HandEvaluatorHiLo.evaluateHigh(
            hand("6S", "5D", "4C", "3H", "2S", "KH", "QD")
        )
        val trips = HandEvaluatorHiLo.evaluateHigh(
            hand("KS", "KH", "KD", "9C", "8S", "7D", "2H")
        )
        assertThat(sixStraight).isGreaterThan(wheel)
        assertThat(wheel).isGreaterThan(trips)
    }

    // ============================================================================================
    // Wheel + 일반 6-high straight 가 둘 다 있는 7장 — 일반 스트레이트가 채택됨
    // ============================================================================================

    @Test fun seven_cards_with_both_wheel_and_six_high_picks_six_straight_for_high() {
        // A,2,3,4,5,6 → 6-high straight 가능 (5-high 백스트레이트보다 강).
        val seven = hand("AS", "2D", "3C", "4H", "5S", "6C", "KD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)!!
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(high.tiebreakers).isEqualTo(listOf(6))
        // 로우는 여전히 휠 (5,4,3,2,1) — 가장 강한 lo.
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ============================================================================================
    // ShowdownResolver — 동률 lo + hi 단독 (quarter 분배)
    // ============================================================================================

    /** 3인 hi-lo 분배: hi 단독 1명, lo 동률 2명 → hi=500, lo 250+250. */
    @Test fun resolver_lo_tie_two_way_quarters_pot() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.FULL_HOUSE, 13, 9),
            1 to hv(HandCategory.ONE_PAIR, 5),
            2 to hv(HandCategory.HIGH_CARD, 12),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to lv(7, 5, 4, 3, 2),  // 동률
            2 to lv(7, 5, 4, 3, 2),  // 동률
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true,
            seatOrderForOdd = listOf(0, 1, 2))
        assertThat(r[0]).isEqualTo(500L)
        assertThat(r[1]).isEqualTo(250L)
        assertThat(r[2]).isEqualTo(250L)
        // 칩 보존
        assertThat(r.values.sum()).isEqualTo(1000L)
    }

    /** Hi 동률 + Lo 단독 — hi 측 절반을 hi 동률 2명에게 분할, lo 단독 1명. */
    @Test fun resolver_hi_tie_two_way_lo_solo() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.FLUSH, 14, 10, 8, 5, 2),     // 동률
            1 to hv(HandCategory.FLUSH, 14, 10, 8, 5, 2),     // 동률
            2 to hv(HandCategory.HIGH_CARD, 12),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to null,
            2 to lv(7, 5, 4, 3, 2),
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true,
            seatOrderForOdd = listOf(0, 1, 2))
        // hi=500 → seat 0/1 각 250; lo=500 → seat 2.
        assertThat(r[0]).isEqualTo(250L)
        assertThat(r[1]).isEqualTo(250L)
        assertThat(r[2]).isEqualTo(500L)
        assertThat(r.values.sum()).isEqualTo(1000L)
    }

    /** Hi 동률 + Lo 동률 — 둘 다 quarter 분배 (4자 모두 250 가능). */
    @Test fun resolver_hi_and_lo_both_tied_two_way_quarters() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        // 같은 핸드를 양쪽 다 갖고 있다고 가정 — chopped scoop.
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT_BACK, 5),
            1 to hv(HandCategory.STRAIGHT_BACK, 5),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(5, 4, 3, 2, 1),
            1 to lv(5, 4, 3, 2, 1),
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true,
            seatOrderForOdd = listOf(0, 1))
        // hi=500/2=250 each; lo=500/2=250 each → 500 each.
        assertThat(r[0]).isEqualTo(500L)
        assertThat(r[1]).isEqualTo(500L)
        assertThat(r.values.sum()).isEqualTo(1000L)
    }

    /** 1003 칩 hi-lo 동률 다중 — 홀수칩 흐름 검증. half=501, oddChip=1, hi=502 lo=501. */
    @Test fun resolver_hi_lo_odd_chip_distributes_per_seat_order() {
        val pot = Pot(1003L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),     // 동률
            1 to hv(HandCategory.STRAIGHT, 9),     // 동률
            2 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to null,
            2 to lv(7, 5, 4, 3, 2),
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true,
            seatOrderForOdd = listOf(0, 1, 2))
        // hi=502 → seat 0/1 동률 → 251 each (base=251, no remainder).
        // lo=501 → seat 2 단독 → 501.
        assertThat(r[0]).isEqualTo(251L)
        assertThat(r[1]).isEqualTo(251L)
        assertThat(r[2]).isEqualTo(501L)
        assertThat(r.values.sum()).isEqualTo(1003L)
    }

    // ============================================================================================
    // 다중 사이드팟 + Hi-Lo qualifier 가 사이드팟마다 다르게 (자격자 풀이 다르므로)
    // ============================================================================================

    /**
     * 메인팟에는 lo 자격자 1명, 사이드팟에는 lo 자격자 0명.
     * 메인팟은 hi/lo 분할, 사이드팟은 hi 단독.
     */
    @Test fun resolve_all_side_pot_lo_qualifier_disappears() {
        val mainPot = Pot(amount = 3000L, eligibleSeats = setOf(0, 1, 2))
        val sidePot = Pot(amount = 4000L, eligibleSeats = setOf(1, 2))
        val result = SidePotResult(
            pots = listOf(mainPot, sidePot),
            uncalledReturn = emptyMap(),
            deadMoney = 0L,
        )
        val hi = mapOf(
            0 to hv(HandCategory.HIGH_CARD, 14, 10, 5, 4, 2),
            1 to hv(HandCategory.TWO_PAIR, 13, 9),    // 사이드팟 hi 승
            2 to hv(HandCategory.ONE_PAIR, 7),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(7, 5, 4, 3, 2),     // 메인팟 lo 자격, 단독
            1 to null,
            2 to null,
        )
        val r = ShowdownResolver.resolveAll(
            result, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
        )
        // 메인팟 3000 → hi=1500 (TWO_PAIR seat1) + lo=1500 (seat0).
        // 사이드팟 4000 → lo qualifier 없음 → hi 전액 (TWO_PAIR seat1).
        assertThat(r[0]).isEqualTo(1500L)
        assertThat(r[1]).isEqualTo(1500L + 4000L)
        assertThat(r[2]).isNull()
    }

    /**
     * 사이드팟에 lo 자격자가 새로 등장 (메인에는 lo 0명) — main 은 hi 단독, side 는 hi/lo 분할.
     * 실전에선 드물지만 (lo 자격은 4장 이상 8 이하 가질 때 가능) 가능.
     */
    @Test fun resolve_all_main_pot_lo_disqualified_side_lo_qualified() {
        // seat 0: 작은 스택 (메인팟만 자격), 페어로 lo 자격 X.
        // seat 1, 2: 큰 스택 (메인+사이드 자격), 둘 다 lo 자격.
        val mainPot = Pot(amount = 3000L, eligibleSeats = setOf(0, 1, 2))
        val sidePot = Pot(amount = 2000L, eligibleSeats = setOf(1, 2))
        val result = SidePotResult(
            pots = listOf(mainPot, sidePot),
            uncalledReturn = emptyMap(),
            deadMoney = 0L,
        )
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 14, 10, 8, 5),
            1 to hv(HandCategory.HIGH_CARD, 8, 7, 5, 3, 2),
            2 to hv(HandCategory.HIGH_CARD, 7, 6, 4, 3, 2),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,                    // 페어 → lo 자격 X
            1 to lv(8, 7, 5, 3, 2),
            2 to lv(7, 6, 4, 3, 2),       // 더 강한 lo
        )
        val r = ShowdownResolver.resolveAll(
            result, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
        )
        // 메인팟 3000: lo 자격자 = {1, 2}. hi=1500 (ONE_PAIR seat0 우승), lo=1500 (seat2 우승).
        // 사이드팟 2000: lo 자격자 = {2} (seat1 lo 도 자격이지만 seat2 lo 가 더 강).
        //   hi=1000 (HIGH_CARD seat1 8-high vs seat2 7-high → seat1 win), lo=1000 (seat2).
        assertThat(r[0]).isEqualTo(1500L)
        assertThat(r[1]).isEqualTo(1000L)
        assertThat(r[2]).isEqualTo(1500L + 1000L)
        // 칩 보존
        assertThat(r.values.sum()).isEqualTo(5000L)
    }

    // ============================================================================================
    // SidePotCalculator + Hi-Lo: all-in 시나리오의 chip-conservation 검증
    // ============================================================================================

    /**
     * 3인 stud Hi-Lo: seat 0 small all-in, seat 1/2 big all-in.
     * SidePot 분리 후 ShowdownResolver 가 사이드팟마다 hi/lo 독립 평가하는지 확인.
     */
    @Test fun all_in_hi_lo_chip_conservation_through_resolve_all() {
        val commitments = listOf(
            PlayerCommitment(seat = 0, committed = 1000L, folded = false),
            PlayerCommitment(seat = 1, committed = 5000L, folded = false),
            PlayerCommitment(seat = 2, committed = 5000L, folded = false),
        )
        val sideResult = SidePotCalculator.compute(commitments)
        // L=1000: 1000*3=3000, eligible {0,1,2}
        // L=5000: 4000*2=8000, eligible {1,2}
        assertThat(sideResult.pots).hasSize(2)
        assertThat(sideResult.uncalledReturn).isEmpty()
        assertThat(sideResult.deadMoney).isEqualTo(0L)

        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT_BACK, 5),    // 백스트레이트 = 휠
            1 to hv(HandCategory.TWO_PAIR, 13, 9),
            2 to hv(HandCategory.ONE_PAIR, 12),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(5, 4, 3, 2, 1),     // 휠
            1 to null,
            2 to null,
        )
        val r = ShowdownResolver.resolveAll(
            sideResult, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
        )
        // 메인팟 3000:
        //   hi 비교 — STRAIGHT_BACK(strength=5) > TWO_PAIR(3) > ONE_PAIR(2). → seat 0 우승.
        //   lo 자격자 = {0}. lo 단독 → seat 0.
        //   → seat 0 scoop 3000.
        // 사이드팟 8000:
        //   eligible {1, 2}. hi 비교 — TWO_PAIR > ONE_PAIR. → seat 1 우승.
        //   lo 자격자 = 0 (eligible 안에). → hi 전액 8000 → seat 1.
        assertThat(r[0]).isEqualTo(3000L)
        assertThat(r[1]).isEqualTo(8000L)
        assertThat(r[2]).isNull()
        assertThat(r.values.sum()).isEqualTo(11_000L)
    }

    // ============================================================================================
    // LowValue compareTo — 동일 객체 비교
    // ============================================================================================

    @Test fun low_value_compares_to_self_as_zero() {
        val lo = lv(8, 7, 5, 3, 2)
        assertThat(lo.compareTo(lo)).isEqualTo(0)
    }

    @Test fun low_value_natural_ordering_strongest_first_with_wheel() {
        val wheel = lv(5, 4, 3, 2, 1)
        val seven = lv(7, 6, 5, 3, 2)
        val eight = lv(8, 5, 4, 3, 2)
        val sorted = listOf(eight, seven, wheel).sorted()   // 자연 정렬: 작을수록 강함
        assertThat(sorted).containsExactly(wheel, seven, eight).inOrder()
    }
}
