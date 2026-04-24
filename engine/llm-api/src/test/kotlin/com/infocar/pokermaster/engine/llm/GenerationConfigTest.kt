package com.infocar.pokermaster.engine.llm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class GenerationConfigTest {

    @Test
    fun `defaults for poker persona sampling`() {
        val c = GenerationConfig()
        assertThat(c.maxNewTokens).isEqualTo(128)
        assertThat(c.temperature).isEqualTo(0.7f)
        assertThat(c.topK).isEqualTo(40)
        assertThat(c.topP).isEqualTo(0.9f)
        assertThat(c.seed).isEqualTo(-1L)
    }

    @Test
    fun `maxNewTokens range is enforced`() {
        assertThrows<IllegalArgumentException> { GenerationConfig(maxNewTokens = 0) }
        assertThrows<IllegalArgumentException> {
            GenerationConfig(maxNewTokens = GenerationConfig.MAX_NEW_TOKENS_CAP + 1)
        }
    }

    @Test
    fun `temperature range is enforced incl NaN guard`() {
        assertThrows<IllegalArgumentException> { GenerationConfig(temperature = -0.1f) }
        assertThrows<IllegalArgumentException> { GenerationConfig(temperature = 2.01f) }
        assertThrows<IllegalArgumentException> { GenerationConfig(temperature = Float.NaN) }
    }

    @Test
    fun `topP range is enforced incl NaN guard`() {
        assertThrows<IllegalArgumentException> { GenerationConfig(topP = -0.01f) }
        assertThrows<IllegalArgumentException> { GenerationConfig(topP = 1.01f) }
        assertThrows<IllegalArgumentException> { GenerationConfig(topP = Float.NaN) }
    }

    @Test
    fun `topK must be positive`() {
        assertThrows<IllegalArgumentException> { GenerationConfig(topK = 0) }
        assertThrows<IllegalArgumentException> { GenerationConfig(topK = -5) }
    }

    @Test
    fun `seed allows -1 random and any non-negative`() {
        GenerationConfig(seed = -1L)        // OK
        GenerationConfig(seed = 0L)         // OK
        GenerationConfig(seed = Long.MAX_VALUE)  // OK
        assertThrows<IllegalArgumentException> { GenerationConfig(seed = -2L) }
    }

    @Test
    fun `copy re-evaluates invariants`() {
        val base = GenerationConfig()
        assertThat(base.copy(temperature = 0.0f).temperature).isEqualTo(0.0f)
        assertThrows<IllegalArgumentException> { base.copy(topK = 0) }
    }
}
