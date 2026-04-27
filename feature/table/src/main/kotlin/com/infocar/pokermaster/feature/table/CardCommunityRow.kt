package com.infocar.pokermaster.feature.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.anim.DealAnimationSpec
import com.infocar.pokermaster.feature.table.anim.cardEntrance

/**
 * 커뮤니티 카드 5장 슬롯. 공개된 카드는 faceUp, 나머지는 placeholder.
 *
 * 딜링 애니메이션 (v1.1 §1.2 M3 Sprint2-D):
 *  - 플롭 3장은 i * [DealAnimationSpec.FLOP_CARD_STAGGER_MS] 간격으로 slideIn + fadeIn.
 *  - 턴/리버는 단일 카드 즉시 등장 (stagger 없음).
 *  - 같은 카드가 유지되면 remember(card) 로 transitionState 유지해서 재구성에도 재애니 없음.
 */
@Composable
fun CardCommunityRow(
    community: List<Card>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 5) {
            val card = community.getOrNull(i)
            if (card == null) {
                PlayingCard(card = null, faceDown = false, width = 32.dp, height = 46.dp)
            } else {
                val duration = when {
                    i < 3 -> DealAnimationSpec.FLOP_CARD_DURATION_MS
                    i == 3 -> DealAnimationSpec.TURN_CARD_DURATION_MS
                    else -> DealAnimationSpec.RIVER_CARD_DURATION_MS
                }
                val delay = if (i < 3) i * DealAnimationSpec.FLOP_CARD_STAGGER_MS else 0
                val easing = if (i < 3) LinearOutSlowInEasing else FastOutSlowInEasing

                val transitionState = remember(card) {
                    MutableTransitionState(false).apply { targetState = true }
                }
                val slideSpec: FiniteAnimationSpec<IntOffset> =
                    tween(durationMillis = duration, delayMillis = delay, easing = easing)
                val fadeSpec: FiniteAnimationSpec<Float> =
                    tween(durationMillis = duration, delayMillis = delay)

                AnimatedVisibility(
                    visibleState = transitionState,
                    enter = slideInHorizontally(animationSpec = slideSpec) { w -> w / 2 } +
                        fadeIn(animationSpec = fadeSpec),
                ) {
                    PlayingCard(
                        card = card,
                        faceDown = false,
                        width = 32.dp,
                        height = 46.dp,
                        modifier = Modifier.cardEntrance(durationMs = duration, delayMs = delay, key = card),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Community — preflop (empty)")
@Composable
private fun CommunityPreviewEmpty() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CardCommunityRow(community = emptyList())
        }
    }
}

@Preview(showBackground = true, name = "Community — flop")
@Composable
private fun CommunityPreviewFlop() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CardCommunityRow(
                community = listOf(
                    Card(Suit.SPADE, Rank.ACE),
                    Card(Suit.HEART, Rank.KING),
                    Card(Suit.DIAMOND, Rank.SEVEN),
                ),
            )
        }
    }
}

@Preview(showBackground = true, name = "Community — river")
@Composable
private fun CommunityPreviewRiver() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            CardCommunityRow(
                community = listOf(
                    Card(Suit.SPADE, Rank.ACE),
                    Card(Suit.HEART, Rank.KING),
                    Card(Suit.DIAMOND, Rank.SEVEN),
                    Card(Suit.CLUB, Rank.TWO),
                    Card(Suit.SPADE, Rank.TEN),
                ),
            )
        }
    }
}
