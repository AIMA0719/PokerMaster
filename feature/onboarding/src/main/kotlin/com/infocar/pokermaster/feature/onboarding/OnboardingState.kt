package com.infocar.pokermaster.feature.onboarding

/** 4단계 위저드 진행 상태. */
enum class OnboardingStep { WELCOME, AGE_GATE, NICKNAME, PERMISSION }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val ageConfirmed: Boolean = false,
    val nickname: String = "",
    val permissionAcked: Boolean = false,
) {
    val canAdvance: Boolean get() = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.AGE_GATE -> ageConfirmed
        OnboardingStep.NICKNAME -> nickname.isNotBlank() && nickname.length <= 12
        OnboardingStep.PERMISSION -> true
    }
}

/** 완료 결과 — 호출자 화면(앱)이 영속화 결정. */
data class OnboardingResult(
    val nickname: String,
    val ageConfirmed: Boolean,
)

/** DataStore key (`Preferences`) — app 모듈에서 읽을 수 있도록 상수 export. */
object OnboardingPrefs {
    const val PREFS_NAME = "pokermaster_onboarding"
    const val KEY_COMPLETED = "completed"
    const val KEY_NICKNAME = "nickname"
}
