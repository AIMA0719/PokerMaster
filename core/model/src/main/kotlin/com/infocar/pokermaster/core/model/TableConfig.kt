package com.infocar.pokermaster.core.model

/**
 * 테이블 정적 설정 — 핸드 시작 전 확정, 핸드 진행 중 변경 없음.
 *
 *  - [seats]: 활성 좌석 수 (2~8). 헤즈업(2) 은 Reducer 에서 SB=BTN 분기.
 *  - [smallBlind]/[bigBlind]: NL 홀덤. 7스터드는 ante+bringIn 사용.
 *  - [ante]: 7스터드 앤티. 홀덤은 0 디폴트.
 *  - [bringIn]: 7스터드 브링인. 홀덤은 0.
 *  - [minChipsToSit]: 기본 스택 (파산 리셋 용). v1 기본 10,000 (v1.1 §4.7).
 */
data class TableConfig(
    val mode: GameMode,
    val seats: Int,
    val smallBlind: Long = 25L,
    val bigBlind: Long = 50L,
    val ante: Long = 0L,
    val bringIn: Long = 0L,
    val minChipsToSit: Long = 10_000L,
    /** 핸드 타이머(초). v1.1 §2.2 기본 15 + 타임뱅크 30. UI가 소비. */
    val turnTimerSeconds: Int = 15,
) {
    init {
        require(seats in 2..8) { "seats must be 2~8" }
        require(smallBlind >= 0 && bigBlind >= smallBlind) { "blinds invalid" }
    }
}
