package com.infocar.pokermaster.feature.table.guide

/**
 * 포커 용어집 엔트리.
 *
 * @param key 코드 레벨 식별자 (예: "pot", "sb", "bb")
 * @param title 사용자에게 노출되는 용어 이름
 * @param shortDesc 툴팁/칩 hover 용 한 줄 설명 (≤ 40자 권장)
 * @param longDesc 가이드 모드 상세 설명 (다이얼로그 본문 용)
 */
data class Term(
    val key: String,
    val title: String,
    val shortDesc: String,
    val longDesc: String,
)

/**
 * v1.1 §1.2 — 입문자용 용어 사전.
 * 한국어 설명. 설계서 기준 필수 용어를 모두 커버.
 */
object Glossary {

    val Pot = Term(
        key = "pot",
        title = "팟(Pot)",
        shortDesc = "이번 핸드에 쌓인 총 베팅액.",
        longDesc = "현재 핸드에서 모든 플레이어가 베팅한 칩의 합계. 쇼다운에서 승자가 가져간다.",
    )

    val SmallBlind = Term(
        key = "sb",
        title = "스몰 블라인드(SB)",
        shortDesc = "딜러 왼쪽이 내는 강제 선 베팅.",
        longDesc = "딜러 버튼 바로 왼쪽 좌석이 액션 전에 강제로 내는 블라인드. 보통 빅블라인드의 절반.",
    )

    val BigBlind = Term(
        key = "bb",
        title = "빅 블라인드(BB)",
        shortDesc = "SB 왼쪽이 내는 큰 강제 베팅.",
        longDesc = "SB 왼쪽 좌석이 내는 강제 베팅. 최소 레이즈 단위의 기준이며 프리플롭 액션은 UTG부터 시작된다.",
    )

    val Ante = Term(
        key = "ante",
        title = "앤티(Ante)",
        shortDesc = "전원이 내는 참가비 (7카드 스터드).",
        longDesc = "핸드 시작 전 모든 플레이어가 의무적으로 내는 소액. 7카드 스터드 같은 종목에서 블라인드 대신 사용된다.",
    )

    val Check = Term(
        key = "check",
        title = "체크(Check)",
        shortDesc = "추가 베팅 없이 턴을 넘김.",
        longDesc = "현재 콜해야 할 금액이 0일 때 칩을 내지 않고 액션을 다음 플레이어에게 넘기는 행위.",
    )

    val Call = Term(
        key = "call",
        title = "콜(Call)",
        shortDesc = "직전 베팅과 같은 금액을 맞춤.",
        longDesc = "이전 플레이어가 건 베팅 금액만큼 자신도 같은 금액을 내는 것. 팟에 남아 계속 플레이한다.",
    )

    val Bet = Term(
        key = "bet",
        title = "베트(Bet)",
        shortDesc = "첫 베팅을 거는 것.",
        longDesc = "라운드에서 아직 아무도 베팅하지 않았을 때 먼저 칩을 거는 행위. 최소 단위는 보통 BB.",
    )

    val Raise = Term(
        key = "raise",
        title = "레이즈(Raise)",
        shortDesc = "직전 베팅을 올려서 다시 건다.",
        longDesc = "이전 베팅/레이즈 금액보다 더 큰 금액을 거는 행위. 최소 레이즈는 직전 레이즈 크기 이상.",
    )

    val AllIn = Term(
        key = "all_in",
        title = "올인(All-in)",
        shortDesc = "남은 칩 전부를 건다.",
        longDesc = "보유 스택 전체를 팟에 거는 행위. 상대가 콜하지 않으면 그 시점 팟을 가져가고, 콜되면 쇼다운까지 간다.",
    )

    val Fold = Term(
        key = "fold",
        title = "폴드(Fold)",
        shortDesc = "핸드를 포기하고 빠진다.",
        longDesc = "현재 핸드를 포기하고 이미 낸 칩에 대한 권리를 버린다. 다음 핸드까지 액션에 참여하지 않는다.",
    )

