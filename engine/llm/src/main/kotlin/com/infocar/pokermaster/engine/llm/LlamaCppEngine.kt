package com.infocar.pokermaster.engine.llm

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * llama.cpp JNI 브리지 — v1.1 §5.6. [LlmEngine] 의 Android 네이티브 구현.
 *
 * **싱글톤**: `llama_backend_init/free` 는 프로세스 수명당 1회 UB-safe 한 전역 상태를 건드린다.
 * 인스턴스가 둘 이상 공존하면 한쪽이 free 한 상태에서 다른 쪽이 init 을 가정해 UAF 가능.
 * 반드시 [tryCreate] 팩토리를 통해서만 획득한다 (double-checked locking).
 * Hilt 에서는 [com.infocar.pokermaster.engine.llm.di.LlmModule] 이 [LlmEngineHandle] 로 노출.
 *
 * **스레드 안전성**:
 *   - 모든 JNI 호출은 전용 단일 스레드 dispatcher (`llm-jni`, priority = NORM-1) 에서 수행.
 *     Compose RenderThread/Choreographer frame 에 우선권을 양보한다.
 *   - 상태 전이는 [Mutex] 로 직렬화, idempotency 는 [AtomicBoolean]+CAS 로 원자성 보장.
 *   - `backendInit/Free` 는 idempotent 재호출 안전. `loadModel` 은 이전 핸들 자동 해제 후 재로드.
 *
 * **예외 계약**:
 *   - [tryCreate] 는 `UnsatisfiedLinkError`/`LinkageError` 를 [Result.failure] 로 래핑한다.
 *   - JNI 측 예외는 C++ `ThrowJavaForCurrent` 헬퍼로 Java 예외로 변환 — RuntimeException/OOM.
 *   - [loadModel] 입력은 `ModelParams.init` + [loadModel] 본체에서 사전 검증 — GGML_ABORT 예방.
 *
 * **JNI 바인딩**: C++ 쪽은 `JNI_OnLoad` + `RegisterNatives` 로 명시 매핑. 클래스/패키지 rename
 * 은 C++의 `FindClass` 경로 한 줄만 갱신하면 된다.
 */
