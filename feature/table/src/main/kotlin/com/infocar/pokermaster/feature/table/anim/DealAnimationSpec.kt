package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.Dp

/**
 * 딜링 애니메이션 스펙 모음.
 *
 * - Hole card: 카드가 덱에서 각 시트로 슬라이드. 2장 연속 딜 간격 120ms.
 * - Flop: 3장이 한 번에 펼쳐지되, 스태거 80ms 간격으로 한 장씩.
 * - Turn / River: 단일 카드 슬라이드 + 뒤집기.
 *
 * 설계서 §1.2 (M3 Sprint2-D) 기준. 전체 딜링은 1.2s 이내로 끝나야 타겟 템포 유지.
 */
object DealAnimationSpec {

    /** 홀 카드 1장 슬라이드 지속 시간 (ms). */
    const val HOLE_CARD_DURATION_MS: Int = 260

    /** 홀 카드 연속 딜 간격 (ms). 같은 플레이어 2장, 혹은 다음 플레이어와의 간격. */
    const val HOLE_CARD_STAGGER_MS: Int = 120

    /** 플롭 카드 1장 지속 시간 (ms). */
    const val FLOP_CARD_DURATION_MS: Int = 220

    /** 플롭 스태거 간격 (ms). */
    const val FLOP_CARD_STAGGER_MS: Int = 80

    /** 턴 카드 지속 시간 (ms). */
    const val TURN_CARD_DURATION_MS: Int = 260

    /** 리버 카드 지속 시간 (ms). */
    const val RIVER_CARD_DURATION_MS: Int = 260

    /** 카드 뒤집기 (flip) 지속 시간 (ms). */
    const val CARD_FLIP_DURATION_MS: Int = 180

    /** 홀 카드 슬라이드용 AnimationSpec<Dp>. */
    val holeCardSlide: AnimationSpec<Dp> = tween(
        durationMillis = HOLE_CARD_DURATION_MS,
        easing = FastOutSlowInEasing,
    )

    /** 플롭 카드 슬라이드용 AnimationSpec<Dp>. */
    val flopCardSlide: AnimationSpec<Dp> = tween(
        durationMillis = FLOP_CARD_DURATION_MS,
        easing = LinearOutSlowInEasing,
    )

    /** 턴 카드 슬라이드용 AnimationSpec<Dp>. */
    val turnCardSlide: AnimationSpec<Dp> = tween(
        durationMillis = TURN_CARD_DURATION_MS,
        easing = FastOutSlowInEasing,
    )

    /** 리버 카드 슬라이드용 AnimationSpec<Dp>. */
    val riverCardSlide: AnimationSpec<Dp> = tween(
        durationMillis = RIVER_CARD_DURATION_MS,
        easing = FastOutSlowInEasing,
    )

    /** 카드가 착지할 때 살짝 튀어오르는 스프링. */
    val cardLandSpring: AnimationSpec<Dp> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )
}
