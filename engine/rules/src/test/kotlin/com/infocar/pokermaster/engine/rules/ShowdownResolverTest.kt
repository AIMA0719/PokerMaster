package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
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
