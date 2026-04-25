package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme

/**
 * "기본 AI 모드" 폴백 배지 — v1.1 §1.2.N.
 *
 * M4 이전엔 LLM 연동 없이 결정형 코어만 사용하므로 showAlways 기본 true.
 * 점선 테두리 + 작은 로봇 아이콘 + 라벨.
 */
@Composable
fun FallbackModeBadge(
    modifier: Modifier = Modifier,
    showAlways: Boolean = true,
) {
    if (!showAlways) return

    val cornerRadius = 12.dp
    val outline = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
    val background = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(background),
    ) {
        // 점선 테두리 (Canvas 로 그려서 dashed 효과).
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRoundRect(
                color = outline,
                style = Stroke(
                    width = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 4f), 0f),
                ),
                cornerRadius = CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
            )
        }

        Row(
            modifier = Modifier.padding(PaddingValues(horizontal = 10.dp, vertical = 5.dp)),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Default.SmartToy,
                contentDescription = stringResource(id = R.string.badge_fallback_ai),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
                modifier = Modifier.padding(0.dp),
            )
            Text(
                text = stringResource(id = R.string.badge_fallback_ai),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Preview
// ---------------------------------------------------------------------------

@Preview(showBackground = true, name = "Fallback badge")
@Composable
private fun FallbackModeBadgePreview() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(24.dp)) {
            FallbackModeBadge()
        }
    }
}
