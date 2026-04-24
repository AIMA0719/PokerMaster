package com.infocar.pokermaster.engine.controller.llm

/**
 * GBNF 문법 — Phase5-I. [LlmDecision] 스키마를 샘플링 단계에서 강제한다.
 *
 * [com.infocar.pokermaster.engine.llm.JsonGrammar.STRICT] 보다 좁은 제한:
 *  - root 가 반드시 `action/amount/confidence[/reasoning]` 필드를 포함하는 JSON 객체
 *  - `action` 값은 ActionType enum 이름 (FOLD/CHECK/CALL/BET/RAISE/ALL_IN) 로만 제한
 *  - `amount` 는 non-negative integer
 *  - `confidence` 는 0..1 범위 decimal (GBNF 에서 [0-9] 최대값 0.999 로 근사)
 *  - `reasoning` 은 optional 하면 복잡해지므로 항상 포함시키되 빈 문자열 허용
 *
 * LLM 이 이 문법을 벗어난 토큰을 생성하는 것이 샘플러 단계에서 차단된다 (top_k/top_p 이전에
 * grammar mask).
 */
object DecisionGrammar {

    /** Hold'em / 7-stud 공통 제한. SAVE_LIFE/BRING_IN/COMPLETE 같은 variant-specific 은 Phase5-II. */
    val DECISION: String =
        """
        root     ::= "{" ws "\"action\"" ws ":" ws action ws "," ws "\"amount\"" ws ":" ws amount ws "," ws "\"confidence\"" ws ":" ws confidence ws "," ws "\"reasoning\"" ws ":" ws reason ws "}"

        action   ::= "\"FOLD\"" | "\"CHECK\"" | "\"CALL\"" | "\"BET\"" | "\"RAISE\"" | "\"ALL_IN\""
        amount   ::= "0" | [1-9] [0-9]*
        confidence ::= "0" ("." [0-9]+)? | "1" ("." "0"+)? | "0." [0-9]+
        reason   ::= "\"" ( [^"\\] | "\\" [\"\\nrt] ){0,256} "\""

        ws       ::= [ \t\n]*
        """.trimIndent()
}
