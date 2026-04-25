package com.infocar.pokermaster.feature.lobby

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.model.GameMode

@Composable
fun LobbyScreen(
    onSelectMode: (GameMode, Int, Long) -> Unit = { _, _, _ -> },
    onOpenHistory: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenStats: () -> Unit = {},
    viewModel: LobbyViewModel = hiltViewModel(),
) {
    val wallet by viewModel.wallet.collectAsState()
    val event by viewModel.events.collectAsState()
    var selectedSeats by rememberSaveable { mutableIntStateOf(2) }

    LaunchedEffect(Unit) { viewModel.onEntered() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HangameColors.BackgroundBrush),
    ) {
        // 가로 모드 두 컬럼: 좌측 환영/지갑, 우측 모드+메뉴.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // 좌측 — 타이틀 + 지갑.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.lobby_title),
                    style = MaterialTheme.typography.displayLarge,
                    color = HangameColors.TextPrimary,
                    fontWeight = FontWeight.Black,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(id = R.string.lobby_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HangameColors.TextSecondary,
                )
                Spacer(Modifier.height(24.dp))
                WalletHeader(
                    balance = wallet.balanceChips,
                    streak = wallet.streakDays,
                )
            }

            // 우측 — 모드 카드 + 메뉴 (verticalScroll 로 가로 short-height 안전).
            Column(
                modifier = Modifier
                    .weight(1.2f)
                    .fillMaxHeight()
                    .widthIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Top,
            ) {
                // 인원 수 선택 — HOLDEM_NL 만 다인 지원.
                SeatCountPicker(
                    selected = selectedSeats,
                    onSelect = { selectedSeats = it },
                )
                Spacer(Modifier.height(12.dp))

                GameMode.entries.forEach { mode ->
                    // 본인 buy-in = wallet 잔고 전체. wallet > 0 면 진입 가능.
                    val hasChips = wallet.balanceChips > 0L
                    val supported = true // 모든 GameMode 정식 지원 (HOLDEM_NL / SEVEN_STUD / SEVEN_STUD_HI_LO)
                    ModeCard(
                        mode = mode,
                        enabled = hasChips && supported,
                        reasonIfDisabled = when {
                            !supported -> "준비 중 (다음 업데이트)"
                            !hasChips -> "잔고 부족 — 보너스 받고 다시 시도하세요."
                            else -> null
                        },
                        // 본인 buy-in = wallet 잔고 전체. NPC 는 ViewModel 에서 5만 fixed.
                        onClick = { onSelectMode(mode, selectedSeats, wallet.balanceChips) },
                    )
                    Spacer(Modifier.height(12.dp))
                }

                Spacer(Modifier.height(8.dp))
                MenuRow(
                    label = "핸드 히스토리",
                    onClick = onOpenHistory,
                )
                Spacer(Modifier.height(8.dp))
                MenuRow(
                    label = "통계",
                    onClick = onOpenStats,
                )
                Spacer(Modifier.height(8.dp))
                MenuRow(
                    label = "설정",
                    onClick = onOpenSettings,
                )
                Spacer(Modifier.height(16.dp))
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
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = "잔고",
                    style = MaterialTheme.typography.labelSmall,
                    color = HangameColors.TextMuted,
                )
                Text(
                    text = "🪙 ${formatChips(balance)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HangameColors.TextChip,
                )
            }
            if (streak > 0) {
                Text(
                    text = "🔥 $streak 일",
                    style = MaterialTheme.typography.titleMedium,
                    color = HangameColors.TextLime,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SeatCountPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "인원 수",
            style = MaterialTheme.typography.labelLarge,
            color = HangameColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for (n in 2..4) {
                val active = n == selected
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    colors = CardDefaults.cardColors(
                        containerColor = if (active) HangameColors.SeatBgActive else HangameColors.SeatBg,
                    ),
                    border = if (active)
                        androidx.compose.foundation.BorderStroke(1.5.dp, HangameColors.SeatBorderActive)
                    else null,
                    onClick = { onSelect(n) },
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${n}인",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (active) HangameColors.TextLime else HangameColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HangameColors.SeatBg,
        ),
        onClick = onClick,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = HangameColors.TextPrimary,
                fontWeight = FontWeight.Medium,
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

private fun formatChips(n: Long): String {
    val abs = kotlin.math.abs(n)
    val sign = if (n < 0) "-" else ""
    val MAN = 10_000L
    val EOK = 100_000_000L
    val JO = 1_000_000_000_000L
    val GYEONG = 10_000_000_000_000_000L
    return sign + when {
        abs < MAN -> "%,d원".format(abs)
        abs < EOK -> {
            val man = abs / MAN
            val rem = abs % MAN
            if (rem == 0L) "%,d만".format(man) else "%,d만 %,d원".format(man, rem)
        }
        abs < JO -> {
            val eok = abs / EOK
            val man = (abs % EOK) / MAN
            if (man == 0L) "%,d억".format(eok) else "%,d억 %,d만".format(eok, man)
        }
        abs < GYEONG -> {
            val jo = abs / JO
            val eok = (abs % JO) / EOK
            if (eok == 0L) "%,d조".format(jo) else "%,d조 %,d억".format(jo, eok)
        }
        else -> {
            val gyeong = abs / GYEONG
            val jo = (abs % GYEONG) / JO
            if (jo == 0L) "%,d경".format(gyeong) else "%,d경 %,d조".format(gyeong, jo)
        }
    }
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
    reasonIfDisabled: String? = null,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) HangameColors.SeatBgActive else HangameColors.SeatBgFolded,
        ),
        enabled = enabled,
        onClick = onClick,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(id = mode.titleRes()),
                style = MaterialTheme.typography.titleLarge,
                color = if (enabled) HangameColors.TextPrimary
                else HangameColors.TextMuted,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
            if (!enabled && reasonIfDisabled != null) {
                Text(
                    reasonIfDisabled,
                    style = MaterialTheme.typography.bodySmall,
                    color = HangameColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
