package com.infocar.pokermaster.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.infocar.pokermaster.core.data.history.HandHistoryDao
import com.infocar.pokermaster.core.data.history.HandHistoryEntity
import com.infocar.pokermaster.core.data.wallet.WalletDao
import com.infocar.pokermaster.core.data.wallet.WalletEntity

/**
 * 앱 전역 Room 데이터베이스.
 *
 * 스키마 버전:
 *  - v1 (M5-A): HandHistoryEntity.
 *  - v2 (M6-C): + WalletEntity. 기존 데이터는 fallbackToDestructiveMigration 으로 drop
 *    (배포 전이라 실 사용자 없음). 배포 이후 schema 변경 시 proper migration 작성.
 */
@Database(
    entities = [HandHistoryEntity::class, WalletEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class PokerMasterDatabase : RoomDatabase() {
    abstract fun handHistoryDao(): HandHistoryDao
    abstract fun walletDao(): WalletDao

    companion object {
        const val NAME: String = "pokermaster.db"
    }
}
