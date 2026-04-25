package com.infocar.pokermaster.feature.table.guide

/**
 * 가이드 모드 사용자 설정 — v1.1 §1.2.
 *
 * 이 데이터 클래스는 순수 모델로, 영속화(SharedPreferences/DataStore) 는
 * 다음 패스에서 [com.infocar.pokermaster.feature.table.ResumeRepository] 와 비슷한 방식으로 붙인다.
 *
 * @param guideModeEnabled 가이드 오버레이를 켤지 여부. 기본 ON (입문자 친화).
 * @param seenWelcome [GuideStep.Welcome] 을 한 번이라도 본 적이 있는지.
 *                    true 면 다음 세션부터 ActionHint 부터 바로 시작한다.
 */
data class GuideSettings(
    val guideModeEnabled: Boolean = DEFAULT_GUIDE_ENABLED,
    val seenWelcome: Boolean = false,
) {
    /** 처음 진입하면 Welcome, 이후엔 ActionHint 가 첫 단계. */
    fun initialStep(defaultHint: String = DEFAULT_HINT): GuideStep =
        if (!seenWelcome) {
            GuideStep.Welcome
        } else {
            GuideStep.ActionHint(defaultHint)
        }

    /** Welcome 을 보고난 뒤 상태. */
    fun markWelcomeSeen(): GuideSettings =
        if (seenWelcome) this else copy(seenWelcome = true)

    /** 사용자가 가이드를 껐을 때. */
    fun disable(): GuideSettings =
        if (!guideModeEnabled) this else copy(guideModeEnabled = false)

    /** 사용자가 가이드를 다시 켰을 때. */
    fun enable(): GuideSettings =
        if (guideModeEnabled) this else copy(guideModeEnabled = true)

    companion object {
        /**
         * 신규 설치 사용자는 가이드 OFF — 한게임 풍 풀스크린 펠트가 가이드 backdrop 으로
         * 가려지지 않도록. 인게임 메뉴에서 "가이드 모드 켜기" 토글로 명시 활성화.
         */
        const val DEFAULT_GUIDE_ENABLED: Boolean = false

        /** Welcome 이후 첫 ActionHint 의 기본 문구. */
        const val DEFAULT_HINT: String =
            "현재 액션 차례예요. 베팅 바의 각 버튼을 눌러 보면 어떤 행동인지 확인할 수 있어요."

        /** 신규 설치/초기 상태. */
        val Default: GuideSettings = GuideSettings()

        /** 이미 튜토리얼을 본 복귀 유저. */
        val Returning: GuideSettings = GuideSettings(
            guideModeEnabled = true,
            seenWelcome = true,
        )
    }
}
