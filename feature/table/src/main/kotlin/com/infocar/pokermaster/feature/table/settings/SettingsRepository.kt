package com.infocar.pokermaster.feature.table.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.infocar.pokermaster.core.ui.theme.ThemeMode
import com.infocar.pokermaster.feature.table.a11y.A11ySettings
import com.infocar.pokermaster.feature.table.a11y.ColorblindMode
import com.infocar.pokermaster.feature.table.guide.GuideSettings
import com.infocar.pokermaster.feature.table.sfx.SfxPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "poker_settings")

/**
 * SfxPolicy / A11ySettings / GuideSettings 의 영속 저장소. Sprint3-A.
 *
 *  - PreferencesDataStore 하나(poker_settings)에 flat key-value 로 9개 필드를 저장.
 *  - Flow<T> 는 초기값 없이 store.data 로부터 map — 소비자가 collectAsState(initial=Default) 로 처리.
 *  - 누락된 키는 각 설정의 DEFAULT 상수로 보정하므로 스키마 추가 시 backward compatible.
 *  - ColorblindMode 는 enum.name 으로 직렬화. unknown 문자열이면 NORMAL 로 복구.
 */
@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val store = context.applicationContext.settingsDataStore

    val sfxPolicy: Flow<SfxPolicy> = store.data.map { prefs ->
        SfxPolicy(
            soundEnabled = prefs[Keys.SoundEnabled] ?: SfxPolicy.DEFAULT_SOUND_ENABLED,
            hapticEnabled = prefs[Keys.HapticEnabled] ?: SfxPolicy.DEFAULT_HAPTIC_ENABLED,
        )
    }

    val a11ySettings: Flow<A11ySettings> = store.data.map { prefs ->
        val cbName = prefs[Keys.ColorblindMode]
        val cb = ColorblindMode.entries.firstOrNull { it.name == cbName } ?: ColorblindMode.NORMAL
        A11ySettings(
            colorblindMode = cb,
            largerText = prefs[Keys.LargerText] ?: false,
            highContrastCards = prefs[Keys.HighContrastCards] ?: false,
            announceActionsAudibly = prefs[Keys.AnnounceActions] ?: true,
            reduceMotion = prefs[Keys.ReduceMotion] ?: false,
        )
    }

    val guideSettings: Flow<GuideSettings> = store.data.map { prefs ->
        GuideSettings(
            guideModeEnabled = prefs[Keys.GuideEnabled] ?: GuideSettings.DEFAULT_GUIDE_ENABLED,
            seenWelcome = prefs[Keys.SeenWelcome] ?: false,
        )
    }

    /** M7-A: 테마 모드. 미지정/unknown 문자열 → ThemeMode.DEFAULT (LIGHT). */
    val themeMode: Flow<ThemeMode> = store.data.map { prefs ->
        ThemeMode.fromStorage(prefs[Keys.ThemeMode])
    }

    suspend fun setSfxPolicy(policy: SfxPolicy) {
        store.edit {
            it[Keys.SoundEnabled] = policy.soundEnabled
            it[Keys.HapticEnabled] = policy.hapticEnabled
        }
    }

    suspend fun setA11ySettings(settings: A11ySettings) {
        store.edit {
            it[Keys.ColorblindMode] = settings.colorblindMode.name
            it[Keys.LargerText] = settings.largerText
            it[Keys.HighContrastCards] = settings.highContrastCards
            it[Keys.AnnounceActions] = settings.announceActionsAudibly
            it[Keys.ReduceMotion] = settings.reduceMotion
        }
    }

    suspend fun setGuideSettings(settings: GuideSettings) {
        store.edit {
            it[Keys.GuideEnabled] = settings.guideModeEnabled
            it[Keys.SeenWelcome] = settings.seenWelcome
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[Keys.ThemeMode] = mode.name }
    }

    private object Keys {
        val SoundEnabled = booleanPreferencesKey("sfx.sound_enabled")
        val HapticEnabled = booleanPreferencesKey("sfx.haptic_enabled")
        val ColorblindMode = stringPreferencesKey("a11y.colorblind_mode")
        val LargerText = booleanPreferencesKey("a11y.larger_text")
        val HighContrastCards = booleanPreferencesKey("a11y.high_contrast_cards")
        val AnnounceActions = booleanPreferencesKey("a11y.announce_actions")
        val ReduceMotion = booleanPreferencesKey("a11y.reduce_motion")
        val GuideEnabled = booleanPreferencesKey("guide.mode_enabled")
        val SeenWelcome = booleanPreferencesKey("guide.seen_welcome")
        val ThemeMode = stringPreferencesKey("ui.theme_mode")
    }
}
