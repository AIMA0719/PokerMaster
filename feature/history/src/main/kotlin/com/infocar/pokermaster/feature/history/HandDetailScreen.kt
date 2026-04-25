package com.infocar.pokermaster.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.data.history.ActionLogEntry
import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.ui.theme.HangameColors

/**
 * 핸드 상세 화면 — M5-D. 정적 요약: 헤더 / 초기 홀카드 / 커뮤니티 / 액션 로그 /
 * Provably Fair 검증 섹션. Step-by-step scrubber 는 후속 버전.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandDetailScreen(
    onBack: () -> Unit,
    viewModel: HandDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HangameColors.BgTop,
        topBar = {
            TopAppBar(
                title = { Text("핸드 상세", color = HangameColors.TextPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "뒤로",
                            tint = HangameColors.TextPrimary,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    titleContentColor = HangameColors.TextPrimary,
                    navigationIconContentColor = HangameColors.TextPrimary,
                ),
            )
        },
    ) { inner ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(HangameColors.BackgroundBrush)
                .padding(inner),
            contentAlignment = Alignment.TopCenter,
        ) {
            if (state.loading) {
                CenteredContent { Text("불러오는 중…", color = HangameColors.TextSecondary) }
                return@Box
            }
            if (state.notFound || state.record == null) {
                CenteredContent {
                    Text(
                        "해당 핸드를 찾을 수 없습니다.",
                        color = HangameColors.TextDanger,
                    )
                }
                return@Box
            }

            val record = state.record!!
            // step scrubber: 0 = 핸드 시작 직후 (액션 0건 진행), N = 모든 액션 진행 완료.
            var currentStep by remember(record.id) { mutableIntStateOf(record.actions.size) }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item { HeaderCard(record = record) }
                item { CardsCard(record = record) }
                if (record.actions.isNotEmpty()) {
                    item {
                        ScrubberCard(
                            totalSteps = record.actions.size,
                            currentStep = currentStep,
                            onStepChange = { currentStep = it },
                        )
                    }
                }
                item { ActionsCard(actions = record.actions, currentStep = currentStep) }
                item { ProvablyFairCard(record = record, seedVerified = state.seedVerified) }
            }
        }
    }
}

@Composable
private fun CenteredContent(content: @Composable () -> Unit) {
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun HeaderCard(record: HandHistoryRecord) {
    SectionCard(title = "#${record.handIndex} · ${record.mode}") {
        Text(
            "승자: " + (record.winnerSeat?.let { "seat $it" } ?: "무승부/사이드팟"),
            color = HangameColors.TextLime,
            fontWeight = FontWeight.SemiBold,
        )
        Text("pot: ${record.potSize}", color = HangameColors.TextChip)
        Text(
            text = "핸드 길이: ${(record.endedAt - record.startedAt) / 1000L}초",
            style = MaterialTheme.typography.bodySmall,
            color = HangameColors.TextSecondary,
        )
    }
}

@Composable
private fun CardsCard(record: HandHistoryRecord) {
    val state = record.initialState
    SectionCard(title = "초기 카드") {
        state.players.forEach { p ->
            if (p.holeCards.isNotEmpty()) {
                Text(
                    "seat ${p.seat} (${p.nickname}): " + p.holeCards.joinToString(" ") { it.short() },
                    color = HangameColors.TextPrimary,
                )
            }
        }
        if (state.community.isNotEmpty()) {
            Text(
                "community: " + state.community.joinToString(" ") { it.short() },
                style = MaterialTheme.typography.bodyMedium,
                color = HangameColors.TextLime,
            )
        }
    }
}

@Composable
private fun ActionsCard(actions: List<ActionLogEntry>, currentStep: Int = actions.size) {
    SectionCard(title = "액션 로그 (${actions.size})") {
        if (actions.isEmpty()) {
            Text(
                "액션 없음",
                style = MaterialTheme.typography.bodySmall,
                color = HangameColors.TextSecondary,
            )
            return@SectionCard
        }
        actions.forEachIndexed { idx, e ->
            val streetLabel = when (e.streetIndex) {
                0 -> "pre"
                1 -> "flop"
                2 -> "turn"
                3 -> "river"
                else -> "s${e.streetIndex}"
            }
            val amount = if (e.action.amount > 0L) " ${e.action.amount}" else ""
            // 도달한 액션은 진한색, 현재 액션은 lime 강조, 아직 미도달은 흐림.
            val color = when {
                idx + 1 == currentStep -> HangameColors.TextLime
                idx < currentStep -> HangameColors.TextPrimary
                else -> HangameColors.TextSecondary.copy(alpha = 0.4f)
            }
            val fontWeight = if (idx + 1 == currentStep) FontWeight.SemiBold else FontWeight.Normal
            Text(
                "[$streetLabel] seat ${e.seat} → ${e.action.type}$amount",
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = color,
                fontWeight = fontWeight,
            )
        }
    }
}

@Composable
private fun ScrubberCard(
    totalSteps: Int,
    currentStep: Int,
    onStepChange: (Int) -> Unit,
) {
    SectionCard(title = "리플레이 스크러버 ($currentStep / $totalSteps)") {
        Slider(
            value = currentStep.toFloat(),
            onValueChange = { onStepChange(it.toInt().coerceIn(0, totalSteps)) },
            valueRange = 0f..totalSteps.toFloat(),
            steps = (totalSteps - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = HangameColors.TextLime,
                activeTrackColor = HangameColors.TextLime,
                inactiveTrackColor = HangameColors.SeatBorder,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "슬라이더로 액션 시점을 이동하면 해당 시점까지의 로그가 강조됩니다.",
            style = MaterialTheme.typography.bodySmall,
            color = HangameColors.TextSecondary,
        )
    }
}

@Composable
private fun ProvablyFairCard(record: HandHistoryRecord, seedVerified: Boolean) {
    SectionCard(title = "Provably Fair (§3.5)") {
        Text(
            "commit: ${record.seedCommitHex.take(16)}…",
            fontFamily = FontFamily.Monospace,
            color = HangameColors.TextSecondary,
        )
        Text(
            "server: ${record.serverSeedHex.take(16)}…",
            fontFamily = FontFamily.Monospace,
            color = HangameColors.TextSecondary,
        )
        Text(
            "client: ${record.clientSeedHex.take(16)}…",
            fontFamily = FontFamily.Monospace,
            color = HangameColors.TextSecondary,
        )
        Text("nonce: ${record.nonce}", color = HangameColors.TextSecondary)
        Text(
            text = if (seedVerified) "✓ 검증 성공 (SHA-256 일치)" else "✗ 검증 실패",
            fontWeight = FontWeight.SemiBold,
            color = if (seedVerified) HangameColors.TextLime else HangameColors.TextDanger,
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = HangameColors.SeatBg,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HangameColors.TextPrimary,
            )
            content()
        }
    }
}

private fun Card.short(): String {
    val rankShort = when (rank) {
        com.infocar.pokermaster.core.model.Rank.TWO -> "2"
        com.infocar.pokermaster.core.model.Rank.THREE -> "3"
        com.infocar.pokermaster.core.model.Rank.FOUR -> "4"
        com.infocar.pokermaster.core.model.Rank.FIVE -> "5"
        com.infocar.pokermaster.core.model.Rank.SIX -> "6"
        com.infocar.pokermaster.core.model.Rank.SEVEN -> "7"
        com.infocar.pokermaster.core.model.Rank.EIGHT -> "8"
        com.infocar.pokermaster.core.model.Rank.NINE -> "9"
        com.infocar.pokermaster.core.model.Rank.TEN -> "T"
        com.infocar.pokermaster.core.model.Rank.JACK -> "J"
        com.infocar.pokermaster.core.model.Rank.QUEEN -> "Q"
        com.infocar.pokermaster.core.model.Rank.KING -> "K"
        com.infocar.pokermaster.core.model.Rank.ACE -> "A"
    }
    val suitLetter = when (suit) {
        com.infocar.pokermaster.core.model.Suit.SPADE -> "s"
        com.infocar.pokermaster.core.model.Suit.HEART -> "h"
        com.infocar.pokermaster.core.model.Suit.DIAMOND -> "d"
        com.infocar.pokermaster.core.model.Suit.CLUB -> "c"
    }
    return "$rankShort$suitLetter"
}
