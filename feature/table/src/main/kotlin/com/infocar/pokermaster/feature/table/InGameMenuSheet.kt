package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 인게임 메뉴 시트 — 테이블 화면 우상단 햄버거에서 호출.
 *
 * 메뉴 (v1.1 §5.11):
 *  1. 규칙 (M5 에서 실화면 연결 예정 → 지금은 onDismiss 만).
 *  2. 도움말 (마찬가지).
 *  3. 핸드 항복 → onSurrender.
 *  4. 테이블 나가기 → onExit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InGameMenuSheet(
    onDismiss: () -> Unit,
    onSurrender: () -> Unit,
    onExit: () -> Unit,
    guideEnabled: Boolean = true,
    onToggleGuide: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            MenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = stringResource(id = R.string.menu_rules),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onDismiss,
            )
            MenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = stringResource(id = R.string.menu_help),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onDismiss,
            )
            MenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = if (guideEnabled) "가이드 모드 끄기" else "가이드 모드 켜기",
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onToggleGuide,
            )
            MenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.Default.Flag,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                label = stringResource(id = R.string.menu_surrender),
                tint = MaterialTheme.colorScheme.error,
                onClick = onSurrender,
            )
            MenuRow(
                icon = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                },
                label = stringResource(id = R.string.menu_exit),
                tint = MaterialTheme.colorScheme.onSurface,
                onClick = onExit,
            )
        }
    }
}

@Composable
private fun MenuRow(
    icon: @Composable () -> Unit,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        leadingContent = icon,
        headlineContent = {
            Text(
                text = label,
                color = tint,
                style = MaterialTheme.typography.bodyLarge,
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surface,
        ),
    )
}

// ---------------------------------------------------------------------------
// Preview — ModalBottomSheet 은 Preview 에서 렌더링이 제한적이므로 플레이스홀더.
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "InGameMenuSheet placeholder")
@Composable
private fun InGameMenuSheetPreview() {
    PokerMasterTheme {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "InGameMenuSheet — preview in emulator",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
