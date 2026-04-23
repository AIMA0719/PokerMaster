package com.infocar.pokermaster.feature.table

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 2~8인 홀덤 테이블 좌석 레이아웃.
 *
 * 배치 규칙:
 *  - humanSeat 은 항상 90° (bottom-center) 고정.
 *  - 그 외 좌석은 players.sortedBy { seat } 기준 human 다음부터 시계방향으로 균등 각도 분할.
 *  - 타원 반경 = min(maxWidth, maxHeight) * 0.38 (가로 방향에 약간의 가중치).
 *
 * SB/BB 계산 (players.sortedBy { seat } 기반, btn+1/+2 인덱스):
 *  - 2명(heads-up): BTN = SB, 상대 = BB.
 *  - 3명 이상: BTN 다음(시계방향)이 SB, 그 다음이 BB.
 */
@Composable
fun SeatLayout(
    players: List<PlayerState>,
    btnSeat: Int,
    toActSeat: Int?,
    humanSeat: Int,
    modifier: Modifier = Modifier,
) {
    if (players.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    // 좌석 정렬 및 사람-기준 시계방향 순회 리스트 구성.
    val ordered = remember(players, humanSeat) {
        orderClockwiseFromHuman(players, humanSeat)
    }

    // SB/BB 인덱스 계산 — 원본 sortedBy { seat } 순서 기준.
    val sortedBySeat = remember(players) { players.sortedBy { it.seat } }
    val (sbSeat, bbSeat) = remember(sortedBySeat, btnSeat) {
        computeBlinds(sortedBySeat, btnSeat)
    }

    BoxWithConstraints(modifier = modifier) {
        val minDim: Dp = min(maxWidth.value, maxHeight.value).dp
        val radiusX: Dp = (maxWidth.value * 0.40f).dp
        val radiusY: Dp = (maxHeight.value * 0.36f).dp
        val count = ordered.size
        // 90°(아래) 에 human. 나머지는 시계방향으로 index = 1..n-1 에 할당.
        // 각 index 의 각도 = 90° + (i / n) * 360°.
        val angles = remember(count) {
            List(count) { i -> 90.0 + (i.toDouble() / count.toDouble()) * 360.0 }
        }

        ordered.forEachIndexed { i, player ->
            val angleRad = Math.toRadians(angles[i])
            val dx: Dp = (radiusX.value * cos(angleRad).toFloat()).dp
            val dy: Dp = (radiusY.value * sin(angleRad).toFloat()).dp
            PlayerSeat(
                player = player,
                isBtn = player.seat == btnSeat,
                isSb = sbSeat != null && player.seat == sbSeat,
                isBb = bbSeat != null && player.seat == bbSeat,
                isToAct = toActSeat != null && player.seat == toActSeat,
                isHuman = player.seat == humanSeat,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dx, y = dy),
            )
        }

        // minDim 은 여백 확보용 참조 — 사용하지 않더라도 측정 의도를 명시.
        @Suppress("UNUSED_EXPRESSION") minDim
    }
}

// ---------------------------------------------------------------------------
// 내부 배치 도우미
// ---------------------------------------------------------------------------

/**
 * players 를 seat 오름차순으로 정렬한 뒤, humanSeat 을 맨 앞으로 두고
 * 그 다음부터 시계방향(= seat 순환 index +1)으로 나열한다.
 */
private fun orderClockwiseFromHuman(
    players: List<PlayerState>,
    humanSeat: Int,
): List<PlayerState> {
    val sorted = players.sortedBy { it.seat }
    val startIdx = sorted.indexOfFirst { it.seat == humanSeat }.let { if (it < 0) 0 else it }
    return List(sorted.size) { i -> sorted[(startIdx + i) % sorted.size] }
}

/**
 * BTN 좌석을 기준으로 SB/BB 좌석 번호 계산.
 *  - 2명: (BTN, 상대) = (SB, BB)
 *  - 3명+: (BTN+1, BTN+2) = (SB, BB)
 */
private fun computeBlinds(
    sortedBySeat: List<PlayerState>,
    btnSeat: Int,
): Pair<Int?, Int?> {
    if (sortedBySeat.isEmpty()) return null to null
    val n = sortedBySeat.size
    val btnIdx = sortedBySeat.indexOfFirst { it.seat == btnSeat }
    if (btnIdx < 0) return null to null
    return when (n) {
        1 -> sortedBySeat[btnIdx].seat to null
        2 -> sortedBySeat[btnIdx].seat to sortedBySeat[(btnIdx + 1) % n].seat
        else -> sortedBySeat[(btnIdx + 1) % n].seat to sortedBySeat[(btnIdx + 2) % n].seat
    }
}

