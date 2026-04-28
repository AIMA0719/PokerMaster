package com.infocar.pokermaster.engine.controller.llm

import com.infocar.pokermaster.engine.decision.DecisionResult
import com.infocar.pokermaster.engine.decision.GameContext
import com.infocar.pokermaster.engine.decision.Persona
import com.infocar.pokermaster.engine.llm.GenerationConfig
import com.infocar.pokermaster.engine.llm.LlmSession

/**
 * LLM 기반 포커 Advisor — Phase5-I. 결정형 코어 출력 [DecisionResult] 를 LLM 에 "검토" 시켜
 * 최종 action 추천을 받는다. Phase5-II 에서 AiDriver 가 이 결과를 legalize 후 적용.
 *
 * **폴백 계약**: 반환 null 은 "LLM 사용 불가" 신호. 호출자 (AiDriver) 는 기존 Persona-bias 계산
 * 으로 즉시 대체해야 한다. null 원인:
 *  - LlmSession 이 Ready 상태가 아님 (backend 미초기화 / 모델 미로드)
 *  - generate 자체가 예외를 던짐 (OOM, cancel, 기타)
 *  - LLM 응답이 스키마를 벗어남 ([LlmDecision.parse] 실패)
 */
interface LlmAdvisor {
    suspend fun suggest(ctx: GameContext, result: DecisionResult, persona: Persona?): LlmDecision?
}

/**
 * 실 구현. [LlmSession] 에서 현재 로드된 모델 핸들을 꺼내 `generateJson` 을 호출한다.
 *
 * **Hot-path 제약**: 포커 테이블의 NPC 턴은 200~800ms 지연 이내여야 UX 매끄러움. 현재는
 * timeout/deadline 설정 없음 — Phase5-II 에서 coroutine withTimeout 로 감싸 폴백 유도.
 */
class LlmAdvisorImpl(
    private val session: LlmSession,
    private val config: GenerationConfig = DEFAULT_CONFIG,
) : LlmAdvisor {

    override suspend fun suggest(
        ctx: GameContext,
        result: DecisionResult,
        persona: Persona?,
    ): LlmDecision? = runCatching {
        // Ready 이고 모델 로드된 상태에서만 LLM 호출. 나머지는 null 로 폴백 유도.
        // 폴백 계약(상단 KDoc): 어떤 예외든 null 로 흡수해 호출자(AiDriver) 가
        // Persona-bias 경로로 즉시 대체 가능해야 한다. session/PromptFormatter/parse
        // 어디서 throw 해도 동일.
        val engine = session.engineIfReady() ?: return@runCatching null
        val handle = session.currentModel() ?: return@runCatching null
        val prompt = PromptFormatter.formatContext(ctx, result, persona)
        engine.generateJson(
            handle = handle,
            prompt = prompt,
            grammar = DecisionGrammar.DECISION,
            config = config,
        ).let(LlmDecision::parse)
    }.getOrNull()

    companion object {
        /** 포커 결정용 기본 샘플링 — 낮은 temperature 로 안정적 출력 우선. */
        val DEFAULT_CONFIG: GenerationConfig = GenerationConfig(
            maxNewTokens = 160,   // JSON 한 덩어리면 충분
            temperature = 0.3f,   // 안정성 우선
            topK = 30,
            topP = 0.85f,
            seed = -1L,
        )
    }
}

