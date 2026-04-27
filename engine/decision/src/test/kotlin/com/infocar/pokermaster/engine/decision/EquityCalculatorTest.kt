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

    // ---------- HiLo 가중치 (M7-BugFix 회귀) ----------
    @Test fun hi_lo_share_full_pot_to_hi_when_no_one_qualifies() {
        val share = EquityCalculator.hiLoIterationShare(
            hiShare = 1.0, loShare = 0.0,
            myLoQualified = false, anyOpLoQualified = false,
        )
        // 아무도 lo qualify 못 하면 본인이 hi 단독 승 시 팟 100%.
        assertThat(share).isEqualTo(1.0)
    }

    @Test fun hi_lo_share_split_when_my_lo_qualifies() {
        val share = EquityCalculator.hiLoIterationShare(
            hiShare = 1.0, loShare = 1.0,
            myLoQualified = true, anyOpLoQualified = false,
        )
        // hi+lo scoop = 50% + 50% = 100%
        assertThat(share).isEqualTo(1.0)
    }

    @Test fun hi_lo_share_split_when_only_opponent_lo_qualifies() {
        val share = EquityCalculator.hiLoIterationShare(
            hiShare = 1.0, loShare = 0.0,
            myLoQualified = false, anyOpLoQualified = true,
        )
        // 상대가 lo 가져가므로 본인은 hi 절반만 차지.
        assertThat(share).isEqualTo(0.5)
    }

    @Test fun hi_lo_share_zero_hi_with_only_op_qualifier() {
        val share = EquityCalculator.hiLoIterationShare(
            hiShare = 0.0, loShare = 0.0,
            myLoQualified = false, anyOpLoQualified = true,
        )
        assertThat(share).isEqualTo(0.0)
    }

    // ---------- 한국식 Hi-Lo Declare equity ----------
    //
    // [hi/lo/both] 는 같은 시드 + 1500 iteration 으로 평균 ±0.07 변동 허용.
    // 상대 1명 가정 (총 14장 known + random fill). 8-or-better qualifier 없음.

    private fun deckMinus(used: List<com.infocar.pokermaster.core.model.Card>) =
        com.infocar.pokermaster.core.model.standardDeck() - used.toSet()

    @Test fun korean_hi_lo_strong_hi_only_trips_high_hi_low_low_both_near_zero() {
        // 트리플 K + broadway: hi 매우 강함. lo 는 9 이상 카드만 → 매우 약함. both 는 0 근처.
        val mine = hand("KS", "KH", "KD", "QC", "JS", "TH", "9D")
        val oppUp = hand("2C", "5D", "7H", "8S")  // 상대 4 up — 약한 hi 와중 lo 후보
        val deck = deckMinus(mine + oppUp)
        val de = calc().declareEquity(mine, listOf(oppUp), deck, iterations = 1_500)
        assertThat(de.hiEquity).isAtLeast(0.7)
        assertThat(de.loEquity).isAtMost(0.3)
        assertThat(de.bothEquity).isAtMost(0.15)
    }

    @Test fun korean_hi_lo_wheel_with_pair_dominates_lo_weak_hi() {
        // 6-low (6-4-3-2-A) + 2H 페어. lo 는 강함 (6-low), hi 는 페어 — 약함.
        // 주의: 휠(A-2-3-4-5) 자체는 hi 백스트레이트로 의외로 강하니 lo-only 약 hi 시연 위해 6-4-3-2-A 사용.
        val mine = hand("AS", "2D", "3C", "4H", "6S", "2H", "9C")
        val oppUp = hand("KH", "QC", "JD", "TS")  // 상대 broadway up — hi 유리
        val deck = deckMinus(mine + oppUp)
        val de = calc().declareEquity(mine, listOf(oppUp), deck, iterations = 1_500)
        // 6-low 는 lo 최강 후보 — 거의 항상 단독 1위 또는 동률
        assertThat(de.loEquity).isAtLeast(0.6)
        // hi 는 한 페어 (2 pair) — 상대 broadway 페어/스트레이트 상대로 약함
        assertThat(de.hiEquity).isAtMost(0.4)
        // both 는 0 근처 (hi 가 약하므로 scoop 사실상 불가)
        assertThat(de.bothEquity).isAtMost(0.15)
    }

    @Test fun korean_hi_lo_wheel_plus_trips_strong_both_directions() {
        // 트리플 5 + 휠 (5-4-3-2-A 동시 포함): hi 트리플 5, lo 휠. 양방향 매우 강함.
        // 5 트리플 + 휠 구성: 5S 5H 5D + A 2 3 4 = 7 장.
        val mine = hand("5S", "5H", "5D", "AC", "2D", "3H", "4S")
        val oppUp = hand("KC", "QH", "JS", "TD")
        val deck = deckMinus(mine + oppUp)
        val de = calc().declareEquity(mine, listOf(oppUp), deck, iterations = 1_500)
        // hi 트리플 5: 상대 random fill 시 셋/스트레이트/플러시에 종종 패배하나 평균 우위.
        assertThat(de.hiEquity).isAtLeast(0.5)
        // lo 휠: 거의 항상 단독 1위.
        assertThat(de.loEquity).isAtLeast(0.7)
        // both: 두 방향 모두 단독 1위 확률 — 0.4+ 기대.
        assertThat(de.bothEquity).isAtLeast(0.4)
    }

    @Test fun korean_hi_lo_weak_hi_weak_lo_all_low() {
        // 본인 약한 hi (9 high) + 약한 lo (8 high). 상대 1명 random.
        val mine = hand("9S", "8H", "7D", "5C", "3D", "TH", "KC")
        val oppUp = hand("AC", "AS", "KD", "QH")  // 상대 강한 up — AA 시나리오
        val deck = deckMinus(mine + oppUp)
        val de = calc().declareEquity(mine, listOf(oppUp), deck, iterations = 1_500)
        // 상대 AA up 강함 — hi 패배 빈번
        assertThat(de.hiEquity).isAtMost(0.4)
        // 상대도 lo 가 fill 으로 만들어질 수 있음 — lo 도 압도적 아님
        assertThat(de.loEquity).isAtMost(0.7)
        // both 는 strict — 매우 낮음
        assertThat(de.bothEquity).isAtMost(0.2)
    }

    @Test fun korean_hi_lo_optimal_declare_returns_max_pot_share() {
        // optimalDeclareEquity 산식 검증.
        val de1 = DeclareEquity(hiEquity = 0.8, loEquity = 0.1, bothEquity = 0.05)
        // HI 단독 0.4 vs LO 0.05 vs BOTH 0.05 → HI 우세
        assertThat(EquityCalculator.optimalDeclareEquity(de1)).isWithin(1e-9).of(0.4)

        val de2 = DeclareEquity(hiEquity = 0.9, loEquity = 0.9, bothEquity = 0.7)
        // HI 0.45 vs LO 0.45 vs BOTH 0.7 → BOTH 우세
        assertThat(EquityCalculator.optimalDeclareEquity(de2)).isWithin(1e-9).of(0.7)

        val de3 = DeclareEquity(hiEquity = 0.0, loEquity = 0.0, bothEquity = 0.0)
        assertThat(EquityCalculator.optimalDeclareEquity(de3)).isEqualTo(0.0)
    }

    // ---------- 회귀: 비-HiLo 모드 unchanged ----------
    @Test fun regression_holdem_aces_equity_unchanged() {
        // AA vs random 1명: 약 85% — Hi-Lo 변경에 영향 없어야 함.
        val eq = calc().holdemEquity(hand("AS", "AH"), opponents = 1, iterations = 3_000)
        within(eq, 0.85, 0.04)
    }

    @Test fun regression_seven_stud_non_hilo_unchanged() {
        // 트리플 A + broadway: Hi-only 7스터드에서도 매우 강함.
        val mySeven = hand("AS", "AH", "AD", "KC", "QH", "JS", "TD")
        val opp = listOf(hand("2C", "5D", "7H"))
        val eq = calc().sevenStudEquity(mySeven, opp, iterations = 500, hiLoSplit = false)
        assertThat(eq).isAtLeast(0.7)
    }
}
