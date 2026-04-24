package com.infocar.pokermaster.engine.llm

/**
 * [LlmEngine.loadModel] 에 전달할 llama context 파라미터 — v1.1 §5.6 / 감사 Mem#1~3.
 *
 * 기본값:
 *  - [nCtx] = 2048: 설계서 ctx. 1B 모델에 포커 컨텍스트 윈도우로 적절.
 *  - [nThreads] = 4: big.LITTLE 단말에서 big 클러스터만 (감사 Perf#11, Mem#3).
 *    LITTLE 코어까지 잡으면 cache thrashing 으로 오히려 느려진다. Phase3b-II 에서 thermal
 *    상태 시 2 로 드롭.
 *  - [useMmap] = true: 파일 backed 매핑으로 kernel 이 메모리 압력 시 정상 evict 가능.
 *  - [useMlock] = false: mlock 은 RAM 을 지속 점유해 Android 에서 OOM 위험. §5.7.
 *  - [kvQuant] = Q8_0: KV cache 메모리 절반 (~34MB vs FP16 67MB), 품질 손실 미미.
 *
 * 생성 시점에 범위 검증. `copy(...)` 에도 적용되어 방어선 역할.
 */
data class ModelParams(
    val nCtx: Int = DEFAULT_N_CTX,
    val nThreads: Int = DEFAULT_N_THREADS,
    val useMmap: Boolean = true,
    val useMlock: Boolean = false,
    val kvQuant: KvQuant = KvQuant.Q8_0,
) {
    init {
        // llama.cpp GGML_ABORT 는 process-kill 이므로 네이티브 진입 전에 방어한다.
        require(nCtx in MIN_N_CTX..MAX_N_CTX) {
            "nCtx must be in $MIN_N_CTX..$MAX_N_CTX (got $nCtx)"
        }
        require(nThreads in MIN_N_THREADS..MAX_N_THREADS) {
            "nThreads must be in $MIN_N_THREADS..$MAX_N_THREADS (got $nThreads)"
        }
    }

    companion object {
        const val DEFAULT_N_CTX: Int = 2048
        const val DEFAULT_N_THREADS: Int = 4

        const val MIN_N_CTX: Int = 1
        const val MAX_N_CTX: Int = 131072

        const val MIN_N_THREADS: Int = 1
        const val MAX_N_THREADS: Int = 16
    }
}
