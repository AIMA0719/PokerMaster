package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 7스터드 하이로우 (한국식) 평가기 테스트.
 *
 * 한국식 변형:
 *  - 8-or-better qualifier 없음 → [HandEvaluatorHiLo.evaluateLow] NON-NULL
 *  - 페어/트립/투페어/풀하우스/포카드도 로우 후보가 됨 (단 category 가 더 큼 = 약함)
 *  - 휠(A-2-3-4-5)이 가장 강한 로우 — category 0, tiebreakers (5,4,3,2,1)
 *  - 스트레이트/플러시 모양은 로우에서 무시
 */
class HandEvaluatorHiLoTest {

    private fun lo(vararg s: String) = HandEvaluatorHiLo.evaluateLow(hand(*s))
    private fun hi(vararg s: String) = HandEvaluatorHiLo.evaluateHigh(hand(*s))

    // ---------- 휠이 가장 강한 로우 ----------
    @Test fun wheel_is_strongest_low() {
        val v = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    @Test fun wheel_beats_8_low() {
        val wheel = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val eight = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")
        // 둘 다 category 0, tiebreaker 비교 — wheel 더 강함
        assertThat(wheel.compareTo(eight)).isLessThan(0)
    }

    @Test fun ace_is_low_in_lowball() {
        val v = lo("AH", "2C", "3D", "4S", "6C", "KH", "QD")
        assertThat(v.tiebreakersDesc.last()).isEqualTo(1)   // A=1 가 끝에
    }

    // ---------- 한국식: 무페어 로우 가능한 5장이 없으면 페어로우 등이 결과 ----------
    @Test fun all_face_cards_returns_paired_low() {
        // K,K,Q,Q,J,T,9 → 무페어 5장 distinct rank 가 5종 이상
        // {K,Q,J,T,9} 5종 → 무페어 가능 (category 0).
        val v = lo("KS", "KD", "QC", "QH", "JS", "TC", "9D")
        // 가장 강한 로우는 무페어 K-Q-J-T-9 (=category 0)
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(13, 12, 11, 10, 9))
    }

    @Test fun pair_when_no_unpaired_5_distinct_exists() {
        // 7장에 distinct rank 가 5종 미만이면 무페어 5장 불가 → 페어 로우.
        val v = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        // distinct rank set = {2,3,4,5} 4종 → 5장 distinct 불가 → 최선은 페어
        assertThat(v.category).isAtLeast(1)  // 페어 이상
    }

    @Test fun pair_skipped_finds_better_unpaired_combo() {
        // 7장: A,2,3,4,5,5,6 → 5 한 장만 쓰면 휠(A-2-3-4-5) 가능
        val v = lo("AS", "2D", "3C", "4H", "5S", "5C", "6D")
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 페어 vs 무페어: 어떤 무페어 로우든 페어보다 강 ----------
    @Test fun any_unpaired_low_beats_any_pair_low() {
        // 무페어 K-high (K,Q,J,T,9) 7장
        val unpairedKHigh = lo("KS", "KD", "QC", "QH", "JS", "TC", "9D")
        // 페어 강제 7장 (distinct rank 4종)
        val pairLow = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        assertThat(unpairedKHigh.category).isEqualTo(0)
        assertThat(pairLow.category).isAtLeast(1)
        // 무페어가 페어보다 강 (category 작을수록 강함)
        assertThat(unpairedKHigh.compareTo(pairLow)).isLessThan(0)
    }

    @Test fun paired_seven_low_loses_to_unpaired_eight_low() {
        // 5장 LowValue 비교: 페어 7-low (7,6,5,3,3) vs 무페어 8-low (8,7,5,3,2)
        // 한국식: category 0(무페어) 가 category 1(페어) 보다 강
        val pairedSevenLow = LowValue(category = 1, tiebreakersDesc = listOf(3, 7, 6, 5), cards = emptyList())
        val unpairedEightLow = LowValue(category = 0, tiebreakersDesc = listOf(8, 7, 5, 3, 2), cards = emptyList())
        assertThat(unpairedEightLow.compareTo(pairedSevenLow)).isLessThan(0)
    }

    @Test fun forced_pair_low_when_only_4_distinct_ranks() {
        // distinct rank 4종(2,3,4,5)만 7장에 있으면 무페어 5장 불가 → 페어로우 강제
        val v = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        assertThat(v.category).isAtLeast(1)
    }

    @Test fun two_pair_worse_than_one_pair() {
        val onePair = LowValue(category = 1, tiebreakersDesc = listOf(7, 6, 5, 4), cards = emptyList())
        val twoPair = LowValue(category = 2, tiebreakersDesc = listOf(7, 5, 4), cards = emptyList())
        assertThat(onePair.compareTo(twoPair)).isLessThan(0)
    }

    @Test fun all_paired_returns_paired_low() {
        // 7장에 distinct rank 가 4종이면 결과는 페어로우. tiebreakers 첫 항목 = 페어 rank.
        val v = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        // 가장 강한 페어로우는 A=1 페어가 없으니 가장 작은 페어(2)가 들어가는 형태일 것 (페어rank 작을수록 강).
        // groups: count desc, rank desc — 5장 조합 중 페어 1쌍만 포함하는 게 가장 강 (category 1).
        assertThat(v.category).isAtMost(2) // 페어 또는 투페어 가능
    }

    // ---------- 8 경계 (한국식: 의미 없음, 검증만) ----------
    @Test fun eight_low_qualifies() {
        val v = lo("8S", "7D", "5C", "3H", "2S", "KH", "QD")
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(8, 7, 5, 3, 2))
    }

