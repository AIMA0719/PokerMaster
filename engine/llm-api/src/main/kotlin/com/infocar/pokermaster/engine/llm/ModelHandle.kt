package com.infocar.pokermaster.engine.llm

/**
 * 로드된 LLM 세션에 대한 불투명 핸들.
 *
 * API 모듈은 내부 표현을 모른다. 엔진 구현 (`:engine:llm` 의 `LlamaCppEngine`) 이 자체
 * `NativeModelHandle` 로 발급하며, 소비자는 [LlmEngine.unloadModel] 에 다시 전달하는 것 외의
 * 조작을 해서는 안 된다.
 *
 * 외부에서 이 인터페이스를 구현하지 말 것 — 엔진이 받은 핸들의 런타임 타입을 판별해 내부
 * 포인터를 꺼내므로, 외부 구현체를 전달하면 `IllegalArgumentException` 이 발생한다.
 */
interface ModelHandle
