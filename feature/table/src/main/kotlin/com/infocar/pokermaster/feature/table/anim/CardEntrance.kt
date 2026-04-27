// First-appearance entrance motion for playing cards: translateY(-40dp→0), alpha(0→1), rotation(-6°→0°).

package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp

private const val ENTRANCE_TRANSLATE_DP = 40f
private const val ENTRANCE_ROTATION_DEG = -6f

/**
 * Modifier that animates a card's first appearance.
 *
 * - translateY: -40dp → 0
 * - alpha: 0 → 1
 * - rotation: -6° → 0°
 *
 * Driven by a one-shot `LaunchedEffect(Unit)` flip of an internal state, so the entrance plays
 * exactly once per remembered slot. Stacks with existing AnimatedVisibility wrappers — this is
 * an additional layer (graphicsLayer transform) and does not replace fade/slide enters.
 *
 * @param durationMs total entrance duration
 * @param delayMs stagger delay before starting
 * @param key recompose-stable identity that resets the entrance when changed (e.g. the card)
 */
@Composable
fun Modifier.cardEntrance(
    durationMs: Int,
    delayMs: Int = 0,
    key: Any? = Unit,
): Modifier = composed {
    val density = LocalDensity.current
    var started by remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(
            durationMillis = durationMs.coerceAtLeast(0),
            delayMillis = delayMs.coerceAtLeast(0),
            easing = FastOutSlowInEasing,
        ),
        label = "card-entrance",
    )

    val translatePx = with(density) { ENTRANCE_TRANSLATE_DP.dp.toPx() }
    this.graphicsLayer {
        val inv = 1f - progress
        translationY = -translatePx * inv
        rotationZ = ENTRANCE_ROTATION_DEG * inv
        alpha = progress
    }
}
