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
 * 파산(chips==0) 좌석이 다음 핸드부터 자동 제외되는지 검증.
 *
 * 폴드(folded=true, chips>0) 와 파산(chips==0) 분리:
 *  - 폴드는 한 핸드만 X (다음 핸드 startHand 에서 reset).
 *  - 파산은 startHand 가 chips==0 좌석을 영구 folded 처리 → 블라인드/딜링/액션 순서 모두 제외.
 *
 * 다인 → 한 명 파산 → 헤즈업 자동 전환도 함께 검증.
 */
class HoldemReducerBustTest {

    private val configBase = TableConfig(
        mode = GameMode.HOLDEM_NL,
        seats = 4,
        smallBlind = 25L,
        bigBlind = 50L,
    )

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

    // ========================================================== Single-axis guards

    @Test fun bust_seat_excluded_from_next_hand_blinds() {
        // 4명 시작, P3(BTN+3 = UTG) 가 파산 → 다음 핸드 3명만 진행.
        // 첫 핸드 BTN=0 → 다음 BTN=1 → SB=2, BB=3 (P3 살아있다면). 그러나 P3 chips=0 이면
        // P3 는 SB/BB 후보에서 제외 → BB=0 (wrap).
        val cfg = configBase.copy(seats = 4)
        // 초기 chips: P0=10000, P1=10000, P2=10000, P3=0 (이미 파산 상태로 다음 핸드 시작 시뮬레이션)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 10_000L, 0L),
            prevBtnSeat = 0,   // 이전 핸드 BTN=0 → 다음 BTN 시계방향 next alive
            rng = deterministicRng(101L),
            handIndex = 2L,
            startingVersion = 0L,
        )

