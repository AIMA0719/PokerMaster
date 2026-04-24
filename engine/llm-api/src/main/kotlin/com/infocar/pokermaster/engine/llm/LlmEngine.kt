package com.infocar.pokermaster.engine.llm

/**
 * On-device LLM 런타임 추상화 — v1.1 §5.6.
 *
 * 모든 메서드는 [Mutex]-직렬화된 JNI 호출 또는 그에 상응하는 스레드 안전 구현이어야 한다.
 * 구현체는 `:engine:llm` 의 [LlamaCppEngine] (llama.cpp b8870 기반).
 *
 * Phase3b+ 에서 loadModel/generate 등 확장.
 */
interface LlmEngine {
    /** 네이티브 빌드 식별 문자열 — 로드 smoke 용. */
    suspend fun version(): String

    /** OS 페이지 크기 (sysconf _SC_PAGE_SIZE). 16KB 단말은 16384. */
    suspend fun pageSize(): Long

    /** 프로세스 수명당 1회 실제 호출 보장. 재호출 safe (no-op). */
    suspend fun backendInit()

    /** 앱 종료 정리용. 초기화 안 됐으면 no-op. */
    suspend fun backendFree()
}
