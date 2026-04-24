package com.infocar.pokermaster.engine.controller.llm

import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.engine.decision.DecisionResult
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * [GameContext] + [DecisionResult] + [Persona] 를 LLM 에게 넘길 JSON prompt 로 포맷.
 *
 * 상대 홀카드는 절대 포함하지 않는다 (누출 금지). 출력은 결정에 필요한 요약만 — cards/pot/
 * equity/pot_odds/position/candidates — 짧고 예측 가능한 스키마.
 *
 * Phase5-II 에서 system-prompt (페르소나 지시) 와 결합해 최종 LLM 입력을 만든다.
 */
object PromptFormatter {

    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    fun formatContext(ctx: GameContext, result: DecisionResult, persona: Persona?): String {
        val payload = ContextPayload(
            mode = ctx.mode.name,
            seat = ctx.seat,
            persona = persona?.name,
            hole = ctx.hole.map { it.short() },
            upCards = ctx.upCards.map { it.short() },
            community = ctx.community.map { it.short() },
            pot = ctx.pot,
            betToCall = ctx.betToCall,
            minRaise = ctx.minRaise,
            myStack = ctx.myStack,
            effectiveStack = ctx.effectiveStack,
            numActiveOpponents = ctx.numActiveOpponents,
            equity = round3(result.equity),
            potOdds = round3(result.potOdds),
            spr = round3(result.spr),
            candidates = result.candidates.map {
                CandidatePayload(
                    action = it.action.name,
                    amount = it.amount,
                    ev = round3(it.ev),
                    equity = round3(it.equity),
                    potOdds = round3(it.potOdds),
                )
            },
        )
        return json.encodeToString(ContextPayload.serializer(), payload)
    }

    /** 포커 표준 표기 "As", "Kh", "2d", "Tc" — ASCII 전용으로 tokenizer 안정성 확보. */
    private fun Card.short(): String = "${rank.short}${suit.letter()}"

    private fun Suit.letter(): String = when (this) {
        Suit.SPADE -> "s"
        Suit.HEART -> "h"
        Suit.DIAMOND -> "d"
        Suit.CLUB -> "c"
    }

    private fun round3(v: Double): Double =
        if (v.isFinite()) Math.round(v * 1000.0) / 1000.0 else 0.0

    @Serializable
    private data class ContextPayload(
        val mode: String,
        val seat: Int,
        val persona: String?,
        val hole: List<String>,
        val upCards: List<String>,
        val community: List<String>,
        val pot: Long,
        val betToCall: Long,
        val minRaise: Long,
        val myStack: Long,
        val effectiveStack: Long,
        val numActiveOpponents: Int,
        val equity: Double,
        val potOdds: Double,
        val spr: Double,
        val candidates: List<CandidatePayload>,
    )

    @Serializable
    private data class CandidatePayload(
        val action: String,
        val amount: Long,
        val ev: Double,
        val equity: Double,
        val potOdds: Double,
    )

    /** GameMode 참조 확보용 — 컴파일 단계에서 core:model 쪽 import 를 미사용 경고 없이 유지. */
    @Suppress("unused")
    private val allModes: Array<GameMode> = GameMode.values()
}
