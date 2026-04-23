package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit

/**
 * 5장 평가의 공통 알고리즘 모음. 7장 → 베스트 5장 선택은 평가기별로 다르게 호출.
 */
internal object HandEval {

    /**
     * 5장 카드의 카테고리 + tiebreakers 산출. 룰 변형(7스터드 백스트레이트 약/강)에 따라 결과가 다르므로
     * [variant] 로 분기. 홀덤은 [Variant.HOLDEM], 7스터드 한국식은 [Variant.SEVEN_STUD_KR].
     */
    fun evaluateFive(five: List<Card>, variant: Variant): HandValue {
        require(five.size == 5) { "evaluateFive requires exactly 5 cards" }

        val ranks = five.map { it.rank.value }.sortedDescending()
        val suits = five.map { it.suit }
        val isFlush = suits.distinct().size == 1
        val straightHigh = detectStraight(ranks)        // null = 스트레이트 아님
        val isWheel = isWheelStraight(ranks)            // A-2-3-4-5 (rank set)

        // 같은 rank 카드 묶음 (count, rank) 내림차순 — count 우선, 동률이면 rank 우선
        val rankGroups: List<Pair<Int, Int>> = ranks
            .groupingBy { it }
            .eachCount()
            .toList()
            .map { (rank, count) -> count to rank }
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.first }.thenByDescending { it.second })

        // 1) Straight Flush / Royal / Straight Flush BACK
        if (isFlush && straightHigh != null) {
            return when {
                straightHigh == 14 -> HandValue(HandCategory.ROYAL_FLUSH, listOf(14), five)
                isWheel && variant == Variant.SEVEN_STUD_KR ->
                    HandValue(HandCategory.STRAIGHT_FLUSH_BACK, listOf(5), five)
                else -> HandValue(HandCategory.STRAIGHT_FLUSH, listOf(straightHigh), five)
            }
        }
        // 2) Four of a Kind
        if (rankGroups[0].first == 4) {
            val quad = rankGroups[0].second
            val kicker = rankGroups[1].second
            return HandValue(HandCategory.FOUR_OF_A_KIND, listOf(quad, kicker), five)
        }
        // 3) Full House
        if (rankGroups[0].first == 3 && rankGroups[1].first == 2) {
            return HandValue(HandCategory.FULL_HOUSE, listOf(rankGroups[0].second, rankGroups[1].second), five)
        }
        // 4) Flush
        if (isFlush) {
            return HandValue(HandCategory.FLUSH, ranks, five)
        }
        // 5) Straight / Straight BACK (한국식 7스터드만 별도)
        if (straightHigh != null) {
            return if (isWheel && variant == Variant.SEVEN_STUD_KR) {
                HandValue(HandCategory.STRAIGHT_BACK, listOf(5), five)
            } else {
                HandValue(HandCategory.STRAIGHT, listOf(straightHigh), five)
            }
        }
        // 6) Three of a Kind
        if (rankGroups[0].first == 3) {
            val trip = rankGroups[0].second
            val kickers = rankGroups.drop(1).map { it.second }.take(2)
            return HandValue(HandCategory.THREE_OF_A_KIND, listOf(trip) + kickers, five)
        }
        // 7) Two Pair
        if (rankGroups[0].first == 2 && rankGroups[1].first == 2) {
            val highPair = rankGroups[0].second
            val lowPair = rankGroups[1].second
            val kicker = rankGroups[2].second
            return HandValue(HandCategory.TWO_PAIR, listOf(highPair, lowPair, kicker), five)
        }
        // 8) One Pair
        if (rankGroups[0].first == 2) {
            val pair = rankGroups[0].second
            val kickers = rankGroups.drop(1).map { it.second }.take(3)
            return HandValue(HandCategory.ONE_PAIR, listOf(pair) + kickers, five)
        }
        // 9) High Card
        return HandValue(HandCategory.HIGH_CARD, ranks, five)
    }

    /**
     * 5개 rank value (내림차순)이 스트레이트라면 최고 카드 rank 반환.
     * - 일반 스트레이트: 5개 연속 (예: 6,5,4,3,2 → 6 / 10,9,8,7,6 → 10)
     * - 마운틴: A,K,Q,J,10 → 14
     * - 휠 (5-high): A,5,4,3,2 (A 가 1로 사용) → 5
     */
    private fun detectStraight(ranksDesc: List<Int>): Int? {
        val unique = ranksDesc.distinct()
        if (unique.size != 5) return null
        // 일반 5연속
        if (unique[0] - unique[4] == 4) return unique[0]
        // 휠: A,5,4,3,2 (정렬 후 14,5,4,3,2)
        if (unique == listOf(14, 5, 4, 3, 2)) return 5
        return null
    }

    private fun isWheelStraight(ranksDesc: List<Int>): Boolean =
        ranksDesc.distinct() == listOf(14, 5, 4, 3, 2)

    /**
     * 7장에서 5장 베스트 평가. C(7,5) = 21 조합 모두 시도 후 최고값 반환.
     */
    fun bestFiveOfSeven(seven: List<Card>, variant: Variant): HandValue {
        require(seven.size == 7) { "bestFiveOfSeven requires exactly 7 cards" }
        var best: HandValue? = null
        // 7장 인덱스에서 5장 선택. 효율보다 명확성 우선 (M1: 21회 평가)
        for (a in 0..2) {
            for (b in (a + 1)..3) {
                for (c in (b + 1)..4) {
                    for (d in (c + 1)..5) {
                        for (e in (d + 1)..6) {
                            val five = listOf(seven[a], seven[b], seven[c], seven[d], seven[e])
                            val v = evaluateFive(five, variant)
                            if (best == null || v > best) best = v
                        }
                    }
                }
            }
        }
        return best!!
    }
}

