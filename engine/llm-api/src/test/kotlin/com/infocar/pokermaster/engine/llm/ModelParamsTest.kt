package com.infocar.pokermaster.engine.llm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ModelParamsTest {

    @Test
    fun `defaults match design doc §5_6`() {
        val p = ModelParams()
        assertThat(p.nCtx).isEqualTo(2048)
        assertThat(p.nThreads).isEqualTo(4)
        assertThat(p.useMmap).isTrue()
        assertThat(p.useMlock).isFalse()
        assertThat(p.kvQuant).isEqualTo(KvQuant.Q8_0)
    }

    @Test
    fun `nCtx out of range is rejected`() {
        assertThrows<IllegalArgumentException> { ModelParams(nCtx = 0) }
        assertThrows<IllegalArgumentException> { ModelParams(nCtx = ModelParams.MAX_N_CTX + 1) }
    }

    @Test
    fun `nThreads out of range is rejected`() {
        assertThrows<IllegalArgumentException> { ModelParams(nThreads = 0) }
        assertThrows<IllegalArgumentException> { ModelParams(nThreads = ModelParams.MAX_N_THREADS + 1) }
    }

    @Test
    fun `copy re-evaluates invariants`() {
        val base = ModelParams()
        val wider = base.copy(nCtx = 4096)
        assertThat(wider.nCtx).isEqualTo(4096)
        // copy 로도 범위 위반은 거부되어야 한다 (data class init 블록이 재실행됨).
        assertThrows<IllegalArgumentException> { base.copy(nCtx = -1) }
    }

    @Test
    fun `KvQuant ordinals are stable for JNI`() {
        // C++ kvQuantOrdinal 매핑 (0=F16, 1=Q8_0) 과 일치해야 한다.
        // 순서를 바꾸거나 중간에 값 삽입 금지 — 새 enum 은 항상 끝에 추가.
        assertThat(KvQuant.F16.ordinal).isEqualTo(0)
        assertThat(KvQuant.Q8_0.ordinal).isEqualTo(1)
        assertThat(KvQuant.values()).hasLength(2)
    }
}
