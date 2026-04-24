package com.infocar.pokermaster.engine.llm

/**
 * [LlmEngine.generate] 의 샘플링 파라미터 — Phase3c-I. v1.1 §5.6.
 *
 * llama.cpp 샘플러 체인 순서 (fix): `top_k → top_p → temp → dist(seed)`. 포커 도메인에서는
 * 결정성 높은 샘플링 (낮은 temp, 좁은 top_p) 이 바람직하지만 Persona 다양성을 위해 약간의
 * entropy 는 유지한다. Phase4 에서 grammar 제약과 함께 튜닝.
 *
 * - [maxNewTokens]: 프롬프트 제외 생성할 토큰 수 상한. EOS 만나면 더 일찍 종료.
 * - [temperature]: 0.0 은 greedy, 클수록 다양.
 * - [topK]: 상위 K 확률 토큰만 샘플 후보로 유지.
 * - [topP]: 누적 확률 P 이상의 상위 토큰만 유지 (nucleus).
 * - [seed]: `-1` 은 llama.cpp LLAMA_DEFAULT_SEED (시간 기반 랜덤). 양의 값은 재현성 샘플링.
 *
 * 생성 시 범위 검증. `copy(...)` 에도 적용.
 */
data class GenerationConfig(
    val maxNewTokens: Int = DEFAULT_MAX_NEW_TOKENS,
    val temperature: Float = DEFAULT_TEMPERATURE,
    val topK: Int = DEFAULT_TOP_K,
    val topP: Float = DEFAULT_TOP_P,
    val seed: Long = RANDOM_SEED,
) {
    init {
        require(maxNewTokens in 1..MAX_NEW_TOKENS_CAP) {
            "maxNewTokens must be in 1..$MAX_NEW_TOKENS_CAP (got $maxNewTokens)"
        }
        require(!temperature.isNaN() && temperature in 0.0f..2.0f) {
            "temperature must be in 0.0..2.0 (got $temperature)"
        }
        require(topK > 0) {
            "topK must be > 0 (got $topK)"
        }
        require(!topP.isNaN() && topP in 0.0f..1.0f) {
            "topP must be in 0.0..1.0 (got $topP)"
        }
        require(seed >= RANDOM_SEED) {
            "seed must be >= -1 (got $seed; -1 = random)"
        }
    }

    companion object {
        const val DEFAULT_MAX_NEW_TOKENS: Int = 128
        const val DEFAULT_TEMPERATURE: Float = 0.7f
        const val DEFAULT_TOP_K: Int = 40
        const val DEFAULT_TOP_P: Float = 0.9f
        const val RANDOM_SEED: Long = -1L

        /** Phase3c 에서 단일 턴 최대. Phase4 스트리밍 시 조정 고려. */
        const val MAX_NEW_TOKENS_CAP: Int = 4096
    }
}
