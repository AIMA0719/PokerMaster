package com.infocar.pokermaster.feature.table

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.infocar.pokermaster.core.model.Action
import com.infocar.pokermaster.core.model.Card
import com.infocar.pokermaster.core.model.Declaration
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
    /** 실제 액션 입력 가능 여부. 상대 턴에는 UI 자리는 유지하되 버튼은 비활성화한다. */
    val actionsEnabled: Boolean get() = true
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
    val declarationsBySeat: Map<Int, Declaration> = emptyMap(),
    val mode: GameMode = GameMode.HOLDEM_NL,
)

/** 전체 테이블 상태 → UI 뷰데이터로 변환. TableScreen 이 호출. */
object TableUiMapper {

    fun mapHandEnd(state: GameState): HandEndViewData? {
        val s = state.pendingShowdown ?: return null
        val declarations: Map<Int, Declaration> =
            if (state.mode == GameMode.SEVEN_STUD_HI_LO) {
                state.declarations
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
        val me = state.players.firstOrNull { it.seat == humanSeat } ?: return null
        val isHumanTurn = state.toActSeat == humanSeat

        if (state.street == Street.DECLARE) {
            if (!isHumanTurn) return null
            if (!me.alive) return null
            return object : ActionBarState {
                override val actionsEnabled = true
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

        if (!me.active) return null

        val toCall = (state.betToCall - me.committedThisStreet).coerceAtLeast(0L)
        val myMaxCommit = me.committedThisStreet + me.chips
        val isStud = state.mode == GameMode.SEVEN_STUD || state.mode == GameMode.SEVEN_STUD_HI_LO
        val mayRaise = state.reopenAction || !me.actedThisStreet
        return object : ActionBarState {
            override val actionsEnabled = isHumanTurn
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
     * 선언 값은 [GameState.declarations] 에 저장된다. 이 함수는 기존 호출부 호환용 identity mapper.
     */
    fun mapPlayerForViewer(player: PlayerState, viewerSeat: Int, street: Street): PlayerState = player
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
