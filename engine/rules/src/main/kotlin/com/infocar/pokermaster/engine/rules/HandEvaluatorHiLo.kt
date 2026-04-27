package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank

/**
 * 7스터드 하이로우 평가기 (한국식 변형).
 *
 * # 한국식 룰 (8-or-better qualifier 없음)
 *  - 모든 핸드는 항상 로우 값을 갖는다 → [evaluateLow] 는 NON-NULL.
 *  - 페어/트립/투페어/풀하우스/포카드 가 5장 안에 있어도 로우 후보가 된다.
 *    단 [LowValue.category] 가 더 큰(=약한) 값을 갖는다 (페어 ≥ 어떤 무페어 로우).
 *  - 스트레이트/플러시 모양은 로우 평가에서 무시 (휠 A-2-3-4-5 = 가장 강한 로우).
 *
 * # 알고리즘
 *  - 7장에서 5장을 고르는 21가지 조합 모두 시도.
 *  - 각 조합에 대해 [LowValue] 산출 → category(작을수록 강함) → tiebreakers 내림차순(작을수록 강함).
 *  - 가장 강한 [LowValue] 를 채택.
 *
 * # 하이 평가
 *  - 하이/로우 독립 평가 — 7장에서 각각 베스트 5장.
 *  - [evaluateHigh] 는 [Variant.SEVEN_STUD_KR] 으로 호출 (백스트레이트/백SF 약화).
 */
object HandEvaluatorHiLo {

    /** 하이 사이드 평가: 7스터드 한국식 변형 사용 (백스트레이트 = 일반 SF/Straight 보다 약). */
    fun evaluateHigh(seven: List<Card>): HandValue =
        HandEval.bestFiveOfSeven(seven, Variant.SEVEN_STUD_KR)

    /**
     * 로우 사이드 평가 (한국식, NON-NULL).
     *
     * 7장에서 5장 조합 21가지를 모두 평가해 가장 강한 [LowValue] 를 채택.
     * 페어/트립 등이 끼어 있는 조합도 후보에 포함되며, 그 경우 [LowValue.category] 가 커진다(=약함).
     *
     * @return 항상 non-null. 가장 강한 로우.
     */
    fun evaluateLow(seven: List<Card>): LowValue {
        require(seven.size == 7) { "Hi-Lo evaluation requires exactly 7 cards" }
        var best: LowValue? = null
        for (a in 0..2) for (b in (a + 1)..3) for (c in (b + 1)..4)
            for (d in (c + 1)..5) for (e in (d + 1)..6) {
                val five = listOf(seven[a], seven[b], seven[c], seven[d], seven[e])
                val low = makeLow(five)
                if (best == null || low < best) best = low  // 작을수록 강함
            }
        return checkNotNull(best) { "evaluateLow inner loop produced no candidate (size=${seven.size})" }
    }

    /**
     * 5장의 로우 평가 (한국식). 항상 non-null — 어떤 5장이든 [LowValue] 를 갖는다.
     *
     * 알고리즘:
     *  1. A 는 1로 사용 (ACE.value = 14 → 1).
     *  2. rank 빈도수 그룹화 → category 결정.
     *     - distinct 5장: category = 0 (high-card-low; 가장 강한 카테고리)
     *     - 페어 1쌍 + 키커 3장: category = 1
     *     - 투페어 + 키커: category = 2
     *     - 트립 + 키커 2장: category = 3
     *     - 포카드 + 키커: category = 4
     *     - 풀하우스: category = 5
     *  3. tiebreakersDesc 는 카테고리 내 비교용 — high → low 순 (작을수록 강함).
     *     - 페어/트립/포카드/풀하우스 의 경우 group rank 가 먼저 오도록 정렬해
     *       동일 카테고리 안에서 group rank 약한 쪽이 더 강한 로우가 되도록 유지.
     *
     * 스트레이트/플러시 모양은 로우 평가에서 무시 — 휠 A-2-3-4-5 는 distinct 5장 → category 0 의
     * tiebreakers (5,4,3,2,1) 로 가장 강함.
     */
    private fun makeLow(five: List<Card>): LowValue {
        // A=1 매핑
        val lowRanks = five.map { if (it.rank == Rank.ACE) 1 else it.rank.value }
        // 빈도 그룹화: count 우선, 동률이면 rank desc — 페어 rank 가 키커 앞에 오도록.
        val groups = lowRanks
            .groupingBy { it }
            .eachCount()
            .toList()
            .sortedWith(compareByDescending<Pair<Int, Int>> { it.second }.thenByDescending { it.first })
        val counts = groups.map { it.second }
        val ranksByGroup = groups.map { it.first }

        val category = when (counts) {
            listOf(1, 1, 1, 1, 1) -> 0  // 무페어
            listOf(2, 1, 1, 1) -> 1     // 원페어
            listOf(2, 2, 1) -> 2        // 투페어
            listOf(3, 1, 1) -> 3        // 트립
            listOf(4, 1) -> 4           // 포카드
            listOf(3, 2) -> 5           // 풀하우스
            else -> error("Unexpected rank-count grouping: $counts (lowRanks=$lowRanks)")
        }

        // tiebreakers: group rank 순(group rank desc, 같은 group 안에선 정렬 결과)
        // - 무페어: 5장 rank desc (그대로)
        // - 페어 1쌍: [페어rank, 키커1, 키커2, 키커3] (키커는 rank desc)
        // - 투페어: [상위페어, 하위페어, 키커]
        // - 트립: [트립rank, 키커1, 키커2]
        // - 포카드: [포카드rank, 키커]
        // - 풀하우스: [트립rank, 페어rank]
        // 어떤 카테고리든 sortedWith(count desc, then rank desc) 그룹 순서로 rank 를 그대로 나열하면
        // 카테고리 내부에서 "작은 group rank 가 더 강함" 비교가 정상 동작한다.
        val tiebreakers = ranksByGroup

        return LowValue(category = category, tiebreakersDesc = tiebreakers, cards = five)
    }
}

/**
 * 한국식 로우 핸드 값.
 *
 * - [category]: 0 = 무페어(가장 강한 카테고리), 1 = 페어, 2 = 투페어, 3 = 트립, 4 = 포카드, 5 = 풀하우스.
 *   카테고리 작을수록 강함.
 * - [tiebreakersDesc]: 카테고리 내 비교 — high → low 순 (작을수록 강함).
 *   휠 A-2-3-4-5 = (5,4,3,2,1) = 가장 강한 무페어 로우.
 *
 * compareTo 의미: this < other 이면 this 가 더 강함. (오름차순 정렬 = 강한 것 먼저)
 */
data class LowValue(
    val category: Int,
    val tiebreakersDesc: List<Int>,
    val cards: List<Card>,
) : Comparable<LowValue> {

    override fun compareTo(other: LowValue): Int {
        val byCategory = category.compareTo(other.category)
        if (byCategory != 0) return byCategory
        val n = minOf(tiebreakersDesc.size, other.tiebreakersDesc.size)
        for (i in 0 until n) {
            val c = tiebreakersDesc[i].compareTo(other.tiebreakersDesc[i])
            if (c != 0) return c
        }
        return tiebreakersDesc.size.compareTo(other.tiebreakersDesc.size)
    }
}
