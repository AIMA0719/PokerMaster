package com.infocar.pokermaster.engine.llm

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * LLM 세션 상태 머신 — Phase3c-III (감사 Clean#6).
 *
 * [LlmEngine] 위에 얹힌 pure-Kotlin wrapper. state 전이를 [state] StateFlow 로 노출해 UI/VM 이
 * 관찰하고, 실 엔진 호출은 [LlmEngineHandle] 을 통해 위임한다. 테스트는 [LlmEngine] 인터페이스를
 * fake 로 구현해 JVM 에서 전이 로직만 검증할 수 있다.
 *
 * **전이**:
 *   Uninitialized     → (initBackend OK)   → Ready
 *   Uninitialized     → (handle Unavailable) → LoadFailed (ctor 시 즉시 분기)
 *   Ready             → (engine.backendInit 실패) → LoadFailed
 *   Ready             → Released (release)
 *   Released          → Ready (initBackend 재호출)
 *
 * **모델 수명**: backend 상태와 별개. [loadModel] 은 Ready 에서만 가능하며 내부 [currentModel]
 * 참조만 갱신한다 (state 는 Ready 유지). 모델 해제는 [unloadModel] 또는 [release].
 *
 * **Thread-safety**: 모든 전이는 내부 [Mutex] 로 직렬화. [LlmEngine] 자체도 mutex 가 있지만
 * 그것은 JNI 경계 보호용이고, 이 mutex 는 state 원자성 보호용이다.
 */
class LlmSession(
    private val handle: LlmEngineHandle,
) {
    private val mutex = Mutex()
    private val _state = MutableStateFlow<LlmState>(
        when (handle) {
            is LlmEngineHandle.Available -> LlmState.Uninitialized
            is LlmEngineHandle.Unavailable -> LlmState.LoadFailed(handle.cause)
        }
    )
    val state: StateFlow<LlmState> = _state.asStateFlow()

    @Volatile
    private var currentModel: ModelHandle? = null

    /** 현재 로드된 모델 핸들 (없으면 null). */
    fun currentModel(): ModelHandle? = currentModel

    /**
     * state 가 [LlmState.Ready] 일 때만 underlying [LlmEngine] 을 노출.
     * 그 외 상태 (Uninitialized / Released / LoadFailed) 에서는 null — 호출자는 폴백해야 한다.
     * Advisor 가 generate/tokenize 를 직접 호출해야 하는 경우 사용.
     */
    fun engineIfReady(): LlmEngine? {
        if (_state.value !is LlmState.Ready) return null
        return handle.engineOrNull()
    }

    /**
     * 네이티브 backend 초기화. 이미 Ready 이면 no-op. LoadFailed 상태 (엔진 미지원 단말) 에서는
     * 네이티브 호출 자체가 불가능하므로 no-op.
     */
    suspend fun initBackend(): Unit = mutex.withLock {
        val engine = handle.engineOrNull() ?: return@withLock
        if (_state.value is LlmState.Ready) return@withLock
        runCatching { engine.backendInit() }.fold(
            onSuccess = { _state.value = LlmState.Ready },
            onFailure = { _state.value = LlmState.LoadFailed(it) },
        )
    }

    /**
     * GGUF 모델 로드. backend 선결 (Ready 상태) 필수 — 위반 시 [IllegalStateException].
     * 이미 로드된 모델이 있으면 엔진 구현이 먼저 unload 후 재로드 (LlamaCppEngine.loadModel 참조).
     */
    suspend fun loadModel(
        path: String,
        params: ModelParams = ModelParams(),
    ): ModelHandle = mutex.withLock {
        val engine = handle.engineOrNull()
            ?: throw IllegalStateException("engine is Unavailable (native load failed)")
        check(_state.value is LlmState.Ready) {
            "loadModel requires LlmState.Ready (current = ${_state.value})"
        }
        val loaded = engine.loadModel(path, params)
        currentModel = loaded
        loaded
    }

    /**
     * 로드된 모델만 해제. backend 는 살아있다 (다른 모델 재로드 가능). Ready 유지.
     * 로드된 모델이 없으면 no-op.
     */
    suspend fun unloadModel(): Unit = mutex.withLock {
        val engine = handle.engineOrNull() ?: return@withLock
        val loaded = currentModel ?: return@withLock
        engine.unloadModel(loaded)
        currentModel = null
    }

    /**
     * 모델 + backend 를 모두 해제. 상태 → [LlmState.Released]. [initBackend] 재호출로 복귀 가능.
     * TRIM_MEMORY_RUNNING_CRITICAL 같은 메모리 압박 시그널에서 호출 (PokerMasterApp 참조).
     */
    suspend fun release(): Unit = mutex.withLock {
        val engine = handle.engineOrNull() ?: run {
            _state.value = LlmState.Released
            return@withLock
        }
        currentModel?.let { engine.unloadModel(it) }
        currentModel = null
        runCatching { engine.backendFree() }
        _state.value = LlmState.Released
    }
}
