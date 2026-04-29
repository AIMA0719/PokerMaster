package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 2단계 베팅 확인 다이얼로그 — v1.1 §1.2.E 오터치 방지.
 *
 *  - ALL_IN: 간단한 확인 문구 + 차감되는 칩(delta) 표시.
 *  - RAISE (큰 금액): 금액 포함 문구.
 */
@Composable
fun BettingConfirmDialog(
    type: ActionType,
    amount: Long,
    /** 본인 차감 칩(delta). 0이면 표시 생략. */
    deltaChips: Long = 0L,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val body = if (type == ActionType.ALL_IN)
        stringResource(id = R.string.betting_confirm_all_in)
    else
        stringResource(id = R.string.betting_confirm_large, ChipFormat.format(amount))

    val isDanger = type == ActionType.ALL_IN
    val confirmColor: Color = if (isDanger) HangameColors.Danger else Color.Unspecified

    AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = stringResource(id = R.string.betting_confirm_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            if (deltaChips > 0L) {
                Text(
                    text = "$body\n\n이 액션으로 ${ChipFormat.format(deltaChips)} 칩이 차감됩니다.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            } else {
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.betting_confirm_yes),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = confirmColor,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onCancel,
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text(
                    text = stringResource(id = R.string.betting_confirm_no),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun BettingConfirmDialogAllInPreview() {
    PokerMasterTheme {
        BettingConfirmDialog(
            type = ActionType.ALL_IN,
            amount = 8_500L,
            onConfirm = {},
            onCancel = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BettingConfirmDialogLargeRaisePreview() {
    PokerMasterTheme {
        BettingConfirmDialog(
            type = ActionType.RAISE,
            amount = 4_200L,
            onConfirm = {},
            onCancel = {},
        )
    }
}
