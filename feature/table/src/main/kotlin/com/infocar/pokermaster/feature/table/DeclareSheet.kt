package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.infocar.pokermaster.core.model.Declaration
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.a11y.A11yStrings
import java.util.concurrent.atomic.AtomicLong

/**
 * 7-Stud Hi-Lo 한국식 declare 시트.
 *
 *  - HIGH / LOW / SWING 3택 — 사람 좌석이 declare 단계에서만 노출.
 *  - 시트가 떠 있는 동안 일반 [ActionBar] 는 [TableScreen] 분기에서 숨김.
 *  - 버튼 스타일은 [ActionBar] 와 동일한 그라데이션 + 보더 + 폰트로 통일.
 *  - tap race condition: ActionBar 와 동일한 [AtomicLong] CAS 가드 사용. 빠른 연타나
 *    재컴포지션이 겹쳐도 같은 declare 가 두 번 디스패치되지 않음.
 */
@Composable
fun DeclareSheet(
    onDeclare: (Declaration) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, HangameColors.SeatBorder),
        shadowElevation = 8.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "선언 — 하이 / 로우 / 스윙",
                fontSize = 13.sp,
                color = HangameColors.TextSecondary,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DeclareButton(
                    label = "하이",
                    tint = HangameColors.BtnHalf,
                    tintDark = HangameColors.BtnHalfDark,
                    a11y = A11yStrings.declareButton(Declaration.HIGH),
                    onClick = { onDeclare(Declaration.HIGH) },
                    modifier = Modifier.weight(1f),
                )
                DeclareButton(
                    label = "로우",
                    tint = HangameColors.BtnCall,
                    tintDark = HangameColors.BtnCallDark,
                    a11y = A11yStrings.declareButton(Declaration.LOW),
                    onClick = { onDeclare(Declaration.LOW) },
                    modifier = Modifier.weight(1f),
                )
                DeclareButton(
                    label = "스윙",
                    tint = HangameColors.BtnAllIn,
                    tintDark = HangameColors.BtnAllInDark,
                    a11y = A11yStrings.declareButton(Declaration.SWING),
                    onClick = { onDeclare(Declaration.SWING) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun DeclareButton(
    label: String,
    tint: Color,
    tintDark: Color,
    a11y: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.verticalGradient(listOf(tint, tintDark))
    // ActionBar 와 동일한 race-safe 가드: AtomicLong.compareAndSet 으로 atomic check-and-update.
    // 같은 컴포지션 안에서 빠른 연타 / 재컴포지션 race 모두 안전.
    val lastTapAt = remember { AtomicLong(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(gradient)
            .border(
                BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                RoundedCornerShape(8.dp),
            )
            .pointerInput(Unit) {
                detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    val prev = lastTapAt.get()
                    if (now - prev >= DECLARE_TAP_THROTTLE_MS && lastTapAt.compareAndSet(prev, now)) {
                        onClick()
                    }
                })
            }
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            color = HangameColors.TextPrimary,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** Declare 버튼 디바운스 시간 (ms). ActionBar 와 동일 정책. */
private const val DECLARE_TAP_THROTTLE_MS = 350L

@Preview(showBackground = true, widthDp = 720, heightDp = 120, backgroundColor = 0xFF0B2D52)
@Composable
private fun DeclareSheetPreview() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            DeclareSheet(onDeclare = {}, modifier = Modifier.fillMaxWidth())
        }
    }
}