        // P3 는 살아있는 좌석 후보 X. 살아있는 시트 = [0,1,2].
        // BTN: prev=0 → next > 0 = 1.
        assertThat(s.btnSeat).isEqualTo(1)
        // 3명 (live) 분기: SB = next(BTN=1) = 2, BB = next(SB=2) = 0 (wrap).
        assertThat(s.players[1].committedThisStreet).isEqualTo(0L)   // BTN
        assertThat(s.players[2].committedThisStreet).isEqualTo(25L)  // SB
        assertThat(s.players[0].committedThisStreet).isEqualTo(50L)  // BB (wrap)
        // P3 는 블라인드 X.
        assertThat(s.players[3].committedThisStreet).isEqualTo(0L)
        assertThat(s.players[3].chips).isEqualTo(0L)
        // P3 는 startHand 가 folded=true 로 마킹 → 다음 핸드 영구 제외.
        assertThat(s.players[3].folded).isTrue()
    }

    @Test fun bust_seat_excluded_from_dealing() {
        // chips==0 좌석은 카드 안 받음 (holeCards 비어있음).
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 10_000L, 0L),
            prevBtnSeat = 0,
            rng = deterministicRng(102L),
            handIndex = 2L,
            startingVersion = 0L,
        )
        // 3명만 카드 분배 (각 2장 = 6장).
        assertThat(s.players[0].holeCards).hasSize(2)
        assertThat(s.players[1].holeCards).hasSize(2)
        assertThat(s.players[2].holeCards).hasSize(2)
        assertThat(s.players[3].holeCards).isEmpty()
        // deckCursor 는 6 (3명 × 2장).
        assertThat(s.deckCursor).isEqualTo(6)
        // 분배된 카드는 모두 distinct.
        val all = s.players.flatMap { it.holeCards }
        assertThat(all).hasSize(6)
        assertThat(all.toSet()).hasSize(6)
    }

    @Test fun bust_seat_excluded_from_action_order() {
        // 4명 (P3 파산 상태) → 3-handed 진행. preflop 액션 순서: BTN=1 → SB=2 → BB=0 (wrap).
        // 단, 3-handed 은 UTG = BB+1 = next(BB=0) = 1 = BTN → toAct=1.
        val cfg = configBase.copy(seats = 4)
        val rng = deterministicRng(103L)
        var s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 10_000L, 0L),
            prevBtnSeat = 0,
            rng = rng,
            handIndex = 2L,
            startingVersion = 0L,
        )
        // 3-max preflop first to act = BTN (BB+1 wrap).
        assertThat(s.toActSeat).isEqualTo(1)   // BTN

        // BTN(1) call → 다음 액션은 P3 가 아니라 SB=2.
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng)
        assertThat(s.toActSeat).isEqualTo(2)

        // SB(2) call → BB=0.
        s = HoldemReducer.act(s, 2, Action(ActionType.CALL), rng)
        assertThat(s.toActSeat).isEqualTo(0)

        // BB(0) check → flop. P3 는 어떤 시점에도 toAct 가 되지 않음.
        s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng)
        assertThat(s.street).isEqualTo(Street.FLOP)

        // postflop: SB(2) 가 first to act (3-handed).
        assertThat(s.toActSeat).isEqualTo(2)
    }

    // ========================================================== Multi-stage collapses

    @Test fun four_handed_one_bust_collapses_to_three() {
        // 4명 → 1명 파산 → 다음 핸드 정상 3-handed 진행.
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 10_000L, 0L),
            prevBtnSeat = 0,
            rng = deterministicRng(201L),
            handIndex = 2L,
            startingVersion = 0L,
        )
        // 살아있는 활성 좌석 수 = 3.
        val live = s.players.count { !it.folded }
        assertThat(live).isEqualTo(3)
        // 3-handed blind 분배: SB=2, BB=0, BTN=1.
        assertThat(s.btnSeat).isEqualTo(1)
        // 3-handed 분기 (heads-up 분기 X).
        // betToCall = BB.
        assertThat(s.betToCall).isEqualTo(50L)
        // 헤즈업이라면 toAct = BTN. 3-handed 에서는 toAct = BB+1 wrap = BTN (이 시나리오에선 우연히 같다).
        // 차이 검증: 3-handed 의 dealOrder 는 [SB, BB, BTN] 형식 (heads-up 은 [non-btn, btn] 형식).
        // P0(BB)=deck[1], P3=card 없음 으로 식별 가능.
        assertThat(s.players[2].holeCards).hasSize(2)   // SB
        assertThat(s.players[0].holeCards).hasSize(2)   // BB
        assertThat(s.players[1].holeCards).hasSize(2)   // BTN
        assertThat(s.players[3].holeCards).isEmpty()    // bust
    }

    @Test fun three_handed_one_bust_collapses_to_heads_up() {
        // 3명 → 1명 파산 → 다음 핸드 헤즈업 분기 자동 전환.
        // P2 chips=0 (파산 상태). P0, P1 만 live.
        val cfg = configBase.copy(seats = 3)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 0L),
            prevBtnSeat = 1,   // 이전 BTN=1 → 다음 BTN: live={0,1} 중 prev=1 보다 큰 좌석 없음 → wrap → 0.
            rng = deterministicRng(202L),
            handIndex = 2L,
            startingVersion = 0L,
        )
        // live size = 2 → heads-up 분기.
        val liveCount = s.players.count { !it.folded }
        assertThat(liveCount).isEqualTo(2)
        // BTN=0 (wrap). heads-up: BTN=SB, 상대=BB.
        assertThat(s.btnSeat).isEqualTo(0)
        assertThat(s.players[0].committedThisStreet).isEqualTo(25L)   // SB(=BTN)
        assertThat(s.players[1].committedThisStreet).isEqualTo(50L)   // BB
        assertThat(s.players[2].committedThisStreet).isEqualTo(0L)    // bust
        // heads-up preflop first to act = BTN(=SB).
        assertThat(s.toActSeat).isEqualTo(0)
        // P2 는 카드 없음, folded=true (영구 제외).
        assertThat(s.players[2].holeCards).isEmpty()
        assertThat(s.players[2].folded).isTrue()
    }

    @Test fun bust_seat_skipped_in_btn_rotation() {
        // 4명, prev BTN=2 (지금 파산 상태). 다음 BTN 은 P2 를 건너뛰어 next live 시트.
        val cfg = configBase.copy(seats = 4)
        val s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 0L, 10_000L),
            prevBtnSeat = 2,   // P2 가 파산했지만 prev BTN 이었음.
            rng = deterministicRng(301L),
            handIndex = 3L,
            startingVersion = 0L,
        )
        // live = {0,1,3}. next > 2 = 3. → BTN=3.
        assertThat(s.btnSeat).isEqualTo(3)
        // 3-handed: SB=next(3)=0 (wrap), BB=next(0)=1.
        assertThat(s.players[3].committedThisStreet).isEqualTo(0L)
        assertThat(s.players[0].committedThisStreet).isEqualTo(25L)
        assertThat(s.players[1].committedThisStreet).isEqualTo(50L)
        assertThat(s.players[2].committedThisStreet).isEqualTo(0L)
        assertThat(s.players[2].folded).isTrue()
    }

    // ========================================================== End-to-end: bust mid-hand → next hand collapses

    @Test fun mid_hand_bust_then_next_hand_excludes_bust_seat() {
        // 3명, P2 단스택 → all-in 패배로 파산 → 다음 startHand 에서 자동 제외.
        // 컨트롤러 시나리오 재현: startHand → act ... → showdown → ackShowdown → 새 startHand.
        val cfg = configBase.copy(seats = 3)
        val rng1 = deterministicRng(401L)
        var s = HoldemReducer.startHand(
            config = cfg,
            players = playersChips(10_000L, 10_000L, 70L),
            prevBtnSeat = null,
            rng = rng1,
            handIndex = 1L,
            startingVersion = 0L,
        )
        // BTN=0, SB=1, BB=2. P2 chips 70 → BB 50 + 20 chips left.
        // BTN(0) raise to 1000, SB(1) call, BB(2) all-in (70 → call semantics).
        s = HoldemReducer.act(s, 0, Action(ActionType.RAISE, 1000L), rng1)
        s = HoldemReducer.act(s, 1, Action(ActionType.CALL), rng1)
        s = HoldemReducer.act(s, 2, Action(ActionType.ALL_IN), rng1)
        // BTN(0), SB(1) 둘만 활성 → flop check-down 까지 가서 showdown.
        // postflop check-down (P0, P1 만 액션, P2 는 all-in 제외).
        repeat(3) {
            s = HoldemReducer.act(s, 1, Action(ActionType.CHECK), rng1)
            s = HoldemReducer.act(s, 0, Action(ActionType.CHECK), rng1)
        }
        assertThat(s.pendingShowdown).isNotNull()
        // ackShowdown → 다음 핸드.
        val acked = HoldemReducer.ackShowdown(s)

        // P2 가 main pot 우승해서 살아남았을 수도 있고 패배해서 chips=0 일 수도 있음.
        // 시나리오 검증을 위해 P2 chips=0 강제.
        val playersAfter = acked.players.map { p ->
            if (p.seat == 2) p.copy(chips = 0L) else p
        }

        // 다음 핸드 시작 → P2 자동 제외.
        val rng2 = deterministicRng(402L)
        val next = HoldemReducer.startHand(
            config = cfg,
            players = playersAfter,
            prevBtnSeat = acked.btnSeat,
            rng = rng2,
            handIndex = 2L,
            startingVersion = acked.stateVersion,
        )
        // live = {0,1} → heads-up.
        assertThat(next.players.count { !it.folded }).isEqualTo(2)
        assertThat(next.players[2].folded).isTrue()
        assertThat(next.players[2].holeCards).isEmpty()
        assertThat(next.players[2].committedThisStreet).isEqualTo(0L)
        // heads-up 분기 — toAct = BTN.
        assertThat(next.toActSeat).isEqualTo(next.btnSeat)
    }

    // ========================================================== Fold ≠ Bust separation

    @Test fun fold_resets_next_hand_but_bust_persists() {
        // P1 가 chips>0 인데 folded=true 인 상태로 startHand 호출 시 → 다음 핸드 정상 부활.
        // P2 는 chips=0 이면 영구 folded.
        val cfg = configBase.copy(seats = 3)
        val initial = listOf(
            PlayerState(0, "P0", isHuman = true, chips = 10_000L),
            // P1: 이전 핸드에서 폴드 후 끝났다고 가정 (folded=true 지만 chips 남음).
            PlayerState(1, "P1", isHuman = false, personaId = "PRO", chips = 5_000L, folded = true),
            // P2: 파산.
            PlayerState(2, "P2", isHuman = false, personaId = "PRO", chips = 0L, folded = true),
        )
        val s = HoldemReducer.startHand(
            config = cfg,
            players = initial,
            prevBtnSeat = 0,
            rng = deterministicRng(501L),
            handIndex = 2L,
            startingVersion = 0L,
        )
        // P1 은 chips>0 → 다음 핸드 부활 (folded=false).
        assertThat(s.players[1].folded).isFalse()
        assertThat(s.players[1].holeCards).hasSize(2)
        // P2 는 chips==0 → 영구 folded.
        assertThat(s.players[2].folded).isTrue()
        assertThat(s.players[2].holeCards).isEmpty()
        // live=2 (P0, P1) → heads-up.
        assertThat(s.players.count { !it.folded }).isEqualTo(2)
    }
}
