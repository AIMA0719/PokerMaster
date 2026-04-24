package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.standardDeck
import com.infocar.pokermaster.engine.rules.HandEvaluator7Stud
import com.infocar.pokermaster.engine.rules.HandEvaluatorHiLo
import com.infocar.pokermaster.engine.rules.HandEvaluatorHoldem
import com.infocar.pokermaster.engine.rules.HandValue
import com.infocar.pokermaster.engine.rules.LowValue
import kotlin.random.Random

/**
 * 몬테카를로 equity 계산기.
 *
 *  - 본인 hole + 알려진 카드(보드, 상대 업카드)를 고정한 채 미지의 카드를 무작위로 채워 시뮬.
 *  - 1 시행 결과: 1.0 (본인 단독 승) / 0.5 (분할) / 0.0 (패).
 *  - equity = 평균.
 *
 *  결정론성: [seed] 주입 시 동일 결과. 매 호출마다 Random(seed) 신규 인스턴스 — thread-safe.
 *
 *  v1: Hold'em / 7-Stud (단순화) / 7-Stud Hi-Lo (hi=0.5, lo=0.5 정규화 모델, scoop = 1.0).
 *  Hi-Lo 모델 한계: 상대 다운카드 conditional sampling 미적용 (균등 random).
 */
class EquityCalculator(private val seed: Long? = null) {

    /**
     * 텍사스 홀덤 equity. board 0~5 장 알려진 상태에서 random opp hole + 잔여 board 시뮬.
     *
     * @param hole 본인 홀카드 2장
     * @param board 현재 공개된 커뮤니티 카드 (0~5)
     * @param opponents 활성 상대 수 (1~9)
     * @param iterations 시뮬 횟수 (기본 5,000 — 중급 단말 < 50ms 목표)
     */
    fun holdemEquity(
        hole: List<Card>,
        board: List<Card> = emptyList(),
        opponents: Int,
        iterations: Int = 5_000,
    ): Double {
        require(hole.size == 2) { "Hold'em hole must be exactly 2 cards" }
        require(board.size in 0..5) { "Board must be 0~5 cards" }
        // M7-BugFix: 상대 0 = 본인 단독 생존. crash 대신 1.0.
        if (opponents <= 0) return 1.0

        val known = (hole + board).toSet()
        require(known.size == hole.size + board.size) { "Duplicate cards in hole/board" }

        val deck = standardDeck() - known
        val needBoard = 5 - board.size
        // M7-BugFix: 9명 초과/덱 부족 엣지에선 합리적 fallback 반환 (crash 대신 균등 추정).
        val capped = opponents.coerceIn(1, 9)
        val needTotal = needBoard + capped * 2
        if (deck.size < needTotal) return 1.0 / (capped + 1.0)

        // 매 호출마다 신규 Random 인스턴스 — thread-safe (동일 EquityCalculator 인스턴스 동시 호출 안전)
        val rng = Random(seed ?: System.nanoTime())
        var sum = 0.0
        val deckArr = deck.toMutableList()
        val effectiveOpponents = capped

        repeat(iterations) {
            // partial Fisher-Yates: 앞 needTotal 장만 셔플
            shufflePrefix(deckArr, needTotal, rng)
            val newBoard = if (needBoard > 0) board + deckArr.subList(0, needBoard) else board
            var pos = needBoard

            val myValue = HandEvaluatorHoldem.evaluate(hole, newBoard)
            var bestOpp: HandValue = HandValue.MIN
            var ties = 0

            for (op in 0 until effectiveOpponents) {
                val oppHole = listOf(deckArr[pos], deckArr[pos + 1])
                pos += 2
                val v = HandEvaluatorHoldem.evaluate(oppHole, newBoard)
                val cmp = v.compareTo(bestOpp)
                if (cmp > 0) {
                    bestOpp = v
                    ties = 1
                } else if (cmp == 0) {
                    ties++
                }
            }

            val cmpMe = myValue.compareTo(bestOpp)
            sum += when {
                cmpMe > 0 -> 1.0                          // 본인 단독 승
                cmpMe == 0 -> 1.0 / (ties + 1)            // 분할 (본인 포함)
                else -> 0.0
            }
        }
        return sum / iterations
    }

