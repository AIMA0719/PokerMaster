package com.infocar.pokermaster.engine.controller.llm

import com.infocar.pokermaster.engine.llm.GenerationConfig
import com.infocar.pokermaster.engine.llm.LlmSession
import kotlinx.coroutines.withTimeoutOrNull

/**
 * 핸드 종료 후 한 줄 코칭 — sprint C2 Phase 3.
 *
 * **폴백 계약**: 반환 null 은 "LLM 코칭 사용 불가" 신호. 호출자는 정적 폴백
 * (예: feature.history.coaching.CoachingTip) 으로 대체.
 *
 * null 원인: LlmSession 미초기화 / 모델 미로드 / 미지원 단말 / timeout / generate 예외.
 *
 * record→prompt 변환은 호출자 책임 — HandHistoryRecord 가 :core:data 라 engine 모듈
 * 의존 방향 위배 회피.
 */
interface LlmCoach {
    /** 자유 텍스트 prompt 입력 → 80자 이내 한 줄 응답. 실패/미지원 시 null. */
    suspend fun review(prompt: String): String?
}

/**
 * LLM 기반 구현. [LlmSession] 의 현재 모델 핸들로 [LlmEngine.generate] 호출 (free-text).
 * 출력은 prompt 외 토큰만 — trim + 첫 비어있지 않은 줄 + take(80) 으로 단일 문장 강제.
 *
 * GBNF JSON 강제 안 함 — 한국어 utf-8 byte 매칭이 까다롭고, 80자 단순 텍스트면 충분.
 * 모델이 메타 텍스트 ("답:", "Tip:") 출력해도 그대로 노출 — 사용자가 인지 가능한 1차 가치.
 * 정제는 후속 sprint.
 */
class LlmCoachImpl(
    private val session: LlmSession,
    private val config: GenerationConfig = DEFAULT_CONFIG,
    private val timeoutMs: Long = DEFAULT_TIMEOUT_MS,
) : LlmCoach {

    override suspend fun review(prompt: String): String? = runCatching {
        val engine = session.engineIfReady() ?: return@runCatching null
        val handle = session.currentModel() ?: return@runCatching null
        withTimeoutOrNull(timeoutMs) {
            val tokens = engine.tokenize(handle, prompt)
            val output = engine.generate(handle, tokens, config)
            engine.detokenize(handle, output)
                .trim()
                .lineSequence()
                .firstOrNull { it.isNotBlank() }
                ?.take(80)
        }
    }.getOrNull()

    companion object {
        /** 코칭은 짧은 plain text — 80 토큰이면 한국어 ~30자, 영어 ~60단어 cover. */
        val DEFAULT_CONFIG: GenerationConfig = GenerationConfig(
            maxNewTokens = 80,
            temperature = 0.5f,    // NPC 결정보다 약간 자유롭게 — 다양한 표현
            topK = 40,
            topP = 0.9f,
            seed = -1L,
        )

        /** 800ms — 사용자가 핸드 상세 진입 후 카드 등장하기 전 응답 기대치. */
        const val DEFAULT_TIMEOUT_MS: Long = 800L
    }
}
