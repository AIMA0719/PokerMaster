package com.infocar.pokermaster.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.data.profile.NicknameRepository
import com.infocar.pokermaster.core.data.wallet.CheckInResult
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.data.wallet.WalletState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * 로비 VM — M6-C. 지갑 잔고/streak 노출 + 1회성 이벤트 (daily bonus 다이얼로그).
 */
@HiltViewModel
class LobbyViewModel @Inject constructor(
    private val walletRepo: WalletRepository,
    private val historyRepo: HandHistoryRepository,
    private val missionRepo: MissionRepository,
    private val nicknameRepo: NicknameRepository,
    private val tierRepo: TierProgressionRepository,
) : ViewModel() {

    val nickname: StateFlow<String> = nicknameRepo.nickname
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = nicknameRepo.current(),
        )

    fun setNickname(name: String) {
        nicknameRepo.set(name)
    }

    val wallet: StateFlow<WalletState> = walletRepo.observe()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WalletState(
                balanceChips = WalletRepository.STARTING_BANKROLL,
                streakDays = 0,
                lastCheckInEpochDay = 0L,
                totalEarnedLifetime = 0L,
            ),
        )

    private val _events = MutableStateFlow<LobbyEvent?>(null)
    val events: StateFlow<LobbyEvent?> = _events.asStateFlow()

    /** Phase B: 일일 미션 3종 상태. onEntered + claim 시 갱신. */
    private val _missions = MutableStateFlow(MissionsState())
    val missions: StateFlow<MissionsState> = _missions.asStateFlow()

    /**
     * 화면 최초 진입 시 1회 호출 (hilt VM 기본 스코프) — daily check-in + 파산 감지.
     * M7-BugFix: Room IO 예외가 viewModelScope 로 전파되면 VM 이 죽어 로비가 freeze.
     * 모든 repo 호출을 runCatching 으로 격리.
     */
    fun onEntered() = viewModelScope.launch {
        runCatching { walletRepo.recordCheckIn(LocalDate.now()) }
            .onSuccess { r ->
                if (r is CheckInResult.Granted) {
                    _events.value = LobbyEvent.DailyBonus(r.bonus, r.newStreak, r.newBalance)
                }
            }
            .onFailure {
                android.util.Log.w("LobbyVM", "check-in failed", it)
                // M7: 사용자 가시 메시지 — 보너스 누락 사실은 알려주되 게임 진입은 차단하지 않음.
                _events.value = LobbyEvent.Error("일일 보너스 확인에 실패했어요. 잠시 후 다시 시도해 주세요.")
            }
        // 테이블에서 막 복귀했을 때 settle(wallet 입금) 가 백그라운드 launch 라 race — 짧은
        // delay 로 settle 완료를 기다린 뒤 잔고 판정.
        kotlinx.coroutines.delay(800L)
        val state = runCatching { walletRepo.getState() }.getOrNull() ?: return@launch
        // 사용자 룰: 본인 buy-in = wallet 잔고 전체 → wallet > 0 이면 진입 가능. 파산은 0 일 때만.
        if (state.balanceChips == 0L) {
            _events.value = LobbyEvent.Bankrupt(state.balanceChips)
        }
        // 일일 미션 상태 갱신 (오늘 시작된 핸드 카운트 + 수령 여부).
        refreshMission()
        // Phase C: 티어 진급 감지. 첫 진입 (last seen=null) 은 모달 X, 현재 tier 저장만.
        // 이후 진입 시 ordinal 증가하면 TierUp 모달 1회 + Phase C2: 보상 chip 적립.
        val currentTier = TierLevel.forLifetime(state.totalEarnedLifetime)
        val seenTier = tierRepo.lastSeenTier()
        if (seenTier == null) {
            tierRepo.saveTier(currentTier)
        } else if (currentTier.ordinal > seenTier.ordinal) {
            // 보상 적립 — wallet 가산. 적립 실패해도 모달은 노출 (사용자에 보임은 보장).
            if (currentTier.rewardChips > 0L) {
                runCatching { walletRepo.claimMissionReward(currentTier.rewardChips) }
                    .onFailure { android.util.Log.w("LobbyVM", "tier reward credit failed", it) }
            }
            _events.value = LobbyEvent.TierUp(newTier = currentTier, oldTier = seenTier)
            tierRepo.saveTier(currentTier)
        }
    }

    /** 미션 상태 재조회. claim 직후 / lobby onEntered 에서 호출. */
    private fun refreshMission() = viewModelScope.launch {
        val today = LocalDate.now()
        val todayEpoch = today.toEpochDay()
        val sinceMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val handsToday = runCatching { historyRepo.countSince(sinceMs) }.getOrNull() ?: 0
        val updated = MissionsState.DEFAULT_DEFINITIONS.map { def ->
            Mission(
                id = def.id,
                label = def.label,
                targetHands = def.targetHands,
                rewardAmount = def.rewardAmount,
                claimed = missionRepo.lastClaimedEpochDay(def.id) == todayEpoch,
            )
        }
        _missions.value = MissionsState(todayHands = handsToday, missions = updated)
    }

    fun claimMission(missionId: String) = viewModelScope.launch {
        val state = _missions.value
        val target = state.missions.find { it.id == missionId } ?: return@launch
        if (!target.canClaim(state.todayHands)) return@launch
        runCatching {
            walletRepo.claimMissionReward(target.rewardAmount)
            missionRepo.saveClaimed(missionId, LocalDate.now().toEpochDay())
        }.onFailure {
            android.util.Log.w("LobbyVM", "mission claim failed", it)
            _events.value = LobbyEvent.Error("보상 적립에 실패했어요.")
        }
        refreshMission()
    }

    fun onResetBankrupt() = viewModelScope.launch {
        runCatching { walletRepo.resetBankrupt() }
            .onSuccess { _events.value = null }
            .onFailure {
                android.util.Log.w("LobbyVM", "reset bankrupt failed", it)
                _events.value = LobbyEvent.Error("리셋에 실패했어요. 앱 재시작 후 다시 시도해 주세요.")
            }
    }

    fun dismissEvent() {
        _events.value = null
    }

    /** Table 진입 가능 여부 — 본인 buy-in = wallet 전체 룰: wallet > 0 면 진입. */
    fun canEnterTable(): Boolean = wallet.value.balanceChips > 0L
}

