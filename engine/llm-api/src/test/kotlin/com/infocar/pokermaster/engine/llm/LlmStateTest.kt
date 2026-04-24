package com.infocar.pokermaster.engine.llm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class LlmStateTest {

    @Test
    fun `data objects are distinct singletons`() {
        assertThat(LlmState.Uninitialized).isNotEqualTo(LlmState.Ready)
        assertThat(LlmState.Ready).isNotEqualTo(LlmState.Released)
        assertThat(LlmState.Uninitialized).isSameInstanceAs(LlmState.Uninitialized)
    }

    @Test
    fun `LoadFailed preserves cause`() {
        val cause = UnsatisfiedLinkError("libpokermaster_llm.so not found for this ABI")
        val state: LlmState = LlmState.LoadFailed(cause)

        assertThat(state).isInstanceOf(LlmState.LoadFailed::class.java)
        assertThat((state as LlmState.LoadFailed).cause).isSameInstanceAs(cause)
    }

    @Test
    fun `LoadFailed equality derives from cause identity`() {
        val cause = RuntimeException("boom")
        assertThat(LlmState.LoadFailed(cause)).isEqualTo(LlmState.LoadFailed(cause))
        assertThat(LlmState.LoadFailed(cause)).isNotEqualTo(LlmState.LoadFailed(RuntimeException("other")))
    }
}
