package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank

/**
 * 7스터드 하이로우 (8-or-better) 평가기.
 *
 * v1.1 §3.4 패치 반영:
 *  - 하이/로우 독립 평가 (B), 7장에서 각각 베스트 5장
 *  - 21조합 모두 페어 검증 (A): 어떤 5장 조합도 페어를 포함하면 로우 자격 X
 *  - 5장 모두 동률은 분할 (C) — 본 평가기는 [LowValue] 동치 비교만 제공, 분할은 [SidePotCalculator] 책임
 *  - 8-or-better qualifier: 5장 모두 8 이하 + 페어 없음 + 스트레이트/플러시는 로우에서 무시
 */
object HandEvaluatorHiLo {

    /** 하이 사이드 평가: 7스터드 한국식 변형 사용 (백스트레이트 = 일반 SF/Straight 보다 약). */
    fun evaluateHigh(seven: List<Card>): HandValue =
        HandEval.bestFiveOfSeven(seven, Variant.SEVEN_STUD_KR)

    /**
     * 로우 사이드 평가. 자격 미달이면 null.
     *
     * 알고리즘:
     *  1. 7장에서 5장 조합 21가지 모두 시도
     *  2. 각 조합에 대해 (a) 5장 모두 rank ≤ 8, (b) 페어 없음 검증
     *  3. 자격 통과 조합 중 [LowValue] 가장 강한(낮은 high card) 것 채택
     */
    fun evaluateLow(seven: List<Card>): LowValue? {
        require(seven.size == 7) { "Hi-Lo evaluation requires exactly 7 cards" }
        var best: LowValue? = null
        for (a in 0..2) for (b in (a + 1)..3) for (c in (b + 1)..4)
            for (d in (c + 1)..5) for (e in (d + 1)..6) {
                val five = listOf(seven[a], seven[b], seven[c], seven[d], seven[e])
                val low = makeLow(five) ?: continue
                if (best == null || low < best) best = low  // low 는 작을수록 강함
            }
        return best
    }

    /**
     * 5장이 8-or-better 자격을 만족하면 [LowValue] 반환.
     * - 모든 카드 rank ≤ 8 (A 는 1로 사용)
     * - 페어 없음 (5장 모두 distinct rank)
     * - 스트레이트/플러시 여부는 로우 평가에서 무시
     */
    private fun makeLow(five: List<Card>): LowValue? {
        // A 는 로우에서 1로 사용. ACE.value = 14 → 1 로 매핑.
        val lowRanks = five.map { if (it.rank == Rank.ACE) 1 else it.rank.value }
        // 모두 8 이하
        if (lowRanks.any { it > 8 }) return null
        // 페어 없음 (distinct == 5)
        if (lowRanks.toSet().size != 5) return null
        // 비교는 high → low 순으로 작을수록 강함
        val sortedDesc = lowRanks.sortedDescending()
        return LowValue(sortedDesc, five)
    }
}

/**
 * 로우 핸드 값. tiebreakers 는 high → low 순으로 정렬 (작을수록 강함).
 * 휠 A-2-3-4-5 = [5,4,3,2,1] = 가장 강한 로우.
 *
 * compareTo 의미: this < other 이면 this 가 더 강함. (즉 정렬 시 오름차순 = 강한 것 먼저)
 */
data class LowValue(
    val tiebreakersDesc: List<Int>,   // 5장 rank 내림차순 (A=1)
    val cards: List<Card>,
) : Comparable<LowValue> {

    override fun compareTo(other: LowValue): Int {
        val n = minOf(tiebreakersDesc.size, other.tiebreakersDesc.size)
        for (i in 0 until n) {
            val c = tiebreakersDesc[i].compareTo(other.tiebreakersDesc[i])
            if (c != 0) return c
        }
        return tiebreakersDesc.size.compareTo(other.tiebreakersDesc.size)
    }
}