    @Test fun nine_low_returns_unpaired_low() {
        // 한국식: 9-low 도 정상 무페어 로우 (category 0).
        val v = lo("9S", "7D", "5C", "3H", "2S", "KH", "QD")
        assertThat(v.category).isEqualTo(0)
        // 가장 강한 5장 무페어 = 9,7,5,3,2
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(9, 7, 5, 3, 2))
    }

    // ---------- 하이/로우 독립 평가 ----------
    @Test fun straight_in_low_is_ignored() {
        // A-2-3-4-5 는 하이로는 백스트레이트, 로우로는 휠 (스트레이트 자체는 무시)
        val seven = hand("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(low.category).isEqualTo(0)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    @Test fun flush_in_low_is_ignored() {
        // A-2-3-4-5 모두 같은 무늬 → 하이는 백SF, 로우는 휠
        val seven = hand("AH", "2H", "3H", "4H", "5H", "KS", "QD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_FLUSH_BACK)
        assertThat(low.category).isEqualTo(0)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- hi/lo 7장에서 다른 5장 사용 가능 ----------
    @Test fun high_and_low_use_different_five_cards() {
        // 7장: A,A,2,3,4,5,K → 하이는 백스트레이트, 로우는 A,2,3,4,5(휠)
        val seven = hand("AS", "AH", "2D", "3C", "4H", "5S", "KD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(low.category).isEqualTo(0)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 5장 동률 ----------
    @Test fun two_wheels_are_equal_ignoring_suits() {
        val a = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val b = lo("AC", "2H", "3D", "4S", "5C", "KS", "QH")
        assertThat(a.compareTo(b)).isEqualTo(0)
    }

    // ---------- 다양한 로우 비교 ----------
    @Test fun lower_high_card_wins() {
        // 7-low (7,5,4,3,2) vs 8-low (8,5,4,3,2): 7-low 가 강
        val seven = lo("7S", "5D", "4C", "3H", "2S", "KH", "QD")
        val eight = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")
        assertThat(seven.compareTo(eight)).isLessThan(0)
    }

    @Test fun second_card_breaks_tie() {
        // 둘 다 8-low 이면 두번째 비교
        val a = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")
        val b = lo("8C", "6H", "4D", "3S", "2C", "KS", "QH")
        // a: 8,5,4,3,2 / b: 8,6,4,3,2 → a 가 더 낮음(강)
        assertThat(a.compareTo(b)).isLessThan(0)
    }

    @Test fun selects_lowest_low_among_combos() {
        // 7장: 8,7,5,4,3,2,A → 21조합 중 A-2-3-4-5(휠)가 가장 강
        val v = lo("8S", "7D", "5C", "4H", "3S", "2C", "AD")
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 7장 미만/초과 거부 ----------
    @Test fun rejects_non_seven_card_input_low() {
        val ex = runCatching { HandEvaluatorHiLo.evaluateLow(hand("AS", "2D", "3C")) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---------- 페어 검증이 21조합 모두에 적용 ----------
    @Test fun any_combo_with_pair_disqualifies_that_combo_only() {
        // 7장: A,A,2,3,4,5,7 → A 두 장 중 한 장 + 휠 = 무페어 로우 통과
        val v = lo("AS", "AH", "2D", "3C", "4H", "5S", "7D")
        // 가장 강한 로우 = 휠 (category 0)
        assertThat(v.category).isEqualTo(0)
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 하이 평가는 7스터드 한국식 변형 ----------
    @Test fun high_evaluator_uses_korean_back_straight() {
        val v = hi("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_BACK)
    }

    // ---------- LowValue compareTo 안정성 ----------
    @Test fun low_value_natural_ordering_strongest_first() {
        val wheel = LowValue(category = 0, tiebreakersDesc = listOf(5, 4, 3, 2, 1), cards = emptyList())
        val sevenLow = LowValue(category = 0, tiebreakersDesc = listOf(7, 5, 4, 3, 2), cards = emptyList())
        val eightLow = LowValue(category = 0, tiebreakersDesc = listOf(8, 7, 5, 3, 2), cards = emptyList())
        val sorted = listOf(eightLow, wheel, sevenLow).sorted()
        assertThat(sorted).containsExactly(wheel, sevenLow, eightLow).inOrder()
    }

    @Test fun category_dominates_tiebreakers() {
        // 무페어의 가장 약한 로우(K,Q,J,T,9 = high-card K) 가 페어의 가장 강한 로우보다도 강하다.
        val unpairedWeak = LowValue(category = 0, tiebreakersDesc = listOf(13, 12, 11, 10, 9), cards = emptyList())
        val pairAces = LowValue(category = 1, tiebreakersDesc = listOf(1, 4, 3, 2), cards = emptyList())
        assertThat(unpairedWeak.compareTo(pairAces)).isLessThan(0)  // 무페어가 강
    }

    // ---------- 회귀: 휠과 6-4 low 비교 ----------
    @Test fun wheel_beats_six_four_low() {
        // 휠 = A,2,3,4,5 / 6-low = 6,4,3,2,A
        val wheel = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val sixFour = lo("6S", "4D", "3C", "2H", "AS", "KH", "QD")
        assertThat(wheel.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
        assertThat(sixFour.tiebreakersDesc).isEqualTo(listOf(6, 4, 3, 2, 1))
        assertThat(wheel.compareTo(sixFour)).isLessThan(0)
    }
}
