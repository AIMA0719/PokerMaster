package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.infocar.pokermaster.feature.table.anim.pressFeedback
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.roundToLong

/**
 * 한게임 풍 compact 액션바.
 *
 *  배치(상): 슬라이더 — 사용자 임의 raise (min..max). 토글로 펼침/접음.
 *  배치(하): [다이 | 체크/콜 | 구사? | 쿼터 | 하프 | 풀팟 | 올인]
 *  - 큰 raise / all-in 은 [onRequestConfirm] 으로 2단계 확인.
 */
@Composable
fun ActionBar(
    state: ActionBarState,
    onAction: OnAction,
    onRequestConfirm: (ActionType, Long) -> Unit,
    modifier: Modifier = Modifier,
    /** 슬라이더 drag 매 step 진입 시 호출 — 호출자가 SfxPolicy 게이트 + 햅틱 실행. */
    onSliderTick: () -> Unit = {},
    /** 슬라이더 확정 (베팅 버튼) 시 호출 — onChipCommit 등 강한 햅틱. */
    onSliderConfirmHaptic: () -> Unit = {},
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

    val primaryActionType = if (state.canCall) ActionType.CALL else ActionType.CHECK
    val primaryEnabled = state.actionsEnabled && (state.canCall || state.canCheck)
    val primaryLabel = when {
        state.canCall -> "콜 ${formatActionAmount(state.callAmount)}"
        else -> "체크"
    }
    val primaryA11y = if (state.canCall) {
        A11yStrings.actionButton(ActionType.CALL, toCall = state.callAmount)
    } else {
        A11yStrings.actionButton(ActionType.CHECK)
    }

    // 임의 raise — 슬라이더 토글. raise 가능할 때만 노출.
    var sliderOpen by remember { mutableStateOf(false) }
    val sliderShown = sliderOpen && state.canRaise && state.actionsEnabled &&
        state.minRaiseTotal <= state.maxRaiseTotal && state.myChips > 0

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (sliderShown) {
            CustomRaiseRow(
                state = state,
                onConfirm = dispatchRaise,
                onClose = { sliderOpen = false },
                onTick = onSliderTick,
                onConfirmHaptic = onSliderConfirmHaptic,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
        ActionButton(
            label = "다이",
            tint = HangameColors.BtnFold,
            tintDark = HangameColors.BtnFoldDark,
            enabled = state.actionsEnabled,
            a11y = A11yStrings.actionButton(ActionType.FOLD),
            onClick = { onAction(Action(ActionType.FOLD)) },
            modifier = Modifier.weight(0.9f),
        )
        ActionButton(
            label = primaryLabel,
            tint = HangameColors.BtnCall,
            tintDark = HangameColors.BtnCallDark,
            enabled = primaryEnabled,
            a11y = primaryA11y,
            onClick = { onAction(Action(primaryActionType)) },
            modifier = Modifier.weight(1.3f),
        )
        // 7스터드/HiLo 전용 "구사" 버튼 — 홀덤에서는 false.
        if (state.canSaveLife) {
            ActionButton(
                label = "구사",
                tint = HangameColors.BtnSaveLife,
                tintDark = HangameColors.BtnSaveLifeDark,
                enabled = state.actionsEnabled,
                a11y = "구사 (한국식 7스터드)",
                onClick = { onAction(Action(ActionType.SAVE_LIFE)) },
                modifier = Modifier.weight(0.9f),
            )
        }
        ActionButton(
            label = "쿼터 ${formatActionAmount(quarterAmount)}",
            tint = HangameColors.BtnQuarter,
            tintDark = HangameColors.BtnQuarterDark,
            enabled = state.actionsEnabled && state.canRaise && quarterAmount in state.minRaiseTotal..state.maxRaiseTotal,
            a11y = A11yStrings.actionButton(ActionType.RAISE, amount = quarterAmount),
            onClick = { dispatchRaise(quarterAmount) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = "하프 ${formatActionAmount(halfAmount)}",
            tint = HangameColors.BtnHalf,
            tintDark = HangameColors.BtnHalfDark,
            enabled = state.actionsEnabled && state.canRaise && halfAmount in state.minRaiseTotal..state.maxRaiseTotal,
            a11y = A11yStrings.actionButton(ActionType.RAISE, amount = halfAmount),
            onClick = { dispatchRaise(halfAmount) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = "풀팟 ${formatActionAmount(potAmount)}",
            tint = HangameColors.BtnQuarter,
            tintDark = HangameColors.BtnQuarterDark,
            enabled = state.actionsEnabled && state.canRaise && potAmount in state.minRaiseTotal..state.maxRaiseTotal,
            a11y = A11yStrings.actionButton(ActionType.RAISE, amount = potAmount),
            onClick = { dispatchRaise(potAmount) },
            modifier = Modifier.weight(1f),
        )
        ActionButton(
            label = "올인 ${formatActionAmount(allInAmount)}",
            tint = HangameColors.BtnAllIn,
            tintDark = HangameColors.BtnAllInDark,
            enabled = state.actionsEnabled && state.canRaise,
            a11y = A11yStrings.actionButton(ActionType.ALL_IN, amount = allInAmount),
            onClick = { onRequestConfirm(ActionType.ALL_IN, allInAmount) },
            modifier = Modifier.weight(1.0f),
        )
        // 슬라이더 토글 — 임의 raise 입력. canRaise=false면 비활성.
        ActionButton(
            label = if (sliderOpen) "닫기" else "직접",
            tint = HangameColors.BtnHalfDark,
            tintDark = HangameColors.BtnHalfDark,
            enabled = state.actionsEnabled && state.canRaise && state.minRaiseTotal <= state.maxRaiseTotal,
            a11y = if (sliderOpen) "임의 베팅 입력 닫기" else "임의 베팅 입력 열기",
            onClick = { sliderOpen = !sliderOpen },
            modifier = Modifier.weight(0.7f),
        )
        }
    }
}

/**
 * 임의 raise 입력 행 — 슬라이더(min..max) + 현재 amount + "베팅" 버튼.
 *  - 슬라이더 단위는 가급적 BB 단위 (config.bigBlind / 5스터드는 bringIn). 0이면 1.
 *  - max(올인)에 도달하면 dispatchRaise가 ALL_IN 으로 자동 변환.
 */
@Composable
private fun CustomRaiseRow(
    state: ActionBarState,
    onConfirm: (Long) -> Unit,
    onClose: () -> Unit,
    onTick: () -> Unit = {},
    onConfirmHaptic: () -> Unit = {},
) {
    val min = state.minRaiseTotal
    val max = state.maxRaiseTotal
    val safeMin = min.coerceAtMost(max)
    val safeMax = max.coerceAtLeast(safeMin)
    var amount by remember(safeMin, safeMax) { mutableStateOf(safeMin) }
    val tickStep = remember(safeMin, safeMax) {
        val span = (safeMax - safeMin).coerceAtLeast(1L)
        (span / SLIDER_TICK_DIVISIONS).coerceAtLeast(1L)
    }
    var lastTickBucket by remember(safeMin, safeMax) { mutableStateOf(0L) }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = HangameColors.HeaderBgRight.copy(alpha = 0.85f),
        border = BorderStroke(1.dp, HangameColors.SeatBorder),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "베팅 ${formatActionAmount(amount)}",
                fontSize = 14.sp,
                color = HangameColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.widthIn(min = 96.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Slider(
                value = amount.toFloat(),
                onValueChange = { v ->
                    val newAmount = v.toLong().coerceIn(safeMin, safeMax)
                    amount = newAmount
                    val bucket = (newAmount - safeMin) / tickStep
                    if (bucket != lastTickBucket) {
                        lastTickBucket = bucket
                        onTick()
                    }
                },
                valueRange = safeMin.toFloat()..safeMax.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = HangameColors.BtnCall,
                    activeTrackColor = HangameColors.BtnCall,
                    inactiveTrackColor = HangameColors.SeatBg,
                ),
                modifier = Modifier.weight(1f),
                enabled = safeMin < safeMax,
            )
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .widthIn(min = 84.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HangameColors.buttonBrush(HangameColors.BtnCall, HangameColors.BtnCallDark))
                    .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)), RoundedCornerShape(8.dp))
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            onConfirmHaptic()
                            onConfirm(amount.coerceIn(safeMin, safeMax))
                            onClose()
                        })
                    }
                    .semantics { contentDescription = "베팅 확정 ${amount}칩" },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "베팅",
                    fontSize = 14.sp,
                    color = HangameColors.TextPrimary,
                    fontWeight = FontWeight.Black,
                )
            }
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
    val gradient = if (enabled) HangameColors.buttonBrush(tint, tintDark)
    else HangameColors.buttonBrush(HangameColors.BtnDisabled, HangameColors.BtnDisabled)

    // 빠른 연타로 같은 액션이 두 번 디스패치되는 것 방어. 350ms throttle.
    // 감사 결과 #2 fix: 기존 mutableLongStateOf 는 non-atomic check-and-set —
    // 빠른 더블탭과 재컴포지션이 겹치면 두 코루틴 모두 throttle 게이트 통과 가능.
    // AtomicLong.compareAndSet 으로 atomic check-and-update — 두 번째 탭은 항상 게이트에서 떨어진다.
    val lastTapAt = remember { AtomicLong(0L) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressFeedback(tint = tint, shape = ACTION_BUTTON_SHAPE, enabled = enabled)
            .clip(ACTION_BUTTON_SHAPE)
            .background(gradient)
            .border(
                BorderStroke(
                    1.dp,
                    if (enabled) Color.White.copy(alpha = 0.25f) else HangameColors.SeatBorder,
                ),
                ACTION_BUTTON_SHAPE,
            )
            .pointerInput(enabled) {
                if (enabled) detectTapGestures(onTap = {
                    val now = System.currentTimeMillis()
                    val prev = lastTapAt.get()
                    if (now - prev >= ACTION_TAP_THROTTLE_MS && lastTapAt.compareAndSet(prev, now)) {
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

private fun formatActionAmount(chips: Long): String {
    val abs = kotlin.math.abs(chips)
    val sign = if (chips < 0) "-" else ""
    return sign + when {
        abs < 10_000L -> "%,d".format(abs)
        abs < 100_000_000L -> formatOneDecimal(abs, unit = 10_000L, suffix = "만")
        abs < 1_000_000_000_000L -> formatOneDecimal(abs, unit = 100_000_000L, suffix = "억")
        else -> formatOneDecimal(abs, unit = 1_000_000_000_000L, suffix = "조")
    }
}

private fun formatOneDecimal(abs: Long, unit: Long, suffix: String): String {
    val scaledTimesTen = ((abs.toDouble() / unit.toDouble()) * 10.0).roundToLong()
    val whole = scaledTimesTen / 10L
    val decimal = scaledTimesTen % 10L
    return if (decimal == 0L) {
        "%,d%s".format(whole, suffix)
    } else {
        "%,d.%d%s".format(whole, decimal, suffix)
    }
}

/** ActionBar 버튼 디바운스 시간 (ms). 빠른 더블탭/연타 방지. */
private const val ACTION_TAP_THROTTLE_MS = 350L

/** 슬라이더 drag 햅틱 분할 수 — span 을 N 등분해 경계 통과 시 tick. */
private const val SLIDER_TICK_DIVISIONS = 20L

private val ACTION_BUTTON_SHAPE = RoundedCornerShape(8.dp)

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
