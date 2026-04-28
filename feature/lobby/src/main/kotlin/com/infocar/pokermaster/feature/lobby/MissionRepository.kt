package com.infocar.pokermaster.feature.lobby

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 일일 미션 상태 영속 저장소 — 잔여9-도전과제 minimal.
 *
 * 사용 키:
 *  - `last_claimed_epoch_day` (Long): 마지막 보상 수령 일자 (LocalDate.toEpochDay()).
 *    오늘 epoch != lastClaimed 면 새 보상 사이클.
 *
 * SharedPreferences 사용 — DataStore 가 아닌 이유: lobby 모듈이 feature.table 의
 * SettingsRepository(=DataStore) 에 의존할 수 없어 (모듈 단방향), 별도 파일로 격리.
 * 향후 :core:preferences 추출 시 migration 가능.
 */
@Singleton
class MissionRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext.getSharedPreferences(
        PREF_NAME, Context.MODE_PRIVATE,
    )

    /** 미션 id 별 마지막 보상 수령 epoch day. 기본 0L (미수령). */
    fun lastClaimedEpochDay(missionId: String): Long =
        prefs.getLong(keyFor(missionId), 0L)

    fun saveClaimed(missionId: String, epochDay: Long) {
        prefs.edit { putLong(keyFor(missionId), epochDay) }
    }

    private fun keyFor(missionId: String): String = "$KEY_LAST_CLAIMED_PREFIX$missionId"

    companion object {
        private const val PREF_NAME = "poker_mission"
        private const val KEY_LAST_CLAIMED_PREFIX = "last_claimed_epoch_day_"
    }
}
