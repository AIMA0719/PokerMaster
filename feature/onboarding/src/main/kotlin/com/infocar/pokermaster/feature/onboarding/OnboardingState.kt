package com.infocar.pokermaster.feature.onboarding

/** 4단계 위저드 진행 상태. WELCOME → AGE_GATE → TERMS → NICKNAME. */
enum class OnboardingStep { WELCOME, AGE_GATE, TERMS, NICKNAME }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.WELCOME,
    val ageConfirmed: Boolean = false,
    val termsAccepted: Boolean = false,
    val privacyAccepted: Boolean = false,
    val nickname: String = "",
) {
    val canAdvance: Boolean get() = when (step) {
        OnboardingStep.WELCOME -> true
        OnboardingStep.AGE_GATE -> ageConfirmed
        OnboardingStep.TERMS -> termsAccepted && privacyAccepted
        OnboardingStep.NICKNAME -> nickname.trim().let { it.isNotBlank() && it.length <= 12 }
    }
}

/** 완료 결과 — 호출자 화면(앱)이 영속화 결정. */
data class OnboardingResult(
    val nickname: String,
    val ageConfirmed: Boolean,
    val termsAccepted: Boolean,
    val privacyAccepted: Boolean,
    /** 동의 시점 (epochMs) — 약관 변경 시 재동의 판단 근거. */
    val acceptedAtMs: Long,
    /** 동의한 약관 버전. 약관 본문 변경 시 [TERMS_VERSION] 을 올려 재동의 유도. */
    val termsVersion: Int,
)

/** DataStore key (`Preferences`) — app 모듈에서 읽을 수 있도록 상수 export. */
object OnboardingPrefs {
    const val PREFS_NAME = "pokermaster_onboarding"
    const val KEY_COMPLETED = "completed"
    const val KEY_NICKNAME = "nickname"
    const val KEY_AGE_CONFIRMED = "age_confirmed"
    const val KEY_TERMS_ACCEPTED = "terms_accepted"
    const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
    const val KEY_ACCEPTED_AT_MS = "accepted_at_ms"
    const val KEY_TERMS_VERSION = "terms_version"

    /** 현재 약관 버전. 본문 변경 시 +1 → 기존 사용자 재동의 유도. */
    const val TERMS_VERSION: Int = 1
}
