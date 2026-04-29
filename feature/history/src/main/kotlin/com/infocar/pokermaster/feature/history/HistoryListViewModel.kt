package com.infocar.pokermaster.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import com.infocar.pokermaster.core.model.ShowdownSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/**
 * 히스토리 리스트 상태 — M5-C.
 *
 * Room Flow → 변환된 [HandHistoryRow] 리스트를 UI 에 그대로 노출.
 * Paging3 도입 전까지는 최근 50개 (repo.DEFAULT_LIMIT) 로 충분.
 */
@HiltViewModel
class HistoryListViewModel @Inject constructor(
    repo: HandHistoryRepository,
) : ViewModel() {

    val state: StateFlow<HistoryListUiState> = repo.observeRecent()
        .map { records -> HistoryListUiState(records.map { it.toRow() }, loaded = true) }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryListUiState(items = emptyList(), loaded = false),
        )
}

data class HistoryListUiState(
    val items: List<HandHistoryRow>,
    /** 최초 Flow emission 이 도착했는지. 로딩 스피너 vs empty placeholder 분기. */
    val loaded: Boolean,
)

data class HandHistoryRow(
    val id: Long,
    val mode: String,
    val handIndex: Long,
    val startedAtDisplay: String,
    val winnerDisplay: String,
    val potSize: Long,
    /** 본인의 net 손익 (양수=이익, 음수=손실, 0=무승부). null=계산 불가. */
    val humanNet: Long?,
)

/** Record → UI row. 포매팅은 pure 함수 — preview / 테스트에서 재사용 가능. */
internal fun HandHistoryRecord.toRow(): HandHistoryRow {
    val humanSeat = initialState.players.firstOrNull { it.isHuman }?.seat
    val nicknameBySeat = initialState.players.associate { it.seat to it.nickname }

    // 한 번 디코딩으로 humanNet + 다중 winner 둘 다 추출.
    val summary: ShowdownSummary? = runCatching {
        HandHistoryRepository.DEFAULT_JSON.decodeFromString(ShowdownSummary.serializer(), resultJson)
    }.getOrNull()

    val humanNet: Long? = if (humanSeat != null && summary != null) {
        val payout = summary.payouts[humanSeat] ?: 0L
        val committed = initialState.players.firstOrNull { it.seat == humanSeat }?.committedThisHand ?: 0L
        payout - committed
    } else null

    val winnerLabel = computeWinnerLabel(
        summary = summary,
        legacyWinnerSeat = winnerSeat,
        humanSeat = humanSeat,
        nicknameBySeat = nicknameBySeat,
    )

    return HandHistoryRow(
        id = id,
        mode = mode,
        handIndex = handIndex,
        startedAtDisplay = DATE_FORMAT.format(Date(startedAt)),
        winnerDisplay = winnerLabel,
        potSize = potSize,
        humanNet = humanNet,
    )
}

/**
 * 분할/사이드팟까지 반영한 winnerDisplay 계산.
 *  - HiLo split: "하이 {hi닉네임} / 로우 {lo닉네임}"
 *  - 다중 winner (사이드팟 포함): "{닉1}, {닉2} 승" (내가 포함되면 "내가 승")
 *  - 단독: "{닉네임} 승" 또는 "내가 승"
 *  - summary 디코딩 실패 시 legacy winnerSeat fallback.
 */
private fun computeWinnerLabel(
    summary: ShowdownSummary?,
    legacyWinnerSeat: Int?,
    humanSeat: Int?,
    nicknameBySeat: Map<Int, String>,
): String {
    if (summary == null) {
        // legacy fallback — 디코딩 실패 또는 빈 resultJson.
        val seat = legacyWinnerSeat ?: return "무승부/사이드팟"
        val nick = nicknameBySeat[seat] ?: "좌석 $seat"
        return if (seat == humanSeat) "내가 승" else "$nick 승"
    }
    val mainPot = summary.pots.firstOrNull()
    val isHiLoSplit = mainPot != null &&
        mainPot.hiWinnerSeats.isNotEmpty() &&
        mainPot.loWinnerSeats.isNotEmpty() &&
        mainPot.hiWinnerSeats != mainPot.loWinnerSeats
    if (isHiLoSplit && mainPot != null) {
        val hiNick = mainPot.hiWinnerSeats.toSortedSet().joinToString(",") { s ->
            if (s == humanSeat) "나" else (nicknameBySeat[s] ?: "좌석 $s")
        }
        val loNick = mainPot.loWinnerSeats.toSortedSet().joinToString(",") { s ->
            if (s == humanSeat) "나" else (nicknameBySeat[s] ?: "좌석 $s")
        }
        return "Hi $hiNick · Lo $loNick"
    }
    val winners = summary.payouts.filter { it.value > 0L }.keys.toSortedSet()
    if (winners.isEmpty()) {
        return legacyWinnerSeat?.let { seat ->
            val nick = nicknameBySeat[seat] ?: "좌석 $seat"
            if (seat == humanSeat) "내가 승" else "$nick 승"
        } ?: "무승부/사이드팟"
    }
    return winners.joinToString(", ") { s ->
        if (s == humanSeat) "내가" else (nicknameBySeat[s] ?: "좌석 $s")
    } + " 승"
}

private val DATE_FORMAT: SimpleDateFormat =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
