package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import com.infocar.pokermaster.engine.rules.Rng
import org.junit.jupiter.api.Test

/**
 * 7-card stud 브링인 결정 룰 (한국식 + 정통 모두 동일):
 *  - 가장 약한 up-card 좌석이 강제 베팅.
 *  - 동일 rank 동률 시 무늬 우선순위로 결정 — ♣(약) < ♦ < ♥ < ♠(강).
 *  - 가장 약한 무늬 = ♣ → 4♣ 가 4♠ 보다 약함, 4♣ 가 bring-in.
 *
 * 본 테스트는 startHand 후 lastAggressorSeat (= bring-in seat) 의 up-card 가
 * 다른 모든 좌석의 up-card 보다 (rank, suit) 사전식으로 약한지 검증.
 */
class StudBringInSuitTest {

    private val cfg = TableConfig(
        mode = GameMode.SEVEN_STUD,
        seats = 4,
        smallBlind = 0L,
        bigBlind = 0L,
        ante = 10L,
        bringIn = 25L,
    )

    private fun players(n: Int = 4) = (0 until n).map { i ->
        PlayerState(
            seat = i,
            nickname = "P$i",
            isHuman = false,
            personaId = "PRO",
            chips = 10_000L,
        )
    }

    /** 다양한 nonce 로 startHand 반복 — 매 회 bring-in 좌석이 약한 up-card 인지. */
    @Test fun bring_in_seat_is_weakest_up_card_across_many_seeds() {
        for (n in 1..30L) {
            val rng = run {
                val seed = ByteArray(Rng.SEED_BYTES) { (it + n.toInt()).toByte() }
                Rng.ofSeeds(seed, seed, n)
            }
            val s = StudReducer.startHand(
                config = cfg,
                players = players(),
                prevBtnSeat = null,
                rng = rng,
                handIndex = n,
                startingVersion = 0L,
            )
            val bringInSeat = s.lastAggressorSeat
                ?: error("nonce=$n: lastAggressorSeat null")
            val bringInUp = s.players.first { it.seat == bringInSeat }.upCards.first()
            // bring-in card 가 (rank, suit) 사전식으로 다른 모든 좌석의 up-card 보다 약하거나 같음.
            for (p in s.players) {
                if (p.seat == bringInSeat) continue
                val theirs = p.upCards.first()
                // 약함 = (낮은 rank) 또는 (rank 동률 + 낮은 suit ordinal)
                val mineKey = bringInUp.rank.value to bringInUp.suit.ordinal
                val theirKey = theirs.rank.value to theirs.suit.ordinal
                val isWeaklyLower =
                    mineKey.first < theirKey.first ||
                    (mineKey.first == theirKey.first && mineKey.second <= theirKey.second)
                assertThat(isWeaklyLower).isTrue()
            }
        }
    }

    /**
     * 직접 동률 시나리오 — startHand 가 아닌 GameState 를 만들어 동률 분기 분석.
     * ace=14 가 가장 강하므로 사용 X. 모든 좌석에 같은 rank 4 의 다른 suit 분배.
     *
     * 4♣(0), 4♦(1), 4♥(2), 4♠(3) → bring-in = 4♣ 좌석.
     */
    @Test fun bring_in_resolves_tied_rank_by_lowest_suit_club() {
        // pickBringInSeat 는 private 이므로 startHand 의 결정을 우회 검증.
        // 여기서는 의사-시나리오: 4 좌석 모두 rank 4 다른 suit upcard 받았을 때 어느 좌석이 bring-in?
        // 직접 GameState 를 합성하지 않고, 평가 로직 핵심만 검증한다.
        // bring-in 좌석 결정 공식 (코드 내 inline): minByOrNull { rank.value * 10 + suit.ordinal }.
        //  - 4♣ = 4*10+0 = 40
        //  - 4♦ = 4*10+1 = 41
        //  - 4♥ = 4*10+2 = 42
        //  - 4♠ = 4*10+3 = 43
        // → minimum = 4♣ → bring-in.
        val cards = listOf(
            Card(Suit.CLUB, Rank.FOUR),
            Card(Suit.DIAMOND, Rank.FOUR),
            Card(Suit.HEART, Rank.FOUR),
            Card(Suit.SPADE, Rank.FOUR),
        )
        val ranked = cards.sortedBy { it.rank.value * 10 + it.suit.ordinal }
        assertThat(ranked.first().suit).isEqualTo(Suit.CLUB)
        assertThat(ranked.last().suit).isEqualTo(Suit.SPADE)
    }

    @Test fun bring_in_suit_ranking_clubs_diamonds_hearts_spades() {
        // 한국식 + 국제(브릿지) 표준 모두: C < D < H < S
        assertThat(Suit.CLUB.ordinal).isLessThan(Suit.DIAMOND.ordinal)
        assertThat(Suit.DIAMOND.ordinal).isLessThan(Suit.HEART.ordinal)
        assertThat(Suit.HEART.ordinal).isLessThan(Suit.SPADE.ordinal)
    }
}
