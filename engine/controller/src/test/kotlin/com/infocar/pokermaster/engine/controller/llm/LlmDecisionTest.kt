package com.infocar.pokermaster.engine.controller.llm

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import org.junit.jupiter.api.Test

class LlmDecisionTest {

    @Test
    fun `parses well-formed decision`() {
        val d = LlmDecision.parse(
            """{"action":"RAISE","amount":200,"confidence":0.8,"reasoning":"top pair"}"""
        )
        assertThat(d).isNotNull()
        assertThat(d!!.actionTypeOrNull()).isEqualTo(ActionType.RAISE)
        assertThat(d.amount).isEqualTo(200L)
        assertThat(d.confidence).isEqualTo(0.8)
    }

    @Test
    fun `tolerates missing reasoning`() {
        val d = LlmDecision.parse("""{"action":"FOLD","amount":0,"confidence":0.9}""")
        assertThat(d).isNotNull()
        assertThat(d!!.reasoning).isNull()
    }

    @Test
    fun `rejects unknown action enum`() {
        val d = LlmDecision.parse(
            """{"action":"DOUBLE_DOWN","amount":100,"confidence":0.5}"""
        )
        assertThat(d).isNull()
    }

    @Test
    fun `rejects negative amount`() {
        val d = LlmDecision.parse(
            """{"action":"BET","amount":-50,"confidence":0.5}"""
        )
        assertThat(d).isNull()
    }

    @Test
    fun `rejects confidence out of range`() {
        val d = LlmDecision.parse(
            """{"action":"CALL","amount":50,"confidence":1.5}"""
        )
        assertThat(d).isNull()
    }

    @Test
    fun `rejects empty input`() {
        assertThat(LlmDecision.parse("")).isNull()
        assertThat(LlmDecision.parse("   ")).isNull()
    }

    @Test
    fun `rejects non-JSON garbage`() {
        assertThat(LlmDecision.parse("hello world")).isNull()
        assertThat(LlmDecision.parse("{broken json")).isNull()
    }

    @Test
    fun `ignores extra unknown fields`() {
        val d = LlmDecision.parse(
            """{"action":"CHECK","amount":0,"confidence":0.6,"extra":"ignored"}"""
        )
        assertThat(d).isNotNull()
        assertThat(d!!.actionTypeOrNull()).isEqualTo(ActionType.CHECK)
    }
}
