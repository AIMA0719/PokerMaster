package com.infocar.pokermaster.feature.table.anim

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Dp

/**
 * 칩 이동 애니메이션 스펙.
 *
 * 사용 시나리오:
 * - [betToPot]: 플레이어 시트 → 팟 중앙. 베팅/콜/레이즈 확정 시.
 * - [potToWinner]: 팟 → 승자 시트. 핸드 종료 후 팟 분배.
 * - [sidePotSplit]: 사이드 팟 정산 (여러 위너 동시). 약간의 스태거로 겹침 방지.
 */
object ChipMoveSpec {

    /** 베팅 칩이 팟으로 빨려 들어가는 지속 시간 (ms). */
    const val BET_TO_POT_DURATION_MS: Int = 340

    /** 팟 → 위너 지속 시간 (ms). 좀 더 길게 줘서 "따낸다"는 리워드 감. */
    const val POT_TO_WINNER_DURATION_MS: Int = 520

    /** 사이드 팟 분배 시 칩 뭉치 간 스태거 (ms). */
    const val SIDE_POT_STAGGER_MS: Int = 140

    /** 베팅 확정 이후 팟 합산 애니메이션 시작까지의 지연 (ms). */
    const val BET_COMMIT_START_DELAY_MS: Int = 40

    /** 핸드 종료 후 팟 분배 시작 지연 (ms). 위너 애니/쇼다운 후. */
    const val POT_SWEEP_START_DELAY_MS: Int = 220

    /** 베팅 → 팟 AnimationSpec<Offset>. 포물선 같은 자연스러운 감속. */
    val betToPot: AnimationSpec<Offset> = tween(
        durationMillis = BET_TO_POT_DURATION_MS,
        delayMillis = BET_COMMIT_START_DELAY_MS,
        easing = FastOutSlowInEasing,
    )

    /** 팟 → 위너 AnimationSpec<Offset>. 살짝 가속해서 튀어나가는 느낌. */
    val potToWinner: AnimationSpec<Offset> = tween(
        durationMillis = POT_TO_WINNER_DURATION_MS,
        delayMillis = POT_SWEEP_START_DELAY_MS,
        easing = FastOutLinearInEasing,
    )

    /** 칩 크기(스케일) 변화용 AnimationSpec<Dp>. 쌓이면서 약간 커졌다가 안정화. */
    val chipStackGrow: AnimationSpec<Dp> = tween(
        durationMillis = 220,
        easing = FastOutSlowInEasing,
    )
}
