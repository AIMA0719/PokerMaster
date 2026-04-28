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

    /** 잔여9-도전과제: 일일 미션 상태. onEntered + claim 시 갱신. */
    private val _mission = MutableStateFlow(MissionState())
    val mission: StateFlow<MissionState> = _mission.asStateFlow()

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
    }

    /** 미션 상태 재조회. claim 직후 / lobby onEntered 에서 호출. */
    private fun refreshMission() = viewModelScope.launch {
        val today = LocalDate.now()
        val sinceMs = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val handsToday = runCatching { historyRepo.countSince(sinceMs) }.getOrNull() ?: 0
        val claimed = missionRepo.lastClaimedEpochDay() == today.toEpochDay()
        _mission.value = MissionState(todayHands = handsToday, claimed = claimed)
    }

    fun claimMissionReward() = viewModelScope.launch {
        val current = _mission.value
        if (!current.canClaim) return@launch
        runCatching {
            walletRepo.claimMissionReward(current.rewardAmount)
            missionRepo.saveClaimed(LocalDate.now().toEpochDay())
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
    /** silent fail 대신 사용자에게 짧게 토스트로 알려주는 전이형 메시지. dismissEvent() 로 소거. */
    data class Error(val message: String) : LobbyEvent
}

/**
 * 일일 미션 상태. v1: "오늘 5핸드 플레이" 단일 미션, 보상 1k chips, 일 1회.
 * 향후 미션 풀 확장 시 sealed class 또는 List<Mission> 으로 전환.
 */
data class MissionState(
    val todayHands: Int = 0,
    val targetHands: Int = TARGET_HANDS,
    val rewardAmount: Long = REWARD_CHIPS,
    val claimed: Boolean = false,
) {
    val progress: Float
        get() = (todayHands.toFloat() / targetHands).coerceAtMost(1f)
    val canClaim: Boolean
        get() = todayHands >= targetHands && !claimed

    companion object {
        /** v1 단일 미션 임계치 — 사용자 결정에 따라 조정. */
        const val TARGET_HANDS: Int = 5
        const val REWARD_CHIPS: Long = 1_000L
    }
}
