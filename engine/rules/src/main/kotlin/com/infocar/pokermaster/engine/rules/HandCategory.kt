package com.infocar.pokermaster.engine.rules

/**
 * 5장 핸드 카테고리. strength 가 클수록 강함.
 *
 * 한국식 7스터드 변형 (v1.1 §2.2.4):
 *  - STRAIGHT_FLUSH_BACK: 일반 스트레이트 플러시보다 약, 풀하우스보다 강
 *  - STRAIGHT_BACK: 일반 스트레이트보다 약, 트리플보다 강
 *
 * 홀덤은 BACK 카테고리를 사용하지 않음 — 휠(A-2-3-4-5) = 5-high STRAIGHT 또는 STRAIGHT_FLUSH 와 동일 카테고리,
 * tiebreaker 의 high card = 5 (TWO_PAIR/STRAIGHT 정렬에서 자연 처리).
 */
enum class HandCategory(val strength: Int, val korean: String) {
    HIGH_CARD(1, "탑"),
    ONE_PAIR(2, "원페어"),
    TWO_PAIR(3, "투페어"),
    THREE_OF_A_KIND(4, "트리플"),

    /** 한국식 7스터드: 일반 스트레이트보다 약함. 홀덤은 미사용. */
    STRAIGHT_BACK(5, "백스트레이트"),

    STRAIGHT(6, "스트레이트"),
    FLUSH(7, "플러시"),
    FULL_HOUSE(8, "풀하우스"),
    FOUR_OF_A_KIND(9, "포카드"),

    /** 한국식 7스터드: 일반 스트레이트 플러시보다 약함. 홀덤은 미사용. */
    STRAIGHT_FLUSH_BACK(10, "백스트레이트 플러시"),

    STRAIGHT_FLUSH(11, "스트레이트 플러시"),
    ROYAL_FLUSH(12, "로열 플러시"),
}
