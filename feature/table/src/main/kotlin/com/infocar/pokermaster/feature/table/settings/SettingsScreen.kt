package com.infocar.pokermaster.feature.table.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.ThemeMode
import com.infocar.pokermaster.feature.table.a11y.ColorblindMode
import kotlinx.coroutines.launch

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
    /** AI 모델 검증 콜백. null 이면 "AI 모델" 섹션 노출 안 함 (테스트/Preview). */
    onVerifyModel: (suspend () -> String)? = null,
    /** AI 모델 삭제 콜백. true=삭제 성공. */
    onDeleteModel: (suspend () -> Boolean)? = null,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val clearedCount by viewModel.lastClearedCount.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = HangameColors.BgTop,
        topBar = {
            TopAppBar(
                title = { Text("설정", color = HangameColors.TextPrimary) },
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
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { Text("불러오는 중…", color = HangameColors.TextSecondary) }
                return@Box
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SectionCard("테마") {
                    Text(
                        "앱 색상 모드. 기본은 라이트입니다.",
                        style = MaterialTheme.typography.bodySmall,
                        color = HangameColors.TextSecondary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label()) },
                                colors = hangameFilterChipColors(),
                            )
                        }
                    }
                }

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
                    Text(
                        "색각 모드",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorblindMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.a11y.colorblindMode == mode,
                                onClick = { viewModel.setColorblindMode(mode) },
                                label = { Text(mode.name) },
                                colors = hangameFilterChipColors(),
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
                    ToggleRow(
                        label = "고대비 카드",
                        checked = state.a11y.highContrastCards,
                        onChange = viewModel::setHighContrastCards,
                    )
                    ToggleRow(
                        label = "액션 음성 안내 (TalkBack)",
                        checked = state.a11y.announceActionsAudibly,
                        onChange = viewModel::setAnnounceActions,
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
                        color = HangameColors.TextSecondary,
                    )
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("핸드 히스토리 전체 삭제")
                    }
                }

                if (onVerifyModel != null || onDeleteModel != null) {
                    val mScope = rememberCoroutineScope()
                    val mCtx = LocalContext.current
                    SectionCard("AI 모델") {
                        Text(
                            "다운로드된 LLM 모델 파일의 무결성을 확인하거나 강제 삭제(다음 진입 시 재다운로드).",
                            style = MaterialTheme.typography.bodySmall,
                            color = HangameColors.TextSecondary,
                        )
                        if (onVerifyModel != null) {
                            OutlinedButton(
                                onClick = {
                                    mScope.launch {
                                        val msg = onVerifyModel()
                                        android.widget.Toast.makeText(
                                            mCtx, msg, android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("모델 파일 검증") }
                        }
                        if (onDeleteModel != null) {
                            OutlinedButton(
                                onClick = {
                                    mScope.launch {
                                        val ok = onDeleteModel()
                                        android.widget.Toast.makeText(
                                            mCtx,
                                            if (ok) "모델 파일을 삭제했습니다. 다음 진입 시 재다운로드됩니다."
                                            else "삭제 실패 또는 파일이 없습니다.",
                                            android.widget.Toast.LENGTH_LONG,
                                        ).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("모델 파일 삭제") }
                        }
                    }
                }

                SectionCard("정보") {
                    LabeledRow(label = "앱 버전", value = versionName)
                    LabeledRow(label = "LLM 런타임", value = "llama.cpp b8870 (static)")
                    Text(
                        "이 앱은 Meta Llama 3.2 모델을 사용합니다 (Llama Community License).",
                        style = MaterialTheme.typography.bodySmall,
                        color = HangameColors.TextSecondary,
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
                    modifier = Modifier.fillMaxSize(),
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
}

@Composable
private fun hangameFilterChipColors() = FilterChipDefaults.filterChipColors(
    containerColor = HangameColors.SeatBg,
    labelColor = HangameColors.TextSecondary,
    selectedContainerColor = HangameColors.SeatBgActive,
    selectedLabelColor = HangameColors.TextLime,
)

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = HangameColors.SeatBg,
        ),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = HangameColors.TextPrimary,
            )
            HorizontalDivider(color = HangameColors.SeatBorder)
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
        Text(label, style = MaterialTheme.typography.bodyLarge, color = HangameColors.TextPrimary)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "라이트"
    ThemeMode.DARK -> "다크"
    ThemeMode.SYSTEM -> "시스템"
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = HangameColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = HangameColors.TextChip)
    }
}
