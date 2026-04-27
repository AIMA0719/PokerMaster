// Hand-end "ghost chip" overlay: small circles that translate from pot center toward the winner seat then fade.

package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

private const val GHOST_DURATION_MS = 600
private const val GHOST_COUNT = 5

/**
 * Renders a few small "ghost chip" circles that travel from the screen-relative pot center
 * (top of the parent box) toward the winner's seat region (bottom of the parent box) over
 * ~600ms then fade out. Caller should layer this overlay above existing pot-reveal content.
 *
 * The component is layout-agnostic — it uses the parent Canvas size to compute start/end
 * positions. `winnerSeatHorizontalBias` is a 0..1 horizontal anchor where 0=left, 1=right,
 * 0.5=center. Defaults to center, which works well for the human seat (bottom-center).
 */
@Composable
fun PotGhostChipsOverlay(
    modifier: Modifier = Modifier,
    winnerSeatHorizontalBias: Float = 0.5f,
    chipColor: Color = Color(0xFFFFC857),
) {
    var started by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { started = true }

    val progress by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(durationMillis = GHOST_DURATION_MS, easing = FastOutSlowInEasing),
        label = "ghost-chip-progress",
    )

    val bias = winnerSeatHorizontalBias.coerceIn(0f, 1f)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (progress <= 0f) return@Canvas
        val w = size.width
        val h = size.height
        val potX = w * 0.5f
        val potY = -h * 0.6f
        val winX = w * bias
        val winY = h * 0.55f

        val radius = (w.coerceAtMost(h)) * 0.012f + 4f
        val alphaCurve = when {
            progress < 0.7f -> 1f
            else -> 1f - ((progress - 0.7f) / 0.3f)
        }

        for (i in 0 until GHOST_COUNT) {
            val phase = (progress + i * 0.07f).coerceIn(0f, 1f)
            val cx = potX + (winX - potX) * phase
            val arcLift = -h * 0.08f * (1f - (phase - 0.5f) * 2f).let { it * it }
            val cy = potY + (winY - potY) * phase + arcLift
            val a = alphaCurve.coerceIn(0f, 1f) * (0.85f - i * 0.08f).coerceAtLeast(0.2f)
            drawCircle(
                color = chipColor.copy(alpha = a),
                radius = radius,
                center = Offset(cx, cy),
            )
        }
    }
}
