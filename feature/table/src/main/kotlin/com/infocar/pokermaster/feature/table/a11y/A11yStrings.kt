package com.infocar.pokermaster.feature.table.a11y

import com.infocar.pokermaster.core.model.ActionType
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.Rank
import com.infocar.pokermaster.core.model.Street
import com.infocar.pokermaster.core.model.Suit

/**
 * TalkBack 용 한국어 contentDescription 빌더.
 *
 * v1.1 §1.2 접근성 스펙:
 *  - 카드는 "스페이드 에이스" 같이 슈트+랭크 full-name (symbol 그대로 읽게 두지 않음).
 *  - 좌석은 "플레이어 이름, 칩 N, 상태(폴드/올인/대기/액션중)".
 *  - 액션 버튼은 금액 의미까지 포함 ("300 레이즈").
 *  - 팟/스트릿/모드 안내도 읽기 친화적 한국어.
 *
 * Compose 사용 예:
 * ```
 * Modifier.semantics { contentDescription = A11yStrings.card(card) }
 * ```
 */
object A11yStrings {

    // --- Card ---------------------------------------------------------------

    fun suitName(suit: Suit): String = when (suit) {
        Suit.SPADE -> "스페이드"
        Suit.HEART -> "하트"
        Suit.DIAMOND -> "다이아몬드"
        Suit.CLUB -> "클로버"
    }

    fun rankName(rank: Rank): String = when (rank) {
        Rank.TWO -> "2"
        Rank.THREE -> "3"
        Rank.FOUR -> "4"
        Rank.FIVE -> "5"
        Rank.SIX -> "6"
        Rank.SEVEN -> "7"
        Rank.EIGHT -> "8"
        Rank.NINE -> "9"
        Rank.TEN -> "10"
        Rank.JACK -> "잭"
        Rank.QUEEN -> "퀸"
        Rank.KING -> "킹"
        Rank.ACE -> "에이스"
    }

    /** 카드 단품. 예: "스페이드 에이스". */
    fun card(card: Card): String = "${suitName(card.suit)} ${rankName(card.rank)}"

    /** 덮인 카드 (홀/다운). */
    fun hiddenCard(): String = "덮인 카드"

    /** 카드 목록. showHidden=true 면 비공개 카드로 치환. */
    fun cards(cards: List<Card>, hidden: Boolean = false): String =
        if (cards.isEmpty()) "카드 없음"
        else if (hidden) List(cards.size) { hiddenCard() }.joinToString(", ")
        else cards.joinToString(", ") { card(it) }

    // --- Seat / Player ------------------------------------------------------

    /**
     * 좌석 전체 설명.
     *
     * @param isToAct 지금 이 좌석이 액션 차례인가.
     * @param isDealer BTN 여부.
     * @param showHoleCards 다운카드를 공개해서 읽어줄지 (보통 본인 좌석만 true).
     */
    fun seat(
        player: PlayerState,
        isToAct: Boolean,
        isDealer: Boolean,
        showHoleCards: Boolean = false,
    ): String {
        val parts = mutableListOf<String>()
        parts += "${player.nickname} 좌석"
        if (isDealer) parts += "딜러 버튼"
        parts += "칩 ${chips(player.chips)}"
        parts += seatStatus(player, isToAct)
        if (player.committedThisStreet > 0L) {
            parts += "이번 스트릿 베팅 ${chips(player.committedThisStreet)}"
        }
        if (showHoleCards && player.holeCards.isNotEmpty()) {
            parts += "핸드 ${cards(player.holeCards)}"
        }
        if (player.upCards.isNotEmpty()) {
            parts += "업카드 ${cards(player.upCards)}"
        }
        return parts.joinToString(", ")
    }

    fun seatStatus(player: PlayerState, isToAct: Boolean): String = when {
        player.folded -> "폴드"
        player.allIn -> "올인"
        isToAct -> "액션 차례"
        player.actedThisStreet -> "액션 완료"
        else -> "대기"
    }

    // --- Chips / Pot --------------------------------------------------------

    /** 칩 금액 읽기용. 예: 1500 → "1,500". */
    fun chips(amount: Long): String {
        if (amount == 0L) return "0"
        val neg = amount < 0
        val s = kotlin.math.abs(amount).toString().reversed().chunked(3).joinToString(",").reversed()
        return if (neg) "-$s" else s
    }

