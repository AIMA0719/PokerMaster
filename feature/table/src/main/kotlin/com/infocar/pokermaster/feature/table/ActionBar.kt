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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
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
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.ui.theme.HangameColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.a11y.A11yStrings
import com.infocar.pokermaster.feature.table.anim.pressScale
import kotlin.math.roundToLong

/**
 * 한게임 풍 6버튼 액션바 — v3.
 *
 *  배치: 상단 [다이 | 체크 | 콜], 하단 [쿼터 | 하프 | 팟 | 올인].
 *  - 한게임 포커처럼 기본 액션과 베팅 크기 선택을 분리해 모바일에서 금액 라벨 절단을 줄인다.
 *  - 체크박스 인디케이터(□) 제거 — 단순화.
 *  - 비활성 상태는 회색 + 라벨 흐리게.
 *  - 큰 raise / all-in 은 [onRequestConfirm] 으로 2단계 확인 (기존 로직 유지).
 */
@Composable
fun ActionBar(
    state: ActionBarState,
    onAction: OnAction,
    onRequestConfirm: (ActionType, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val potSize = state.potSize.coerceAtLeast(1L)
    val callAbs = state.currentCommitted + state.callAmount

    val quarterAmount = clampRaise(state, callAbs + (potSize.toDouble() * 0.25).roundToLong())
    val halfAmount = clampRaise(state, callAbs + (potSize.toDouble() * 0.5).roundToLong())
    val potAmount = clampRaise(state, callAbs + potSize)
    val allInAmount = state.maxRaiseTotal

    // 액션 dispatch helper — raise 류는 80% chip 이상이면 confirm.
    val dispatchRaise: (Long) -> Unit = { amount ->
        val isAllIn = amount >= state.maxRaiseTotal
        val delta = (amount - state.currentCommitted).coerceAtLeast(0L)
        val needsConfirm = isAllIn ||
            (state.myChips > 0 && delta >= (state.myChips.toDouble() * 0.8).roundToLong())
        val type = if (isAllIn) ActionType.ALL_IN else ActionType.RAISE
        if (needsConfirm) onRequestConfirm(type, amount) else onAction(Action(type, amount))
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                label = "다이",
                tint = HangameColors.BtnFold,
                tintDark = HangameColors.BtnFoldDark,
                enabled = true,
                a11y = A11yStrings.actionButton(ActionType.FOLD),
                onClick = { onAction(Action(ActionType.FOLD)) },
                modifier = Modifier.weight(1f),
            )
            // 체크 — canCheck 시만 활성. 상대 올인이라도 본인이 이미 매치(callAmount=0)면
            // canCheck=true 라 활성됨. NL 홀덤 표준 룰 그대로.
            ActionButton(
                label = "체크",
                tint = HangameColors.BtnCall,
                tintDark = HangameColors.BtnCallDark,
                enabled = state.canCheck,
                a11y = A11yStrings.actionButton(ActionType.CHECK),
                onClick = { onAction(Action(ActionType.CHECK)) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = if (state.canCall) "콜 ${ChipFormat.format(state.callAmount)}" else "콜",
                tint = HangameColors.BtnCall,
                tintDark = HangameColors.BtnCallDark,
                enabled = state.canCall,
                a11y = A11yStrings.actionButton(ActionType.CALL, toCall = state.callAmount),
                onClick = { onAction(Action(ActionType.CALL)) },
                modifier = Modifier.weight(1f),
            )
            // 7스터드/HiLo 전용 "구사" 버튼 — 홀덤에서는 false.
            if (state.canSaveLife) {
                ActionButton(
                    label = "구사",
                    tint = HangameColors.BtnFold,
                    tintDark = HangameColors.BtnFoldDark,
                    enabled = true,
                    a11y = "구사 (한국식 7스터드)",
                    onClick = { onAction(Action(ActionType.SAVE_LIFE)) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActionButton(
                label = "쿼터 ${ChipFormat.format(quarterAmount)}",
                tint = HangameColors.BtnQuarter,
                tintDark = HangameColors.BtnQuarterDark,
                enabled = state.canRaise && quarterAmount in state.minRaiseTotal..state.maxRaiseTotal,
                a11y = A11yStrings.actionButton(ActionType.RAISE, amount = quarterAmount),
                onClick = { dispatchRaise(quarterAmount) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "하프 ${ChipFormat.format(halfAmount)}",
                tint = HangameColors.BtnHalf,
                tintDark = HangameColors.BtnHalfDark,
                enabled = state.canRaise && halfAmount in state.minRaiseTotal..state.maxRaiseTotal,
                a11y = A11yStrings.actionButton(ActionType.RAISE, amount = halfAmount),
                onClick = { dispatchRaise(halfAmount) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "팟 ${ChipFormat.format(potAmount)}",
                tint = HangameColors.BtnQuarter,
                tintDark = HangameColors.BtnQuarterDark,
                enabled = state.canRaise && potAmount in state.minRaiseTotal..state.maxRaiseTotal,
                a11y = A11yStrings.actionButton(ActionType.RAISE, amount = potAmount),
                onClick = { dispatchRaise(potAmount) },
                modifier = Modifier.weight(1f),
            )
            ActionButton(
                label = "올인 ${ChipFormat.format(allInAmount)}",
                tint = HangameColors.BtnAllIn,
                tintDark = HangameColors.BtnAllInDark,
                enabled = state.canRaise,
                a11y = A11yStrings.actionButton(ActionType.ALL_IN, amount = allInAmount),
                onClick = { onRequestConfirm(ActionType.ALL_IN, allInAmount) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    tint: Color,
    tintDark: Color,
    enabled: Boolean,
    a11y: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = if (enabled) Brush.verticalGradient(listOf(tint, tintDark))
    else Brush.verticalGradient(listOf(HangameColors.BtnDisabled, HangameColors.BtnDisabled))

    // M7-BugFix: 빠른 연타로 같은 액션이 두 번 디스패치되는 것 방어. 350ms throttle.
    val lastTapAt = remember { mutableLongStateOf(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .pressScale(enabled = enabled)
            .clip(RoundedCornerShape(8.dp))
            .background(gradient)
            .border(
                BorderStroke(
                    1.dp,
                    if (enabled) Color.White.copy(alpha = 0.25f) else HangameColors.SeatBorder,
                ),
                RoundedCornerShape(8.dp),
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    if (now - lastTapAt.longValue >= ACTION_TAP_THROTTLE_MS) {
                        lastTapAt.longValue = now
                        onClick()
                    }
                })
            }
            .semantics { contentDescription = a11y },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = if (enabled) HangameColors.TextPrimary else HangameColors.TextMuted,
            fontWeight = FontWeight.Black,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

// -----------------------------------------------------------------------------
// Internal helpers
// -----------------------------------------------------------------------------

/** 비율 → raise total. min..max 범위 강제. */
internal fun computeRaiseTotalForRatio(state: ActionBarState, ratio: Double): Long {
    val betToCallAbs = state.currentCommitted + state.callAmount
    val target = betToCallAbs + (state.potSize.toDouble() * ratio).roundToLong()
    return safeCoerceIn(target, state.minRaiseTotal, state.maxRaiseTotal)
}

private fun clampRaise(state: ActionBarState, raw: Long): Long =
    safeCoerceIn(raw, state.minRaiseTotal, state.maxRaiseTotal)

internal fun safeCoerceIn(value: Long, min: Long, max: Long): Long =
    if (min > max) max else value.coerceIn(min, max)

/** ActionBar 버튼 디바운스 시간 (ms). 빠른 더블탭/연타 방지. */
private const val ACTION_TAP_THROTTLE_MS = 350L

// -----------------------------------------------------------------------------
// Previews
// -----------------------------------------------------------------------------

private fun previewState(
    canCheck: Boolean,
    canCall: Boolean,
    callAmount: Long,
    canRaise: Boolean,
    minRaiseTotal: Long,
    maxRaiseTotal: Long,
    currentCommitted: Long,
    potSize: Long,
    myChips: Long,
): ActionBarState = object : ActionBarState {
    override val canCheck = canCheck
    override val canCall = canCall
    override val callAmount = callAmount
    override val canRaise = canRaise
    override val minRaiseTotal = minRaiseTotal
    override val maxRaiseTotal = maxRaiseTotal
    override val currentCommitted = currentCommitted
    override val potSize = potSize
    override val myChips = myChips
}

@Preview(showBackground = true, widthDp = 1280, heightDp = 130, backgroundColor = 0xFF0B2D52)
@Composable
private fun ActionBarPreviewPreflopCall() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ActionBar(
                state = previewState(
                    canCheck = false,
                    canCall = true,
                    callAmount = 25L,
                    canRaise = true,
                    minRaiseTotal = 100L,
                    maxRaiseTotal = 9_975L,
                    currentCommitted = 25L,
                    potSize = 75L,
                    myChips = 9_975L,
                ),
                onAction = {},
                onRequestConfirm = { _, _ -> },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
