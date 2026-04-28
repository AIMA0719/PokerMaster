package com.infocar.pokermaster.feature.table

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Declaration
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.ShowdownHandInfo
import com.infocar.pokermaster.core.model.ShowdownSummary
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import org.junit.Test

class TableUiMapperModeTest {

    @Test
    fun holdemActionBarDoesNotExposeStudOnlyControls() {
        val state = state(
            mode = GameMode.HOLDEM_NL,
            street = Street.PREFLOP,
            players = listOf(
                player(0, isHuman = true, chips = 9_975L, committedStreet = 25L),
                player(1, chips = 9_950L, committedStreet = 50L),
            ),
            toActSeat = 0,
            betToCall = 50L,
            minRaise = 100L,
        )

        val actionBar = TableUiMapper.mapActionBar(state, humanSeat = 0)

        assertThat(actionBar).isNotNull()
        assertThat(actionBar!!.canCall).isTrue()
        assertThat(actionBar.callAmount).isEqualTo(25L)
        assertThat(actionBar.canCheck).isFalse()
        assertThat(actionBar.canRaise).isTrue()
        assertThat(actionBar.actionsEnabled).isTrue()
        assertThat(actionBar.canSaveLife).isFalse()
        assertThat(actionBar.isDeclarePhase).isFalse()
    }

    @Test
    fun opponentTurnStillKeepsHumanActionBarVisibleButDisabled() {
        val state = state(
            mode = GameMode.HOLDEM_NL,
            street = Street.FLOP,
            players = listOf(
                player(0, isHuman = true, chips = 9_950L),
                player(1, chips = 9_950L),
            ),
            toActSeat = 1,
            betToCall = 0L,
            minRaise = 50L,
        )

        val actionBar = TableUiMapper.mapActionBar(state, humanSeat = 0)

        assertThat(actionBar).isNotNull()
        assertThat(actionBar!!.actionsEnabled).isFalse()
        assertThat(actionBar.canCheck).isTrue()
        assertThat(actionBar.canCall).isFalse()
        assertThat(actionBar.canRaise).isTrue()
    }

    @Test
    fun sevenStudCallSpotExposesSaveLifeButNotDeclare() {
        val state = state(
            mode = GameMode.SEVEN_STUD,
            street = Street.THIRD,
            players = listOf(
                player(0, isHuman = true, chips = 9_990L, committedHand = 10L),
                player(1, chips = 9_965L, committedHand = 35L, committedStreet = 25L),
            ),
            toActSeat = 0,
            betToCall = 25L,
            minRaise = 50L,
            lastAggressorSeat = 1,
        )

        val actionBar = TableUiMapper.mapActionBar(state, humanSeat = 0)

        assertThat(actionBar).isNotNull()
        assertThat(actionBar!!.canCall).isTrue()
        assertThat(actionBar.callAmount).isEqualTo(25L)
        assertThat(actionBar.actionsEnabled).isTrue()
        assertThat(actionBar.canSaveLife).isTrue()
        assertThat(actionBar.isDeclarePhase).isFalse()
    }

    @Test
    fun hiLoDeclareTurnMapsToDeclarePhaseEvenWhenHumanIsAllIn() {
        val state = state(
            mode = GameMode.SEVEN_STUD_HI_LO,
            street = Street.DECLARE,
            players = listOf(
                player(0, isHuman = true, chips = 0L, allIn = true),
                player(1, chips = 9_000L),
            ),
            toActSeat = 0,
            declarations = mapOf(1 to Declaration.HIGH),
        )

        val actionBar = TableUiMapper.mapActionBar(state, humanSeat = 0)

        assertThat(actionBar).isNotNull()
        assertThat(actionBar!!.isDeclarePhase).isTrue()
        assertThat(actionBar.canRaise).isFalse()
        assertThat(actionBar.canSaveLife).isFalse()
    }

