package com.infocar.pokermaster.core.model

import kotlinx.serialization.Serializable

/**
 * 좌석별 플레이어 상태. immutable — reducer 가 매 이벤트마다 copy 해서 새 상태 산출.
 *
 *  - [seat]: 좌석 인덱스 (0 기준, BTN 위치와 독립). 일치성만 유지.
 *  - [personaId]: null = 인간. 에이전트이면 persona enum name.
 *  - [chips]: 현재 보유 칩 (핸드 중 차감/환급 반영).
 *  - [holeCards]: 다운카드 (홀덤 2, 7스터드 3).
 *  - [upCards]: 업카드 (7스터드 전용).
 *  - [committedThisHand]: 앤티+모든 스트릿 베팅 누적 (사이드팟 산정 입력).
 *  - [committedThisStreet]: 현 스트릿에서 베팅 누적 (콜/레이즈 계산 기준).
 *  - [folded]: 핸드 폐기 여부.
 *  - [allIn]: 잔여 stack 0 + 활성 여부.
 */
@Serializable
data class PlayerState(
    val seat: Int,
    val nickname: String,
    val isHuman: Boolean,
    val personaId: String? = null,
    val chips: Long,
    val holeCards: List<Card> = emptyList(),
    val upCards: List<Card> = emptyList(),
    val committedThisHand: Long = 0L,
    val committedThisStreet: Long = 0L,
    val folded: Boolean = false,
    val allIn: Boolean = false,
    /** 현 스트릿 액션 참여 완료 (체크/콜/레이즈/폴드 후 true). 새 스트릿 시작 시 false 로 리셋. */
    val actedThisStreet: Boolean = false,
    /**
     * 한국식 Hi-Lo 선언. SEVEN_STUD_HI_LO 모드의 Street.DECLARE 단계에서만 채워짐.
     * 다른 모드/스트릿에서는 항상 null. 다른 좌석에 노출 X (UI 매핑 시 본인 좌석만 공개).
     */
    val declaration: DeclareDirection? = null,
) {
    /** 이 좌석이 이번 스트릿에 액션 선택 가능한가 (folded/allIn 제외). */
    val active: Boolean get() = !folded && !allIn
    /** 핸드 생존 (showdown 자격). */
    val alive: Boolean get() = !folded
}
