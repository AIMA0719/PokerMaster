package com.infocar.pokermaster.engine.controller.llm

import com.infocar.pokermaster.core.model.ActionType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * LLM 이 생성한 포커 결정 — Phase5-I.
 *
 * [action] 은 [ActionType] 열거 이름 (대문자). [amount] 는 누적 commit 절대값 (GameContext 계약).
 * [confidence] 는 0..1, [reasoning] 은 디버그/튜닝 용 optional.
 *
 * JSON 스키마는 [com.infocar.pokermaster.engine.controller.llm.DecisionGrammar.DECISION] 으로
 * 강제. 스키마 어긋난 응답은 [parse] 가 null 반환.
 */
@Serializable
data class LlmDecision(
    @SerialName("action") val action: String,
    @SerialName("amount") val amount: Long = 0L,
    @SerialName("confidence") val confidence: Double = 0.5,
    @SerialName("reasoning") val reasoning: String? = null,
) {
    /** LLM 출력이 유효한 ActionType 으로 매핑되는지. */
    fun actionTypeOrNull(): ActionType? = runCatching { ActionType.valueOf(action) }.getOrNull()

    /** [confidence] 가 실수 범위를 벗어났는지 체크. */
    val confidenceInRange: Boolean get() = confidence in 0.0..1.0

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true  // LLM 이 추가 필드 만들어도 무시
            coerceInputValues = true
            isLenient = true
        }

        /**
         * LLM 이 [com.infocar.pokermaster.engine.llm.LlmEngine.generateJson] 으로 반환한 문자열을
         * [LlmDecision] 으로 파싱. 실패/schema 어긋남/unknown action 은 모두 null.
         *
         * 호출자는 null 이면 폴백 (PersonaBias heuristic) 으로 내려가야 한다 (Phase5-II).
         */
        fun parse(raw: String): LlmDecision? {
            val trimmed = raw.trim().takeIf { it.isNotEmpty() } ?: return null
            // 1차: JSON strict decode. GBNF 가 정상 동작하면 거의 모든 케이스 cover.
            val decision = runCatching { json.decodeFromString<LlmDecision>(trimmed) }.getOrNull()
                ?: parseFromRegex(trimmed)        // 2차: GBNF 무시되거나 LLM 가 메타 텍스트 끼워넣은 경우.
                ?: return null
            // 최소 검증: action 이 알려진 enum 이어야 함, amount non-negative,
            // confidence 범위, reasoning 은 선택.
            if (decision.actionTypeOrNull() == null) return null
            if (decision.amount < 0L) return null
            if (!decision.confidenceInRange) return null
            return decision
        }

        /**
         * sprint C2 Phase 4: regex 폴백. GBNF 가 soft-fail 하거나 LLM 이 메타 텍스트
         * ("Sure, my decision is RAISE 200 because...") 를 끼워 넣었을 때 ActionType 이름과
         * amount 만 정직하게 추출. confidence 0.5 기본 (불확실 표시).
         *
         * 매칭 못 하면 null → 호출자 폴백 (PersonaBias heuristic) 유지.
         */
        private fun parseFromRegex(raw: String): LlmDecision? {
            val actionMatch = ACTION_REGEX.find(raw) ?: return null
            val action = actionMatch.value.uppercase()
            val amount = AMOUNT_REGEX.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            return LlmDecision(action = action, amount = amount, confidence = 0.5)
        }

        private val ACTION_REGEX = Regex(
            """\b(FOLD|CHECK|CALL|RAISE|ALL_IN|BET|COMPLETE|BRING_IN|SAVE_LIFE|DECLARE)\b""",
            RegexOption.IGNORE_CASE,
        )

        /** "amount": 200 / amount=200 / amount 200 — quote/colon/equals/공백 구분자 허용. */
        private val AMOUNT_REGEX = Regex(
            """amount["\s:=]+(\d+)""",
            RegexOption.IGNORE_CASE,
        )
    }
}
