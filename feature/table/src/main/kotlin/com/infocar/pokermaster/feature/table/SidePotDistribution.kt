package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 단일 팟(메인/사이드) 분배 행 — Hi-Lo Declare 분배 시각화 폴리시 적용.
 *
 *  분배 라벨 우선순위:
 *  - scoop (★HL, 단독 양방향 우승): 밝은 골드 + bold + 풀팟 금액
 *  - hi+lo 동시 발생 (HiLo 표준 분할): "★H {nicknames}" 와 "★L {nicknames}" 두 줄, 각각 1/2 팟
 *  - hi only: "★H {nicknames}" 한 줄, 풀팟
 *  - lo only: "★L {nicknames}" 한 줄, 풀팟
 *  - 동률: comma 로 nicknames join
 *  - 위 어느 것도 안 맞으면 (winnerSeats 도 비어있는 dead pot) 자격자만 회색 표시.
 */
@Composable
fun SidePotDistribution(
    pot: PotSummary,
    label: String,
    nicknameBySeat: Map<Int, String>,
    payoutsBySeat: Map<Int, Long>,
    modifier: Modifier = Modifier,
) {
    val goldBorder = BorderStroke(2.dp, HangameColors.PotValue)
    val anyWinners = pot.scoopWinnerSeats.isNotEmpty() ||
        pot.hiWinnerSeats.isNotEmpty() ||
        pot.loWinnerSeats.isNotEmpty() ||
        pot.winnerSeats.isNotEmpty()

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = HangameColors.SeatBg,
        tonalElevation = 2.dp,
        border = if (anyWinners) goldBorder else null,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // 좌: 팟 라벨 + 금액
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = HangameColors.TextSecondary,
                    )
                    Text(
                        text = ChipFormat.format(pot.amount),
                        style = MaterialTheme.typography.titleMedium,
                        color = HangameColors.PotValue,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            // 분배 시각화 분기.
            when {
                pot.scoopWinnerSeats.isNotEmpty() -> {
                    val names = pot.scoopWinnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    DistributionLine(
                        prefix = "★HL",
                        names = names,
                        amount = pot.amount,
                        emphasize = true,
                    )
                }
                pot.hiWinnerSeats.isNotEmpty() && pot.loWinnerSeats.isNotEmpty() -> {
                    val hiNames = pot.hiWinnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    val loNames = pot.loWinnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    val half = pot.amount / 2
                    DistributionLine(prefix = "★H", names = hiNames, amount = half)
                    Spacer(Modifier.height(2.dp))
                    DistributionLine(prefix = "★L", names = loNames, amount = pot.amount - half)
                }
                pot.hiWinnerSeats.isNotEmpty() -> {
                    val names = pot.hiWinnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    DistributionLine(prefix = "★H", names = names, amount = pot.amount)
                }
                pot.loWinnerSeats.isNotEmpty() -> {
                    val names = pot.loWinnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    DistributionLine(prefix = "★L", names = names, amount = pot.amount)
                }
                pot.winnerSeats.isNotEmpty() -> {
                    // 비-HiLo (홀덤/Hi-only 7스터드) 또는 hiWinnerSeats 미세팅 폴백.
                    val names = pot.winnerSeats
                        .sorted()
                        .joinToString(", ") { nicknameBySeat[it] ?: "#$it" }
                    DistributionLine(prefix = "★", names = names, amount = pot.amount)
                }
                else -> {
                    // 자격자만 회색.
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        pot.eligibleSeats.sorted().forEach { seat ->
                            Text(
                                text = nicknameBySeat[seat] ?: "#$seat",
                                style = MaterialTheme.typography.labelMedium,
                                color = HangameColors.TextMuted,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistributionLine(
    prefix: String,
    names: String,
    amount: Long,
    emphasize: Boolean = false,
) {
    val color: Color = if (emphasize) HangameColors.PotValue else HangameColors.TextLime
    val weight = if (emphasize) FontWeight.Black else FontWeight.SemiBold
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "$prefix $names",
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasize) HangameColors.PotValue else HangameColors.TextPrimary,
            fontWeight = weight,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "+${ChipFormat.format(amount)}",
            style = MaterialTheme.typography.titleSmall,
            color = color,
            fontWeight = weight,
            modifier = Modifier.wrapContentWidth(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SidePotDistributionPreview() {
    PokerMasterTheme {
        SidePotDistribution(
            pot = PotSummary(
                amount = 1_800L,
                eligibleSeats = setOf(0, 1, 2),
                winnerSeats = setOf(1),
                index = 0,
            ),
            label = "메인 팟",
            nicknameBySeat = mapOf(0 to "나", 1 to "프로", 2 to "LAG"),
            payoutsBySeat = mapOf(1 to 1_800L),
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SidePotDistributionScoopPreview() {
    PokerMasterTheme {
        SidePotDistribution(
            pot = PotSummary(
                amount = 2_400L,
                eligibleSeats = setOf(0, 1, 2),
                winnerSeats = setOf(1),
                index = 0,
                hiWinnerSeats = setOf(1),
                loWinnerSeats = setOf(1),
                scoopWinnerSeats = setOf(1),
            ),
            label = "메인 팟",
            nicknameBySeat = mapOf(0 to "나", 1 to "프로", 2 to "LAG"),
            payoutsBySeat = mapOf(1 to 2_400L),
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun SidePotDistributionHiLoSplitPreview() {
    PokerMasterTheme {
        SidePotDistribution(
            pot = PotSummary(
                amount = 2_400L,
                eligibleSeats = setOf(0, 1, 2),
                winnerSeats = setOf(0, 2),
                index = 0,
                hiWinnerSeats = setOf(2),
                loWinnerSeats = setOf(0),
            ),
            label = "메인 팟",
            nicknameBySeat = mapOf(0 to "나", 1 to "프로", 2 to "LAG"),
            payoutsBySeat = mapOf(0 to 1_200L, 2 to 1_200L),
            modifier = Modifier.padding(12.dp),
        )
    }
}
