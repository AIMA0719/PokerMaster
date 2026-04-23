package com.infocar.pokermaster.core.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CardTest {

    @Test
    fun `standard deck has 52 unique cards`() {
        val deck = standardDeck()
        assertThat(deck).hasSize(52)
        assertThat(deck.toSet()).hasSize(52)
    }

    @Test
    fun `suit ordering follows Korean 7-stud convention CLUB lowest SPADE highest`() {
        assertThat(Suit.CLUB.ordinal).isLessThan(Suit.DIAMOND.ordinal)
        assertThat(Suit.DIAMOND.ordinal).isLessThan(Suit.HEART.ordinal)
        assertThat(Suit.HEART.ordinal).isLessThan(Suit.SPADE.ordinal)
    }

    @Test
    fun `rank ordering TWO lowest ACE highest`() {
        assertThat(Rank.TWO.ordinal).isEqualTo(0)
        assertThat(Rank.ACE.ordinal).isEqualTo(12)
    }

    @Test
    fun `card toString uses short rank and suit symbol`() {
        assertThat(Card(Suit.SPADE, Rank.ACE).toString()).isEqualTo("A♠")
        assertThat(Card(Suit.CLUB, Rank.TEN).toString()).isEqualTo("T♣")
    }
}
