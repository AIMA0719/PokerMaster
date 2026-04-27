package com.infocar.pokermaster.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class GameMode {
    SEVEN_STUD,
    SEVEN_STUD_HI_LO,
    HOLDEM_NL,
}

@Serializable
enum class Street {
    ANTE,
    PREFLOP, FLOP, TURN, RIVER,
    THIRD, FOURTH, FIFTH, SIXTH, SEVENTH,
    /**
     * 7-Card Stud Hi-Lo (8-or-better) 전용 단계.
     * 7th street 베팅 종료 후 SHOWDOWN 직전, 살아있는 모든 플레이어가 HIGH/LOW/SWING 을 동시 선언.
     * Hi-only / Hold'em 모드에서는 통과하지 않음.
     */
    DECLARE,
    SHOWDOWN,
}

@Serializable
enum class ActionType {
    FOLD, CHECK, CALL, BET, RAISE, ALL_IN,
    COMPLETE,   // 7-stud 브링인 콤플리트 (v1.1 §3.3.B)
    BRING_IN,   // 7-stud 강제 브링인
    SAVE_LIFE,  // 7-stud 한국식 구사 (v1.1 §3.3.C)
    /**
     * 7-Stud Hi-Lo 한국식 declare. payload 는 [Action.declaration] 으로 전달.
     * Street.DECLARE 단계에서만 유효. 폴드한 플레이어는 declare 안 함, all-in 도 declare 가능.
     */
    DECLARE,
}

/**
 * 7-Stud Hi-Lo 한국식 declare 선언. SWING 은 양방향 모두 노림 — 한쪽이라도 단독 1위 자리 못 얻으면
 * 전체 자격 박탈. HIGH/LOW 는 단방향. 8-or-better qualifier 미달 시 LOW 선언자라도 lo 자격 잃음.
 */
@Serializable
enum class Declaration { HIGH, LOW, SWING }
