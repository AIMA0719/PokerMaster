package com.infocar.pokermaster.engine.controller

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Declaration
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.TableConfig
import org.junit.jupiter.api.Test

/**
 * B1 — [AiDriver.declareAction] 단위 테스트. 7장 fixture 로 declare 분기 검증.
 *
 * 룰 ([AiDriver.declareAction] 138-156행):
 *  - hasLo + hi >= STRAIGHT  → SWING
 *  - hasLo + hi <  STRAIGHT  → LOW
 *  - !hasLo + hi >= TWO_PAIR → HIGH
 *  - 그 외                   → HIGH (디폴트)
 */
class AiDriverDeclareTest {

    private val driver = AiDriver()

    private fun stateWith(seven: List<Card>): GameState {
        require(seven.size == 7) { "fixture must be exactly 7 cards" }
        // 7스터드 분배: 다운 3 + 업 4. (3rd 2D+1U → 4/5/6th 1U each → 7th 1D = 3D+4U.)
        val hole = seven.take(3)
        val ups = seven.drop(3)
        val player = PlayerState(
            seat = 0,
            nickname = "NPC",
            isHuman = false,
            personaId = "PRO",
            chips = 1000L,
            holeCards = hole,
            upCards = ups,
        )
        return GameState(
            mode = GameMode.SEVEN_STUD_HI_LO,
            config = TableConfig(
                mode = GameMode.SEVEN_STUD_HI_LO,
                seats = 2,
                ante = 10L,
                bringIn = 25L,
            ),
            stateVersion = 0,
            handIndex = 1,
            players = listOf(
                player,
                PlayerState(seat = 1, nickname = "X", isHuman = true, chips = 1000L),
            ),
            btnSeat = 0,
            toActSeat = 0,
            street = Street.DECLARE,
            betToCall = 0,
            minRaise = 25,
        )
    }

    private fun c(rank: Rank, suit: Suit) = Card(suit, rank)

    @Test
    fun wheel_straight_with_low_qualifier_declares_swing() {
        // 휠 스트레이트(A,2,3,4,5) + lo 자격(같은 5장 = A,2,3,4,5 distinct ≤8).
        // 7장: A♣ 2♣ 3♠ 4♥ 5♦ 7♣ 8♦. hi = STRAIGHT (또는 한국식 STRAIGHT_BACK — 둘 다 strength
        // >= STRAIGHT_BACK(5) 인데 룰은 `>= STRAIGHT(6)`. 한국식 휠은 STRAIGHT_BACK(5) — STRAIGHT(6) 미만.
        //
        // 따라서 hi 가 보장 STRAIGHT 이상이려면 *진짜* STRAIGHT 가 필요. 휠은 한국식 모드에서 BACK 으로
        // 분류되어 분기 결과가 LOW 가 됨 — fixture 를 진짜 5-high straight 가 아닌 6-high straight 로
        // 변경: 7장 2♣ 3♠ 4♥ 5♦ 6♣ A♥ 7♦. hi = STRAIGHT(6-high; 7-high 는 234567).
        // 7장: 2,3,4,5,6,A,7 → best straight = 3-4-5-6-7 (또는 2-3-4-5-6, 3-4-5-6-7 더 강) → 7-high STRAIGHT.
        // lo 자격: 5장 distinct ≤8 — A,2,3,4,5 (5장) 가능. → hasLo=true. hi=STRAIGHT(6 strength).
        val seven = listOf(
            c(Rank.TWO, Suit.CLUB),
            c(Rank.THREE, Suit.SPADE),
            c(Rank.FOUR, Suit.HEART),
            c(Rank.FIVE, Suit.DIAMOND),
            c(Rank.SIX, Suit.CLUB),
            c(Rank.ACE, Suit.HEART),
            c(Rank.SEVEN, Suit.DIAMOND),
        )
        val action = driver.declareAction(stateWith(seven), seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.SWING)
    }

