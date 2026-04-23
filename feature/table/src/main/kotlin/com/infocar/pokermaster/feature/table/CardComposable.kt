package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.model.symbol
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 플레잉 카드 컴포넌트. v1.1 §4.3 — 저작권 회피를 위해 자체 일러스트 없이
 * Compose 그래픽 (Text + Canvas) 만으로 카드를 그린다.
 *
 * 상태:
 *  - faceDown=true → 뒷면 (진한 청록 + 금색 테두리 + 대각 패턴).
 *  - faceDown=false, card != null → 앞면 (rank + suit 좌상/우하 2코너).
 *  - card == null (faceDown=false) → placeholder (점선 테두리 + 반투명 슬롯).
 *  - highlight=true → 2dp 골드 테두리 + scale 1.05.
 *
 * 크기는 default 44x64dp, 호출자가 Modifier.size 로 override 가능.
 * (Compose 의 Modifier chain 은 먼저 선언된 제약이 우선하므로, default size 를
 *  먼저 적용하되 호출자 modifier 를 나중에 chain 하여 override 되도록 한다.)
 */
@Composable
fun PlayingCard(
    card: Card?,
    faceDown: Boolean = false,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    val gold = Color(0xFFD4AF37)
    val backNavy = Color(0xFF123244)
    val backTeal = Color(0xFF1B5566)
    val cornerRadius = 8.dp

    val semanticsLabel: String = when {
        faceDown -> "뒷면 카드"
        card == null -> "빈 카드 슬롯"
        else -> "${card.rank.short}${card.suit.symbol}"
    }

    // default size 를 먼저 선언한 뒤 호출자 modifier 로 override 가능하도록 체이닝.
    val baseModifier = Modifier
        .size(width = 44.dp, height = 64.dp)
        .then(modifier)
        .then(if (highlight) Modifier.scale(1.05f) else Modifier)
        .semantics { contentDescription = semanticsLabel }

    when {
        faceDown -> CardBack(
            modifier = baseModifier,
            cornerRadius = cornerRadius,
            gold = gold,
            navy = backNavy,
            teal = backTeal,
            highlight = highlight,
        )
        card == null -> CardPlaceholder(
            modifier = baseModifier,
            cornerRadius = cornerRadius,
            highlight = highlight,
            gold = gold,
        )
        else -> CardFace(
            card = card,
            modifier = baseModifier,
            cornerRadius = cornerRadius,
            highlight = highlight,
            gold = gold,
        )
    }
}

@Composable
private fun CardFace(
    card: Card,
    modifier: Modifier,
    cornerRadius: Dp,
    highlight: Boolean,
    gold: Color,
) {
    val suitColor = when (card.suit) {
        Suit.HEART, Suit.DIAMOND -> Color(0xFFC8372D)
        Suit.SPADE, Suit.CLUB -> Color.Black
    }
    val surface = MaterialTheme.colorScheme.surface
    val borderModifier = if (highlight) {
        Modifier.border(BorderStroke(2.dp, gold), RoundedCornerShape(cornerRadius))
    } else {
        Modifier.border(
            BorderStroke(0.5.dp, Color.Black.copy(alpha = 0.25f)),
            RoundedCornerShape(cornerRadius),
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(surface)
            .then(borderModifier)
            .padding(horizontal = 3.dp, vertical = 2.dp),
    ) {
        // 좌상단 코너
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = card.rank.short,
                color = suitColor,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 16.sp,
            )
            Text(
                text = card.suit.symbol,
                color = suitColor,
                fontSize = 10.sp,
                lineHeight = 10.sp,
            )
        }

        // 우하단 코너 (180° 회전)
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .rotate(180f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = card.rank.short,
                    color = suitColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 16.sp,
                )
                Text(
                    text = card.suit.symbol,
                    color = suitColor,
                    fontSize = 10.sp,
                    lineHeight = 10.sp,
                )
            }
        }

        // 중앙 심볼 (은은하게)
        Text(
            text = card.suit.symbol,
            color = suitColor.copy(alpha = 0.18f),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

@Composable
private fun CardBack(
    modifier: Modifier,
    cornerRadius: Dp,
    gold: Color,
    navy: Color,
    teal: Color,
    highlight: Boolean,
) {
    val borderStroke = if (highlight) {
        BorderStroke(2.dp, gold)
    } else {
        BorderStroke(1.dp, gold)
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(navy)
            .border(borderStroke, RoundedCornerShape(cornerRadius))
            .padding(3.dp),
    ) {
        // 대각 패턴 Canvas
        Canvas(modifier = Modifier.fillMaxSize()) {
            val step = 6f
            val strokeWidth = 1f
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = teal,
                    start = Offset(x, 0f),
                    end = Offset(x + size.height, size.height),
                    strokeWidth = strokeWidth,
                )
                x += step
            }
            // 중앙 다이아몬드 악센트 — 사각형을 45도로 회전하여 그리기.
            drawRotate(
                degrees = 45f,
                pivot = Offset(size.width / 2f, size.height / 2f),
            ) {
                val w = size.width * 0.35f
                val h = size.height * 0.22f
                drawRect(
                    color = gold.copy(alpha = 0.85f),
                    topLeft = Offset(
                        (size.width - w) / 2f,
                        (size.height - h) / 2f,
                    ),
                    size = Size(w, h),
                    style = Stroke(width = 1.5f),
                )
            }
        }
    }
}

@Composable
private fun CardPlaceholder(
    modifier: Modifier,
    cornerRadius: Dp,
    highlight: Boolean,
    gold: Color,
) {
    val outline = if (highlight) gold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
    val fill = MaterialTheme.colorScheme.surface.copy(alpha = 0.25f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(fill),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = Stroke(
                width = 1.5f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
            )
            drawRoundRect(
                color = outline,
                style = stroke,
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Ace of Spades")
@Composable
private fun PlayingCardPreviewAceSpade() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PlayingCard(card = Card(Suit.SPADE, Rank.ACE))
        }
    }
}

@Preview(showBackground = true, name = "King of Hearts highlighted")
@Composable
private fun PlayingCardPreviewHighlight() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            PlayingCard(
                card = Card(Suit.HEART, Rank.KING),
                highlight = true,
            )
        }
    }
}

@Preview(showBackground = true, name = "Face down")
@Composable
private fun PlayingCardPreviewFaceDown() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlayingCard(card = null, faceDown = true)
                PlayingCard(card = null, faceDown = false)
            }
        }
    }
}
