package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank

/**
 * 169 가지 홀덤 프리플롭 핸드 분류 + 표준 액션 권장.
 *
 * 단순화 모델:
 *  - 13 페어 + 78 suited + 78 offsuit = 169
 *  - 4 등급(PREMIUM / STRONG / SPECULATIVE / TRASH) 으로 그룹화
 *  - 포지션·액션 히스토리는 v2 에서 고도화 (현재는 base action 만)
 */
enum class PreflopGroup(val baseAction: PreflopAction) {
    PREMIUM(PreflopAction.RAISE),     // AA, KK, QQ, JJ, AKs, AKo
    STRONG(PreflopAction.RAISE),      // TT~88, AQs, AQo, KQs, AJs
    SPECULATIVE(PreflopAction.CALL),  // 77~22, suited connectors, suited aces
    TRASH(PreflopAction.FOLD),        // 나머지
}

enum class PreflopAction { RAISE, CALL, FOLD }

object PreflopChart {

    /** 두 카드 → 그룹 분류. */
    fun classify(hole: List<Card>): PreflopGroup {
        require(hole.size == 2) { "Hole must be 2 cards" }
        val (a, b) = hole.sortedByDescending { it.rank.value }
        val high = a.rank
        val low = b.rank
        val suited = a.suit == b.suit
        val pair = high == low
        val gap = high.value - low.value

        // Pairs
        if (pair) {
            return when (high) {
                Rank.ACE, Rank.KING, Rank.QUEEN, Rank.JACK -> PreflopGroup.PREMIUM
                Rank.TEN, Rank.NINE, Rank.EIGHT -> PreflopGroup.STRONG
                else -> PreflopGroup.SPECULATIVE   // 77~22
            }
        }

        // AKs / AKo
        if (high == Rank.ACE && low == Rank.KING) return PreflopGroup.PREMIUM

        // Strong: AQ, KQs, AJs
        if (high == Rank.ACE && low == Rank.QUEEN) return PreflopGroup.STRONG
        if (high == Rank.ACE && low == Rank.JACK && suited) return PreflopGroup.STRONG
        if (high == Rank.KING && low == Rank.QUEEN && suited) return PreflopGroup.STRONG

        // Speculative: suited connectors / one-gap suited / suited aces
        if (suited && high == Rank.ACE) return PreflopGroup.SPECULATIVE   // A2s~AJs
        if (suited && gap == 1 && low.value >= 4) return PreflopGroup.SPECULATIVE   // 54s~JTs
        if (suited && gap == 2 && low.value >= 5) return PreflopGroup.SPECULATIVE   // 64s~T8s

        // Strong-ish broadway combos
        if (high.value >= 11 && low.value >= 10) return PreflopGroup.STRONG   // KJ, QJ, QT, JT 등 일부
        if (suited && high.value >= 11 && low.value >= 8) return PreflopGroup.SPECULATIVE   // KTs, QTs, J8s 등

        return PreflopGroup.TRASH
    }

    /** 169 핸드 모두에 대한 그룹 통계 (테스트/UI 용). */
    fun allHandGroups(): Map<String, PreflopGroup> {
        val ranks = Rank.entries.sortedByDescending { it.value }
        val result = LinkedHashMap<String, PreflopGroup>(169)
        for (i in ranks.indices) {
            for (j in i until ranks.size) {
                val high = ranks[i]
                val low = ranks[j]
                if (i == j) {
                    val key = "${high.short}${high.short}"
                    result[key] = classifyByRanks(high, low, suited = false)
                } else {
                    val keyS = "${high.short}${low.short}s"
                    val keyO = "${high.short}${low.short}o"
                    result[keyS] = classifyByRanks(high, low, suited = true)
                    result[keyO] = classifyByRanks(high, low, suited = false)
                }
            }
        }
        return result
    }

    /** rank+suit 만으로 분류 (테스트용 헬퍼). */
    internal fun classifyByRanks(high: Rank, low: Rank, suited: Boolean): PreflopGroup {
        val pair = high == low
        val gap = high.value - low.value
        if (pair) {
            return when (high) {
                Rank.ACE, Rank.KING, Rank.QUEEN, Rank.JACK -> PreflopGroup.PREMIUM
                Rank.TEN, Rank.NINE, Rank.EIGHT -> PreflopGroup.STRONG
                else -> PreflopGroup.SPECULATIVE
            }
        }
        if (high == Rank.ACE && low == Rank.KING) return PreflopGroup.PREMIUM
        if (high == Rank.ACE && low == Rank.QUEEN) return PreflopGroup.STRONG
        if (high == Rank.ACE && low == Rank.JACK && suited) return PreflopGroup.STRONG
        if (high == Rank.KING && low == Rank.QUEEN && suited) return PreflopGroup.STRONG
        if (suited && high == Rank.ACE) return PreflopGroup.SPECULATIVE
        if (suited && gap == 1 && low.value >= 4) return PreflopGroup.SPECULATIVE
        if (suited && gap == 2 && low.value >= 5) return PreflopGroup.SPECULATIVE
        if (high.value >= 11 && low.value >= 10) return PreflopGroup.STRONG
        if (suited && high.value >= 11 && low.value >= 8) return PreflopGroup.SPECULATIVE
        return PreflopGroup.TRASH
    }
}
