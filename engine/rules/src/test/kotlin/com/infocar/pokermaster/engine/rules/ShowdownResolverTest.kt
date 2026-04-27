package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Declaration
import org.junit.jupiter.api.Test

/**
 * 쇼다운 분배기 테스트 (v1.1 §3.4 분배).
 *
 * 검증:
 *  - High-only 단독 승자
 *  - High-only 동률 분할 + 홀수칩 좌석순
 *  - Hi-Lo 50:50 분할
 *  - Hi-Lo 로우 자격자 0 → 하이 단독
 *  - Hi-Lo 사이드팟별 qualifier 독립 (resolveAll)
 *  - 스쿱: 동일인이 hi+lo 모두 → 100%
 */
class ShowdownResolverTest {

    private fun hv(category: HandCategory, vararg tiebreakers: Int) =
        HandValue(category, tiebreakers.toList(), emptyList())

    private fun lv(vararg desc: Int) =
        LowValue(desc.toList(), emptyList())

    // ---------- High-only ----------
    @Test fun single_winner_takes_all() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 7),
            1 to hv(HandCategory.TWO_PAIR, 9, 5),
            2 to hv(HandCategory.HIGH_CARD, 14),
        )
        val r = ShowdownResolver.resolve(pot, hi)
        assertThat(r).containsExactly(1, 1000L)
    }

    @Test fun tie_splits_evenly() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),
            1 to hv(HandCategory.STRAIGHT, 9),
        )
        val r = ShowdownResolver.resolve(pot, hi)
        assertThat(r).containsExactly(0, 500L, 1, 500L)
    }

    @Test fun tie_with_odd_chip_goes_to_first_seat_in_order() {
        val pot = Pot(1001L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),
            1 to hv(HandCategory.STRAIGHT, 9),
            2 to hv(HandCategory.STRAIGHT, 9),
        )
        // base = 333, remainder = 2 → 좌석순 [0,1,2] 의 처음 두 좌석에 1씩
        val r = ShowdownResolver.resolve(pot, hi, seatOrderForOdd = listOf(0, 1, 2))
        assertThat(r[0]).isEqualTo(334L)
        assertThat(r[1]).isEqualTo(334L)
        assertThat(r[2]).isEqualTo(333L)
        assertThat(r.values.sum()).isEqualTo(1001L)
    }

    @Test fun tie_with_odd_chip_respects_btn_left_order() {
        val pot = Pot(1001L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),
            1 to hv(HandCategory.STRAIGHT, 9),
            2 to hv(HandCategory.STRAIGHT, 9),
        )
        // BTN 좌측이 seat 2 부터 시계방향이라고 가정 → [2,0,1]
        val r = ShowdownResolver.resolve(pot, hi, seatOrderForOdd = listOf(2, 0, 1))
        assertThat(r[2]).isEqualTo(334L)
        assertThat(r[0]).isEqualTo(334L)
        assertThat(r[1]).isEqualTo(333L)
        assertThat(r.values.sum()).isEqualTo(1001L)
    }

    // ---------- Hi-Lo ----------
    @Test fun hi_lo_no_low_qualifier_hi_takes_all() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 12),
            1 to hv(HandCategory.TWO_PAIR, 9, 5),
        )
        val lo = mapOf<Int, LowValue?>(0 to null, 1 to null)
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        assertThat(r).containsExactly(1, 1000L)
    }

    @Test fun hi_lo_split_evenly() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.TWO_PAIR, 13, 7),  // hi 승
            1 to hv(HandCategory.ONE_PAIR, 5),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to lv(7, 5, 4, 3, 2),     // lo 승
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        assertThat(r).containsExactly(0, 500L, 1, 500L)
    }

    @Test fun hi_lo_odd_chip_goes_to_hi_side() {
        val pot = Pot(1001L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.TWO_PAIR, 13, 7),
            1 to hv(HandCategory.ONE_PAIR, 5),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to lv(7, 5, 4, 3, 2),
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        // half=500, oddChip=1 → hi 받음
        assertThat(r[0]).isEqualTo(501L)
        assertThat(r[1]).isEqualTo(500L)
    }

    @Test fun hi_lo_scoop_same_player_takes_all() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT_BACK, 5),  // 백스트레이트 = 휠 = 강한 hi
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(5, 4, 3, 2, 1),    // 휠
            1 to null,
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        // seat 0 가 hi+lo 모두 = scoop
        assertThat(r).containsExactly(0, 1000L)
    }

    @Test fun hi_lo_low_tied_quarters_pot() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.TWO_PAIR, 13, 7),
            1 to hv(HandCategory.ONE_PAIR, 5),
            2 to hv(HandCategory.ONE_PAIR, 4),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to lv(7, 5, 4, 3, 2),    // 동률
            2 to lv(7, 5, 4, 3, 2),    // 동률
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        // hi=500 → seat 0, lo=500 → seat 1,2 각 250
        assertThat(r[0]).isEqualTo(500L)
        assertThat(r[1]).isEqualTo(250L)
        assertThat(r[2]).isEqualTo(250L)
    }

    // ---------- 사이드팟별 qualifier 독립 (v1.1 §3.4.A) ----------
    @Test fun resolve_all_side_pot_qualifier_independent() {
        // seat 0 = 1000 (작은 스택), seat 1 = 3000, seat 2 = 3000
        // seat 0 만 로우 가능, seat 1/2 는 페어 핸드라 로우 자격 X
        // → 메인팟은 hi+lo 분할(seat 0 lo 단독), 사이드팟은 hi only

        val mainPot = Pot(amount = 3000L, eligibleSeats = setOf(0, 1, 2))
        val sidePot = Pot(amount = 4000L, eligibleSeats = setOf(1, 2))
        val result = SidePotResult(
            pots = listOf(mainPot, sidePot),
            uncalledReturn = emptyMap(),
            deadMoney = 0L,
        )
        val hi = mapOf(
            0 to hv(HandCategory.HIGH_CARD, 14),
            1 to hv(HandCategory.TWO_PAIR, 13, 9),
            2 to hv(HandCategory.ONE_PAIR, 7),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(7, 5, 4, 3, 2),
            1 to null,
            2 to null,
        )
        val r = ShowdownResolver.resolveAll(
            result, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
        )
        // 메인팟: hi=1500 → seat 1 (TWO_PAIR), lo=1500 → seat 0
        // 사이드팟: lo 자격자 0 → hi 전액 → seat 1 (TWO_PAIR > ONE_PAIR)
        assertThat(r[0]).isEqualTo(1500L)
        assertThat(r[1]).isEqualTo(1500L + 4000L)
        assertThat(r[2]).isNull()
    }

    // ---------- Hi-Lo declare 모드 (한국식 7-Stud Hi-Lo) ----------

    @Test fun declare_high_only_takes_high_pot_alone() {
        // seat 0 = HIGH, seat 1 = LOW (lo qualifier 미달 — null)
        // → high 후보 = {0}, low 후보 = {} → seat 0 가 단독 high pot 차지(low 자격자 0 → scoop).
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 7),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(0 to null, 1 to null)
        val declarations = mapOf(0 to Declaration.HIGH, 1 to Declaration.LOW)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1),
            hiLoSplit = true,
            declarations = declarations,
        )
        // seat 1 은 LOW 선언 + qualifier 미달 → lo 자격 박탈, hi 자격도 없음.
        // seat 0 은 HIGH 단독 → 1000 전체.
        assertThat(r).containsExactly(0, 1000L)
    }

    @Test fun declare_low_without_qualifier_loses_low_eligibility() {
        // seat 0 = HIGH, seat 1 = LOW(lo 자격 미달), seat 2 = LOW(lo 자격 만족).
        // hi 후보 = {0}; lo 후보 = {2}; → hi-half + lo-half 분배.
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.TWO_PAIR, 13, 7),
            1 to hv(HandCategory.ONE_PAIR, 9),
            2 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to null,            // qualifier 미달
            2 to lv(7, 5, 4, 3, 2),
        )
        val decls = mapOf(0 to Declaration.HIGH, 1 to Declaration.LOW, 2 to Declaration.LOW)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r[0]).isEqualTo(500L)
        assertThat(r[2]).isEqualTo(500L)
        assertThat(r[1]).isNull()
    }

    @Test fun declare_swing_winning_both_sides_scoops() {
        // seat 0 = SWING (hi/lo 양쪽 1위 — 휠 + 휠), seat 1 = HIGH (약함).
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT_BACK, 5),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(5, 4, 3, 2, 1),
            1 to null,
        )
        val decls = mapOf(0 to Declaration.SWING, 1 to Declaration.HIGH)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r).containsExactly(0, 1000L)
    }

    @Test fun declare_swing_loses_one_side_disqualified_other_player_scoops() {
        // seat 0 = SWING, seat 1 = HIGH 가 hi 더 강함, seat 2 = LOW 가 lo 자격.
        // SWING 은 hi 에서 졌으므로 양쪽 자격 박탈 → hi=seat 1 (HIGH 후보), lo=seat 2 (LOW 후보).
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 7),       // SWING
            1 to hv(HandCategory.TWO_PAIR, 13, 9),  // HIGH 더 강함
            2 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(8, 6, 4, 3, 2),
            1 to null,
            2 to lv(7, 5, 4, 3, 2),
        )
        val decls = mapOf(
            0 to Declaration.SWING,
            1 to Declaration.HIGH,
            2 to Declaration.LOW,
        )
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r[1]).isEqualTo(500L)   // hi-half
        assertThat(r[2]).isEqualTo(500L)   // lo-half
        assertThat(r[0]).isNull()           // SWING 박탈
    }

    @Test fun declare_swing_ties_high_and_wins_low_qualifies() {
        // seat 0 = SWING, seat 1 = HIGH; hi 동률, lo 는 SWING 단독.
        // 동률은 OK ("tie or beat") → SWING 자격 유지, hi 동률 시 둘 다 hi 승자.
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val tieHi = hv(HandCategory.STRAIGHT, 9)
        val hi = mapOf(0 to tieHi, 1 to tieHi)
        val lo = mapOf<Int, LowValue?>(
            0 to lv(7, 5, 4, 3, 2),
            1 to null,
        )
        val decls = mapOf(0 to Declaration.SWING, 1 to Declaration.HIGH)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1),
            hiLoSplit = true,
            declarations = decls,
        )
        // hi-half(500) → seat 0/1 동률 → 250+250.
        // lo-half(500) → seat 0 단독.
        assertThat(r[0]).isEqualTo(250L + 500L)
        assertThat(r[1]).isEqualTo(250L)
    }

    @Test fun declare_two_swings_each_lose_one_side_then_other_declarer_scoops() {
        // seat 0 = SWING (hi 약, lo 강), seat 1 = SWING (hi 강, lo 약), seat 2 = HIGH (hi 중간).
        // SWING 0 은 hi 에서 SWING 1 에 짐 → 박탈.
        // SWING 1 은 lo 에서 SWING 0 에 짐 → 박탈.
        // 결국 hi 후보 = {2}, lo 후보 = {} → seat 2 가 scoop.
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.HIGH_CARD, 9),
            1 to hv(HandCategory.TWO_PAIR, 13, 7),
            2 to hv(HandCategory.ONE_PAIR, 11),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(7, 5, 4, 3, 2),
            1 to lv(8, 7, 6, 4, 3),
            2 to null,
        )
        val decls = mapOf(
            0 to Declaration.SWING,
            1 to Declaration.SWING,
            2 to Declaration.HIGH,
        )
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1, 2),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r).containsExactly(2, 1000L)
    }

    @Test fun declare_swing_vs_high_high_tied_swing_wins_low_swing_takes_both_halves() {
        // seat 0 = SWING (hi 동률, lo 단독), seat 1 = HIGH (hi 동률, no lo).
        // SWING 자격 유지 → hi-pool = {0, 1}, lo-pool = {0}.
        // hi-half(500) 동률 → 250+250, lo-half(500) → seat 0 단독.
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val tied = hv(HandCategory.FLUSH, 14, 9, 7, 5, 3)
        val hi = mapOf(0 to tied, 1 to tied)
        val lo = mapOf<Int, LowValue?>(
            0 to lv(7, 6, 5, 3, 2),
            1 to null,
        )
        val decls = mapOf(0 to Declaration.SWING, 1 to Declaration.HIGH)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r[0]).isEqualTo(250L + 500L)
        assertThat(r[1]).isEqualTo(250L)
    }

    @Test fun declare_only_swings_all_disqualified_falls_back_to_hi_cards_speak() {
        // seat 0 = SWING(약-약), seat 1 = SWING(강-강) — 양쪽 모두 SWING 만 있고,
        // SWING 0 은 양쪽 모두 패배(loBeaten/hiBeaten true) 박탈, SWING 1 은 자기보다 강한 게 없으므로 통과.
        // hi/lo 후보 = {1} → seat 1 scoop.
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.HIGH_CARD, 9),
            1 to hv(HandCategory.TWO_PAIR, 13, 9),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(8, 7, 6, 4, 3),
            1 to lv(7, 5, 4, 3, 2),
        )
        val decls = mapOf(0 to Declaration.SWING, 1 to Declaration.SWING)
        val r = ShowdownResolver.resolve(
            pot, hi, lo,
            seatOrderForOdd = listOf(0, 1),
            hiLoSplit = true,
            declarations = decls,
        )
        assertThat(r).containsExactly(1, 1000L)
    }

    // ---------- 기존 ----------
    @Test fun resolve_all_includes_uncalled_returns() {
        val pot = Pot(2000L, eligibleSeats = setOf(0, 1))
        val result = SidePotResult(
            pots = listOf(pot),
            uncalledReturn = mapOf(2 to 500L),
            deadMoney = 0L,
        )
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val r = ShowdownResolver.resolveAll(
            result, hi,
            seatOrderForOdd = listOf(0, 1, 2),
        )
        assertThat(r[0]).isEqualTo(2000L)
        assertThat(r[2]).isEqualTo(500L)   // uncalled 환급
    }
}
