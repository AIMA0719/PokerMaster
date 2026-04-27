package com.infocar.pokermaster.feature.table.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.ui.theme.ThemeMode
import com.infocar.pokermaster.feature.table.a11y.A11ySettings
import com.infocar.pokermaster.feature.table.a11y.ColorblindMode
import com.infocar.pokermaster.feature.table.guide.GuideSettings
import com.infocar.pokermaster.feature.table.sfx.SfxPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 설정 화면 VM — M6-A.
 *
 * 기존 [SettingsRepository] (SFX/A11y/Guide) + [HandHistoryRepository] (데이터 초기화) 를 합성해
 * 하나의 화면에 노출. 값 변경은 즉시 DataStore 에 영속.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepo: SettingsRepository,
    private val historyRepo: HandHistoryRepository,
) : ViewModel() {

    val state: StateFlow<SettingsUiState> =
        combine(
            settingsRepo.sfxPolicy,
            settingsRepo.a11ySettings,
            settingsRepo.guideSettings,
            settingsRepo.themeMode,
            settingsRepo.useImageCards,
        ) { sfx, a11y, guide, theme, useImages ->
            SettingsUiState(sfx, a11y, guide, theme, useImageCards = useImages, loaded = true)
        }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SettingsUiState(loaded = false),
            )

    private val _lastClearedCount = MutableStateFlow<Int?>(null)
    val lastClearedCount: StateFlow<Int?> = _lastClearedCount

    fun setSoundEnabled(enabled: Boolean) = viewModelScope.launch {
        val current = state.value.sfx
        settingsRepo.setSfxPolicy(current.copy(soundEnabled = enabled))
    }

    fun setHapticEnabled(enabled: Boolean) = viewModelScope.launch {
        val current = state.value.sfx
        settingsRepo.setSfxPolicy(current.copy(hapticEnabled = enabled))
    }

    fun setColorblindMode(mode: ColorblindMode) = viewModelScope.launch {
        settingsRepo.setA11ySettings(state.value.a11y.copy(colorblindMode = mode))
    }

    fun setLargerText(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setA11ySettings(state.value.a11y.copy(largerText = enabled))
    }

    fun setReduceMotion(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setA11ySettings(state.value.a11y.copy(reduceMotion = enabled))
    }

    fun setHighContrastCards(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setA11ySettings(state.value.a11y.copy(highContrastCards = enabled))
    }

    fun setAnnounceActions(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setA11ySettings(state.value.a11y.copy(announceActionsAudibly = enabled))
    }

    fun setGuideMode(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setGuideSettings(state.value.guide.copy(guideModeEnabled = enabled))
    }

    fun setThemeMode(mode: ThemeMode) = viewModelScope.launch {
        settingsRepo.setThemeMode(mode)
    }

    fun setUseImageCards(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setUseImageCards(enabled)
    }

    fun clearAllHistory() = viewModelScope.launch {
        val before = historyRepo.count()
        historyRepo.clear()
        _lastClearedCount.value = before
    }

    fun acknowledgeCleared() {
        _lastClearedCount.value = null
    }
}

data class SettingsUiState(
    val sfx: SfxPolicy = SfxPolicy(),
    val a11y: A11ySettings = A11ySettings(),
    val guide: GuideSettings = GuideSettings(),
    val themeMode: ThemeMode = ThemeMode.DEFAULT,
    val useImageCards: Boolean = false,
    val loaded: Boolean = false,
)
