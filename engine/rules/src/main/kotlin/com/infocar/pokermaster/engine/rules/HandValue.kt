package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Card

/**
 * 평가된 핸드. 5장 베스트 + 카테고리 + 카테고리 내 비교용 tiebreakers.
 *
 * tiebreakers 는 카테고리별 비교 우선순위 그대로 나열:
 *  - HIGH_CARD: 5장 rank value 내림차순
 *  - ONE_PAIR: [페어 rank, 키커1, 키커2, 키커3]
 *  - TWO_PAIR: [상위 페어 rank, 하위 페어 rank, 키커]
 *  - THREE_OF_A_KIND: [트리플 rank, 키커1, 키커2]
 *  - STRAIGHT / STRAIGHT_BACK: [최고 카드 rank] (백스트레이트는 5)
 *  - FLUSH: 5장 rank value 내림차순
 *  - FULL_HOUSE: [트리플 rank, 페어 rank]
 *  - FOUR_OF_A_KIND: [포카드 rank, 키커]
 *  - STRAIGHT_FLUSH / STRAIGHT_FLUSH_BACK: [최고 카드 rank]
 *  - ROYAL_FLUSH: [14]
 */
data class HandValue(
    val category: HandCategory,
    val tiebreakers: List<Int>,
    val cards: List<Card>,   // 베스트 5장 (UI 하이라이트용)
) : Comparable<HandValue> {

    override fun compareTo(other: HandValue): Int {
        val byCategory = category.strength.compareTo(other.category.strength)
        if (byCategory != 0) return byCategory
        // 동일 카테고리: tiebreakers 같은 인덱스끼리 순차 비교
        val n = minOf(tiebreakers.size, other.tiebreakers.size)
        for (i in 0 until n) {
            val c = tiebreakers[i].compareTo(other.tiebreakers[i])
            if (c != 0) return c
        }
        return tiebreakers.size.compareTo(other.tiebreakers.size)
    }

    companion object {
        val MIN: HandValue = HandValue(HandCategory.HIGH_CARD, listOf(0), emptyList())
    }
}
