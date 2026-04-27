package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import org.junit.jupiter.api.Test

/**
 * 노출 카드(upCards) 강도 키 테스트.
 *
 * 정통 7-card stud 룰: 4th street 부터는 가장 강한 노출 핸드 좌석이 first-to-act.
 * 패턴 우선순위 — 포카 > 트리플 > 투페어 > 페어 > 하이카드. 동률 시 그룹 rank, 그래도 동률이면 suit ordinal.
 *
 * **회귀 보호**: 본 테스트들은 evaluatePartial(<5 cards)=null 폴백 버그 (4~6th 스트릿에서 페어/트리플
 * 무시되어 단일 high-card 좌석 잘못 선정) 가 다시 들어오지 않도록 한다.
 */
class StudExposedRankingTest {

    private fun c(suit: Suit, rank: Rank) = Card(suit, rank)

    // 짧은 표기: "AS" → A♠. T=10. ♣C ♦D ♥H ♠S
    private fun card(s: String): Card {
        require(s.length == 2)
        val r = when (s[0]) {
            '2' -> Rank.TWO; '3' -> Rank.THREE; '4' -> Rank.FOUR; '5' -> Rank.FIVE
            '6' -> Rank.SIX; '7' -> Rank.SEVEN; '8' -> Rank.EIGHT; '9' -> Rank.NINE
            'T' -> Rank.TEN;  'J' -> Rank.JACK;  'Q' -> Rank.QUEEN; 'K' -> Rank.KING
            'A' -> Rank.ACE
            else -> error("Bad rank: ${s[0]}")
        }
        val u = when (s[1]) {
            'S' -> Suit.SPADE; 'H' -> Suit.HEART; 'D' -> Suit.DIAMOND; 'C' -> Suit.CLUB
            else -> error("Bad suit: ${s[1]}")
        }
        return Card(u, r)
    }

    private fun ups(vararg s: String): List<Card> = s.map { card(it) }

    private fun key(vararg s: String): List<Int> = StudReducer.exposedStrengthKey(ups(*s))

    private fun strongerThan(left: List<Int>, right: List<Int>): Boolean {
        val n = minOf(left.size, right.size)
        for (i in 0 until n) {
            if (left[i] != right[i]) return left[i] > right[i]
        }
        return left.size > right.size
    }

    // ---------- tier 우선 ----------

    @Test fun pair_of_twos_beats_high_ace_on_4th_street() {
        // 4th street: 2 upcards. 페어 vs 단일 A — 페어가 first-to-act.
        val pair2 = key("2S", "2D")     // tier 1
        val highA = key("AS", "KH")     // tier 0
        assertThat(strongerThan(pair2, highA)).isTrue()
    }

    @Test fun trips_beats_pair_on_5th_street() {
        // 5th street: 3 upcards. 트리플 vs 페어 — 트리플 우선.
        val trips = key("7S", "7D", "7H")    // tier 3
        val pair = key("KS", "KH", "QD")     // tier 1
        assertThat(strongerThan(trips, pair)).isTrue()
    }

    @Test fun two_pair_beats_pair_on_6th_street() {
        // 6th street: 4 upcards. 투페어 vs 페어 — 투페어 우선.
        val twoPair = key("9S", "9D", "5H", "5C")   // tier 2
        val pair = key("AS", "AC", "KH", "QD")      // tier 1
        assertThat(strongerThan(twoPair, pair)).isTrue()
    }

    @Test fun quads_beats_trips_on_6th_street() {
        // 6th street: 4 upcards. 포카 vs 트리플 — 포카 우선 (드물지만 가능).
        val quads = key("8S", "8D", "8H", "8C")     // tier 4
        val trips = key("AS", "AH", "AC", "QD")     // tier 3
        assertThat(strongerThan(quads, trips)).isTrue()
    }

    // ---------- 같은 tier — 그룹 rank 우선 ----------

