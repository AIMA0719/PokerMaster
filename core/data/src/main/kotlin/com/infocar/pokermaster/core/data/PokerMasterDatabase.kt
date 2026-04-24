package com.infocar.pokermaster.core.data

import androidx.room.Database
import androidx.room.RoomDatabase
import com.infocar.pokermaster.core.data.history.HandHistoryDao
import com.infocar.pokermaster.core.data.history.HandHistoryEntity

/**
 * 앱 전역 Room 데이터베이스 — M5-A.
 *
 * 스키마 v1: HandHistoryEntity 만 포함. 이후 통계/리더보드 (§1.2.P/K) 들어오면 엔티티 추가 +
 * migration 작성.
 */
@Database(
    entities = [HandHistoryEntity::class],
    version = 1,
    // M5-A 는 schema 첫 도입이라 export 생략. v2 migration 도입 시 Room gradle plugin 으로
    // schemaLocation 지정 + true 로 전환.
    exportSchema = false,
)
abstract class PokerMasterDatabase : RoomDatabase() {
    abstract fun handHistoryDao(): HandHistoryDao

    companion object {
        const val NAME: String = "pokermaster.db"
    }
}
