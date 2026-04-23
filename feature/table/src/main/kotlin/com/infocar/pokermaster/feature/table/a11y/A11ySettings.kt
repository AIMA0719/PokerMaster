package com.infocar.pokermaster.feature.table.a11y

/**
 * 색약 모드 식별자. NORMAL = 일반 시야 (기본값).
 *
 * v1.1 §1.2: 설정 화면에서 사용자가 수동 선택. 자동 감지 없음.
 */
enum class ColorblindMode {
    NORMAL,
    DEUTAN,  // 적록 색각이상 중 녹색 인지 저하
    PROTAN,  // 적록 색각이상 중 적색 인지 저하
    TRITAN,  // 청황 색각이상 (드문 타입)
    ;

    val displayName: String
        get() = when (this) {
            NORMAL -> "일반"
            DEUTAN -> "녹색약 (Deutan)"
            PROTAN -> "적색약 (Protan)"
            TRITAN -> "청황색약 (Tritan)"
        }
}

/**
 * 런타임 접근성 설정. 이 data class 는 메모리 보관용 — persistence (DataStore) 는
 * Sprint 3 통합 패스에서 추가.
 *
 * @property colorblindMode 카드 슈트 색상 팔레트 모드.
 * @property largerText 본문/칩 텍스트를 상향 (1.15~1.3x). Compose typography 축적 사용.
 * @property highContrastCards 카드 테두리/배경 대비 강화 (다크모드 등 배경 위).
 * @property announceActionsAudibly 상대방 액션(Call/Raise/Fold)을 TalkBack LiveRegion 으로 읽어주기.
 * @property reduceMotion 칩 이동/딜링 애니메이션 축소 (전정감각 민감 사용자).
 */
data class A11ySettings(
    val colorblindMode: ColorblindMode = ColorblindMode.NORMAL,
    val largerText: Boolean = false,
    val highContrastCards: Boolean = false,
    val announceActionsAudibly: Boolean = true,
    val reduceMotion: Boolean = false,
) {
    companion object {
        /** 기본값 — 기존 사용자 경험 변경 없음 (NORMAL 팔레트 + 액션 음성 안내 on). */
        val Default: A11ySettings = A11ySettings()
    }
}
