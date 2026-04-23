package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit

internal fun card(s: String): Card {
    require(s.length == 2) { "Bad card token: $s" }
    val r = when (s[0]) {
        '2' -> Rank.TWO; '3' -> Rank.THREE; '4' -> Rank.FOUR; '5' -> Rank.FIVE
        '6' -> Rank.SIX; '7' -> Rank.SEVEN; '8' -> Rank.EIGHT; '9' -> Rank.NINE
        'T' -> Rank.TEN; 'J' -> Rank.JACK;  'Q' -> Rank.QUEEN; 'K' -> Rank.KING
        'A' -> Rank.ACE
        else -> error("Bad rank: ${s[0]}")
    }
    val u = when (s[1]) {
        'S' -> Suit.SPADE; 'H' -> Suit.HEART; 'D' -> Suit.DIAMOND; 'C' -> Suit.CLUB
        else -> error("Bad suit: ${s[1]}")
    }
    return Card(u, r)
}

internal fun hand(vararg s: String): List<Card> = s.map { card(it) }
