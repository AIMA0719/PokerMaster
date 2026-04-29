// Press-feedback helpers: scale (1.0 → 0.94 → 1.0) and outer glow (tint border fade).

package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val PRESS_DOWN_MS = 60
private const val PRESS_UP_MS = 60
private const val PRESS_HOLD_MS = 30L
private const val PRESS_TARGET_SCALE = 0.94f

private const val GLOW_FADE_IN_MS = 60
private const val GLOW_FADE_OUT_MS = 240
private const val GLOW_HOLD_MS = 60L
private const val GLOW_VISIBLE_THRESHOLD = 0.05f

/**
 * Captures pointer-down on the wrapped node into a self-resetting [MutableState]<Boolean>.
 * Used by [pressScale] and [pressFeedback] so press detection state can be shared.
 *
 * @param holdMs how long pressed=true persists after first detection (matches longest tween).
 */
@Composable
private fun rememberPressedPulse(holdMs: Long): MutableState<Boolean> {
    val state = remember { mutableStateOf(false) }
    LaunchedEffect(state.value) {
        if (state.value) {
            delay(holdMs)
            state.value = false
        }
    }
    return state
}

/**
 * Pointer-input modifier that flips [target] to true on any pointer-down event.
 * Stacked siblings (e.g. detectTapGestures) still receive the same event since this
 * neither consumes pointers nor calls awaitFirstDown's release path.
 */
private fun Modifier.observePressDown(target: MutableState<Boolean>, enabled: Boolean): Modifier =
    pointerInput(enabled) {
        if (!enabled) return@pointerInput
        awaitPointerEventScope {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.any { it.pressed }) target.value = true
            }
        }
    }

/**
 * Brief scale-down/up tween on press. Sequence: down → 0.94 over 60ms → hold 30ms → 1.0 over 60ms.
 * Disabled buttons stay flat. Use [pressFeedback] for combined scale + glow.
 */
fun Modifier.pressScale(enabled: Boolean = true): Modifier = composed {
    val pressed = rememberPressedPulse(holdMs = (PRESS_DOWN_MS + PRESS_HOLD_MS).toLong())
    val target = if (pressed.value && enabled) PRESS_TARGET_SCALE else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (pressed.value) PRESS_DOWN_MS else PRESS_UP_MS),
        label = "press-scale",
    )
    this
        .scale(scale)
        .observePressDown(pressed, enabled)
}

/**
 * Combined scale + outer-glow press feedback. Single press-detection coroutine drives both
 * effects so a button using this modifier registers exactly one pointerInput listener.
 *
 * - Scale: 1.0 → 0.94 → 1.0 (down 60ms / hold 30ms / up 60ms)
 * - Glow: [tint] 2dp border fades in 60ms, holds 60ms, fades out 240ms.
 *   Border is omitted when alpha is below the visible threshold to avoid per-frame allocation.
 */
fun Modifier.pressFeedback(
    tint: Color,
    shape: Shape,
    enabled: Boolean = true,
): Modifier = composed {
    val pressed = rememberPressedPulse(
        holdMs = maxOf(PRESS_DOWN_MS + PRESS_HOLD_MS, GLOW_FADE_IN_MS + GLOW_HOLD_MS).toLong(),
    )
    val scaleValue by animateFloatAsState(
        targetValue = if (pressed.value && enabled) PRESS_TARGET_SCALE else 1f,
        animationSpec = tween(durationMillis = if (pressed.value) PRESS_DOWN_MS else PRESS_UP_MS),
        label = "press-scale",
    )
    val glow by animateFloatAsState(
        targetValue = if (pressed.value) 1f else 0f,
        animationSpec = tween(durationMillis = if (pressed.value) GLOW_FADE_IN_MS else GLOW_FADE_OUT_MS),
        label = "press-glow",
    )
    this
        .scale(scaleValue)
        .then(
            if (glow > GLOW_VISIBLE_THRESHOLD) {
                Modifier.border(
                    BorderStroke(2.dp, tint.copy(alpha = (glow * 0.9f).coerceIn(0f, 1f))),
                    shape,
                )
            } else Modifier
        )
        .observePressDown(pressed, enabled)
}
