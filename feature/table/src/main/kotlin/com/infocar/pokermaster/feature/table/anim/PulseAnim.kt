package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue

/**
 * 시트 보더, 골드 배지 알파, 딜러 준비 배지 펄스에 공통으로 쓰는 reverse-loop 애니.
 * tween + FastOutSlowInEasing + RepeatMode.Reverse 조합 — 콜 사이트마다 구조가 같아 한 줄로 통일.
 */
@Composable
fun pulseFloat(
    initial: Float,
    target: Float,
    periodMs: Int,
    label: String = "pulse",
): Float {
    val transition = rememberInfiniteTransition(label = label)
    val value by transition.animateFloat(
        initialValue = initial,
        targetValue = target,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "$label-v",
    )
    return value
}
