package com.infocar.pokermaster.engine.llm

/**
 * LlmEngine 로드 결과 — Hilt 주입 경계 타입.
 *
 * Kotlin `Result<T>` 는 inline value class 라 Dagger KSP 가 함수 이름을 mangling 한 결과
 * (`-d1pmJ48Factory`) 를 javapoet 이 처리하지 못해 Factory 생성이 실패한다. 이 sealed interface
 * 로 대체해 DI 경계에서 타입 안전한 fold 가 가능하다.
 *
 * 소비자 (예: ModelGate ViewModel):
 * ```
 * when (handle) {
 *   is LlmEngineHandle.Available   -> handle.engine.backendInit()
 *   is LlmEngineHandle.Unavailable -> // "미지원 단말" 안내
 * }
 * ```
 */
sealed interface LlmEngineHandle {

    data class Available(val engine: LlmEngine) : LlmEngineHandle

    data class Unavailable(val cause: Throwable) : LlmEngineHandle

    /** 편의: Available 일 때만 engine 을 꺼내고 그 외는 null. */
    fun engineOrNull(): LlmEngine? = (this as? Available)?.engine
}
