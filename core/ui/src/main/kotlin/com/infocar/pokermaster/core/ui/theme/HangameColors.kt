package com.infocar.pokermaster.core.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * 한게임 포커 레퍼런스 톤 — v2 디자인 토큰.
 *
 * 기존 [PokerColors] 의 그린 톤은 호환을 위해 유지하고, 신규 화면은 본 토큰을 사용.
 * 레이아웃 가이드:
 *  - 배경: 네이비 → 시안 수직 그라데이션. 펠트는 더 진한 네이비 라디얼.
 *  - 시트 카드: 가로 직사각형 (아바타 | 닉/칩 | 홀카드).
 *  - 액션 버튼: 6분할 (다이/따당/콜/쿼터/하프/올인) — 빨강/초록/청록.
 *  - 텍스트: 흰색 본문 + 하늘색 보조 + 칩 골드.
 */
object HangameColors {

    // ── 배경 그라데이션 ──────────────────────────────────────────────
    val BgTop = Color(0xFF0B2D52)
    val BgMid = Color(0xFF0F4470)
    val BgBottom = Color(0xFF1158A0)

    // ── 펠트 (테이블 안쪽 타원 영역) ────────────────────────────────
    val FeltOuter = Color(0xFF0A1B33)
    val FeltMid = Color(0xFF0E2C56)
    val FeltInner = Color(0xFF143E70)

    // ── 시트 카드 ───────────────────────────────────────────────────
    // 펠트 inner(0xFF143E70) 보다 명확히 어둡게 — 시트가 펠트 안에서 시각적으로 분리되게.
    val SeatBg = Color(0xFF0A1828)
    val SeatBgActive = Color(0xFF152E5C)
    val SeatBgFolded = Color(0xFF080F1A)
    val SeatBorder = Color(0xFF2C426A)
    val SeatBorderActive = Color(0xFFCDE940)   // 차례 = 라임 옐로
    val SeatBorderWinner = Color(0xFFFFD54F)   // 승자 = 골드
    val SeatBorderHuman = Color(0xFFCDE940)

    // ── 텍스트 ──────────────────────────────────────────────────────
    val TextPrimary = Color(0xFFFFFFFF)
    val TextSecondary = Color(0xFFB0C8E5)
    val TextMuted = Color(0xFF7A93B5)
    /** 시트 안 칩 잔고 — 빛바랜 골드. Total Pot 의 밝은 골드와 톤 차이. */
    val TextChip = Color(0xFFE0BE5C)
    val TextDanger = Color(0xFFFF5252)
    val TextLime = Color(0xFFCDE940)

    // ── 상단 띠 (닉네임/칩 + 설정/나가기) ──────────────────────────
    val HeaderBgLeft = Color(0xFF0E1F3A)
    val HeaderBgRight = Color(0xFF152841)
    val HeaderBorder = Color(0xFF2C426A)

    // ── 팟 표시 (중앙) ──────────────────────────────────────────────
    val PotBg = Color(0xFF0A1B33)
    val PotLabel = Color(0xFFB0C8E5)
    /** Total Pot 금액 — 가장 밝은 골드. 시트 안 chips 와 차별. */
    val PotValue = Color(0xFFFFD54F)

    // ── 액션 버튼 (하단 6개) ────────────────────────────────────────
    /** 다이 (FOLD) — 빨강 */
    val BtnFold = Color(0xFFE63946)
    val BtnFoldDark = Color(0xFFA02530)
    /** 따당 (CHECK) / 콜 (CALL) — 초록 */
    val BtnCall = Color(0xFF35B85B)
    val BtnCallDark = Color(0xFF1E7D3B)
    /** 쿼터 (1/4 베팅) — 청록 */
    val BtnQuarter = Color(0xFF00BFA5)
    val BtnQuarterDark = Color(0xFF00897B)
    /** 하프 (1/2 베팅) — 짙은 청록 */
    val BtnHalf = Color(0xFF26A69A)
    val BtnHalfDark = Color(0xFF00695C)
    /** 올인 — 빨강 (다이와 구분 위해 살짝 어두움) */
    val BtnAllIn = Color(0xFFD32F2F)
    val BtnAllInDark = Color(0xFF8B0000)
    /** 비활성 (조건 미충족) */
    val BtnDisabled = Color(0xFF394A66)

    // ── 카드 ────────────────────────────────────────────────────────
    val CardFace = Color(0xFFFFFFFF)
    val CardBack = Color(0xFF1F3460)
    val CardBackPattern = Color(0xFF2A4A7A)
    val CardEdgeShadow = Color(0xFF000000)

    // ── 마커 (BTN/SB/BB 디스크) ────────────────────────────────────
    val MarkerBtn = Color(0xFFB07020)         // 갈색 D 디스크
    val MarkerBtnText = Color(0xFFFFE082)
    val MarkerSb = Color(0xFF35B85B)
    val MarkerBb = Color(0xFFFF9800)

    // ── 칩 베팅 표시 (시트 앞 청색 칩) ─────────────────────────────
    val BetChipBg = Color(0xFF2979FF)
    val BetChipText = Color(0xFFFFFFFF)

    // ── 7스터드 / 하이로우 전용 액센트 ──────────────────────────────
    /**
     * 7스터드 전용 보조색 — 한국식 스터드의 "구사/브링인" 같은 모드 특성을 시각적으로 강조.
     * 기본 lime 활성색과 구분되도록 Hi(보랏빛)/Lo(시원한 시안)로 분할.
     */
    val StudAccent = Color(0xFFCDE940)         // 7스터드 모드 라벨 (lime)
    val HiLoHiBadge = Color(0xFFFFB74D)        // Hi 사이드 (오렌지/골드)
    val HiLoLoBadge = Color(0xFF4FC3F7)        // Lo 사이드 (시안)
    val HiLoScoopBadge = Color(0xFFFFD54F)     // 스쿠프 (밝은 골드)
    /** 7스터드 모드에서만 노출되는 "구사" 버튼 — 일반 폴드와 구분. */
    val BtnSaveLife = Color(0xFFB5651D)
    val BtnSaveLifeDark = Color(0xFF7A3F0F)

    // ── 그라데이션 헬퍼 ─────────────────────────────────────────────
    /** 메인 배경 — 네이비 수직 그라데이션. */
    val BackgroundBrush: Brush
        get() = Brush.verticalGradient(colors = listOf(BgTop, BgMid, BgBottom))

    /** 펠트 라디얼 — 중앙 밝고 가장자리 어둡게. */
    fun feltBrush(centerOffset: androidx.compose.ui.geometry.Offset, radius: Float): Brush =
        Brush.radialGradient(
            colors = listOf(FeltInner, FeltMid, FeltOuter),
            center = centerOffset,
            radius = radius,
        )

    /** 시트 카드 — 활성 여부에 따른 수직 그라데이션. */
    fun seatBrush(active: Boolean): Brush = Brush.verticalGradient(
        colors = if (active) listOf(SeatBgActive, SeatBg)
        else listOf(SeatBg, SeatBgFolded),
    )

    /** 액션 버튼 수직 그라데이션 (위 밝, 아래 어둡). */
    fun buttonBrush(top: Color, bottom: Color): Brush =
        Brush.verticalGradient(colors = listOf(top, bottom))
}
