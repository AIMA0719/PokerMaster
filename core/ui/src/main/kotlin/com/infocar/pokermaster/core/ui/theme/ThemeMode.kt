package com.infocar.pokermaster.core.ui.theme

/**
 * 사용자가 선택 가능한 테마 모드.
 * 기본값은 [LIGHT] — 화이트 배경을 선호한다는 UX 결정 (M7-A).
 */
enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM;

    companion object {
        val DEFAULT: ThemeMode = LIGHT

        fun fromStorage(name: String?): ThemeMode =
            entries.firstOrNull { it.name == name } ?: DEFAULT
    }
}
