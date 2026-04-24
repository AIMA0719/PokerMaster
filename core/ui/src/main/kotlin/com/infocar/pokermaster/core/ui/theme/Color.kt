package com.infocar.pokermaster.core.ui.theme

import androidx.compose.ui.graphics.Color

// 디자인 토큰 (v1.0 §4.8)
object PokerColors {
    val Primary = Color(0xFF0E5C3D)         // 포커 그린
    val PrimaryDark = Color(0xFF093F2A)
    val Accent = Color(0xFFD4AF37)          // 골드
    val Danger = Color(0xFFC8372D)          // 폴드
    val Success = Color(0xFF2D8C54)         // 콜
    val Warning = Color(0xFFE89B2D)

    val BackgroundDark = Color(0xFF0B1220)
    val BackgroundLight = Color(0xFFF4F1EA)
    val SurfaceDark = Color(0xFF131C2E)
    val SurfaceLight = Color(0xFFFFFFFF)

    val OnDark = Color(0xFFE8EAEE)
    val OnLight = Color(0xFF1A1A1A)

    // M7-C: 카드 뒷면 — 라이트/다크 둘 다에서 포커 느낌 유지.
    val CardBackBaseLight = Color(0xFF0E5C3D)   // 포커 그린 (Primary)
    val CardBackPatternLight = Color(0xFF1C8A5C) // 밝은 그린 — 대각 패턴
    val CardBackBaseDark = Color(0xFF123244)     // 네이비
    val CardBackPatternDark = Color(0xFF1B5566)  // 틸 — 대각 패턴
}
