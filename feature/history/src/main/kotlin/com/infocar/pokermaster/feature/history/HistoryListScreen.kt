package com.infocar.pokermaster.feature.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

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
        topBar = {
            TopAppBar(
                title = { Text("핸드 히스토리") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { inner ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text("불러오는 중…", style = MaterialTheme.typography.bodyLarge)
            }
            return@Scaffold
        }
        if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(
                    "아직 기록된 핸드가 없어요.\n한 판 플레이하면 여기에 표시됩니다.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(state.items, key = { it.id }) { row ->
                HandHistoryCard(row = row, onClick = { onOpenDetail(row.id) })
            }
        }
    }
}

@Composable
private fun HandHistoryCard(row: HandHistoryRow, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("#${row.handIndex} · ${row.mode}", fontWeight = FontWeight.SemiBold)
                Text(row.startedAtDisplay, style = MaterialTheme.typography.bodySmall)
            }
            Text("${row.winnerDisplay} · pot ${formatChips(row.potSize)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
        }
    }
}

private fun formatChips(n: Long): String =
    if (n >= 1000L) "${n / 1000L}k" else n.toString()