    /**
     * 7-Stud equity (단순화 버전).
     *
     * 본인 0~7 장 + 알려진 상대 업카드들. 미지 카드를 무작위 채워 7장씩 평가.
     * Hi-Lo 모드에서는 [hiLoSplit]=true 로 hi/lo 점유율 평균.
     *
     * @param mySeven 본인 hole+up (현재 보유 카드, 1~7장). 7장 미만이면 random fill.
     * @param knownOppUpCards 상대별 업카드 (모르면 빈 리스트). 길이는 상대당 1~4 장.
     */
    fun sevenStudEquity(
        mySeven: List<Card>,
        knownOppUpCards: List<List<Card>>,
        iterations: Int = 5_000,
        hiLoSplit: Boolean = false,
    ): Double {
        require(mySeven.size in 1..7) { "My cards must be 1~7" }
        val opponents = knownOppUpCards.size
        // M7-BugFix: 상대 0 = 본인 단독 생존, 1.0. 7명 초과/덱 부족 시 균등 fallback.
        if (opponents <= 0) return 1.0
        if (opponents > 7) return 1.0 / (opponents + 1.0)
        knownOppUpCards.forEach {
            require(it.size in 0..4) { "Each opponent up cards must be 0~4" }
        }

        val known = mySeven.toMutableSet().apply { addAll(knownOppUpCards.flatten()) }
        require(known.size == mySeven.size + knownOppUpCards.sumOf { it.size }) {
            "Duplicate cards"
        }

        val deck = standardDeck() - known
        val needForMe = 7 - mySeven.size
        val needForOpps = knownOppUpCards.sumOf { 7 - it.size }
        val needTotal = needForMe + needForOpps
        if (deck.size < needTotal) return 1.0 / (opponents + 1.0)

        val rng = Random(seed ?: System.nanoTime())
        val deckArr = deck.toMutableList()
        var sum = 0.0

        repeat(iterations) {
            shufflePrefix(deckArr, needTotal, rng)
            var pos = 0
            val mySim = if (needForMe > 0) mySeven + deckArr.subList(pos, pos + needForMe) else mySeven
            pos += needForMe

            if (hiLoSplit) {
                // Hi-Lo: hi 점유율 + lo 점유율을 합산하여 0.0~1.0 로 정규화 (간이 모델)
                val myHi = HandEvaluatorHiLo.evaluateHigh(mySim)
                val myLo = HandEvaluatorHiLo.evaluateLow(mySim)
                var hiBetter = 0; var hiTies = 0; var hiWorse = 0
                var loQualified = myLo != null
                var loBetter = 0; var loTies = 0; var loWorse = 0

                for (opUp in knownOppUpCards) {
                    val opSim = opUp + deckArr.subList(pos, pos + (7 - opUp.size))
                    pos += 7 - opUp.size
                    val opHi = HandEvaluatorHiLo.evaluateHigh(opSim)
                    val cmpHi = myHi.compareTo(opHi)
                    when {
                        cmpHi > 0 -> hiBetter++
                        cmpHi == 0 -> hiTies++
                        else -> hiWorse++
                    }
                    val opLo = HandEvaluatorHiLo.evaluateLow(opSim)
                    if (myLo != null && opLo != null) {
                        val cmpLo = myLo.compareTo(opLo)
                        when {
                            cmpLo < 0 -> loBetter++       // 작을수록 강
                            cmpLo == 0 -> loTies++
                            else -> loWorse++
                        }
                    } else if (myLo != null && opLo == null) {
                        loBetter++
                    } else if (myLo == null && opLo != null) {
                        loWorse++
                    }
                }
                // 팟 절반은 hi, 절반은 lo. scoop = hi+lo 모두 차지 = 1.0 (정확).
                val hiShare = if (hiBetter == opponents) 1.0
                              else if (hiBetter + hiTies == opponents) 1.0 / (hiTies + 1)
                              else 0.0
                val loShare = if (loQualified) {
                    if (loBetter == opponents) 1.0
                    else if (loBetter + loTies == opponents) 1.0 / (loTies + 1)
                    else 0.0
                } else 0.0
                // hi 0.5 + lo 0.5 가중치 — scoop 시 0.5 + 0.5 = 1.0 (전체 팟)
                sum += hiShare * 0.5 + loShare * 0.5
            } else {
                val myValue = HandEvaluator7Stud.evaluateBest(mySim)
                var bestOpp: HandValue = HandValue.MIN
                var ties = 0
                for (opUp in knownOppUpCards) {
                    val opSim = opUp + deckArr.subList(pos, pos + (7 - opUp.size))
                    pos += 7 - opUp.size
                    val v = HandEvaluator7Stud.evaluateBest(opSim)
                    val cmp = v.compareTo(bestOpp)
                    if (cmp > 0) { bestOpp = v; ties = 1 }
                    else if (cmp == 0) { ties++ }
                }
                val cmp = myValue.compareTo(bestOpp)
                sum += when {
                    cmp > 0 -> 1.0
                    cmp == 0 -> 1.0 / (ties + 1)
                    else -> 0.0
                }
            }
        }
        return sum / iterations
    }

    /** 모드 별 dispatch. 단순 헬퍼. */
    fun equity(ctx: GameContext, iterations: Int = 5_000): Double = when (ctx.mode) {
        GameMode.HOLDEM_NL ->
            holdemEquity(ctx.hole, ctx.community, ctx.numActiveOpponents, iterations)
        GameMode.SEVEN_STUD ->
            sevenStudEquity(
                mySeven = ctx.hole + ctx.upCards,
                knownOppUpCards = ctx.opponentSeats.map {
                    ctx.knownOpponentUpCards[it] ?: emptyList()
                },
                iterations = iterations,
                hiLoSplit = false,
            )
        GameMode.SEVEN_STUD_HI_LO ->
            sevenStudEquity(
                mySeven = ctx.hole + ctx.upCards,
                knownOppUpCards = ctx.opponentSeats.map {
                    ctx.knownOpponentUpCards[it] ?: emptyList()
                },
                iterations = iterations,
                hiLoSplit = true,
            )
    }

    /**
     * Fisher-Yates 의 앞 [k] 장만 셔플 (전체 셔플보다 빠름, 풀에서 무작위 추출과 동등).
     */
    private fun shufflePrefix(arr: MutableList<Card>, k: Int, rng: Random) {
        val n = arr.size
        require(k in 0..n)
        for (i in 0 until k) {
            val j = i + rng.nextInt(n - i)
            if (j != i) {
                val tmp = arr[i]; arr[i] = arr[j]; arr[j] = tmp
            }
        }
    }
}