    val SidePot = Term(
        key = "side_pot",
        title = "사이드 팟(Side Pot)",
        shortDesc = "올인 플레이어 이후 추가 베팅으로 만드는 별도 팟.",
        longDesc = "한 플레이어가 올인하면 그 금액까지만 메인 팟에 들어가고, 남은 플레이어들의 추가 베팅은 사이드 팟으로 분리된다. 올인한 플레이어는 사이드 팟 권리가 없다.",
    )

    val Preflop = Term(
        key = "preflop",
        title = "프리플롭(Preflop)",
        shortDesc = "커뮤니티 카드 나오기 전 첫 스트릿.",
        longDesc = "각자 홀카드 2장을 받은 후 보드에 커뮤니티 카드가 깔리기 전의 베팅 라운드. UTG부터 액션 시작.",
    )

    val Flop = Term(
        key = "flop",
        title = "플롭(Flop)",
        shortDesc = "커뮤니티 카드 3장이 깔린다.",
        longDesc = "프리플롭 종료 후 보드에 첫 3장의 커뮤니티 카드가 공개되는 스트릿. SB부터 액션 시작.",
    )

    val Turn = Term(
        key = "turn",
        title = "턴(Turn)",
        shortDesc = "커뮤니티 4번째 카드.",
        longDesc = "플롭 이후 공개되는 네 번째 커뮤니티 카드와 그 베팅 라운드. 베팅 크기가 한 단계 커지기도 한다.",
    )

    val River = Term(
        key = "river",
        title = "리버(River)",
        shortDesc = "마지막 커뮤니티 카드.",
        longDesc = "다섯 번째이자 마지막 커뮤니티 카드. 리버 베팅이 끝나면 쇼다운으로 넘어간다.",
    )

    val HeadsUp = Term(
        key = "heads_up",
        title = "헤즈업(Heads-up)",
        shortDesc = "둘만 남아서 맞붙는 상황.",
        longDesc = "핸드 혹은 테이블에 플레이어가 2명만 남아 1:1로 대결하는 상황. 블라인드 구조와 전략이 달라진다.",
    )

    val PositionBtn = Term(
        key = "pos_btn",
        title = "버튼(BTN)",
        shortDesc = "딜러 위치, 가장 유리한 포지션.",
        longDesc = "딜러 표시가 있는 좌석. 포스트플롭에서 가장 마지막에 액션하기 때문에 정보 이점이 크다.",
    )

    val PositionSb = Term(
        key = "pos_sb",
        title = "SB 포지션",
        shortDesc = "BTN 왼쪽, 포스트플롭 첫 액션.",
        longDesc = "스몰 블라인드 좌석. 프리플롭엔 두 번째로 늦게 액션하지만 포스트플롭에선 가장 먼저 액션해야 해서 불리하다.",
    )

    val PositionBb = Term(
        key = "pos_bb",
        title = "BB 포지션",
        shortDesc = "SB 왼쪽, 프리플롭 마지막 액션.",
        longDesc = "빅 블라인드 좌석. 프리플롭에선 가장 마지막에 액션하지만 포스트플롭에선 SB 다음으로 먼저 액션한다.",
    )

    val Showdown = Term(
        key = "showdown",
        title = "쇼다운(Showdown)",
        shortDesc = "핸드를 공개해 승자를 가린다.",
        longDesc = "리버 베팅이 끝난 후 남은 플레이어들이 홀카드를 공개하고 핸드 랭킹을 비교해 팟을 분배하는 단계.",
    )

    /** v1.1 §1.2 필수 용어 전체. */
    val All: List<Term> = listOf(
        Pot,
        SmallBlind,
        BigBlind,
        Ante,
        Check,
        Call,
        Bet,
        Raise,
        AllIn,
        Fold,
        SidePot,
        Preflop,
        Flop,
        Turn,
        River,
        HeadsUp,
        PositionBtn,
        PositionSb,
        PositionBb,
        Showdown,
    )

    private val byKey: Map<String, Term> = All.associateBy { it.key }

    /** [key] 에 해당하는 용어. 없으면 null. */
    fun find(key: String): Term? = byKey[key]

    /** [key] 에 해당하는 용어. 없으면 [IllegalArgumentException]. */
    fun require(key: String): Term =
        byKey[key] ?: throw IllegalArgumentException("Unknown glossary key: $key")
}
