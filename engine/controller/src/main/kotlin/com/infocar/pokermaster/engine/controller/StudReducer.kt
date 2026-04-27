package com.infocar.pokermaster.engine.controller

import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.DeclareDirection
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.model.ShowdownHandInfo
import com.infocar.pokermaster.core.model.ShowdownSummary
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.HandEvaluator7Stud
import com.infocar.pokermaster.engine.rules.HandEvaluatorHiLo
import com.infocar.pokermaster.engine.rules.HandValue
import com.infocar.pokermaster.engine.rules.LowValue
import com.infocar.pokermaster.engine.rules.PlayerCommitment
import com.infocar.pokermaster.engine.rules.Rng
import com.infocar.pokermaster.engine.rules.ShowdownResolver
import com.infocar.pokermaster.engine.rules.SidePotCalculator

/**
 * 한국식 7카드 스터드 (high-only) 상태 머신 — 순수 함수 (no coroutines / no IO).
 *
 * 흐름:
 *  - [startHand]: 앤티 → 3rd street(2D+1U) 분배 → 브링인(약한 up-card 좌석) 강제 베팅 → 다음 액션 좌석 설정
 *  - [act]: 단일 액션 검증+적용. 라운드 종료 시 자동 4~7th 진행, 7th 후 자동 쇼다운.
 *  - [ackShowdown]: 쇼다운 애니 후 UI 확인 → pending 해제.
 *
 * 베팅 구조 (첫 컷):
 *  - NL 스타일 — applyBetOrRaise / applyCall 시맨틱은 HoldemReducer 와 동일.
 *  - 4th double-bet on open pair, raise cap 3, fixed-limit 룰은 후속 카드.
 *  - SAVE_LIFE (§3.3.C) 는 스펙 미확정 — 현재는 FOLD 로 다운그레이드.
 *
 * 카드 분배:
 *  - 3rd: 모든 active 좌석에 2 down + 1 up
 *  - 4/5/6th: 1 up
 *  - 7th: 1 down
 *
 * 베스트 익스포즈드(first-to-act 4~7th) 동률 처리:
 *  - HandEvaluator7Stud.evaluatePartial 결과 비교, 동률 시 최고 up-card rank → suit ordinal (♠ 최강) 우선.
 */
object StudReducer {

    /** 한국식 7스터드 raise cap — 스트릿당 최대 3회 raise (complete 포함). */
    private const val STUD_RAISE_CAP_PER_STREET = 3

