package com.infocar.pokermaster.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * v1 → v2 (M6-C): `wallet` 싱글턴 테이블 신설. 기존 `hand_history` 는 변경 없음.
 *
 * 컬럼 정의는 [com.infocar.pokermaster.core.data.wallet.WalletEntity] 와 정확히 일치해야 한다 —
 * Room 이 부팅 시 schema validation 으로 비교한다. 컬럼 추가/이름 변경 시 v2→v3 migration 추가.
 */
internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `wallet` (" +
                "`id` INTEGER NOT NULL, " +
                "`balanceChips` INTEGER NOT NULL, " +
                "`lastCheckInEpochDay` INTEGER NOT NULL, " +
                "`streakDays` INTEGER NOT NULL, " +
                "`totalEarnedLifetime` INTEGER NOT NULL, " +
                "PRIMARY KEY(`id`)" +
                ")"
        )
    }
}

/**
 * v2 → v3 (Phase E): wallet 에 `elo` INTEGER 컬럼 추가. 기본 1200 — 신규/기존 사용자 동일.
 */
internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `wallet` ADD COLUMN `elo` INTEGER NOT NULL DEFAULT 1200")
    }
}

/** PokerMasterDatabase 에 등록할 모든 정식 migration. 신규 추가 시 끝에 append. */
internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(
    MIGRATION_1_2,
    MIGRATION_2_3,
)
