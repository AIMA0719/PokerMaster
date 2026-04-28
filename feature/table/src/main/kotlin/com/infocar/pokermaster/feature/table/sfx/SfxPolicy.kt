package com.infocar.pokermaster.feature.table.sfx

/**
 * 사운드/햅틱 On-Off 정책.
 *
 * 호출부(ViewModel/UI)는 재생 직전에 이 플래그를 체크해서 [SoundManager]/[HapticManager] 호출을 스킵해야 함.
 * 설정 화면이 생기면 DataStore에서 읽어 이 객체로 매핑.
 */
data class SfxPolicy(
    val soundEnabled: Boolean = DEFAULT_SOUND_ENABLED,
    val hapticEnabled: Boolean = DEFAULT_HAPTIC_ENABLED,
    val bgmEnabled: Boolean = DEFAULT_BGM_ENABLED,
) {
    companion object {
        const val DEFAULT_SOUND_ENABLED: Boolean = true
        const val DEFAULT_HAPTIC_ENABLED: Boolean = true
        // BGM 은 호불호 + 배터리 영향 → 기본 OFF. 사용자가 명시적으로 ON.
        const val DEFAULT_BGM_ENABLED: Boolean = false

        /** 기본값 (효과음/햅틱 ON, BGM OFF). */
        val Default: SfxPolicy = SfxPolicy()

        /** 무음 모드 (전부 꺼짐) — 시스템 Do-Not-Disturb/접근성용. */
        val Silent: SfxPolicy = SfxPolicy(
            soundEnabled = false,
            hapticEnabled = false,
            bgmEnabled = false,
        )
    }
}
