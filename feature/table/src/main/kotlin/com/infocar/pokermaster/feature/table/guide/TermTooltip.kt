package com.infocar.pokermaster.feature.table.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * 용어 상세 툴팁 다이얼로그 — v1.1 §1.2.
 *
 * 칩/텍스트 탭 시 표시. 제목 + longDesc + 확인 버튼 구조.
 * 가이드 모드 OFF 상태에서도 호출 가능하도록 별도 컴포넌트로 분리.
 */
@Composable
fun TermTooltip(
    term: Term,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = term.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = term.shortDesc,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = term.longDesc,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "확인")
            }
        },
    )
}

/**
 * 본문에 박아 넣는 클릭 가능한 용어 칩.
 *
 * [termKey] 는 [Glossary] 의 key. 잘못된 키면 [Glossary.require] 가 실패하므로
 * 호출부에서 상수 참조를 권장한다.
 *
 * @param onClick 칩 탭 시 호출. 보통 [TermTooltip] 을 띄우는 상태 업데이트에 연결한다.
 */
@Composable
fun TermChip(
    termKey: String,
    onClick: (Term) -> Unit,
    modifier: Modifier = Modifier,
) {
    val term = Glossary.require(termKey)
    AssistChip(
        onClick = { onClick(term) },
        label = { Text(text = term.title) },
        modifier = modifier.padding(horizontal = 2.dp),
        colors = AssistChipDefaults.assistChipColors(),
    )
}

@Preview(showBackground = true)
@Composable
private fun TermTooltipPreview() {
    PokerMasterTheme {
        TermTooltip(
            term = Glossary.SidePot,
            onDismiss = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TermChipPreview() {
    PokerMasterTheme {
        TermChip(
            termKey = Glossary.BigBlind.key,
            onClick = {},
        )
    }
}
