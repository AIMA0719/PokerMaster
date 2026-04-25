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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.ui.theme.PokerColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 단일 팟(메인/사이드) 분배 행.
 *
 *  - 좌: 팟 금액 (ChipFormat)
 *  - 중: 자격 좌석 닉네임 (승자 = 골드, 비승자 = 회색)
 *  - 우: 이 팟에서 각 승자가 받은 금액 (= pot.amount / winnerCount 근사)
 *
 *  payoutsBySeat 는 모든 팟 합계이므로, 위젯은 본 팟의 균등 분할을 표기.
 */
@Composable
fun SidePotDistribution(
    pot: PotSummary,
    label: String,
    nicknameBySeat: Map<Int, String>,
    payoutsBySeat: Map<Int, Long>,
    modifier: Modifier = Modifier,
) {
    val winnerCount = pot.winnerSeats.size.coerceAtLeast(1)
    val perWinner = pot.amount / winnerCount

    val goldBorder = BorderStroke(2.dp, PokerColors.Accent)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        border = if (pot.winnerSeats.isNotEmpty()) goldBorder else null,
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
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    )
                    Text(
                        text = ChipFormat.format(pot.amount),
                        style = MaterialTheme.typography.titleMedium,
                        color = PokerColors.Accent,
                        fontWeight = FontWeight.Bold,
                    )
                }

                // 우: per-winner 금액 (승자가 있을 때만)
                if (pot.winnerSeats.isNotEmpty()) {
                    Column(
                        modifier = Modifier.wrapContentWidth(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        Text(
                            text = "승자 분배",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        )
                        Text(
                            text = "+${ChipFormat.format(perWinner)}",
                            style = MaterialTheme.typography.titleSmall,
                            color = PokerColors.Success,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // 중: 자격자 닉네임 목록 (승자 = 골드, 비승자 = 회색)
            //  HiLo 모드(loWinnerSeats 가 비어있지 않거나 hiWinnerSeats != winnerSeats)면
            //  hi/lo/scoop 라벨 표시. 그 외엔 단순 ★ 표시.
            val isHiLoBranch = pot.loWinnerSeats.isNotEmpty()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                pot.eligibleSeats.sorted().forEach { seat ->
                    val name = nicknameBySeat[seat] ?: "#$seat"
                    val hi = seat in pot.hiWinnerSeats
                    val lo = seat in pot.loWinnerSeats
                    val isWinner = hi || lo
                    val label = when {
                        !isWinner -> name
                        !isHiLoBranch -> "★ $name"
                        hi && lo -> "★ HL $name"   // 스쿠프 (hi+lo 동시)
                        hi -> "★ H $name"
                        else -> "★ L $name"
                    }
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isWinner) PokerColors.Accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        fontWeight = if (isWinner) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
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