    fun pot(totalPot: Long): String = "팟 합계 ${chips(totalPot)}"

    fun sidePot(index: Int, amount: Long): String =
        if (index == 0) "메인 팟 ${chips(amount)}"
        else "사이드 팟 ${index}, ${chips(amount)}"

    // --- Street / Mode ------------------------------------------------------

    fun streetName(street: Street): String = when (street) {
        Street.ANTE -> "앤티"
        Street.PREFLOP -> "프리플랍"
        Street.FLOP -> "플랍"
        Street.TURN -> "턴"
        Street.RIVER -> "리버"
        Street.THIRD -> "써드 스트릿"
        Street.FOURTH -> "포스 스트릿"
        Street.FIFTH -> "핍스 스트릿"
        Street.SIXTH -> "식스 스트릿"
        Street.SEVENTH -> "세븐스 스트릿"
        Street.DECLARE -> "선언"
        Street.SHOWDOWN -> "쇼다운"
    }

    fun modeName(mode: GameMode): String = when (mode) {
        GameMode.HOLDEM_NL -> "노리밋 홀덤"
        GameMode.SEVEN_STUD -> "세븐 스터드"
        GameMode.SEVEN_STUD_HI_LO -> "세븐 스터드 하이로우"
    }

    // --- Action Buttons -----------------------------------------------------

    /**
     * 액션 버튼 contentDescription.
     * @param type 액션 타입.
     * @param amount 버튼에 바인딩된 금액 (commit 절대값). 베팅성 아니면 0.
     * @param toCall 현 betToCall (CALL 버튼에서 "콜 300" 표현용).
     */
    fun actionButton(type: ActionType, amount: Long = 0L, toCall: Long = 0L): String = when (type) {
        ActionType.FOLD -> "폴드 버튼"
        ActionType.CHECK -> "체크 버튼"
        ActionType.CALL -> if (toCall > 0L) "콜 ${chips(toCall)} 버튼" else "콜 버튼"
        ActionType.BET -> "베팅 ${chips(amount)} 버튼"
        ActionType.RAISE -> "레이즈 ${chips(amount)} 버튼"
        ActionType.ALL_IN -> "올인 ${chips(amount)} 버튼"
        ActionType.COMPLETE -> "콤플리트 ${chips(amount)} 버튼"
        ActionType.BRING_IN -> "브링인 ${chips(amount)} 버튼"
        ActionType.SAVE_LIFE -> "구사 버튼"
        ActionType.DECLARE_HI -> declarePromptHi
        ActionType.DECLARE_LO -> declarePromptLo
        ActionType.DECLARE_BOTH -> declarePromptBoth
    }

    // --- HiLo Declare 안내 -------------------------------------------------

    /** HI 선언 버튼/뱃지. */
    const val declarePromptHi: String = "하이 선언"

    /** LO 선언 버튼/뱃지. */
    const val declarePromptLo: String = "로우 선언"

    /** Both(scoop) 선언 — 위험 안내 포함. */
    const val declarePromptBoth: String = "양방향 선언, 두 방향 모두 사올 1등 필수"

    /** 상대 좌석의 선언이 마스킹되어 보이지 않을 때. */
    const val declarationOpponentHidden: String = "상대 선언 비공개"

    /** 분배 라벨 — scoop 단독 우승. */
    const val scoopWinnerLabel: String = "양방향 우승"

    /** 분배 라벨 — HI 사이드 우승. */
    const val hiWinnerLabel: String = "하이 우승"

    /** 분배 라벨 — LO 사이드 우승. */
    const val loWinnerLabel: String = "로우 우승"

    /** 양방향 선언 무산 — 0 payout. */
    const val bothForfeit: String = "양방향 선언 실패"

    // --- Misc UI ------------------------------------------------------------

    fun dealerButton(seatNickname: String): String = "딜러 버튼, $seatNickname"
    fun timer(secondsLeft: Int): String = "남은 시간 ${secondsLeft}초"
    fun handIndex(index: Long): String = "${index}번째 핸드"
    fun rngCommit(hex: String): String =
        if (hex.isBlank()) "알엔지 커밋 없음"
        else "알엔지 커밋 ${hex.take(8)}"
}
