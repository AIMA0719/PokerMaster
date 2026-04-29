package com.infocar.pokermaster.feature.table.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
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

// SimpleDateFormat은 thread-safe 가 아니므로 ThreadLocal 캐시.
private val DAILY_STAMP_FORMAT: ThreadLocal<SimpleDateFormat> = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue() = SimpleDateFormat("yyyy-MM-dd", Locale.US)
}

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
            bgmEnabled = prefs[Keys.BgmEnabled] ?: SfxPolicy.DEFAULT_BGM_ENABLED,
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

    /**
     * UI-Images: opt-in 이미지 카드 모드. 기본 false → 기존 Canvas/Text 자체 렌더 유지.
     * true 면 [com.infocar.pokermaster.feature.table.LocalUseImageCards] 가 활성화되어
     * PlayingCard 가 PNG 자산을 그린다.
     */
    val useImageCards: Flow<Boolean> = store.data.map { prefs ->
        prefs[Keys.UseImageCards] ?: false
    }

    /**
     * 책임 있는 게임 / 자기 제한 설정. 청소년 보호·과몰입 방지.
     *  - breakReminderMinutes: N분마다 휴식 안내 토스트. 0 = 비활성.
     *  - dailyHandLimit: 일일 핸드 수 한도. 0 = 무제한.
     *  - dailySessionMinutes: 일일 누적 플레이 시간 한도. 0 = 무제한.
     */
    val selfLimit: Flow<SelfLimitSettings> = store.data.map { prefs ->
        SelfLimitSettings(
            breakReminderMinutes = prefs[Keys.BreakReminderMinutes] ?: 30,
            dailyHandLimit = prefs[Keys.DailyHandLimit] ?: 0,
            dailySessionMinutes = prefs[Keys.DailySessionMinutes] ?: 0,
        )
    }

    suspend fun setSelfLimit(limit: SelfLimitSettings) {
        store.edit {
            it[Keys.BreakReminderMinutes] = limit.breakReminderMinutes
            it[Keys.DailyHandLimit] = limit.dailyHandLimit
            it[Keys.DailySessionMinutes] = limit.dailySessionMinutes
        }
    }

    /**
     * 일일 누적 사용량 — 핸드 수와 플레이 시간(분).
     * 저장된 날짜와 오늘 날짜가 다르면 자동 리셋(0/0 반환).
     */
    val dailyUsage: Flow<DailyUsage> = store.data.map { prefs ->
        val storedDate = prefs[Keys.DailyUsageDate]
        val today = todayStamp()
        if (storedDate == today) {
            DailyUsage(
                date = today,
                hands = prefs[Keys.DailyUsageHands] ?: 0,
                playMinutes = prefs[Keys.DailyUsageMinutes] ?: 0,
            )
        } else {
            DailyUsage(date = today, hands = 0, playMinutes = 0)
        }
    }

    /** 핸드 수 누적 — handsDelta는 마지막 호출 이후 진행한 핸드 수. */
    suspend fun addDailyHands(handsDelta: Int) {
        if (handsDelta <= 0) return
        store.edit {
            val today = todayStamp()
            val storedDate = it[Keys.DailyUsageDate]
            val rolledOver = storedDate != today
            val base = if (rolledOver) 0 else it[Keys.DailyUsageHands] ?: 0
            if (rolledOver) {
                it[Keys.DailyUsageDate] = today
                it[Keys.DailyUsageMinutes] = 0
            }
            it[Keys.DailyUsageHands] = base + handsDelta
        }
    }

    /** 플레이 시간 누적 — minutesDelta는 마지막 호출 이후 경과 분. */
    suspend fun addDailyMinutes(minutesDelta: Int) {
        if (minutesDelta <= 0) return
        store.edit {
            val today = todayStamp()
            val storedDate = it[Keys.DailyUsageDate]
            val rolledOver = storedDate != today
            val base = if (rolledOver) 0 else it[Keys.DailyUsageMinutes] ?: 0
            if (rolledOver) {
                it[Keys.DailyUsageDate] = today
                it[Keys.DailyUsageHands] = 0
            }
            it[Keys.DailyUsageMinutes] = base + minutesDelta
        }
    }

    private fun todayStamp(): String = DAILY_STAMP_FORMAT.get()!!.format(Date())

    suspend fun setSfxPolicy(policy: SfxPolicy) {
        store.edit {
            it[Keys.SoundEnabled] = policy.soundEnabled
            it[Keys.HapticEnabled] = policy.hapticEnabled
            it[Keys.BgmEnabled] = policy.bgmEnabled
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

    suspend fun setUseImageCards(enabled: Boolean) {
        store.edit { it[Keys.UseImageCards] = enabled }
    }

    private object Keys {
        val SoundEnabled = booleanPreferencesKey("sfx.sound_enabled")
        val HapticEnabled = booleanPreferencesKey("sfx.haptic_enabled")
        val BgmEnabled = booleanPreferencesKey("sfx.bgm_enabled")
        val ColorblindMode = stringPreferencesKey("a11y.colorblind_mode")
        val LargerText = booleanPreferencesKey("a11y.larger_text")
        val HighContrastCards = booleanPreferencesKey("a11y.high_contrast_cards")
        val AnnounceActions = booleanPreferencesKey("a11y.announce_actions")
        val ReduceMotion = booleanPreferencesKey("a11y.reduce_motion")
        val GuideEnabled = booleanPreferencesKey("guide.mode_enabled")
        val SeenWelcome = booleanPreferencesKey("guide.seen_welcome")
        val ThemeMode = stringPreferencesKey("ui.theme_mode")
        val UseImageCards = booleanPreferencesKey("ui.use_image_cards")
        val BreakReminderMinutes = intPreferencesKey("self_limit.break_minutes")
        val DailyHandLimit = intPreferencesKey("self_limit.daily_hand_limit")
        val DailySessionMinutes = intPreferencesKey("self_limit.daily_session_minutes")
        val DailyUsageDate = stringPreferencesKey("self_limit.usage_date")
        val DailyUsageHands = intPreferencesKey("self_limit.usage_hands")
        val DailyUsageMinutes = intPreferencesKey("self_limit.usage_minutes")
    }
}

/** 오늘(date 기준) 누적 사용량 — 자정 넘어가면 자동 리셋. */
data class DailyUsage(
    val date: String,
    val hands: Int,
    val playMinutes: Int,
)

/**
 * 자기 제한 설정 — 청소년 보호 / 과몰입 방지.
 *
 * @property breakReminderMinutes N분마다 휴식 안내 (0 = off).
 * @property dailyHandLimit 일일 핸드 한도 (0 = 무제한).
 * @property dailySessionMinutes 일일 누적 플레이 시간(분) 한도 (0 = 무제한).
 */
data class SelfLimitSettings(
    val breakReminderMinutes: Int = 30,
    val dailyHandLimit: Int = 0,
    val dailySessionMinutes: Int = 0,
) {
    /**
     * 오늘 누적 사용량과 비교해 도달한 한도를 반환. Hand 가 Time 보다 우선 (UX: 핸드 한도가 명확).
     */
    fun evaluate(usage: DailyUsage): LimitTriggerKind? {
        if (dailyHandLimit > 0 && usage.hands >= dailyHandLimit) {
            return LimitTriggerKind.Hand(limit = dailyHandLimit, current = usage.hands)
        }
        if (dailySessionMinutes > 0 && usage.playMinutes >= dailySessionMinutes) {
            return LimitTriggerKind.Time(limit = dailySessionMinutes, current = usage.playMinutes)
        }
        return null
    }

    companion object {
        val Default = SelfLimitSettings()
    }
}

/** 자기 제한 한도 도달 종류. AppNav 진입 게이트, TableScreen 누적 도달 다이얼로그가 공유. */
sealed class LimitTriggerKind {
    abstract val limit: Int
    abstract val current: Int
    abstract val message: String

    data class Hand(override val limit: Int, override val current: Int) : LimitTriggerKind() {
        override val message: String
            get() = "오늘 ${limit}핸드 한도에 도달했어요 (현재 ${current}핸드). 충분한 휴식을 권장합니다."
    }

    data class Time(override val limit: Int, override val current: Int) : LimitTriggerKind() {
        override val message: String
            get() = "오늘 ${limit}분 누적 플레이에 도달했어요 (현재 ${current}분). 잠시 멈추고 쉬어가세요."
    }
}
