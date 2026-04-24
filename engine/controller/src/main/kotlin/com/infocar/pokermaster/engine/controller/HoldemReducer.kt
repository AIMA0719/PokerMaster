package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.model.ShowdownHandInfo
import com.infocar.pokermaster.core.model.ShowdownSummary
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.HandEvaluatorHoldem
import com.infocar.pokermaster.engine.rules.HandValue
import com.infocar.pokermaster.engine.rules.PlayerCommitment
import com.infocar.pokermaster.engine.rules.Rng
import com.infocar.pokermaster.engine.rules.ShowdownResolver
import com.infocar.pokermaster.engine.rules.SidePotCalculator

/**
 * 텍사스 홀덤 NL 상태 머신 — 순수 함수 (no coroutines / no IO).
 *
 * 흐름:
 *  - [startHand]: 블라인드, 홀카드 분배, toAct 결정
 *  - [act]: 단일 액션 검증+적용. 라운드 종료 시 자동 스트릿 advance, 끝나면 자동 쇼다운.
 *  - [ackShowdown]: 쇼다운 애니 후 UI 확인 → pending 해제 (다음 [startHand] 를 Controller 가 호출).
 *
 * v1.1 §3.2 반영:
 *  - A. All-in less-than-min-raise → [GameState.reopenAction] = false (이후 라운드는 call/fold 만)
 *  - B. 헤즈업 SB=BTN 분기 (pre-flop first to act = BTN, post-flop first to act = BB)
 *  - BB option: 프리플롭 all-call 시 BB 에게 체크/레이즈 option (actedThisStreet 플래그로 처리)
 */
object HoldemReducer {

    // ------------------------------------------------------- Hand lifecycle

    fun startHand(
        config: TableConfig,
        players: List<PlayerState>,
        prevBtnSeat: Int?,
        rng: Rng,
        handIndex: Long,
        startingVersion: Long,
    ): GameState {
        require(config.mode == GameMode.HOLDEM_NL) { "HoldemReducer only handles HOLDEM_NL" }
        val seated = players.filter { it.chips > 0 }
        require(seated.size >= 2) { "need >=2 players with chips to start hand" }

        // 1) reset per-hand state
        var current = players.map { p ->
            val canPlay = p.chips > 0
            p.copy(
                holeCards = emptyList(),
                upCards = emptyList(),
                committedThisHand = 0L,
                committedThisStreet = 0L,
                folded = !canPlay,
                allIn = false,
                actedThisStreet = false,
            )
        }

        // 2) BTN 이동
        val newBtn = chooseBtnSeat(prevBtnSeat, current)

        // 3) blinds
        val (sbSeat, bbSeat) = blindSeats(newBtn, current)
        current = postBlind(current, sbSeat, config.smallBlind)
        current = postBlind(current, bbSeat, config.bigBlind)

        // 4) hole card distribution — BTN+1 부터 시계방향 두 바퀴
        val order = dealOrder(newBtn, current)
        val holes = order.associateWith { mutableListOf<Card>() }
        var cursor = 0
        repeat(2) {
            for (seat in order) {
                holes[seat]!!.add(rng.deck[cursor++])
            }
        }
        current = current.map { p ->
            if (p.chips > 0 && !p.folded && holes.containsKey(p.seat)) {
                p.copy(holeCards = holes[p.seat]!!.toList())
            } else p
        }

        // 5) first to act
        val liveCount = current.count { !it.folded }
        val firstToAct = if (liveCount == 2) newBtn else nextActiveSeatAfter(bbSeat, current)

        val bb = config.bigBlind
        return GameState(
            mode = config.mode,
            config = config,
            stateVersion = startingVersion + 1,
            handIndex = handIndex,
            players = current,
            btnSeat = newBtn,
            toActSeat = firstToAct,
            street = Street.PREFLOP,
            community = emptyList(),
            betToCall = bb,
            minRaise = bb * 2,
            reopenAction = true,
            lastFullRaiseAmount = bb,
            lastAggressorSeat = bbSeat,
            deckCursor = cursor,
            rngCommitHex = rng.commit.toHex(),
            pendingShowdown = null,
            paused = false,
        )
    }

