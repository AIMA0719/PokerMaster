package com.infocar.pokermaster.engine.controller.llm

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.engine.decision.ActionCandidate
import com.infocar.pokermaster.engine.decision.DecisionResult
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona
import org.junit.jupiter.api.Test

class PromptFormatterTest {

    @Test
    fun `includes hero cards pot and equity in JSON`() {
        val ctx = GameContext(
            mode = GameMode.HOLDEM_NL,
            seat = 3,
            opponentSeats = listOf(0, 1, 2, 4),
            hole = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.SPADE, Rank.KING)),
            community = listOf(
                Card(Suit.HEART, Rank.TWO),
                Card(Suit.CLUB, Rank.SEVEN),
                Card(Suit.DIAMOND, Rank.JACK),
            ),
            pot = 300,
            betToCall = 100,
            minRaise = 200,
            myStack = 5000,
            effectiveStack = 4000,
            numActiveOpponents = 2,
        )
        val result = DecisionResult(
            candidates = listOf(
                ActionCandidate(ActionType.FOLD, amount = 0, ev = -0.5, equity = 0.65),
                ActionCandidate(ActionType.CALL, amount = 100, ev = 120.0, equity = 0.65),
                ActionCandidate(ActionType.RAISE, amount = 400, ev = 180.0, equity = 0.65),
            ),
            equity = 0.65,
            potOdds = 0.25,
            effectiveStack = 4000,
            spr = 13.33,
        )

        val json = PromptFormatter.formatContext(ctx, result, Persona.PRO)

        assertThat(json).contains("\"mode\":\"HOLDEM_NL\"")
        assertThat(json).contains("\"seat\":3")
        assertThat(json).contains("\"persona\":\"PRO\"")
        // 포커 표준 표기 "As" (ace of spades), "Ks" (king of spades) — ASCII.
        assertThat(json).contains("\"hole\":[\"As\",\"Ks\"]")
        assertThat(json).contains("\"community\":[\"2h\",\"7c\",\"Jd\"]")
        assertThat(json).contains("\"pot\":300")
        assertThat(json).contains("\"betToCall\":100")
        assertThat(json).contains("\"equity\":0.65")
        assertThat(json).contains("\"potOdds\":0.25")
        assertThat(json).contains("\"candidates\":")
        assertThat(json).contains("\"ev\":180.0")
    }

    @Test
    fun `omits persona when null`() {
        val ctx = GameContext(
            mode = GameMode.HOLDEM_NL,
            seat = 0,
            opponentSeats = listOf(1),
            hole = listOf(Card(Suit.CLUB, Rank.TWO), Card(Suit.CLUB, Rank.THREE)),
            pot = 100,
            betToCall = 0,
            minRaise = 100,
            myStack = 1000,
            effectiveStack = 1000,
            numActiveOpponents = 1,
        )
        val result = DecisionResult(
            candidates = listOf(ActionCandidate(ActionType.CHECK, ev = 0.0)),
            equity = 0.1,
            potOdds = 0.0,
            effectiveStack = 1000,
            spr = 10.0,
        )

        val json = PromptFormatter.formatContext(ctx, result, persona = null)

        assertThat(json).contains("\"persona\":null")
    }

    @Test
    fun `does not leak opponent hole cards field`() {
        val ctx = GameContext(
            mode = GameMode.HOLDEM_NL,
            seat = 0,
            opponentSeats = listOf(1),
            hole = listOf(Card(Suit.HEART, Rank.ACE), Card(Suit.SPADE, Rank.ACE)),
            pot = 200,
            betToCall = 0,
            minRaise = 100,
            myStack = 1000,
            effectiveStack = 1000,
            numActiveOpponents = 1,
        )
        val result = DecisionResult(
            candidates = emptyList(),
            equity = 0.85,
            potOdds = 0.0,
            effectiveStack = 1000,
            spr = 5.0,
        )

        val json = PromptFormatter.formatContext(ctx, result, null)

        // opponent hole 키가 존재해서는 안 된다 (GameContext 에도 없지만 회귀 방지).
        assertThat(json).doesNotContain("opponentHole")
        assertThat(json).doesNotContain("opponent_cards")
    }
}
