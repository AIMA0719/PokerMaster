package com.infocar.pokermaster.feature.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 커뮤니티 카드 5장 슬롯. 공개된 카드는 faceUp, 나머지는 placeholder.
 *
 * 딜링 애니메이션:
 *  - community 크기가 증가할 때마다 AnimatedVisibility 로 개별 카드가 horizontal slide-in + fade-in 으로 등장.
 *  - placeholder 슬롯은 항상 노출 (점선 테두리).
 */
@Composable
fun CardCommunityRow(
    community: List<Card>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 0 until 5) {
            val card = community.getOrNull(i)
            // M3 MVP: placeholder 또는 공개된 카드를 즉시 렌더.
            // 카드 등장 애니메이션은 M5 에서 고도화 (AnimatedVisibility Row scope 충돌 회피).
            PlayingCard(card = card, faceDown = false)
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
