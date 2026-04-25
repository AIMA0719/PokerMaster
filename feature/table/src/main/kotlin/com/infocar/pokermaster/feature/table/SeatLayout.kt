package com.infocar.pokermaster.feature.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.a11y.A11yStrings
import com.infocar.pokermaster.feature.table.anim.DealAnimationSpec
import com.infocar.pokermaster.feature.table.anim.pulseFloat
import kotlin.math.cos
import kotlin.math.sin

/**
 * 한게임 풍 가로 시트 레이아웃 — v2.
 *
 * 시트 디자인:
 *  - 가로 직사각형 박스 [아바타 ◯] [닉/칩] [미니 홀카드 2장]
 *  - 본인 시트는 라임 보더 + YOU 배지 + 미니카드 faceUp
 *  - 차례 시트는 라임 보더 펄스, 승자는 골드 보더 펄스
 *  - 폴드 시트는 어두운 오버레이 + "FOLD" 라벨
 *  - BTN/SB/BB 마커는 시트 박스 우하단 외부 디스크
 *  - 베팅 칩 / 액션 라벨은 시트 위 floating
 *
 * 배치:
 *  - humanSeat 은 90° (bottom-center) 고정
 *  - 그 외는 시계방향 균등 분할
 *  - 타원 반경 = 가로화면 기준 (radiusX = w*0.40, radiusY = h*0.34)
 */
