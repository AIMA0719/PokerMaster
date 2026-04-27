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
    /** 한국식 7스터드 Hi-Lo 선언 단계 — SEVENTH 베팅 종료 후 SHOWDOWN 직전. SEVEN_STUD_HI_LO 모드 한정. */
    DECLARE,
    SHOWDOWN,
}

@Serializable
enum class ActionType {
    FOLD, CHECK, CALL, BET, RAISE, ALL_IN,
    COMPLETE,   // 7-stud 브링인 콤플리트 (v1.1 §3.3.B)
    BRING_IN,   // 7-stud 강제 브링인
    SAVE_LIFE,  // 7-stud 한국식 구사 (v1.1 §3.3.C)
    /** Hi-Lo 선언 — Hi 방향. amount=0. SEVEN_STUD_HI_LO + Street.DECLARE 한정. */
    DECLARE_HI,
    /** Hi-Lo 선언 — Lo 방향. amount=0. */
    DECLARE_LO,
    /** Hi-Lo 선언 — Both(scoop). amount=0. 두 방향 모두 우승해야 팟 전체. */
    DECLARE_BOTH,
}
