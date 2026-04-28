package com.infocar.pokermaster.feature.history.coaching

import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.model.ActionType

/**
 * Phase F: 핸드 종료 후 한 줄 코칭 — 정적 룰 베이스.
 *
 * 본격 LLM 통합은 별도 sprint. 현재는 결과 (WIN/LOSE/TIE) + 첫 스트릿 자발 참여 (VPIP) +
 * 본인 액션 패턴으로 4~5종 메시지 분기. 사용자 가치: 매 핸드 즉각 피드백 + 학습 hint.
 *
 * 향후 확장:
 *  - LlmCoach interface — engine 로드 시 LLM 호출, 미로드 시 본 객체 폴백.
 *  - 페르소나 별 advice ("호랑이형은 aggression 높음 — 콜 신중").
 *  - 통계 기반 trend ("최근 5핸드 winrate 60%, 잘 가고 있어요").
 */
object CoachingTip {

    private val VPIP_ACTIONS: Set<ActionType> = setOf(
        ActionType.CALL,
        ActionType.BET,
        ActionType.RAISE,
        ActionType.ALL_IN,
        ActionType.COMPLETE,
    )

    private val PFR_ACTIONS: Set<ActionType> = setOf(
        ActionType.BET,
        ActionType.RAISE,
        ActionType.ALL_IN,
        ActionType.COMPLETE,
    )

    fun forRecord(record: HandHistoryRecord): Tip {
        val humanSeat = record.initialState.players.firstOrNull { it.isHuman }?.seat
            ?: return Tip("기록 분석 불가 — 다음 판 가봐요.", "🃏")

        val human = record.initialState.players.first { it.seat == humanSeat }
        val committed = human.committedThisHand
        val payout = record.winnerSeat?.let {
            // record.winnerSeat 가 본인이면 potSize 추정 (간이) — 정확 payout 은 ShowdownSummary
            // resultJson 에 있지만 파싱 비용 큼. winnerSeat == human 이면 potSize 회수, 아니면 0.
            if (it == humanSeat) record.potSize else 0L
        } ?: 0L
        val net = payout - committed

        val firstStreetActions = record.actions
            .filter { it.streetIndex == 0 && it.seat == humanSeat }
            .map { it.action.type }
        val foldedPre = firstStreetActions.none { it in VPIP_ACTIONS } &&
            firstStreetActions.any { it == ActionType.FOLD }
        val raisedPre = firstStreetActions.any { it in PFR_ACTIONS }

        return when {
            net > 0 && raisedPre ->
                Tip("프리플롭 공격적 진입이 결실로! 좋은 셀렉션이었어요.", "🔥")
            net > 0 ->
                Tip("이번 핸드 잘 잡았어요. 침착한 콜 한 번이 주효했네요.", "😎")
            net == 0L && foldedPre ->
                Tip("프리플롭 폴드는 좋은 선택. 칩 보존이 곧 승리 🛡️", "👍")
            net == 0L ->
                Tip("무손실로 끝낸 핸드. 다음 큰 한 방을 노려봐요.", "🃏")
            net < 0 && raisedPre ->
                Tip("적극 진입했지만 끝까지 따라간 게 부담이 됐어요. 콜 vs 폴드 균형 점검.", "🎯")
            net < 0 && !foldedPre ->
                Tip("후반 베팅 사이즈가 부담이었어요. 팟 대비 콜 가치 다시 보세요.", "📉")
            else ->
                Tip("이번엔 아쉬웠지만 다음 핸드 노려봐요.", "💪")
        }
    }

    data class Tip(val message: String, val emoji: String)
}
