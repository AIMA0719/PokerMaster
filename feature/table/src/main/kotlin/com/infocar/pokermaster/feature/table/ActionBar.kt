package com.infocar.pokermaster.feature.table

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.ui.theme.PokerColors
import com.infocar.pokermaster.core.ui.theme.PokerMasterTheme
import com.infocar.pokermaster.feature.table.a11y.A11yStrings
import kotlin.math.roundToLong

/**
 * 액션바 — 사람 차례 전용.
 *
 *  v1.1 §1.2.E: 슬라이더와 액션 버튼 사이 8dp 이상 분리, 큰 raise / all-in 은 2단계 확인.
 *  v1.1 §4.8: 폴드 빨강, 콜/체크 회색, 레이즈 녹색.
 */
@Composable
fun ActionBar(
    state: ActionBarState,
    onAction: OnAction,
    onRequestConfirm: (ActionType, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    // 슬라이더 현재값 (레이즈 총액, Long).
    var raiseTotal by remember(state.minRaiseTotal, state.maxRaiseTotal) {
        mutableStateOf(state.minRaiseTotal.coerceIn(state.minRaiseTotal, state.maxRaiseTotal))
    }

    // state 바뀔 때 범위 재고정 (예: 상대 베팅 갱신).
    LaunchedEffect(state.minRaiseTotal, state.maxRaiseTotal) {
        raiseTotal = raiseTotal.coerceIn(state.minRaiseTotal, state.maxRaiseTotal)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state.canRaise) {
            QuickRatioRow(
                onRatio = { ratio ->
                    raiseTotal = computeRaiseTotalForRatio(state, ratio)
                },
                onAllIn = { raiseTotal = state.maxRaiseTotal },
            )
            Spacer(Modifier.height(6.dp))

            // 슬라이더.
            RaiseSlider(
                min = state.minRaiseTotal,
                max = state.maxRaiseTotal,
                value = raiseTotal,
                onValueChange = { raiseTotal = it },
            )
            // v1.1 §1.2.E: 슬라이더↔액션버튼 8dp 이상 분리.
            Spacer(Modifier.height(12.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // 폴드 — 항상 활성 (체크 가능 시 살짝 흐리게).
            val foldSoft = state.canCheck
            val foldA11y = A11yStrings.actionButton(ActionType.FOLD)
            Button(
                onClick = { onAction(Action(ActionType.FOLD)) },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { contentDescription = foldA11y },
                shape = ActionButtonShape,
                elevation = ActionButtonElevation,
                contentPadding = ActionButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (foldSoft) PokerColors.Danger.copy(alpha = 0.7f) else PokerColors.Danger,
                    contentColor = Color.White,
                ),
            ) {
                ActionButtonLabel(text = stringResource(id = R.string.action_fold))
            }

            // 중앙 — 체크 / 콜.
            val middleEnabled = state.canCheck || state.canCall
            val middleLabel = when {
                state.canCheck -> stringResource(id = R.string.action_check)
                state.canCall -> stringResource(
                    id = R.string.action_call_with_amount,
                    ChipFormat.format(state.callAmount),
                )
                else -> stringResource(id = R.string.action_call)
            }
            val middleA11y = when {
                state.canCheck -> A11yStrings.actionButton(ActionType.CHECK)
                state.canCall -> A11yStrings.actionButton(ActionType.CALL, toCall = state.callAmount)
                else -> A11yStrings.actionButton(ActionType.CALL)
            }
            Button(
                onClick = {
                    if (state.canCheck) onAction(Action(ActionType.CHECK))
                    else if (state.canCall) onAction(Action(ActionType.CALL))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { contentDescription = middleA11y },
                enabled = middleEnabled,
                shape = ActionButtonShape,
                elevation = ActionButtonElevation,
                contentPadding = ActionButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
            ) {
                ActionButtonLabel(text = middleLabel)
            }

            // 레이즈.
            val raiseLabel = if (raiseTotal >= state.maxRaiseTotal)
                stringResource(id = R.string.action_all_in)
            else
                stringResource(id = R.string.action_raise_to, ChipFormat.format(raiseTotal))
            val raiseA11y = if (raiseTotal >= state.maxRaiseTotal)
                A11yStrings.actionButton(ActionType.ALL_IN, amount = raiseTotal)
            else
                A11yStrings.actionButton(ActionType.RAISE, amount = raiseTotal)

            Button(
                onClick = {
                    val amount = raiseTotal
                    val isAllIn = amount >= state.maxRaiseTotal
                    val delta = (amount - state.currentCommitted).coerceAtLeast(0L)
                    val needsConfirm = isAllIn ||
                        (state.myChips > 0 && delta >= (state.myChips.toDouble() * 0.8).roundToLong())

                    val type = if (isAllIn) ActionType.ALL_IN else ActionType.RAISE
                    if (needsConfirm) onRequestConfirm(type, amount)
                    else onAction(Action(type, amount))
                },
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .semantics { contentDescription = raiseA11y },
                enabled = state.canRaise,
                shape = ActionButtonShape,
                elevation = ActionButtonElevation,
                contentPadding = ActionButtonPadding,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PokerColors.Success,
                    contentColor = Color.White,
                ),
            ) {
                ActionButtonLabel(text = raiseLabel)
            }
        }
    }
}

// -----------------------------------------------------------------------------
// 버튼 공통 스타일 — 좁은 너비에서 한글이 글자 단위로 줄바꿈되지 않도록 고정.
// (v1.1 §1.2.E, M7-B)
// -----------------------------------------------------------------------------

private val ActionButtonShape = RoundedCornerShape(14.dp)
private val ActionButtonPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)

