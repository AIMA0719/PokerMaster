package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PersonaPoolTest {

    @Test fun npc_1_returns_pro_only() {
        val r = PersonaPool.pickFor(npcCount = 1, seed = 0L)
        assertThat(r).containsExactly(Persona.PRO)
    }

    @Test fun npc_2_starts_with_pro_and_one_other() {
        val r = PersonaPool.pickFor(npcCount = 2, seed = 0L)
        assertThat(r).hasSize(2)
        assertThat(r[0]).isEqualTo(Persona.PRO)
        assertThat(r[1]).isNotEqualTo(Persona.PRO)
    }

    @Test fun npc_3_returns_three_distinct_personas_starting_with_pro() {
        val r = PersonaPool.pickFor(npcCount = 3, seed = 0L)
        assertThat(r).hasSize(3)
        assertThat(r[0]).isEqualTo(Persona.PRO)
        assertThat(r.toSet()).hasSize(3)   // unique
    }

    @Test fun npc_4_returns_four_distinct_personas_starting_with_pro() {
        val r = PersonaPool.pickFor(npcCount = 4, seed = 0L)
        assertThat(r).hasSize(4)
        assertThat(r[0]).isEqualTo(Persona.PRO)
        assertThat(r.toSet()).hasSize(4)   // unique
    }

    @Test fun same_seed_produces_same_output() {
        val a = PersonaPool.pickFor(npcCount = 4, seed = 7L)
        val b = PersonaPool.pickFor(npcCount = 4, seed = 7L)
        assertThat(a).isEqualTo(b)
    }

    @Test fun different_seed_can_produce_different_output() {
        // seed 다르면 일반적으로 다른 셔플 — 적어도 한 쌍의 seed 에서는 차이가 있어야.
        // 동일 npcCount=4 에서 seed 0,1,2,3 중 최소 두 결과가 다름을 확인.
        val results = (0L..5L).map { PersonaPool.pickFor(4, it) }
        assertThat(results.toSet().size).isAtLeast(2)
    }

    @Test fun first_entry_is_always_pro_across_seeds() {
        for (seed in 0L..10L) {
            for (n in 1..8) {
                val r = PersonaPool.pickFor(n, seed)
                assertThat(r.first()).isEqualTo(Persona.PRO)
            }
        }
    }

    @Test fun no_duplicates_for_npc_count_up_to_8() {
        for (n in 1..8) {
            val r = PersonaPool.pickFor(n, seed = 42L)
            assertThat(r).hasSize(n)
            assertThat(r.toSet()).hasSize(n)   // unique
        }
    }

    @Test fun all_8_personas_used_when_npc_count_is_8() {
        val r = PersonaPool.pickFor(npcCount = 8, seed = 123L)
        assertThat(r.toSet()).isEqualTo(Persona.entries.toSet())
    }

    @Test fun wraps_around_when_npc_count_exceeds_8() {
        val r = PersonaPool.pickFor(npcCount = 10, seed = 0L)
        assertThat(r).hasSize(10)
        // 10 슬롯에 8개 unique 는 불가 → 중복 허용
        assertThat(r.toSet().size).isLessThan(10)
        assertThat(r.first()).isEqualTo(Persona.PRO)
    }

    @Test fun zero_or_negative_npc_count_throws() {
        assertThrows<IllegalArgumentException> { PersonaPool.pickFor(0) }
        assertThrows<IllegalArgumentException> { PersonaPool.pickFor(-1) }
    }
}
