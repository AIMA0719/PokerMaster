package com.infocar.pokermaster.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.ui.theme.HangameColors

/**
 * 히스토리 리스트 화면 — M5-C.
 *
 * `HandHistoryRepository.observeRecent` 의 Flow 를 [HistoryListViewModel] 에서 StateFlow 로 변환,
 * LazyColumn 으로 렌더. 각 행 클릭 → [onOpenDetail] (M5-D 상세 리플레이).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryListScreen(
    onBack: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    viewModel: HistoryListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HangameColors.BgTop,
        topBar = {
            TopAppBar(
                title = { Text("핸드 히스토리", color = HangameColors.TextPrimary) },
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
            if (!state.loaded) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "불러오는 중…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HangameColors.TextSecondary,
                    )
                }
                return@Box
            }
            if (state.items.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "아직 기록된 핸드가 없어요.\n한 판 플레이하면 여기에 표시됩니다.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HangameColors.TextSecondary,
                    )
                }
                return@Box
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.id }) { row ->
                    HandHistoryCard(row = row, onClick = { onOpenDetail(row.id) })
                }
            }
        }
    }
}

@Composable
private fun HandHistoryCard(row: HandHistoryRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HangameColors.SeatBg,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "#${row.handIndex} · ${row.mode}",
                    fontWeight = FontWeight.SemiBold,
                    color = HangameColors.TextPrimary,
                )
                Text(
                    row.startedAtDisplay,
                    style = MaterialTheme.typography.bodySmall,
                    color = HangameColors.TextSecondary,
                )
            }
            Text(
                "${row.winnerDisplay} · pot ${formatChips(row.potSize)}",
                style = MaterialTheme.typography.bodyMedium,
                color = HangameColors.TextChip,
            )
        }
    }
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
