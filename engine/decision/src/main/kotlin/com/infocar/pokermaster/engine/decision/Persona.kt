package com.infocar.pokermaster.engine.decision

import com.infocar.pokermaster.core.model.ActionType

/**
 * 페르소나 정의 (v1.1 §3.4).
 *
 * 두 차원으로 모델화:
 *  - looseness: 1.0 = 매우 loose (자주 콜/콜다운), 0.0 = 매우 tight (자주 폴드)
 *  - aggression: 1.0 = 매우 aggressive (자주 raise/bluff), 0.0 = 매우 passive (체크/콜만)
 *  - randomness: 0.0 = 결정적, 1.0 = 매우 변동
 */
enum class Persona(
    val displayName: String,
    val looseness: Double,
    val aggression: Double,
    val randomness: Double,
) {
    GRANPA("영자할배", looseness = 0.75, aggression = 0.20, randomness = 0.20),     // LP
    TIGER("호랑이형", looseness = 0.45, aggression = 0.85, randomness = 0.15),       // TAG
    SILENT("무표정", looseness = 0.35, aggression = 0.30, randomness = 0.05),        // TP
    BLUFFER("허세군", looseness = 0.65, aggression = 0.90, randomness = 0.30),       // LAG
    STUDENT("공대생", looseness = 0.50, aggression = 0.60, randomness = 0.05),       // GTO 근사
    AUNT("동네아줌마", looseness = 0.70, aggression = 0.50, randomness = 0.40),      // 직관형
    PRO("프로", looseness = 0.50, aggression = 0.65, randomness = 0.10),             // 균형
    NOOB("초보", looseness = 0.55, aggression = 0.45, randomness = 0.50);            // 랜덤성↑
}

/**
 * 페르소나 가중치 적용 — EV 후보에 pot-relative 편향을 더해 페르소나 색깔을 입힌다.
 *
 *  - tight 일수록 fold 가중치↑, loose 할수록 call 가중치↑
 *  - aggressive 일수록 raise/all-in 가중치↑, passive 할수록 check/call 가중치↑
 *  - randomness 는 다음 페이즈(LLM 페르소나 레이어)에서 샘플링 시 사용
 *
 *  pot 규모와 무관한 절대 칩 offset 은 작은 게임 폭주/큰 게임 무시 문제. pot 비율로 정규화.
 *
 *  결과는 [ActionCandidate] 의 ev 를 가중치로 보정한 뉴 후보 리스트.
 *  실제 액션 선택은 LLM 또는 정책 레이어가 담당.
 */
object PersonaBias {

    /**
     * @param pot 현재 팟 사이즈 (offset 정규화 기준). 0 이면 fallback offset 50칩 단위.
     */
    fun apply(
        persona: Persona,
        candidates: List<ActionCandidate>,
        pot: Long = 0L,
    ): List<ActionCandidate> {
        if (candidates.isEmpty()) return emptyList()
        // pot-relative offset — 팟의 비율 (fold ≈ 5%, call ≈ 3%, raise ≈ 4%)
        // pot=0 이면 fallback 50 chip
        val unit = if (pot > 0) pot.toDouble() * 0.05 else 50.0
        return candidates.map { c ->
            val offsetMultiplier = when (c.action) {
                ActionType.FOLD -> (1.0 - persona.looseness) * 1.0
                ActionType.CHECK -> (1.0 - persona.aggression) * 0.4
                ActionType.CALL -> persona.looseness * 0.6
                ActionType.BET, ActionType.RAISE -> persona.aggression * 0.8
                ActionType.ALL_IN -> persona.aggression * persona.aggression * 0.6
                ActionType.COMPLETE -> 0.0
                ActionType.BRING_IN -> 0.0    // 강제 액션 — 페르소나 영향 없음
                ActionType.SAVE_LIFE -> (1.0 - persona.looseness) * 0.6   // tight 일수록 구사 선호
                // 한국식 Hi-Lo Declare: 페르소나 편향 없음 — 결정론적 EV 비교로 [AiDriver] 가 선택.
                ActionType.DECLARE_HI -> 0.0
                ActionType.DECLARE_LO -> 0.0
                ActionType.DECLARE_BOTH -> 0.0
            }
            c.copy(ev = c.ev + offsetMultiplier * unit)
        }
    }
}
