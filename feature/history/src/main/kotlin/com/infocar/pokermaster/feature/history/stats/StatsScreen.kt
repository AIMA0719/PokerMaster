package com.infocar.pokermaster.feature.history.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

/**
 * 통계 대시보드 화면 — M6-B.
 *
 * v1: 총 핸드, 승률, 팟 합/최대, 모드별 테이블. VPIP/PFR 같은 세부 지표는 actionsJson 분석 필요 (v2).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(
    onBack: () -> Unit,
    viewModel: StatsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("통계") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { inner ->
        if (!state.loaded) {
            Center(inner) { Text("불러오는 중…") }
            return@Scaffold
        }
        val o = state.overview
        if (o.totalHands == 0) {
            Center(inner) {
                Text(
                    "아직 기록된 핸드가 없어요.\n한 판 플레이하면 통계가 표시됩니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(12.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SummaryCard(overview = o)
            ModeBreakdownCard(overview = o)
        }
    }
}

@Composable
private fun Center(inner: PaddingValues, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun SummaryCard(overview: StatsOverview) {
    SectionCard("전체 요약") {
        MetricRow("총 핸드 수", overview.totalHands.toString())
        MetricRow("승리 핸드", "${overview.handsWon}")
        MetricRow("승률", formatPercent(overview.winrate))
        MetricRow("누적 팟 (칩)", formatChips(overview.totalPotChips))
        MetricRow("최대 팟 (칩)", formatChips(overview.biggestPot))
    }
}

@Composable
private fun ModeBreakdownCard(overview: StatsOverview) {
    SectionCard("모드별 통계") {
        if (overview.byMode.isEmpty()) {
            Text(
                "모드별 데이터 없음",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
            return@SectionCard
        }
        overview.byMode.forEach { (mode, ms) ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(mode, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                MetricRow("  핸드", ms.hands.toString())
                MetricRow("  승률", formatPercent(ms.winrate))
                MetricRow("  최대 팟", formatChips(ms.biggestPot))
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun formatPercent(p: Double): String = "%.1f%%".format(p * 100)

private fun formatChips(n: Long): String = when {
    n >= 1_000_000L -> "%.1fM".format(n / 1_000_000.0)
    n >= 1_000L -> "${n / 1_000L}k"
    else -> n.toString()
}
