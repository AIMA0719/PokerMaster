package com.infocar.pokermaster.feature.table

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Suit
import com.infocar.pokermaster.core.ui.theme.PokerColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.anim.PotGhostChipsOverlay
import kotlinx.coroutines.delay

/**
 * 핸드 종료 컴팩트 오버레이.
 *
 * 기존 바텀시트(640dp 상한) 에서 컴팩트 인라인 오버레이로 변경:
 *  - 테이블 하단에 작은 패널로 등장
 *  - 자동 다음 핸드 카운트다운 표시
 *  - 팟 시퀀스 200ms 간격 등장
 *  - 높이 상한 320dp 로 축소
 */
@Composable
fun HandEndSheet(
    data: HandEndViewData,
    onNext: () -> Unit,
    onInsights: () -> Unit,
    autoNextCountdown: Int? = null,
    modifier: Modifier = Modifier,
) {
    // 시트 자체 등장 애니메이션.
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    // 팟 시퀀스 진행도.
    var revealedPots by remember(data.pots) { mutableStateOf(0) }
    LaunchedEffect(data.pots) {
        revealedPots = 0
        data.pots.forEachIndexed { idx, _ ->
            delay(if (idx == 0) 100L else 200L)
            revealedPots = idx + 1
        }
    }

    // 첫 팟의 첫 승자 기준 헤더 문구.
    val firstPot = data.pots.firstOrNull()
    val firstWinnerSeat = firstPot?.winnerSeats?.firstOrNull()
    val winnerName = firstWinnerSeat?.let { data.nicknameBySeat[it] } ?: "-"
    val winnerCategory = firstWinnerSeat?.let { data.handInfos[it] } ?: ""
    val headerText = if (firstWinnerSeat != null) {
        val baseSingle = stringResource(id = R.string.hand_end_single_winner, winnerName)
        if (winnerCategory.isNotEmpty()) "$baseSingle — $winnerCategory" else baseSingle
    } else {
        stringResource(id = R.string.hand_end_winner)
    }

    val reduceMotion = LocalReduceMotion.current
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { fullHeight -> fullHeight }) + fadeIn(),
        modifier = modifier,
    ) {
      Box(modifier = Modifier.fillMaxWidth()) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 320.dp),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                // 1. 헤더
                Text(
                    text = headerText,
                    style = MaterialTheme.typography.titleMedium,
                    color = PokerColors.Accent,
                    fontWeight = FontWeight.Bold,
                )

                // 2. 팟 시퀀스 (컴팩트)
                data.pots.take(revealedPots).forEach { pot ->
                    val label = if (pot.index == 0)
                        stringResource(id = R.string.hand_end_pot_main)
                    else
                        stringResource(id = R.string.hand_end_pot_side, pot.index)
                    SidePotDistribution(
                        pot = pot,
                        label = label,
                        nicknameBySeat = data.nicknameBySeat,
                        payoutsBySeat = data.payoutsBySeat,
                    )
                }

                // 3. 베스트 5장
                val winnerSeats = data.pots.flatMap { it.winnerSeats }.toSet()
                val bestFiveRows = winnerSeats
                    .mapNotNull { seat -> data.bestFiveBySeat[seat]?.let { seat to it } }
                    .sortedBy { it.first }
                if (bestFiveRows.isNotEmpty()) {
                    bestFiveRows.forEach { (seat, cards) ->
                        BestFiveRow(
                            nickname = data.nicknameBySeat[seat] ?: "#$seat",
                            cards = cards,
                        )
                    }
                }

                // 4. Uncalled 환급
                if (data.uncalledBySeat.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.hand_end_uncalled_returned),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    data.uncalledBySeat.entries.sortedBy { it.key }.forEach { (seat, chips) ->
                        val name = data.nicknameBySeat[seat] ?: "#$seat"
                        Text(
                            text = "$name +${ChipFormat.format(chips)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = PokerColors.Warning,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                // 5. 하단 액션 (카운트다운 포함)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onNext,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PokerColors.Primary,
                            contentColor = Color.White,
                        ),
                    ) {
                        val btnText = if (autoNextCountdown != null) {
                            stringResource(id = R.string.hand_end_next) + " (${autoNextCountdown}s)"
                        } else {
                            stringResource(id = R.string.hand_end_next)
                        }
                        Text(
                            text = btnText,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
        if (!reduceMotion) {
            PotGhostChipsOverlay(
                modifier = Modifier
                    .matchParentSize(),
            )
        }
      }
    }
}

/**
 * 베스트 5장 가로 배치.
 */
@Composable
private fun BestFiveRow(
    nickname: String,
    cards: List<Card>,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = nickname,
            style = MaterialTheme.typography.labelMedium,
            color = PokerColors.Accent,
            modifier = Modifier.width(64.dp),
        )
        Spacer(Modifier.width(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            cards.take(5).forEach { card ->
                PlayingCard(
                    card = card,
                    faceDown = false,
                    modifier = Modifier.width(32.dp).height(44.dp),
                )
            }
        }
    }
}

// -----------------------------------------------------------------------------
// Preview
// -----------------------------------------------------------------------------

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun HandEndSheetPreview() {
    val pots = listOf(
        PotSummary(
            amount = 2_400L,
            eligibleSeats = setOf(0, 1, 2),
            winnerSeats = setOf(1),
            index = 0,
        ),
        PotSummary(
            amount = 800L,
            eligibleSeats = setOf(0, 1),
            winnerSeats = setOf(0),
            index = 1,
        ),
    )
    val best = listOf(
        Card(Suit.SPADE, Rank.ACE),
        Card(Suit.SPADE, Rank.KING),
        Card(Suit.SPADE, Rank.QUEEN),
        Card(Suit.SPADE, Rank.JACK),
        Card(Suit.SPADE, Rank.TEN),
    )
    val data = HandEndViewData(
        pots = pots,
        handInfos = mapOf(0 to "투 페어", 1 to "스트레이트 플러시"),
        bestFiveBySeat = mapOf(0 to best, 1 to best),
        payoutsBySeat = mapOf(0 to 800L, 1 to 2_400L),
        uncalledBySeat = mapOf(1 to 200L),
        nicknameBySeat = mapOf(0 to "나", 1 to "프로", 2 to "LAG"),
    )
    PokerMasterTheme {
        Box(modifier = Modifier.fillMaxWidth().fillMaxHeight()) {
            HandEndSheet(
                data = data,
                onNext = {},
                onInsights = {},
                autoNextCountdown = 3,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}