    fun startHand(
        config: TableConfig,
        players: List<PlayerState>,
        prevBtnSeat: Int?,
        rng: Rng,
        handIndex: Long,
        startingVersion: Long,
    ): GameState {
        require(config.mode == GameMode.SEVEN_STUD || config.mode == GameMode.SEVEN_STUD_HI_LO) {
            "StudReducer only handles SEVEN_STUD / SEVEN_STUD_HI_LO"
        }
        val seated = players.filter { it.chips > 0 }
        require(seated.size >= 2) { "need >=2 players with chips to start hand" }
        val anteAmount = config.ante.coerceAtLeast(0L)
        val bringInAmount = config.bringIn.coerceAtLeast(0L)

        // 1) reset per-hand
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
                // HiLo: declaration 은 핸드별 — 새 핸드 시작 시 항상 null 로 리셋.
                declaration = null,
            )
        }

        // 2) BTN — 스터드는 first-to-act 가 브링인/베스트익스포즈드로 결정되지만
        //    odd-chip 분배 / 다음 핸드 BTN 회전 / deal order tie-breaker 용으로 유지.
        val newBtn = chooseBtnSeat(prevBtnSeat, current)

        // 3) ante (모든 not-folded 좌석). committedThisHand 만 갱신, committedThisStreet 미반영(brick-in 만 콜 기준).
        if (anteAmount > 0L) {
            current = current.map { p ->
                if (p.folded) p
                else {
                    val pay = anteAmount.coerceAtMost(p.chips)
                    val isAllIn = pay > 0L && pay == p.chips
                    p.copy(
                        chips = p.chips - pay,
                        committedThisHand = p.committedThisHand + pay,
                        allIn = isAllIn || p.allIn,
                    )
                }
            }
        }

        // 4) deal: 2 down + 1 up to each not-folded, in BTN+1 시계방향 순서
        val order = dealOrder(newBtn, current)
        val downs = order.associateWith { mutableListOf<Card>() }
        val ups = order.associateWith { mutableListOf<Card>() }
        var cursor = 0
        repeat(2) {
            for (seat in order) downs[seat]!!.add(rng.deck[cursor++])
        }
        for (seat in order) ups[seat]!!.add(rng.deck[cursor++])

        current = current.map { p ->
            if (p.folded || !downs.containsKey(p.seat)) p
            else p.copy(
                holeCards = downs[p.seat]!!.toList(),
                upCards = ups[p.seat]!!.toList(),
            )
        }

        // 5) bring-in 좌석: 가장 약한 up-card (rank asc; 동률 시 suit asc — ♣<♦<♥<♠).
        val bringInSeat = pickBringInSeat(current)

        // 6) bring-in 강제 베팅 — committedThisStreet 도 갱신 (3rd street 콜 기준).
        if (bringInAmount > 0L) {
            current = current.map { p ->
                if (p.seat != bringInSeat) p
                else {
                    val pay = bringInAmount.coerceAtMost(p.chips)
                    val isAllIn = pay > 0L && pay == p.chips
                    p.copy(
                        chips = p.chips - pay,
                        committedThisHand = p.committedThisHand + pay,
                        committedThisStreet = p.committedThisStreet + pay,
                        allIn = isAllIn || p.allIn,
                    )
                }
            }
        }

        // 7) first-to-act = bring-in 다음 active 좌석.
        val firstToAct = nextActiveSeatAfter(bringInSeat, current)

        return GameState(
            mode = config.mode,
            config = config,
            stateVersion = startingVersion + 1,
            handIndex = handIndex,
            players = current,
            btnSeat = newBtn,
            toActSeat = firstToAct,
            street = Street.THIRD,
            community = emptyList(),
            betToCall = bringInAmount,
            minRaise = (bringInAmount * 2L).coerceAtLeast(1L),
            reopenAction = true,
            lastFullRaiseAmount = bringInAmount.coerceAtLeast(1L),
            lastAggressorSeat = bringInSeat,
            deckCursor = cursor,
            raisesThisStreet = 0,
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

        // HiLo Declare: DECLARE 단계 분기. alive 만 (allIn 포함) 액션 가능.
        if (state.street == Street.DECLARE) {
            require(player.alive) { "player at seat $seat is not alive (cannot declare)" }
            val direction = when (action.type) {
                ActionType.DECLARE_HI -> DeclareDirection.HI
                ActionType.DECLARE_LO -> DeclareDirection.LO
                ActionType.DECLARE_BOTH -> DeclareDirection.BOTH
                else -> error("only DECLARE_* actions allowed during Street.DECLARE (got ${action.type})")
            }
            return applyDeclare(state, seat, direction)
        }

        // 비-DECLARE 스트릿: declare 액션 거절.
        when (action.type) {
            ActionType.DECLARE_HI, ActionType.DECLARE_LO, ActionType.DECLARE_BOTH ->
                error("DECLARE_* actions only allowed in Street.DECLARE (street=${state.street})")
            else -> { /* fall through */ }
        }

        require(player.active) { "player at seat $seat is not active" }

        val s0 = when (action.type) {
            ActionType.FOLD -> applyFold(state, seat)
            ActionType.CHECK -> applyCheck(state, seat)
            ActionType.CALL -> applyCall(state, seat)
            ActionType.BET, ActionType.RAISE, ActionType.COMPLETE -> applyBetOrRaise(state, seat, action)
            ActionType.ALL_IN -> applyAllIn(state, seat)
            ActionType.BRING_IN -> applyCall(state, seat) // 강제 브링인 — 사용자 선택 X, 들어오면 콜로 강등
            ActionType.SAVE_LIFE -> applySaveLife(state, seat) // §3.3.C 한국식 구사 (잠정 사양)
            ActionType.DECLARE_HI, ActionType.DECLARE_LO, ActionType.DECLARE_BOTH ->
                error("unreachable — declare guarded above")
        }

        if (s0.players.count { !it.folded } == 1) {
            return runShowdown(s0.copy(toActSeat = null))
        }

        if (isBettingRoundComplete(s0)) {
            return runoutOrShowdown(s0, rng)
        }

        val nextSeat = nextActiveSeatAfter(seat, s0.players)
        return s0.copy(toActSeat = nextSeat, stateVersion = s0.stateVersion + 1)
    }

    /**
     * 한 스트릿 종료 후, 다음 스트릿에서도 모두 all-in 상태로 즉시 라운드 완료라면 계속 전진.
     * 7th 통과 후 쇼다운.
     */
    private fun runoutOrShowdown(s: GameState, rng: Rng): GameState {
        var cur = s
        while (true) {
            val next = when (cur.street) {
                Street.THIRD -> Street.FOURTH
                Street.FOURTH -> Street.FIFTH
                Street.FIFTH -> Street.SIXTH
                Street.SIXTH -> Street.SEVENTH
                Street.SEVENTH -> {
                    // HiLo: 7th 베팅 종료 후 alive ≥ 2 → DECLARE 단계 진입.
                    val live = cur.players.filter { !it.folded }
                    if (cur.mode == GameMode.SEVEN_STUD_HI_LO && live.size >= 2) {
                        return enterDeclareStreet(cur)
                    }
                    return runShowdown(cur.copy(street = Street.SHOWDOWN, toActSeat = null))
                }
                else -> error("unexpected street ${cur.street}")
            }
            cur = advanceToStreet(cur, next, rng)
            if (!isBettingRoundComplete(cur)) return cur
        }
    }

    /**
     * 7th 베팅 종료 후 SEVEN_STUD_HI_LO 모드에서 [Street.DECLARE] 단계 진입.
     * 모든 alive 좌석을 위해 actedThisStreet/declaration 리셋, betToCall/minRaise 0,
     * toActSeat = BTN-left clockwise 첫 alive 좌석.
     */
    private fun enterDeclareStreet(state: GameState): GameState {
        val newPlayers = state.players.map { p ->
            if (p.alive) p.copy(actedThisStreet = false, declaration = null)
            else p.copy(actedThisStreet = !p.active)
        }
        val firstActor = firstAliveSeatFromBtn(state.btnSeat, newPlayers)
        return state.copy(
            players = newPlayers,
            street = Street.DECLARE,
            toActSeat = firstActor,
            betToCall = 0L,
            minRaise = 0L,
            reopenAction = false,
            lastFullRaiseAmount = 0L,
            lastAggressorSeat = null,
            raisesThisStreet = 0,
            stateVersion = state.stateVersion + 1,
        )
    }

    /** BTN+1 시계방향 첫 alive(=non-folded) 좌석. all-in 도 포함 — declare 는 alive 면 누구든 함. */
    private fun firstAliveSeatFromBtn(btn: Int, players: List<PlayerState>): Int? {
        val order = seatOrderFromBtn(btn, players)
        return order.firstOrNull { seat -> players.first { it.seat == seat }.alive }
    }

    /**
     * Declare 액션 적용. PlayerState.declaration = direction, actedThisStreet = true.
     * 다음 toActSeat = 다음 alive + declaration 미설정 좌석. 모두 declare 완료 시 showdown.
     */
    private fun applyDeclare(state: GameState, seat: Int, direction: DeclareDirection): GameState {
        val newPlayers = state.players.map { p ->
            if (p.seat == seat) p.copy(declaration = direction, actedThisStreet = true)
            else p
        }
        val s1 = state.copy(players = newPlayers, stateVersion = state.stateVersion + 1)
        // 모두 declare 끝났는지: alive 좌석 모두 declaration != null.
        val allDeclared = s1.players.filter { it.alive }.all { it.declaration != null }
        if (allDeclared) {
            return runShowdownDeclare(s1.copy(toActSeat = null))
        }
        // 다음 declare 좌석: 시계방향 다음 alive + declaration null 좌석.
        val nextSeat = nextAliveDeclareSeat(seat, s1.players)
        return s1.copy(toActSeat = nextSeat)
    }

    /** [from] 좌석 다음 시계방향 alive + declaration null 좌석. */
    private fun nextAliveDeclareSeat(from: Int, players: List<PlayerState>): Int? {
        val seats = players.filter { it.alive }.map { it.seat }.sorted()
        if (seats.isEmpty()) return null
        // from 다음 좌석부터 시계방향 한 바퀴.
        val ordered = seats.dropWhile { it <= from } + seats.takeWhile { it <= from }
        return ordered.firstOrNull { s ->
            players.first { it.seat == s }.declaration == null
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

    // ------------------------------------------------------- Apply (Holdem 과 동일)

    private fun applyFold(state: GameState, seat: Int): GameState {
        val ps = state.players.map {
            if (it.seat == seat) it.copy(folded = true, actedThisStreet = true) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyCheck(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        if (me.committedThisStreet < state.betToCall) return applyCall(state, seat)
        val ps = state.players.map {
            if (it.seat == seat) it.copy(actedThisStreet = true) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyCall(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        val rawDelta = state.betToCall - me.committedThisStreet
        if (rawDelta <= 0) {
            val ps = state.players.map {
                if (it.seat == seat) it.copy(actedThisStreet = true) else it
            }
            return state.copy(players = ps, stateVersion = state.stateVersion + 1)
        }
        val delta = rawDelta.coerceAtMost(me.chips)
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
        // Raise cap 3 — 한국식 7스터드 정통: 스트릿당 최대 3회 raise (complete 포함).
        // 초과 시 콜/체크로 강등.
        val raiseCapHit = state.raisesThisStreet >= STUD_RAISE_CAP_PER_STREET
        if (!state.reopenAction || me.chips == 0L || action.amount <= state.betToCall || raiseCapHit) {
            return if (state.betToCall > me.committedThisStreet) applyCall(state, seat)
            else applyCheck(state, seat)
        }
        val target = action.amount
        val delta = target - me.committedThisStreet
        if (delta !in 1..me.chips) {
            return if (state.betToCall > me.committedThisStreet) applyCall(state, seat)
            else applyCheck(state, seat)
        }

        val wouldBeAllIn = delta == me.chips
        val raiseIncrement = target - state.betToCall
        val baselineRaise = maxOf(state.lastFullRaiseAmount, state.config.bringIn.coerceAtLeast(1L))
        val isFullRaise = raiseIncrement >= baselineRaise

        if (!isFullRaise && !wouldBeAllIn) {
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
                isFullRaise && p.active && p.seat != seat -> p.copy(actedThisStreet = false)
                else -> p
            }
        }

        return state.copy(
            players = newPlayers,
            betToCall = target,
            minRaise = target + maxOf(raiseIncrement, baselineRaise),
            lastFullRaiseAmount = if (isFullRaise) raiseIncrement else state.lastFullRaiseAmount,
            reopenAction = isFullRaise,
            lastAggressorSeat = seat,
            raisesThisStreet = if (isFullRaise) state.raisesThisStreet + 1 else state.raisesThisStreet,
            stateVersion = state.stateVersion + 1,
        )
    }

    /**
     * SAVE_LIFE (§3.3.C 한국식 "구사") — 잠정 사양.
     *
     * 동작:
     *  - 콜할 베팅이 없으면(betToCall ≤ committedThisStreet) CHECK 로 강등.
     *  - 그 외: 잔여 콜 비용의 절반(rounded down, 최소 1, 칩 한도 클램프) 만 팟에 commit 하고
     *    플레이어를 [PlayerState.allIn] 으로 마킹. 더 이상 액션 불가, 그러나 폴드 아님 →
     *    카드는 7th 까지 정상 분배되고 쇼다운에 동석. SidePotCalculator 가 자기 commit 한도까지의
     *    레이어만 자격으로 처리하므로 상위 팟은 자연스럽게 분리.
     *
     * 의미: "콜은 부담스럽지만 죽기는 아까운" 상황에서 비용을 반으로 끊고 쇼다운까지 동석하는 한국식 구사.
     *
     * 정식 사양 미확정 — 사용자가 §3.3.C 텍스트 확정 시 본 함수만 수정.
     */
    private fun applySaveLife(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        val rawCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        if (rawCall <= 0L) return applyCheck(state, seat)
        val halfCost = (rawCall / 2L).coerceAtLeast(1L).coerceAtMost(me.chips)
        val ps = state.players.map {
            if (it.seat == seat) it.copy(
                chips = it.chips - halfCost,
                committedThisHand = it.committedThisHand + halfCost,
                committedThisStreet = it.committedThisStreet + halfCost,
                allIn = true,
                actedThisStreet = true,
            ) else it
        }
        return state.copy(players = ps, stateVersion = state.stateVersion + 1)
    }

    private fun applyAllIn(state: GameState, seat: Int): GameState {
        val me = state.players.first { it.seat == seat }
        if (me.chips <= 0L) {
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

    // ------------------------------------------------------- Round / street advance

    private fun isBettingRoundComplete(state: GameState): Boolean {
        val live = state.players.filter { !it.folded }
        if (live.size < 2) return true
        val actors = live.filter { !it.allIn }
        if (actors.isEmpty()) return true
        return actors.all { it.actedThisStreet && it.committedThisStreet == state.betToCall }
    }

    private fun advanceToStreet(state: GameState, next: Street, rng: Rng): GameState {
        // 4~6th = up card 1장, 7th = down card 1장 (모든 not-folded — all-in 도 카드 받음)
        val isUpDeal = next != Street.SEVENTH
        val recipients = state.players.filter { !it.folded }.map { it.seat }.sorted()
            .let { sorted ->
                val i = sorted.indexOfFirst { it > state.btnSeat }
                if (i == -1) sorted else sorted.drop(i) + sorted.take(i)
            }
        var cursor = state.deckCursor
        val updates = HashMap<Int, Pair<List<Card>, List<Card>>>()
        for (seat in recipients) {
            val card = rng.deck[cursor++]
            val p = state.players.first { it.seat == seat }
            updates[seat] = if (isUpDeal) {
                p.holeCards to (p.upCards + card)
            } else {
                (p.holeCards + card) to p.upCards
            }
        }
        val baseCommittedReset = state.players.map { p ->
            val u = updates[p.seat]
            if (u == null) {
                p.copy(
                    committedThisStreet = 0L,
                    actedThisStreet = !p.active,
                )
            } else {
                p.copy(
                    holeCards = u.first,
                    upCards = u.second,
                    committedThisStreet = 0L,
                    actedThisStreet = !p.active,
                )
            }
        }

        val firstActor = bestExposedSeat(baseCommittedReset)

        return state.copy(
            players = baseCommittedReset,
            deckCursor = cursor,
            street = next,
            betToCall = 0L,
            minRaise = state.config.bringIn.coerceAtLeast(1L),
            reopenAction = true,
            lastFullRaiseAmount = state.config.bringIn.coerceAtLeast(1L),
            lastAggressorSeat = null,
            raisesThisStreet = 0,   // 새 스트릿마다 raise cap 카운터 초기화
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
            val winner = live.firstOrNull()?.seat
            val total = sideResult.pots.sumOf { it.amount }
            payouts = if (winner != null) {
                val per = mutableMapOf(winner to total)
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
            val isHiLo = state.mode == GameMode.SEVEN_STUD_HI_LO
            val bestHands: Map<Int, HandValue> = live.associate { p ->
                p.seat to HandEvaluator7Stud.evaluateBest(p.holeCards + p.upCards)
            }
            val lowHands: Map<Int, LowValue?> = if (isHiLo) {
                live.associate { p ->
                    p.seat to HandEvaluatorHiLo.evaluateLow(p.holeCards + p.upCards)
                }
            } else emptyMap()
            payouts = ShowdownResolver.resolveAll(
                result = sideResult,
                hi = bestHands,
                lo = lowHands,
                seatOrderForOdd = seatOrder,
                hiLoSplit = isHiLo,
            )
            potSummaries = sideResult.pots.mapIndexed { idx, pot ->
                val hiWinners = winnersForPot(pot.eligibleSeats, bestHands).toSet()
                val loWinners = if (isHiLo) {
                    loWinnersForPot(pot.eligibleSeats, lowHands).toSet()
                } else emptySet()
                PotSummary(
                    amount = pot.amount,
                    eligibleSeats = pot.eligibleSeats,
                    winnerSeats = hiWinners + loWinners,
                    index = idx,
                    hiWinnerSeats = hiWinners,
                    loWinnerSeats = loWinners,
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

    /**
     * Declare flow 쇼다운 — runShowdown 의 HiLo Declare 변형.
     *
     * 차이:
     *  - HiLo split 강제 (mode == SEVEN_STUD_HI_LO 전제 — 호출 측 가드).
     *  - 자격: alive 좌석 모두 declaration != null (Street.DECLARE 종료 시점).
     *  - 분배: ShowdownResolver.resolveAllDeclare(...) — declarations 까지 입력.
     *    Team Rules merge 전에는 [resolveAllDeclareStub] 가 임시 대체.
     *
     * 단일 alive 좌석 케이스는 runoutOrShowdown 가 [enterDeclareStreet] 진입을 막아주므로 본 함수
     * 호출 시점에는 항상 alive ≥ 2.
     */
    private fun runShowdownDeclare(state: GameState): GameState {
        val live = state.players.filter { !it.folded }
        val commitments = state.players.map {
            PlayerCommitment(seat = it.seat, committed = it.committedThisHand, folded = it.folded)
        }
        val sideResult = SidePotCalculator.compute(commitments)
        val seatOrder = seatOrderFromBtn(state.btnSeat, state.players)

        val bestHands: Map<Int, HandValue> = live.associate { p ->
            p.seat to HandEvaluator7Stud.evaluateBest(p.holeCards + p.upCards)
        }
        // Team Rules 후속: HandEvaluatorHiLo.evaluateLow 가 non-null LowValue 로 변경됨.
        // 현재는 nullable → !! 강제 X. 임시로 null 좌석은 분배 대상 제외 처리 (스텁).
        val lowHandsRaw: Map<Int, LowValue?> = live.associate { p ->
            p.seat to HandEvaluatorHiLo.evaluateLow(p.holeCards + p.upCards)
        }
        val declarations: Map<Int, DeclareDirection> = live.associate { p ->
            p.seat to (p.declaration
                ?: error("alive seat ${p.seat} has no declaration at runShowdownDeclare"))
        }

        // Team Rules 통합 후 — ShowdownResolver.resolveAllDeclare(sideResult, bestHands, lowHandsRaw, declarations, seatOrder).
        val outcome = resolveAllDeclareStub(
            result = sideResult,
            hi = bestHands,
            lo = lowHandsRaw,
            declarations = declarations,
            seatOrderForOdd = seatOrder,
        )

        val potSummaries = sideResult.pots.mapIndexed { idx, pot ->
            val per = outcome.perPotOutcomes.getOrNull(idx)
            val hiWinners = per?.hiWinners ?: emptySet()
            val loWinners = per?.loWinners ?: emptySet()
            val scoopWinners = per?.scoopWinners ?: emptySet()
            PotSummary(
                amount = pot.amount,
                eligibleSeats = pot.eligibleSeats,
                winnerSeats = hiWinners + loWinners + scoopWinners,
                index = idx,
                hiWinnerSeats = hiWinners,
                loWinnerSeats = loWinners,
                scoopWinnerSeats = scoopWinners,
            )
        }
        val handInfos = live.associate { p ->
            val hv = bestHands[p.seat]!!
            p.seat to ShowdownHandInfo(
                seat = p.seat,
                categoryName = hv.category.korean,
                bestFive = hv.cards,
            )
        }
        val payouts = outcome.payouts.toMutableMap()
        // uncalled 환급 합산 (resolveAll 과 동등 시맨틱).
        for ((seat, amount) in sideResult.uncalledReturn) {
            payouts.merge(seat, amount) { a, b -> a + b }
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

    // -------------------------------------------------------
    // TEMPORARY STUB — Team Rules merge 후 제거.
    //
    // Team Rules 가 ShowdownResolver.resolveAllDeclare(sideResult, hi, lo, declarations, seatOrder)
    // 와 ResolveAllDeclareOutcome / PotDeclareOutcome 데이터 클래스를 추가하면 본 스텁 + 데이터 클래스
    // 정의를 모두 삭제하고 호출부 (runShowdownDeclare) 의 resolveAllDeclareStub(...) 호출을
    // ShowdownResolver.resolveAllDeclare(...) 로 교체.
    //
    // 동작 (스텁):
    //  - payouts: 빈 맵 (모든 칩 보존 X — 테스트는 state-machine progression 만 검증).
    //  - perPotOutcomes: 각 팟마다 빈 hi/lo/scoop 셋.
    //
    // 이 스텁은 컴파일을 위한 placeholder 일 뿐, 실제 분배 로직은 Team Rules 가 책임진다.
    // -------------------------------------------------------

    /** TODO Team Rules merge: replace stub with ShowdownResolver.resolveAllDeclare */
    private data class StubPotDeclareOutcome(
        val potIndex: Int,
        val hiWinners: Set<Int> = emptySet(),
        val loWinners: Set<Int> = emptySet(),
        val scoopWinners: Set<Int> = emptySet(),
    )

    /** TODO Team Rules merge: replace stub with ShowdownResolver.resolveAllDeclare */
    private data class StubResolveAllDeclareOutcome(
        val payouts: Map<Int, Long>,
        val perPotOutcomes: List<StubPotDeclareOutcome>,
    )

    /** TODO Team Rules merge: replace stub with ShowdownResolver.resolveAllDeclare */
    @Suppress("UNUSED_PARAMETER")
    private fun resolveAllDeclareStub(
        result: com.infocar.pokermaster.engine.rules.SidePotResult,
        hi: Map<Int, HandValue>,
        lo: Map<Int, LowValue?>,
        declarations: Map<Int, DeclareDirection>,
        seatOrderForOdd: List<Int>,
    ): StubResolveAllDeclareOutcome {
        val perPot = result.pots.mapIndexed { idx, _ -> StubPotDeclareOutcome(potIndex = idx) }
        return StubResolveAllDeclareOutcome(payouts = emptyMap(), perPotOutcomes = perPot)
    }

    private fun winnersForPot(eligible: Set<Int>, hi: Map<Int, HandValue>): List<Int> {
        val sub = eligible.mapNotNull { s -> hi[s]?.let { s to it } }
        if (sub.isEmpty()) return emptyList()
        val max = sub.maxOf { it.second }
        return sub.filter { it.second.compareTo(max) == 0 }.map { it.first }
    }

    /** HiLo 로우 승자 — qualifier 미달(null) 좌석 제외, LowValue 가장 강한(작은) 좌석. */
    private fun loWinnersForPot(eligible: Set<Int>, lo: Map<Int, LowValue?>): List<Int> {
        val sub = eligible.mapNotNull { s -> lo[s]?.let { s to it } }
        if (sub.isEmpty()) return emptyList()
        val min = sub.minOf { it.second }
        return sub.filter { it.second.compareTo(min) == 0 }.map { it.first }
    }

    // ------------------------------------------------------- Stud helpers

    /** 가장 약한 up-card 좌석 (rank asc, 동률 시 suit asc — ♣ 가장 약). */
    private fun pickBringInSeat(players: List<PlayerState>): Int {
        val candidates = players.filter { !it.folded && it.upCards.isNotEmpty() }
        require(candidates.isNotEmpty()) { "no up-cards dealt; cannot determine bring-in" }
        return candidates.minByOrNull { p ->
            val u = p.upCards.first()
            // 작을수록 약 — rank.value 가장 작은 + 동률 시 suit ordinal 작은
            u.rank.value * 10 + u.suit.ordinal
        }!!.seat
    }

    /** 4~7th first-to-act: 가장 강한 노출 핸드 (HandValue), 동률 시 최고 up-card (rank then suit desc — ♠ 최강). */
    private fun bestExposedSeat(players: List<PlayerState>): Int {
        val candidates = players.filter { it.active && it.upCards.isNotEmpty() }
        if (candidates.isEmpty()) {
            // 모두 all-in/folded — fallback: 살아있는 첫 좌석 (어차피 isBettingRoundComplete 가 즉시 true 처리)
            return players.firstOrNull { !it.folded }?.seat ?: 0
        }
        val best = candidates.maxWithOrNull(
            compareBy<PlayerState>(
                { p -> HandEvaluator7Stud.evaluatePartial(p.upCards) ?: HandValue.MIN },
                { p -> p.upCards.maxOf { it.rank.value * 10 + it.suit.ordinal } },
            )
        )!!
        return best.seat
    }

    // ------------------------------------------------------- Seat utils (Holdem 과 동일)

    private fun chooseBtnSeat(prev: Int?, players: List<PlayerState>): Int {
        val seats = players.filter { !it.folded }.map { it.seat }.sorted()
        require(seats.isNotEmpty()) { "no seats to choose btn" }
        if (prev == null) return seats.first()
        return seats.firstOrNull { it > prev } ?: seats.first()
    }

    private fun dealOrder(btn: Int, players: List<PlayerState>): List<Int> {
        val live = players.filter { !it.folded }.map { it.seat }.sorted()
        if (live.isEmpty()) return emptyList()
        val i = live.indexOfFirst { it > btn }
        val start = if (i == -1) 0 else i
        return live.drop(start) + live.take(start)
    }

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

    // ------------------------------------------------------- Open-pair detector (4th street rule hook)

    /**
     * 4th street(=업카드 2장 시점)에 노출 페어를 가진 좌석들. 정통 한국식/미국 fixed-limit 7스터드에서
     * 이 시점에 노출 페어가 있으면 베팅 한도가 small bet → big bet 으로 더블되는 옵션이 발동되지만,
     * 본 구현은 NL 베팅이라 베팅 룰 자체는 변경되지 않음 — 본 함수는 UI 인디케이터(시트 라벨) 와
     * 추후 fixed-limit 변환 카드의 후크 용도.
     *
     * 반환: seat → 노출 페어 rank value (예: pair of 7s → 7).
     */
    fun openPairsOnFourthStreet(state: GameState): Map<Int, Int> {
        if (state.street != Street.FOURTH) return emptyMap()
        if (state.mode != GameMode.SEVEN_STUD && state.mode != GameMode.SEVEN_STUD_HI_LO) return emptyMap()
        return state.players
            .filter { !it.folded && it.upCards.size == 2 }
            .mapNotNull { p ->
                val (a, b) = p.upCards
                if (a.rank == b.rank) p.seat to a.rank.value else null
            }
            .toMap()
    }
}

private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
