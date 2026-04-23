package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

/**
 * 7스터드 하이로우 (8-or-better) 평가기 테스트.
 * v1.1 §3.4 패치 검증:
 *  - 21조합 모두 페어 검증
 *  - 하이/로우 독립 평가
 *  - 8-or-better qualifier
 *  - 휠(A-2-3-4-5) 가 가장 강한 로우
 */
class HandEvaluatorHiLoTest {

    private fun lo(vararg s: String) = HandEvaluatorHiLo.evaluateLow(hand(*s))
    private fun hi(vararg s: String) = HandEvaluatorHiLo.evaluateHigh(hand(*s))

    // ---------- 휠이 가장 강한 로우 ----------
    @Test fun wheel_is_strongest_low() {
        val v = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")!!
        // A 는 로우에서 1로 사용, 5,4,3,2,1 → tiebreakers
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    @Test fun wheel_beats_8_low() {
        val wheel = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")!!
        val eight = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")!!
        // wheel < eight 이면 wheel 더 강함 (compareTo)
        assertThat(wheel.compareTo(eight)).isLessThan(0)
    }

    @Test fun ace_is_low_in_lowball() {
        val v = lo("AH", "2C", "3D", "4S", "6C", "KH", "QD")!!
        assertThat(v.tiebreakersDesc.last()).isEqualTo(1)   // A=1 가 끝에
    }

    // ---------- qualifier 미달 ----------
    @Test fun no_low_when_no_5_cards_under_8() {
        val v = lo("9S", "TD", "JC", "QH", "KS", "AH", "5C")
        // A,5,9 → 3장만 8 이하 (A,2~8 중 A,5만). 5장 부족 → null
        assertThat(v).isNull()
    }

    @Test fun all_face_cards_no_low() {
        assertThat(lo("KS", "KD", "QC", "QH", "JS", "TC", "9D")).isNull()
    }

    @Test fun pair_in_all_combos_no_low() {
        // 7장에 8 이하만 있어도 모든 5장 조합이 페어 포함이면 null
        // A,A,2,3,4,5,6 → 5장 조합 21가지 중 모든 조합이 A-pair 포함 X
        // (A 1장만 사용한 5장 조합도 가능: A,2,3,4,5 / A,2,3,4,6 등)
        // 그러므로 위 핸드는 휠 가능. 진짜 페어 강제 케이스: 모든 카드가 한 쌍씩.
        val v = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        // 21조합 중 페어 없는 5장이 가능한가? rank set = {2,3,4,5} → 4종, 5장 distinct 불가
        assertThat(v).isNull()
    }

    @Test fun pair_skipped_finds_lower_combo() {
        // 7장: A,2,3,4,5,5,6 → 페어(5) 제외하면 A-2-3-4-5(휠) 또는 A-2-3-4-6 가능
        val v = lo("AS", "2D", "3C", "4H", "5S", "5C", "6D")!!
        // 휠이 가장 강함
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 8 경계 ----------
    @Test fun eight_low_qualifies() {
        val v = lo("8S", "7D", "5C", "3H", "2S", "KH", "QD")!!
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(8, 7, 5, 3, 2))
    }

    @Test fun nine_low_does_not_qualify() {
        val v = lo("9S", "7D", "5C", "3H", "2S", "KH", "QD")
        // 7장에 8 이하 4장만 (7,5,3,2) + 9 → 5장 조합 모두 9 포함 → null
        // 실제로는 9를 빼면 4장뿐이라 5장 조합 불가 → null
        assertThat(v).isNull()
    }

    // ---------- 하이/로우 독립 평가 ----------
    @Test fun straight_in_low_is_ignored() {
        // A-2-3-4-5 는 하이로는 백스트레이트, 로우로는 휠 (스트레이트 자체는 무시)
        val seven = hand("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)!!
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    @Test fun flush_in_low_is_ignored() {
        // A-2-3-4-5 모두 같은 무늬 → 하이는 백SF, 로우는 휠
        val seven = hand("AH", "2H", "3H", "4H", "5H", "KS", "QD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)!!
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_FLUSH_BACK)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- hi/lo 7장에서 다른 5장 사용 가능 ----------
    @Test fun high_and_low_use_different_five_cards() {
        // 7장: A,A,2,3,4,5,K → 하이는 페어AA + 키커, 로우는 A,2,3,4,5(휠)
        val seven = hand("AS", "AH", "2D", "3C", "4H", "5S", "KD")
        val high = HandEvaluatorHiLo.evaluateHigh(seven)
        val low = HandEvaluatorHiLo.evaluateLow(seven)!!
        // 하이는 백스트레이트(strength=5) 가 페어(strength=2)보다 강 → STRAIGHT_BACK
        assertThat(high.category).isEqualTo(HandCategory.STRAIGHT_BACK)
        assertThat(low.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 5장 동률 ----------
    @Test fun two_wheels_are_equal_ignoring_suits() {
        val a = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")!!
        val b = lo("AC", "2H", "3D", "4S", "5C", "KS", "QH")!!
        assertThat(a.compareTo(b)).isEqualTo(0)
    }

    // ---------- 다양한 8-low 비교 ----------
    @Test fun lower_high_card_wins() {
        // 7-low (7,5,4,3,2) vs 8-low (8,5,4,3,2): 7-low 가 강
        val seven = lo("7S", "5D", "4C", "3H", "2S", "KH", "QD")!!
        val eight = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")!!
        assertThat(seven.compareTo(eight)).isLessThan(0)
    }

    @Test fun second_card_breaks_tie() {
        // 둘 다 8-low 이면 두번째 비교
        val a = lo("8S", "5D", "4C", "3H", "2S", "KH", "QD")!!
        val b = lo("8C", "6H", "4D", "3S", "2C", "KS", "QH")!!
        // a: 8,5,4,3,2 / b: 8,6,4,3,2 → a 가 더 낮음(강)
        assertThat(a.compareTo(b)).isLessThan(0)
    }

    @Test fun selects_lowest_low_among_combos() {
        // 7장: 8,7,5,4,3,2,A → 21조합 중 A-2-3-4-5(휠)가 가장 강
        val v = lo("8S", "7D", "5C", "4H", "3S", "2C", "AD")!!
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    // ---------- 7장 미만/초과 거부 ----------
    @Test fun rejects_non_seven_card_input_low() {
        val ex = runCatching { HandEvaluatorHiLo.evaluateLow(hand("AS", "2D", "3C")) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }

    // ---------- 페어 검증이 21조합 모두에 적용 ----------
    @Test fun any_combo_with_pair_disqualifies_that_combo_only() {
        // 7장: A,A,2,3,4,5,7 → A 두 장 중 한 장 + 휠 = 자격 통과
        val v = lo("AS", "AH", "2D", "3C", "4H", "5S", "7D")!!
        // 가장 강한 로우 = 휠
        assertThat(v.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
    }

    @Test fun pair_blocks_when_no_alternative_combo_exists() {
        // 7장에 페어 X 5장 = 8 이하 distinct rank 5장 조합이 0인 경우
        // 2,2,3,3,4,4,5 → distinct rank set = {2,3,4,5} 4종 → 5장 distinct 불가
        val v = lo("2S", "2D", "3C", "3H", "4S", "4C", "5D")
        assertThat(v).isNull()
    }

    // ---------- 하이 평가는 7스터드 한국식 변형 ----------
    @Test fun high_evaluator_uses_korean_back_straight() {
        val v = hi("AS", "2D", "3C", "4H", "5S", "KH", "QD")
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_BACK)
    }

    // ---------- LowValue compareTo 안정성 ----------
    @Test fun low_value_natural_ordering_strongest_first() {
        val wheel = LowValue(listOf(5, 4, 3, 2, 1), emptyList())
        val sevenLow = LowValue(listOf(7, 5, 4, 3, 2), emptyList())
        val eightLow = LowValue(listOf(8, 7, 5, 3, 2), emptyList())
        val sorted = listOf(eightLow, wheel, sevenLow).sorted()
        assertThat(sorted).containsExactly(wheel, sevenLow, eightLow).inOrder()
    }

    // ---------- 회귀: 휠과 6-4 low 비교 ----------
    @Test fun wheel_beats_six_four_low() {
        // 휠 = A,2,3,4,5 / 6-low = 6,4,3,2,A
        val wheel = lo("AS", "2D", "3C", "4H", "5S", "KH", "QD")!!
        val sixFour = lo("6S", "4D", "3C", "2H", "AS", "KH", "QD")!!
        // wheel = (5,4,3,2,1) / sixFour = (6,4,3,2,1)
        assertThat(wheel.tiebreakersDesc).isEqualTo(listOf(5, 4, 3, 2, 1))
        assertThat(sixFour.tiebreakersDesc).isEqualTo(listOf(6, 4, 3, 2, 1))
        assertThat(wheel.compareTo(sixFour)).isLessThan(0)
    }
}