private val ActionButtonElevation
    @Composable get() = ButtonDefaults.buttonElevation(
        defaultElevation = 2.dp,
        pressedElevation = 4.dp,
        disabledElevation = 0.dp,
    )

@Composable
private fun ActionButtonLabel(text: String) {
    Text(
        text = text,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

// -----------------------------------------------------------------------------
// Internal helpers
// -----------------------------------------------------------------------------

/**
 * raise 총액 = betToCall + round(potSize * ratio), `minRaiseTotal..maxRaiseTotal` 로 clamp.
 * (v1.1 §1.2 퀵비율 버튼 — 콜 비용 포함 합산 총액 기준.)
 */
internal fun computeRaiseTotalForRatio(state: ActionBarState, ratio: Double): Long {
    // betToCall 절대값 = currentCommitted + callAmount (callAmount 은 필요 시 0).
    val betToCallAbs = state.currentCommitted + state.callAmount
    val target = betToCallAbs + (state.potSize.toDouble() * ratio).roundToLong()
    return target.coerceIn(state.minRaiseTotal, state.maxRaiseTotal)
}

@Composable
private fun QuickRatioRow(
    onRatio: (Double) -> Unit,
    onAllIn: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        QuickRatioButton(label = stringResource(id = R.string.quick_one_third)) { onRatio(1.0 / 3.0) }
        QuickRatioButton(label = stringResource(id = R.string.quick_half)) { onRatio(0.5) }
        QuickRatioButton(label = stringResource(id = R.string.quick_two_thirds)) { onRatio(2.0 / 3.0) }
        QuickRatioButton(label = stringResource(id = R.string.quick_pot)) { onRatio(1.0) }
        QuickRatioButton(label = stringResource(id = R.string.quick_all_in), onClick = onAllIn)
    }
}

@Composable
private fun RowScope.QuickRatioButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 36.dp),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun RaiseSlider(
    min: Long,
    max: Long,
    value: Long,
    onValueChange: (Long) -> Unit,
) {
    val safeMax = if (max <= min) min + 1 else max
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = ChipFormat.format(min),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
            Text(
                text = ChipFormat.format(value),
                style = MaterialTheme.typography.labelMedium,
                color = PokerColors.Accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = ChipFormat.format(max),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            )
        }
        Slider(
            value = value.toFloat(),
            valueRange = min.toFloat()..safeMax.toFloat(),
            onValueChange = { onValueChange(it.roundToLong().coerceIn(min, max)) },
            colors = SliderDefaults.colors(
                thumbColor = PokerColors.Accent,
                activeTrackColor = PokerColors.Accent,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

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

@Preview(showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun ActionBarPreviewPreflopCall() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ActionBar(
                state = previewState(
                    canCheck = false,
                    canCall = true,
                    callAmount = 100L,
                    canRaise = true,
                    minRaiseTotal = 200L,
                    maxRaiseTotal = 10_000L,
                    currentCommitted = 50L,
                    potSize = 250L,
                    myChips = 9_950L,
                ),
                onAction = {},
                onRequestConfirm = { _, _ -> },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 220)
@Composable
private fun ActionBarPreviewFlopBigRaise() {
    PokerMasterTheme {
        Box(modifier = Modifier.padding(12.dp)) {
            ActionBar(
                state = previewState(
                    canCheck = true,
                    canCall = false,
                    callAmount = 0L,
                    canRaise = true,
                    minRaiseTotal = 600L,
                    maxRaiseTotal = 8_500L,
                    currentCommitted = 0L,
                    potSize = 1_200L,
                    myChips = 8_500L,
                ),
                onAction = {},
                onRequestConfirm = { _, _ -> },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
