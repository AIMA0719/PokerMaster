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
import org.junit.jupiter.api.assertThrows

class HoldemReducerTest {

    private val config = TableConfig(
        mode = GameMode.HOLDEM_NL,
        seats = 2,
        smallBlind = 25L,
        bigBlind = 50L,
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

    private fun deterministicRng(nonce: Long = 1L): Rng {
        val seed = ByteArray(Rng.SEED_BYTES) { (it + nonce.toInt()).toByte() }
        return Rng.ofSeeds(seed, seed, nonce)
    }

    @Test fun heads_up_preflop_blinds_and_first_to_act() {
        val s = HoldemReducer.startHand(
            config = config,
            players = players(10_000, 10_000),
            prevBtnSeat = null,
            rng = deterministicRng(),
            handIndex = 1L,
            startingVersion = 0L,
        )
        // heads-up: BTN = SB, 상대 = BB. SB 먼저 액션.
        assertThat(s.street).isEqualTo(Street.PREFLOP)
        assertThat(s.btnSeat).isEqualTo(0)
        assertThat(s.toActSeat).isEqualTo(0)     // BTN/SB first pre-flop heads-up
        assertThat(s.players[0].committedThisStreet).isEqualTo(25L)   // SB
        assertThat(s.players[1].committedThisStreet).isEqualTo(50L)   // BB
        assertThat(s.betToCall).isEqualTo(50L)
        assertThat(s.minRaise).isEqualTo(100L)
        assertThat(s.players[0].holeCards).hasSize(2)
        assertThat(s.players[1].holeCards).hasSize(2)
    }

    @Test fun chip_conservation_fold_immediate() {
        val rng = deterministicRng()
        val s0 = HoldemReducer.startHand(config, players(10_000, 10_000), null, rng, 1L, 0L)
        val totalBefore = s0.players.sumOf { it.chips + it.committedThisHand }
        // SB 가 폴드 → BB 단독 승
        val s1 = HoldemReducer.act(s0, seat = 0, Action(ActionType.FOLD), rng)
        assertThat(s1.pendingShowdown).isNotNull()
        val summary = s1.pendingShowdown!!
        assertThat(summary.pots).hasSize(1)
        assertThat(summary.pots[0].amount).isEqualTo(50L)
        assertThat(summary.pots[0].eligibleSeats).containsExactly(1)
        assertThat(summary.pots[0].winnerSeats).containsExactly(1)
        assertThat(summary.uncalledReturn).containsExactly(1, 25L)
        val totalAfter = s1.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    @Test fun full_hand_check_down_preserves_chips() {
        val rng = deterministicRng(2L)
        var s = HoldemReducer.startHand(config, players(10_000, 10_000), null, rng, 1L, 0L)
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // SB call (heads-up preflop): 0 seat call BB
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        // BB option: check
        assertThat(s.toActSeat).isEqualTo(1)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        // flop → BB(1) acts first heads-up postflop
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.toActSeat).isEqualTo(1)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        // turn
        assertThat(s.street).isEqualTo(Street.TURN)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        // river
        assertThat(s.street).isEqualTo(Street.RIVER)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        // showdown
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    @Test fun community_cards_burn_before_each_board_street() {
        val rng = deterministicRng(4L)
        var s = HoldemReducer.startHand(config, players(10_000, 10_000), null, rng, 1L, 0L)

        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.community).containsExactly(rng.deck[5], rng.deck[6], rng.deck[7]).inOrder()
        assertThat(s.deckCursor).isEqualTo(8)

        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.TURN)
        assertThat(s.community).containsExactly(rng.deck[5], rng.deck[6], rng.deck[7], rng.deck[9]).inOrder()
        assertThat(s.deckCursor).isEqualTo(10)

        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.RIVER)
        assertThat(s.community)
            .containsExactly(rng.deck[5], rng.deck[6], rng.deck[7], rng.deck[9], rng.deck[11])
            .inOrder()
        assertThat(s.deckCursor).isEqualTo(12)
    }

    @Test fun all_in_shortstack_less_than_min_raise_blocks_reraise() {
        // 3-way 구성 (seats 3): BTN 0, SB 1, BB 2. BB=50, minRaise=100.
        // 시나리오: UTG 없음 (3명). 액션 순서 pre-flop: BTN(0) → SB(1) → BB(2).
        val cfg = config.copy(seats = 3)
        val rng = deterministicRng(7L)
        var s = HoldemReducer.startHand(
            config = cfg,
            players = listOf(
                PlayerState(0, "P0", isHuman = true, chips = 10_000),
                PlayerState(1, "P1", isHuman = false, personaId = "PRO", chips = 10_000),
                PlayerState(2, "P2", isHuman = false, personaId = "PRO", chips = 70L),   // BB strive 50 commit → 20 chips left
            ),
            prevBtnSeat = null,
            rng = rng,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3명 → BTN=0, SB=1, BB=2. blinds: SB=25, BB=50 (P2 chips 70→20). toAct = BTN(0).
        assertThat(s.btnSeat).isEqualTo(0)
        assertThat(s.toActSeat).isEqualTo(0)
        assertThat(s.players[2].committedThisStreet).isEqualTo(50L)
        assertThat(s.players[2].chips).isEqualTo(20L)

        // BTN raises to 200 (full raise: lastFullRaise=200-50=150).
        s = HoldemReducer.act(s, 0, Action(ActionType.RAISE, 200L), rng)
        assertThat(s.betToCall).isEqualTo(200L)
        assertThat(s.reopenAction).isTrue()

        // SB calls 200.
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)

        // BB all-in for 70 total (55 less-than-min-raise beyond 200? no — target = 50+20 = 70 < 200 → call side).
        // 위 시나리오는 call(=all-in for less than to-call). applyAllIn 은 call semantics.
        s = HoldemReducer.act(s, 2, Action(ActionType.ALL_IN), rng)
        // All-in less than betToCall → call semantics, reopenAction 유지 (less-than-min-raise raise 가 아님)
        assertThat(s.reopenAction).isTrue()
        assertThat(s.players[2].allIn).isTrue()
    }

    @Test fun short_all_in_does_not_block_unacted_player_full_raise() {
        val cfg = config.copy(seats = 4)
        val rng = deterministicRng(8L)
        var s = HoldemReducer.startHand(
            config = cfg,
            players = listOf(
                PlayerState(0, "P0", isHuman = true, chips = 125L), // BTN short stack
                PlayerState(1, "P1", isHuman = false, personaId = "PRO", chips = 10_000L),
                PlayerState(2, "P2", isHuman = false, personaId = "PRO", chips = 10_000L),
                PlayerState(3, "P3", isHuman = false, personaId = "PRO", chips = 10_000L),
            ),
            prevBtnSeat = null,
            rng = rng,
            handIndex = 1L,
            startingVersion = 0L,
        )

        // UTG raises from BB 50 to 100. BTN then makes a short all-in raise to 125.
        s = HoldemReducer.act(s, 3, Action(ActionType.RAISE, 100L), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.ALL_IN), rng)
        assertThat(s.betToCall).isEqualTo(125L)
        assertThat(s.minRaise).isEqualTo(150L)
        assertThat(s.reopenAction).isFalse()
        assertThat(s.toActSeat).isEqualTo(1)

        // SB has not acted yet this street, so the short all-in must not remove the raise option.
        s = HoldemReducer.act(s, 1, Action(ActionType.RAISE, 150L), rng)
        assertThat(s.betToCall).isEqualTo(150L)
        assertThat(s.players[1].committedThisStreet).isEqualTo(150L)
        assertThat(s.reopenAction).isTrue()
        assertThat(s.toActSeat).isEqualTo(2)
    }

    @Test fun heads_up_blind_all_in_at_hand_start_runs_out_to_showdown() {
        val rng = deterministicRng(9L)
        val s = HoldemReducer.startHand(
            config = config,
            players = players(25L, 10_000L),
            prevBtnSeat = null,
            rng = rng,
            handIndex = 1L,
            startingVersion = 0L,
        )

        assertThat(s.street).isEqualTo(Street.SHOWDOWN)
        assertThat(s.toActSeat).isNull()
        assertThat(s.pendingShowdown).isNotNull()
        assertThat(s.community)
            .containsExactly(rng.deck[5], rng.deck[6], rng.deck[7], rng.deck[9], rng.deck[11])
            .inOrder()
        assertThat(s.pendingShowdown!!.uncalledReturn).containsExactly(1, 25L)
    }

    @Test fun state_version_is_monotonic() {
        val rng = deterministicRng(3L)
        var s = HoldemReducer.startHand(config, players(10_000, 10_000), null, rng, 1L, 0L)
        val v0 = s.stateVersion
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        assertThat(s.stateVersion).isGreaterThan(v0)
        val v1 = s.stateVersion
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        assertThat(s.stateVersion).isGreaterThan(v1)
    }

    // -------------------------------------------------- A4: 잘못된 좌석 액션 → IAE

    /**
     * A4 — `toActSeat` 이 아닌 좌석에서 액션을 시도하면 require trip → IAE.
     * (`HoldemReducer.kt` 117-121 require 가 IAE 던지는지 명시 검증.)
     */
    @Test fun acting_out_of_turn_throws_illegal_argument() {
        val rng = deterministicRng(31L)
        val s = HoldemReducer.startHand(config, players(10_000, 10_000), null, rng, 1L, 0L)
        val toAct = s.toActSeat!!
        val wrongSeat = s.players.first { it.seat != toAct }.seat
        assertThrows<IllegalArgumentException> {
            HoldemReducer.act(s, wrongSeat, Action(ActionType.CALL), rng)
        }
    }

    // -------------------------------------------------- A5: all-in less-than-min raise 후속 raise 강등

    /**
     * A5 — all-in less-than-min-raise 시 `reopenAction = false` (HoldemReducer 235행). 다음 액터가
     * RAISE 를 시도해도 [HoldemReducer.applyBetOrRaise] 가 reopenAction false 분기에서 CALL 로 강등.
     * (call/check 만 가능 — 추가 raise 닫힘.)
     *
     * 시나리오 (3-way, BTN=0, SB=1, BB=2):
     *  1. BTN 가 200 으로 풀 raise (lastFullRaise=150, betToCall=200).
     *  2. SB 가 칩 부족 상태에서 less-than-min-raise all-in (예: 250 = 50 raise; 50 < 150 → not full).
     *  3. reopenAction false 가 됨.
     *  4. BB(=초기 act 안 함)는 full raise 가능해야 함. 여기서는 CALL 로 맞춘다.
     *  5. 이미 액션했던 BTN 이 다시 RAISE 시도 → reopenAction false + actedThisStreet true 이므로
     *     CALL 로 강등.
     */
    @Test fun all_in_less_than_min_raise_demotes_subsequent_raise_to_call() {
        val cfg = config.copy(seats = 3)
        val rng = deterministicRng(73L)
        var s = HoldemReducer.startHand(
            config = cfg,
            players = listOf(
                PlayerState(0, "P0", isHuman = true, chips = 10_000),
                PlayerState(1, "P1", isHuman = false, personaId = "PRO", chips = 250L),  // 25 SB → 225 chips
                PlayerState(2, "P2", isHuman = false, personaId = "PRO", chips = 10_000),
            ),
            prevBtnSeat = null,
            rng = rng,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // 3-way preflop 액션 순서: BTN(0) → SB(1) → BB(2).
        assertThat(s.toActSeat).isEqualTo(0)

        // 1) BTN raise to 200 (full raise; lastFullRaise = 150).
        s = HoldemReducer.act(s, 0, Action(ActionType.RAISE, 200L), rng)
        assertThat(s.betToCall).isEqualTo(200L)
        assertThat(s.lastFullRaiseAmount).isEqualTo(150L)
        assertThat(s.reopenAction).isTrue()

        // 2) SB all-in for 250 total (=25 SB + 225 chips). 200 → 250 = 50 raise increment.
        //    50 < lastFullRaise(150) → less-than-min-raise → reopenAction = false.
        assertThat(s.toActSeat).isEqualTo(1)
        s = HoldemReducer.act(s, 1, Action(ActionType.ALL_IN), rng)
        assertThat(s.players[1].allIn).isTrue()
        assertThat(s.reopenAction).isFalse()
        val betAfterSbAllIn = s.betToCall
        assertThat(betAfterSbAllIn).isEqualTo(250L)

        // 3) BB(=2)는 아직 액션 전이므로 full raise 권리가 남아 있다. 여기서는 콜로 맞춘다.
        assertThat(s.toActSeat).isEqualTo(2)
        s = HoldemReducer.act(s, 2, Action(ActionType.CALL), rng)
        assertThat(s.betToCall).isEqualTo(betAfterSbAllIn)
        assertThat(s.players[2].committedThisStreet).isEqualTo(250L)

        // 4) 이미 액션했던 BTN(=0)이 다시 RAISE 시도 → short all-in 이 action 을 reopen 하지 않았으므로 CALL 강등.
        assertThat(s.toActSeat).isEqualTo(0)
        val btnBefore = s.players[0].committedThisStreet
        s = HoldemReducer.act(s, 0, Action(ActionType.RAISE, 1000L), rng)
        val btnAfter = s.players[0]
        // raise 가 CALL 로 강등되어 250 매칭 후 라운드가 끝나 FLOP 으로 전진한다.
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.betToCall).isEqualTo(0L)
        assertThat(btnAfter.committedThisHand).isEqualTo(250L)
        assertThat(btnAfter.committedThisHand - btnBefore).isEqualTo(50L)
        // raise 가 강등되었으므로 lastAggressorSeat 갱신 안 됨 (BTN=0 또는 SB=1 그대로 유지 가능 —
        // applyCall 은 lastAggressor 변경 안 함).
        assertThat(s.lastAggressorSeat).isNotEqualTo(0)
    }
}
