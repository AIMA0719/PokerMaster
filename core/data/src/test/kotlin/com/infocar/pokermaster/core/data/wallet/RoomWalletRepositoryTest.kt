package com.infocar.pokermaster.core.data.wallet

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Test

class RoomWalletRepositoryTest {

    @Test
    fun settleCreditsFinalChipsButDoesNotCountBuyInRecoveryAsEarned() = runTest {
        val repo = RoomWalletRepository(FakeWalletDao())

        repo.buyIn(50_000L)
        repo.settle(finalChips = 50_000L, initialBuyIn = 50_000L)

        val state = repo.getState()
        assertThat(state.balanceChips).isEqualTo(50_000L)
        assertThat(state.totalEarnedLifetime).isEqualTo(0L)
    }

    @Test
    fun settleCountsOnlyPositiveNetProfitAsEarned() = runTest {
        val repo = RoomWalletRepository(FakeWalletDao())

        repo.buyIn(50_000L)
        repo.settle(finalChips = 60_000L, initialBuyIn = 50_000L)

        val state = repo.getState()
        assertThat(state.balanceChips).isEqualTo(60_000L)
        assertThat(state.totalEarnedLifetime).isEqualTo(10_000L)
    }

    @Test
    fun concurrentWalletMutationsDoNotLoseCredits() = runTest {
        val repo = RoomWalletRepository(FakeWalletDao(yieldBetweenOperations = true))

        repo.buyIn(50_000L)
        coroutineScope {
            repeat(50) {
                launch { repo.claimMissionReward(1L) }
                launch { repo.settle(finalChips = 1L, initialBuyIn = 0L) }
            }
        }

        val state = repo.getState()
        assertThat(state.balanceChips).isEqualTo(100L)
        assertThat(state.totalEarnedLifetime).isEqualTo(100L)
    }
}

private class FakeWalletDao(
    private val yieldBetweenOperations: Boolean = false,
) : WalletDao {
    private val entity = MutableStateFlow<WalletEntity?>(null)

    override suspend fun get(): WalletEntity? {
        if (yieldBetweenOperations) yield()
        return entity.value
    }

    override fun observe(): Flow<WalletEntity?> = entity

    override suspend fun upsert(entity: WalletEntity) {
        if (yieldBetweenOperations) yield()
        this.entity.value = entity
    }

    override suspend fun clear() {
        entity.value = null
    }
}
