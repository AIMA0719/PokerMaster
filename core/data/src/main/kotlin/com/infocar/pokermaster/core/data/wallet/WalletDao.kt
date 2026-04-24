package com.infocar.pokermaster.core.data.wallet

import androidx.room.Dao
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface WalletDao {

    @Query("SELECT * FROM wallet WHERE id = 0 LIMIT 1")
    suspend fun get(): WalletEntity?

    @Query("SELECT * FROM wallet WHERE id = 0 LIMIT 1")
    fun observe(): Flow<WalletEntity?>

    @Upsert
    suspend fun upsert(entity: WalletEntity)

    @Query("DELETE FROM wallet")
    suspend fun clear()
}
