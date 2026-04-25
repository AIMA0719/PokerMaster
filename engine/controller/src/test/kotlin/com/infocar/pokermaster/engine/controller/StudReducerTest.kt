package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.Rng
import org.junit.jupiter.api.Test

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

        // 모두 콜/체크로 끝까지 진행.
        var guard = 0
        while (s.pendingShowdown == null && guard++ < 80) {
            val seat = s.toActSeat ?: break
            val me = s.players.first { it.seat == seat }
            val a = if (me.committedThisStreet >= s.betToCall) ActionType.CHECK else ActionType.CALL
            s = StudReducer.act(s, seat, Action(a), r)
        }
        assertThat(s.pendingShowdown).isNotNull()
        // 칩 보존
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
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
}
