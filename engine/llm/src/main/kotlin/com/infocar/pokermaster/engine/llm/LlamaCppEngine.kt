package com.infocar.pokermaster.engine.llm

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * llama.cpp JNI 브리지 — v1.1 §5.6. [LlmEngine] 의 Android 네이티브 구현.
 *
 * **싱글톤**: `llama_backend_init/free` 는 프로세스 수명당 1회 UB-safe 한 전역 상태를 건드린다.
 * 인스턴스가 둘 이상 공존하면 한쪽이 free 한 상태에서 다른 쪽이 init 을 가정해 UAF 가능.
 * 반드시 [tryCreate] 팩토리를 통해서만 획득한다 (double-checked locking).
 * Hilt 에서는 [com.infocar.pokermaster.engine.llm.di.LlmModule] 이 `Result<LlmEngine>` 로 노출.
 *
 * **스레드 안전성**:
 *   - 모든 JNI 호출은 전용 단일 스레드 dispatcher (`llm-jni`, priority = NORM-1) 에서 수행.
 *     Compose RenderThread/Choreographer frame 에 우선권을 양보한다.
 *   - 상태 전이는 [Mutex] 로 직렬화, idempotency 는 [AtomicBoolean]+CAS 로 원자성 보장.
 *   - `backendInit/Free` 는 idempotent 재호출 안전. 여러 코루틴이 동시 호출해도 JNI 는 1회만.
 *
 * **예외 계약**:
 *   - [tryCreate] 는 `UnsatisfiedLinkError`/`LinkageError` 를 [Result.failure] 로 래핑한다.
 *     호출측 (예: ModelGate) 은 "미지원 단말" 안내 분기에 사용 가능.
 *   - JNI 측 예외는 C++ `ThrowJavaForCurrent` 헬퍼로 Java 예외로 변환 — RuntimeException/OOM 수신.
 *
 * **JNI 바인딩**: C++ 쪽은 `JNI_OnLoad` + `RegisterNatives` 로 명시 매핑. Kotlin 외부 함수 이름
 * (nativeVersion 등) 만 C++ `kLlamaMethods` 테이블과 맞추면 되고, 클래스/패키지 rename 은 C++의
 * `FindClass` 경로 한 줄만 갱신한다.
 */
class LlamaCppEngine private constructor(
    private val dispatcher: CoroutineDispatcher = defaultDispatcher,
) : LlmEngine {
    private val mutex = Mutex()
    private val backendInitialized = AtomicBoolean(false)

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
        if (backendInitialized.compareAndSet(true, false)) {
            withContext(dispatcher) { nativeBackendFree() }
        }
    }

    // JNI external — private 으로 캡슐화해 외부에서 직접 호출로 불변식 깨는 걸 막는다.
    // 매핑은 C++ JNI_OnLoad 의 kLlamaMethods 테이블이 담당 (이름만 일치해야 함).
    private external fun nativeVersion(): String
    private external fun nativePageSize(): Long
    private external fun nativeBackendInit()
    private external fun nativeBackendFree()

    companion object {
        /**
         * `libpokermaster_llm.so` 로드 후 싱글톤을 [LlmEngine] 추상으로 반환.
         * 로드 실패는 [Result.failure]. 소비자는 `fold` 로 분기 (미지원 단말 안내 등).
         *
         * `UnsatisfiedLinkError`/`LinkageError` 는 Error 계열이라 일반 try/catch 로 잡히지 않는
         * 상황이 있어 `runCatching` (Throwable 전체) 으로 래핑. static init 이
         * `ExceptionInInitializerError` 로 확산되는 것을 막는다.
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
    }
}
