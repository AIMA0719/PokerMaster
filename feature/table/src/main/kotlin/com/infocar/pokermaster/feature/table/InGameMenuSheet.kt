package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
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
) {
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
            onClick = onDismiss,
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
            onClick = onDismiss,
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
                    color = MaterialTheme.colorScheme.error,
                )
            },
            onClick = { onDismiss(); onSurrender() },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
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