    @Test fun higher_pair_beats_lower_pair() {
        val pairK = key("KS", "KH")
        val pairQ = key("QS", "QH")
        assertThat(strongerThan(pairK, pairQ)).isTrue()
    }

    @Test fun higher_trips_beats_lower_trips() {
        val tripA = key("AS", "AH", "AD")
        val tripK = key("KS", "KH", "KD")
        assertThat(strongerThan(tripA, tripK)).isTrue()
    }

    @Test fun two_pair_higher_top_pair_wins() {
        // K-K-9-9 vs Q-Q-J-J. K > Q.
        val a = key("KS", "KH", "9D", "9C")
        val b = key("QS", "QH", "JD", "JC")
        assertThat(strongerThan(a, b)).isTrue()
    }

    @Test fun two_pair_same_top_lower_pair_breaks_tie() {
        // K-K-9-9 vs K-K-7-7. 9 > 7.
        val a = key("KS", "KH", "9D", "9C")
        val b = key("KD", "KC", "7H", "7S")
        assertThat(strongerThan(a, b)).isTrue()
    }

    // ---------- 동률 — suit ordinal (♠>♥>♦>♣) ----------

    @Test fun identical_pair_breaks_by_max_suit() {
        // K♠K♣ vs K♥K♦ — 같은 페어, 그룹 랭크 동률, suit 우선.
        // K♠ ordinal = 3, K♥ ordinal = 2. 첫번째가 강.
        val a = key("KS", "KC")
        val b = key("KH", "KD")
        assertThat(strongerThan(a, b)).isTrue()
    }

    @Test fun high_card_tied_rank_breaks_by_suit() {
        // A♠+9♣ vs A♥+9♦. 4th street.
        // tier 0, groupRanks = [14, 9], 마지막 maxSuit:
        // A♠ ordinal 3 → 3, A♥ ordinal 2 → 2. 첫번째가 강.
        val a = key("AS", "9C")
        val b = key("AH", "9D")
        assertThat(strongerThan(a, b)).isTrue()
    }

    // ---------- 1장 (3rd street 후 추가 분배 직전 가설) ----------

    @Test fun single_card_compared_by_rank_then_suit() {
        val ace = key("AS")
        val king = key("KS")
        assertThat(strongerThan(ace, king)).isTrue()
        val aSpade = key("AS")
        val aHeart = key("AH")
        assertThat(strongerThan(aSpade, aHeart)).isTrue()
    }

    // ---------- 빈 입력 ----------

    @Test fun empty_returns_minimal_key() {
        assertThat(StudReducer.exposedStrengthKey(emptyList())).isEqualTo(listOf(-1))
    }

    // ---------- regression: 페어 vs 단일 카드 (이전 버그) ----------

    @Test fun regression_pair_of_twos_beats_single_ace_4th_street() {
        // 회귀: evaluatePartial(2개 cards) == null 이라 단순 max(rank,suit) 만 비교했을 때
        // A 좌석이 페어 좌석을 이기는 버그 케이스.
        val pair2 = key("2C", "2D")     // weakest pair, weakest suits
        val highA = key("AS", "KS")     // strongest singletons
        assertThat(strongerThan(pair2, highA)).isTrue()
    }

    @Test fun regression_pair_of_threes_beats_two_aces_low_suit() {
        // 4th street 두 좌석 모두 4 upcards 가 아니라 2 upcards 인 경우의 회귀.
        // 페어 3 vs A high (2장 모두 high singletons) — 페어 승.
        val pair3 = key("3H", "3D")
        val highA = key("AC", "KC")
        assertThat(strongerThan(pair3, highA)).isTrue()
    }

    @Test fun regression_three_of_kind_in_3_upcards_beats_pair() {
        // 5th street: 트리플 vs 페어 — 트리플 first-to-act (이전엔 evaluatePartial=null fallback).
        val trips = key("3S", "3D", "3H")
        val pairAce = key("AS", "AH", "KD")
        assertThat(strongerThan(trips, pairAce)).isTrue()
    }
}
