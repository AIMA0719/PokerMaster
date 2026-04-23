package com.infocar.pokermaster.engine.decision

import com.google.common.truth.Truth.assertThat
import org.junit.jupiter.api.Test

class PreflopChartTest {

    @Test fun aces_are_premium() {
        assertThat(PreflopChart.classify(hand("AS", "AH"))).isEqualTo(PreflopGroup.PREMIUM)
    }

    @Test fun ak_suited_is_premium() {
        assertThat(PreflopChart.classify(hand("AS", "KS"))).isEqualTo(PreflopGroup.PREMIUM)
    }

    @Test fun ak_offsuit_is_premium() {
        assertThat(PreflopChart.classify(hand("AS", "KH"))).isEqualTo(PreflopGroup.PREMIUM)
    }

    @Test fun pocket_eights_strong() {
        assertThat(PreflopChart.classify(hand("8S", "8H"))).isEqualTo(PreflopGroup.STRONG)
    }

    @Test fun pocket_twos_speculative() {
        assertThat(PreflopChart.classify(hand("2S", "2H"))).isEqualTo(PreflopGroup.SPECULATIVE)
    }

    @Test fun seventwo_offsuit_is_trash() {
        assertThat(PreflopChart.classify(hand("7S", "2H"))).isEqualTo(PreflopGroup.TRASH)
    }

    @Test fun ace_two_suited_is_speculative() {
        assertThat(PreflopChart.classify(hand("AS", "2S"))).isEqualTo(PreflopGroup.SPECULATIVE)
    }

    @Test fun jack_ten_suited_is_speculative_or_strong() {
        val g = PreflopChart.classify(hand("JS", "TS"))
        assertThat(g).isAnyOf(PreflopGroup.SPECULATIVE, PreflopGroup.STRONG)
    }

    @Test fun all_169_hands_classified() {
        val all = PreflopChart.allHandGroups()
        assertThat(all).hasSize(169)
        // PREMIUM 카운트는 너무 많아서는 안 됨 (탑 6 정도)
        val premium = all.values.count { it == PreflopGroup.PREMIUM }
        assertThat(premium).isAtLeast(5)
        assertThat(premium).isAtMost(15)
    }

    @Test fun base_action_per_group() {
        assertThat(PreflopGroup.PREMIUM.baseAction).isEqualTo(PreflopAction.RAISE)
        assertThat(PreflopGroup.STRONG.baseAction).isEqualTo(PreflopAction.RAISE)
        assertThat(PreflopGroup.SPECULATIVE.baseAction).isEqualTo(PreflopAction.CALL)
        assertThat(PreflopGroup.TRASH.baseAction).isEqualTo(PreflopAction.FOLD)
    }

    @Test fun rejects_non_two_card_hole() {
        val ex = runCatching { PreflopChart.classify(hand("AS")) }.exceptionOrNull()
        assertThat(ex).isInstanceOf(IllegalArgumentException::class.java)
    }
}
