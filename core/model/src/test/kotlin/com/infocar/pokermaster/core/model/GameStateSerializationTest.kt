package com.infocar.pokermaster.core.model

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.Json
import org.junit.Test

/**
 * Resume 스냅샷(v1.1 §1.2.D)의 기본 전제 — [GameState] JSON round-trip 동일성.
 * ResumeRepository 는 이 직렬화 위에 SharedPreferences 저장만 얹는다.
 */
class GameStateSerializationTest {

    private val json = Json { encodeDefaults = true }

    @Test
    fun `mid-hand state round-trips losslessly`() {
        val state = sampleMidHandState()
        val encoded = json.encodeToString(GameState.serializer(), state)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)
        assertThat(decoded).isEqualTo(state)
    }

    @Test
    fun `pending showdown state round-trips losslessly`() {
        val state = sampleMidHandState().copy(
            street = Street.SHOWDOWN,
            toActSeat = null,
            pendingShowdown = ShowdownSummary(
                bestHands = mapOf(
                    0 to ShowdownHandInfo(0, "원페어", listOf(Card(Suit.SPADE, Rank.ACE))),
                ),
                payouts = mapOf(0 to 300L),
                pots = listOf(PotSummary(300L, setOf(0, 1), setOf(0), index = 0)),
                uncalledReturn = emptyMap(),
                deadMoney = 0L,
                rngServerSeedHex = "abcd",
                rngClientSeedHex = "1234",
            ),
        )
        val encoded = json.encodeToString(GameState.serializer(), state)
        val decoded = json.decodeFromString(GameState.serializer(), encoded)
        assertThat(decoded).isEqualTo(state)
        assertThat(decoded.pendingShowdown).isEqualTo(state.pendingShowdown)
    }

    private fun sampleMidHandState(): GameState {
        val config = TableConfig(mode = GameMode.HOLDEM_NL, seats = 2)
        return GameState(
            mode = GameMode.HOLDEM_NL,
            config = config,
            stateVersion = 7L,
            handIndex = 3L,
            players = listOf(
                PlayerState(
                    seat = 0, nickname = "나", isHuman = true, chips = 9_800L,
                    holeCards = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.KING)),
                    committedThisHand = 200L, committedThisStreet = 150L, actedThisStreet = true,
                ),
                PlayerState(
                    seat = 1, nickname = "프로", isHuman = false, personaId = "PRO", chips = 9_850L,
                    holeCards = listOf(Card(Suit.CLUB, Rank.QUEEN), Card(Suit.DIAMOND, Rank.JACK)),
                    committedThisHand = 150L, committedThisStreet = 150L, actedThisStreet = false,
                ),
            ),
            btnSeat = 0,
            toActSeat = 1,
            street = Street.FLOP,
            community = listOf(
                Card(Suit.SPADE, Rank.TEN),
                Card(Suit.HEART, Rank.FIVE),
                Card(Suit.DIAMOND, Rank.TWO),
            ),
            betToCall = 150L,
            minRaise = 300L,
            lastFullRaiseAmount = 150L,
            lastAggressorSeat = 0,
            deckCursor = 9,
            rngCommitHex = "deadbeef",
        )
    }
}
