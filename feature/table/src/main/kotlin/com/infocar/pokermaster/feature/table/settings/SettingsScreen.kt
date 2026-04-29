package com.infocar.pokermaster.feature.table.settings

import androidx.annotation.RawRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
                ) {
                    Text(
                        "불러오는 중…",
                        style = MaterialTheme.typography.bodyLarge,
                        color = HangameColors.TextSecondary,
                    )
                }
                return@Box
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 720.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SectionCard("테마") {
                    Text(
                        "앱 색상 모드. 기본은 라이트입니다.",
                        style = MaterialTheme.typography.bodyMedium,
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
                                label = {
                                    Text(
                                        mode.label(),
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                colors = hangameFilterChipColors(),
                            )
                        }
                    }
                }

                SectionCard("외관") {
                    Text(
                        "이미지 카드(베타). CC0 카드 페이스 PNG 자산을 사용. 끄면 기본 자체 렌더링.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextSecondary,
                    )
                    ToggleRow(
                        label = "이미지 카드 사용",
                        checked = state.useImageCards,
                        onChange = viewModel::setUseImageCards,
                    )
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
                    ToggleRow(
                        label = "배경음악 (BGM)",
                        checked = state.sfx.bgmEnabled,
                        onChange = viewModel::setBgmEnabled,
                    )
                    Text(
                        "BGM 자산이 설치된 경우에만 재생됩니다 (자산 미설치 시 무음).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextMuted,
                    )
                }

                SectionCard("접근성") {
                    Text(
                        "색각 모드",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        color = HangameColors.TextPrimary,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ColorblindMode.entries.forEach { mode ->
                            FilterChip(
                                selected = state.a11y.colorblindMode == mode,
                                onClick = { viewModel.setColorblindMode(mode) },
                                label = {
                                    Text(
                                        mode.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
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

                // 책임 있는 게임 — 청소년 보호·과몰입 방지.
                val selfLimit by viewModel.selfLimit.collectAsState()
                SectionCard("책임 있는 게임") {
                    Text(
                        "본 게임은 가상 칩으로 진행되며 실제 금전 거래는 없습니다. 미성년자(만 18세 미만) 사용을 권장하지 않으며, 과몰입을 방지하기 위해 아래 자기 제한 옵션을 활용하세요.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextSecondary,
                    )
                    SelfLimitChoiceRow(
                        label = "휴식 알림 주기",
                        options = listOf(0 to "끔", 15 to "15분", 30 to "30분", 60 to "60분"),
                        selected = selfLimit.breakReminderMinutes,
                        onSelect = viewModel::setBreakReminderMinutes,
                    )
                    SelfLimitChoiceRow(
                        label = "일일 핸드 한도",
                        options = listOf(0 to "무제한", 50 to "50핸드", 100 to "100핸드", 200 to "200핸드"),
                        selected = selfLimit.dailyHandLimit,
                        onSelect = viewModel::setDailyHandLimit,
                    )
                    SelfLimitChoiceRow(
                        label = "일일 누적 시간 한도",
                        options = listOf(0 to "무제한", 30 to "30분", 60 to "60분", 120 to "2시간"),
                        selected = selfLimit.dailySessionMinutes,
                        onSelect = viewModel::setDailySessionMinutes,
                    )
                    Text(
                        "한도 초과 시 테이블 진행이 일시 중단되고 휴식을 안내합니다.",
                        style = MaterialTheme.typography.labelSmall,
                        color = HangameColors.TextMuted,
                    )
                }

                SectionCard("데이터") {
                    Text(
                        "핸드 히스토리 전체 삭제. 되돌릴 수 없습니다.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextSecondary,
                    )
                    OutlinedButton(
                        onClick = { confirmClear = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 48.dp),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(
                            "핸드 히스토리 전체 삭제",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (onVerifyModel != null || onDeleteModel != null) {
                    val mScope = rememberCoroutineScope()
                    var modelDialog by remember { mutableStateOf<String?>(null) }
                    var confirmDeleteModel by remember { mutableStateOf(false) }
                    SectionCard("AI 모델") {
                        Text(
                            "다운로드된 LLM 모델 파일의 무결성을 확인하거나 강제 삭제(다음 진입 시 재다운로드).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = HangameColors.TextSecondary,
                        )
                        if (onVerifyModel != null) {
                            OutlinedButton(
                                onClick = {
                                    mScope.launch { modelDialog = onVerifyModel() }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Text(
                                    "모델 파일 검증",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (onDeleteModel != null) {
                            OutlinedButton(
                                onClick = { confirmDeleteModel = true },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = 48.dp),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = HangameColors.Danger,
                                ),
                            ) {
                                Text(
                                    "모델 파일 삭제",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = HangameColors.Danger,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (confirmDeleteModel && onDeleteModel != null) {
                        AlertDialog(
                            onDismissRequest = { confirmDeleteModel = false },
                            title = { Text("모델 파일 삭제") },
                            text = {
                                Text("LLM 모델 파일을 삭제합니다. 다음 진입 시 ~수백 MB 재다운로드가 필요합니다. 계속할까요?")
                            },
                            confirmButton = {
                                Button(onClick = {
                                    confirmDeleteModel = false
                                    mScope.launch {
                                        val ok = onDeleteModel()
                                        modelDialog = if (ok) "모델 파일을 삭제했습니다. 다음 진입 시 재다운로드됩니다."
                                        else "삭제 실패 또는 파일이 없습니다."
                                    }
                                }) { Text("삭제") }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { confirmDeleteModel = false }) { Text("취소") }
                            },
                        )
                    }
                    modelDialog?.let { msg ->
                        AlertDialog(
                            onDismissRequest = { modelDialog = null },
                            title = { Text("AI 모델") },
                            text = { Text(msg) },
                            confirmButton = { Button(onClick = { modelDialog = null }) { Text("확인") } },
                        )
                    }
                }

                // 법적 고지 섹션. 4개 항목 → 다이얼로그로 raw resource 표시.
                var legalDialog by remember { mutableStateOf<Pair<Int, String>?>(null) }
                SectionCard("법적 고지") {
                    LegalRow("오픈소스 라이선스") {
                        legalDialog = com.infocar.pokermaster.feature.table.R.raw.legal_open_source to "오픈소스 라이선스"
                    }
                    LegalRow("개인정보 처리방침") {
                        legalDialog = com.infocar.pokermaster.feature.table.R.raw.legal_privacy to "개인정보 처리방침"
                    }
                    LegalRow("이용약관") {
                        legalDialog = com.infocar.pokermaster.feature.table.R.raw.legal_terms to "이용약관"
                    }
                    LegalRow("책임 있는 게임") {
                        legalDialog = com.infocar.pokermaster.feature.table.R.raw.legal_responsible_gaming to "책임 있는 게임"
                    }
                }
                legalDialog?.let { (resId, title) ->
                    LegalDialog(resId = resId, title = title, onDismiss = { legalDialog = null })
                }

                SectionCard("정보") {
                    LabeledRow(label = "앱 버전", value = versionName)
                    LabeledRow(label = "LLM 런타임", value = "llama.cpp b8870 (static)")
                    Text(
                        "Built with Llama — Meta Llama 3.2 1B-Instruct (Q4_K_M).",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextSecondary,
                    )
                    Text(
                        "문의: tech.infocar@gmail.com",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextMuted,
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            if (confirmClear) {
                AlertDialog(
                    onDismissRequest = { confirmClear = false },
                    title = {
                        Text(
                            "히스토리 전체 삭제",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    text = {
                        Text(
                            "모든 핸드 기록이 영구적으로 지워집니다. 계속 하시겠습니까?",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.clearAllHistory()
                                confirmClear = false
                            },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = HangameColors.Danger),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "삭제",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    },
                    dismissButton = {
                        OutlinedButton(
                            onClick = { confirmClear = false },
                            modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                        ) {
                            Text(
                                "취소",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
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
                            Button(
                                onClick = { viewModel.acknowledgeCleared() },
                                modifier = Modifier.defaultMinSize(minHeight = 40.dp),
                            ) {
                                Text(
                                    "확인",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        },
                    ) {
                        Text(
                            "${clearedCount}건의 기록을 삭제했습니다.",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
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
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = HangameColors.TextPrimary,
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = HangameColors.SeatBorder,
            )
            content()
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = HangameColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = true)
                .padding(end = 12.dp),
        )
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private fun ThemeMode.label(): String = when (this) {
    ThemeMode.LIGHT -> "라이트"
    ThemeMode.DARK -> "다크"
    ThemeMode.SYSTEM -> "시스템"
}

@Composable
private fun SelfLimitChoiceRow(
    label: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = HangameColors.TextPrimary,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (value, lbl) ->
                FilterChip(
                    selected = selected == value,
                    onClick = { onSelect(value) },
                    label = { Text(lbl) },
                    colors = hangameFilterChipColors(),
                )
            }
        }
    }
}

@Composable
private fun LabeledRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = HangameColors.TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = HangameColors.TextChip,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LegalRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .defaultMinSize(minHeight = 48.dp)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = HangameColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = true)
                .padding(end = 12.dp),
        )
        Text(
            "›",
            style = MaterialTheme.typography.titleLarge,
            color = HangameColors.TextSecondary,
        )
    }
}

@Composable
private fun LegalDialog(
    @RawRes resId: Int,
    title: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val text = remember(resId) {
        context.resources.openRawResource(resId).bufferedReader().use { it.readText() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 480.dp)) {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = HangameColors.TextPrimary,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Text(
                    "닫기",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}
