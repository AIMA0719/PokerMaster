package com.infocar.pokermaster.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// M0: 시스템 기본 폰트 사용. M5 에서 Pretendard 도입 예정 (SIL OFL 1.1).
// 한국어 폰트 특성상 lineHeight 는 fontSize × 1.4~1.5 권장 (영어 권장 1.2 보다 여유).
private val DefaultFamily = FontFamily.Default

val PokerTypography = Typography(
    // ── Display: 스플래시·온보딩 헤드라인 ───────────────────
    displayLarge = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 44.sp, letterSpacing = (-0.25).sp),
    displayMedium = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Bold, fontSize = 30.sp, lineHeight = 38.sp),
    displaySmall = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 34.sp),

    // ── Headline: 화면 헤더 / 큰 카드 헤더 ──────────────────
    headlineLarge = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 32.sp),
    headlineMedium = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 30.sp),
    headlineSmall = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),

    // ── Title: 카드/섹션 헤더 ──────────────────────────────
    titleLarge = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.15.sp),
    titleSmall = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),

    // ── Body: 본문 ─────────────────────────────────────────
    bodyLarge = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.5.sp),
    bodyMedium = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.4.sp),

    // ── Label: 칩/배지/캡션 ────────────────────────────────
    labelLarge = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    labelMedium = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = DefaultFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.5.sp),
)
