package com.infocar.pokermaster.core.data.profile

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 사용자 닉네임 저장소. 모든 모듈 (lobby/table/settings) 공통 접근.
 *
 *  - 저장: SharedPreferences (`poker_profile`).
 *  - 길이 제한: 12자, 공백 trim, 빈값/동일값은 no-op.
 *  - 기본값: "나" (기존 hardcoded TableViewModel 인자와 동일).
 *
 * SharedPreferences 사용 — 단일 String key 라 DataStore 까지 도입 X. 향후 :core:preferences
 * 추출 시 통합 가능.
 */
@Singleton
class NicknameRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val _nickname = MutableStateFlow(load())
    val nickname: Flow<String> = _nickname.asStateFlow()

    /** 동기 1회 조회 — TableViewModel.createDefault 같은 비-Compose 호출처용. */
    fun current(): String = _nickname.value

    fun set(nickname: String) {
        val trimmed = nickname.trim().take(MAX_LENGTH)
        if (trimmed.isBlank() || trimmed == _nickname.value) return
        prefs.edit { putString(KEY_NICKNAME, trimmed) }
        _nickname.value = trimmed
    }

    private fun load(): String =
        prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME) ?: DEFAULT_NICKNAME

    companion object {
        const val DEFAULT_NICKNAME: String = "나"
        const val MAX_LENGTH: Int = 12
        private const val PREF_NAME: String = "poker_profile"
        private const val KEY_NICKNAME: String = "user_nickname"
    }
}
