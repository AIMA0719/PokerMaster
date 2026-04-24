package com.infocar.pokermaster.feature.lobby

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import javax.inject.Inject

/**
 * 로비 VM — M6-C. 지갑 잔고/streak 노출 + 1회성 이벤트 (daily bonus 다이얼로그).
 */
@HiltViewModel
class LobbyViewModel @Inject constructor(
    private val walletRepo: WalletRepository,
) : ViewModel() {

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
            .onFailure { android.util.Log.w("LobbyVM", "check-in failed", it) }
        val state = runCatching { walletRepo.getState() }.getOrNull() ?: return@launch
        if (state.balanceChips < WalletRepository.TABLE_STAKE) {
            _events.value = LobbyEvent.Bankrupt(state.balanceChips)
        }
    }

    fun onResetBankrupt() = viewModelScope.launch {
        runCatching { walletRepo.resetBankrupt() }
            .onFailure { android.util.Log.w("LobbyVM", "reset bankrupt failed", it) }
        _events.value = null
    }

    fun dismissEvent() {
        _events.value = null
    }

    /** Table 진입 가능 여부 (잔고 >= STAKE). UI 가 파산 모달 처리 후에만 navigate. */
    fun canEnterTable(): Boolean = wallet.value.balanceChips >= WalletRepository.TABLE_STAKE
}

sealed interface LobbyEvent {
    data class DailyBonus(val chipsGranted: Long, val streak: Int, val newBalance: Long) : LobbyEvent
    data class Bankrupt(val currentBalance: Long) : LobbyEvent
}
