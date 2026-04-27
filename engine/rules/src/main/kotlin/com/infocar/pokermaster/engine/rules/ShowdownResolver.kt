package com.infocar.pokermaster.engine.rules

import com.infocar.pokermaster.core.model.DeclareDirection

/**
 * 쇼다운 분배기 (v1.1 §3.4.A + §3.1 분배).
 *
 * 단일 [Pot] 에 대해 자격자별 핸드(하이/로우) 를 받아 좌석별 수령액을 산출.
 *
 *  - [resolve] / [resolveAll]: 홀덤 + 비-Declare 7스터드용 (8-or-better Hi-Lo 포함).
 *  - [resolveDeclare] / [resolveAllDeclare]: 한국식 Declare Hi-Lo 용 (HI/LO/BOTH 선언 기반).
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

    // -----------------------------------------------------------------------------------------------
    // 한국식 Declare Hi-Lo 분배
    // -----------------------------------------------------------------------------------------------

    /**
     * 한국식 Declare Hi-Lo 단일 팟 분배.
     *
     * 알고리즘:
     *  1. eligibleDeclarations = [declarations] 중 [Pot.eligibleSeats] 에 속한 좌석만.
     *  2. 초기 HI 후보 = HI 또는 BOTH 선언 좌석. 초기 LO 후보 = LO 또는 BOTH 선언 좌석.
     *  3. 각 방향에서 best 핸드 보유 좌석들이 winner (동률 허용).
     *  4. BOTH 선언자가 HI 단독 1등 AND LO 단독 1등이면 scoop. (sole-1st 두 방향 모두)
     *  5. scoop 이 아닌 BOTH 선언자(어느 방향이든 동률/패배): forfeit — 양쪽 후보집합에서 제거하고
     *     남은 HI/BOTH ∖ forfeit, LO/BOTH ∖ forfeit 으로 winner 재계산.
     *  6. Edge case: 모두 BOTH 선언했고 전부 forfeit 되어 양쪽 후보집합이 비면
     *     → 칩 손실 방지를 위해 원래 BOTH 선언자 전원에게 균등 분배 (소수의 칩 사라짐 방지).
     *  7. 분배:
     *     - scoop 1좌석이면 그 좌석이 팟 전체 수령.
     *     - 아니면 HI 측 = 팟/2 + 홀수칩, LO 측 = 팟/2.
     *     - 한쪽 후보가 비면(예: 모두 HI 선언) 다른쪽이 팟 전체.
     *
     * @param hi eligibleSeats 모든 살아있는 좌석에 대해 HandValue 가 있어야 함.
     * @param lo eligibleSeats 모든 살아있는 좌석에 대해 LowValue 가 있어야 함 (한국식: 항상 non-null).
     * @param declarations eligibleSeats 모든 살아있는 좌석에 대해 [DeclareDirection] 이 있어야 함.
     */
    fun resolveDeclare(
        pot: Pot,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue>,
        declarations: Map<Int, DeclareDirection>,
        seatOrderForOdd: List<Int> = pot.eligibleSeats.sorted(),
    ): ResolveDeclareOutcome {
        if (pot.amount == 0L) {
            return ResolveDeclareOutcome(emptyMap(), emptySet(), emptySet(), emptySet())
        }
        val payouts = mutableMapOf<Int, Long>()

        // 1) 자격 선언자만 추림
        val eligibleDecl: Map<Int, DeclareDirection> = declarations
            .filterKeys { it in pot.eligibleSeats }

        if (eligibleDecl.isEmpty()) {
            // 안전 가드 — 선언자가 한 명도 없으면 분배 불가, 빈 결과 반환.
            return ResolveDeclareOutcome(emptyMap(), emptySet(), emptySet(), emptySet())
        }

        val originalBothDeclarers = eligibleDecl.filterValues { it == DeclareDirection.BOTH }.keys

        // 2) 초기 후보집합
        val initialHiCandidates = eligibleDecl
            .filterValues { it == DeclareDirection.HI || it == DeclareDirection.BOTH }
            .keys
        val initialLoCandidates = eligibleDecl
            .filterValues { it == DeclareDirection.LO || it == DeclareDirection.BOTH }
            .keys

        // 3) 초기 winner 계산
        val initialHiWinners = bestHiAmong(initialHiCandidates, hi)
        val initialLoWinners = bestLoAmong(initialLoCandidates, lo)

        // 4) BOTH-strict scoop 식별: 어떤 BOTH 선언자가 HI 단독 1등 AND LO 단독 1등?
        val scoopWinners: Set<Int> = originalBothDeclarers.filter { seat ->
            initialHiWinners.size == 1 && initialHiWinners.contains(seat) &&
                initialLoWinners.size == 1 && initialLoWinners.contains(seat)
        }.toSet()

        // 5) scoop 이 아닌 BOTH 선언자 → forfeit
        val forfeited: Set<Int> = originalBothDeclarers - scoopWinners

        // 분배: scoop 이면 그 좌석 전체 수령
        if (scoopWinners.isNotEmpty()) {
            // sole-1st 검증으로 정의상 1좌석
            val winner = scoopWinners.single()
            payouts[winner] = pot.amount
            return ResolveDeclareOutcome(
                payouts = payouts,
                hiWinners = initialHiWinners.toSet(),
                loWinners = initialLoWinners.toSet(),
                scoopWinners = scoopWinners,
            )
        }

        // 6) forfeit 적용해 winner 재계산
        val finalHiCandidates = initialHiCandidates - forfeited
        val finalLoCandidates = initialLoCandidates - forfeited
        val finalHiWinners = bestHiAmong(finalHiCandidates, hi).toSet()
        val finalLoWinners = bestLoAmong(finalLoCandidates, lo).toSet()

        // Edge case: 양쪽 후보 모두 비었음 — 모두 BOTH 선언 + 전부 forfeit
        // 칩 손실 방지: 원래 BOTH 선언자 전원에게 균등 분배.
        if (finalHiCandidates.isEmpty() && finalLoCandidates.isEmpty()) {
            val fallback = originalBothDeclarers.toList()
            distribute(pot.amount, fallback, seatOrderForOdd, payouts)
            return ResolveDeclareOutcome(
                payouts = payouts,
                hiWinners = emptySet(),
                loWinners = emptySet(),
                scoopWinners = emptySet(),
            )
        }

        // 7) 한쪽이 비면 반대쪽이 전체 팟 (예: 모두 HI 선언)
        when {
            finalLoCandidates.isEmpty() -> {
                distribute(pot.amount, finalHiWinners.toList(), seatOrderForOdd, payouts)
                return ResolveDeclareOutcome(
                    payouts = payouts,
                    hiWinners = finalHiWinners,
                    loWinners = emptySet(),
                    scoopWinners = emptySet(),
                )
            }
            finalHiCandidates.isEmpty() -> {
                distribute(pot.amount, finalLoWinners.toList(), seatOrderForOdd, payouts)
                return ResolveDeclareOutcome(
                    payouts = payouts,
                    hiWinners = emptySet(),
                    loWinners = finalLoWinners,
                    scoopWinners = emptySet(),
                )
            }
        }

        // 양쪽 후보 모두 살아있음 → 50:50 분할 (홀수칩 hi 우선)
        val half = pot.amount / 2
        val oddChip = pot.amount - half * 2
        val hiAmount = half + oddChip
        val loAmount = half
        distribute(hiAmount, finalHiWinners.toList(), seatOrderForOdd, payouts)
        distribute(loAmount, finalLoWinners.toList(), seatOrderForOdd, payouts)

        return ResolveDeclareOutcome(
            payouts = payouts,
            hiWinners = finalHiWinners,
            loWinners = finalLoWinners,
            scoopWinners = emptySet(),
        )
    }

    /**
     * 다중 팟(메인 + 사이드) 일괄 Declare Hi-Lo 분배.
     * [SidePotResult.pots] 모두 처리한 후 [SidePotResult.uncalledReturn] 합산.
     */
    fun resolveAllDeclare(
        result: SidePotResult,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue>,
        declarations: Map<Int, DeclareDirection>,
        seatOrderForOdd: List<Int>,
    ): ResolveAllDeclareOutcome {
        val total = mutableMapOf<Int, Long>()
        val perPot = mutableListOf<PotDeclareOutcome>()
        result.pots.forEachIndexed { index, pot ->
            val outcome = resolveDeclare(pot, hi, lo, declarations, seatOrderForOdd)
            for ((seat, amount) in outcome.payouts) {
                total.merge(seat, amount) { a, b -> a + b }
            }
            perPot += PotDeclareOutcome(
                potIndex = index,
                hiWinners = outcome.hiWinners,
                loWinners = outcome.loWinners,
                scoopWinners = outcome.scoopWinners,
            )
        }
        for ((seat, amount) in result.uncalledReturn) {
            total.merge(seat, amount) { a, b -> a + b }
        }
        return ResolveAllDeclareOutcome(payouts = total, perPotOutcomes = perPot)
    }

    // -----------------------------------------------------------------------------------------------
    // 내부 헬퍼
    // -----------------------------------------------------------------------------------------------

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

    private fun bestHiAmong(eligible: Set<Int>, hi: Map<Int, HandValue>): List<Int> {
        if (eligible.isEmpty()) return emptyList()
        val candidates = eligible.mapNotNull { seat -> hi[seat]?.let { seat to it } }
        if (candidates.isEmpty()) return emptyList()
        val maxValue = candidates.maxOf { it.second }
        return candidates.filter { it.second.compareTo(maxValue) == 0 }.map { it.first }
    }

    private fun bestLoAmong(eligible: Set<Int>, lo: Map<Int, LowValue>): List<Int> {
        if (eligible.isEmpty()) return emptyList()
        val candidates = eligible.mapNotNull { seat -> lo[seat]?.let { seat to it } }
        if (candidates.isEmpty()) return emptyList()
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

/**
 * Declare Hi-Lo 단일 팟 분배 결과.
 *
 * @param payouts 좌석별 수령 칩.
 * @param hiWinners 최종 HI 측 승자(0~N). scoop 일 때는 초기 단독 HI 승자(=scoopWinner) 를 포함.
 *   forfeit 후 후보가 비면 emptySet().
 * @param loWinners 최종 LO 측 승자(0~N). 위와 동일 규칙.
 * @param scoopWinners BOTH 선언이 sole-1st 두 방향 모두 통과한 좌석. 정의상 size = 0 또는 1.
 */
data class ResolveDeclareOutcome(
    val payouts: Map<Int, Long>,
    val hiWinners: Set<Int>,
    val loWinners: Set<Int>,
    val scoopWinners: Set<Int>,
)

/**
 * Declare Hi-Lo 다중 팟 분배 결과.
 *
 * @param payouts 좌석별 수령 칩 합계 (uncalledReturn 포함).
 * @param perPotOutcomes 각 팟별 winner 정보 — [SidePotResult.pots] 와 인덱스 동기.
 */
data class ResolveAllDeclareOutcome(
    val payouts: Map<Int, Long>,
    val perPotOutcomes: List<PotDeclareOutcome>,
)

/** 단일 팟의 Declare 결과 (다중 팟 결과 안에서 인덱스로 조회). */
data class PotDeclareOutcome(
    val potIndex: Int,
    val hiWinners: Set<Int>,
    val loWinners: Set<Int>,
    val scoopWinners: Set<Int>,
)
