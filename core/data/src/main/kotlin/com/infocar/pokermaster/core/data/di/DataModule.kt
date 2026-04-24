package com.infocar.pokermaster.core.data.di

import android.content.Context
import androidx.room.Room
import com.infocar.pokermaster.core.data.PokerMasterDatabase
import com.infocar.pokermaster.core.data.history.HandHistoryDao
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.data.history.RoomHandHistoryRepository
import com.infocar.pokermaster.core.data.wallet.RoomWalletRepository
import com.infocar.pokermaster.core.data.wallet.WalletDao
import com.infocar.pokermaster.core.data.wallet.WalletRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * [:core:data] Hilt 배선 — M5-A.
 *
 * DB 는 Application scope 단일 인스턴스. Repository 도 싱글톤.
 */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PokerMasterDatabase =
        Room.databaseBuilder(
            context,
            PokerMasterDatabase::class.java,
            PokerMasterDatabase.NAME,
        )
            // M5-A: schema v1 — migration 은 v2 진입 시 작성. Room 2.6.1 의 no-arg 호출.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideHandHistoryDao(db: PokerMasterDatabase): HandHistoryDao = db.handHistoryDao()

    @Provides
    @Singleton
    fun provideHandHistoryRepository(dao: HandHistoryDao): HandHistoryRepository =
        RoomHandHistoryRepository(dao)

    @Provides
    fun provideWalletDao(db: PokerMasterDatabase): WalletDao = db.walletDao()

    @Provides
    @Singleton
    fun provideWalletRepository(dao: WalletDao): WalletRepository = RoomWalletRepository(dao)
}
