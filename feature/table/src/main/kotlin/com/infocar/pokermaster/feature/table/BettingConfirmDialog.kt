package com.infocar.pokermaster.feature.table

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 2단계 베팅 확인 다이얼로그 — v1.1 §1.2.E 오터치 방지.
 *
 *  - ALL_IN: 간단한 확인 문구.
 *  - RAISE (큰 금액): 금액 포함 문구.
 */
@Composable
fun BettingConfirmDialog(
    type: ActionType,
    amount: Long,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val body = if (type == ActionType.ALL_IN)
        stringResource(id = R.string.betting_confirm_all_in)
    else
        stringResource(id = R.string.betting_confirm_large, ChipFormat.format(amount))

    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(text = stringResource(id = R.string.betting_confirm_title)) },
        text = { Text(text = body) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(text = stringResource(id = R.string.betting_confirm_yes))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(id = R.string.betting_confirm_no))
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
