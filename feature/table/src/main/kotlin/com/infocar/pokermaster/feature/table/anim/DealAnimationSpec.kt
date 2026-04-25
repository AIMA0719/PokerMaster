package com.infocar.pokermaster.feature.table.anim

/**
 * 딜링 애니메이션 스펙 모음.
 *
 * 사용자 룰: "실제 카지노 딜러가 카드 주듯 슉슉 — 너무 빠르지 않게."
 * → 홀 카드는 시트별로 1장씩 도는 카지노 라운드 방식 (1라운드: 모든 시트에 1장씩, 2라운드 동일).
 * → 커뮤니티는 플롭 3장 천천히 펼치고, 턴/리버는 한 장씩 묵직하게.
 */
object DealAnimationSpec {

    const val HOLE_CARD_DURATION_MS: Int = 480

    /** 카지노 라운드 딜링 — 시트 사이 카드 도착 간격 (ms). N=4 시트면 1라운드 = 4 * 180 = 720ms. */
    const val HOLE_SEAT_STAGGER_MS: Int = 180

    const val FLOP_CARD_DURATION_MS: Int = 460
    const val FLOP_CARD_STAGGER_MS: Int = 260
    const val TURN_CARD_DURATION_MS: Int = 520
    const val RIVER_CARD_DURATION_MS: Int = 520
}
