package com.infocar.pokermaster.feature.table

import androidx.annotation.DrawableRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * Opt-in 이미지 카드 모드 — `LocalUseImageCards.current == true` 일 때 [PlayingCard] 가
 * Canvas/Text 자체 렌더 대신 [ImageCard] 로 PNG 자산을 그린다. 기본값 false → 기존 경로 유지.
 *
 * 자산 출처: Kenney boardgame-pack (CC0). `res/drawable-nodpi/card_*.png` 53장 (52 face + 1 back).
 */
val LocalUseImageCards = staticCompositionLocalOf { false }

/**
 * PNG 자산 기반 플레잉 카드. drawable-nodpi 의 `card_<rank>_<suit>.png` 또는 `card_back.png` 를
 * Image 로 렌더한다.
 *
 *  - faceDown=true → card_back
 *  - faceDown=false, card != null → 해당 rank/suit
 *  - card == null → CardPlaceholder (이미지 모드에서도 점선 슬롯 그대로)
 *
 * Canvas 경로 [PlayingCard] 와 동일한 size/highlight/semantics 동작을 유지한다.
 */
@Composable
fun ImageCard(
    card: Card?,
    faceDown: Boolean = false,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
    width: Dp = 44.dp,
    height: Dp = 64.dp,
    contentDescription: String? = null,
) {
    val gold = Color(0xFFD4AF37)
    val cornerRadius = 8.dp

    val baseModifier = modifier
        .size(width = width, height = height)
        .then(if (highlight) Modifier.scale(1.05f) else Modifier)

    if (card == null && !faceDown) {
        // Placeholder: 이미지 모드에서도 카드가 없는 슬롯은 동일한 점선 표현.
        CardPlaceholder(
            modifier = baseModifier,
            cornerRadius = cornerRadius,
            highlight = highlight,
            gold = gold,
        )
        return
    }

    @DrawableRes val resId: Int = if (faceDown || card == null) {
        R.drawable.card_back
    } else {
        cardDrawableRes(card)
    }

    val borderStroke = if (highlight) {
        BorderStroke(2.dp, gold)
    } else {
        BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.25f))
    }

    Box(
        modifier = baseModifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surface)
            .border(borderStroke, RoundedCornerShape(cornerRadius)),
    ) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            // PNG aspect (140:190) 와 표시 aspect (44:64 ≈ 11:16) 가 살짝 달라
            // Crop 시 외곽이 약간 잘릴 수 있어 Fit 으로 letterbox.
            contentScale = ContentScale.Fit,
        )
    }
}

/**
 * Card → drawable resId 매핑. 누락 시 컴파일 시점에 R.drawable.* 미해결로 에러.
 * (52장 PNG 가 res/drawable-nodpi 에 모두 있어야 한다.)
 */
@DrawableRes
private fun cardDrawableRes(card: Card): Int = when (card.suit) {
    Suit.SPADE -> spadeRes(card.rank)
    Suit.HEART -> heartRes(card.rank)
    Suit.DIAMOND -> diamondRes(card.rank)
    Suit.CLUB -> clubRes(card.rank)
}

@DrawableRes
private fun spadeRes(rank: Rank): Int = when (rank) {
    Rank.TWO -> R.drawable.card_2_spades
    Rank.THREE -> R.drawable.card_3_spades
    Rank.FOUR -> R.drawable.card_4_spades
    Rank.FIVE -> R.drawable.card_5_spades
    Rank.SIX -> R.drawable.card_6_spades
    Rank.SEVEN -> R.drawable.card_7_spades
    Rank.EIGHT -> R.drawable.card_8_spades
    Rank.NINE -> R.drawable.card_9_spades
    Rank.TEN -> R.drawable.card_10_spades
    Rank.JACK -> R.drawable.card_j_spades
    Rank.QUEEN -> R.drawable.card_q_spades
    Rank.KING -> R.drawable.card_k_spades
    Rank.ACE -> R.drawable.card_a_spades
}

@DrawableRes
private fun heartRes(rank: Rank): Int = when (rank) {
    Rank.TWO -> R.drawable.card_2_hearts
    Rank.THREE -> R.drawable.card_3_hearts
    Rank.FOUR -> R.drawable.card_4_hearts
    Rank.FIVE -> R.drawable.card_5_hearts
    Rank.SIX -> R.drawable.card_6_hearts
    Rank.SEVEN -> R.drawable.card_7_hearts
    Rank.EIGHT -> R.drawable.card_8_hearts
    Rank.NINE -> R.drawable.card_9_hearts
    Rank.TEN -> R.drawable.card_10_hearts
    Rank.JACK -> R.drawable.card_j_hearts
    Rank.QUEEN -> R.drawable.card_q_hearts
    Rank.KING -> R.drawable.card_k_hearts
    Rank.ACE -> R.drawable.card_a_hearts
}

@DrawableRes
private fun diamondRes(rank: Rank): Int = when (rank) {
    Rank.TWO -> R.drawable.card_2_diamonds
    Rank.THREE -> R.drawable.card_3_diamonds
    Rank.FOUR -> R.drawable.card_4_diamonds
    Rank.FIVE -> R.drawable.card_5_diamonds
    Rank.SIX -> R.drawable.card_6_diamonds
    Rank.SEVEN -> R.drawable.card_7_diamonds
    Rank.EIGHT -> R.drawable.card_8_diamonds
    Rank.NINE -> R.drawable.card_9_diamonds
    Rank.TEN -> R.drawable.card_10_diamonds
    Rank.JACK -> R.drawable.card_j_diamonds
    Rank.QUEEN -> R.drawable.card_q_diamonds
    Rank.KING -> R.drawable.card_k_diamonds
    Rank.ACE -> R.drawable.card_a_diamonds
}

@DrawableRes
private fun clubRes(rank: Rank): Int = when (rank) {
    Rank.TWO -> R.drawable.card_2_clubs
    Rank.THREE -> R.drawable.card_3_clubs
    Rank.FOUR -> R.drawable.card_4_clubs
    Rank.FIVE -> R.drawable.card_5_clubs
    Rank.SIX -> R.drawable.card_6_clubs
    Rank.SEVEN -> R.drawable.card_7_clubs
    Rank.EIGHT -> R.drawable.card_8_clubs
    Rank.NINE -> R.drawable.card_9_clubs
    Rank.TEN -> R.drawable.card_10_clubs
    Rank.JACK -> R.drawable.card_j_clubs
    Rank.QUEEN -> R.drawable.card_q_clubs
    Rank.KING -> R.drawable.card_k_clubs
    Rank.ACE -> R.drawable.card_a_clubs
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Image Ace of Spades")
@Composable
private fun ImageCardPreviewAceSpade() {
    PokerMasterTheme {
        Box(modifier = Modifier.size(64.dp)) {
            ImageCard(card = Card(Suit.SPADE, Rank.ACE), highlight = true)
        }
    }
}

@Preview(showBackground = true, name = "Image face down")
@Composable
private fun ImageCardPreviewBack() {
    PokerMasterTheme {
        Box(modifier = Modifier.size(64.dp)) {
            ImageCard(card = null, faceDown = true)
        }
    }
}
