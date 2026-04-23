package com.infocar.pokermaster.core.model

/**
 * 카드 무늬. 한국식 7포커 브링인 동률 시 우선순위는 ♣ < ♦ < ♥ < ♠ (v1.1 §3.3.E).
 * ordinal 이 작을수록 약함.
 */
enum class Suit { CLUB, DIAMOND, HEART, SPADE }

/**
 * 카드 끗수. ordinal 이 작을수록 약함.
 * 백스트레이트(A-2-3-4-5) 등 특수 처리는 HandEvaluator 내부에서 수행.
 */
enum class Rank(val short: String, val value: Int) {
    TWO("2", 2), THREE("3", 3), FOUR("4", 4), FIVE("5", 5), SIX("6", 6),
    SEVEN("7", 7), EIGHT("8", 8), NINE("9", 9), TEN("T", 10),
    JACK("J", 11), QUEEN("Q", 12), KING("K", 13), ACE("A", 14);

    companion object {
        val LOW_ACE_VALUE: Int = 1   // 백스트레이트/로우에서 사용 (A → 1 매핑)
        val HIGH_ACE_VALUE: Int = 14
    }
}

data class Card(val suit: Suit, val rank: Rank) {
    override fun toString(): String = "${rank.short}${suit.symbol}"
}

val Suit.symbol: String
    get() = when (this) {
        Suit.SPADE -> "♠"
        Suit.HEART -> "♥"
        Suit.DIAMOND -> "♦"
        Suit.CLUB -> "♣"
    }

/** 표준 52장 덱 생성 (정렬 순서, 셔플은 별도). */
fun standardDeck(): List<Card> =
    Suit.entries.flatMap { s -> Rank.entries.map { r -> Card(s, r) } }
