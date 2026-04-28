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
 *  - v2 (M6-C): + WalletEntity. v1→v2 정식 migration 은 [MIGRATION_1_2].
 *  - v3 (Phase E): WalletEntity.elo (Int) 컬럼 추가. v2→v3 [MIGRATION_2_3].
 *
 * exportSchema=true 로 schema JSON 을 `core/data/schemas/` 에 출력 — 배포 후 schema diff
 * 검증 + 차후 migration 작성 시 정답지 역할.
 */
@Database(
    entities = [HandHistoryEntity::class, WalletEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class PokerMasterDatabase : RoomDatabase() {
    abstract fun handHistoryDao(): HandHistoryDao
    abstract fun walletDao(): WalletDao

    companion object {
        const val NAME: String = "pokermaster.db"
    }
}
