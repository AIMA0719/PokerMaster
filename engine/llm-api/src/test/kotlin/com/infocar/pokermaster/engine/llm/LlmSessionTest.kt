package com.infocar.pokermaster.engine.llm

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class LlmSessionTest {

    @Test
    fun `Unavailable handle starts in LoadFailed`() = runTest {
        val cause = UnsatisfiedLinkError("no .so on this ABI")
        val session = LlmSession(LlmEngineHandle.Unavailable(cause))
        val state = session.state.value
        assertThat(state).isInstanceOf(LlmState.LoadFailed::class.java)
        assertThat((state as LlmState.LoadFailed).cause).isSameInstanceAs(cause)
    }

    @Test
    fun `Available handle starts in Uninitialized and goes Ready on initBackend`() = runTest {
        val engine = FakeLlmEngine()
        val session = LlmSession(LlmEngineHandle.Available(engine))
        assertThat(session.state.value).isEqualTo(LlmState.Uninitialized)

        session.initBackend()

        assertThat(session.state.value).isEqualTo(LlmState.Ready)
        assertThat(engine.backendInitCalls).isEqualTo(1)
    }

    @Test
    fun `initBackend is idempotent (no extra engine call once Ready)`() = runTest {
        val engine = FakeLlmEngine()
        val session = LlmSession(LlmEngineHandle.Available(engine))
        session.initBackend()
        session.initBackend()
        session.initBackend()
        assertThat(engine.backendInitCalls).isEqualTo(1)
        assertThat(session.state.value).isEqualTo(LlmState.Ready)
    }

    @Test
    fun `initBackend failure transitions to LoadFailed`() = runTest {
        val boom = RuntimeException("backend init crashed")
        val engine = FakeLlmEngine(initError = boom)
        val session = LlmSession(LlmEngineHandle.Available(engine))

        session.initBackend()

        val state = session.state.value
        assertThat(state).isInstanceOf(LlmState.LoadFailed::class.java)
        assertThat((state as LlmState.LoadFailed).cause).isSameInstanceAs(boom)
    }

    @Test
    fun `loadModel before Ready throws IllegalStateException`() = runTest {
        val session = LlmSession(LlmEngineHandle.Available(FakeLlmEngine()))
        assertThrows<IllegalStateException> { session.loadModel("dummy.gguf") }
    }

    @Test
    fun `loadModel records currentModel`() = runTest {
        val engine = FakeLlmEngine()
        val session = LlmSession(LlmEngineHandle.Available(engine))
        session.initBackend()

        val handle = session.loadModel("dummy.gguf")

        assertThat(session.currentModel()).isSameInstanceAs(handle)
        assertThat(session.state.value).isEqualTo(LlmState.Ready)
    }

    @Test
    fun `unloadModel clears currentModel but keeps Ready`() = runTest {
        val session = LlmSession(LlmEngineHandle.Available(FakeLlmEngine()))
        session.initBackend()
        session.loadModel("dummy.gguf")

        session.unloadModel()

        assertThat(session.currentModel()).isNull()
        assertThat(session.state.value).isEqualTo(LlmState.Ready)
    }

    @Test
    fun `release transitions to Released and frees backend`() = runTest {
        val engine = FakeLlmEngine()
        val session = LlmSession(LlmEngineHandle.Available(engine))
        session.initBackend()
        session.loadModel("dummy.gguf")

        session.release()

        assertThat(session.state.value).isEqualTo(LlmState.Released)
        assertThat(session.currentModel()).isNull()
        assertThat(engine.backendFreeCalls).isEqualTo(1)
    }

    @Test
    fun `Released can re-initialize back to Ready`() = runTest {
        val engine = FakeLlmEngine()
        val session = LlmSession(LlmEngineHandle.Available(engine))
        session.initBackend()
        session.release()

        session.initBackend()

        assertThat(session.state.value).isEqualTo(LlmState.Ready)
        assertThat(engine.backendInitCalls).isEqualTo(2)
    }
}

/**
 * 테스트 전용 in-memory LlmEngine. 상태 전이와 호출 횟수만 기록한다.
 * 실제 네이티브 호출은 없으므로 JVM 유닛테스트로 실행 가능.
 */
private class FakeLlmEngine(
    private val initError: Throwable? = null,
) : LlmEngine {
    var backendInitCalls = 0
        private set
    var backendFreeCalls = 0
        private set
    private var unloadedModel: ModelHandle? = null

    override suspend fun version(): String = "fake-llm/0.1"
    override suspend fun pageSize(): Long = 4096L

    override suspend fun backendInit() {
        initError?.let { throw it }
        backendInitCalls++
    }

    override suspend fun backendFree() {
        backendFreeCalls++
    }

    override suspend fun loadModel(path: String, params: ModelParams): ModelHandle {
        val handle = object : ModelHandle {}
        unloadedModel = handle
        return handle
    }

    override suspend fun unloadModel(handle: ModelHandle) {
        unloadedModel = null
    }

    override suspend fun tokenize(handle: ModelHandle, text: String): IntArray = IntArray(0)
    override suspend fun detokenize(handle: ModelHandle, tokens: IntArray): String = ""
    override suspend fun generate(
        handle: ModelHandle,
        promptTokens: IntArray,
        config: GenerationConfig,
    ): IntArray = IntArray(0)
}
