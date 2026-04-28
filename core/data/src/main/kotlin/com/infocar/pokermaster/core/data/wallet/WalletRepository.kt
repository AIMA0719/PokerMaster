package com.infocar.pokermaster.core.data.wallet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

/**
 * 사용자 지갑 Repository — M6-C.
 *
 * 비즈니스 규칙:
 *  - 신규 사용자 (엔티티 없음) → [STARTING_BANKROLL] 로 시작.
 *  - [buyIn]: 테이블 진입 시 호출. balance < stake 면 실패.
 *  - [settle]: 테이블 종료 시 최종 chips 반납 (buy-in 차감본의 반대 정산).
 *  - [recordCheckIn]: 로비 진입 시 호출. 오늘 첫 체크인이면 [CheckInResult.NewCheckIn] +
 *    streak 증가. 이미 오늘 체크한 상태면 [CheckInResult.AlreadyCheckedIn].
 *  - [resetBankrupt]: 파산 리셋. 선택적으로 streak/총획득 유지.
 */
interface WalletRepository {

    fun observe(): Flow<WalletState>

    suspend fun getState(): WalletState

    /**
     * 테이블 buy-in. balance 에서 [stake] 차감. 잔고 부족이면 [BuyInResult.Insufficient].
     */
    suspend fun buyIn(stake: Long): BuyInResult

    /**
     * 테이블 정산. [finalChips] 는 인간 플레이어 좌석의 최종 보유 chips.
     * buy-in 이 이미 차감되었으므로 finalChips 를 그대로 더하면 된다 (게임 결과 = finalChips - stake).
     */
    suspend fun settle(finalChips: Long)

    /**
     * 파산 리셋. balance 를 [STARTING_BANKROLL] 로 되돌린다. streak/total 은 유지.
     */
    suspend fun resetBankrupt()

    /**
     * 로비 진입 시 오늘자 체크인. 하루 1회만 보너스 지급.
     */
    suspend fun recordCheckIn(today: LocalDate): CheckInResult

    /**
     * 일일 미션 보상 적립. balance + totalEarnedLifetime 가산. 중복 방지는 호출자
     * (MissionRepository) 에서 처리.
     */
    suspend fun claimMissionReward(amount: Long)

    companion object {
        /** 신규 사용자 / 파산 리셋 시 지급 칩. */
        const val STARTING_BANKROLL: Long = 50_000L

        /** 테이블 1회 buy-in 비용. */
        const val TABLE_STAKE: Long = 10_000L

        /** Daily bonus 기본 보상 (streak 무관 v1). */
        const val DAILY_BONUS: Long = 2_000L
    }
}

data class WalletState(
    val balanceChips: Long,
    val streakDays: Int,
    val lastCheckInEpochDay: Long,
    val totalEarnedLifetime: Long,
)

sealed interface BuyInResult {
    data class Success(val newBalance: Long) : BuyInResult
    data class Insufficient(val balance: Long, val required: Long) : BuyInResult
}

sealed interface CheckInResult {
    /** 오늘 처음 체크인. 보너스 지급 + streak 업데이트. */
    data class Granted(val bonus: Long, val newStreak: Int, val newBalance: Long) : CheckInResult

    /** 오늘은 이미 체크인 완료. 추가 보너스 없음. */
    data class AlreadyCheckedIn(val streak: Int) : CheckInResult
}

/**
 * Room 기반 구현. [com.infocar.pokermaster.core.data.PokerMasterDatabase] 의 [WalletDao] 를 감싼다.
 * 모든 연산은 현재 entity 를 읽어 mutable 사본으로 계산 후 upsert. 동시 호출은 DI 외부에서 Mutex
 * 로 제어하거나 Room coroutine dispatcher 의 직렬성에 의존 (대부분 lobby/table 진입 1회씩).
 */
class RoomWalletRepository(
    private val dao: WalletDao,
) : WalletRepository {

    override fun observe(): Flow<WalletState> =
        dao.observe().map { it?.toState() ?: initialState() }

    override suspend fun getState(): WalletState =
        dao.get()?.toState() ?: initialState().also { seedIfMissing() }

    override suspend fun buyIn(stake: Long): BuyInResult {
        val current = currentOrSeeded()
        if (current.balanceChips < stake) {
            return BuyInResult.Insufficient(current.balanceChips, stake)
        }
        val next = current.copy(balanceChips = current.balanceChips - stake)
        dao.upsert(next)
        return BuyInResult.Success(next.balanceChips)
    }

    override suspend fun settle(finalChips: Long) {
        val current = currentOrSeeded()
        // 정산 금액이 chip wallet 정책상 음수(buy-in < final) 만 아니면 그냥 가산.
        val credited = finalChips.coerceAtLeast(0L)
        val next = current.copy(
            balanceChips = current.balanceChips + credited,
            totalEarnedLifetime = current.totalEarnedLifetime + credited,
        )
        dao.upsert(next)
    }

    override suspend fun claimMissionReward(amount: Long) {
        if (amount <= 0L) return
        val current = currentOrSeeded()
        val next = current.copy(
            balanceChips = current.balanceChips + amount,
            totalEarnedLifetime = current.totalEarnedLifetime + amount,
        )
        dao.upsert(next)
    }

    override suspend fun resetBankrupt() {
        val current = currentOrSeeded()
        val next = current.copy(balanceChips = WalletRepository.STARTING_BANKROLL)
        dao.upsert(next)
    }

    override suspend fun recordCheckIn(today: LocalDate): CheckInResult {
        val current = currentOrSeeded()
        val todayEpoch = today.toEpochDay()
        if (current.lastCheckInEpochDay == todayEpoch) {
            return CheckInResult.AlreadyCheckedIn(current.streakDays)
        }
        val newStreak = if (todayEpoch - current.lastCheckInEpochDay == 1L) {
            current.streakDays + 1
        } else {
            1  // 첫 체크인 또는 streak 단절
        }
        val bonus = WalletRepository.DAILY_BONUS
        val next = current.copy(
            balanceChips = current.balanceChips + bonus,
            lastCheckInEpochDay = todayEpoch,
            streakDays = newStreak,
            totalEarnedLifetime = current.totalEarnedLifetime + bonus,
        )
        dao.upsert(next)
        return CheckInResult.Granted(bonus = bonus, newStreak = newStreak, newBalance = next.balanceChips)
    }

    // ------ helpers ------

    private suspend fun seedIfMissing() {
        if (dao.get() == null) dao.upsert(seedEntity())
    }

    private suspend fun currentOrSeeded(): WalletEntity {
        val existing = dao.get()
        if (existing != null) return existing
        val seed = seedEntity()
        dao.upsert(seed)
        return seed
    }

    private fun seedEntity(): WalletEntity = WalletEntity(
        balanceChips = WalletRepository.STARTING_BANKROLL,
        lastCheckInEpochDay = 0L,
        streakDays = 0,
        totalEarnedLifetime = 0L,
    )

    private fun initialState(): WalletState = WalletState(
        balanceChips = WalletRepository.STARTING_BANKROLL,
        streakDays = 0,
        lastCheckInEpochDay = 0L,
        totalEarnedLifetime = 0L,
    )

    private fun WalletEntity.toState(): WalletState = WalletState(
        balanceChips = balanceChips,
        streakDays = streakDays,
        lastCheckInEpochDay = lastCheckInEpochDay,
        totalEarnedLifetime = totalEarnedLifetime,
    )
}
