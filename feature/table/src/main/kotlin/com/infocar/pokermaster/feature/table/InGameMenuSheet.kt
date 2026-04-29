package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 인게임 플로팅 드롭다운 메뉴 — 좌상단 햄버거 아이콘 아래에 오버레이로 표시.
 *
 * 기존 ModalBottomSheet(전체 높이 차지) 에서 DropdownMenu 로 변경:
 * - 게임 테이블을 가리지 않음
 * - 좌상단에 컴팩트하게 표시
 */
@Composable
fun InGameMenuDropdown(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onSurrender: () -> Unit,
    onExit: () -> Unit,
    exitRequested: Boolean = false,
    guideEnabled: Boolean = true,
    onToggleGuide: () -> Unit = {},
    /** 룰/도움말 텍스트 컨텍스트(현재 모드). null이면 일반 텍스트. */
    mode: GameMode = GameMode.HOLDEM_NL,
    /** "포기"가 작동 가능한지(=인간 차례+활성). false면 메뉴 항목 비활성화. */
    canSurrender: Boolean = true,
) {
    var showRules by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var confirmSurrender by remember { mutableStateOf(false) }

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(id = R.string.menu_rules),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = { onDismiss(); showRules = true },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.MenuBook,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(id = R.string.menu_help),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = { onDismiss(); showHelp = true },
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = if (guideEnabled) "가이드 모드 끄기" else "가이드 모드 켜기",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = { onDismiss(); onToggleGuide() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                )
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(id = R.string.menu_surrender),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (canSurrender) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            },
            onClick = { onDismiss(); confirmSurrender = true },
            enabled = canSurrender,
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = if (canSurrender) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
                )
            },
        )
        DropdownMenuItem(
            text = {
                Text(
                    text = stringResource(
                        id = if (exitRequested) R.string.exit_queued else R.string.menu_exit,
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            onClick = { onDismiss(); onExit() },
            enabled = !exitRequested,
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = null,
                )
            },
        )
    }

    if (showRules) {
        RulesDialog(mode = mode, onDismiss = { showRules = false })
    }
    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
    if (confirmSurrender) {
        AlertDialog(
            onDismissRequest = { confirmSurrender = false },
            title = { Text("이번 핸드를 포기할까요?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "지금까지 배팅한 칩은 회수할 수 없으며 즉시 폴드 처리됩니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(
                    onClick = { confirmSurrender = false; onSurrender() },
                ) { Text("포기") }
            },
            dismissButton = {
                OutlinedButton(onClick = { confirmSurrender = false }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun RulesDialog(mode: GameMode, onDismiss: () -> Unit) {
    val title = when (mode) {
        GameMode.HOLDEM_NL -> "텍사스 홀덤 룰"
        GameMode.SEVEN_STUD -> "7카드 스터드 룰"
        GameMode.SEVEN_STUD_HI_LO -> "7카드 하이로우 룰"
    }
    val body = when (mode) {
        GameMode.HOLDEM_NL -> """
            • 각자 홀카드 2장 + 공유 커뮤니티 5장으로 5장 핸드 구성
            • 4스트릿: 프리플롭 → 플롭(3) → 턴(1) → 리버(1)
            • 노리밋: 임의 금액 raise 가능 (최소=직전 풀레이즈 크기 이상)
            • 라스트 액션 / 라이브 1명 시 즉시 핸드 종료
        """.trimIndent()
        GameMode.SEVEN_STUD -> """
            • 7장 스터드 (다운2 + 업4 + 다운1) — 베스트 5장으로 승부
            • 약한 업카드 좌석 강제 브링인, 이후 4구~7구 진행
            • 한국식 raise cap 3 — 스트릿당 최대 3회 raise
            • SAVE_LIFE("구사"): 콜 비용의 절반만 내고 쇼다운까지 동석 가능
        """.trimIndent()
        GameMode.SEVEN_STUD_HI_LO -> """
            • 7스터드 베이스 + 하이/로우 분할
            • 7구 종료 후 DECLARE — 하이/로우/스윙 중 선언 (잠김)
            • 로우 자격: 페어 없는 8 이하 (8 or better)
            • 스윙: 양쪽 모두 이겨야 챙김 — 한쪽 패배 시 0원
        """.trimIndent()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("도움말", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = """
                        • 액션바: 다이/체크·콜/쿼터·하프·풀팟 raise/올인. 큰 베팅은 한 번 더 확인합니다.
                        • 백키: 1번 = "이번 핸드 끝나고 나가기" 예약, 2번 = 즉시 종료.
                        • 무응답 60초가 지나면 자동 폴드/체크로 정리됩니다.
                        • 가이드 모드: 화면 안내 오버레이를 켜고 끌 수 있습니다.
                        • 화면 우상단 X: 이번 핸드 종료 후 로비 복귀.
                        • 통신 오류·예외 발생 시 토스트로 안내합니다.
                    """.trimIndent(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "InGameMenuDropdown placeholder")
@Composable
private fun InGameMenuDropdownPreview() {
    PokerMasterTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "InGameMenuDropdown — preview in emulator",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
