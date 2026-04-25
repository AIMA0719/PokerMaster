package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import org.junit.jupiter.api.Test

class StudOpenerTest {

    private val cfg = TableConfig(
        mode = GameMode.SEVEN_STUD,
        seats = 2,
        smallBlind = 0L,
        bigBlind = 0L,
        ante = 10L,
        bringIn = 25L,
    )

    private fun state(
        mode: GameMode = GameMode.SEVEN_STUD,
        street: Street = Street.THIRD,
        myHole: List<Card>,
        myUp: List<Card>,
        chips: Long = 10_000L,
        committedThisStreet: Long = 0L,
        betToCall: Long = 25L,
    ): GameState = GameState(
        mode = mode,
        config = cfg.copy(mode = mode),
        stateVersion = 1L,
        handIndex = 1L,
        players = listOf(
            PlayerState(
                seat = 0, nickname = "me", isHuman = false, personaId = "PRO",
                chips = chips, holeCards = myHole, upCards = myUp,
                committedThisStreet = committedThisStreet,
            ),
            PlayerState(
                seat = 1, nickname = "opp", isHuman = false, personaId = "PRO",
                chips = 10_000L,
            ),
        ),
        btnSeat = 0, toActSeat = 0, street = street,
        betToCall = betToCall, minRaise = betToCall * 2L,
    )

    @Test fun rolled_up_trips_on_third_street_returns_raise_action() {
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.ACE)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.ACE)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, seat = 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isAnyOf(ActionType.RAISE, ActionType.ALL_IN)
        assertThat(act.amount).isGreaterThan(s.betToCall)
    }

    @Test fun pair_against_cheap_call_returns_call() {
        // 페어(KK + 7) — 봉착 콜 25 ≤ bringIn(25) × 2 = 50 이므로 폴드 방지 CALL.
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.KING), Card(Suit.HEART, Rank.KING)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.SEVEN)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isEqualTo(ActionType.CALL)
    }

    @Test fun pair_against_big_raise_defers_to_equity() {
        // 페어 + 콜 봉착 100 > 50 → 폴드 방지선 밖, equity 결정에 위임 (null).
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.KING), Card(Suit.HEART, Rank.KING)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.SEVEN)),
            betToCall = 100L,
        )
        assertThat(StudOpener.overrideOnThirdStreet(s, 0)).isNull()
    }

    @Test fun three_flush_against_cheap_call_returns_call() {
        // 3-flush (♠ A K Q) — 폴드 방지 CALL.
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.SPADE, Rank.KING)),
            myUp = listOf(Card(Suit.SPADE, Rank.QUEEN)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isEqualTo(ActionType.CALL)
    }

    @Test fun three_straight_against_cheap_call_returns_call() {
        // 3-straight (5,6,7) — 폴드 방지 CALL.
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.FIVE), Card(Suit.HEART, Rank.SIX)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.SEVEN)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isEqualTo(ActionType.CALL)
    }

    @Test fun wheel_three_straight_a_2_3_returns_call() {
        // wheel 형 3-straight (A,2,3) — 폴드 방지 CALL.
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.TWO)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.THREE)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isEqualTo(ActionType.CALL)
    }

    @Test fun three_distinct_unconnected_unsuited_returns_null() {
        // None 티어 — equity 결정에 위임 (null).
        val s = state(
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.SEVEN)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.TWO)),
        )
        assertThat(StudOpener.overrideOnThirdStreet(s, 0)).isNull()
    }

    @Test fun fourth_street_returns_null() {
        val s = state(
            street = Street.FOURTH,
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.ACE)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.ACE), Card(Suit.CLUB, Rank.SEVEN)),
        )
        assertThat(StudOpener.overrideOnThirdStreet(s, 0)).isNull()
    }

    @Test fun holdem_mode_returns_null() {
        val s = state(
            mode = GameMode.HOLDEM_NL,
            myHole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.ACE)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.ACE)),
        )
        assertThat(StudOpener.overrideOnThirdStreet(s, 0)).isNull()
    }

    @Test fun rolled_up_in_hilo_mode_also_returns_raise() {
        val s = state(
            mode = GameMode.SEVEN_STUD_HI_LO,
            myHole = listOf(Card(Suit.SPADE, Rank.SEVEN), Card(Suit.HEART, Rank.SEVEN)),
            myUp = listOf(Card(Suit.DIAMOND, Rank.SEVEN)),
        )
        val act = StudOpener.overrideOnThirdStreet(s, 0)
        assertThat(act).isNotNull()
        assertThat(act!!.type).isAnyOf(ActionType.RAISE, ActionType.ALL_IN)
    }
}
