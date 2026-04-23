package com.infocar.pokermaster.feature.table.a11y

import androidx.compose.ui.graphics.Color

/**
 * 카드 슈트별 색상 토큰. 색약(CVD) 사용자 대응용 4종 팔레트 제공.
 *
 * v1.1 §1.2 접근성 스펙:
 *  - 일반 모드: 전통 4색 구분 (♠ 검정, ♥ 빨강, ♦ 빨강, ♣ 검정) — 실제로는 4색 구분 위해
 *    spade/club 도 약간 다른 hue 사용 (♠ 딥 네이비, ♣ 포레스트 그린 쪽으로 살짝 tint).
 *  - Deutan (적록 중 녹색약): 빨강↔초록 구분 약함 → heart/diamond 는 warm magenta/orange 로 분리,
 *    club 은 파란 계열로 뺌.
 *  - Protan (적록 중 적색약): 적색 인지 저하 → heart 는 더 어두운 마젠타, diamond 는 오렌지/노랑.
 *  - Tritan (청황): 파랑↔노랑 구분 약함 → club/spade 는 블랙/그레이 로 고립, diamond 는 청록 회피.
 *
 * core/ui MaterialTheme 을 참조하지 않고 독립 토큰으로 유지 (세팅 화면에서 미리보기 시
 * Theme 바인딩 없이 pure-preview 가능하도록).
 */
data class ColorblindCardColors(
    val club: Color,
    val diamond: Color,
    val heart: Color,
    val spade: Color,
)

object ColorblindPalettes {

    /** 일반 시야. 전통 배치 + 4색 구분 미세 tint. */
    val Normal: ColorblindCardColors = ColorblindCardColors(
        club = Color(0xFF1B5E20),    // 딥 포레스트 그린 (검정 대비 살짝 초록)
        diamond = Color(0xFFD32F2F), // 레드
        heart = Color(0xFFB71C1C),   // 다크 레드 (diamond 와 명도 차이)
        spade = Color(0xFF0D1B2A),   // 딥 네이비 (club 과 hue 로 분리)
    )

    /** Deutan (녹색약): 초록 감도 저하 → club 을 블루 계열로 이동. */
    val Deutan: ColorblindCardColors = ColorblindCardColors(
        club = Color(0xFF1565C0),    // 스트롱 블루
        diamond = Color(0xFFEF6C00), // 오렌지 (red 와 분리)
        heart = Color(0xFFC2185B),   // 마젠타 계열
        spade = Color(0xFF000000),   // 블랙
    )

    /** Protan (적색약): 적색 인지 저하 → heart 는 어두운 마젠타, diamond 오렌지로 분리. */
    val Protan: ColorblindCardColors = ColorblindCardColors(
        club = Color(0xFF004D40),    // 딥 틸
        diamond = Color(0xFFF9A825), // 앰버/오렌지-노랑
        heart = Color(0xFF6A1B9A),   // 딥 퍼플/마젠타
        spade = Color(0xFF000000),   // 블랙
    )

    /** Tritan (청황): 파랑↔노랑 구분 약함 → 블루/옐로우 회피, red/magenta 축으로 분리. */
    val Tritan: ColorblindCardColors = ColorblindCardColors(
        club = Color(0xFF424242),    // 다크 그레이
        diamond = Color(0xFFE53935), // 레드
        heart = Color(0xFFAD1457),   // 딥 핑크/마젠타
        spade = Color(0xFF000000),   // 블랙
    )

    /** [ColorblindMode] 에 대응하는 팔레트 반환. */
    fun of(mode: ColorblindMode): ColorblindCardColors = when (mode) {
        ColorblindMode.NORMAL -> Normal
        ColorblindMode.DEUTAN -> Deutan
        ColorblindMode.PROTAN -> Protan
        ColorblindMode.TRITAN -> Tritan
    }
}
