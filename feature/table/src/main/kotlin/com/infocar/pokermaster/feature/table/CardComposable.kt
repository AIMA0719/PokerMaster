package com.infocar.pokermaster.feature.table

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate as drawRotate
import androidx.compose.ui.graphics.luminance
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
import com.infocar.pokermaster.core.ui.theme.PokerColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.a11y.A11yStrings

/**
 * 고대비 카드 모드 — A11ySettings.highContrastCards 가 true 일 때 [PlayingCard] 가 더 진한
 * suit 색상 + 두꺼운 카드 테두리로 그려진다. TableScreen 에서 CompositionLocalProvider 로
 * 사용자 설정값을 주입한다. 기본값 false (저시력 미사용자 영향 없음).
 */
val LocalHighContrastCards = staticCompositionLocalOf { false }

/**
 * 모션 최소화 — A11ySettings.reduceMotion 가 true 일 때 사용자 인지 부담을 줄이기 위해
 * 큰 애니메이션(딜링 prep delay, 카드 슬라이드, deal stagger 등) 을 단축/생략한다.
 * 화면 진입 후 최소 대기는 유지하되, 시각적 motion 만 감소.
 */
val LocalReduceMotion = staticCompositionLocalOf { false }

/**
 * 액션 알림 — A11ySettings.announceActions 가 true 일 때 NPC/사람 액션 발생 시 TalkBack 으로
 * 음성 안내 (액션 라벨 변화 → View.announceForAccessibility). 시각 정보 보조 채널.
 */
val LocalAnnounceActions = staticCompositionLocalOf { false }

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
    width: Dp = 44.dp,
    height: Dp = 64.dp,
) {
    val gold = Color(0xFFD4AF37)
    // M7-C: Light 모드에서는 포커 그린, Dark 모드에서는 네이비/틸.
    val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val backBase = if (darkUi) PokerColors.CardBackBaseDark else PokerColors.CardBackBaseLight
    val backPattern = if (darkUi) PokerColors.CardBackPatternDark else PokerColors.CardBackPatternLight
    val cornerRadius = 8.dp

    val semanticsLabel: String = when {
        faceDown -> A11yStrings.hiddenCard()
        card == null -> "빈 카드 슬롯"
        else -> A11yStrings.card(card)
    }

    // size 는 파라미터로 받아 caller 가 정확히 제어. modifier 는 chaining 위치에 무관하게 동작.
    val baseModifier = modifier
        .size(width = width, height = height)
        .then(if (highlight) Modifier.scale(1.05f) else Modifier)
        .semantics { contentDescription = semanticsLabel }

    // M7: 카드 flip 애니 — faceDown 변경 시 Y축 180° 회전. 0~90° 면 앞면, 90~180° 면 뒷면 표시.
    //  reduceMotion 시 duration 0 으로 즉시 전환.
    val flipDuration = if (LocalReduceMotion.current) 0 else 400
    val rotation by animateFloatAsState(
        targetValue = if (faceDown) 180f else 0f,
        animationSpec = tween(flipDuration, easing = FastOutSlowInEasing),
        label = "card-flip",
    )
    val showFront = rotation < 90f
    val flipModifier = baseModifier.graphicsLayer {
        rotationY = rotation
        cameraDistance = 12f * density
    }

    when {
        !showFront -> {
            // 뒷면. 부모 layer 가 이미 180° 부근으로 회전했으므로 뒷면 그래픽은 거울 반전 상태.
            // 자식에 graphicsLayer { rotationY = 180f } 를 추가해 보정 — flipped(180) 시 텍스트/패턴이
            // 정상으로 보임.
            Box(modifier = flipModifier) {
                CardBack(
                    modifier = Modifier.fillMaxSize().graphicsLayer { rotationY = 180f },
                    cornerRadius = cornerRadius,
                    gold = gold,
                    navy = backBase,
                    teal = backPattern,
                    highlight = highlight,
                )
            }
        }
        card == null -> CardPlaceholder(
            modifier = flipModifier,
            cornerRadius = cornerRadius,
            highlight = highlight,
            gold = gold,
        )
        else -> CardFace(
            card = card,
            modifier = flipModifier,
            cornerRadius = cornerRadius,
            highlight = highlight,
            gold = gold,
            width = width,
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
    width: Dp,
) {
    val highContrast = LocalHighContrastCards.current
    val suitColor = when (card.suit) {
        // 고대비: 빨강을 더 짙은 톤(B30000) 으로 — 다크모드에서도 잘 보임. 검정은 그대로.
        Suit.HEART, Suit.DIAMOND -> if (highContrast) Color(0xFFB30000) else Color(0xFFC8372D)
        Suit.SPADE, Suit.CLUB -> Color.Black
    }
    // 폰트 사이즈 비례 — 44dp 기준 16sp/10sp 가 미니카드(22dp) 에선 8sp/5sp 로 축소.
    val rankSp = (width.value * 0.36f).coerceAtLeast(8f)
    val suitSp = (width.value * 0.22f).coerceAtLeast(5f)
    val centerSp = (width.value * 0.6f).coerceAtLeast(12f)
    val surface = MaterialTheme.colorScheme.surface
    val borderModifier = if (highlight) {
        Modifier.border(BorderStroke(2.dp, gold), RoundedCornerShape(cornerRadius))
    } else {
        // 고대비: 테두리 두께/대비 강화 (0.5dp@25% → 1.5dp@90%).
        val (bw, ba) = if (highContrast) 1.5.dp to 0.9f else 0.5.dp to 0.25f
        Modifier.border(
            BorderStroke(bw, Color.Black.copy(alpha = ba)),
            RoundedCornerShape(cornerRadius),
        )
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(surface)
            .then(borderModifier)
            .padding(horizontal = 2.dp, vertical = 1.dp),
    ) {
        // 좌상단 코너
        Column(
            modifier = Modifier.align(Alignment.TopStart),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            Text(
                text = card.rank.displayShort(),
                color = suitColor,
                fontSize = rankSp.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = rankSp.sp,
            )
            Text(
                text = card.suit.symbol,
                color = suitColor,
                fontSize = suitSp.sp,
                lineHeight = suitSp.sp,
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
                    text = card.rank.displayShort(),
                    color = suitColor,
                    fontSize = rankSp.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = rankSp.sp,
                )
                Text(
                    text = card.suit.symbol,
                    color = suitColor,
                    fontSize = suitSp.sp,
                    lineHeight = suitSp.sp,
                )
            }
        }

        // 중앙 심볼 (은은하게)
        Text(
            text = card.suit.symbol,
            color = suitColor.copy(alpha = 0.18f),
            fontSize = centerSp.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.align(Alignment.Center),
        )
    }
}

private fun Rank.displayShort(): String = if (this == Rank.TEN) "10" else short

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
