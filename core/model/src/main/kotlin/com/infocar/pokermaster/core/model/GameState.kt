package com.infocar.pokermaster.core.model

import kotlinx.serialization.Serializable

/**
 * 게임 상태 스냅샷. immutable. Reducer 는 이 인스턴스를 copy 해 신규 상태 산출.
 *
 *  - [stateVersion]: 단조 증가 — v1.1 §5.1 선계산 큐 무효화의 봉인 키.
 *  - [handIndex]: 현 테이블의 몇 번째 핸드 (nonce 로 Rng 에 주입, 1부터).
 *  - [btnSeat]: 딜러 버튼 위치. 핸드 간 이동 (시계방향 next alive 좌석).
 *  - [toActSeat]: 다음 액션 좌석 (null = 액션 대기 없음 — deal/advance 중).
 *  - [community]: 홀덤 커뮤니티 0~5 장. 7스터드는 빈 리스트.
 *  - [betToCall]: 현 스트릿 콜 비용 절대 기준 (committedThisStreet 기준 최대치).
 *  - [minRaise]: 다음 raise 의 최소 commit 절대값 (미이행 raise 블로킹 반영).
 *  - [reopenAction]: v1.1 §3.2.A — all-in less-than-min-raise 이후에는 기존 raiser 가 re-raise 불가.
 *  - [lastFullRaiseAmount]: 마지막 full-raise 의 delta (다음 min-raise 계산 기준).
 *  - [lastAggressorSeat]: 마지막 bet/raise 좌석 (헤즈업·헤드업 UI 마커).
 *  - [pendingShowdown]: SHOWDOWN 시 payouts 결과 (UI 가 애니메이션 후 applyShowdown 으로 반영).
 */
@Serializable
data class GameState(
    val mode: GameMode,
    val config: TableConfig,
    val stateVersion: Long,
    val handIndex: Long,
    val players: List<PlayerState>,
    val btnSeat: Int,
    val toActSeat: Int?,
    val street: Street,
    val community: List<Card> = emptyList(),
    val betToCall: Long = 0L,
    val minRaise: Long = 0L,
    val reopenAction: Boolean = true,
    val lastFullRaiseAmount: Long = 0L,
    val lastAggressorSeat: Int? = null,
    /** 소비된 카드 수 (deckCursor). Rng.deck 에서 이미 분배된 인덱스 기반. */
    val deckCursor: Int = 0,
    /** 현 스트릿에서 발생한 full raise 수. 7스터드 raise cap 3 enforcement 용. 홀덤은 미사용. */
    val raisesThisStreet: Int = 0,
    /** 현 핸드 Rng commit (hex) — UI 표기용. 원본 Rng 는 Controller 가 보관. */
    val rngCommitHex: String = "",
    /** 쇼다운 시 계산된 좌석별 payout (Controller 가 채움). 다음 핸드 전까지 UI 애니에 사용. */
    val pendingShowdown: ShowdownSummary? = null,
    /** 핸드 종료 후 다음 핸드 대기. UI 가 [pendingShowdown] 소비 후 'Next Hand' 누르면 풀림. */
    val paused: Boolean = false,
    /**
     * 7-Stud Hi-Lo 한국식 declare 단계에서 좌석별 선언. [Street.DECLARE] 단계에서만 채워짐.
     * 키 = seat, 값 = HIGH/LOW/SWING. 폴드한 좌석은 포함되지 않음.
     * SHOWDOWN 으로 전이된 후에도 분배 산정용으로 유지(다음 startHand 가 비움).
     */
    val declarations: Map<Int, Declaration> = emptyMap(),
) {
    /** 살아있는 좌석들 (폴드 아닌). */
    val alivePlayers: List<PlayerState> get() = players.filter { it.alive }
    /** 액션 가능한 좌석들 (폴드/올인 아닌). */
    val activePlayers: List<PlayerState> get() = players.filter { it.active }
    /** 현 toAct 플레이어. */
    val toAct: PlayerState? get() = toActSeat?.let { seat -> players.firstOrNull { it.seat == seat } }

    companion object {
        const val INITIAL_VERSION: Long = 0L
    }
}

/**
 * 쇼다운 결과 요약 — UI 애니/해설용. payouts/hands 는 좌석별.
 *
 *  - [bestHands]: 좌석별 베스트 5장 + 카테고리 — UI 하이라이트.
 *  - [payouts]: SidePotResult + ShowdownResolver 결과 통합 (uncalled 환급 포함).
 *  - [pots]: 사이드팟 배열 (시각화 시퀀스용 amount + eligible + winners).
 *  - [uncalledReturn]: 자기 혼자 적립한 layer 환급 (v1.1 §3.1).
 *  - [deadMoney]: 자격자 0 layer (사라진 칩 — 통계용).
 */
@Serializable
data class ShowdownSummary(
    val bestHands: Map<Int, ShowdownHandInfo>,
    val payouts: Map<Int, Long>,
    val pots: List<PotSummary>,
    val uncalledReturn: Map<Int, Long>,
    val deadMoney: Long,
    val rngServerSeedHex: String,     // v1.1 §3.5 commit/reveal
    val rngClientSeedHex: String,
)

@Serializable
data class ShowdownHandInfo(
    val seat: Int,
    val categoryName: String,   // "풀하우스" 등 localizedish
    val bestFive: List<Card>,   // UI 하이라이트
)

@Serializable
data class PotSummary(
    val amount: Long,
    val eligibleSeats: Set<Int>,
    /** Hi+Lo 합집합 — 기존 호출자 호환 위해 유지. UI 분기는 [hiWinnerSeats]/[loWinnerSeats] 사용. */
    val winnerSeats: Set<Int>,
    /** 사이드팟 인덱스 — 0 = main, 1+ = side. */
    val index: Int,
    /** 하이 사이드 승자. 홀덤 + Hi-only 7스터드는 [winnerSeats] 와 동일. */
    val hiWinnerSeats: Set<Int> = winnerSeats,
    /** 로우 사이드 승자. HiLo 모드에서만 채워짐 (qualify 미달 시 emptySet). 그 외 emptySet. */
    val loWinnerSeats: Set<Int> = emptySet(),
    /**
     * 한국식 Hi-Lo Declare 에서 단독 양방향 우승(scoop) 좌석. UI 가 ★HL 강조 표시.
     * 비-HiLo / Both 선언 무산 / 동률 분할 등 일반 케이스는 emptySet.
     */
    val scoopWinnerSeats: Set<Int> = emptySet(),
)
