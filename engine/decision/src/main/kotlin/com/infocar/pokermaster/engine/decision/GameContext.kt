package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode

/**
 * 결정형 코어가 보는 게임 상태 스냅샷. 전체 게임 상태(GameState in M3)의 부분 뷰.
 *
 *  - 본인 시점: hole/upCards 는 본인 것만, knownOpponentUpCards 는 7스터드의 상대 업카드
 *  - effectiveStack 은 본인 vs 가장 깊은 상대 중 작은 값
 *  - betToCall = 현재 콜 비용 (체크 가능하면 0)
 *  - minRaise = 최소 레이즈 절대값 (본인 베팅 후 commit total)
 */
data class GameContext(
    val mode: GameMode,
    val seat: Int,
    val opponentSeats: List<Int>,
    val hole: List<Card>,                          // 홀덤 2 / 7스터드 1~3 (다운카드)
    val upCards: List<Card> = emptyList(),         // 7스터드 본인 업카드
    val community: List<Card> = emptyList(),        // 홀덤 0~5
    val knownOpponentUpCards: Map<Int, List<Card>> = emptyMap(),  // 7스터드 상대 업카드
    val pot: Long,
    val betToCall: Long,                            // 0 = 체크 가능
    val minRaise: Long,                             // 최소 raise 절대값 (전체 commit)
    val myStack: Long,
    val effectiveStack: Long,                       // min(myStack, max opp stack)
    /**
     * 남아있는 상대 수 (equity 계산용).
     * 폴드 안 한 모든 상대 포함 — all-in 상대도 쇼다운까지 합류하므로 포함.
     */
    val numActiveOpponents: Int,
    /**
     * fold equity 계산용 상대 수 (M7-BugFix). all-in 상대는 절대 fold 못하므로 제외.
     * 기본값은 [numActiveOpponents] — 호출자가 all-in 구분 못 하면 최악값으로 폴백.
     */
    val numFoldableOpponents: Int = numActiveOpponents,
)

/**
 * 단일 액션 후보 + 기대값.
 *
 * @param action 액션 타입
 * @param amount **본 액션 후 본인의 핸드당 누적 commit 절대값** (increment 가 아님).
 *               예: minRaise=200 이면 RAISE amount=200 은 "이번 핸드 누적 commit 200" 의미.
 *               BET/RAISE/ALL_IN/CALL 일 때만 의미, FOLD/CHECK 는 0.
 * @param ev 칩 기대값 (음수 가능)
 * @param equity 핸드 win prob (0~1). 0.0 디폴트 — UI 가 NaN 처리할 필요 없음.
 * @param potOdds 0~1 (CALL 만 의미). 0.0 디폴트.
 */
data class ActionCandidate(
    val action: ActionType,
    val amount: Long = 0L,
    val ev: Double,
    val equity: Double = 0.0,
    val potOdds: Double = 0.0,
)

/** 결정형 코어 출력. UI/LLM 페르소나가 이 후보들을 바탕으로 최종 액션 선택. */
data class DecisionResult(
    val candidates: List<ActionCandidate>,
    val equity: Double,                     // 본인 hand 의 vs random opp 평균 win prob
    val potOdds: Double,                    // betToCall / (pot + betToCall), call 비용 0 이면 0
    val effectiveStack: Long,
    val spr: Double,                        // effectiveStack / pot (pot 0 이면 SPR_CAP)
) {
    companion object {
        /** spr 무한대 cap — JSON 직렬화 안전성 위해 999.0 으로 제한. */
        const val SPR_CAP: Double = 999.0
    }
}