    // ------------------------------------------------------- Act

    fun act(state: GameState, seat: Int, action: Action, rng: Rng): GameState {
        require(state.pendingShowdown == null) { "hand already ended — call ackShowdown and startHand" }
        require(state.toActSeat == seat) { "not seat $seat's turn (toAct=${state.toActSeat})" }
        val player = state.players.first { it.seat == seat }
        require(player.active) { "player at seat $seat is not active" }

        val s0 = when (action.type) {
            ActionType.FOLD -> applyFold(state, seat)
            ActionType.CHECK -> applyCheck(state, seat)
            ActionType.CALL -> applyCall(state, seat)
            ActionType.BET, ActionType.RAISE -> applyBetOrRaise(state, seat, action)
            ActionType.ALL_IN -> applyAllIn(state, seat)
            else -> error("Action ${action.type} not supported in Holdem")
        }

        // 라이브 1 명 → 즉시 핸드 종료
        if (s0.players.count { !it.folded } == 1) {
            return runShowdown(s0.copy(toActSeat = null))
        }

        // 라운드 종료 검사
        if (isBettingRoundComplete(s0)) {
            return runoutOrShowdown(s0, rng)
        }

        val nextSeat = nextActiveSeatAfter(seat, s0.players)
        return s0.copy(toActSeat = nextSeat, stateVersion = s0.stateVersion + 1)
    }

    /**
     * M7-BugFix: 전원 all-in 이어서 남은 스트릿도 자동 런아웃 해야 하는 경우 연쇄 전진.
     *
     *  - 기존: 한 스트릿만 `advanceToStreet` 후 종료. 새 스트릿에도 액터가 없으면
     *    `toActSeat` 가 inactive seat 을 가리키게 되어 다음 `act()` 가
     *    `require(player.active)` 트립 혹은 UI tick 루프 freeze.
     *  - 수정: 다음 스트릿이 여전히 "모두 all-in" 이면 계속 전진, River 통과 시 쇼다운.
     */
    private fun runoutOrShowdown(s: GameState, rng: Rng): GameState {
        var cur = s
        while (true) {
            val next = when (cur.street) {
                Street.PREFLOP -> Street.FLOP
                Street.FLOP -> Street.TURN
                Street.TURN -> Street.RIVER
                Street.RIVER ->
                    return runShowdown(cur.copy(street = Street.SHOWDOWN, toActSeat = null))
                else -> error("unexpected street ${cur.street}")
            }
            cur = advanceToStreet(cur, next, rng)
            // 새 스트릿에서 누군가 액션 가능하면 그 seat 에 toAct 놔두고 종료.
            if (!isBettingRoundComplete(cur)) return cur
            // 전원 all-in 상태로 라운드가 즉시 완료된 상태 — 다음 스트릿으로 계속.
        }
    }

    fun ackShowdown(state: GameState): GameState {
        require(state.pendingShowdown != null) { "no pending showdown to ack" }
        return state.copy(
            pendingShowdown = null,
            paused = false,
            stateVersion = state.stateVersion + 1,
        )
    }

    // ------------------------------------------------------- Apply

