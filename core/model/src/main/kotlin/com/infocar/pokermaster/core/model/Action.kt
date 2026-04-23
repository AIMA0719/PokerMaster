package com.infocar.pokermaster.core.model

/**
 * 플레이어 액션. [type] 이 베팅성(CALL/BET/RAISE/ALL_IN/BRING_IN/COMPLETE) 일 때만 [amount] 가 의미.
 *
 *  - [amount] = **이번 핸드 누적 commit 절대값** (증분 아님).
 *    예: 현재 commit 100 에서 RAISE to 300 하면 amount=300.
 *  - FOLD/CHECK/SAVE_LIFE 는 amount=0.
 *  - ALL_IN 은 amount = (현재 commit + 잔여 stack).
 *
 * 모든 적법성 검증은 [com.infocar.pokermaster.engine.controller] 의 Reducer 가 담당.
 */
data class Action(
    val type: ActionType,
    val amount: Long = 0L,
)