/** 평가 변형. 한국식 7스터드 백스트레이트 처리만 다름. */
enum class Variant {
    /** 텍사스 홀덤(국제 표준): 휠 = 5-high straight (별도 BACK 카테고리 없음). */
    HOLDEM,
    /** 한국식 7카드 스터드: 백스트레이트(A-2-3-4-5)와 백SF가 별도 카테고리(약화). */
    SEVEN_STUD_KR,
}

/**
 * 텍사스 홀덤 평가기. 7장(홀 2 + 커뮤니티 5) 중 베스트 5장.
 * 휠(A-2-3-4-5) = 5-high STRAIGHT (또는 STRAIGHT_FLUSH).
 */
object HandEvaluatorHoldem {
    fun evaluateBest(seven: List<Card>): HandValue =
        HandEval.bestFiveOfSeven(seven, Variant.HOLDEM)

    /** 보드 5장 + 홀 2장 의 명시적 시그니처. */
    fun evaluate(holeCards: List<Card>, community: List<Card>): HandValue {
        require(holeCards.size == 2) { "Hold'em hole cards must be 2" }
        require(community.size == 5) { "Hold'em community must be 5" }
        return evaluateBest(holeCards + community)
    }
}

/**
 * 한국식 7스터드 평가기. 7장 중 베스트 5장.
 * 백스트레이트는 STRAIGHT_BACK (일반 스트레이트보다 약), 백SF는 STRAIGHT_FLUSH_BACK.
 *
 * v1.1 ADR-011: 백SF 위치는 "약/강/동일" 3옵션 — 디폴트 약. 옵션 적용은 본 평가기에 변형 카테고리만 주입하므로,
 * 향후 옵션 토글 시 호출자가 [Variant] 또는 후처리로 카테고리 매핑(STRAIGHT_FLUSH_BACK → STRAIGHT_FLUSH)만 변경.
 */
object HandEvaluator7Stud {
    fun evaluateBest(seven: List<Card>): HandValue =
        HandEval.bestFiveOfSeven(seven, Variant.SEVEN_STUD_KR)

    /** 임의 카드 수에서 베스트 5장 (최소 5, 최대 7). 진행 중 라운드 평가용. */
    fun evaluatePartial(cards: List<Card>): HandValue? {
        if (cards.size < 5) return null
        if (cards.size == 5) return HandEval.evaluateFive(cards, Variant.SEVEN_STUD_KR)
        if (cards.size == 6) {
            var best: HandValue? = null
            for (skip in 0..5) {
                val five = cards.filterIndexed { i, _ -> i != skip }
                val v = HandEval.evaluateFive(five, Variant.SEVEN_STUD_KR)
                if (best == null || v > best) best = v
            }
            return best
        }
        return HandEval.bestFiveOfSeven(cards, Variant.SEVEN_STUD_KR)
    }
}

/** Suit 우선순위 (브릿지 표준 / v1.1 §3.3.E). 7스터드 브링인 동률 비교에 사용. CLUB 가 가장 약함. */
internal val Suit.bridgeRank: Int
    get() = when (this) {
        Suit.CLUB -> 1
        Suit.DIAMOND -> 2
        Suit.HEART -> 3
        Suit.SPADE -> 4
    }

internal val Rank.intValue: Int get() = this.value