sealed interface LobbyEvent {
    data class DailyBonus(val chipsGranted: Long, val streak: Int, val newBalance: Long) : LobbyEvent
    data class Bankrupt(val currentBalance: Long) : LobbyEvent
    /** Phase C: 티어 진급 1회성 모달. */
    data class TierUp(val newTier: TierLevel, val oldTier: TierLevel) : LobbyEvent
    /** silent fail 대신 사용자에게 짧게 토스트로 알려주는 전이형 메시지. dismissEvent() 로 소거. */
    data class Error(val message: String) : LobbyEvent
}

/**
 * 일일 미션 단건. todayHands 외부 입력 — Mission 자체는 정의 + 수령 여부만 보유.
 * canClaim/progress 는 현재 todayHands 와 결합한 view-model 함수.
 */
data class Mission(
    val id: String,
    val label: String,
    val targetHands: Int,
    val rewardAmount: Long,
    val claimed: Boolean,
) {
    fun progress(todayHands: Int): Float =
        (todayHands.toFloat() / targetHands).coerceAtMost(1f)

    fun canClaim(todayHands: Int): Boolean =
        todayHands >= targetHands && !claimed
}

/**
 * Phase B: 3종 누적 미션 (5 / 10 / 20 핸드). 각 단계별 보상 1k / 2k / 3k.
 * todayHands 는 화면 최상단 1회만 표시, 미션마다 progress/claim 분기.
 */
data class MissionsState(
    val todayHands: Int = 0,
    val missions: List<Mission> = DEFAULT_DEFINITIONS.map { def ->
        Mission(def.id, def.label, def.targetHands, def.rewardAmount, claimed = false)
    },
) {
    companion object {
        data class Definition(
            val id: String,
            val label: String,
            val targetHands: Int,
            val rewardAmount: Long,
        )

        val DEFAULT_DEFINITIONS: List<Definition> = listOf(
            Definition("hands_5", "5핸드 플레이", 5, 1_000L),
            Definition("hands_10", "10핸드 플레이", 10, 2_000L),
            Definition("hands_20", "20핸드 플레이", 20, 3_000L),
        )
    }
}
