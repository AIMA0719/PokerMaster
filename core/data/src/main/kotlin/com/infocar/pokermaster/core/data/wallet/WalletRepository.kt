package com.infocar.pokermaster.core.data.wallet

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
     * buy-in 이 이미 차감되었으므로 balance 에는 finalChips 를 그대로 더한다.
     * totalEarnedLifetime 은 티어 기준이므로 buy-in 회수분을 제외한 순이익만 더한다.
     */
    suspend fun settle(finalChips: Long, initialBuyIn: Long = 0L)

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

    /**
     * 핸드 종료 후 ELO 점수 변경. 정식 ELO K-factor 공식:
     *
     *  expected = 1 / (1 + 10^((opponentAvgElo - myElo) / 400))
     *  delta = K * (actual - expected)
     *    actual: WIN 1.0 / LOSE 0.0 / TIE 0.5
     *    K = [K_FACTOR] = 32 (체스 표준)
     *
     * opponentAvgElo 는 호출자 (TableViewModel) 가 NPC 페르소나 base ELO 평균으로 산출.
     * MIN_ELO 하한 보호. delta=0 또는 결과 동일 시 no-op (DB write 회피).
     */
    suspend fun applyHandOutcome(outcome: HandOutcome, opponentAvgElo: Int)

    companion object {
        /** 신규 사용자 / 파산 리셋 시 지급 칩. */
        const val STARTING_BANKROLL: Long = 50_000L

        /** 테이블 1회 buy-in 비용. */
        const val TABLE_STAKE: Long = 10_000L

        /** Daily bonus 기본 보상 (streak 무관 v1). */
        const val DAILY_BONUS: Long = 2_000L

        /** Phase E: ELO 하한선 — 더 떨어지지 않게 보호. */
        const val MIN_ELO: Int = 800

        /** Phase E2: ELO K-factor (체스 표준 32 채택). 매 핸드당 최대 ±32 변동. */
        const val K_FACTOR: Int = 32
    }
}

/** Phase E: 핸드 결과 분류 — TableViewModel 이 payout vs committedThisHand 비교로 산출. */
enum class HandOutcome {
    WIN, LOSE, TIE
}

data class WalletState(
    val balanceChips: Long,
    val streakDays: Int,
    val lastCheckInEpochDay: Long,
    val totalEarnedLifetime: Long,
    val elo: Int = WalletEntity.DEFAULT_ELO,
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

    private val writeMutex = Mutex()

    override fun observe(): Flow<WalletState> =
        dao.observe().map { it?.toState() ?: initialState() }

    override suspend fun getState(): WalletState =
        writeMutex.withLock { currentOrSeeded().toState() }

    override suspend fun buyIn(stake: Long): BuyInResult = writeMutex.withLock {
        if (stake <= 0L) {
            val current = currentOrSeeded()
            return@withLock BuyInResult.Success(current.balanceChips)
        }
        val current = currentOrSeeded()
        if (current.balanceChips < stake) {
            return@withLock BuyInResult.Insufficient(current.balanceChips, stake)
        }
        val next = current.copy(balanceChips = current.balanceChips - stake)
        dao.upsert(next)
        BuyInResult.Success(next.balanceChips)
    }

    override suspend fun settle(finalChips: Long, initialBuyIn: Long) = writeMutex.withLock {
        val current = currentOrSeeded()
        val credited = finalChips.coerceAtLeast(0L)
        val netEarned = (credited - initialBuyIn.coerceAtLeast(0L)).coerceAtLeast(0L)
        val next = current.copy(
            balanceChips = current.balanceChips + credited,
            totalEarnedLifetime = current.totalEarnedLifetime + netEarned,
        )
        dao.upsert(next)
    }

    override suspend fun claimMissionReward(amount: Long) = writeMutex.withLock {
        if (amount <= 0L) return
        val current = currentOrSeeded()
        val next = current.copy(
            balanceChips = current.balanceChips + amount,
            totalEarnedLifetime = current.totalEarnedLifetime + amount,
        )
        dao.upsert(next)
    }

    override suspend fun applyHandOutcome(outcome: HandOutcome, opponentAvgElo: Int) = writeMutex.withLock {
        val current = currentOrSeeded()
        val actual = when (outcome) {
            HandOutcome.WIN -> 1.0
            HandOutcome.LOSE -> 0.0
            HandOutcome.TIE -> 0.5
        }
        // expected score (ELO 표준 공식). 강한 상대 이김 → expected↓ → delta↑.
        val expected = 1.0 / (1.0 + Math.pow(10.0, (opponentAvgElo - current.elo).toDouble() / 400.0))
        val delta = (WalletRepository.K_FACTOR * (actual - expected)).toInt()
        if (delta == 0) return
        val newElo = (current.elo + delta).coerceAtLeast(WalletRepository.MIN_ELO)
        if (newElo == current.elo) return
        dao.upsert(current.copy(elo = newElo))
    }

    override suspend fun resetBankrupt() = writeMutex.withLock {
        val current = currentOrSeeded()
        val next = current.copy(balanceChips = WalletRepository.STARTING_BANKROLL)
        dao.upsert(next)
    }

    override suspend fun recordCheckIn(today: LocalDate): CheckInResult = writeMutex.withLock {
        val current = currentOrSeeded()
        val todayEpoch = today.toEpochDay()
        if (current.lastCheckInEpochDay == todayEpoch) {
            return@withLock CheckInResult.AlreadyCheckedIn(current.streakDays)
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
        CheckInResult.Granted(bonus = bonus, newStreak = newStreak, newBalance = next.balanceChips)
    }

    // ------ helpers ------

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
        elo = WalletEntity.DEFAULT_ELO,
    )

    private fun initialState(): WalletState = WalletState(
        balanceChips = WalletRepository.STARTING_BANKROLL,
        streakDays = 0,
        lastCheckInEpochDay = 0L,
        totalEarnedLifetime = 0L,
        elo = WalletEntity.DEFAULT_ELO,
    )

    private fun WalletEntity.toState(): WalletState = WalletState(
        balanceChips = balanceChips,
        streakDays = streakDays,
        lastCheckInEpochDay = lastCheckInEpochDay,
        totalEarnedLifetime = totalEarnedLifetime,
        elo = elo,
    )
}
