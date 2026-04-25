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

/**
 * 다인(3·4명) 테이블 NL 홀덤 reducer 동작 검증.
 *
 * 검증 대상:
 *  - 블라인드 결정: BTN+1=SB, BTN+2=BB (3+인)
 *  - 딜링 순서: SB→BB→UTG→...→BTN 시계방향 두 바퀴
 *  - 프리플롭 첫 액션: UTG = BB+1 (3-max 에선 wrap → BTN)
 *  - 포스트플롭 첫 액션: BTN+1 = SB (살아 있는 첫 좌석)
 *  - 사이드팟: 일부 all-in 시 메인/사이드 분리
 *  - 칩 보존: 모든 시나리오에서 strict
 *
 * Heads-up 동작은 [HoldemReducerTest] 에서 보장. 본 파일은 다인 분기에 집중.
 */
class HoldemReducerMultiwayTest {

    private val configBase = TableConfig(
        mode = GameMode.HOLDEM_NL,
        seats = 4,
        smallBlind = 25L,
        bigBlind = 50L,
    )

    private fun playersN(count: Int, chips: Long = 10_000L): List<PlayerState> =
        (0 until count).map { i ->
            PlayerState(
                seat = i,
                nickname = "P$i",
                isHuman = i == 0,
                personaId = if (i == 0) null else "PRO",
                chips = chips,
            )
        }

    private fun playersChips(vararg chips: Long): List<PlayerState> =
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

    // ============================================================ Blinds

