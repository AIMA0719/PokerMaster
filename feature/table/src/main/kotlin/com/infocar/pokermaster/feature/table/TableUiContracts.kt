package com.infocar.pokermaster.feature.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.DeclareDirection
import com.infocar.pokermaster.core.model.GameMode
import com.infocar.pokermaster.core.model.GameState
import com.infocar.pokermaster.core.model.PlayerState
import com.infocar.pokermaster.core.model.PotSummary
import com.infocar.pokermaster.core.model.Street

/**
 * 테이블 하위 컴포넌트들이 합의하는 UI 계약.
 *
 * 각 항목은 Phase-B 병렬 에이전트가 구현 — 여기선 시그니처만 고정해 동시 작업 충돌을 방지.
 */

/** 좌석 레이아웃: 8좌석 타원 좌표 + 내 좌석 bottom-center 고정. (Agent A) */
typealias SeatContent = @Composable (PlayerState) -> Unit

/** 액션바 — 사람 차례 전용. (Agent B) */
interface ActionBarState {
    val canCheck: Boolean
    val canCall: Boolean
    val callAmount: Long
    val canRaise: Boolean
    val minRaiseTotal: Long
    val maxRaiseTotal: Long
    val currentCommitted: Long
    val potSize: Long
    val myChips: Long
    /** 7스터드/HiLo 에서 콜 봉착 시 노출되는 "구사" (SAVE_LIFE) 옵션. 홀덤은 항상 false. */
    val canSaveLife: Boolean get() = false
    /** Street.DECLARE 시 true. ActionBar 가 [하이][로우][양방향] 모드로 렌더링. */
    val isDeclarePhase: Boolean get() = false
}

/** 핸드 종료 바텀시트에 표현되는 사이드팟 시퀀스. (Agent B) */
data class HandEndViewData(
    val pots: List<PotSummary>,
    val handInfos: Map<Int, String>,   // seat → 한국어 카테고리 ("풀하우스")
    val bestFiveBySeat: Map<Int, List<Card>>,
    val payoutsBySeat: Map<Int, Long>,
    val uncalledBySeat: Map<Int, Long>,
    val nicknameBySeat: Map<Int, String>,
    /** 좌석별 선언. SHOWDOWN 시점이라 모두 visible. 비-HiLo 모드는 emptyMap. */
    val declarationsBySeat: Map<Int, DeclareDirection> = emptyMap(),
    val mode: GameMode = GameMode.HOLDEM_NL,
)

/** 전체 테이블 상태 → UI 뷰데이터로 변환. TableScreen 이 호출. */
object TableUiMapper {

    fun mapHandEnd(state: GameState): HandEndViewData? {
        val s = state.pendingShowdown ?: return null
        // HiLo UI bug-hunt: SEVEN_STUD_HI_LO 모드에서 좌석 선언이 SHOWDOWN 직전까지 declaration 으로
        // 남아있다고 가정. mode != SEVEN_STUD_HI_LO 면 emptyMap (확정성 + 비-HiLo 화면 노출 방지).
        val declarations: Map<Int, DeclareDirection> =
            if (state.mode == GameMode.SEVEN_STUD_HI_LO) {
                state.players
                    .filter { it.alive && it.declaration != null }
                    .associate { it.seat to it.declaration!! }
            } else emptyMap()
        return HandEndViewData(
            pots = s.pots,
            handInfos = s.bestHands.mapValues { it.value.categoryName },
            bestFiveBySeat = s.bestHands.mapValues { it.value.bestFive },
            payoutsBySeat = s.payouts,
            uncalledBySeat = s.uncalledReturn,
            nicknameBySeat = state.players.associate { it.seat to it.nickname },
            declarationsBySeat = declarations,
            mode = state.mode,
        )
    }

    fun mapActionBar(state: GameState, humanSeat: Int): ActionBarState? {
        if (state.pendingShowdown != null) return null
        if (state.toActSeat != humanSeat) return null
        val me = state.players.firstOrNull { it.seat == humanSeat } ?: return null
        if (!me.active) return null

        // HiLo UI bug-hunt: Street.DECLARE 진입 시 베팅 대신 선언 액션바로 분기. 베팅 필드 zero/disabled.
        if (state.street == Street.DECLARE) {
            return object : ActionBarState {
                override val canCheck = false
                override val canCall = false
                override val callAmount = 0L
                override val canRaise = false
                override val minRaiseTotal = 0L
                override val maxRaiseTotal = 0L
                override val currentCommitted = me.committedThisStreet
                override val potSize = state.players.sumOf { it.committedThisHand }
                override val myChips = me.chips
                override val canSaveLife = false
                override val isDeclarePhase = true
            }
        }

        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        val myMaxCommit = me.committedThisStreet + me.chips
        val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
        val mayRaise = state.reopenAction || !me.actedThisStreet
        return object : ActionBarState {
            override val canCheck = toCall == 0L
            override val canCall = toCall > 0L
            override val callAmount = toCall.coerceAtMost(me.chips)
            override val canRaise = mayRaise && me.chips > 0 && myMaxCommit > state.betToCall
            override val minRaiseTotal = state.minRaise.coerceAtMost(myMaxCommit)
            override val maxRaiseTotal = myMaxCommit
            override val currentCommitted = me.committedThisStreet
            override val potSize = state.players.sumOf { it.committedThisHand }
            override val myChips = me.chips
            override val canSaveLife = isStud && toCall > 0L && me.chips > 0
        }
    }

    fun totalPot(state: GameState): Long =
        state.players.sumOf { it.committedThisHand }

    /**
     * 상대 선언 마스킹: DECLARE 단계 동안 본인이 아닌 좌석의 declaration 을 null 로 지운다.
     *
     * 한국식 7스터드 Hi-Lo 의 동시 비공개 선언 룰 — SHOWDOWN 진입 직전까지 다른 좌석의 선언을
     * 보면 안 된다. UI 가 PlayerState 를 그리기 전에 이 헬퍼로 viewer-perspective 로 변환한다.
     */
    fun mapPlayerForViewer(player: PlayerState, viewerSeat: Int, street: Street): PlayerState =
        if (street == Street.DECLARE && player.seat != viewerSeat) player.copy(declaration = null)
        else player
}

/** 게임 오버 정보 (한 명만 칩 보유 시). */
data class GameOverInfo(
    val winnerNickname: String,
    val winnerSeat: Int,
    val isHumanWinner: Boolean,
    val finalChips: Long,
)

/** Modifier alias — Compose import 줄이기. */
typealias Modif = Modifier

/** 액션 dispatch alias — ViewModel 없이 테스트 가능. */
typealias OnAction = (Action) -> Unit
