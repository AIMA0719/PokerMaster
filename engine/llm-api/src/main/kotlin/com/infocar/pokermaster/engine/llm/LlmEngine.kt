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

    /**
     * 입력 [text] 를 모델 토크나이저로 분해. 기본적으로 BOS 자동 추가 (모델이 요구 시).
     * 빈 문자열 → 빈 `IntArray`.
     */
    suspend fun tokenize(handle: ModelHandle, text: String): IntArray

    /**
     * 토큰 배열을 문자열로 복원 (subword piece 누적). special token 은 제거.
     */
    suspend fun detokenize(handle: ModelHandle, tokens: IntArray): String

    /**
     * [promptTokens] 를 컨텍스트에 decode 한 뒤 [GenerationConfig.maxNewTokens] 개까지 샘플링.
     * EOS/EOG 토큰 만나면 조기 종료. 반환은 **프롬프트 제외** 생성된 토큰만.
     *
     * 현재 구현은 단일 턴 (warmup/cancel/streaming 은 Phase3c-II/Phase4). 동일 핸들로 재호출
     * 하면 KV cache 에 이어 append 되지 않고 clean reset 을 가정하므로, 연속 턴 대화는
     * Phase3c-II 에서 별도 API (예: `chat(handle, messages)`) 로 노출.
     */
    suspend fun generate(
        handle: ModelHandle,
        promptTokens: IntArray,
        config: GenerationConfig = GenerationConfig(),
    ): IntArray

    /**
     * Phase4: 문법 제약 (GBNF) 하에 자유 텍스트 프롬프트로부터 JSON 문자열 생성.
     *
     * 기본은 [JsonGrammar.STRICT] — 엄격 JSON 객체/배열만 허용. 호출자는 `schema`-specific
     * GBNF 를 넘겨 더 좁힐 수 있다. 내부적으로 tokenize → [generate] (grammar 장착) → detokenize
     * 를 순차 수행. 실패 경로 (grammar 자체가 invalid GBNF) 는 JNI 가 grammar 없이 진행하는
     * soft-fail 로 동작하므로 반환 문자열이 JSON 이 아닐 수 있다 — 호출자는 파싱 검증을 해야 한다.
     */
    suspend fun generateJson(
        handle: ModelHandle,
        prompt: String,
        grammar: String = JsonGrammar.STRICT,
        config: GenerationConfig = GenerationConfig(),
    ): String {
        val promptTokens = tokenize(handle, prompt)
        val out = generate(handle, promptTokens, config.copy(grammar = grammar))
        return detokenize(handle, out)
    }
}
