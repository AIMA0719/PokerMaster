package com.infocar.pokermaster.feature.lobby

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.model.GameMode

@Composable
fun LobbyScreen(
    onSelectMode: (GameMode) -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: LobbyViewModel = hiltViewModel(),
) {
    val wallet by viewModel.wallet.collectAsState()
    val event by viewModel.events.collectAsState()

    LaunchedEffect(Unit) { viewModel.onEntered() }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = stringResource(id = R.string.lobby_title),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(id = R.string.lobby_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(24.dp))

            // M6-C: 지갑 잔고 헤더. 파산 감지는 onEntered() 의 event 로 모달 표시.
            WalletHeader(
                balance = wallet.balanceChips,
                streak = wallet.streakDays,
            )
            Spacer(Modifier.height(16.dp))

            GameMode.entries.forEach { mode ->
                ModeCard(
                    mode = mode,
                    enabled = wallet.balanceChips >= WalletRepository.TABLE_STAKE,
                    onClick = { onSelectMode(mode) },
                )
                Spacer(Modifier.height(16.dp))
            }

            Spacer(Modifier.height(16.dp))

            // M5-C: 핸드 히스토리 진입점. 다른 모드 카드와 유사한 스타일.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                onClick = onOpenHistory,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "핸드 히스토리",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // M6-B: 통계 진입점.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                onClick = onOpenStats,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "통계",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // M6-A: 설정 진입점.
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
                onClick = onOpenSettings,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "설정",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }

    // M6-C: Daily bonus / 파산 모달.
    when (val e = event) {
        is LobbyEvent.DailyBonus -> DailyBonusDialog(
            chipsGranted = e.chipsGranted,
            streak = e.streak,
            newBalance = e.newBalance,
            onDismiss = viewModel::dismissEvent,
        )
        is LobbyEvent.Bankrupt -> BankruptDialog(
            balance = e.currentBalance,
            onReset = viewModel::onResetBankrupt,
        )
        null -> Unit
    }
}

@Composable
private fun WalletHeader(balance: Long, streak: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "잔고 ${formatChips(balance)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (streak > 0) {
            Text(
                text = "🔥 streak $streak",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DailyBonusDialog(
    chipsGranted: Long,
    streak: Int,
    newBalance: Long,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("오늘의 보너스") },
        text = {
            Column {
                Text("+${formatChips(chipsGranted)} chips 지급", fontWeight = FontWeight.SemiBold)
                Text("연속 접속 $streak 일", style = MaterialTheme.typography.bodySmall)
                Text("현재 잔고: ${formatChips(newBalance)}", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("확인") } },
    )
}

@Composable
private fun BankruptDialog(balance: Long, onReset: () -> Unit) {
    AlertDialog(
        onDismissRequest = { /* 강제 선택 — 닫기 비활성 */ },
        title = { Text("파산") },
        text = {
            Text(
                "현재 잔고 ${formatChips(balance)} 로는 테이블 입장이 불가능합니다." +
                    " 재시작 보너스를 받으시겠어요?",
            )
        },
        confirmButton = { Button(onClick = onReset) { Text("재시작 보너스 수령") } },
    )
}

private fun formatChips(n: Long): String = when {
    n >= 1_000_000L -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000L -> "${n / 1_000L}k"
    else -> n.toString()
}

@StringRes
private fun GameMode.titleRes(): Int = when (this) {
    GameMode.SEVEN_STUD -> R.string.mode_seven_stud
    GameMode.SEVEN_STUD_HI_LO -> R.string.mode_seven_stud_hi_lo
    GameMode.HOLDEM_NL -> R.string.mode_holdem
}

@Composable
private fun ModeCard(
    mode: GameMode,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = mode.titleRes()),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            if (!enabled) {
                Text(
                    "잔고 부족 (최소 ${WalletRepository.TABLE_STAKE / 1000}k 필요)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}
