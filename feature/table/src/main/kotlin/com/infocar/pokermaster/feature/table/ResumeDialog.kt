package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * v1.1 §1.2.D — 진행 중이던 핸드 복원 다이얼로그.
 *
 *  - [onResume]: 이어하기 → Controller 를 snapshot 으로 재구성.
 *  - [onDiscard]: 포기하고 새 핸드 → snapshot 삭제 + 새 핸드 시작.
 *  - [onCancel]: 취소 → snapshot 유지하되 이번엔 새 핸드 시작 (나중에 다시 물어봄).
 */
@Composable
fun ResumeDialog(
    prompt: ResumePrompt,
    onResume: () -> Unit,
    onDiscard: () -> Unit,
    onCancel: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text("이어서 플레이할까요?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "지난번 진행 중이던 핸드가 남아 있습니다.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(8.dp))
                ResumeRow(label = "모드", value = prompt.modeName)
                ResumeRow(label = "핸드", value = "#${prompt.handIndex}")
                ResumeRow(label = "팟", value = ChipFormat.format(prompt.potSize))
                ResumeRow(label = "내 칩", value = ChipFormat.format(prompt.myChips))
            }
        },
        confirmButton = {
            TextButton(onClick = onResume) { Text("이어하기") }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(onClick = onCancel) { Text("취소") }
                TextButton(onClick = onDiscard) { Text("포기") }
            }
        },
    )
}

@Composable
private fun ResumeRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ResumeDialogPreview() {
    PokerMasterTheme {
        ResumeDialog(
            prompt = ResumePrompt(
                potSize = 1_200L,
                myChips = 8_400L,
                modeName = "HOLDEM_NL",
                handIndex = 5L,
            ),
            onResume = {},
            onDiscard = {},
            onCancel = {},
        )
    }
}
