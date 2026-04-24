package com.infocar.pokermaster.engine.llm

/**
 * 사전 정의된 GBNF 문법 — Phase4 구조화 출력.
 *
 * llama.cpp 의 grammar sampler 는 공식 `grammars/json.gbnf` 와 호환되는 상위 문법 subset 을
 * 수용한다. 문법 결합 순서는 `root` 규칙이 엔트리여야 한다 (JNI 측에서 `"root"` 고정).
 */
object JsonGrammar {

    /**
     * 엄격 JSON — llama.cpp grammars/json.gbnf 를 약간 축약 (whitespace 단순화).
     *
     * 포커 AI 의 Decision 객체 (action/sizing/confidence) 처럼 예측 가능한 JSON 오브젝트를
     * 강제할 때 사용. Phase5 에서 Decision-specific schema 로 더 좁힐 예정.
     */
    val STRICT: String =
        """
        root   ::= object
        value  ::= object | array | string | number | boolean | "null"

        object ::= "{" ws ( string ws ":" ws value ( ws "," ws string ws ":" ws value )* )? ws "}"
        array  ::= "[" ws ( value ( ws "," ws value )* )? ws "]"

        string ::= "\"" ( [^"\\] | "\\" ( ["\\/bfnrt] | "u" [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] [0-9a-fA-F] ) )* "\""
        number ::= ("-")? ("0" | [1-9] [0-9]*) ("." [0-9]+)? ([eE] [-+]? [0-9]+)?
        boolean ::= "true" | "false"

        ws     ::= [ \t\n]*
        """.trimIndent()
}