@Composable
fun SeatLayout(
    players: List<PlayerState>,
    btnSeat: Int,
    toActSeat: Int?,
    humanSeat: Int,
    modifier: Modifier = Modifier,
    isShowdown: Boolean = false,
    winnerSeats: Set<Int> = emptySet(),
    lastActionBySeat: Map<Int, String> = emptyMap(),
    seatBadges: Map<Int, String> = emptyMap(),
) {
    if (players.isEmpty()) {
        Box(modifier = modifier)
        return
    }

    val ordered = remember(players, humanSeat) {
        orderClockwiseFromHuman(players, humanSeat)
    }
    val sortedBySeat = remember(players) { players.sortedBy { it.seat } }
    // Codex P2: 다인 게임에서 한 명 파산(chips==0) 후에도 블라인드는 활성 좌석에서만 포스트되므로,
    // SB/BB 마커도 활성 좌석 기준으로 계산해야 한다. 그렇지 않으면 파산 시트에 SB/BB 가 잘못 찍힘.
    val activeBySeat = remember(players) { players.filter { it.chips > 0 }.sortedBy { it.seat } }
    val (sbSeat, bbSeat) = remember(activeBySeat, btnSeat) {
        computeBlinds(activeBySeat, btnSeat)
    }

    BoxWithConstraints(modifier = modifier) {
        // 시트 column 의 보수적 추정 높이 (floating ~24 + spacer 4 + 시트박스 60(본인) = 88).
        val maxColumnHeightDp = 90.dp
        val radiusX: Dp = (maxWidth.value * 0.38f).dp
        // 동적 radiusY — 시트 column 이 영역 위·아래로 튀어나오지 않도록.
        val radiusY: Dp = ((maxHeight - maxColumnHeightDp) / 2).coerceAtLeast(40.dp)
        val count = ordered.size
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
                isShowdown = isShowdown,
                isWinner = player.seat in winnerSeats,
                lastActionLabel = lastActionBySeat[player.seat],
                dealOrderIndex = i,
                totalActiveSeats = ordered.size,
                extraBadgeLabel = seatBadges[player.seat],
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = dx, y = dy),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// PlayerSeat — 한게임 풍 가로 박스
// ---------------------------------------------------------------------------

private val SeatWidthOther = 150.dp
private val SeatWidthHuman = 178.dp
private val SeatHeight = 52.dp
private val SeatCorner = 10.dp
private val AvatarSize = 36.dp
private val MiniCardWidthOther = 20.dp
private val MiniCardHeightOther = 28.dp
private val MiniCardWidthHuman = 28.dp
private val MiniCardHeightHuman = 40.dp

/**
 * 단일 좌석 시트. TableScreen 의 명시적 layout (헤즈업/3인/4인) 에서 직접 호출하기 위해 internal.
 *
 * 카지노 라운드 딜링: [dealOrderIndex] 는 SB → BB → ... 순서로 0, 1, 2 ... 부여.
 * [totalActiveSeats] 는 라운드 길이 (시트 수). MiniHoleCards 가 카드별 delay 를 계산하는 데 사용.
 */
@Composable
internal fun PlayerSeat(
    player: PlayerState,
    isBtn: Boolean,
    isSb: Boolean,
    isBb: Boolean,
    isToAct: Boolean,
    isHuman: Boolean,
    isShowdown: Boolean = false,
    isWinner: Boolean = false,
    lastActionLabel: String? = null,
    /** 핸드 종료 시 이 시트가 받은 payout. > 0 이면 시트 우측에 골드 +X 라벨 등장. */
    winnerPayout: Long? = null,
    /** 카지노 라운드 딜링용 시트 순번 (0..totalActiveSeats-1). */
    dealOrderIndex: Int = 0,
    /** 라운드 길이 — 시트 수. */
    totalActiveSeats: Int = 1,
    /** 7스터드 전용 시트 라벨 — "브링인" / "오픈 페어" 등 짧은 인디케이터. nickname 위에 노출. */
    extraBadgeLabel: String? = null,
    modifier: Modifier = Modifier,
) {
    val seatWidth = if (isHuman) SeatWidthHuman else SeatWidthOther
    val miniCardWidth = if (isHuman) MiniCardWidthHuman else MiniCardWidthOther
    val miniCardHeight = if (isHuman) MiniCardHeightHuman else MiniCardHeightOther
    val seatLabel = A11yStrings.seat(
        player = player,
        isToAct = isToAct,
        isDealer = isBtn,
        showHoleCards = isHuman,
    )

    // 외곽선 펄스 — 차례/승자만 강조. 본인 시트(YOU)는 별도 표시 없음 (사용자 요청).
    val borderWidth: Dp = if (isToAct || isWinner) {
        pulseFloat(initial = 1.5f, target = 3f, periodMs = 700, label = "seat-pulse").dp
    } else 1.dp

    val borderColor = when {
        isWinner -> HangameColors.SeatBorderWinner
        isToAct -> HangameColors.SeatBorderActive
        else -> HangameColors.SeatBorder
    }

    val seatBg = when {
        player.folded -> HangameColors.SeatBgFolded
        isToAct -> HangameColors.SeatBgActive
        else -> HangameColors.SeatBg
    }

    // 우측 floating: BetChip + ALL-IN. PayoutBadge 는 좁은 화면에서 우측 floating 이 절단되는
    // 케이스가 있어 시트박스 위 가운데 오버레이로 분리 (아래 Box 안에 별도 렌더).
    val sideFloating: @Composable () -> Unit = {
        Column(
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AnimatedVisibility(
                visible = player.allIn,
                enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.5f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.6f),
            ) {
                AllInBadge()
            }
            AnimatedVisibility(
                visible = player.committedThisStreet > 0 && !player.folded,
                enter = fadeIn(tween(220)) + scaleIn(tween(220), initialScale = 0.5f),
                exit = fadeOut(tween(160)) + scaleOut(tween(160), targetScale = 0.6f),
            ) {
                BetChipBadge(amount = player.committedThisStreet)
            }
        }
    }

    val seatHeight = if (isHuman) 60.dp else SeatHeight

    Row(
        modifier = modifier.semantics(mergeDescendants = true) {
            contentDescription = seatLabel
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 시트 박스
        Box {
            Surface(
                modifier = Modifier
                    .size(width = seatWidth, height = seatHeight)
                    .border(BorderStroke(borderWidth, borderColor), RoundedCornerShape(SeatCorner)),
                shape = RoundedCornerShape(SeatCorner),
                color = seatBg,
                tonalElevation = 2.dp,
                shadowElevation = 4.dp,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SeatAvatar(
                        initial = player.nickname.take(1).ifBlank { "?" },
                        folded = player.folded,
                        isHuman = isHuman,
                    )
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        if (extraBadgeLabel != null) {
                            Text(
                                text = extraBadgeLabel,
                                color = HangameColors.SeatBorderActive,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            text = player.nickname,
                            color = HangameColors.TextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // chips 카운트업 애니 — 승리/패배 시 변화량을 시각적으로 인지.
                        // Float 정밀도 7자리라 큰 단위(조 이상)는 천원 단위 정밀도 손실되지만
                        // 시각상 충분 (최종 target 값은 정확).
                        val animatedChips by animateFloatAsState(
                            targetValue = player.chips.toFloat(),
                            animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
                            label = "chips-count",
                        )
                        Text(
                            text = ChipFormat.format(animatedChips.toLong()),
                            color = HangameColors.TextChip,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // 미니 카드 — 7스터드는 hole + up 모두 표시, 홀덤은 hole 2장만.
                    if (player.upCards.isNotEmpty()) {
                        MiniSeatCards(
                            holeCards = player.holeCards,
                            upCards = player.upCards,
                            isHuman = isHuman,
                            isShowdown = isShowdown,
                            cardWidth = miniCardWidth,
                            cardHeight = miniCardHeight,
                        )
                    } else {
                        MiniHoleCards(
                            cards = player.holeCards,
                            faceDown = !isHuman && !isShowdown,
                            cardWidth = miniCardWidth,
                            cardHeight = miniCardHeight,
                            dealOrderIndex = dealOrderIndex,
                            totalActiveSeats = totalActiveSeats,
                        )
                    }
                }
            }

            // 폴드 오버레이
            if (player.folded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(SeatCorner))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(id = R.string.badge_fold),
                        fontSize = 14.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            // 핸드 종료 시 +payout 뱃지 — 시트박스 위 가운데 오버레이.
            // 외부 Box 안에서 별도 PayoutPulse 함수 호출 — outer RowScope 의 AnimatedVisibility
            // overload 추론 충돌 회피 (PayoutPulse 가 자체 composable scope 라 깨끗).
            Box(modifier = Modifier.align(Alignment.Center)) {
                PayoutPulse(amount = winnerPayout ?: 0L)
            }

            // BTN/SB/BB 마커 — 시트 우하단 외부 디스크.
            val markerLabel = when {
                isBtn -> stringResource(id = R.string.seat_btn)
                isSb -> stringResource(id = R.string.seat_sb)
                isBb -> stringResource(id = R.string.seat_bb)
                else -> null
            }
            if (markerLabel != null) {
                MarkerDisc(
                    label = markerLabel,
                    isBtn = isBtn,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 6.dp, y = 6.dp),
                )
            }
        }

        // 시트박스 우측에 BetChip + AllIn floating. PayoutBadge 는 시트 위 오버레이로 분리됨 (위 참고).
        Spacer(Modifier.width(6.dp))
        sideFloating()
    }
}

// ---------------------------------------------------------------------------
// 시트 내부 컴포넌트들
// ---------------------------------------------------------------------------

@Composable
private fun SeatAvatar(initial: String, folded: Boolean, isHuman: Boolean) {
    val bg = when {
        folded -> HangameColors.SeatBgFolded
        isHuman -> HangameColors.FeltMid
        else -> HangameColors.FeltInner
    }
    Box(
        modifier = Modifier
            .size(AvatarSize)
            .clip(CircleShape)
            .background(bg)
            .border(BorderStroke(1.dp, HangameColors.SeatBorder), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            fontSize = 16.sp,
            color = HangameColors.TextPrimary,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun MiniHoleCards(
    cards: List<Card>,
    faceDown: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    /** 0..totalActiveSeats-1. 카지노 딜러 라운드 순서. */
    dealOrderIndex: Int = 0,
    /** 라운드 길이 — 시트 수. 1라운드 (=N장) 도는 데 N * HOLE_SEAT_STAGGER_MS 소요. */
    totalActiveSeats: Int = 1,
) {
    val seats = totalActiveSeats.coerceAtLeast(1)
    val seatStagger = DealAnimationSpec.HOLE_SEAT_STAGGER_MS
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (i in 0 until 2) {
            val card = cards.getOrNull(i)
            if (card == null) {
                // 프리게임 대기 (=holeCards 비워둔 상태). 자리 reserve 만, placeholder 슬라이드인 X.
                Box(modifier = Modifier.size(width = cardWidth, height = cardHeight))
                continue
            }
            val visibleState = remember(card, faceDown) {
                MutableTransitionState(false).apply { targetState = true }
            }
            // 카지노 라운드: 모든 시트가 1장씩 받은 뒤 두 번째 라운드 시작.
            val absoluteDelay = (i * seats + dealOrderIndex) * seatStagger
            val slideSpec = tween<IntOffset>(
                durationMillis = DealAnimationSpec.HOLE_CARD_DURATION_MS,
                delayMillis = absoluteDelay,
                easing = FastOutSlowInEasing,
            )
            val fadeSpec = tween<Float>(
                durationMillis = DealAnimationSpec.HOLE_CARD_DURATION_MS,
                delayMillis = absoluteDelay,
            )
            AnimatedVisibility(
                visibleState = visibleState,
                enter = slideInVertically(animationSpec = slideSpec) { -it * 3 } +
                    fadeIn(animationSpec = fadeSpec),
            ) {
                PlayingCard(
                    card = card,
                    faceDown = faceDown,
                    width = cardWidth,
                    height = cardHeight,
                )
            }
        }
    }
}

/**
 * 7스터드용 미니 카드 묶음. 시트 폭 안에 무조건 들어가도록 카드 수에 따라 자동 축소.
 *
 *  - 비-본인 + 비-쇼다운: 다운카드(face-down placeholder)는 정보가 없어 생략하고 업카드만 표시.
 *  - 본인 또는 쇼다운: 다운+업 모두 face-up 으로 표시.
 *  - 카드 수 4 → 0.85x, 5 → 0.70x, 6+ → 0.55x. seat width overflow 방지.
 */
@Composable
private fun MiniSeatCards(
    holeCards: List<Card>,
    upCards: List<Card>,
    isHuman: Boolean,
    isShowdown: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
) {
    val showHole = isHuman || isShowdown
    val cards = if (showHole) holeCards + upCards else upCards
    val n = cards.size
    val factor = when {
        n >= 6 -> 0.55f
        n == 5 -> 0.70f
        n == 4 -> 0.85f
        else -> 1.0f
    }
    val w = (cardWidth.value * factor).dp
    val h = (cardHeight.value * factor).dp
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        for (card in cards) {
            PlayingCard(card = card, faceDown = false, width = w, height = h)
        }
    }
}

@Composable
private fun BetChipBadge(amount: Long) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = HangameColors.BetChipBg,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = "🪙",
                fontSize = 10.sp,
            )
            Text(
                text = ChipFormat.format(amount),
                fontSize = 11.sp,
                color = HangameColors.BetChipText,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun AllInBadge() {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = HangameColors.BtnAllIn,
    ) {
        Text(
            text = stringResource(id = R.string.badge_all_in),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            fontSize = 11.sp,
            color = Color.White,
            fontWeight = FontWeight.Black,
        )
    }
}

@Composable
private fun PayoutPulse(amount: Long) {
    AnimatedVisibility(
        visible = amount > 0L,
        enter = fadeIn(tween(500)) + scaleIn(tween(500), initialScale = 0.3f),
        exit = fadeOut(tween(220)) + scaleOut(tween(220), targetScale = 0.6f),
    ) {
        PayoutBadge(amount = amount)
    }
}

@Composable
private fun PayoutBadge(amount: Long) {
    // 승리 시 prominent 한 골드 펄스 — 펄스 알파로 시각 강조.
    val pulse = pulseFloat(initial = 0.85f, target = 1f, periodMs = 600, label = "payout-pulse")
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = HangameColors.PotBg.copy(alpha = pulse),
        border = BorderStroke(2.dp, HangameColors.PotValue),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = "🪙", fontSize = 14.sp)
            Text(
                text = "+${ChipFormat.format(amount)}",
                fontSize = 15.sp,
                color = HangameColors.PotValue,
                fontWeight = FontWeight.Black,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}


@Composable
private fun MarkerDisc(label: String, isBtn: Boolean, modifier: Modifier = Modifier) {
    val (bg, fg) = when {
        isBtn -> HangameColors.MarkerBtn to HangameColors.MarkerBtnText
        label == "SB" -> HangameColors.MarkerSb to Color.White
        else -> HangameColors.MarkerBb to Color.White
    }
    Surface(
        modifier = modifier.size(20.dp),
        shape = CircleShape,
        color = bg,
        shadowElevation = 2.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = if (isBtn) "D" else label,
                fontSize = 9.sp,
                color = fg,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 내부 배치 도우미
// ---------------------------------------------------------------------------

private fun orderClockwiseFromHuman(
    players: List<PlayerState>,
    humanSeat: Int,
): List<PlayerState> {
    val sorted = players.sortedBy { it.seat }
    val startIdx = sorted.indexOfFirst { it.seat == humanSeat }.let { if (it < 0) 0 else it }
    return List(sorted.size) { i -> sorted[(startIdx + i) % sorted.size] }
}

internal fun computeBlinds(
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
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, heightDp = 600, widthDp = 1280, backgroundColor = 0xFF0B2D52)
@Composable
private fun SeatLayoutPreview() {
    val players = listOf(
        PlayerState(
            seat = 0,
            nickname = "나",
            isHuman = true,
            chips = 9_500L,
            holeCards = listOf(Card(Suit.SPADE, Rank.ACE), Card(Suit.HEART, Rank.KING)),
            committedThisStreet = 50L,
            committedThisHand = 50L,
        ),
        PlayerState(
            seat = 1,
            nickname = "프로",
            isHuman = false,
            chips = 12_300L,
            committedThisStreet = 100L,
            committedThisHand = 100L,
        ),
    )
    PokerMasterTheme {
        Box(modifier = Modifier.fillMaxSize().background(HangameColors.BackgroundBrush)) {
            SeatLayout(
                players = players,
                btnSeat = 0,
                toActSeat = 0,
                humanSeat = 0,
                lastActionBySeat = mapOf(1 to "레이즈 100"),
                modifier = Modifier.fillMaxSize().padding(40.dp),
            )
        }
    }
}
