package com.infocar.pokermaster.feature.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infocar.pokermaster.core.data.history.HandHistoryRecord
import com.infocar.pokermaster.core.data.history.HandHistoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
)

/** Record → UI row. 포매팅은 pure 함수 — preview / 테스트에서 재사용 가능. */
internal fun HandHistoryRecord.toRow(): HandHistoryRow = HandHistoryRow(
    id = id,
    mode = mode,
    handIndex = handIndex,
    startedAtDisplay = DATE_FORMAT.format(Date(startedAt)),
    winnerDisplay = winnerSeat?.let { "seat $it 승" } ?: "무승부/사이드팟",
    potSize = potSize,
)

private val DATE_FORMAT: SimpleDateFormat =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
