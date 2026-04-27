package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.DeclareDirection
import org.junit.jupiter.api.Test

/**
 * 쇼다운 분배기 테스트.
 *
 * 검증:
 *  - High-only 단독 승자
 *  - High-only 동률 분할 + 홀수칩 좌석순
 *  - Hi-Lo 50:50 분할 (8-or-better 모드)
 *  - Hi-Lo 로우 자격자 0 → 하이 단독
 *  - Hi-Lo 사이드팟별 qualifier 독립 (resolveAll)
 *  - 스쿱: 동일인이 hi+lo 모두 → 100%
 *  - 한국식 Declare Hi-Lo 분배 (resolveDeclare)
 */
class ShowdownResolverTest {

    private fun hv(category: HandCategory, vararg tiebreakers: Int) =
        HandValue(category, tiebreakers.toList(), emptyList())

    private fun lv(vararg desc: Int) =
        LowValue(category = 0, tiebreakersDesc = desc.toList(), cards = emptyList())

    private fun lvCat(category: Int, vararg desc: Int) =
        LowValue(category = category, tiebreakersDesc = desc.toList(), cards = emptyList())

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
        val r = ShowdownResolver.resolve(pot, hi, seatOrderForOdd = listOf(2, 0, 1))
        assertThat(r[2]).isEqualTo(334L)
        assertThat(r[0]).isEqualTo(334L)
        assertThat(r[1]).isEqualTo(333L)
        assertThat(r.values.sum()).isEqualTo(1001L)
    }

    // ---------- Hi-Lo (8-or-better 모드용 — 비-Declare) ----------
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
            0 to hv(HandCategory.TWO_PAIR, 13, 7),
            1 to hv(HandCategory.ONE_PAIR, 5),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to null,
            1 to lv(7, 5, 4, 3, 2),
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
        assertThat(r[0]).isEqualTo(501L)
        assertThat(r[1]).isEqualTo(500L)
    }

    @Test fun hi_lo_scoop_same_player_takes_all() {
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT_BACK, 5),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf<Int, LowValue?>(
            0 to lv(5, 4, 3, 2, 1),
            1 to null,
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
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
            1 to lv(7, 5, 4, 3, 2),
            2 to lv(7, 5, 4, 3, 2),
        )
        val r = ShowdownResolver.resolve(pot, hi, lo, hiLoSplit = true)
        assertThat(r[0]).isEqualTo(500L)
        assertThat(r[1]).isEqualTo(250L)
        assertThat(r[2]).isEqualTo(250L)
    }

    // ---------- 사이드팟별 qualifier 독립 (v1.1 §3.4.A) ----------
    @Test fun resolve_all_side_pot_qualifier_independent() {
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
        assertThat(r[2]).isEqualTo(500L)
    }

    // ============================================================================================
    // 한국식 Declare Hi-Lo: resolveDeclare
    // ============================================================================================

    @Test fun declare_two_players_both_one_alone_best_in_both_scoops() {
        // 2명, 둘 다 BOTH 선언, A 가 양방향 단독 1등 → A 가 팟 전체
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),     // 강
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf(
            0 to lv(7, 5, 4, 3, 2),                // 강 (7-low)
            1 to lv(8, 7, 6, 5, 4),                // 약
        )
        val decl = mapOf(
            0 to DeclareDirection.BOTH,
            1 to DeclareDirection.BOTH,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        assertThat(out.payouts).containsExactly(0, 1000L)
        assertThat(out.scoopWinners).containsExactly(0)
        assertThat(out.hiWinners).containsExactly(0)
        assertThat(out.loWinners).containsExactly(0)
    }

    @Test fun declare_two_players_both_tied_hi_fallback_split() {
        // 2명, 둘 다 BOTH, HI 동률 → 둘 다 forfeit
        // 양쪽 후보 모두 비어 fallback: 원래 BOTH 선언자 전원 균등 분배
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 9),
            1 to hv(HandCategory.STRAIGHT, 9),     // HI 동률
        )
        val lo = mapOf(
            0 to lv(7, 5, 4, 3, 2),
            1 to lv(8, 7, 6, 5, 4),
        )
        val decl = mapOf(
            0 to DeclareDirection.BOTH,
            1 to DeclareDirection.BOTH,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        // fallback: 원래 BOTH 둘에게 균등 분배 (500/500)
        assertThat(out.payouts[0]).isEqualTo(500L)
        assertThat(out.payouts[1]).isEqualTo(500L)
        assertThat(out.scoopWinners).isEmpty()
        assertThat(out.hiWinners).isEmpty()
        assertThat(out.loWinners).isEmpty()
    }

    @Test fun declare_three_players_HI_LO_BOTH_scoop() {
        // 3명: A=HI, B=LO, C=BOTH; C 가 양방향 단독 1등 → C 스쿱
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.ONE_PAIR, 7),     // 약
            1 to hv(HandCategory.HIGH_CARD, 14),   // 약
            2 to hv(HandCategory.STRAIGHT, 9),     // 강
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),                // 약
            1 to lv(7, 6, 5, 4, 3),                // 중
            2 to lv(5, 4, 3, 2, 1),                // 휠 (강)
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.LO,
            2 to DeclareDirection.BOTH,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        assertThat(out.payouts).containsExactly(2, 1000L)
        assertThat(out.scoopWinners).containsExactly(2)
    }

    @Test fun declare_three_players_BOTH_ties_LO_forfeits() {
        // 3명: A=HI, B=LO, C=BOTH; C 가 LO 에서 B 와 동률 → C forfeit
        // 결과: A 가 HI 절반, B 가 LO 절반
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),    // A: HI 단독 1등 (HI 후보집합 안에서)
            1 to hv(HandCategory.HIGH_CARD, 14),   // B: 약
            2 to hv(HandCategory.ONE_PAIR, 8),     // C: 중간
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),                // A: 약 (LO 후보 아님)
            1 to lv(7, 5, 4, 3, 2),                // B: 강 (LO 단독 후보 — A 는 HI 선언이라 제외)
            2 to lv(7, 5, 4, 3, 2),                // C: B 와 LO 동률
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.LO,
            2 to DeclareDirection.BOTH,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        // 초기 HI 후보 = {A, C}: A(STRAIGHT) > C(ONE_PAIR) → A 단독 1등
        // 초기 LO 후보 = {B, C}: 동률 → B,C 둘 다 winner
        // C 는 BOTH 인데 LO 에서 단독 1등이 아니라 동률 → forfeit
        // 재계산: HI 후보 = {A}, LO 후보 = {B}
        // A: 500 (+ 홀수칩 0), B: 500
        assertThat(out.payouts[0]).isEqualTo(500L)
        assertThat(out.payouts[1]).isEqualTo(500L)
        assertThat(out.payouts[2]).isNull()
        assertThat(out.hiWinners).containsExactly(0)
        assertThat(out.loWinners).containsExactly(1)
        assertThat(out.scoopWinners).isEmpty()
    }

    @Test fun declare_three_players_HI_LO_normal_split() {
        // 3명: A=HI, B=LO, C=HI (BOTH 없음) — 정상 50:50
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),
            1 to hv(HandCategory.HIGH_CARD, 14),
            2 to hv(HandCategory.ONE_PAIR, 8),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),
            1 to lv(7, 5, 4, 3, 2),
            2 to lv(8, 6, 5, 4, 3),
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.LO,
            2 to DeclareDirection.HI,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        // HI 후보 = {A, C}: A 가 STRAIGHT 로 단독 1등 → 500
        // LO 후보 = {B}: B 단독 → 500
        assertThat(out.payouts[0]).isEqualTo(500L)
        assertThat(out.payouts[1]).isEqualTo(500L)
        assertThat(out.payouts[2]).isNull()
        assertThat(out.hiWinners).containsExactly(0)
        assertThat(out.loWinners).containsExactly(1)
    }

    @Test fun declare_all_HI_takes_entire_pot() {
        // 전원 HI 선언 → LO 후보 0 → 가장 강한 HI 가 팟 전체
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),
            1 to hv(HandCategory.HIGH_CARD, 14),
            2 to hv(HandCategory.ONE_PAIR, 8),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),
            1 to lv(7, 5, 4, 3, 2),
            2 to lv(8, 6, 5, 4, 3),
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.HI,
            2 to DeclareDirection.HI,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        assertThat(out.payouts).containsExactly(0, 1000L)
        assertThat(out.hiWinners).containsExactly(0)
        assertThat(out.loWinners).isEmpty()
    }

    @Test fun declare_all_LO_takes_entire_pot() {
        // 전원 LO 선언 → HI 후보 0 → 가장 강한 LO 가 팟 전체
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1, 2))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),
            1 to hv(HandCategory.HIGH_CARD, 14),
            2 to hv(HandCategory.ONE_PAIR, 8),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),
            1 to lv(7, 5, 4, 3, 2),                // 가장 강
            2 to lv(8, 6, 5, 4, 3),
        )
        val decl = mapOf(
            0 to DeclareDirection.LO,
            1 to DeclareDirection.LO,
            2 to DeclareDirection.LO,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        assertThat(out.payouts).containsExactly(1, 1000L)
        assertThat(out.loWinners).containsExactly(1)
        assertThat(out.hiWinners).isEmpty()
    }

    @Test fun declare_odd_chip_goes_to_hi_side_on_50_50_split() {
        // 정상 50:50 분할에서 홀수칩은 HI 측
        val pot = Pot(1001L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),
            1 to lv(7, 5, 4, 3, 2),
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.LO,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl, seatOrderForOdd = listOf(0, 1))
        assertThat(out.payouts[0]).isEqualTo(501L)  // HI: 500 + 1 odd chip
        assertThat(out.payouts[1]).isEqualTo(500L)
    }

    @Test fun declare_pair_low_loses_to_unpaired_low() {
        // 한국식 LowValue category 검증 — 페어로우는 무페어로우보다 약
        val pot = Pot(1000L, eligibleSeats = setOf(0, 1))
        val hi = mapOf(
            0 to hv(HandCategory.HIGH_CARD, 13),
            1 to hv(HandCategory.HIGH_CARD, 14),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),                // 무페어 (cat 0)
            1 to lvCat(1, 2, 13, 12, 11),          // 페어 2's (cat 1)
        )
        val decl = mapOf(
            0 to DeclareDirection.LO,
            1 to DeclareDirection.LO,
        )
        val out = ShowdownResolver.resolveDeclare(pot, hi, lo, decl)
        // 무페어 8-low(cat 0) > 페어 2-low(cat 1) → seat 0 단독 LO 1등
        assertThat(out.payouts).containsExactly(0, 1000L)
        assertThat(out.loWinners).containsExactly(0)
    }

    // ---------- resolveAllDeclare ----------
    @Test fun resolve_all_declare_aggregates_per_pot_outcomes() {
        val mainPot = Pot(amount = 1000L, eligibleSeats = setOf(0, 1, 2))
        val sidePot = Pot(amount = 2000L, eligibleSeats = setOf(0, 1))
        val result = SidePotResult(
            pots = listOf(mainPot, sidePot),
            uncalledReturn = mapOf(2 to 100L),
            deadMoney = 0L,
        )
        val hi = mapOf(
            0 to hv(HandCategory.STRAIGHT, 10),
            1 to hv(HandCategory.HIGH_CARD, 14),
            2 to hv(HandCategory.ONE_PAIR, 8),
        )
        val lo = mapOf(
            0 to lv(8, 7, 6, 5, 4),
            1 to lv(7, 5, 4, 3, 2),
            2 to lv(8, 6, 5, 4, 3),
        )
        val decl = mapOf(
            0 to DeclareDirection.HI,
            1 to DeclareDirection.LO,
            2 to DeclareDirection.HI,
        )
        val out = ShowdownResolver.resolveAllDeclare(
            result, hi, lo, decl,
            seatOrderForOdd = listOf(0, 1, 2),
        )
        // 메인팟 1000: HI={A,C} A 강 → 500 / LO={B} → 500
        // 사이드팟 2000 (eligible 0,1): HI={A} → 1000 / LO={B} → 1000
        // uncalledReturn: seat 2 += 100
        assertThat(out.payouts[0]).isEqualTo(500L + 1000L)
        assertThat(out.payouts[1]).isEqualTo(500L + 1000L)
        assertThat(out.payouts[2]).isEqualTo(100L)
        assertThat(out.perPotOutcomes).hasSize(2)
        assertThat(out.perPotOutcomes[0].potIndex).isEqualTo(0)
        assertThat(out.perPotOutcomes[0].hiWinners).containsExactly(0)
        assertThat(out.perPotOutcomes[0].loWinners).containsExactly(1)
        assertThat(out.perPotOutcomes[1].potIndex).isEqualTo(1)
        assertThat(out.perPotOutcomes[1].hiWinners).containsExactly(0)
        assertThat(out.perPotOutcomes[1].loWinners).containsExactly(1)
    }
}