    private fun applyFold(state: GameState, seat: Int): GameState {
        val ps = state.players.map {
            if (it.seat == seat) it.copy(folded = true, actedThisStreet = true) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyCheck(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        // M7-BugFix: 체크 불가(콜 필요) 상태에서 CHECK 오면 CALL 로 강등 — UI stale/race 방어.
        if (me.committedThisStreet < state.betToCall) {
            return applyCall(state, seat)
        }
        val ps = state.players.map {
            if (it.seat == seat) it.copy(actedThisStreet = true) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyCall(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        val rawDelta = state.betToCall - me.committedThisStreet
        // M7-BugFix: "nothing to call" → CHECK 로 강등.
        if (rawDelta <= 0) {
            val ps = state.players.map {
                if (it.seat == seat) it.copy(actedThisStreet = true) else it
            }
            return state.copy(players = ps, stateVersion = state.stateVersion + 1)
        }
        val delta = rawDelta.coerceAtMost(me.chips)
        // me.chips==0 인 active seat 은 invariant 상 불가능이나 방어적으로 noop.
        if (delta <= 0) {
            val ps = state.players.map {
                if (it.seat == seat) it.copy(actedThisStreet = true) else it
            }
            return state.copy(players = ps, stateVersion = state.stateVersion + 1)
        }
        val becomeAllIn = delta == me.chips
        val ps = state.players.map {
            if (it.seat == seat) it.copy(
                chips = it.chips - delta,
                committedThisHand = it.committedThisHand + delta,
                committedThisStreet = it.committedThisStreet + delta,
                allIn = becomeAllIn,
                actedThisStreet = true,
            ) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyBetOrRaise(state: GameState, seat: Int, action: Action): GameState {
        val me = state.players.first { it.seat == seat }
        // M7-BugFix: action 재오픈 불가, stack 소진, invalid target → CALL/CHECK 로 강등.
        if (!state.reopenAction || me.chips == 0L || action.amount <= state.betToCall) {
            return if (state.betToCall > me.committedThisStreet) applyCall(state, seat)
            else applyCheck(state, seat)
        }
        val target = action.amount
        val delta = target - me.committedThisStreet
        if (delta !in 1..me.chips) {
            // 범위 밖 → 가장 가까운 legal action 으로 강등.
            return if (state.betToCall > me.committedThisStreet) applyCall(state, seat)
            else applyCheck(state, seat)
        }

        val wouldBeAllIn = delta == me.chips
        val raiseIncrement = target - state.betToCall
        val baselineRaise = maxOf(state.lastFullRaiseAmount, state.config.bigBlind)
        val isFullRaise = raiseIncrement >= baselineRaise

        if (!isFullRaise && !wouldBeAllIn) {
            // M7-BugFix: less-than-min-raise 인데 all-in 도 아닌 invalid 시도 → CALL 로 강등.
            // (기존 require trip 대신 — UI/LLM 에서 잘못된 amount 를 보내도 크래시 방지.)
            return applyCall(state, seat)
        }

        val newPlayers = state.players.map { p ->
            when {
                p.seat == seat -> p.copy(
                    chips = p.chips - delta,
                    committedThisHand = p.committedThisHand + delta,
                    committedThisStreet = p.committedThisStreet + delta,
                    allIn = wouldBeAllIn,
                    actedThisStreet = true,
                )
                // full raise 시 다른 active 플레이어의 actedThisStreet 리셋 (재응답 요구)
                isFullRaise && p.active && p.seat != seat -> p.copy(actedThisStreet = false)
                else -> p
            }
        }

        return state.copy(
            players = newPlayers,
            betToCall = target,
            minRaise = target + maxOf(raiseIncrement, state.config.bigBlind),
            lastFullRaiseAmount = if (isFullRaise) raiseIncrement else state.lastFullRaiseAmount,
            reopenAction = isFullRaise,
            lastAggressorSeat = seat,
            stateVersion = state.stateVersion + 1,
        )
    }

    private fun applyAllIn(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        // M7-BugFix: chips==0 (이미 all-in) 상태에서 ALL_IN 재투입 → CHECK/CALL 로 강등.
        if (me.chips <= 0) {
            return if (state.betToCall > me.committedThisStreet) applyCall(state, seat)
            else applyCheck(state, seat)
        }
        val target = me.committedThisStreet + me.chips
        return if (target > state.betToCall) {
            applyBetOrRaise(state, seat, Action(ActionType.RAISE, target))
        } else {
            applyCall(state, seat)
        }
    }

    // ------------------------------------------------------- Street advance

    private fun isBettingRoundComplete(state: GameState): Boolean {
        val live = state.players.filter { !it.folded }
        if (live.size < 2) return true
        val actors = live.filter { !it.allIn }
        if (actors.isEmpty()) return true   // 모두 all-in
        return actors.all { it.actedThisStreet && it.committedThisStreet == state.betToCall }
    }

    private fun advanceToStreet(state: GameState, next: Street, rng: Rng): GameState {
        val toAdd = when (next) {
            Street.FLOP -> 3
            Street.TURN -> 1
            Street.RIVER -> 1
            else -> 0
        }
        val newCommunity = state.community + (0 until toAdd).map { rng.deck[state.deckCursor + it] }
        val newCursor = state.deckCursor + toAdd
        val resetPlayers = state.players.map { p ->
            p.copy(
                committedThisStreet = 0L,
                // active 는 actedThisStreet=false, 비활성(folded/allIn)은 true 로 유지 (라운드 종료 판정 영향 없게)
                actedThisStreet = !p.active,
            )
        }
        // post-flop: first to act = SB (btn 다음 alive). heads-up 도 같은 공식 → btn 다음 = non-btn = BB
        val firstActor = nextActiveSeatAfter(state.btnSeat, resetPlayers)
        return state.copy(
            players = resetPlayers,
            community = newCommunity,
            deckCursor = newCursor,
            street = next,
            betToCall = 0L,
            minRaise = state.config.bigBlind,
            reopenAction = true,
            lastFullRaiseAmount = state.config.bigBlind,
            lastAggressorSeat = null,
            toActSeat = firstActor,
            stateVersion = state.stateVersion + 1,
        )
    }

    // ------------------------------------------------------- Showdown

    private fun runShowdown(state: GameState): GameState {
        val live = state.players.filter { !it.folded }

        val commitments = state.players.map {
            PlayerCommitment(seat = it.seat, committed = it.committedThisHand, folded = it.folded)
        }
        val sideResult = SidePotCalculator.compute(commitments)
        val seatOrder = seatOrderFromBtn(state.btnSeat, state.players)

        val payouts: Map<Int, Long>
        val potSummaries: List<PotSummary>
        val handInfos: Map<Int, ShowdownHandInfo>

        if (live.size <= 1) {
            // 단독 승: 모든 팟 + uncalled 는 단독 승자에게 귀속
            val winner = live.firstOrNull()?.seat
            val total = sideResult.pots.sumOf { it.amount }
            val uncalledSum = sideResult.uncalledReturn.values.sum()
            payouts = if (winner != null) {
                val per = mutableMapOf(winner to total)
                // uncalled 는 기존 committer 들에게 각자 환급 (단독 승자에게 재귀속 아님)
                sideResult.uncalledReturn.forEach { (s, amt) -> per.merge(s, amt) { a, b -> a + b } }
                per
            } else emptyMap()
            potSummaries = sideResult.pots.mapIndexed { idx, pot ->
                PotSummary(
                    amount = pot.amount,
                    eligibleSeats = pot.eligibleSeats,
                    winnerSeats = winner?.let { if (it in pot.eligibleSeats) setOf(it) else emptySet() }
                        ?: emptySet(),
                    index = idx,
                )
            }
            handInfos = emptyMap()
        } else {
            val bestHands: Map<Int, HandValue> = live.associate { p ->
                p.seat to HandEvaluatorHoldem.evaluate(p.holeCards, state.community)
            }
            payouts = ShowdownResolver.resolveAll(
                result = sideResult,
                hi = bestHands,
                seatOrderForOdd = seatOrder,
                hiLoSplit = false,
            )
            potSummaries = sideResult.pots.mapIndexed { idx, pot ->
                val winners = winnersForPot(pot.eligibleSeats, bestHands)
                PotSummary(
                    amount = pot.amount,
                    eligibleSeats = pot.eligibleSeats,
                    winnerSeats = winners.toSet(),
                    index = idx,
                )
            }
            handInfos = live.associate { p ->
                val hv = bestHands[p.seat]!!
                p.seat to ShowdownHandInfo(
                    seat = p.seat,
                    categoryName = hv.category.korean,
                    bestFive = hv.cards,
                )
            }
        }

        val newPlayers = state.players.map { p ->
            val gain = payouts[p.seat] ?: 0L
            p.copy(chips = p.chips + gain)
        }

        val summary = ShowdownSummary(
            bestHands = handInfos,
            payouts = payouts,
            pots = potSummaries,
            uncalledReturn = sideResult.uncalledReturn,
            deadMoney = sideResult.deadMoney,
            rngServerSeedHex = "",
            rngClientSeedHex = "",
        )

        return state.copy(
            players = newPlayers,
            street = Street.SHOWDOWN,
            toActSeat = null,
            pendingShowdown = summary,
            paused = true,
            stateVersion = state.stateVersion + 1,
        )
    }

    private fun winnersForPot(eligible: Set<Int>, hi: Map<Int, HandValue>): List<Int> {
        val sub = eligible.mapNotNull { s -> hi[s]?.let { s to it } }
        if (sub.isEmpty()) return emptyList()
        val max = sub.maxOf { it.second }
        return sub.filter { it.second.compareTo(max) == 0 }.map { it.first }
    }

    // ------------------------------------------------------- Seat utils

    private fun chooseBtnSeat(prev: Int?, players: List<PlayerState>): Int {
        val seats = players.filter { !it.folded }.map { it.seat }.sorted()
        require(seats.isNotEmpty()) { "no seats to choose btn" }
        if (prev == null) return seats.first()
        return seats.firstOrNull { it > prev } ?: seats.first()
    }

    private fun blindSeats(btn: Int, players: List<PlayerState>): Pair<Int, Int> {
        val live = players.filter { !it.folded }.map { it.seat }.sorted()
        return if (live.size == 2) {
            val bb = live.first { it != btn }
            btn to bb
        } else {
            val sb = nextSeat(btn, live)
            val bb = nextSeat(sb, live)
            sb to bb
        }
    }

    private fun postBlind(players: List<PlayerState>, seat: Int, amount: Long): List<PlayerState> =
        players.map { p ->
            if (p.seat != seat) p
            else {
                val pay = amount.coerceAtMost(p.chips)
                val isAllIn = pay == p.chips
                p.copy(
                    chips = p.chips - pay,
                    committedThisHand = p.committedThisHand + pay,
                    committedThisStreet = p.committedThisStreet + pay,
                    allIn = isAllIn,
                    actedThisStreet = false,   // BB option 보존
                )
            }
        }

    private fun dealOrder(btn: Int, players: List<PlayerState>): List<Int> {
        val live = players.filter { !it.folded }.map { it.seat }.sorted()
        if (live.size == 2) {
            // heads-up 딜: non-btn(BB) 먼저, btn(SB) 나중 (M0 기준 단순)
            val nonBtn = live.first { it != btn }
            return listOf(nonBtn, btn)
        }
        val i = live.indexOfFirst { it > btn }
        val start = if (i == -1) 0 else i
        return live.drop(start) + live.take(start)
    }

    private fun nextSeat(from: Int, seats: List<Int>): Int =
        seats.firstOrNull { it > from } ?: seats.first()

    private fun nextActiveSeatAfter(from: Int, players: List<PlayerState>): Int {
        val seats = players.filter { it.active }.map { it.seat }.sorted()
        if (seats.isEmpty()) return from
        return seats.firstOrNull { it > from } ?: seats.first()
    }

    private fun seatOrderFromBtn(btn: Int, players: List<PlayerState>): List<Int> {
        val seats = players.map { it.seat }.sorted()
        val i = seats.indexOfFirst { it > btn }
        val start = if (i == -1) 0 else i
        return seats.drop(start) + seats.take(start)
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