    @Test fun three_handed_blind_assignment_btn_plus_one_sb_btn_plus_two_bb() {
        val cfg = configBase.copy(seats = 3)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersN(3),
            prevBtnSeat = null,
            rng = deterministicRng(),
            handIndex = 1L,
            startingVersion = 0L,
        )
        // BTN=0 → SB=1, BB=2
        assertThat(s.btnSeat).isEqualTo(0)
        assertThat(s.players[0].committedThisStreet).isEqualTo(0L)   // BTN: no blind
        assertThat(s.players[1].committedThisStreet).isEqualTo(25L)  // SB
        assertThat(s.players[2].committedThisStreet).isEqualTo(50L)  // BB
        assertThat(s.betToCall).isEqualTo(50L)
        assertThat(s.minRaise).isEqualTo(100L)
    }

    @Test fun four_handed_blind_assignment() {
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersN(4),
            prevBtnSeat = null,
            rng = deterministicRng(),
            handIndex = 1L,
            startingVersion = 0L,
        )
        // BTN=0 → SB=1, BB=2, UTG=3
        assertThat(s.btnSeat).isEqualTo(0)
        assertThat(s.players[0].committedThisStreet).isEqualTo(0L)
        assertThat(s.players[1].committedThisStreet).isEqualTo(25L)  // SB
        assertThat(s.players[2].committedThisStreet).isEqualTo(50L)  // BB
        assertThat(s.players[3].committedThisStreet).isEqualTo(0L)   // UTG
    }

    @Test fun btn_rotation_three_handed_next_hand() {
        val cfg = configBase.copy(seats = 3)
        // 첫 핸드 BTN=0 → 다음 BTN=1 → 그 다음 BTN=2.
        val s1 = HoldemReducer.startHand(cfg, playersN(3), null, deterministicRng(1), 1L, 0L)
        assertThat(s1.btnSeat).isEqualTo(0)

        val s2 = HoldemReducer.startHand(cfg, s1.players, prevBtnSeat = 0, deterministicRng(2), 2L, s1.stateVersion)
        assertThat(s2.btnSeat).isEqualTo(1)
        // BTN=1 → SB=2, BB=0
        assertThat(s2.players[1].committedThisStreet).isEqualTo(0L)
        assertThat(s2.players[2].committedThisStreet).isEqualTo(25L)
        assertThat(s2.players[0].committedThisStreet).isEqualTo(50L)
    }

    // ============================================================ Dealing order

    @Test fun three_handed_deal_order_is_sb_bb_btn_then_repeat() {
        // dealOrder 결과를 직접 관찰: 시드 결정론 → deck 인덱스 0~5 가
        // SB(1)→BB(2)→BTN(0)→SB(1)→BB(2)→BTN(0) 순서로 분배되어야 한다.
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(101L)
        val s = HoldemReducer.startHand(cfg, playersN(3), null, rng, 1L, 0L)

        // BTN=0, SB=1, BB=2.
        // dealOrder = [1,2,0] → 첫 카드: P1=deck[0], P2=deck[1], P0=deck[2]
        //                   → 둘째 카드: P1=deck[3], P2=deck[4], P0=deck[5]
        assertThat(s.players[1].holeCards).containsExactly(rng.deck[0], rng.deck[3]).inOrder()
        assertThat(s.players[2].holeCards).containsExactly(rng.deck[1], rng.deck[4]).inOrder()
        assertThat(s.players[0].holeCards).containsExactly(rng.deck[2], rng.deck[5]).inOrder()
        assertThat(s.deckCursor).isEqualTo(6)  // 3명 × 2장
    }

    @Test fun four_handed_deal_order_is_sb_bb_utg_btn_then_repeat() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(102L)
        val s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)

        // BTN=0, SB=1, BB=2, UTG=3.
        // dealOrder = [1,2,3,0] → 1라운드: deck[0..3], 2라운드: deck[4..7].
        assertThat(s.players[1].holeCards).containsExactly(rng.deck[0], rng.deck[4]).inOrder()
        assertThat(s.players[2].holeCards).containsExactly(rng.deck[1], rng.deck[5]).inOrder()
        assertThat(s.players[3].holeCards).containsExactly(rng.deck[2], rng.deck[6]).inOrder()
        assertThat(s.players[0].holeCards).containsExactly(rng.deck[3], rng.deck[7]).inOrder()
        assertThat(s.deckCursor).isEqualTo(8)
    }

    @Test fun no_card_is_dealt_twice_three_handed() {
        val cfg = configBase.copy(seats = 3)
        val s = HoldemReducer.startHand(cfg, playersN(3), null, deterministicRng(99L), 1L, 0L)
        val all = s.players.flatMap { it.holeCards }
        assertThat(all).hasSize(6)
        assertThat(all.toSet()).hasSize(6)   // 모두 distinct
    }

    @Test fun no_card_is_dealt_twice_four_handed() {
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(cfg, playersN(4), null, deterministicRng(98L), 1L, 0L)
        val all = s.players.flatMap { it.holeCards }
        assertThat(all).hasSize(8)
        assertThat(all.toSet()).hasSize(8)
    }

    // ============================================================ First-to-act

    @Test fun three_handed_preflop_first_to_act_is_btn() {
        // 3-max: UTG = BB+1 = wrap → BTN. (BB=2 → next active = 0 = BTN)
        val cfg = configBase.copy(seats = 3)
        val s = HoldemReducer.startHand(cfg, playersN(3), null, deterministicRng(), 1L, 0L)
        assertThat(s.street).isEqualTo(Street.PREFLOP)
        assertThat(s.toActSeat).isEqualTo(0)   // BTN
    }

    @Test fun four_handed_preflop_first_to_act_is_utg() {
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(cfg, playersN(4), null, deterministicRng(), 1L, 0L)
        assertThat(s.street).isEqualTo(Street.PREFLOP)
        assertThat(s.toActSeat).isEqualTo(3)   // UTG = BB+1
    }

    @Test fun three_handed_postflop_first_to_act_is_sb() {
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(11L)
        var s = HoldemReducer.startHand(cfg, playersN(3), null, rng, 1L, 0L)
        // pre-flop: BTN(0) call, SB(1) call, BB(2) check → flop
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.toActSeat).isEqualTo(1)   // SB(=BTN+1) postflop first
    }

    @Test fun four_handed_postflop_first_to_act_is_sb() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(12L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        // pre-flop: UTG(3) call, BTN(0) call, SB(1) call, BB(2) check → flop
        s = HoldemReducer.act(s, 3, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.toActSeat).isEqualTo(1)   // SB
    }

    @Test fun four_handed_postflop_skips_folded_sb_to_bb() {
        // SB 가 프리플롭에 폴드 → 포스트플롭 first to act = BB.
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(13L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        // pre-flop: UTG(3) call, BTN(0) call, SB(1) FOLD, BB(2) check
        s = HoldemReducer.act(s, 3, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.FOLD), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.toActSeat).isEqualTo(2)   // BB (SB folded, skipped)
    }

    // ============================================================ Action order in betting round

    @Test fun four_handed_preflop_action_cycles_utg_btn_sb_bb() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(14L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        assertThat(s.toActSeat).isEqualTo(3)   // UTG
        s = HoldemReducer.act(s, 3, Action(ActionType.CALL), rng)
        assertThat(s.toActSeat).isEqualTo(0)   // BTN
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        assertThat(s.toActSeat).isEqualTo(1)   // SB
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        assertThat(s.toActSeat).isEqualTo(2)   // BB option
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)
    }

    @Test fun four_handed_full_raise_reopens_and_actedThisStreet_resets() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(15L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        // UTG raise to 200 (full raise), BTN call, SB call, BB raise to 600 (full re-raise) →
        // UTG/BTN/SB 다시 액션 필요 (actedThisStreet 리셋).
        s = HoldemReducer.act(s, 3, Action(ActionType.RAISE, 200L), rng)
        assertThat(s.betToCall).isEqualTo(200L)
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.RAISE, 600L), rng)
        assertThat(s.betToCall).isEqualTo(600L)
        assertThat(s.toActSeat).isEqualTo(3)   // back to UTG
        assertThat(s.players[3].actedThisStreet).isFalse()
        assertThat(s.players[0].actedThisStreet).isFalse()
        assertThat(s.players[1].actedThisStreet).isFalse()
        assertThat(s.players[2].actedThisStreet).isTrue()
    }

    // ============================================================ Side pots (multi-way all-in)

    @Test fun three_handed_short_stack_all_in_creates_side_pot() {
        // 3명, BB(seat 2) 가 단 스택. BTN raise → SB call → BB all-in (작은 상태).
        // 이후 모두 체크다운 → main + uncalled.
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(21L)
        var s = HoldemReducer.startHand(
            cfg,
            playersChips(10_000L, 10_000L, 70L),  // P2 매우 짧음
            null, rng, 1L, 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // BTN(0) raise to 200, SB(1) call, BB(2) all-in (chips 70 → call 50 + 20 추가, total 70).
        s = HoldemReducer.act(s, 0, Action(ActionType.RAISE, 200L), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.ALL_IN), rng)
        // BB all-in 70 < betToCall(200) → call semantics, reopenAction stays true (no raise),
        // BTN/SB 도 actedThisStreet=true 이므로 라운드 즉시 종료 → flop 으로 전진.
        // 다음 BTN(0) act 가 toAct (postflop SB folded? no SB still alive).
        assertThat(s.street).isEqualTo(Street.FLOP)
        // Postflop 첫 액션 = SB(1). BTN(0) check, SB(1) check 가능.
        assertThat(s.toActSeat).isEqualTo(1)

        // SB check, BTN check → turn
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.TURN)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.RIVER)
        s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        // showdown
        assertThat(s.pendingShowdown).isNotNull()
        // 사이드팟: P2(70) 가 main 자격, 추가 130(=200-70) × 2 = 260 이 side
        val pots = s.pendingShowdown!!.pots
        assertThat(pots).hasSize(2)
        // main: 70*3=210, eligible {0,1,2}
        assertThat(pots[0].amount).isEqualTo(210L)
        assertThat(pots[0].eligibleSeats).containsExactly(0, 1, 2)
        // side: (200-70)*2 = 260, eligible {0,1}
        assertThat(pots[1].amount).isEqualTo(260L)
        assertThat(pots[1].eligibleSeats).containsExactly(0, 1)

        // 칩 보존
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter + s.pendingShowdown!!.deadMoney).isEqualTo(totalBefore)
    }

    @Test fun four_handed_two_short_stacks_all_in_creates_chained_side_pots() {
        // 4명, P2(BB) 와 P3(UTG) 둘 다 짧음. 각각 다른 금액 all-in 으로 다단 사이드팟 발생.
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(22L)
        var s = HoldemReducer.startHand(
            cfg,
            playersChips(10_000L, 10_000L, 300L, 600L),
            null, rng, 1L, 0L,
        )
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // pre-flop: BTN=0(big stack), SB=1(big), BB=2(300), UTG=3(600).
        // UTG(3) all-in → 600 (raise to 600 since 600>50 and >=minRaise=100 full raise)
        s = HoldemReducer.act(s, 3, Action(ActionType.ALL_IN), rng)
        assertThat(s.players[3].allIn).isTrue()
        assertThat(s.betToCall).isEqualTo(600L)
        // BTN(0) call 600
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        // SB(1) call 600
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        // BB(2) all-in 300 (< 600 → call semantics, partial)
        s = HoldemReducer.act(s, 2, Action(ActionType.ALL_IN), rng)
        assertThat(s.players[2].allIn).isTrue()

        // 라운드 종료 → flop. BTN/SB 살아있고 둘 다 active(non all-in) → 베팅 가능.
        assertThat(s.street).isEqualTo(Street.FLOP)
        assertThat(s.toActSeat).isEqualTo(1)   // SB

        // 둘 다 체크다운
        repeat(3) {
            s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        }
        assertThat(s.pendingShowdown).isNotNull()
        val pots = s.pendingShowdown!!.pots
        // commitments: P0=600, P1=600, P2=300, P3=600
        // L=300: 300*4=1200, eligible {0,1,2,3}
        // L=600: 300*3=900, eligible {0,1,3} (P2 all-in 자기 layer 이상 적립 X)
        assertThat(pots).hasSize(2)
        assertThat(pots[0].amount).isEqualTo(1_200L)
        assertThat(pots[0].eligibleSeats).containsExactly(0, 1, 2, 3)
        assertThat(pots[1].amount).isEqualTo(900L)
        assertThat(pots[1].eligibleSeats).containsExactly(0, 1, 3)

        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter + s.pendingShowdown!!.deadMoney).isEqualTo(totalBefore)
    }

    // ============================================================ Chip conservation

    @Test fun three_handed_full_check_down_preserves_chips() {
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(31L)
        var s = HoldemReducer.startHand(cfg, playersN(3), null, rng, 1L, 0L)
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // pre-flop: BTN call, SB call, BB check
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        // postflop check-down (3 streets × 3 players)
        repeat(3) {
            s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        }
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter + s.pendingShowdown!!.deadMoney).isEqualTo(totalBefore)
    }

    @Test fun four_handed_full_check_down_preserves_chips() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(32L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // pre-flop
        s = HoldemReducer.act(s, 3, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        // postflop check-down
        repeat(3) {
            s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 3, Action(ActionType.CHECK), rng)
            s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        }
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter + s.pendingShowdown!!.deadMoney).isEqualTo(totalBefore)
    }

    @Test fun four_handed_fold_to_winner_preserves_chips() {
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(33L)
        var s = HoldemReducer.startHand(cfg, playersN(4), null, rng, 1L, 0L)
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // 3명 폴드 → BB 단독 승
        s = HoldemReducer.act(s, 3, Action(ActionType.FOLD), rng)
        s = HoldemReducer.act(s, 0, Action(ActionType.FOLD), rng)
        s = HoldemReducer.act(s, 1, Action(ActionType.FOLD), rng)
        assertThat(s.pendingShowdown).isNotNull()
        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter).isEqualTo(totalBefore)
    }

    @Test fun three_handed_state_version_monotonic() {
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(34L)
        var s = HoldemReducer.startHand(cfg, playersN(3), null, rng, 1L, 0L)
        var prev = s.stateVersion
        s = HoldemReducer.act(s, 0, Action(ActionType.CALL), rng)
        assertThat(s.stateVersion).isGreaterThan(prev); prev = s.stateVersion
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        assertThat(s.stateVersion).isGreaterThan(prev); prev = s.stateVersion
        s = HoldemReducer.act(s, 2, Action(ActionType.CHECK), rng)
        assertThat(s.stateVersion).isGreaterThan(prev)
    }

    // ============================================================ All-in run-out

    @Test fun three_handed_all_three_all_in_preflop_runs_to_showdown() {
        // 3명 모두 동일 스택으로 all-in → 자동 런아웃 후 SHOWDOWN.
        val cfg = configBase.copy(seats = 3)
        val rng = deterministicRng(41L)
        var s = HoldemReducer.startHand(cfg, playersChips(500L, 500L, 500L), null, rng, 1L, 0L)
        val totalBefore = s.players.sumOf { it.chips + it.committedThisHand }

        // BTN(0) all-in 500 (full raise)
        s = HoldemReducer.act(s, 0, Action(ActionType.ALL_IN), rng)
        // SB(1) all-in (call 500 → already committed 25, +475)
        s = HoldemReducer.act(s, 1, Action(ActionType.ALL_IN), rng)
        // BB(2) all-in (call 500 → already committed 50, +450)
        s = HoldemReducer.act(s, 2, Action(ActionType.ALL_IN), rng)

        // 모두 all-in → 자동 런아웃, 쇼다운 도달
        assertThat(s.street).isEqualTo(Street.SHOWDOWN)
        assertThat(s.pendingShowdown).isNotNull()
        assertThat(s.toActSeat).isNull()

        val totalAfter = s.players.sumOf { it.chips }
        assertThat(totalAfter + s.pendingShowdown!!.deadMoney).isEqualTo(totalBefore)
    }
}
