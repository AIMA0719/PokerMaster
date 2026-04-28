package com.infocar.pokermaster.feature.table

import com.google.common.truth.Truth.assertThat
import com.infocar.pokermaster.core.data.wallet.BuyInResult
import com.infocar.pokermaster.core.data.wallet.CheckInResult
import com.infocar.pokermaster.core.data.wallet.HandOutcome
import com.infocar.pokermaster.core.data.wallet.WalletEntity
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import com.infocar.pokermaster.core.data.wallet.WalletState
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.GameMode
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class TableViewModelExitAndWalletTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun exitRequestDuringActiveHandWaitsForShowdownAndDisablesAutoNext() = runTest {
        val wallet = FakeWalletRepository(initialBalance = 50_000L)
        val vm = createViewModel(wallet)
        runCurrent()

        assertThat(wallet.buyInCalls).isEqualTo(1)
        assertThat(wallet.balance).isEqualTo(0L)
        val handIndex = vm.state.value.handIndex

        val canExitImmediately = vm.requestExitAfterHand()
        assertThat(canExitImmediately).isFalse()
        assertThat(vm.exitRequested.value).isTrue()
        assertThat(wallet.settleCalls).isEqualTo(0)

        vm.onHumanAction(Action(ActionType.FOLD))
        runCurrent()

        assertThat(vm.state.value.pendingShowdown).isNotNull()
        assertThat(vm.autoNextCountdown.value).isNull()
        assertThat(vm.state.value.handIndex).isEqualTo(handIndex)

        vm.onNextHand()
        assertThat(vm.state.value.handIndex).isEqualTo(handIndex)

        val finalChips = vm.state.value.players.first { it.isHuman }.chips
        vm.settleAndCloseAwait()
        assertThat(wallet.settleCalls).isEqualTo(1)
        assertThat(wallet.lastSettledFinalChips).isEqualTo(finalChips)
        assertThat(wallet.lastSettledInitialBuyIn).isEqualTo(50_000L)

        vm.settleAndCloseAwait()
        assertThat(wallet.settleCalls).isEqualTo(1)
    }

    @Test
    fun exitRequestAfterShowdownCanSettleImmediatelyButOnlyOnce() = runTest {
        val wallet = FakeWalletRepository(initialBalance = 50_000L)
        val vm = createViewModel(wallet)
        runCurrent()

        vm.onHumanAction(Action(ActionType.FOLD))
        runCurrent()
        assertThat(vm.state.value.pendingShowdown).isNotNull()
        assertThat(vm.autoNextCountdown.value).isEqualTo(3)

        val canExitImmediately = vm.requestExitAfterHand()
        assertThat(canExitImmediately).isTrue()
        assertThat(vm.autoNextCountdown.value).isNull()

        vm.settleAndCloseAwait()
        vm.settleAndClose()
        runCurrent()

        assertThat(wallet.settleCalls).isEqualTo(1)
        assertThat(wallet.lastSettledInitialBuyIn).isEqualTo(50_000L)
    }

    @Test
    fun rejectedBuyInDoesNotSettleHistoryRewardsOrElo() = runTest {
        val wallet = FakeWalletRepository(initialBalance = 25_000L)
        val vm = createViewModel(wallet, humanBuyIn = 50_000L)
        runCurrent()

        val rejected = vm.buyInRejected.value
        assertThat(rejected).isNotNull()
        assertThat(rejected!!.balance).isEqualTo(25_000L)
        assertThat(rejected.required).isEqualTo(50_000L)

        vm.onHumanAction(Action(ActionType.FOLD))
        runCurrent()
        vm.settleAndCloseAwait()

        assertThat(wallet.balance).isEqualTo(25_000L)
        assertThat(wallet.settleCalls).isEqualTo(0)
        assertThat(wallet.applyOutcomeCalls).isEqualTo(0)
        assertThat(wallet.totalEarned).isEqualTo(0L)
    }

    private fun TestScope.createViewModel(
        wallet: FakeWalletRepository,
        humanBuyIn: Long = 50_000L,
    ): TableViewModel =
        TableViewModel.createDefault(
            context = null,
            mode = GameMode.HOLDEM_NL,
            seats = 2,
            humanBuyIn = humanBuyIn,
            historyScope = backgroundScope,
            walletRepo = wallet,
        )
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    private val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

private class FakeWalletRepository(
    initialBalance: Long,
) : WalletRepository {
    private val state = MutableStateFlow(
        WalletState(
            balanceChips = initialBalance,
            streakDays = 0,
            lastCheckInEpochDay = 0L,
            totalEarnedLifetime = 0L,
            elo = WalletEntity.DEFAULT_ELO,
        )
    )

    var buyInCalls: Int = 0
        private set
    var settleCalls: Int = 0
        private set
    var applyOutcomeCalls: Int = 0
        private set
    var lastSettledFinalChips: Long? = null
        private set
    var lastSettledInitialBuyIn: Long? = null
        private set

    val balance: Long get() = state.value.balanceChips
    val totalEarned: Long get() = state.value.totalEarnedLifetime

    override fun observe(): Flow<WalletState> = state

    override suspend fun getState(): WalletState = state.value

    override suspend fun buyIn(stake: Long): BuyInResult {
        buyInCalls += 1
        val current = state.value
        if (current.balanceChips < stake) {
            return BuyInResult.Insufficient(current.balanceChips, stake)
        }
        state.value = current.copy(balanceChips = current.balanceChips - stake)
        return BuyInResult.Success(state.value.balanceChips)
    }

    override suspend fun settle(finalChips: Long, initialBuyIn: Long) {
        settleCalls += 1
        lastSettledFinalChips = finalChips
        lastSettledInitialBuyIn = initialBuyIn
        val credited = finalChips.coerceAtLeast(0L)
        val earned = (credited - initialBuyIn.coerceAtLeast(0L)).coerceAtLeast(0L)
        val current = state.value
        state.value = current.copy(
            balanceChips = current.balanceChips + credited,
            totalEarnedLifetime = current.totalEarnedLifetime + earned,
        )
    }

    override suspend fun resetBankrupt() {
        state.value = state.value.copy(balanceChips = WalletRepository.STARTING_BANKROLL)
    }

    override suspend fun recordCheckIn(today: LocalDate): CheckInResult =
        CheckInResult.AlreadyCheckedIn(state.value.streakDays)

    override suspend fun claimMissionReward(amount: Long) {
        if (amount <= 0L) return
        val current = state.value
        state.value = current.copy(
            balanceChips = current.balanceChips + amount,
            totalEarnedLifetime = current.totalEarnedLifetime + amount,
        )
    }

    override suspend fun applyHandOutcome(outcome: HandOutcome, opponentAvgElo: Int) {
        applyOutcomeCalls += 1
    }
}
