package com.infocar.pokermaster.feature.history.stats

import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.model.ActionType

/**
 * 통계 대시보드 표시용 집계 — M6-B + M7 VPIP/PFR.
 *
 * 설계서 §1.2.Y: winrate / hands / 최대팟 / 모드별 + 첫 스트릿 자발 참여(VPIP) / 첫 스트릿 레이즈(PFR).
 * VPIP/PFR 정의:
 *  - VPIP: 첫 스트릿(홀덤=preflop, 7스터드=3rd) 에서 본인 좌석이 자발적으로 칩을 더 넣은 핸드 비율.
 *    CALL/BET/RAISE/ALL_IN/COMPLETE 가 한 번이라도 있으면 카운트. 강제 블라인드/앤티/브링인은
 *    액션 로그에 기록되지 않으므로 자연스레 제외.
 *  - PFR: 첫 스트릿에서 본인 좌석이 RAISE/BET/ALL_IN/COMPLETE 한 핸드 비율 (CALL 만 한 핸드는 제외).
 *
 *  분모: 본인 좌석이 살아있는 핸드 (= 모든 기록된 핸드) 수.
 */
data class StatsOverview(
    val totalHands: Int,
    val handsWon: Int,
    val winrate: Double,
    val totalPotChips: Long,
    val biggestPot: Long,
    val vpip: Double,      // 0.0 ~ 1.0
    val pfr: Double,       // 0.0 ~ 1.0
    val byMode: Map<String, ModeStats>,
    /** Phase D: 최근 핸드 시간 순 rolling-N winrate (0~1). 최대 [TREND_MAX_POINTS] 개. */
    val winrateTrend: List<Double> = emptyList(),
) {
    companion object {
        const val TREND_WINDOW: Int = 5
        const val TREND_MAX_POINTS: Int = 30

        val EMPTY = StatsOverview(
            totalHands = 0,
            handsWon = 0,
            winrate = 0.0,
            totalPotChips = 0L,
            biggestPot = 0L,
            vpip = 0.0,
            pfr = 0.0,
            byMode = emptyMap(),
            winrateTrend = emptyList(),
        )
    }
}

data class ModeStats(
    val hands: Int,
    val wonHands: Int,
    val winrate: Double,
    val totalPotChips: Long,
    val biggestPot: Long,
    val vpip: Double,
    val pfr: Double,
)

/** VPIP 카운팅 대상 액션 — 자발적 칩 commit. */
internal val VPIP_ACTIONS: Set<ActionType> = setOf(
    ActionType.CALL,
    ActionType.BET,
    ActionType.RAISE,
    ActionType.ALL_IN,
    ActionType.COMPLETE,
)

/** PFR 카운팅 대상 액션 — 자발적 raise (call 단순참여 제외). */
internal val PFR_ACTIONS: Set<ActionType> = setOf(
    ActionType.BET,
    ActionType.RAISE,
    ActionType.ALL_IN,
    ActionType.COMPLETE,
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
        var vpipHands = 0
        var pfrHands = 0
        val modeAgg: MutableMap<String, MutableModeAgg> = mutableMapOf()

        for (r in records) {
            val humanSeat = r.initialState.players.firstOrNull { it.isHuman }?.seat
            val won = humanSeat != null && r.winnerSeat == humanSeat
            if (won) wonTotal++
            potTotal += r.potSize
            if (r.potSize > biggestPot) biggestPot = r.potSize

            val (didVpip, didPfr) = computeVpipPfr(r, humanSeat)
            if (didVpip) vpipHands++
            if (didPfr) pfrHands++

            val agg = modeAgg.getOrPut(r.mode) { MutableModeAgg() }
            agg.hands++
            if (won) agg.wonHands++
            agg.totalPot += r.potSize
            if (r.potSize > agg.biggestPot) agg.biggestPot = r.potSize
            if (didVpip) agg.vpipHands++
            if (didPfr) agg.pfrHands++
        }

        val total = records.size
        return StatsOverview(
            totalHands = total,
            handsWon = wonTotal,
            winrate = wonTotal.toDouble() / total,
            totalPotChips = potTotal,
            biggestPot = biggestPot,
            vpip = vpipHands.toDouble() / total,
            pfr = pfrHands.toDouble() / total,
            byMode = modeAgg.mapValues { (_, v) ->
                ModeStats(
                    hands = v.hands,
                    wonHands = v.wonHands,
                    winrate = if (v.hands > 0) v.wonHands.toDouble() / v.hands else 0.0,
                    totalPotChips = v.totalPot,
                    biggestPot = v.biggestPot,
                    vpip = if (v.hands > 0) v.vpipHands.toDouble() / v.hands else 0.0,
                    pfr = if (v.hands > 0) v.pfrHands.toDouble() / v.hands else 0.0,
                )
            },
            winrateTrend = computeWinrateTrend(records),
        )
    }

    /**
     * Phase D: 최근 [StatsOverview.TREND_MAX_POINTS] 핸드 시간 순서 rolling-N winrate.
     * records 는 보통 DESC (최신부터) 라 startedAt 으로 ASC 정렬 후 sliding window.
     */
    private fun computeWinrateTrend(records: List<HandHistoryRecord>): List<Double> {
        if (records.size < 2) return emptyList()
        val sorted = records
            .sortedBy { it.startedAt }
            .takeLast(StatsOverview.TREND_MAX_POINTS)
        val window = StatsOverview.TREND_WINDOW
        return sorted.indices.map { i ->
            val start = (i - window + 1).coerceAtLeast(0)
            val slice = sorted.subList(start, i + 1)
            val won = slice.count { r ->
                val human = r.initialState.players.firstOrNull { it.isHuman }?.seat
                human != null && r.winnerSeat == human
            }
            won.toDouble() / slice.size
        }
    }

    /**
     * 한 핸드의 본인 좌석 첫 스트릿(streetIndex==0) 액션을 검사해 (vpip, pfr) 이중값 반환.
     * 본인 좌석이 폴드만 한 경우 (=blind/ante 강제 commit 만) 둘 다 false.
     */
    private fun computeVpipPfr(r: HandHistoryRecord, humanSeat: Int?): Pair<Boolean, Boolean> {
        if (humanSeat == null) return false to false
        var vpip = false
        var pfr = false
        for (entry in r.actions) {
            if (entry.streetIndex != 0) continue
            if (entry.seat != humanSeat) continue
            val type = entry.action.type
            if (type in VPIP_ACTIONS) vpip = true
            if (type in PFR_ACTIONS) pfr = true
            if (vpip && pfr) break
        }
        return vpip to pfr
    }

    private class MutableModeAgg {
        var hands: Int = 0
        var wonHands: Int = 0
        var totalPot: Long = 0L
        var biggestPot: Long = 0L
        var vpipHands: Int = 0
        var pfrHands: Int = 0
    }
}
