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
    SHOWDOWN,
}

@Serializable
enum class ActionType {
    FOLD, CHECK, CALL, BET, RAISE, ALL_IN,
    COMPLETE,   // 7-stud 브링인 콤플리트 (v1.1 §3.3.B)
    BRING_IN,   // 7-stud 강제 브링인
    SAVE_LIFE,  // 7-stud 한국식 구사 (v1.1 §3.3.C)
}
