package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import org.junit.jupiter.api.Test

class DecisionCoreTest {

    private val core = DecisionCore(
        equityCalculator = EquityCalculator(seed = 42L),
        iterations = 800,
    )

    private fun ctxHoldemFlop(
        hole: List<com.infocar.pokermaster.core.model.Card> = hand("AS", "AH"),
        community: List<com.infocar.pokermaster.core.model.Card> = hand("2D", "9C", "KH"),
        pot: Long = 1000L,
        betToCall: Long = 0L,
        minRaise: Long = 100L,
        myStack: Long = 5000L,
        opponents: Int = 1,
    ) = GameContext(
        mode = GameMode.HOLDEM_NL,
        seat = 0,
        opponentSeats = (1..opponents).toList(),
        hole = hole,
        community = community,
        pot = pot,
        betToCall = betToCall,
        minRaise = minRaise,
        myStack = myStack,
        effectiveStack = myStack,
        numActiveOpponents = opponents,
    )

    @Test fun check_available_when_no_call_cost() {
        val r = core.decide(ctxHoldemFlop(betToCall = 0L))
        assertThat(r.candidates.any { it.action == ActionType.CHECK }).isTrue()
        assertThat(r.candidates.none { it.action == ActionType.FOLD }).isTrue()
    }

    @Test fun fold_and_call_available_when_facing_bet() {
        val r = core.decide(ctxHoldemFlop(betToCall = 200L))
        assertThat(r.candidates.any { it.action == ActionType.FOLD }).isTrue()
        assertThat(r.candidates.any { it.action == ActionType.CALL }).isTrue()
    }

    @Test fun all_in_always_available_in_holdem() {
        val r = core.decide(ctxHoldemFlop())
        assertThat(r.candidates.any { it.action == ActionType.ALL_IN }).isTrue()
    }

    @Test fun candidates_sorted_by_ev_desc() {
        val r = core.decide(ctxHoldemFlop())
        val evs = r.candidates.map { it.ev }
        assertThat(evs).isInOrder(Comparator.reverseOrder<Double>())
    }

    @Test fun strong_hand_prefers_aggressive_action() {
        // AA on dry board → 최고 EV 가 fold/check 가 아닌 raise/all-in 이어야 함
        val r = core.decide(ctxHoldemFlop(
            hole = hand("AS", "AH"),
            community = hand("2D", "9C", "KH"),
            pot = 1000L,
            betToCall = 0L,
        ))
        val top = r.candidates.first()
        assertThat(top.action).isAnyOf(ActionType.RAISE, ActionType.ALL_IN, ActionType.BET)
    }

    @Test fun weak_hand_facing_big_bet_prefers_fold() {
        // 72o on AKK board, 큰 베팅 직면
        val r = core.decide(ctxHoldemFlop(
            hole = hand("7S", "2H"),
            community = hand("AS", "KS", "KH"),
            pot = 200L,
            betToCall = 500L,
            myStack = 5000L,
        ))
        // call EV 가 음수, 추천 1순위는 FOLD 여야
        assertThat(r.candidates.first().action).isEqualTo(ActionType.FOLD)
    }

    @Test fun result_includes_equity_and_pot_odds() {
        val r = core.decide(ctxHoldemFlop(betToCall = 200L))
        assertThat(r.equity).isAtLeast(0.0)
        assertThat(r.equity).isAtMost(1.0)
        assertThat(r.potOdds).isGreaterThan(0.0)
    }

    @Test fun persona_changes_top_recommendation() {
        // 약한 핸드에서 콜 직면. SILENT vs GRANPA 의 추천 순위 다름.
        val ctx = ctxHoldemFlop(
            hole = hand("8S", "5H"),
            community = hand("AC", "KH", "QD"),
            pot = 200L,
            betToCall = 80L,
        )
        val silent = core.decide(ctx, persona = Persona.SILENT)
        val granpa = core.decide(ctx, persona = Persona.GRANPA)
        // granpa 는 FOLD 가 silent 보다 작음 → top 이 다를 가능성 높음
        // 적어도 silent.fold.ev > granpa.fold.ev (편향 차이 보장)
        val silentFoldEv = silent.candidates.first { it.action == ActionType.FOLD }.ev
        val granpaFoldEv = granpa.candidates.first { it.action == ActionType.FOLD }.ev
        assertThat(silentFoldEv).isGreaterThan(granpaFoldEv)
    }

    @Test fun seven_stud_mode_uses_partial_cards() {
        val ctx = GameContext(
            mode = GameMode.SEVEN_STUD,
            seat = 0,
            opponentSeats = listOf(1, 2),
            hole = hand("AS", "AH"),
            upCards = hand("AD", "KS"),         // 4구 시점 트리플
            knownOpponentUpCards = mapOf(
                1 to hand("2C", "5D"),
                2 to hand("7H", "JS"),
            ),
            pot = 600L,
            betToCall = 100L,
            minRaise = 200L,
            myStack = 3000L,
            effectiveStack = 3000L,
            numActiveOpponents = 2,
        )
        val r = core.decide(ctx)
        assertThat(r.equity).isGreaterThan(0.5)   // 트리플 A 는 강함
        assertThat(r.candidates).isNotEmpty()
    }
}
