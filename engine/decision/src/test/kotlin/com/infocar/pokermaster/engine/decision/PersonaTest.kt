package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import org.junit.jupiter.api.Test

class PersonaTest {

    private fun cand(action: ActionType, amount: Long = 0L, ev: Double = 0.0) =
        ActionCandidate(action, amount, ev)

    @Test fun all_8_personas_defined() {
        assertThat(Persona.entries).hasSize(8)
    }

    @Test fun granpa_is_loose_passive() {
        val p = Persona.GRANPA
        assertThat(p.looseness).isAtLeast(0.6)
        assertThat(p.aggression).isAtMost(0.4)
    }

    @Test fun tiger_is_tight_aggressive() {
        val p = Persona.TIGER
        assertThat(p.aggression).isAtLeast(0.7)
    }

    @Test fun bluffer_is_loose_aggressive() {
        val p = Persona.BLUFFER
        assertThat(p.looseness).isAtLeast(0.5)
        assertThat(p.aggression).isAtLeast(0.8)
    }

    @Test fun bluffer_boosts_raise_more_than_silent() {
        val original = listOf(
            cand(ActionType.FOLD, ev = 0.0),
            cand(ActionType.CALL, ev = 0.0),
            cand(ActionType.RAISE, amount = 200L, ev = 0.0),
        )
        val bluffer = PersonaBias.apply(Persona.BLUFFER, original)
        val silent = PersonaBias.apply(Persona.SILENT, original)
        val bluffRaise = bluffer.first { it.action == ActionType.RAISE }.ev
        val silentRaise = silent.first { it.action == ActionType.RAISE }.ev
        assertThat(bluffRaise).isGreaterThan(silentRaise)
    }

    @Test fun silent_boosts_fold_more_than_granpa() {
        val original = listOf(
            cand(ActionType.FOLD, ev = 0.0),
            cand(ActionType.CALL, ev = 0.0),
        )
        val silent = PersonaBias.apply(Persona.SILENT, original)
        val granpa = PersonaBias.apply(Persona.GRANPA, original)
        val silentFold = silent.first { it.action == ActionType.FOLD }.ev
        val granpaFold = granpa.first { it.action == ActionType.FOLD }.ev
        assertThat(silentFold).isGreaterThan(granpaFold)
    }

    @Test fun granpa_boosts_call_more_than_silent() {
        val original = listOf(
            cand(ActionType.FOLD, ev = 0.0),
            cand(ActionType.CALL, ev = 0.0),
        )
        val granpa = PersonaBias.apply(Persona.GRANPA, original)
        val silent = PersonaBias.apply(Persona.SILENT, original)
        val granpaCall = granpa.first { it.action == ActionType.CALL }.ev
        val silentCall = silent.first { it.action == ActionType.CALL }.ev
        assertThat(granpaCall).isGreaterThan(silentCall)
    }

    @Test fun bias_preserves_candidate_count() {
        val original = listOf(
            cand(ActionType.FOLD), cand(ActionType.CALL), cand(ActionType.RAISE, 100L),
        )
        val biased = PersonaBias.apply(Persona.PRO, original)
        assertThat(biased).hasSize(3)
    }

    @Test fun empty_input_returns_empty() {
        assertThat(PersonaBias.apply(Persona.PRO, emptyList())).isEmpty()
    }
}
