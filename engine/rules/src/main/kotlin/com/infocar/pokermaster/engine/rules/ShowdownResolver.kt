package com.infocar.pokermaster.engine.rules

/**
 * 쇼다운 분배기 (v1.1 §3.4.A + §3.1 분배).
 *
 * 단일 [Pot] 에 대해 자격자별 핸드(하이/로우) 를 받아 좌석별 수령액을 산출.
 *
 *  - [hi]: 자격 좌석마다 하이 핸드 평가 결과 (필수)
 *  - [lo]: Hi-Lo 모드일 때만 자격 좌석의 로우 평가 결과 (자격 미달 좌석은 null)
 *  - 홀수칩: 1/2 분배 시 hi 측 우선 → 그 안에서 [seatOrderForOdd] 첫 번째 좌석.
 *    [seatOrderForOdd] 는 BTN 좌측부터 시계방향 좌석 리스트(살아있는 좌석만 또는 전체).
 *
 * 본 분배는 단일 팟 단위. 메인/사이드 다중 팟은 호출자가 [SidePotResult.pots] 를 순회하며 각각 호출.
 */
object ShowdownResolver {

    fun resolve(
        pot: Pot,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue?> = emptyMap(),
        seatOrderForOdd: List<Int> = pot.eligibleSeats.sorted(),
        hiLoSplit: Boolean = false,
    ): Map<Int, Long> {
        if (pot.amount == 0L) return emptyMap()
        val payouts = mutableMapOf<Int, Long>()

        if (!hiLoSplit) {
            // High-only: 자격자 중 베스트 hi 한 명 (또는 동률) 분할
            val winners = bestSeats(pot.eligibleSeats, hi)
            distribute(pot.amount, winners, seatOrderForOdd, payouts)
            return payouts
        }

        // Hi-Lo: qualifier 검증을 사이드팟별로 독립 산정 (v1.1 §3.4.A)
        val loWinners = bestLowSeats(pot.eligibleSeats, lo)
        if (loWinners.isEmpty()) {
            // 로우 자격자 0 → 하이가 팟 전체
            val winners = bestSeats(pot.eligibleSeats, hi)
            distribute(pot.amount, winners, seatOrderForOdd, payouts)
            return payouts
        }

        // 절반씩, 홀수칩은 hi 우선 (v1.1 §3.4.B)
        val half = pot.amount / 2
        val oddChip = pot.amount - half * 2
        val hiAmount = half + oddChip   // 1 chip 잔여 → hi 측
        val loAmount = half

        val hiWinners = bestSeats(pot.eligibleSeats, hi)
        distribute(hiAmount, hiWinners, seatOrderForOdd, payouts)
        distribute(loAmount, loWinners, seatOrderForOdd, payouts)
        return payouts
    }

    /**
     * 다중 팟(메인 + 사이드) 일괄 분배. [SidePotResult.pots] 모두 처리한 후 [SidePotResult.uncalledReturn]
     * 까지 합산해 좌석별 최종 수령액을 리턴.
     */
    fun resolveAll(
        result: SidePotResult,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue?> = emptyMap(),
        seatOrderForOdd: List<Int>,
        hiLoSplit: Boolean = false,
    ): Map<Int, Long> {
        val total = mutableMapOf<Int, Long>()
        for (pot in result.pots) {
            val payouts = resolve(pot, hi, lo, seatOrderForOdd, hiLoSplit)
            for ((seat, amount) in payouts) {
                total.merge(seat, amount) { a, b -> a + b }
            }
        }
        for ((seat, amount) in result.uncalledReturn) {
            total.merge(seat, amount) { a, b -> a + b }
        }
        return total
    }

    private fun bestSeats(eligible: Set<Int>, hi: Map<Int, HandValue>): List<Int> {
        val candidates = eligible.mapNotNull { seat ->
            hi[seat]?.let { seat to it }
        }
        if (candidates.isEmpty()) return emptyList()
        val maxValue = candidates.maxOf { it.second }
        return candidates.filter { it.second.compareTo(maxValue) == 0 }.map { it.first }
    }

    private fun bestLowSeats(eligible: Set<Int>, lo: Map<Int, LowValue?>): List<Int> {
        val candidates = eligible.mapNotNull { seat ->
            lo[seat]?.let { seat to it }
        }
        if (candidates.isEmpty()) return emptyList()
        // LowValue 는 작을수록 강함
        val minValue = candidates.minOf { it.second }
        return candidates.filter { it.second.compareTo(minValue) == 0 }.map { it.first }
    }

    /**
     * [amount] 를 [winners] 에 균등 분배. 홀수칩은 [seatOrder] 에서 winners 중 가장 앞선 좌석에 우선.
     * 다중 동률(N명) 의 잔여 chips 는 N-1, N-2 ... 순으로 좌석순 배정.
     */
    private fun distribute(
        amount: Long,
        winners: List<Int>,
        seatOrder: List<Int>,
        out: MutableMap<Int, Long>,
    ) {
        if (winners.isEmpty() || amount == 0L) return
        val n = winners.size.toLong()
        val base = amount / n
        var remainder = amount - base * n
        // base 분배
        for (s in winners) out.merge(s, base) { a, b -> a + b }
        // remainder 좌석순 1칩씩 분배
        if (remainder > 0) {
            val orderedWinners = seatOrder.filter { it in winners }
            for (s in orderedWinners) {
                if (remainder <= 0) break
                out.merge(s, 1L) { a, b -> a + b }
                remainder--
            }
        }
    }
}
