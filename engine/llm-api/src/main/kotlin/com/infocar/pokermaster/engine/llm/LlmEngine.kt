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

    /**
     * GGUF 파일을 읽어 model + context 를 생성하고 불투명 핸들을 반환.
     *
     * 계약:
     *  - [backendInit] 성공 후에만 호출 가능. 아니면 `IllegalStateException`.
     *  - 같은 엔진에 이미 로드된 핸들이 있으면 먼저 [unloadModel] 된 후 재로드.
     *  - [path] 가 존재하지 않거나 읽을 수 없으면 `IllegalArgumentException`.
     *  - [params] 가 범위를 벗어나면 `IllegalArgumentException` (ModelParams.init).
     *  - 로드 자체 실패 (손상 GGUF, OOM, 호환 불가 quant 등) 는 `RuntimeException`.
     */
    suspend fun loadModel(path: String, params: ModelParams = ModelParams()): ModelHandle

    /**
     * [handle] 에 연결된 model + context 를 해제. 핸들이 null 이거나 이미 unload 된 경우 no-op.
     * 알 수 없는 런타임 타입의 핸들 (외부에서 직접 구현한 경우) 은 `IllegalArgumentException`.
     */
    suspend fun unloadModel(handle: ModelHandle)
}
