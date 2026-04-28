package com.infocar.pokermaster.feature.lobby

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 티어 진급 이력 저장. 진급 모달 1회 노출 보장 — last seen tier 와 현재 tier 비교.
 *
 * - 저장: SharedPreferences "poker_profile" (NicknameRepository 와 동일 파일).
 * - 키: `last_seen_tier`.
 * - 값: TierLevel.name (BRONZE/SILVER/GOLD/PLATINUM/DIAMOND), 미저장 시 null.
 *
 * null 의미: 앱 첫 실행 → 모달 노출 X, 현재 tier 만 저장 (BRONZE 새 사용자 환영 모달은 별도).
 */
@Singleton
class TierProgressionRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    fun lastSeenTier(): TierLevel? {
        val name = prefs.getString(KEY, null) ?: return null
        return runCatching { TierLevel.valueOf(name) }.getOrNull()
    }

    fun saveTier(tier: TierLevel) {
        prefs.edit { putString(KEY, tier.name) }
    }

    companion object {
        private const val PREF_NAME = "poker_profile"
        private const val KEY = "last_seen_tier"
    }
}
