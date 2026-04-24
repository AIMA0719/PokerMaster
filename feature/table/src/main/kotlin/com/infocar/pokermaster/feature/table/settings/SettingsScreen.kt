package com.infocar.pokermaster.feature.table.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.feature.table.a11y.ColorblindMode

/**
 * 설정 화면 — M6-A (§1.2.H 축약판).
 *
 * 섹션: 일반(효과음/햅틱) · 접근성(색각/큰글씨/모션) · 가이드 · 데이터 · 정보.
 * 모델 관리 (§1.2.Q) 와 알림 (§1.2.H.알림) 은 별도 스프린트에서.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    versionName: String = "dev",
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clearedCount by viewModel.lastClearedCount.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("설정") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "뒤로")
                    }
                },
            )
        },
    ) { inner ->
        if (!state.loaded) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.Center,
            ) { Text("불러오는 중…") }
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
            SectionCard("일반") {
                ToggleRow(
                    label = "효과음",
                    checked = state.sfx.soundEnabled,
                    onChange = viewModel::setSoundEnabled,
                )
                ToggleRow(
                    label = "햅틱",
                    checked = state.sfx.hapticEnabled,
                    onChange = viewModel::setHapticEnabled,
                )
            }

            SectionCard("접근성") {
                Text("색각 모드", style = MaterialTheme.typography.bodyMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ColorblindMode.entries.forEach { mode ->
                        FilterChip(
                            selected = state.a11y.colorblindMode == mode,
                            onClick = { viewModel.setColorblindMode(mode) },
                            label = { Text(mode.name) },
                        )
                    }
                }
                ToggleRow(
                    label = "큰 글씨",
                    checked = state.a11y.largerText,
                    onChange = viewModel::setLargerText,
                )
                ToggleRow(
                    label = "애니메이션 최소화",
                    checked = state.a11y.reduceMotion,
                    onChange = viewModel::setReduceMotion,
                )
            }

            SectionCard("가이드") {
                ToggleRow(
                    label = "게임 중 가이드 오버레이",
                    checked = state.guide.guideModeEnabled,
                    onChange = viewModel::setGuideMode,
                )
            }

            SectionCard("데이터") {
                Text(
                    "핸드 히스토리 전체 삭제. 되돌릴 수 없습니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
                OutlinedButton(
                    onClick = { confirmClear = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("핸드 히스토리 전체 삭제")
                }
            }

            SectionCard("정보") {
                LabeledRow(label = "앱 버전", value = versionName)
                LabeledRow(label = "LLM 런타임", value = "llama.cpp b8870 (static)")
                Text(
                    "이 앱은 Meta Llama 3.2 모델을 사용합니다 (Llama Community License).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                )
            }

            Spacer(Modifier.height(24.dp))
        }

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("히스토리 전체 삭제") },
                text = { Text("모든 핸드 기록이 영구적으로 지워집니다. 계속 하시겠습니까?") },
                confirmButton = {
                    Button(onClick = {
                        viewModel.clearAllHistory()
                        confirmClear = false
                    }) { Text("삭제") }
                },
                dismissButton = {
                    OutlinedButton(onClick = { confirmClear = false }) { Text("취소") }
                },
            )
        }

        if (clearedCount != null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(inner),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Snackbar(
                    modifier = Modifier.padding(16.dp),
                    action = {
                        Button(onClick = { viewModel.acknowledgeCleared() }) { Text("확인") }
                    },
                ) { Text("${clearedCount}건의 기록을 삭제했습니다.") }
            }
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
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            HorizontalDivider()
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
