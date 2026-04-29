package com.infocar.pokermaster.feature.lobby

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.data.profile.NicknameRepository
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
    val missions by viewModel.missions.collectAsState()
    val nickname by viewModel.nickname.collectAsState()
    var selectedSeats by rememberSaveable { mutableIntStateOf(2) }
    var showNicknameDialog by remember { mutableStateOf(false) }
    var enteringTable by remember { mutableStateOf(false) }
    var pendingModeSelection by remember { mutableStateOf<GameMode?>(null) }

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
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(id = R.string.lobby_subtitle),
                    style = MaterialTheme.typography.bodyLarge,
                    color = HangameColors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                NicknameRow(
                    nickname = nickname,
                    onClick = { showNicknameDialog = true },
                )
                Spacer(Modifier.height(20.dp))
                WalletHeader(
                    balance = wallet.balanceChips,
                    streak = wallet.streakDays,
                    lifetime = wallet.totalEarnedLifetime,
                    elo = wallet.elo,
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

                // Phase B: 일일 미션 3종 (5/10/20 핸드 cumulative, 1k/2k/3k).
                MissionCard(state = missions, onClaim = viewModel::claimMission)
                Spacer(Modifier.height(12.dp))

                GameMode.entries.forEachIndexed { index, mode ->
                    // 본인 buy-in = wallet 잔고 전체. wallet > 0 면 진입 가능.
                    val hasChips = wallet.balanceChips > 0L
                    val supported = true // 모든 GameMode 정식 지원 (HOLDEM_NL / SEVEN_STUD / SEVEN_STUD_HI_LO)
                    val canEnter = hasChips && supported && !enteringTable
                    //진입 시 staggered enter (index * 80ms delay) — cinematic 첫 등장.
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        delay(index * 80L)
                        visible = true
                    }
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(420)) +
                            slideInVertically(tween(420, easing = FastOutSlowInEasing)) { full -> full / 3 },
                    ) {
                        ModeCard(
                            mode = mode,
                            enabled = canEnter,
                            reasonIfDisabled = when {
                                !supported -> "준비 중 (다음 업데이트)"
                                !hasChips -> "잔고 부족 — 보너스 받고 다시 시도하세요."
                                else -> null
                            },
                            // 모드 카드 클릭 → buy-in 선택 다이얼로그.
                            onClick = {
                                if (!enteringTable) {
                                    pendingModeSelection = mode
                                }
                            },
                        )
                    }
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

    if (showNicknameDialog) {
        NicknameEditDialog(
            current = nickname,
            onConfirm = { viewModel.setNickname(it) },
            onDismiss = { showNicknameDialog = false },
        )
    }

    pendingModeSelection?.let { mode ->
        BuyInDialog(
            mode = mode,
            seats = selectedSeats,
            balance = wallet.balanceChips,
            onConfirm = { buyIn ->
                pendingModeSelection = null
                if (!enteringTable) {
                    enteringTable = true
                    onSelectMode(mode, selectedSeats, buyIn)
                }
            },
            onDismiss = { pendingModeSelection = null },
        )
    }

    // Daily bonus / 파산 / Tier-up 모달 + 에러 Toast.
    val ctx = androidx.compose.ui.platform.LocalContext.current
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
        is LobbyEvent.TierUp -> TierUpDialog(
            newTier = e.newTier,
            oldTier = e.oldTier,
            onDismiss = viewModel::dismissEvent,
        )
        is LobbyEvent.Error -> LaunchedEffect(e) {
            android.widget.Toast.makeText(ctx, e.message, android.widget.Toast.LENGTH_SHORT).show()
            viewModel.dismissEvent()
        }
        null -> Unit
    }
}

