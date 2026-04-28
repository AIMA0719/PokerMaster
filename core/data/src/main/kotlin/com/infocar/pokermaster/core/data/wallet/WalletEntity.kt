package com.infocar.pokermaster.core.data.wallet

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 사용자 지갑 상태 — M6-C. 싱글톤 행 (`id = 0`) 으로 저장한다.
 *
 * - [balanceChips]: 현재 보유 칩. 테이블 buy-in 으로 차감, settlement 로 적립.
 * - [lastCheckInEpochDay]: `LocalDate.toEpochDay()`. 0 이면 최초 진입 전.
 * - [streakDays]: 연속 체크인 일수. 오늘 - lastCheckInEpochDay == 1 이면 streak+1,
 *   2 이상이면 streak 리셋 (=1 로 재시작).
 * - [totalEarnedLifetime]: 획득 누적 칩 (보너스/정산 합). 통계/도전과제용.
 */
@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val balanceChips: Long,
    val lastCheckInEpochDay: Long,
    val streakDays: Int,
    val totalEarnedLifetime: Long,
    /** Phase E: 오프라인 ELO 점수. 기본 1200 (chess-style). 핸드 결과별 단순 delta. */
    val elo: Int = DEFAULT_ELO,
) {
    companion object {
        const val SINGLETON_ID: Int = 0
        const val DEFAULT_ELO: Int = 1200
    }
}
