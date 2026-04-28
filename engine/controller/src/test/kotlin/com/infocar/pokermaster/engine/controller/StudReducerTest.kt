package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Declaration
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.HandEvaluatorHiLo
import com.infocar.pokermaster.engine.rules.Rng
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class StudReducerTest {

    private val config = TableConfig(
        mode = GameMode.SEVEN_STUD,
        seats = 4,
        smallBlind = 0L,
        bigBlind = 0L,
        ante = 10L,
        bringIn = 25L,
    )

    private fun players(vararg chips: Long): List<PlayerState> =
        chips.mapIndexed { i, c ->
            PlayerState(
                seat = i,
                nickname = "P$i",
                isHuman = i == 0,
                personaId = if (i == 0) null else "PRO",
                chips = c,
            )
        }

    private fun rngOf(nonce: Long = 1L): Rng {
        val seed = ByteArray(Rng.SEED_BYTES) { (it + nonce.toInt()).toByte() }
        return Rng.ofSeeds(seed, seed, nonce)
    }

    // -------------------------------------------------- start: ante + 3rd street deal

    @Test fun start_hand_third_street_deals_2_down_1_up_and_posts_ante() {
        val s = StudReducer.startHand(
            config = config,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = rngOf(),
            handIndex = 1L,
            startingVersion = 0L,
        )
        assertThat(s.street).isEqualTo(Street.THIRD)
        // 모든 좌석에 down 2 + up 1
        for (p in s.players) {
            assertThat(p.holeCards).hasSize(2)
            assertThat(p.upCards).hasSize(1)
            // 앤티 차감: 10
            // bring-in 좌석은 추가 25 차감
        }
        // 앤티 합계 == 10 * 4 = 40 + bring-in 25 = 65 (committedThisHand 기준)
        assertThat(s.players.sumOf { it.committedThisHand }).isEqualTo(65L)
        // betToCall = bring-in
        assertThat(s.betToCall).isEqualTo(25L)
    }

    @Test fun bring_in_seat_is_lowest_up_card_with_suit_tiebreak() {
        // RNG 셔플 결과는 고정 — 어떤 좌석이 bring-in 인지 검사하려면 실제 분배된 up-card 들 중
        // 최약(rank, then suit asc) 좌석이 bring-in 으로 잡혔는지 확인.
        val s = StudReducer.startHand(
            config = config,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = rngOf(nonce =7L),
            handIndex = 7L,
            startingVersion = 0L,
        )
        // bring-in 좌석 = lastAggressorSeat
        val bringInSeat = s.lastAggressorSeat!!
        val bringInUp = s.players.first { it.seat == bringInSeat }.upCards.first()
        for (p in s.players) {
            if (p.seat == bringInSeat) continue
            val theirs = p.upCards.first()
            val mine = bringInUp.rank.value * 10 + bringInUp.suit.ordinal
            val theirVal = theirs.rank.value * 10 + theirs.suit.ordinal
            assertThat(mine).isAtMost(theirVal)
        }
        // bring-in 좌석은 25 commit (committedThisStreet 기준)
        assertThat(s.players.first { it.seat == bringInSeat }.committedThisStreet).isEqualTo(25L)
    }

    // -------------------------------------------------- everyone folds → bring-in 단독 승

    @Test fun all_others_fold_bring_in_wins_pot() {
        var s = StudReducer.startHand(
            config = config,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = rngOf(nonce =13L),
            handIndex = 1L,
            startingVersion = 0L,
        )
        val bringInSeat = s.lastAggressorSeat!!
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 다른 좌석 모두 fold (toAct 순서대로)
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 8) {
            val seat = s.toActSeat ?: break
            if (seat == bringInSeat) break
            s = StudReducer.act(s, seat, Action(ActionType.FOLD), rngOf())
        }
        assertThat(s.pendingShowdown).isNotNull()
        // bring-in 이 모든 칩 +(앤티 합계 + 자기 bring-in 차감 후 환원) 회수
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
        // bring-in 좌석은 다른 모든 좌석보다 칩 많음
        val bringInChips = s.players.first { it.seat == bringInSeat }.chips
        for (p in s.players) {
            if (p.seat != bringInSeat) assertThat(bringInChips).isGreaterThan(p.chips)
        }
    }

    // -------------------------------------------------- check-down through 7th street

    @Test fun full_hand_call_down_through_seventh_completes_with_showdown() {
        val r = rngOf(nonce =21L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 3rd street: bring-in 다음 좌석이 act. 콜 → bring-in 좌석에 다시 액션.
        // 헤즈업 2명 → bring-in 1명 + 다른 1명. 다른 좌석이 콜 → bring-in 좌석 체크/콜.
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 50) {
            val seat = s.toActSeat ?: break
            // 콜/체크만 (체크 가능하면 체크, 아니면 콜)
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.pendingShowdown).isNotNull()
        // 7장 모두 분배되었어야 함: 3 down + 4 up
        for (p in s.players.filter { !it.folded }) {
            assertThat(p.holeCards.size).isEqualTo(3)
            assertThat(p.upCards.size).isEqualTo(4)
        }
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    // -------------------------------------------------- 4th street first-to-act = best exposed

    @Test fun fourth_street_first_to_act_uses_best_exposed_hand() {
        val r = rngOf(nonce =33L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 4),
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 모두 콜 → 3rd street 종료, 4th 진입.
        var guard = 0
        while (s.street == Street.THIRD && guard++ < 8) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.FOURTH)
        // 4th 첫 액터의 노출 카드 평가가 다른 좌석 중 최강이어야 함.
        val firstSeat = s.toActSeat!!
        val first = s.players.first { it.seat == firstSeat }
        // upCards 사이즈는 모두 2 (3rd 1장 + 4th 1장)
        for (p in s.players) {
            assertThat(p.upCards.size).isEqualTo(2)
        }
        // 동률 시 max up-card rank*10+suit 비교 — first 좌석의 max 가 다른 좌석들의 max 이상.
        val firstScore = first.upCards.maxOf { it.rank.value * 10 + it.suit.ordinal }
        for (p in s.players) {
            if (p.seat == firstSeat) continue
            val theirScore = p.upCards.maxOf { it.rank.value * 10 + it.suit.ordinal }
            // 같거나 약함 (실제 partial 핸드 평가가 같을 때만 단순 비교가 정확하지만,
            // 다를 때는 partial > partial 결과를 신뢰).
            // 이 테스트는 약한 invariant: first 가 동률에서라도 약하지 않다.
            // 더 strict 한 partial 평가는 별도 unit 테스트.
            if (firstScore < theirScore) {
                // partial 평가가 first 가 더 강함을 의미해야 함.
                val firstHv =
                    com.infocar.pokermaster.engine.rules.HandEvaluator7Stud.evaluatePartial(first.upCards)
                val theirHv =
                    com.infocar.pokermaster.engine.rules.HandEvaluator7Stud.evaluatePartial(p.upCards)
                assertThat(firstHv).isAtLeast(theirHv)
            }
        }
    }

    // -------------------------------------------------- chips conservation under raises

    @Test fun chips_conserved_when_raise_then_call_through_to_showdown() {
        val r = rngOf(nonce =41L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 3rd street: 콜 → bring-in 체크
        // 4th 부터 : seat 0 raise to 100, seat 1 call ; 5th, 6th, 7th 도 동일 패턴
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 80) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val needsCall = me.committedThisStreet < s.betToCall
            val isFirstActor = me.committedThisStreet == 0L && s.betToCall == 0L
            val a: Action = when {
                isFirstActor -> Action(ActionType.RAISE, amount = 100L)
                needsCall -> Action(ActionType.CALL)
                else -> Action(ActionType.CHECK)
            }
            s = StudReducer.act(s, seat, a, r)
        }
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    // -------------------------------------------------- ackShowdown unblocks

    @Test fun ack_showdown_clears_pending_and_paused() {
        val r = rngOf(nonce =55L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // bring-in 다음 좌석 fold → 단독 승 → showdown.
        val firstActor = s.toActSeat!!
        s = StudReducer.act(s, firstActor, Action(ActionType.FOLD), r)
        assertThat(s.pendingShowdown).isNotNull()
        assertThat(s.paused).isTrue()

        val v = s.stateVersion
        s = StudReducer.ackShowdown(s)
        assertThat(s.pendingShowdown).isNull()
        assertThat(s.paused).isFalse()
        assertThat(s.stateVersion).isGreaterThan(v)
    }

    // -------------------------------------------------- state version monotonic

    @Test fun state_version_is_monotonic() {
        val r = rngOf(nonce =91L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val v0 = s.stateVersion
        val seat = s.toActSeat!!
        s = StudReducer.act(s, seat, Action(ActionType.CALL), r)
        assertThat(s.stateVersion).isGreaterThan(v0)
    }

    // -------------------------------------------------- BRING_IN action coerced to CALL

    @Test fun bring_in_action_type_coerces_to_call() {
        val r = rngOf(nonce =100L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // bring-in 좌석이 아닌 좌석에서 BRING_IN 보내도 콜로 강등돼야 함 (크래시 X)
        val seat = s.toActSeat!!
        val before = s.players.first { it.seat == seat }.committedThisStreet
        s = StudReducer.act(s, seat, Action(ActionType.BRING_IN), r)
        val after = s.players.first { it.seat == seat }.committedThisStreet
        // 콜이 일어났어야 함 (after > before, == betToCall)
        assertThat(after).isEqualTo(s.betToCall)
    }

    // -------------------------------------------------- SAVE_LIFE 잠정 사양 (구사 = 절반 콜 + allIn)

    @Test fun save_life_pays_half_remaining_call_and_marks_all_in() {
        val r = rngOf(nonce =200L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // toAct 좌석이 봉착한 콜 비용 = bring-in (25L)
        val seat = s.toActSeat!!
        val me = s.players.first { it.seat == seat }
        assertThat(me.committedThisStreet).isEqualTo(0L)
        assertThat(s.betToCall).isEqualTo(25L)

        val chipsBefore = me.chips
        s = StudReducer.act(s, seat, Action(ActionType.SAVE_LIFE), r)
        val after = s.players.first { it.seat == seat }
        // half(25) = 12, chips 차감.
        assertThat(after.chips).isEqualTo(chipsBefore - 12L)
        assertThat(after.committedThisHand - me.committedThisHand).isEqualTo(12L)
        assertThat(after.allIn).isTrue()
        assertThat(after.folded).isFalse()
        assertThat(after.actedThisStreet).isTrue()
    }

    @Test fun save_life_with_no_call_required_coerces_to_check() {
        val r = rngOf(nonce =201L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3rd street 통과해서 4th 진입 — betToCall 0 상태.
        var guard = 0
        while (s.street == Street.THIRD && guard++ < 8) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val a = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(a), r)
        }
        assertThat(s.street).isEqualTo(Street.FOURTH)
        assertThat(s.betToCall).isEqualTo(0L)
        // 4th first actor 가 SAVE_LIFE 시도 → 콜할 게 없으니 CHECK 로 강등.
        val firstSeat = s.toActSeat!!
        val before = s.players.first { it.seat == firstSeat }
        s = StudReducer.act(s, firstSeat, Action(ActionType.SAVE_LIFE), r)
        val after = s.players.first { it.seat == firstSeat }
        assertThat(after.allIn).isFalse()
        assertThat(after.folded).isFalse()
        assertThat(after.chips).isEqualTo(before.chips)
        assertThat(after.actedThisStreet).isTrue()
    }

    @Test fun save_life_chips_conserved_through_showdown() {
        val r = rngOf(nonce =202L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }
        val firstSeat = s.toActSeat!!
        s = StudReducer.act(s, firstSeat, Action(ActionType.SAVE_LIFE), r)
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 80) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val a = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(a), r)
        }
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    // -------------------------------------------------- raise cap 3 enforcement

    @Test fun fourth_raise_in_a_street_is_coerced_to_call() {
        val r = rngOf(nonce = 11L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(100_000, 100_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 헤즈업 3rd street: bring-in 좌석 = lastAggressorSeat. 다른 좌석이 toAct.
        val bringInSeat = s.lastAggressorSeat!!
        val otherSeat = s.players.first { it.seat != bringInSeat && !it.folded }.seat
        assertThat(s.toActSeat).isEqualTo(otherSeat)
        assertThat(s.raisesThisStreet).isEqualTo(0)

        // Raise #1: other → 50 (complete). 25→50 = 25 increment ≥ baseline(bring-in 25) ⇒ full raise.
        s = StudReducer.act(s, otherSeat, Action(ActionType.RAISE, 50L), r)
        assertThat(s.raisesThisStreet).isEqualTo(1)
        // Raise #2: bring-in → 100. 50→100 = 50 ≥ 50 ⇒ full raise.
        s = StudReducer.act(s, bringInSeat, Action(ActionType.RAISE, 100L), r)
        assertThat(s.raisesThisStreet).isEqualTo(2)
        // Raise #3: other → 200. 100→200 = 100 ≥ 100 ⇒ full raise.
        s = StudReducer.act(s, otherSeat, Action(ActionType.RAISE, 200L), r)
        assertThat(s.raisesThisStreet).isEqualTo(3)
        // Raise #4 시도: bring-in → 400. cap 적중 → CALL 로 강등.
        // 강등된 후 라운드가 즉시 완료되어 4th street 로 advance 될 수 있음 (헤즈업).
        // 검증 핵심: bring-in 의 committedThisHand 가 400 까지 늘지 않았어야 함 (콜 이상은 200).
        s = StudReducer.act(s, bringInSeat, Action(ActionType.RAISE, 400L), r)
        val bringInPlayer = s.players.first { it.seat == bringInSeat }
        // 콜 강등 → committedThisHand 는 ante(10) + 200(매칭) = 210 (HiLo 외 정규 NL 베팅 산식).
        // 400 raise 가 통과했다면 committedThisHand 가 410 이 돼야 함.
        assertThat(bringInPlayer.committedThisHand).isLessThan(400L)
        // 라운드 완료로 4th 진입 시 raisesThisStreet 는 reset 0; 아직 3rd 면 raisesThisStreet 은 3 그대로.
        if (s.street == Street.THIRD) {
            assertThat(s.raisesThisStreet).isEqualTo(3)
        } else {
            assertThat(s.raisesThisStreet).isEqualTo(0)
        }
    }

    @Test fun raise_cap_resets_on_new_street() {
        val r = rngOf(nonce = 23L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(100_000, 100_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3rd 통과: 모두 콜로 그냥 진행.
        var guard = 0
        while (s.street == Street.THIRD && guard++ < 8) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val a = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(a), r)
        }
        assertThat(s.street).isEqualTo(Street.FOURTH)
        assertThat(s.raisesThisStreet).isEqualTo(0)  // 새 스트릿에서 cap 카운터 리셋됨
    }

    // -------------------------------------------------- HiLo split showdown

    @Test fun hilo_mode_runs_showdown_without_crash_and_conserves_chips() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 47L)
        var s = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 모두 콜/체크로 끝까지 진행. DECLARE 단계 진입 시에는 HIGH 선언으로 통과.
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val a: Action = if (s.street == Street.DECLARE) {
                Action(ActionType.DECLARE, declaration = Declaration.HIGH)
            } else {
                val me = s.players.first { it.seat == seat }
                val t = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
                Action(t)
            }
            s = StudReducer.act(s, seat, a, r)
        }
        assertThat(s.pendingShowdown).isNotNull()
        // 칩 보존
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    @Test fun hilo_same_seat_winning_hi_and_lo_marks_scoop_for_ui() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        var declareState: com.infocar.pokermaster.core.model.GameState? = null
        var scoopSeat: Int? = null
        var rng: Rng? = null

        for (nonce in 1L..300L) {
            val r = rngOf(nonce)
            val start = StudReducer.startHand(
                config = cfg,
                players = players(10_000, 10_000),
                prevBtnSeat = null,
                rng = r,
                handIndex = 1L,
                startingVersion = 0L,
            )
            val candidate = runUntilDeclare(start, r)
            if (candidate.street != Street.DECLARE) continue

            val live = candidate.players.filter { !it.folded }
            val bestHiSeat = live.maxBy { HandEvaluatorHiLo.evaluateHigh(it.holeCards + it.upCards) }.seat
            val bestLoSeat = live.minBy { HandEvaluatorHiLo.evaluateLow(it.holeCards + it.upCards) }.seat
            if (bestHiSeat == bestLoSeat) {
                declareState = candidate
                scoopSeat = bestHiSeat
                rng = r
                break
            }
        }

        var s = checkNotNull(declareState) { "No deterministic Hi-Lo scoop fixture found" }
        val targetScoopSeat = checkNotNull(scoopSeat)
        val r = checkNotNull(rng)

        while (s.street == Street.DECLARE) {
            val seat = s.toActSeat!!
            val declaration = if (seat == targetScoopSeat) Declaration.SWING else Declaration.HIGH
            s = StudReducer.act(s, seat, Action(ActionType.DECLARE, declaration = declaration), r)
        }

        assertThat(s.pendingShowdown).isNotNull()
        val mainPot = s.pendingShowdown!!.pots.first()
        assertThat(mainPot.hiWinnerSeats).containsExactly(targetScoopSeat)
        assertThat(mainPot.loWinnerSeats).containsExactly(targetScoopSeat)
        assertThat(mainPot.scoopWinnerSeats).containsExactly(targetScoopSeat)
    }

    // -------------------------------------------------- 7th street is dealt down

    @Test fun seventh_street_card_lands_in_hole_cards_not_up_cards() {
        val r = rngOf(nonce =77L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 6th street 까지 모두 콜/체크
        var guard = 0
        while (s.street != Street.SEVENTH && s.pendingShowdown == null && guard++ < 50) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.SEVENTH)
        // 7th 진입 후: hole 3, up 4 (3rd 2D + 7th 1D = 3D, 3rd 1U + 4th 1U + 5th 1U + 6th 1U = 4U)
        for (p in s.players.filter { !it.folded }) {
            assertThat(p.holeCards.size).isEqualTo(3)
            assertThat(p.upCards.size).isEqualTo(4)
        }
    }

    // -------------------------------------------------- HiLo declare phase

    /** 7th 베팅이 끝날 때까지 콜/체크로 진행. DECLARE 단계 진입 직전에서 멈춤 (HiLo 모드). */
    private fun runUntilDeclare(
        initial: com.infocar.pokermaster.core.model.GameState,
        rng: Rng,
    ): com.infocar.pokermaster.core.model.GameState {
        var s = initial
        var guard = 0
        while (s.street != Street.DECLARE && s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), rng)
        }
        return s
    }

    @Test fun hilo_mode_enters_declare_phase_after_seventh_street() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 311L)
        val start = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val s = runUntilDeclare(start, r)
        assertThat(s.street).isEqualTo(Street.DECLARE)
        assertThat(s.pendingShowdown).isNull()
        // 첫 살아있는 좌석이 toAct.
        val firstAlive = s.players.filter { !it.folded }.minOf { it.seat }
        assertThat(s.toActSeat).isEqualTo(firstAlive)
        assertThat(s.declarations).isEmpty()
    }

    @Test fun stud_high_only_mode_skips_declare_and_goes_to_showdown() {
        // HiLo 가 아닌 경우 declare 안 거치고 SHOWDOWN 으로 직행.
        val r = rngOf(nonce = 312L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val a = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(a), r)
            assertThat(s.street).isNotEqualTo(Street.DECLARE)
        }
        assertThat(s.pendingShowdown).isNotNull()
        assertThat(s.street).isEqualTo(Street.SHOWDOWN)
    }

    @Test fun all_alive_players_declaring_transitions_to_showdown() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 4, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 313L)
        val start = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        var s = runUntilDeclare(start, r)
        assertThat(s.street).isEqualTo(Street.DECLARE)

        val aliveSeats = s.players.filter { !it.folded }.map { it.seat }.sorted()
        // 좌석 순서대로 declare. 모두 HIGH 선언.
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }
        for (seat in aliveSeats) {
            assertThat(s.toActSeat).isEqualTo(seat)
            s = StudReducer.act(
                s, seat, Action(ActionType.DECLARE, declaration = Declaration.HIGH), r
            )
        }
        // 모두 declare 완료 → SHOWDOWN
        assertThat(s.pendingShowdown).isNotNull()
        assertThat(s.street).isEqualTo(Street.SHOWDOWN)
        assertThat(s.toActSeat).isNull()
        // 칩 보존
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    @Test fun declaring_out_of_turn_throws() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 4, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 314L)
        val start = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val s = runUntilDeclare(start, r)
        assertThat(s.street).isEqualTo(Street.DECLARE)
        val firstSeat = s.toActSeat!!
        val wrongSeat = s.players
            .filter { !it.folded && it.seat != firstSeat }
            .minOf { it.seat }
        assertThrows<IllegalArgumentException> {
            StudReducer.act(s, wrongSeat, Action(ActionType.DECLARE, declaration = Declaration.HIGH), r)
        }
    }

    @Test fun declare_action_without_payload_throws() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 315L)
        val start = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val s = runUntilDeclare(start, r)
        assertThat(s.street).isEqualTo(Street.DECLARE)
        assertThrows<IllegalArgumentException> {
            StudReducer.act(s, s.toActSeat!!, Action(ActionType.DECLARE, declaration = null), r)
        }
    }

    @Test fun non_declare_action_during_declare_phase_throws() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 316L)
        val start = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val s = runUntilDeclare(start, r)
        assertThat(s.street).isEqualTo(Street.DECLARE)
        assertThrows<IllegalArgumentException> {
            StudReducer.act(s, s.toActSeat!!, Action(ActionType.CHECK), r)
        }
    }

    @Test fun folded_players_are_skipped_in_declare_order() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 4, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 317L)
        var s = StudReducer.startHand(
            config = cfg,
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 첫 액터(브링인 다음 좌석) 폴드시킴 — declare 순서에서 skip 검증.
        val firstActor = s.toActSeat!!
        s = StudReducer.act(s, firstActor, Action(ActionType.FOLD), r)

        // 나머지는 콜/체크로 진행
        var guard = 0
        while (s.street != Street.DECLARE && s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.DECLARE)
        // 폴드한 좌석은 declare 순서에 없음
        val expectedAlive = s.players.filter { !it.folded }.map { it.seat }.sorted()
        assertThat(expectedAlive).doesNotContain(firstActor)
        // 모두 declare
        for (seat in expectedAlive) {
            assertThat(s.toActSeat).isEqualTo(seat)
            s = StudReducer.act(
                s, seat, Action(ActionType.DECLARE, declaration = Declaration.HIGH), r
            )
        }
        // 폴드 좌석은 declarations 에 포함되지 않음
        assertThat(s.declarations.keys).containsExactlyElementsIn(expectedAlive)
        assertThat(s.declarations.keys).doesNotContain(firstActor)
    }

    @Test fun all_in_player_can_still_declare() {
        // 큰 스택 + 큰 스택 헤즈업으로 — 깡통 raise 패턴으로 작은 스택을 all-in 으로 몰아붙임.
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 318L)
        var s = StudReducer.startHand(
            config = cfg,
            players = players(60L, 10_000L),  // seat 0 = 60칩 (앤티 10 후 50, 콜만 가능 → 빠른 all-in)
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 모두 콜/체크/올인으로 7th 까지.
        var guard = 0
        while (s.street != Street.DECLARE && s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        // 작은 스택이 일찍 all-in 으로 들어가 라운드가 즉시 진행되며 7th 까지 도달 후 declare 단계로 진입.
        assertThat(s.street).isEqualTo(Street.DECLARE)
        val seat0 = s.players[0]
        val seat1 = s.players[1]
        assertThat(seat0.alive).isTrue()
        assertThat(seat1.alive).isTrue()
        // 첫 좌석부터 declare. all-in 좌석(seat 0) 도 정상 declare.
        for (seat in listOf(seat0.seat, seat1.seat)) {
            s = StudReducer.act(
                s, seat, Action(ActionType.DECLARE, declaration = Declaration.HIGH), r
            )
        }
        assertThat(s.pendingShowdown).isNotNull()
    }

    // -------------------------------------------------- bestExposedSeat 회귀

    @Test fun exposed_strength_key_is_safe_for_empty_up_cards() {
        // 빈 upCards 에 대해 maxOf 크래시 없이 sentinel 리턴.
        val key = StudReducer.exposedStrengthKey(emptyList())
        assertThat(key).isEqualTo(listOf(-1))
    }

    @Test fun exposed_strength_key_handles_single_up_card_without_crash() {
        // upCards 1장 — 정상 키 산출 (maxOf 크래시 없음, maxOfOrNull 로 방어).
        val one = listOf(Card(Suit.HEART, Rank.SEVEN))
        val key = StudReducer.exposedStrengthKey(one)
        // tier=0 (high card), groupRanks=[7], maxSuitOrd=Heart.ordinal
        assertThat(key[0]).isEqualTo(0)
        assertThat(key[1]).isEqualTo(7)
        assertThat(key[2]).isEqualTo(Suit.HEART.ordinal)
    }

    // -------------------------------------------------- A1: 5th street big bet enforcement (NL doc)

    /**
     * A1 — 5th street 진입 시 big bet 강제(=minRaise 가 small bet 의 2배로 도약) 검증.
     *
     * **현 구현은 NL 스타일** (`StudReducer.kt` 33-34행 주석 참조). fixed-limit 의 4th=small bet,
     * 5th 이상=big bet 더블 강제는 적용되지 않으며 `minRaise`/`betToCall` 산식이 [GameMode] 따라 분기되지
     * 않는다. 본 테스트는 **NL 스타일임을 documenting** 하는 회귀 테스트 — small/big bet 도약 invariant 가
     * 강제되지 않음을 확인. fixed-limit 변환 도입 시 본 테스트는 실패해야 하며 그 시점에 새 spec 으로 갱신.
     */
    @Test fun fifth_street_does_not_enforce_big_bet_minraise_in_nl_style() {
        val r = rngOf(nonce = 401L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 2, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3rd 스트릿 콜로 통과
        var guard = 0
        while (s.street == Street.THIRD && guard++ < 8) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.FOURTH)

        // 4th 의 minRaise 기록. NL 스타일에선 bring-in 베이스라인.
        val fourthMinRaise = s.minRaise

        // 4th 도 모두 체크/콜 통과 → 5th 진입
        guard = 0
        while (s.street == Street.FOURTH && guard++ < 8) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.FIFTH)

        // **문서화 invariant**: NL 스타일에선 5th minRaise 가 4th minRaise 의 2배로 도약하지 *않는다*.
        // (fixed-limit 도입 시 == 2 * fourthMinRaise 가 되어 본 테스트가 실패하면 spec 변경.)
        assertThat(s.minRaise).isEqualTo(fourthMinRaise)
        // betToCall 도 reset(=0) 인지 확인 (NL 베팅 라운드 시작 상태).
        assertThat(s.betToCall).isEqualTo(0L)
    }

    // -------------------------------------------------- A3: best exposed seat 액션 후 fold → recompute

    /**
     * A3 — 4th street 에서 best exposed 좌석이 액션 후 폴드. 해당 라운드 *내에서* 다음 토큰이
     * "다음 살아있는 active 좌석" 으로 이동하는지 확인.
     *
     * 현 구현(`StudReducer.act` 203행)은 폴드 후 다음 좌석을 [bestExposedSeat] 로 재계산하지 않고
     * `nextActiveSeatAfter(seat, ...)` 로 단순히 다음 살아있는 좌석으로 이동한다.
     * (best exposed 재계산은 *새 스트릿 진입 시점에만*. 한 라운드 안에서는 좌석 순서 진행.)
     */
    @Test fun fourth_street_best_exposed_folds_then_token_moves_to_next_alive_seat() {
        val r = rngOf(nonce = 503L)
        var s = StudReducer.startHand(
            config = config.copy(seats = 4, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3rd 스트릿 통과
        var guard = 0
        while (s.street == Street.THIRD && guard++ < 12) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.FOURTH)
        val bestExposedSeat = s.toActSeat!!
        val activeSeatsBefore = s.players.filter { it.active }.map { it.seat }.sorted()
        assertThat(activeSeatsBefore).contains(bestExposedSeat)

        // best exposed 가 폴드 → 토큰은 라이브 액티브 다음 좌석으로 이동(라운드 내).
        s = StudReducer.act(s, bestExposedSeat, Action(ActionType.FOLD), r)

        // 폴드 후 — 1명 남으면 즉시 쇼다운, 그렇지 않으면 다음 좌석에 토큰.
        if (s.pendingShowdown != null) {
            // 헤즈업 같이 1명 남는 분기 — 호출자가 처리.
            val live = s.players.filter { !it.folded }
            assertThat(live.size).isEqualTo(1)
        } else {
            assertThat(s.toActSeat).isNotNull()
            assertThat(s.toActSeat).isNotEqualTo(bestExposedSeat)
            // 토큰은 활성 좌석 (folded 아님 + chips > 0).
            val nextSeat = s.toActSeat!!
            val next = s.players.first { it.seat == nextSeat }
            assertThat(next.folded).isFalse()
            assertThat(next.active).isTrue()
        }
    }

    // -------------------------------------------------- A4: 잘못된 좌석 액션 시도 → IAE

    /**
     * A4 — `toActSeat` 이 아닌 좌석에서 액션을 시도하면 require trip → IAE.
     * (HoldemReducer 와 같은 패턴; `StudReducer.act` 168행.)
     */
    @Test fun acting_out_of_turn_throws_illegal_argument() {
        val r = rngOf(nonce = 601L)
        val s = StudReducer.startHand(
            config = config.copy(seats = 4, ante = 10L, bringIn = 25L),
            players = players(10_000, 10_000, 10_000, 10_000),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        val toActSeat = s.toActSeat!!
        val wrongSeat = s.players
            .filter { it.seat != toActSeat && it.active }
            .first().seat
        assertThrows<IllegalArgumentException> {
            StudReducer.act(s, wrongSeat, Action(ActionType.CALL), r)
        }
    }

    // -------------------------------------------------- B4: declare 단계 active=false 좌석도 통과

    /**
     * B4 — declare 단계에서 all-in(=active=false 가능) 좌석이 toActSeat 에 잡히고
     *      `applyDeclare` 의 require(player.alive) 에서 통과하는지 명시 검증.
     *
     * `PlayerState.active` 는 `!folded && !allIn` 이고 `alive` 는 `!folded`. 베팅 단계에서는 active 검사,
     * declare 단계에서는 alive 검사. 따라서 all-in 좌석도 정상 declare 가능.
     */
    @Test fun all_in_player_passes_declare_require_alive_check() {
        val cfg = config.copy(mode = GameMode.SEVEN_STUD_HI_LO, seats = 2, ante = 10L, bringIn = 25L)
        val r = rngOf(nonce = 318L)
        var s = StudReducer.startHand(
            config = cfg,
            players = players(60L, 10_000L),
            prevBtnSeat = null,
            rng = r,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 7th 까지 콜/체크 — seat 0(60칩) 가 일찍 all-in.
        var guard = 0
        while (s.street != Street.DECLARE && s.pendingShowdown == null && guard++ < 200) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val act = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(act), r)
        }
        assertThat(s.street).isEqualTo(Street.DECLARE)
        // 첫 declarer 좌석 — alive 만으로 토큰 잡힘.
        val firstDeclarer = s.toActSeat!!
        val player = s.players.first { it.seat == firstDeclarer }
        // alive=true 이지만 all-in 일 수 있음 — declare 단계 정상 진행.
        assertThat(player.folded).isFalse()
        // alive 검증: declare 시도 → 정상 통과 (예외 없음).
        s = StudReducer.act(
            s, firstDeclarer, Action(ActionType.DECLARE, declaration = Declaration.HIGH), r
        )
        assertThat(s.declarations).containsKey(firstDeclarer)
    }
}
