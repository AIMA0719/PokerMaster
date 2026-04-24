package com.infocar.pokermaster.feature.history.stats

import com.infocar.pokermaster.core.data.history.HandHistoryRecord

/**
 * 통계 대시보드 표시용 집계 — M6-B.
 *
 * 설계서 §1.2.Y (V1 축약): winrate / hands / 최대팟 / 모드별. VPIP/PFR/3-bet% 같은 지표는
 * actionsJson 분석 필요하므로 v2 로 미룸.
 */
data class StatsOverview(
    val totalHands: Int,
    val handsWon: Int,
    val winrate: Double,
    val totalPotChips: Long,
    val biggestPot: Long,
    val byMode: Map<String, ModeStats>,
) {
    companion object {
        val EMPTY = StatsOverview(
            totalHands = 0,
            handsWon = 0,
            winrate = 0.0,
            totalPotChips = 0L,
            biggestPot = 0L,
            byMode = emptyMap(),
        )
    }
}

data class ModeStats(
    val hands: Int,
    val wonHands: Int,
    val winrate: Double,
    val totalPotChips: Long,
    val biggestPot: Long,
)

/**
 * HandHistoryRecord 리스트로부터 [StatsOverview] 산출 — pure Kotlin, JVM 테스트 가능.
 *
 * `won` 판정: `record.winnerSeat` 가 `record.initialState.players.first { it.isHuman }.seat`
 * 와 일치하는지. 여러 인간 플레이어가 가능해지면 좌석 집합 비교로 확장.
 */
object StatsCalculator {

    fun computeFromRecords(records: List<HandHistoryRecord>): StatsOverview {
        if (records.isEmpty()) return StatsOverview.EMPTY

        var wonTotal = 0
        var potTotal = 0L
        var biggestPot = 0L
        val modeAgg: MutableMap<String, MutableModeAgg> = mutableMapOf()

        for (r in records) {
            val humanSeat = r.initialState.players.firstOrNull { it.isHuman }?.seat
            val won = humanSeat != null && r.winnerSeat == humanSeat
            if (won) wonTotal++
            potTotal += r.potSize
            if (r.potSize > biggestPot) biggestPot = r.potSize

            val agg = modeAgg.getOrPut(r.mode) { MutableModeAgg() }
            agg.hands++
            if (won) agg.wonHands++
            agg.totalPot += r.potSize
            if (r.potSize > agg.biggestPot) agg.biggestPot = r.potSize
        }

        val total = records.size
        return StatsOverview(
            totalHands = total,
            handsWon = wonTotal,
            winrate = wonTotal.toDouble() / total,
            totalPotChips = potTotal,
            biggestPot = biggestPot,
            byMode = modeAgg.mapValues { (_, v) ->
                ModeStats(
                    hands = v.hands,
                    wonHands = v.wonHands,
                    winrate = if (v.hands > 0) v.wonHands.toDouble() / v.hands else 0.0,
                    totalPotChips = v.totalPot,
                    biggestPot = v.biggestPot,
                )
            },
        )
    }

    private class MutableModeAgg {
        var hands: Int = 0
        var wonHands: Int = 0
        var totalPot: Long = 0L
        var biggestPot: Long = 0L
    }
}