class LlamaCppEngine private constructor(
    private val dispatcher: CoroutineDispatcher = defaultDispatcher,
) : LlmEngine {
    private val mutex = Mutex()
    private val backendInitialized = AtomicBoolean(false)
    private val currentHandle = AtomicReference<NativeModelHandle?>(null)

    override suspend fun version(): String = mutex.withLock {
        withContext(dispatcher) { nativeVersion() }
    }

    override suspend fun pageSize(): Long = mutex.withLock {
        withContext(dispatcher) { nativePageSize() }
    }

    override suspend fun backendInit(): Unit = mutex.withLock {
        if (backendInitialized.compareAndSet(false, true)) {
            withContext(dispatcher) { nativeBackendInit() }
        }
    }

    override suspend fun backendFree(): Unit = mutex.withLock {
        // 열려 있는 모델이 있으면 먼저 해제 — backend 해제 전에 context/model 을 놔두면 UAF.
        currentHandle.getAndSet(null)?.let { h ->
            withContext(dispatcher) { nativeModelUnload(h.rawPtr) }
        }
        if (backendInitialized.compareAndSet(true, false)) {
            withContext(dispatcher) { nativeBackendFree() }
        }
    }

    override suspend fun loadModel(path: String, params: ModelParams): ModelHandle = mutex.withLock {
        // 1) backend 초기화 선결. llama_model_load_from_file 은 backend 전역 상태를 참조한다.
        check(backendInitialized.get()) {
            "backendInit() must succeed before loadModel()"
        }

        // 2) 파일 사전 검증 — GGML_ABORT (process kill) 방어선.
        //    ModelParams.init 은 숫자 범위만 본다. 여기서는 path 존재/접근권/최소 크기 체크.
        val file = File(path)
        require(file.isFile) { "GGUF not found or not a regular file: $path" }
        require(file.canRead()) { "GGUF not readable: $path" }
        require(file.length() >= MIN_GGUF_BYTES) {
            "GGUF suspiciously small (${file.length()} B, min $MIN_GGUF_BYTES) — likely truncated: $path"
        }

        // 3) 기존 핸들이 있으면 해제 (idempotent re-load).
        currentHandle.getAndSet(null)?.let { prev ->
            withContext(dispatcher) { nativeModelUnload(prev.rawPtr) }
        }

        // 4) 네이티브 로드. 실패 시 JNI 가 RuntimeException 을 throw 하므로 여기까지 오면 성공.
        val raw = withContext(dispatcher) {
            nativeModelLoad(
                path = path,
                nCtx = params.nCtx,
                nThreads = params.nThreads,
                useMmap = params.useMmap,
                useMlock = params.useMlock,
                kvQuantOrdinal = params.kvQuant.ordinal,
            )
        }
        check(raw != 0L) { "nativeModelLoad returned 0 without throwing (contract violation)" }

        val handle = NativeModelHandle(raw)
        currentHandle.set(handle)
        handle
    }

    override suspend fun unloadModel(handle: ModelHandle): Unit = mutex.withLock {
        val raw = requireNativeRaw(handle)
        withContext(dispatcher) { nativeModelUnload(raw) }
        // 현재 핸들과 일치할 때만 null 로. 이미 다른 핸들로 교체된 경우 그대로 둔다.
        currentHandle.compareAndSet(handle as NativeModelHandle, null)
    }

    override suspend fun tokenize(handle: ModelHandle, text: String): IntArray = mutex.withLock {
        val raw = requireNativeRaw(handle)
        withContext(dispatcher) { nativeTokenize(raw, text) }
    }

    override suspend fun detokenize(handle: ModelHandle, tokens: IntArray): String = mutex.withLock {
        val raw = requireNativeRaw(handle)
        withContext(dispatcher) { nativeDetokenize(raw, tokens) }
    }

    override suspend fun generate(
        handle: ModelHandle,
        promptTokens: IntArray,
        config: GenerationConfig,
    ): IntArray = mutex.withLock {
        val raw = requireNativeRaw(handle)
        // Phase3c-II: cancel state 를 진입 시 리셋. 이전 generate 호출이 cancel 로 끝났다면
        // flag 가 true 로 남아 있을 수 있다 (JNI 측에서도 진입 시 reset 하지만 방어선).
        nativeResetCancel(raw)
        // 현재 Job 이 cancel 되면 네이티브 abort callback 이 true 를 poll 하도록 flag flip.
        // llama.cpp 는 layer/token 단위로 콜백을 체크하므로 cancellation 은 cooperative — 이 훅
        // 없이는 coroutine cancel 이 네이티브 decode 루프까지 전파되지 않는다.
        val job = currentCoroutineContext()[Job]
        val cancelHook = job?.invokeOnCompletion { cause ->
            if (cause is CancellationException) {
                nativeCancel(raw)
            }
        }
        try {
            withContext(dispatcher) {
                nativeGenerate(
                    handle = raw,
                    promptTokens = promptTokens,
                    maxNewTokens = config.maxNewTokens,
                    temperature = config.temperature,
                    topK = config.topK,
                    topP = config.topP,
                    seed = config.seed,
                )
            }
        } finally {
            // 정상 완료 시 hook 해제 (leak 방지). dispose() 는 멱등.
            cancelHook?.dispose()
        }
    }

    /**
     * 핸들 런타임 타입을 [NativeModelHandle] 로 좁히고 raw 포인터를 꺼낸다. 외부에서 임의
     * 구현한 [ModelHandle] 이 전달되면 [IllegalArgumentException]. [backendInit] 이 안 된 상태에서
     * 호출되면 [IllegalStateException].
     */
    private fun requireNativeRaw(handle: ModelHandle): Long {
        check(backendInitialized.get()) { "backendInit() must succeed before inference" }
        val native = handle as? NativeModelHandle
            ?: throw IllegalArgumentException(
                "Unknown ModelHandle implementation: ${handle::class.java.name}"
            )
        return native.rawPtr
    }

    // JNI external — private 으로 캡슐화. 매핑은 C++ JNI_OnLoad 의 kLlamaMethods 테이블.
    private external fun nativeVersion(): String
    private external fun nativePageSize(): Long
    private external fun nativeBackendInit()
    private external fun nativeBackendFree()
    private external fun nativeModelLoad(
        path: String,
        nCtx: Int,
        nThreads: Int,
        useMmap: Boolean,
        useMlock: Boolean,
        kvQuantOrdinal: Int,
    ): Long
    private external fun nativeModelUnload(handle: Long)
    private external fun nativeCancel(handle: Long)
    private external fun nativeResetCancel(handle: Long)
    private external fun nativeTokenize(handle: Long, text: String): IntArray
    private external fun nativeDetokenize(handle: Long, tokens: IntArray): String
    private external fun nativeGenerate(
        handle: Long,
        promptTokens: IntArray,
        maxNewTokens: Int,
        temperature: Float,
        topK: Int,
        topP: Float,
        seed: Long,
    ): IntArray

    companion object {
        /**
         * `libpokermaster_llm.so` 로드 후 싱글톤을 [LlmEngine] 추상으로 반환.
         * 로드 실패는 [Result.failure]. 소비자는 `fold` 로 분기 (미지원 단말 안내 등).
         */
        fun tryCreate(): Result<LlmEngine> = runCatching {
            System.loadLibrary("pokermaster_llm")
            instance ?: synchronized(lock) {
                instance ?: LlamaCppEngine().also { instance = it }
            }
        }

        @Volatile private var instance: LlamaCppEngine? = null
        private val lock = Any()

        /**
         * 전용 단일 스레드 dispatcher. Daemon 이라 프로세스 종료 시 자동 정리.
         * `NORM_PRIORITY - 1` 로 RenderThread/Choreographer frame 우선권 양보.
         */
        private val defaultDispatcher: CoroutineDispatcher =
            Executors.newSingleThreadExecutor { r ->
                Thread(r, "llm-jni").apply {
                    priority = Thread.NORM_PRIORITY - 1
                    isDaemon = true
                }
            }.asCoroutineDispatcher()

        /**
         * 최소 GGUF 크기 가드 — 1B 모델 Q4_K_M 이 ~750MB. 10MB 미만이면 정상 GGUF 아님.
         * tiny test 모델 (1MB 이하) 은 허용되지 않으며 Phase3b-II 테스트 인프라에서 분리한다.
         */
        private const val MIN_GGUF_BYTES: Long = 10_000_000L
    }
}

/**
 * [ModelHandle] 의 JNI 구현. `rawPtr` 은 C++ `LlamaSession*` 를 `reinterpret_cast<jlong>` 한 값.
 * `:engine:llm-api` 의 `ModelHandle` 은 빈 마커이고, 실제 포인터 소유권은 이 타입에만 존재한다.
 */
internal data class NativeModelHandle(val rawPtr: Long) : ModelHandle