// ---------------------------------------------------------------------------
// PlayerSeat
// ---------------------------------------------------------------------------

private val AccentGold = Color(0xFFD4AF37)
private val AllInRed = Color(0xFFD13B3B)

@Composable
private fun PlayerSeat(
    player: PlayerState,
    isBtn: Boolean,
    isSb: Boolean,
    isBb: Boolean,
    isToAct: Boolean,
    isHuman: Boolean,
    modifier: Modifier = Modifier,
) {
    // to-act pulse — 외곽선 두께를 2dp ↔ 3.5dp 사이에서 왕복.
    val borderWidth: Dp = if (isToAct) {
        val transition = rememberInfiniteTransition(label = "toAct")
        val animated by transition.animateFloat(
            initialValue = 2f,
            targetValue = 3.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "toActBorder",
        )
        animated.dp
    } else {
        0.dp
    }

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            // 홀카드 (아바타 위)
            HoleCardsRow(
                holeCards = player.holeCards,
                faceDown = !isHuman,
            )

            // 아바타 (원형) + to-act 외곽선
            Box(contentAlignment = Alignment.Center) {
                val avatarModifier = if (isToAct) {
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                        .border(BorderStroke(borderWidth, AccentGold), CircleShape)
                } else {
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer)
                }
                Box(
                    modifier = avatarModifier,
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = player.nickname.take(1).ifBlank { "?" },
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // 폴드 오버레이
                if (player.folded) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(id = R.string.badge_fold),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // YOU 배지 (우상단)
                if (isHuman) {
                    Surface(
                        modifier = Modifier.align(Alignment.TopEnd),
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.primary,
                    ) {
                        Text(
                            text = stringResource(id = R.string.badge_you),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                // BTN/SB/BB 마커 (좌하단)
                val markerLabel = when {
                    isBtn -> stringResource(id = R.string.seat_btn)
                    isSb -> stringResource(id = R.string.seat_sb)
                    isBb -> stringResource(id = R.string.seat_bb)
                    else -> null
                }
                if (markerLabel != null) {
                    Surface(
                        modifier = Modifier.align(Alignment.BottomStart),
                        shape = CircleShape,
                        color = if (isBtn) AccentGold else MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Text(
                            text = markerLabel,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBtn) Color.Black
                            else MaterialTheme.colorScheme.onTertiaryContainer,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            // 닉네임
            Text(
                text = player.nickname,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Medium,
            )

            // 칩 + all-in 배지
            if (player.allIn) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AllInRed,
                ) {
                    Text(
                        text = stringResource(id = R.string.badge_all_in),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else {
                Text(
                    text = ChipFormat.format(player.chips),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                )
            }
        }
    }
}

@Composable
private fun HoleCardsRow(
    holeCards: List<Card>,
    faceDown: Boolean,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // 홀덤 2장 기준 — 부족하면 빈 슬롯.
        for (i in 0 until 2) {
            val card = holeCards.getOrNull(i)
            if (card == null && holeCards.isEmpty()) {
                EmptyCardSlot()
            } else {
                PlayingCard(
                    card = card,
                    faceDown = faceDown,
                    modifier = Modifier
                        .width(26.dp)
                        .height(36.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyCardSlot() {
    Box(
        modifier = Modifier
            .width(26.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
    )
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 560, widthDp = 360)
@Composable
private fun SeatLayoutPreview() {
    val players = listOf(
        PlayerState(
            seat = 0,
            nickname = "나",
            isHuman = true,
            chips = 9_500L,
            holeCards = listOf(
                Card(Suit.SPADE, Rank.ACE),
                Card(Suit.HEART, Rank.KING),
            ),
        ),
        PlayerState(
            seat = 1,
            nickname = "프로",
            isHuman = false,
            chips = 12_300L,
            holeCards = listOf(
                Card(Suit.CLUB, Rank.QUEEN),
                Card(Suit.DIAMOND, Rank.QUEEN),
            ),
        ),
        PlayerState(
            seat = 2,
            nickname = "루즈",
            isHuman = false,
            chips = 0L,
            allIn = true,
            holeCards = listOf(
                Card(Suit.HEART, Rank.TEN),
                Card(Suit.HEART, Rank.JACK),
            ),
        ),
        PlayerState(
            seat = 3,
            nickname = "탭",
            isHuman = false,
            chips = 4_200L,
            folded = true,
        ),
    )
    PokerMasterTheme {
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            SeatLayout(
                players = players,
                btnSeat = 0,
                toActSeat = 1,
                humanSeat = 0,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// remember 누락 보완 — 파일 전역 import.
// (Compose 의 remember 는 runtime 에서 가져온다.)
// ---------------------------------------------------------------------------
