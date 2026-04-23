package com.infocar.pokermaster.engine.rules

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import org.junit.jupiter.api.Test

class HandEvaluatorHoldemTest {

    private fun c(r: Rank, s: Suit) = Card(s, r)

    // ---------- 카테고리별 인식 ----------

    @Test fun `royal flush is highest`() {
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE), c(Rank.KING, Suit.SPADE),
            c(Rank.QUEEN, Suit.SPADE), c(Rank.JACK, Suit.SPADE), c(Rank.TEN, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.THREE, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.ROYAL_FLUSH)
    }

    @Test fun `straight flush 9-high`() {
        val seven = listOf(
            c(Rank.NINE, Suit.HEART), c(Rank.EIGHT, Suit.HEART),
            c(Rank.SEVEN, Suit.HEART), c(Rank.SIX, Suit.HEART), c(Rank.FIVE, Suit.HEART),
            c(Rank.ACE, Suit.SPADE), c(Rank.KING, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_FLUSH)
        assertThat(v.tiebreakers[0]).isEqualTo(9)
    }

    @Test fun `wheel A-2-3-4-5 in holdem is 5-high straight flush`() {
        val seven = listOf(
            c(Rank.ACE, Suit.HEART), c(Rank.TWO, Suit.HEART),
            c(Rank.THREE, Suit.HEART), c(Rank.FOUR, Suit.HEART), c(Rank.FIVE, Suit.HEART),
            c(Rank.KING, Suit.CLUB), c(Rank.QUEEN, Suit.SPADE),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT_FLUSH)
        assertThat(v.tiebreakers[0]).isEqualTo(5)
    }

    @Test fun `four of a kind`() {
        val seven = listOf(
            c(Rank.SEVEN, Suit.SPADE), c(Rank.SEVEN, Suit.HEART),
            c(Rank.SEVEN, Suit.DIAMOND), c(Rank.SEVEN, Suit.CLUB),
            c(Rank.ACE, Suit.SPADE), c(Rank.TWO, Suit.HEART), c(Rank.THREE, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.FOUR_OF_A_KIND)
        assertThat(v.tiebreakers).isEqualTo(listOf(7, 14))   // quad 7, kicker A
    }

    @Test fun `full house picks higher trip`() {
        val seven = listOf(
            c(Rank.NINE, Suit.SPADE), c(Rank.NINE, Suit.HEART), c(Rank.NINE, Suit.DIAMOND),
            c(Rank.FOUR, Suit.SPADE), c(Rank.FOUR, Suit.HEART),
            c(Rank.TWO, Suit.SPADE), c(Rank.THREE, Suit.HEART),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.FULL_HOUSE)
        assertThat(v.tiebreakers).isEqualTo(listOf(9, 4))
    }

    @Test fun `flush ace high`() {
        val seven = listOf(
            c(Rank.ACE, Suit.CLUB), c(Rank.JACK, Suit.CLUB), c(Rank.NINE, Suit.CLUB),
            c(Rank.SIX, Suit.CLUB), c(Rank.TWO, Suit.CLUB),
            c(Rank.SEVEN, Suit.SPADE), c(Rank.EIGHT, Suit.HEART),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.FLUSH)
        assertThat(v.tiebreakers).isEqualTo(listOf(14, 11, 9, 6, 2))
    }

    @Test fun `straight 6-high`() {
        val seven = listOf(
            c(Rank.SIX, Suit.SPADE), c(Rank.FIVE, Suit.HEART),
            c(Rank.FOUR, Suit.DIAMOND), c(Rank.THREE, Suit.CLUB), c(Rank.TWO, Suit.SPADE),
            c(Rank.ACE, Suit.HEART), c(Rank.KING, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers[0]).isEqualTo(6)
    }

    @Test fun `wheel straight 5-high`() {
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE), c(Rank.TWO, Suit.HEART),
            c(Rank.THREE, Suit.DIAMOND), c(Rank.FOUR, Suit.CLUB), c(Rank.FIVE, Suit.SPADE),
            c(Rank.KING, Suit.HEART), c(Rank.QUEEN, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        // 6-high straight 가 더 강하므로 휠은 못 만든다 — 본 테스트는 명시적 제외 케이스
        // 위 카드는 6 이 없어 휠 자체가 베스트가 됨? K Q A 는 스트레이트 미연결.
        // A-2-3-4-5 와 Q-K-A (3장만) 비교 → 휠 우선
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers[0]).isEqualTo(5)
    }

    @Test fun `mountain straight A-K-Q-J-10`() {
        val seven = listOf(
            c(Rank.TEN, Suit.SPADE), c(Rank.JACK, Suit.HEART),
            c(Rank.QUEEN, Suit.DIAMOND), c(Rank.KING, Suit.CLUB), c(Rank.ACE, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.THREE, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.STRAIGHT)
        assertThat(v.tiebreakers[0]).isEqualTo(14)
    }

    @Test fun `three of a kind picks high kickers`() {
        val seven = listOf(
            c(Rank.JACK, Suit.SPADE), c(Rank.JACK, Suit.HEART), c(Rank.JACK, Suit.DIAMOND),
            c(Rank.ACE, Suit.SPADE), c(Rank.KING, Suit.HEART),
            c(Rank.TWO, Suit.CLUB), c(Rank.THREE, Suit.SPADE),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.THREE_OF_A_KIND)
        assertThat(v.tiebreakers).isEqualTo(listOf(11, 14, 13))
    }

    @Test fun `two pair picks two highest pairs`() {
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE), c(Rank.ACE, Suit.HEART),
            c(Rank.KING, Suit.SPADE), c(Rank.KING, Suit.HEART),
            c(Rank.TWO, Suit.DIAMOND), c(Rank.TWO, Suit.CLUB),
            c(Rank.NINE, Suit.SPADE),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.TWO_PAIR)
        assertThat(v.tiebreakers).isEqualTo(listOf(14, 13, 9))   // A,K + best kicker 9
    }

    @Test fun `one pair`() {
        val seven = listOf(
            c(Rank.SEVEN, Suit.SPADE), c(Rank.SEVEN, Suit.HEART),
            c(Rank.ACE, Suit.SPADE), c(Rank.KING, Suit.HEART), c(Rank.QUEEN, Suit.DIAMOND),
            c(Rank.TWO, Suit.CLUB), c(Rank.THREE, Suit.SPADE),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.ONE_PAIR)
        assertThat(v.tiebreakers).isEqualTo(listOf(7, 14, 13, 12))
    }

    @Test fun `high card ace`() {
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE), c(Rank.JACK, Suit.HEART), c(Rank.NINE, Suit.DIAMOND),
            c(Rank.SEVEN, Suit.CLUB), c(Rank.FIVE, Suit.SPADE),
            c(Rank.THREE, Suit.HEART), c(Rank.TWO, Suit.DIAMOND),
        )
        val v = HandEvaluatorHoldem.evaluateBest(seven)
        assertThat(v.category).isEqualTo(HandCategory.HIGH_CARD)
        assertThat(v.tiebreakers).isEqualTo(listOf(14, 11, 9, 7, 5))
    }

    // ---------- 카테고리 간 순위 ----------

    @Test fun `category strength ordering`() {
        val ordered = HandCategory.entries.sortedBy { it.strength }
        assertThat(ordered.first()).isEqualTo(HandCategory.HIGH_CARD)
        assertThat(ordered.last()).isEqualTo(HandCategory.ROYAL_FLUSH)
    }

    // ---------- tiebreaker 비교 ----------

    @Test fun `flush ace-high beats flush king-high`() {
        val a = HandValue(HandCategory.FLUSH, listOf(14, 11, 9, 6, 2), emptyList())
        val b = HandValue(HandCategory.FLUSH, listOf(13, 12, 11, 9, 8), emptyList())
        assertThat(a > b).isTrue()
    }

    @Test fun `same flush is equal`() {
        val a = HandValue(HandCategory.FLUSH, listOf(14, 11, 9, 6, 2), emptyList())
        val b = HandValue(HandCategory.FLUSH, listOf(14, 11, 9, 6, 2), emptyList())
        assertThat(a.compareTo(b)).isEqualTo(0)
    }

    @Test fun `full house higher trip wins regardless of pair`() {
        val a = HandValue(HandCategory.FULL_HOUSE, listOf(13, 2), emptyList())   // KKK 22
        val b = HandValue(HandCategory.FULL_HOUSE, listOf(12, 14), emptyList())  // QQQ AA
        assertThat(a > b).isTrue()
    }

    @Test fun `straight 6-high beats wheel`() {
        val sixHigh = HandValue(HandCategory.STRAIGHT, listOf(6), emptyList())
        val wheel = HandValue(HandCategory.STRAIGHT, listOf(5), emptyList())
        assertThat(sixHigh > wheel).isTrue()
    }

    @Test fun `straight flush beats four of a kind`() {
        val sf = HandValue(HandCategory.STRAIGHT_FLUSH, listOf(9), emptyList())
        val quads = HandValue(HandCategory.FOUR_OF_A_KIND, listOf(14, 13), emptyList())
        assertThat(sf > quads).isTrue()
    }

    @Test fun `royal beats straight flush`() {
        val royal = HandValue(HandCategory.ROYAL_FLUSH, listOf(14), emptyList())
        val sf = HandValue(HandCategory.STRAIGHT_FLUSH, listOf(13), emptyList())
        assertThat(royal > sf).isTrue()
    }

    @Test fun `pair of aces beats pair of kings`() {
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE), c(Rank.ACE, Suit.HEART),
            c(Rank.SEVEN, Suit.DIAMOND), c(Rank.FIVE, Suit.CLUB), c(Rank.THREE, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.NINE, Suit.CLUB),
        )
        val a = HandEvaluatorHoldem.evaluateBest(seven)

        val seven2 = listOf(
            c(Rank.KING, Suit.SPADE), c(Rank.KING, Suit.HEART),
            c(Rank.SEVEN, Suit.DIAMOND), c(Rank.FIVE, Suit.CLUB), c(Rank.THREE, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.NINE, Suit.CLUB),
        )
        val b = HandEvaluatorHoldem.evaluateBest(seven2)
        assertThat(a > b).isTrue()
    }

    // ---------- evaluate(holeCards, community) ----------

    @Test fun `evaluate splits hole and community correctly`() {
        val hole = listOf(c(Rank.ACE, Suit.SPADE), c(Rank.KING, Suit.SPADE))
        val community = listOf(
            c(Rank.QUEEN, Suit.SPADE), c(Rank.JACK, Suit.SPADE), c(Rank.TEN, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.THREE, Suit.CLUB),
        )
        val v = HandEvaluatorHoldem.evaluate(hole, community)
        assertThat(v.category).isEqualTo(HandCategory.ROYAL_FLUSH)
    }

    @Test fun `evaluate validates input sizes`() {
        val hole = listOf(c(Rank.ACE, Suit.SPADE))
        val community = listOf(
            c(Rank.QUEEN, Suit.SPADE), c(Rank.JACK, Suit.SPADE), c(Rank.TEN, Suit.SPADE),
            c(Rank.TWO, Suit.HEART), c(Rank.THREE, Suit.CLUB),
        )
        val ex = runCatching { HandEvaluatorHoldem.evaluate(hole, community) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }
}
