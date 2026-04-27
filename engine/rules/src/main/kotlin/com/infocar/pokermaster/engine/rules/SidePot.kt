package com.infocar.pokermaster.engine.rules

/**
 * 사이드팟 계산 입력. 한 핸드의 모든 플레이어 베팅 누적·폴드 여부.
 *
 * @param seat 좌석 인덱스 (홀수칩 좌석순 분배에 사용)
 * @param committed 이번 핸드 누적 베팅 (앤티 + 모든 스트릿 베팅 합계)
 * @param folded 폴드 여부 — 폴드해도 본인이 적립한 칩은 자기 layer 까지 메인/하위 사이드팟에 기여
 */
data class PlayerCommitment(
    val seat: Int,
    val committed: Long,
    val folded: Boolean,
)

/** 단일 사이드팟. amount = 모인 칩, eligibleSeats = 자격 좌석(폴드 제외, layer 이상 적립). */
data class Pot(
    val amount: Long,
    val eligibleSeats: Set<Int>,
)

/**
 * 사이드팟 계산 결과. 칩 보존성을 타입으로 강제:
 *  Σ pots.amount + deadMoney + uncalledReturn.values = Σ input.committed
 *
 *  - [pots]: 자격자가 1명 이상인 사이드팟. 분배는 [ShowdownResolver] 가 담당.
 *  - [uncalledReturn]: 자기 혼자 적립한 layer (콜 받지 못한 베팅) 는 그 좌석에 즉시 환급.
 *  - [deadMoney]: 모든 적립자가 폴드해 자격자가 0인 layer 의 칩 (현실 카지노에선 다음 핸드 이월).
 *    싱글플레이 v1 에선 사라진 칩으로 본다. 통계용으로만 노출.
 */
data class SidePotResult(
    val pots: List<Pot>,
    val uncalledReturn: Map<Int, Long>,
    val deadMoney: Long,
)

/**
 * v1.1 §3.1 사이드팟 알고리즘. 칩 보존성 + uncalled bet + dead money 분리.
 */
object SidePotCalculator {

    fun compute(commitments: List<PlayerCommitment>): SidePotResult {
        if (commitments.isEmpty()) return SidePotResult(emptyList(), emptyMap(), 0L)

        val levels = commitments
            .map { it.committed }
            .filter { it > 0 }
            .distinct()
            .sorted()
        if (levels.isEmpty()) return SidePotResult(emptyList(), emptyMap(), 0L)

        val pots = mutableListOf<Pot>()
        val uncalled = mutableMapOf<Int, Long>()
        var dead = 0L
        var prev = 0L

        for (level in levels) {
            // 이 layer 두께만큼 각 플레이어가 기여한 칩 합
            val potAmount = commitments.sumOf { p ->
                minOf(p.committed, level) - minOf(p.committed, prev)
            }

            // 이 layer 에 실제로 칩을 보탠 좌석. 자격자가 1명이어도 기여자가 여럿이면
            // 환급이 아니라 남은 자격자가 이기는 contested pot 이다.
            val contributors = commitments
                .filter { p -> minOf(p.committed, level) - minOf(p.committed, prev) > 0L }
                .map { it.seat }
                .toSet()

            // 자격자: 이 레벨 이상 적립 + 살아있음 (폴드 X)
            val eligible = commitments
                .filter { it.committed >= level && !it.folded }
                .map { it.seat }
                .toSet()

            when {
                potAmount == 0L -> { /* skip */ }
                eligible.isEmpty() -> {
                    // 자격자 0 (모두 폴드한 layer) → 다음 핸드 이월 / dead money
                    dead += potAmount
                }
                eligible.size == 1 && contributors.size == 1 -> {
                    // 자기 혼자 적립한 layer → uncalled bet, 즉시 환급
                    val onlySeat = eligible.first()
                    uncalled.merge(onlySeat, potAmount) { a, b -> a + b }
                }
                else -> {
                    pots += Pot(amount = potAmount, eligibleSeats = eligible)
                }
            }
            prev = level
        }

        return SidePotResult(pots = pots, uncalledReturn = uncalled, deadMoney = dead)
    }
}