@Composable
private fun WalletHeader(balance: Long, streak: Int, lifetime: Long, elo: Int) {
    //잔고 카운트업 — 잔고가 변할 때 600ms 부드럽게.
    val animatedBalance = remember { Animatable(balance.toFloat()) }
    LaunchedEffect(balance) {
        if (animatedBalance.value.toLong() != balance) {
            animatedBalance.animateTo(
                balance.toFloat(),
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            )
        }
    }
    val displayBalance = animatedBalance.value.toLong()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.7f),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = "잔고",
                    style = MaterialTheme.typography.labelMedium,
                    color = HangameColors.TextMuted,
                )
                Text(
                    text = "🪙 ${formatChips(displayBalance)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HangameColors.TextChip,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (lifetime > 0L) {
                    val tier = TierLevel.forLifetime(lifetime)
                    Text(
                        text = "${tier.emoji} 누적 ${formatChips(lifetime)} · ${tier.label}",
                        style = MaterialTheme.typography.labelMedium,
                        color = HangameColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.size(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (streak > 0) {
                    Text(
                        text = "🔥 $streak 일",
                        style = MaterialTheme.typography.titleMedium,
                        color = HangameColors.TextLime,
                        fontWeight = FontWeight.SemiBold,
                    )
                    StreakDots(streak = streak)
                    Spacer(Modifier.height(6.dp))
                }
                // Phase E: ELO 점수. 항상 표시 (1200 시작).
                // ELO 변화량 — 마지막 값과 비교해서 lobby 진입 시 +N/-N 라벨 6초간 노출.
                var prevElo by rememberSaveable { mutableIntStateOf(elo) }
                var eloDelta by remember { mutableIntStateOf(0) }
                LaunchedEffect(elo) {
                    val diff = elo - prevElo
                    if (diff != 0 && prevElo != 0) {
                        eloDelta = diff
                        delay(6_000L)
                        if (eloDelta == diff) eloDelta = 0
                    }
                    prevElo = elo
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = "📊 ELO $elo",
                        style = MaterialTheme.typography.labelLarge,
                        color = HangameColors.TextSecondary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (eloDelta != 0) {
                        val sign = if (eloDelta > 0) "+" else ""
                        Text(
                            text = "$sign$eloDelta",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (eloDelta > 0) HangameColors.TextLime else HangameColors.BtnFold,
                            fontWeight = FontWeight.Black,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Phase4: 7일 streak progress dot. 각 dot 1일 — 채워진 만큼 라임. 7일 도달 시 ★.
 * 7일 초과 시 모든 dot 채워짐 + ★ 유지 (단순 표시 — 28일 등 누적 카운트는 별도).
 */
@Composable
private fun StreakDots(streak: Int) {
    val filled = streak.coerceAtMost(7)
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(7) { i ->
            Box(
                modifier = Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(
                        if (i < filled) HangameColors.TextLime
                        else HangameColors.SeatBgFolded,
                    ),
            )
        }
        if (streak >= 7) {
            Spacer(Modifier.size(4.dp))
            Text(
                text = "★",
                color = HangameColors.PotValue,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NicknameRow(nickname: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
            .heightIn(min = 48.dp)
            .semantics {
                contentDescription = "닉네임 변경: $nickname"
                role = androidx.compose.ui.semantics.Role.Button
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "안녕,",
            style = MaterialTheme.typography.bodyLarge,
            color = HangameColors.TextSecondary,
        )
        Text(
            text = nickname,
            style = MaterialTheme.typography.titleMedium,
            color = HangameColors.TextLime,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.widthIn(max = 220.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = "✏️", style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun NicknameEditDialog(
    current: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var input by androidx.compose.runtime.remember(current) {
        androidx.compose.runtime.mutableStateOf(current)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "닉네임 변경",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.take(NicknameRepository.MAX_LENGTH) },
                    singleLine = true,
                    label = {
                        Text(
                            "닉네임 (최대 ${NicknameRepository.MAX_LENGTH}자)",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "비워두거나 기존과 같으면 변경되지 않습니다.",
                    style = MaterialTheme.typography.labelMedium,
                    color = HangameColors.TextMuted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(input); onDismiss() },
                enabled = input.trim().isNotBlank() && input.trim() != current,
            ) {
                Text(
                    "변경",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(
                    "취소",
                    style = MaterialTheme.typography.titleSmall,
                )
            }
        },
    )
}

/**
 * Phase B: 일일 미션 3종 카드 (5/10/20 핸드 cumulative). 한 핸드 누적 카운트 공유,
 * 임계치별 별도 보상 + 별도 수령 상태.
 */
@Composable
private fun MissionCard(state: MissionsState, onClaim: (String) -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = HangameColors.SeatBg,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "🎯 일일 미션",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = HangameColors.TextPrimary,
                )
                Text(
                    text = "오늘 ${state.todayHands}핸드 플레이",
                    style = MaterialTheme.typography.labelLarge,
                    color = HangameColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.missions.forEach { mission ->
                MissionRow(
                    todayHands = state.todayHands,
                    mission = mission,
                    onClaim = { onClaim(mission.id) },
                )
            }
        }
    }
}

@Composable
private fun MissionRow(todayHands: Int, mission: Mission, onClaim: () -> Unit) {
    val canClaim = mission.canClaim(todayHands)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "${mission.label} (${todayHands.coerceAtMost(mission.targetHands)}/${mission.targetHands})",
                style = MaterialTheme.typography.bodyLarge,
                color = HangameColors.TextPrimary,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = "+${formatChips(mission.rewardAmount)}",
                style = MaterialTheme.typography.labelLarge,
                color = HangameColors.PotValue,
                fontWeight = FontWeight.SemiBold,
            )
        }
        LinearProgressIndicator(
            progress = { mission.progress(todayHands) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
            color = if (mission.claimed) HangameColors.TextMuted else HangameColors.TextLime,
            trackColor = HangameColors.SeatBgFolded,
        )
        when {
            canClaim -> {
                Button(
                    onClick = onClaim,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    Text(
                        "보상 받기 🎁",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            mission.claimed -> {
                Text(
                    text = "✅ 수령 완료",
                    style = MaterialTheme.typography.labelLarge,
                    color = HangameColors.TextLime,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            else -> Unit
        }
    }
}

// Phase C: tier 정의는 TierLevel enum (Tier.kt) 으로 이동. 진급 모달 / 임계치 단일 소스.

@Composable
private fun SeatCountPicker(
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "인원 수",
            style = MaterialTheme.typography.titleSmall,
            color = HangameColors.TextSecondary,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            for (n in 2..4) {
                val active = n == selected
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(RoundedCornerShape(12.dp)),
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
            .height(60.dp)
            .clip(RoundedCornerShape(14.dp)),
        colors = CardDefaults.cardColors(
            containerColor = HangameColors.SeatBg,
        ),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = HangameColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "›",
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.Black,
                style = MaterialTheme.typography.titleLarge,
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
    //chipsGranted 0→amount 카운트업 (800ms) + 칩 이모지 bounce (0→1.2→1.0).
    val animatedGranted = remember { Animatable(0f) }
    LaunchedEffect(chipsGranted) {
        animatedGranted.snapTo(0f)
        animatedGranted.animateTo(
            chipsGranted.toFloat(),
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        )
    }
    val bounceScale = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        bounceScale.animateTo(1.2f, tween(220, easing = FastOutSlowInEasing))
        bounceScale.animateTo(1.0f, tween(140, easing = FastOutSlowInEasing))
    }
    val displayGranted = animatedGranted.value.toLong()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "🪙",
                    modifier = Modifier.scale(bounceScale.value),
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "오늘의 보너스",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "+${formatChips(displayGranted)}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = HangameColors.PotValue,
                )
                if (streak > 0) {
                    Text(
                        "🔥 연속 $streak 일",
                        style = MaterialTheme.typography.titleMedium,
                        color = HangameColors.TextLime,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    "잔고 ${formatChips(newBalance)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = HangameColors.TextMuted,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(
                    "받기",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun TierUpDialog(newTier: TierLevel, oldTier: TierLevel, onDismiss: () -> Unit) {
    // Phase C: 이모지 bounce + 진급 메시지. Phase C2: 보상 chip 카운트업 + 강조.
    val bounce = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    val rewardProgress = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) {
        bounce.animateTo(
            1.3f,
            androidx.compose.animation.core.tween(durationMillis = 280, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
        bounce.animateTo(
            1.0f,
            androidx.compose.animation.core.tween(durationMillis = 180, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        )
    }
    LaunchedEffect(newTier) {
        if (newTier.rewardChips > 0L) {
            rewardProgress.animateTo(
                1f,
                androidx.compose.animation.core.tween(durationMillis = 900, easing = androidx.compose.animation.core.FastOutSlowInEasing),
            )
        } else {
            rewardProgress.snapTo(1f)
        }
    }
    val displayedReward = (newTier.rewardChips * rewardProgress.value).toLong()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = newTier.emoji,
                    modifier = Modifier.scale(bounce.value),
                    style = MaterialTheme.typography.displaySmall,
                )
                Text(
                    "티어 진급!",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "${oldTier.label} → ${newTier.label}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = HangameColors.PotValue,
                )
                Text(
                    "축하합니다! 누적 ${formatChips(newTier.threshold)} 칩을 돌파했습니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (newTier.rewardChips > 0L) {
                    Text(
                        text = "🪙 보상 +${formatChips(displayedReward)} 적립!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        color = HangameColors.TextLime,
                    )
                }
                newTier.next()?.let { next ->
                    Text(
                        "다음 티어: ${next.emoji} ${next.label} (누적 ${formatChips(next.threshold)})",
                        style = MaterialTheme.typography.labelMedium,
                        color = HangameColors.TextMuted,
                    )
                } ?: Text(
                    "최고 티어 달성! 🎉",
                    style = MaterialTheme.typography.labelLarge,
                    color = HangameColors.TextLime,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text(
                    "확인",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun BankruptDialog(balance: Long, onReset: () -> Unit) {
    //💸 이모지 한 번 wobble shake (5 keyframes ~500ms) + 빨간 강조.
    val shake = remember { Animatable(0f) }
    val ctx = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        for (k in listOf(0f, 1f, -1f, 0.5f, -0.3f, 0f)) {
            shake.animateTo(k, tween(durationMillis = 90, easing = FastOutSlowInEasing))
        }
    }
    AlertDialog(
        onDismissRequest = { /* 강제 선택 — 닫기 비활성 */ },
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "💸",
                    modifier = Modifier.offset {
                        IntOffset(x = (shake.value * 6.dp.toPx()).toInt(), y = 0)
                    },
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    "파산",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    color = HangameColors.BtnFold,
                )
            }
        },
        text = {
            Text(
                "현재 잔고 ${formatChips(balance)} 로는 테이블 입장이 불가능합니다." +
                    " 재시작 보너스를 받으시겠어요?",
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        confirmButton = {
            Button(onClick = onReset) {
                Text(
                    "재시작 보너스 수령",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            OutlinedButton(onClick = {
                (ctx as? android.app.Activity)?.finish()
            }) { Text("앱 종료") }
        },
    )
}

/**
 * 모드 진입 전 buy-in 시드 선택 다이얼로그.
 *  - 잔고 비례 프리셋 4개 (10% / 25% / 50% / 100%).
 *  - NPC 는 ViewModel 에서 5만 fixed.
 */
@Composable
private fun BuyInDialog(
    mode: GameMode,
    seats: Int,
    balance: Long,
    onConfirm: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val presets = remember(balance) {
        listOf(
            balance / 10L,
            balance / 4L,
            balance / 2L,
            balance,
        ).map { it.coerceAtLeast(1L) }
    }
    var selected by remember(presets) { mutableStateOf(presets.last()) }

    val modeLabel = when (mode) {
        GameMode.HOLDEM_NL -> "텍사스 홀덤"
        GameMode.SEVEN_STUD -> "7카드 스터드"
        GameMode.SEVEN_STUD_HI_LO -> "7카드 하이로우"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$modeLabel · ${seats}인", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "테이블에 가져갈 시드를 선택하세요. 잔고 ${formatChips(balance)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val labels = listOf("10%", "25%", "50%", "전액")
                labels.forEachIndexed { i, lbl ->
                    val amt = presets[i]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = amt }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "$lbl  ·  ${formatChips(amt)}",
                            color = if (selected == amt) HangameColors.TextLime else HangameColors.TextPrimary,
                            fontWeight = if (selected == amt) FontWeight.Bold else FontWeight.Normal,
                        )
                        if (selected == amt) Text("✓", color = HangameColors.TextLime, fontWeight = FontWeight.Black)
                    }
                }
                Text(
                    "NPC는 5만 칩으로 동일 입장합니다. 패배하면 시드만 잃고, 승리하면 칩이 잔고에 적립됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = HangameColors.TextMuted,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(selected) },
                enabled = selected > 0L,
            ) { Text("입장") }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("취소") }
        },
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
        abs < MAN -> "%,d칩".format(abs)
        abs < EOK -> {
            val man = abs / MAN
            val rem = abs % MAN
            if (rem == 0L) "%,d만".format(man) else "%,d만 %,d".format(man, rem)
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

/** 모드 짧은 부제 — 한게임풍 카드 카피. 학습 부담 낮은 한 줄. */
private fun GameMode.subtitle(): String = when (this) {
    GameMode.SEVEN_STUD -> "한국식 7카드 · 다이/따당/구사"
    GameMode.SEVEN_STUD_HI_LO -> "8 or better · 하이/로우 분할"
    GameMode.HOLDEM_NL -> "노리밋 텍사스 홀덤"
}

/** 모드 표지 이모지 — 카드 좌측 prominent 슬롯. */
private fun GameMode.glyph(): String = when (this) {
    GameMode.SEVEN_STUD -> "♠"
    GameMode.SEVEN_STUD_HI_LO -> "⚖"
    GameMode.HOLDEM_NL -> "♥"
}

@Composable
private fun ModeCard(
    mode: GameMode,
    enabled: Boolean = true,
    reasonIfDisabled: String? = null,
    onClick: () -> Unit,
) {
    // 7스터드 계열은 lime 보더로 한국식 시그니처 모드임을 표지. 홀덤은 기존 톤 유지.
    val isStud = mode == GameMode.SEVEN_STUD || mode == GameMode.SEVEN_STUD_HI_LO
    val accent = when (mode) {
        GameMode.SEVEN_STUD -> HangameColors.StudAccent
        GameMode.SEVEN_STUD_HI_LO -> HangameColors.HiLoHiBadge
        GameMode.HOLDEM_NL -> HangameColors.PotValue
    }
    // press feedback — scale + accent 톤 보더 강화. stud 모드는 휴면 상태에도 약한 보더 유지.
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed && enabled) MODE_CARD_PRESS_SCALE else 1f,
        animationSpec = tween(durationMillis = MODE_CARD_PRESS_TWEEN_MS),
        label = "mode-card-scale",
    )
    val borderAlpha by animateFloatAsState(
        targetValue = when {
            !enabled -> 0f
            isPressed -> MODE_CARD_BORDER_ALPHA_PRESSED
            isStud -> MODE_CARD_BORDER_ALPHA_STUD_REST
            else -> 0f
        },
        animationSpec = tween(
            durationMillis = if (isPressed) MODE_CARD_BORDER_FADE_IN_MS else MODE_CARD_BORDER_FADE_OUT_MS,
        ),
        label = "mode-card-border",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(104.dp)
            .scale(pressScale)
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) HangameColors.SeatBgActive else HangameColors.SeatBgFolded,
        ),
        border = if (borderAlpha > 0f)
            androidx.compose.foundation.BorderStroke(
                if (isPressed) 2.dp else 1.5.dp,
                accent.copy(alpha = borderAlpha),
            )
        else null,
        enabled = enabled,
        interactionSource = interactionSource,
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 좌측 슈트/모드 글리프 — 68dp 원형. 한게임의 "큰 카드 아트" 슬롯 대용.
            Box(
                modifier = Modifier
                    .height(68.dp)
                    .widthIn(min = 68.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (enabled) HangameColors.FeltMid else HangameColors.SeatBgFolded
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.glyph(),
                    color = if (enabled) accent else HangameColors.TextMuted,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
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
                Text(
                    text = if (!enabled && reasonIfDisabled != null) reasonIfDisabled
                    else mode.subtitle(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) HangameColors.TextSecondary
                    else HangameColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 우측 chevron — 입장 가능 시 lime 색 화살표.
            if (enabled) {
                Text(
                    text = "›",
                    color = accent,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineMedium,
                )
            }
        }
    }
}

private const val MODE_CARD_PRESS_SCALE = 0.97f
private const val MODE_CARD_PRESS_TWEEN_MS = 90
private const val MODE_CARD_BORDER_ALPHA_PRESSED = 0.95f
private const val MODE_CARD_BORDER_ALPHA_STUD_REST = 0.55f
private const val MODE_CARD_BORDER_FADE_IN_MS = 60
private const val MODE_CARD_BORDER_FADE_OUT_MS = 240
