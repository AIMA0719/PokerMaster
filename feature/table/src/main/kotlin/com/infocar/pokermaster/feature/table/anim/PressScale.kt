// Press-feedback scale helper for ActionBar buttons (1.0 → 0.94 → 1.0 over ~120ms).

package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.delay

private const val PRESS_DOWN_MS = 60
private const val PRESS_UP_MS = 60
private const val PRESS_HOLD_MS = 30L
private const val PRESS_TARGET_SCALE = 0.94f

/**
 * Compose modifier that observes raw pointer-down events on the wrapped node and triggers a
 * brief scale-down/up tween. Stacks alongside existing tap-detection (does not consume events).
 *
 * Sequence: down → 0.94 over 60ms → hold 30ms → 1.0 over 60ms.
 *
 * Skips animation when `enabled` is false (disabled buttons stay flat).
 */
fun Modifier.pressScale(enabled: Boolean = true): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val target = if (pressed && enabled) PRESS_TARGET_SCALE else 1f
    val scale by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = if (pressed) PRESS_DOWN_MS else PRESS_UP_MS),
        label = "press-scale",
    )

    LaunchedEffect(pressed) {
        if (pressed) {
            delay(PRESS_DOWN_MS + PRESS_HOLD_MS)
            pressed = false
        }
    }

    this
        .scale(scale)
        .pointerInput(enabled) {
            if (!enabled) return@pointerInput
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.changes.any { it.pressed }) {
                        pressed = true
                    }
                }
            }
        }
}
