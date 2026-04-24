package com.infocar.pokermaster.engine.llm

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class JsonGrammarTest {

    @Test
    fun `STRICT grammar defines root and common JSON value types`() {
        val g = JsonGrammar.STRICT
        assertThat(g).contains("root   ::= object")
        assertThat(g).contains("object ::=")
        assertThat(g).contains("array  ::=")
        assertThat(g).contains("string ::=")
        assertThat(g).contains("number ::=")
        assertThat(g).contains("boolean ::=")
        assertThat(g).contains("\"null\"")
    }

    @Test
    fun `STRICT grammar is non-trivial size and not just whitespace`() {
        assertThat(JsonGrammar.STRICT.lines().size).isAtLeast(6)
        assertThat(JsonGrammar.STRICT.trim()).isNotEmpty()
    }

    @Test
    fun `GenerationConfig defaults grammar to null`() {
        assertThat(GenerationConfig().grammar).isNull()
    }

    @Test
    fun `GenerationConfig copy can set and unset grammar`() {
        val base = GenerationConfig()
        val withGrammar = base.copy(grammar = JsonGrammar.STRICT)
        assertThat(withGrammar.grammar).isEqualTo(JsonGrammar.STRICT)

        val cleared = withGrammar.copy(grammar = null)
        assertThat(cleared.grammar).isNull()
    }
}
