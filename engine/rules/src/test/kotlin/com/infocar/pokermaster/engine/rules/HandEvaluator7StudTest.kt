package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 한국식 7스터드 평가기 테스트.
 * 핵심: 백스트레이트(A-2-3-4-5)는 일반 스트레이트보다 약, 백SF는 일반 SF보다 약 (v1.1 §2.2.4).
 */
class HandEvaluator7StudTest {

    private fun eval(vararg s: String): HandValue =
        HandEvaluator7Stud.evaluateBest(hand(*s))

    // ---------- 백스트레이트 (한국식 핵심) ----------
    @Test fun back_straight_is_separate_category() {
        val v = eval("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(v.tiebreakers).isEqualTo(listOf(5))
    }

    @Test fun back_straight_loses_to_6_high_straight() {
        val back = eval("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val six = eval("6S", "5D", "4C", "3H", "2S", "KH", "QD")
        assertThat(six).isGreaterThan(back)
    }

    @Test fun back_straight_beats_three_of_a_kind() {
        val back = eval("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        // 트리플 7 + 키커 K,9,2 — 마운틴/스트레이트 차단 (T,J,Q 없음)
        val trips = eval("7S", "7H", "7D", "KS", "9H", "2D", "3C")
        // 한국식 룰: STRAIGHT_BACK(strength=5) > THREE_OF_A_KIND(strength=4)
        assertThat(trips.category).isEqualTo(HandCategory.THREE_OF_A_KIND)
        assertThat(back).isGreaterThan(trips)
    }

    @Test fun back_straight_loses_to_any_normal_straight_above_6_high() {
        val back = eval("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val seven = eval("7S", "6D", "5C", "4H", "3S", "KH", "QD")
        val mountain = eval("AS", "KS", "QD", "JC", "TH", "2H", "3D")
        assertThat(seven).isGreaterThan(back)
        assertThat(mountain).isGreaterThan(back)
    }

    @Test fun back_straight_flush_is_separate_category() {
        val v = eval("AC", "2C", "3C", "4C", "5C", "KH", "QD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_FLUSH_BACK)
        assertThat(v.tiebreakers).isEqualTo(listOf(5))
    }

    @Test fun back_straight_flush_loses_to_normal_straight_flush() {
        val back = eval("AH", "2H", "3H", "4H", "5H", "KS", "QD")
        val nine = eval("9D", "8D", "7D", "6D", "5D", "AS", "KH")
        assertThat(nine).isGreaterThan(back)
    }

    @Test fun back_straight_flush_beats_full_house() {
        val back = eval("AH", "2H", "3H", "4H", "5H", "KS", "QD")
        val fh = eval("AS", "AD", "AC", "KS", "KH", "2D", "3C")
        assertThat(back).isGreaterThan(fh)
    }

    @Test fun back_straight_flush_beats_four_of_a_kind() {
        val back = eval("AH", "2H", "3H", "4H", "5H", "KS", "QD")
        val quads = eval("KS", "KH", "KD", "KC", "AS", "QD", "JC")
        // STRAIGHT_FLUSH_BACK strength=10 > FOUR_OF_A_KIND strength=9
        assertThat(back).isGreaterThan(quads)
    }

    @Test fun two_back_straights_split() {
        val a = eval("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val b = eval("AC", "2H", "3D", "4S", "5C", "KS", "QH")
        assertThat(a.compareTo(b)).isEqualTo(0)
    }

    // ---------- 마운틴 ----------
    @Test fun mountain_is_strongest_normal_straight() {
        val v = eval("AS", "KS", "QD", "JC", "TH", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers).isEqualTo(listOf(14))
    }

    @Test fun mountain_loses_to_flush() {
        val m = eval("AS", "KS", "QD", "JC", "TH", "2H", "3D")
        val f = eval("AH", "QH", "9H", "5H", "2H", "3D", "4C")
        assertThat(f).isGreaterThan(m)
    }

    // ---------- 표준 카테고리 회귀 ----------
    @Test fun royal_flush_basic() {
        val v = eval("AS", "KS", "QS", "JS", "TS", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.ROYAL_FLUSH)
    }

    @Test fun straight_flush_9_high() {
        val v = eval("9H", "8H", "7H", "6H", "5H", "AS", "KD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_FLUSH)
        assertThat(v.tiebreakers).isEqualTo(listOf(9))
    }

    @Test fun four_of_a_kind() {
        val v = eval("KS", "KH", "KD", "KC", "9S", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.FOUR_OF_A_KIND)
    }

    @Test fun full_house() {
        val v = eval("9S", "9H", "9D", "4C", "4S", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.FULL_HOUSE)
    }

    @Test fun flush() {
        val v = eval("AH", "KH", "9H", "5H", "2H", "3D", "4C")
        assertThat(v.category).isEqualTo(HandCategory.FLUSH)
    }

    @Test fun straight() {
        val v = eval("9D", "8C", "7H", "6S", "5D", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers).isEqualTo(listOf(9))
    }

    @Test fun three_of_a_kind() {
        val v = eval("9S", "9H", "9D", "5C", "2S", "3H", "4D")
        assertThat(v.category).isEqualTo(HandCategory.THREE_OF_A_KIND)
    }

    @Test fun two_pair() {
        val v = eval("KS", "KH", "5D", "5C", "2S", "3H", "4D")
        assertThat(v.category).isEqualTo(HandCategory.TWO_PAIR)
    }

    @Test fun one_pair() {
        val v = eval("9S", "9H", "AS", "KC", "QD", "2H", "3D")
        assertThat(v.category).isEqualTo(HandCategory.ONE_PAIR)
    }

    @Test fun high_card() {
        // 4 빼서 A-2-3-4-5 백스트레이트 차단, K-J-9-7-5 도 스트레이트 아님
        val v = eval("AS", "JH", "9D", "5C", "3S", "2H", "KD")
        assertThat(v.category).isEqualTo(HandCategory.HIGH_CARD)
    }

    // ---------- 카테고리 정렬 검증 (한국식 포함) ----------
    @Test fun korean_category_order_strict() {
        val ordered = listOf(
            HandCategory.HIGH_CARD,
            HandCategory.ONE_PAIR,
            HandCategory.TWO_PAIR,
            HandCategory.THREE_OF_A_KIND,
            HandCategory.STRAIGHT_BACK,
            HandCategory.STRAIGHT,
            HandCategory.FLUSH,
            HandCategory.FULL_HOUSE,
            HandCategory.FOUR_OF_A_KIND,
            HandCategory.STRAIGHT_FLUSH_BACK,
            HandCategory.STRAIGHT_FLUSH,
            HandCategory.ROYAL_FLUSH,
        )
        val strengths = ordered.map { it.strength }
        assertThat(strengths).isInOrder()
    }

    // ---------- 7장 베스트 선택 ----------
    @Test fun chooses_back_straight_over_pair_when_both_present() {
        // 7장: 백스트레이트 5장 + 페어 가능
        val v = eval("AS", "2D", "3C", "4H", "5S", "5C", "KD")
        // STRAIGHT_BACK (strength=5) > ONE_PAIR (strength=2)
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_BACK)
    }

    @Test fun chooses_normal_straight_over_back_straight_when_both_present() {
        // 7장에 A,2,3,4,5,6 → 6-high 일반 straight 선택 (백스트레이트보다 강)
        val v = eval("AS", "2D", "3C", "4H", "5S", "6C", "KD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers).isEqualTo(listOf(6))
    }

    // ---------- partial 평가 ----------
    @Test fun partial_returns_null_below_5_cards() {
        assertThat(HandEvaluator7Stud.evaluatePartial(hand("AS", "AH", "AC", "KS"))).isNull()
    }

    @Test fun partial_works_for_5_cards() {
        val v = HandEvaluator7Stud.evaluatePartial(hand("AS", "AH", "AD", "KS", "KH"))!!
        assertThat(v.category).isEqualTo(HandCategory.FULL_HOUSE)
    }

    @Test fun partial_works_for_6_cards() {
        val v = HandEvaluator7Stud.evaluatePartial(hand("AS", "AH", "AD", "KS", "KH", "5C"))!!
        assertThat(v.category).isEqualTo(HandCategory.FULL_HOUSE)
    }

    @Test fun partial_works_for_7_cards() {
        val v = HandEvaluator7Stud.evaluatePartial(hand("AS", "AH", "AD", "KS", "KH", "5C", "2D"))!!
        assertThat(v.category).isEqualTo(HandCategory.FULL_HOUSE)
    }

    // ---------- 추가 키커 회귀 ----------
    @Test fun two_pair_kicker_breaks_tie() {
        val a = eval("KS", "KH", "9D", "9C", "AS", "2D", "3H")
        val b = eval("KC", "KD", "9S", "9H", "QC", "2H", "3D")
        assertThat(a).isGreaterThan(b)
    }

    @Test fun trip_aces_beats_trip_kings() {
        val a = eval("AS", "AH", "AD", "KC", "QS", "2D", "3H")
        val b = eval("KS", "KH", "KD", "AC", "QS", "2D", "3H")
        assertThat(a).isGreaterThan(b)
    }

    @Test fun split_with_identical_seven_card_holdings() {
        val a = eval("AS", "KH", "QD", "JC", "9S", "5H", "2D")
        val b = eval("AC", "KD", "QH", "JS", "9D", "5S", "2H")
        // 양쪽 모두 동일 베스트 5장 (A high)
        assertThat(a.compareTo(b)).isEqualTo(0)
    }

    // ---------- 에지 ----------
    @Test fun straight_picks_highest_when_six_consecutive() {
        // 5,6,7,8,9,10 → 10-high straight 선택
        val v = eval("5S", "6D", "7C", "8H", "9S", "TC", "2D")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers).isEqualTo(listOf(10))
    }

    @Test fun back_straight_with_extra_low_pair_ignored() {
        // 백스트레이트 + 추가 페어 → 백스트레이트 (strength=5) > 투페어 (strength=3) ?
        // 실제로는 strength 비교: STRAIGHT_BACK(5) > TWO_PAIR(3) → 백스트레이트
        val v = eval("AS", "2D", "3C", "4H", "5S", "5C", "AC")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_BACK)
    }

    @Test fun flush_with_back_straight_potential_picks_normal_flush() {
        // 모두 같은 무늬 + A-2-3-4-5 + 다른 카드 → 플러시 우선? 아니, 같은 무늬면 백SF.
        // 일반 플러시 vs 백SF: 백SF (strength=10) > FLUSH (strength=7) → 백SF.
        val v = eval("AH", "2H", "3H", "4H", "5H", "9S", "TS")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_FLUSH_BACK)
    }
}