    @Test
    fun hiLoHandEndKeepsDeclarationsAndScoopPotForResultUi() {
        val summary = ShowdownSummary(
            bestHands = mapOf(0 to ShowdownHandInfo(0, "스트레이트", bestFive())),
            payouts = mapOf(0 to 1_000L),
            pots = listOf(
                PotSummary(
                    amount = 1_000L,
                    eligibleSeats = setOf(0, 1),
                    winnerSeats = setOf(0),
                    index = 0,
                    hiWinnerSeats = setOf(0),
                    loWinnerSeats = setOf(0),
                    scoopWinnerSeats = setOf(0),
                )
            ),
            uncalledReturn = emptyMap(),
            deadMoney = 0L,
            rngServerSeedHex = "",
            rngClientSeedHex = "",
        )
        val state = state(
            mode = GameMode.SEVEN_STUD_HI_LO,
            street = Street.SHOWDOWN,
            players = listOf(player(0, isHuman = true), player(1)),
            toActSeat = null,
            pendingShowdown = summary,
            declarations = mapOf(0 to Declaration.SWING, 1 to Declaration.HIGH),
        )

        val handEnd = TableUiMapper.mapHandEnd(state)

        assertThat(handEnd).isNotNull()
        assertThat(handEnd!!.declarationsBySeat).containsExactly(
            0, Declaration.SWING,
            1, Declaration.HIGH,
        )
        assertThat(handEnd.pots.first().scoopWinnerSeats).containsExactly(0)
    }

    @Test
    fun nonHiLoHandEndDoesNotExposeDeclarations() {
        val summary = ShowdownSummary(
            bestHands = mapOf(0 to ShowdownHandInfo(0, "페어", bestFive())),
            payouts = mapOf(0 to 1_000L),
            pots = listOf(PotSummary(1_000L, setOf(0, 1), setOf(0), index = 0)),
            uncalledReturn = emptyMap(),
            deadMoney = 0L,
            rngServerSeedHex = "",
            rngClientSeedHex = "",
        )
        val state = state(
            mode = GameMode.SEVEN_STUD,
            street = Street.SHOWDOWN,
            players = listOf(player(0, isHuman = true), player(1)),
            toActSeat = null,
            pendingShowdown = summary,
            declarations = mapOf(0 to Declaration.SWING),
        )

        val handEnd = TableUiMapper.mapHandEnd(state)

        assertThat(handEnd).isNotNull()
        assertThat(handEnd!!.declarationsBySeat).isEmpty()
    }

    private fun state(
        mode: GameMode,
        street: Street,
        players: List<PlayerState>,
        toActSeat: Int?,
        betToCall: Long = 0L,
        minRaise: Long = 0L,
        lastAggressorSeat: Int? = null,
        pendingShowdown: ShowdownSummary? = null,
        declarations: Map<Int, Declaration> = emptyMap(),
    ): GameState = GameState(
        mode = mode,
        config = when (mode) {
            GameMode.HOLDEM_NL -> TableConfig(mode = mode, seats = players.size)
            GameMode.SEVEN_STUD, GameMode.SEVEN_STUD_HI_LO -> TableConfig(
                mode = mode,
                seats = players.size,
                smallBlind = 0L,
                bigBlind = 0L,
                ante = 10L,
                bringIn = 25L,
            )
        },
        stateVersion = 1L,
        handIndex = 1L,
        players = players,
        btnSeat = 0,
        toActSeat = toActSeat,
        street = street,
        betToCall = betToCall,
        minRaise = minRaise,
        lastAggressorSeat = lastAggressorSeat,
        pendingShowdown = pendingShowdown,
        declarations = declarations,
    )

    private fun player(
        seat: Int,
        isHuman: Boolean = false,
        chips: Long = 10_000L,
        committedHand: Long = 0L,
        committedStreet: Long = 0L,
        allIn: Boolean = false,
    ): PlayerState = PlayerState(
        seat = seat,
        nickname = "P$seat",
        isHuman = isHuman,
        personaId = if (isHuman) null else "PRO",
        chips = chips,
        committedThisHand = committedHand,
        committedThisStreet = committedStreet,
        allIn = allIn,
    )

    private fun bestFive(): List<Card> = listOf(
        Card(Suit.SPADE, Rank.ACE),
        Card(Suit.HEART, Rank.KING),
        Card(Suit.DIAMOND, Rank.QUEEN),
        Card(Suit.CLUB, Rank.JACK),
        Card(Suit.SPADE, Rank.TEN),
    )
}
