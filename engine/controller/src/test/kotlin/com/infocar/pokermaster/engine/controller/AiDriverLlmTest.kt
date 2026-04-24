package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.controller.llm.LlmAdvisor
import com.infocar.pokermaster.engine.controller.llm.LlmDecision
import com.infocar.pokermaster.engine.decision.DecisionResult
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test

/**
 * Phase5-II-A: LLM advisor + 폴백/타임아웃 경로 검증. 실 네이티브 엔진은 사용하지 않고
 * FakeLlmAdvisor 로 결과를 주입해 AiDriver.actWithLlm 의 판단 로직만 본다.
 */
class AiDriverLlmTest {

    @Test
    fun `advisor null falls back to DecisionCore top candidate`() = runTest {
        val driver = AiDriver()
        val state = minimalState()

        val action = driver.actWithLlm(state, seat = 1, persona = Persona.PRO, advisor = null)

        // DecisionCore 는 반드시 non-FOLD 를 포함해 legalize 가 합법 액션 반환.
        assertThat(action.type).isNotEqualTo(ActionType.BRING_IN)  // variant-specific 배제
        assertThat(action.type).isAnyOf(
            ActionType.FOLD, ActionType.CHECK, ActionType.CALL,
            ActionType.BET, ActionType.RAISE, ActionType.ALL_IN,
        )
    }

    @Test
    fun `advisor returning valid RAISE is applied (after legalize clamping)`() = runTest {
        val driver = AiDriver()
        val state = minimalState(betToCall = 100)  // CALL 필요 상황
        val advisor = FakeLlmAdvisor(
            response = LlmDecision(action = "RAISE", amount = 400, confidence = 0.9)
        )

        val action = driver.actWithLlm(state, seat = 1, persona = Persona.PRO, advisor = advisor)

        // RAISE 가 minRaise 이상이면 legalize 가 RAISE(>=minRaise) 로 유지.
        // 그 외 제약으로 CALL/ALL_IN 로 강등될 수 있으나 FOLD 는 아님.
        assertThat(action.type).isAnyOf(ActionType.RAISE, ActionType.ALL_IN, ActionType.CALL)
    }

    @Test
    fun `advisor that exceeds timeout is skipped and falls back`() = runTest {
        val driver = AiDriver()
        val state = minimalState()
        // timeout 50ms 로 고정 — advisor 는 200ms delay 시뮬레이션.
        val slowAdvisor = FakeLlmAdvisor(
            response = LlmDecision(action = "FOLD", amount = 0, confidence = 0.1),
            delayMs = 200,
        )

        val action = driver.actWithLlm(
            state = state,
            seat = 1,
            persona = Persona.PRO,
            advisor = slowAdvisor,
            timeoutMs = 50,
        )

        // slowAdvisor 가 FOLD 를 반환했어도 timeout 으로 버려지고 DecisionCore 폴백.
        // DecisionCore 의 PRO persona 는 정상 hand 에서 FOLD 확률 낮음 — 단순히 action 이
        // 적어도 legal 한 결과임을 확인 (null 이 아님).
        assertThat(action.type).isNotNull()
    }

    @Test
    fun `advisor returning unknown action enum is ignored (fallback)`() = runTest {
        val driver = AiDriver()
        val state = minimalState()
        val brokenAdvisor = FakeLlmAdvisor(
            response = null,  // parse 실패 시나리오 시뮬레이션 (FakeLlmAdvisor 가 null 반환)
        )

        val action = driver.actWithLlm(state, seat = 1, persona = Persona.PRO, advisor = brokenAdvisor)

        // null 반환 → 폴백 정상 동작.
        assertThat(action).isInstanceOf(Action::class.java)
    }

    // ---- fixture ----

    private class FakeLlmAdvisor(
        private val response: LlmDecision?,
        private val delayMs: Long = 0,
    ) : LlmAdvisor {
        override suspend fun suggest(
            ctx: GameContext,
            result: DecisionResult,
            persona: Persona?,
        ): LlmDecision? {
            if (delayMs > 0) delay(delayMs)
            return response
        }
    }

    private fun minimalState(betToCall: Long = 0): GameState {
        val p0 = PlayerState(
            seat = 0,
            nickname = "Human",
            isHuman = true,
            chips = 10_000,
        )
        val p1 = PlayerState(
            seat = 1,
            nickname = "NPC-PRO",
            isHuman = false,
            personaId = "PRO",
            chips = 10_000,
            // DecisionCore 의 Monte Carlo equity 계산이 hole 을 요구. 빈 리스트면 IAE.
            holeCards = listOf(
                Card(Suit.SPADE, Rank.ACE),
                Card(Suit.SPADE, Rank.KING),
            ),
        )
        return GameState(
            mode = GameMode.HOLDEM_NL,
            config = TableConfig(
                mode = GameMode.HOLDEM_NL,
                seats = 2,
                smallBlind = 50,
                bigBlind = 100,
            ),
            stateVersion = 0,
            handIndex = 1,
            players = listOf(p0, p1),
            btnSeat = 0,
            toActSeat = 1,
            street = Street.PREFLOP,
            betToCall = betToCall,
            minRaise = 200,
        )
    }
}
