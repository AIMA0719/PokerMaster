package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.Declaration

/**
 * 쇼다운 분배기 (v1.1 §3.4.A + §3.1 분배).
 *
 * 단일 [Pot] 에 대해 자격자별 핸드(하이/로우) 를 받아 좌석별 수령액을 산출.
 *
 *  - [hi]: 자격 좌석마다 하이 핸드 평가 결과 (필수)
 *  - [lo]: Hi-Lo 모드일 때만 자격 좌석의 로우 평가 결과 (자격 미달 좌석은 null)
 *  - [declarations]: 한국식 7-Stud Hi-Lo declare 단계 결과. null/empty 면 cards-speak 모드(기존 동작).
 *    Map 이 채워져 있으면 다음 룰 적용:
 *      * HIGH 선언자 = 하이팟만 자격
 *      * LOW 선언자 = 로우팟만 자격 (8-or-better qualifier 미달 시 lo 자격 박탈)
 *      * SWING 선언자 = 양쪽 모두 동률 또는 단독 1위여야 챙김. 한쪽이라도 더 강한 비-SWING declarer 가
 *        있으면 SWING declarer 는 양쪽 모두 자격 박탈.
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
        declarations: Map<Int, Declaration>? = null,
    ): Map<Int, Long> {
        if (pot.amount == 0L) return emptyMap()
        val payouts = mutableMapOf<Int, Long>()

        if (!hiLoSplit) {
            // High-only: 자격자 중 베스트 hi 한 명 (또는 동률) 분할
            val winners = bestSeats(pot.eligibleSeats, hi)
            distribute(pot.amount, winners, seatOrderForOdd, payouts)
            return payouts
        }

        // declare 모드(한국식 7-Stud Hi-Lo)
        if (!declarations.isNullOrEmpty()) {
            return resolveDeclare(pot, hi, lo, seatOrderForOdd, declarations)
        }

        // Hi-Lo cards-speak: qualifier 검증을 사이드팟별로 독립 산정 (v1.1 §3.4.A)
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
     * 한국식 declare 모드 단일 팟 분배.
     *
     *  1. SWING 후보: 양쪽 후보군에서 자기 핸드가 동률 또는 더 강해야 자격. 한쪽이라도 비-SWING 중에
     *     자기보다 강한 핸드(>) 가 있으면 그 SWING 은 양쪽 자격 박탈.
     *  2. 자격 SWING 이 hi 우승 또는 lo 우승에 포함될 수 있음. 자격 박탈된 SWING 은 hi/lo 양쪽에서 제외.
     *  3. high pot 후보 = HIGH + (자격 유지) SWING. low pot 후보 = LOW + (자격 유지) SWING 중
     *     8-qualifier 만족자.
     *  4. 어느 한쪽 후보가 비면 다른 쪽이 팟 전체 (cards-speak 의 "lo 0 → hi scoop" 룰 재활용).
     *  5. 양쪽 모두 비면 (이론상 폴드 안 한 모두가 SWING 인데 동률도 아님 — 일반적으론 발생X)
     *     hi-only 로 fallback (최약한 후보라도 hi 비교).
     */
    private fun resolveDeclare(
        pot: Pot,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue?>,
        seatOrderForOdd: List<Int>,
        declarations: Map<Int, Declaration>,
    ): Map<Int, Long> {
        val payouts = mutableMapOf<Int, Long>()
        val eligible = pot.eligibleSeats

        // 좌석별 declare; pot eligible 안에서만 본다.
        val highDeclarers = eligible.filter { declarations[it] == Declaration.HIGH }
        val lowDeclarers = eligible.filter { declarations[it] == Declaration.LOW }
        val swingDeclarers = eligible.filter { declarations[it] == Declaration.SWING }

        // 1) SWING 자격 검증
        // 비-SWING declarer 중 hi/lo 각 방향에서 SWING 보다 strict 하게 강한 좌석이 있는지 본다.
        // 동률은 OK (= "tie or beat").
        val nonSwingHiCandidates = highDeclarers + swingDeclarers // SWING 자격 검증 시점에는 SWING 본인도 후보
        val nonSwingLoCandidates = lowDeclarers // SWING 자격 박탈 검증에서 비교 대상은 LOW declarer만
            .filter { lo[it] != null }

        // SWING 자격: 양쪽 모두에서 (자기 핸드) >= max(다른 declarers) 여야 통과.
        val swingDisqualified = mutableSetOf<Int>()
        for (s in swingDeclarers) {
            val myHi = hi[s]
            val myLo = lo[s]
            // hi 비교 — 다른 HIGH/SWING(자기 제외) 누구라도 myHi 보다 strict 하게 강하면 박탈.
            val hiBeaten = nonSwingHiCandidates
                .filter { it != s }
                .any { other -> hi[other]?.let { it > (myHi ?: HandValue.MIN) } == true }
            // lo 비교 — myLo 가 8-qualifier 미달(null) 이면 자동 박탈.
            // 또는 다른 LOW/SWING 중 자기보다 strict 하게 강한(작은) lo 가 있으면 박탈.
            val otherSwingLoCandidates = swingDeclarers.filter { it != s && lo[it] != null }
            val loBeaten = if (myLo == null) {
                true
            } else {
                (nonSwingLoCandidates + otherSwingLoCandidates)
                    .any { other -> lo[other]?.let { it < myLo } == true }
            }
            if (hiBeaten || loBeaten) swingDisqualified.add(s)
        }
        val qualifiedSwing = swingDeclarers - swingDisqualified

        // 2) 후보군 정리
        val hiPool = (highDeclarers + qualifiedSwing).toSet()
        val loPool = (lowDeclarers + qualifiedSwing).filter { lo[it] != null }.toSet()

        val hiWinners = bestSeats(hiPool, hi)
        val loWinners = bestLowSeats(loPool, lo)

        return when {
            hiWinners.isNotEmpty() && loWinners.isNotEmpty() -> {
                val half = pot.amount / 2
                val oddChip = pot.amount - half * 2
                val hiAmount = half + oddChip
                val loAmount = half
                distribute(hiAmount, hiWinners, seatOrderForOdd, payouts)
                distribute(loAmount, loWinners, seatOrderForOdd, payouts)
                payouts
            }
            hiWinners.isNotEmpty() -> {
                distribute(pot.amount, hiWinners, seatOrderForOdd, payouts)
                payouts
            }
            loWinners.isNotEmpty() -> {
                distribute(pot.amount, loWinners, seatOrderForOdd, payouts)
                payouts
            }
            else -> {
                // 양쪽 모두 후보 0 — fallback: 폴드 안 한 eligible 중 베스트 hi (cards-speak).
                val fallback = bestSeats(eligible, hi)
                distribute(pot.amount, fallback, seatOrderForOdd, payouts)
                payouts
            }
        }
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
        declarations: Map<Int, Declaration>? = null,
    ): Map<Int, Long> {
        val total = mutableMapOf<Int, Long>()
        for (pot in result.pots) {
            val payouts = resolve(pot, hi, lo, seatOrderForOdd, hiLoSplit, declarations)
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