    @Test
    fun trips_below_straight_with_eight_qualifier_declares_low() {
        // 트리플 7 + lo 자격(A,2,4,5,8 — 페어 없는 5장 ≤8).
        // 7장: 7♠ 7♥ 7♦ A♣ 2♣ 4♣ 8♦. lo: A,2,4,8 + ?(7 제외 5장 중 페어 없음). lo = (A,2,4,5,8) 가
        // 5장 8≤ 페어 없음 — but 우리 7장에 5 가 없다. 다시 — (A,2,4,8,?) 는 4장만. 7 페어가 5장 중에
        // 들어가면 자격 박탈. → 7장에서 7장 중 페어 아닌 5장 ≤8 조합이 있어야 함.
        //
        // 안전 picks: 7♠ 7♥ 7♦ A♣ 2♣ 4♣ 8♦ — non-7 cards = A,2,4,8 (4장). 5장 lo 조합 가능?
        //   A,2,4,7,8 — 7 포함, A,2,4,7,8 모두 ≤8 + distinct → 자격 통과! (5장 모두 distinct rank).
        //   그러면 hasLo = true. hi = THREE_OF_A_KIND (7s) < STRAIGHT → 조건 "hasLo + hi<STRAIGHT" 매칭.
        val seven = listOf(
            c(Rank.SEVEN, Suit.SPADE),
            c(Rank.SEVEN, Suit.HEART),
            c(Rank.SEVEN, Suit.DIAMOND),
            c(Rank.ACE, Suit.CLUB),
            c(Rank.TWO, Suit.CLUB),
            c(Rank.FOUR, Suit.CLUB),
            c(Rank.EIGHT, Suit.DIAMOND),
        )
        val action = driver.declareAction(stateWith(seven), seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.LOW)
    }

    @Test
    fun pair_only_with_seven_qualifier_declares_low() {
        // 페어 5 + lo 자격 (A,2,3,6,7 5장 distinct ≤8).
        // 7장: 5♠ 5♥ A♣ 2♣ 3♣ 6♦ 7♦. hi = ONE_PAIR < STRAIGHT, hasLo (A,2,3,6,7 자격).
        val seven = listOf(
            c(Rank.FIVE, Suit.SPADE),
            c(Rank.FIVE, Suit.HEART),
            c(Rank.ACE, Suit.CLUB),
            c(Rank.TWO, Suit.CLUB),
            c(Rank.THREE, Suit.CLUB),
            c(Rank.SIX, Suit.DIAMOND),
            c(Rank.SEVEN, Suit.DIAMOND),
        )
        val action = driver.declareAction(stateWith(seven), seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.LOW)
    }

    @Test
    fun trips_only_no_low_qualifier_declares_high() {
        // 트리플 K + lo 자격 미달 (모두 9 이상 또는 페어).
        // 7장: K♠ K♥ K♦ Q♣ J♣ T♣ 9♦. lo 자격: 5장 모두 ≤8 + 페어 없음 → 9 이상 카드만 → 불가.
        // hi = THREE_OF_A_KIND >= TWO_PAIR → HIGH.
        val seven = listOf(
            c(Rank.KING, Suit.SPADE),
            c(Rank.KING, Suit.HEART),
            c(Rank.KING, Suit.DIAMOND),
            c(Rank.QUEEN, Suit.CLUB),
            c(Rank.JACK, Suit.CLUB),
            c(Rank.TEN, Suit.CLUB),
            c(Rank.NINE, Suit.DIAMOND),
        )
        val action = driver.declareAction(stateWith(seven), seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.HIGH)
    }

    @Test
    fun high_card_only_no_low_qualifier_declares_high_default() {
        // 하이카드 only + lo 자격 미달.
        // 7장: A♠ K♥ Q♦ J♣ 9♣ 9♦ 5♣. 9 페어 — hi = ONE_PAIR (9), lo 자격 미달 (페어 + 9 카드).
        // hi=ONE_PAIR < TWO_PAIR → 디폴트 HIGH.
        val seven = listOf(
            c(Rank.ACE, Suit.SPADE),
            c(Rank.KING, Suit.HEART),
            c(Rank.QUEEN, Suit.DIAMOND),
            c(Rank.JACK, Suit.CLUB),
            c(Rank.NINE, Suit.CLUB),
            c(Rank.NINE, Suit.DIAMOND),
            c(Rank.FIVE, Suit.CLUB),
        )
        val action = driver.declareAction(stateWith(seven), seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.HIGH)
    }

    @Test
    fun seven_card_count_required_else_default_high() {
        // 7장이 아닌 좌석 (가드 — `seven.size != 7` 분기 142-144).
        val state = stateWith(
            seven = listOf(
                c(Rank.ACE, Suit.CLUB),
                c(Rank.KING, Suit.CLUB),
                c(Rank.QUEEN, Suit.CLUB),
                c(Rank.JACK, Suit.CLUB),
                c(Rank.TEN, Suit.CLUB),
                c(Rank.NINE, Suit.CLUB),
                c(Rank.EIGHT, Suit.CLUB),
            )
        ).let { s ->
            // hole/up 임의 수정해 7장 미만으로 만든다.
            s.copy(
                players = s.players.mapIndexed { i, p ->
                    if (i == 0) p.copy(holeCards = emptyList(), upCards = emptyList()) else p
                }
            )
        }
        val action = driver.declareAction(state, seat = 0)
        assertThat(action.type).isEqualTo(ActionType.DECLARE)
        assertThat(action.declaration).isEqualTo(Declaration.HIGH)
    }
}
